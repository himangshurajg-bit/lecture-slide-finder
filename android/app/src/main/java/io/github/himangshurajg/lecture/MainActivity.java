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

/** Offline, bundled WebView shell. No server, network permission or JavaScript interface. */
public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/index.html";
    private static final int PICK = 10, EXPORT = 11;
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private String pendingNotes;
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
        settings.setDomStorageEnabled(false);
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
                        name.endsWith(".html") ? "text/html" : name.endsWith(".svg") ? "image/svg+xml" :
                        name.endsWith(".webmanifest") ? "application/manifest+json" : "application/octet-stream";
                    Map<String,String> headers = new HashMap<>();
                    headers.put("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; media-src blob:; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'");
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
                intent.setType(video ? "video/*" : "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivityForResult(intent, PICK); }
                catch(ActivityNotFoundException e) { chooser.onReceiveValue(null); chooser=null; tell("No document picker is installed."); }
                return true;
            }
            @Override public boolean onJsPrompt(WebView view, String url, String message, String value, JsPromptResult result) {
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
                    outcome="Notes exported. Keep the saved file before closing this app.";
                } catch(Exception e) { outcome="Export failed. Your notes are still here; try another location."; }
                final String completed=outcome;
                runOnUiThread(()->tell(completed));
            },"lecture-export").start();
        }
    }
    private void tell(String message) {
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('lecture-export-result',{detail:"+JSONObject.quote(message)+"}))",null);
    }
    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("Close lecture?")
            .setMessage("Export your notes first. Closing the app clears this session.")
            .setPositiveButton("Close",(d,w)->finish()).setNegativeButton("Keep studying",null).show();
    }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(web!=null)web.onResume(); }
    @Override protected void onDestroy() {
        if(chooser!=null)chooser.onReceiveValue(null);
        pendingNotes=null;
        if(web!=null)web.destroy();
        super.onDestroy();
    }
}
