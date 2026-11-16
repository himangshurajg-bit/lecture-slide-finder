package io.github.himangshurajg.lecture.test;
import android.app.*;import android.content.*;import android.os.*;import android.webkit.*;import android.net.Uri;import android.media.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;
/** Original six-frame B-frame fixture; proves sequential output/PTS independently of gallery UI. */
public final class FrameStudyChecks extends RuntimeChecks {
 String ocrProgress()throws Exception{return js("JSON.stringify((function(){var e=document.querySelector('#ocr-state'),d=JSON.parse(localStorage.getItem('lecture-frame-study-v1')||'{}');return {state:e?e.textContent:'',checked:d.checked||0,points:d.points?d.points.length:0};})())");}
 void awaitOcrProbe(String language)throws Exception{
  js("document.querySelector('#ocr-language').value='"+language+"';document.querySelector('#frames-current').click()");
  long deadline=System.currentTimeMillis()+55000;
  while(System.currentTimeMillis()<deadline){
   String state=js("document.querySelector('#ocr-state').textContent");
   if((state.contains("Current displayed picture read")||state.contains("Offline OCR failed")) && "true".equals(js("!document.querySelector('#frames-current').disabled"))){
    log("OCR_LANGUAGE_PROBE language="+language+" state="+ocrProgress());return;
   }
   Thread.sleep(500);
  }
  throw new Exception("OCR diagnostic probe timed out for "+language+"; "+ocrProgress());
 }
 void awaitOcr(String name)throws Exception{
  log("AWAIT "+name);long deadline=System.currentTimeMillis()+180000,nextLog=System.currentTimeMillis();
  while(System.currentTimeMillis()<deadline){
   if("true".equals(js("document.querySelector('#ocr-state').textContent.includes('Complete decoded-frame pass: 6')"))){log("PASS "+name);return;}
   if("true".equals(js("document.querySelector('#ocr-state').textContent.includes('Offline OCR failed')")))throw new Exception("OCR diagnostic failure: "+ocrProgress());
   long now=System.currentTimeMillis();if(now>=nextLog){log("OCR_PROGRESS "+ocrProgress());nextLog=now+15000;}
   Thread.sleep(1000);
  }
  String status=ocrProgress();
  throw new Exception("Timeout: "+name+"; "+status);
 }
 @Override public void onStart(){Bundle result=new Bundle();Object decoder=null;try{
  File source=new File(getTargetContext().getCacheDir(),"frame-study.mp4");
  try(InputStream in=getContext().getAssets().open("frame-study.mp4");OutputStream out=new FileOutputStream(source)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  logExtractorPts(Uri.fromFile(source),"frame-study",12);
  Class<?> type=getTargetContext().getClassLoader().loadClass("io.github.himangshurajg.lecture.FrameDecoder");
  decoder=type.getConstructor(android.content.Context.class,Uri.class).newInstance(getTargetContext(),Uri.fromFile(source));
  AtomicBoolean cancel=new AtomicBoolean(false);int count=0;boolean done=false;JSONArray pts=new JSONArray();
  while(!done){JSONObject batch=(JSONObject)type.getMethod("page",int.class,AtomicBoolean.class).invoke(decoder,4,cancel);JSONArray frames=batch.getJSONArray("frames");if(frames.length()>4)throw new Exception("Unbounded page");for(int i=0;i<frames.length();i++){JSONObject frame=frames.getJSONObject(i);pts.put(new JSONObject().put("index",frame.getInt("index")).put("time",frame.getDouble("time")));if(!frame.getString("image").startsWith("data:image/jpeg;base64,"))throw new Exception("No image");count++;}done=batch.getBoolean("done");if(count>6)throw new Exception("Duplicate frame");}
  log("FRAME_PTS_SEQUENCE api="+Build.VERSION.SDK_INT+" "+pts);if(count!=6)throw new Exception("Expected all 6 frames, got "+count);for(int i=0;i<pts.length();i++){JSONObject frame=pts.getJSONObject(i);double expectedTime=i*.5;if(frame.getInt("index")!=i||Math.abs(frame.getDouble("time")-expectedTime)>.001)throw new Exception("Frame/PTS mismatch: "+pts+" expected index="+i+" time="+expectedTime);}type.getMethod("close").invoke(decoder);decoder=null;log("PASS EXACT_SIX_FRAMES_PTS_AND_EOS");
  decoder=type.getConstructor(android.content.Context.class,Uri.class).newInstance(getTargetContext(),Uri.fromFile(source));cancel.set(true);try{type.getMethod("page",int.class,AtomicBoolean.class).invoke(decoder,4,cancel);throw new Exception("Pre-cancel ignored");}catch(InvocationTargetException expected){if(!(expected.getCause() instanceof InterruptedException))throw expected;}type.getMethod("close").invoke(decoder);decoder=null;log("PASS FRAME_PRE_CANCEL");
  Intent i=new Intent().setClassName(getTargetContext(),"io.github.himangshurajg.lecture.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=startActivitySync(i);Field f=activity.getClass().getDeclaredField("web");f.setAccessible(true);web=(WebView)f.get(activity);await("!!document.querySelector('#frames-start')","frame UI launch");js("localStorage.clear();location.reload()");Thread.sleep(2000);await("!!document.querySelector('#frames-start')","clean frame UI");
  tapWeb("[data-pick=video-file]");navigateToDownloads("frame-study.mp4");preferNativeListView();clickNative("frame-study.mp4");await("document.querySelector('#video').readyState>=2","native picked original frame fixture");
  js("document.querySelector('#all-frames').open=true;document.querySelector('#frames-start').click()");await("document.querySelectorAll('.gallery-frame').length===4","first four exact frames");js("document.querySelector('.gallery-frame').click()");check("document.querySelector('#exact-frame').open && document.querySelector('#exact-caption').textContent.includes('frame 1')","exact frame detail");js("document.querySelector('#exact-next').click()");check("document.querySelector('#exact-caption').textContent.includes('frame 2')","single frame stepping");js("document.querySelector('#close-frame').click();document.querySelector('#frames-next').click()");await("document.querySelectorAll('.gallery-frame').length===2 && document.querySelector('#gallery-state').textContent.includes('end of decoded')","remaining two frames and EOS");
  js("document.querySelector('#video').pause();document.querySelector('#video').currentTime=0.1");await("document.querySelector('#video').readyState>=2 && Math.abs(document.querySelector('#video').currentTime-0.1)<0.05","stable first decoded picture for OCR probes");
  awaitOcrProbe("eng");awaitOcrProbe("hin");awaitOcrProbe("eng+hin");
  js("document.querySelector('#ocr-language').value='eng+hin';window.confirm=()=>true;document.querySelector('#frames-scan').click()");awaitOcr("native all-six-frame offline OCR");
  check("document.querySelector('#frame-points').textContent.includes('Mass and acceleration') && document.querySelector('#frame-points').textContent.includes('बल गति बदलता है')","actual English and Hindi visible content");
  check("JSON.parse(localStorage.getItem('lecture-frame-study-v1')).checked===6 && JSON.parse(localStorage.getItem('lecture-frame-study-v1')).complete","full coverage saved without fake frames");
  tapWeb("#frames-export");clickNative("Save");await("document.querySelector('#status').textContent.includes('Notes exported')","native frame-point export");
  js("document.querySelector('#frames-scan').click()");await("!document.querySelector('#frames-stop').hidden","active all-frame analysis cancel");js("document.querySelector('#frames-stop').click()");await("document.querySelector('#ocr-state').textContent.includes('stopped') && !document.querySelector('#frames-current').disabled","cancel restores controls");
  log("ALL_FRAME_STUDY_CHECKS_PASSED");result.putString("stream","Six-frame sequential decoder and native English/Hindi OCR UI passed.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){if(e instanceof InvocationTargetException)log("CAUSE "+((InvocationTargetException)e).getCause());log("FAIL "+e);result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(decoder!=null)try{decoder.getClass().getMethod("close").invoke(decoder);}catch(Exception ignored){}}}
}
