package io.github.himangshurajg.lecture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;
import android.view.WindowManager;

/** Offline, bundled WebView shell. No server, network permission or JavaScript interface. */
public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/index.html";
    private static final int PICK = 10, EXPORT = 11;
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private boolean choosingVideo;
    private Uri candidateVideoUri, acceptedVideoUri;
    private AtomicBoolean asrCancel;
    private Thread asrThread;
    private boolean destroyed;
    private int asrGeneration;
    private String pendingNotes;
    private final java.util.concurrent.ExecutorService frameExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();
    private AtomicBoolean frameCancel=new AtomicBoolean(false);
    private FrameDecoder frameDecoder;
    private boolean frameRequestPending;
    private final Set<String> selectedUris = Collections.synchronizedSet(new HashSet<String>());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xffedf6f5);
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                         insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Only user-selected document URIs are handed to file inputs.
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(false);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & 2) != 0);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !HOME.equals(req.getUrl().toString()); // No remote navigation, intents or file URLs.
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("content".equals(u.getScheme()) && selectedUris.contains(u.toString())) return null;
                if (!"https".equals(u.getScheme()) || !HOST.equals(u.getHost()) || u.getPort()!=-1)
                    return blocked();
                String path = u.getPath();
                if (path == null || !path.startsWith("/assets/") || path.contains("..") || path.contains("\\")) return blocked();
                String name = path.substring(8);
                try {
                    String mime = name.endsWith(".js") ? "application/javascript" : name.endsWith(".css") ? "text/css" :
                        name.endsWith(".wasm") ? "application/wasm" : name.endsWith(".html") ? "text/html" : name.endsWith(".svg") ? "image/svg+xml" :
                        name.endsWith(".webmanifest") ? "application/manifest+json" : "application/octet-stream";
                    Map<String,String> headers = new HashMap<>();
                    headers.put("Content-Security-Policy", "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; media-src blob:; connect-src 'self' blob: data:; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'");
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open(name));
                } catch (IOException e) { return blocked(); }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (!HOME.equals(view.getUrl())) { cb.onReceiveValue(null); return true; }
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
                boolean video = false;
                for (String accept : params.getAcceptTypes()) if (accept.startsWith("video/")) video=true;
                choosingVideo=video;
                intent.setType(video ? "video/*" : "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivityForResult(intent, PICK); }
                catch(ActivityNotFoundException e) { chooser.onReceiveValue(null); chooser=null; tell("No document picker is installed."); }
                return true;
            }
            @Override public boolean onJsPrompt(WebView view, String url, String message, String value, JsPromptResult result) {
                if(HOME.equals(url)) {
                    if("lecture-frames-v1".equals(message)) {frameRequest(value);result.confirm("handled");return true;}
                    if("lecture-video-accepted-v1".equals(message)) {cancelFrames();acceptedVideoUri=candidateVideoUri;result.confirm("ok");return true;}
                    if("lecture-asr-start-v1".equals(message)) {startTranscription(value);result.confirm("handled");return true;}
                    if("lecture-asr-cancel-v1".equals(message)) {if(asrCancel!=null)asrCancel.set(true);result.confirm("ok");return true;}
                }
                if (!HOME.equals(url) || !"lecture-export-v1".equals(message) || value == null || value.length() > 5*1024*1024) {
                    result.cancel(); return true;
                }
                if(pendingNotes != null) { result.cancel(); tell("Finish or cancel the current export first."); return true; }
                pendingNotes=value;
                Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/markdown").putExtra(Intent.EXTRA_TITLE,"lecture-notes.md");
                try { startActivityForResult(create, EXPORT); result.confirm("pending"); }
                catch(ActivityNotFoundException e) { pendingNotes=null; result.cancel(); tell("No document saver is installed."); }
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                if(!HOME.equals(url)) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("Continue",(d,w)->result.confirm()).setNegativeButton("Cancel",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel()).show(); return true;
            }
        });
        setContentView(web);
        web.loadUrl(HOME);
    }
    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        Uri uri = result==RESULT_OK && data!=null ? data.getData() : null;
        if(request==PICK && chooser!=null) {
            if(uri!=null && "content".equals(uri.getScheme())) {
                if(choosingVideo)candidateVideoUri=uri;
                selectedUris.add(uri.toString()); chooser.onReceiveValue(new Uri[]{uri});
            } else chooser.onReceiveValue(null);
            chooser=null;
        }
        if(request==EXPORT) {
            String text = pendingNotes; pendingNotes=null;
            if(uri==null || text==null) { tell("Export cancelled. Your notes are still here."); return; }
            if(!"content".equals(uri.getScheme())) { tell("Cannot save to this location. Your notes are still here."); return; }
            new Thread(()->{
                String outcome;
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                    if(out==null)throw new IOException("No output stream");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    outcome="Notes exported. A separate copy is saved in your chosen location.";
                } catch(Exception e) { outcome="Export failed. Your notes are still here; try another location."; }
                final String completed=outcome;
                runOnUiThread(()->tell(completed));
            },"lecture-export").start();
        }
    }
    private void asrEvent(String state,String message,int percent) {
        if(destroyed)return;
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('lecture-asr-state',{detail:{state:"+JSONObject.quote(state)+",message:"+JSONObject.quote(message)+",percent:"+percent+"}}))",null);
    }
    private void startTranscription(String language) {
        if(frameRequestPending){asrEvent("error","Wait for frame decoding to stop before transcription.",-1);return;}
        cancelFrames();
        if(asrThread!=null) {asrEvent("busy","Please wait for the current transcription to stop.",-1);return;}
        if(acceptedVideoUri==null){asrEvent("error","Open a video first.",-1);return;}
        if(!"en".equals(language) && !"hi".equals(language)){asrEvent("error","Choose English or Hindi.",-1);return;}
        final Uri source=acceptedVideoUri;
        new AlertDialog.Builder(this).setTitle("Generate transcript on this phone?")
            .setMessage("English or Hindi transcription runs entirely on this phone, without a computer, internet, account or paid API. First use prepares the chosen bundled model in app storage. Allow about 250 MiB of free storage for both models. Choose the recording’s main language; mixed-language speech, names and technical terms can be inaccurate. Keep this screen open; leaving cancels the job. Your existing notes stay until you accept the result.")
            .setPositiveButton("Transcribe",(d,w)->{
                final int generation=++asrGeneration;
                asrCancel=new AtomicBoolean(false);final AtomicBoolean cancel=asrCancel;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                asrEvent("running","Preparing offline transcription…",-1);
                asrThread=new Thread(()->{
                    String transcript=null,error=null;boolean stopped=false;
                    try {transcript=new OfflineTranscriber(this,cancel,(stage,percent)->runOnUiThread(()->{if(generation==asrGeneration)asrEvent("running",stage,percent);})).transcribe(source,language);}
                    catch(InterruptedException e){stopped=true;}
                    catch(Throwable e){error="Cannot transcribe this recording on this device. Check free storage, the recording language and audio format, then try a shorter clip. Your existing notes are unchanged.";android.util.Log.e("LectureASR","Offline transcription failed: "+e.getClass().getSimpleName());}
                    final String json=transcript,problem=error;final boolean wasCancelled=stopped||cancel.get();
                    runOnUiThread(()->{
                        asrThread=null;asrCancel=null;if(destroyed||generation!=asrGeneration)return;
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        if(wasCancelled){asrEvent("cancelled","Transcription cancelled. Your existing transcript and notes are unchanged.",-1);return;}
                        if(problem!=null){asrEvent("error",problem,-1);return;}
                        // Small messages avoid Android's Binder size limit for long lecture transcripts.
                        web.evaluateJavascript("window.__lectureAsrResult=''",null);
                        for(int i=0;i<json.length();i+=16000)web.evaluateJavascript("window.__lectureAsrResult+="+JSONObject.quote(json.substring(i,Math.min(i+16000,json.length()))),null);
                        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('lecture-asr-complete',{detail:window.__lectureAsrResult}));delete window.__lectureAsrResult",null);
                        asrEvent("done","Generated on this phone. Review recognition accuracy before using your notes.",100);
                    });
                },"lecture-offline-asr");asrThread.start();
            }).setNegativeButton("Cancel",(d,w)->asrEvent("cancelled","Transcription was not started.",-1))
            .setOnCancelListener(d->asrEvent("cancelled","Transcription was not started.",-1)).show();
    }
    private void cancelFrames() {
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        frameCancel.set(true);
        frameExecutor.execute(()->{if(frameDecoder!=null){frameDecoder.close();frameDecoder=null;}});
    }
    private void frameReply(int request,JSONObject payload) {
        if(destroyed)return;
        try{payload.put("request",request);}catch(Exception ignored){}
        String json=payload.toString();
        web.evaluateJavascript("window.__lectureFrames=''",null);
        for(int i=0;i<json.length();i+=16000)web.evaluateJavascript("window.__lectureFrames+="+JSONObject.quote(json.substring(i,Math.min(i+16000,json.length()))),null);
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('lecture-frames-result',{detail:window.__lectureFrames}));delete window.__lectureFrames",null);
    }
    private void frameRequest(String value) {
        int request=-1;
        try {
            if(value==null || value.length()>1000)throw new IllegalArgumentException();
            JSONObject args=new JSONObject(value);request=args.getInt("request");
            String action=args.getString("action");
            if("close".equals(action)){cancelFrames();return;}
            if(!"reset".equals(action) && !"next".equals(action))throw new IllegalArgumentException();
            if(frameRequestPending)throw new IllegalStateException("Wait for the current frame request");
            if(asrThread!=null)throw new IllegalStateException("Wait for transcription to finish");
            if(acceptedVideoUri==null)throw new IllegalStateException("Open a video first");
            final int id=request;final boolean reset="reset".equals(action);final Uri source=acceptedVideoUri;
            final AtomicBoolean cancel=reset?new AtomicBoolean(false):frameCancel;
            if(reset){frameCancel.set(true);frameCancel=cancel;}
            frameRequestPending=true;getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            frameExecutor.execute(()->{
                JSONObject payload;
                try {
                    if(reset){if(frameDecoder!=null)frameDecoder.close();frameDecoder=new FrameDecoder(this,source);}
                    if(frameDecoder==null)throw new IllegalStateException("Restart frame browsing");
                    payload=frameDecoder.page(4,cancel);
                    if(frameDecoder.isDone()){frameDecoder.close();frameDecoder=null;}
                } catch(Throwable e) {
                    if(frameDecoder!=null){frameDecoder.close();frameDecoder=null;}
                    payload=new JSONObject();try{payload.put("error",cancel.get()?"Frame decoding cancelled. Completed points are kept.":"Cannot decode every frame of this video on this device. Try H.264 MP4; no full-coverage claim is made.");}catch(Exception ignored){}
                }
                final JSONObject reply=payload;
                runOnUiThread(()->{frameRequestPending=false;frameReply(id,reply);});
            });
        } catch(Exception e){JSONObject result=new JSONObject();try{result.put("error",e instanceof IllegalStateException?e.getMessage():"Invalid frame request");}catch(Exception ignored){}frameReply(request,result);}
    }
    private void tell(String message) {
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('lecture-export-result',{detail:"+JSONObject.quote(message)+"}))",null);
    }
    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("Close lecture?")
            .setMessage("Your transcript and notes stay saved on this device. Select the video again when you return.")
            .setPositiveButton("Close",(d,w)->finish()).setNegativeButton("Keep studying",null).show();
    }
    @Override protected void onPause() { if(asrCancel!=null)asrCancel.set(true); cancelFrames(); web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause()})()",null); web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(web!=null)web.onResume(); }
    @Override protected void onDestroy() {
        destroyed=true; ++asrGeneration;
        if(asrCancel!=null)asrCancel.set(true);
        if(chooser!=null)chooser.onReceiveValue(null);
        pendingNotes=null;
        frameCancel.set(true);frameExecutor.execute(()->{if(frameDecoder!=null){frameDecoder.close();frameDecoder=null;}});frameExecutor.shutdown();
        if(web!=null)web.destroy();
        super.onDestroy();
    }
}
