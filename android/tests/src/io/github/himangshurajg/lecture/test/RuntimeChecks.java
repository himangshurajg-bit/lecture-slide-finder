package io.github.himangshurajg.lecture.test;
import android.app.*;import android.content.*;import android.os.*;import android.webkit.*;import android.view.accessibility.*;import org.json.*;import java.util.concurrent.*;
/** Separate same-signed test instrumentation; never bundled in the distributed app. */
public class RuntimeChecks extends Instrumentation {
 Activity activity; WebView web;
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 void log(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
 String js(String code)throws Exception { final String[] out={null};CountDownLatch done=new CountDownLatch(1);runOnMainSync(()->web.evaluateJavascript(code,value->{out[0]=value;done.countDown();}));if(!done.await(90,TimeUnit.SECONDS))throw new Exception("JS timeout: "+code);return out[0]; }
 void fire(String code){runOnMainSync(()->web.evaluateJavascript(code,null));}
 void check(String code,String name)throws Exception {log("CHECK "+name);String r=js(code);if(!"true".equals(r))throw new Exception(name+": "+r);log("PASS "+name);}
 void await(String code,String name)throws Exception{log("AWAIT "+name);long deadline=System.currentTimeMillis()+180000;while(System.currentTimeMillis()<deadline){if("true".equals(js(code))){log("PASS "+name);return;}Thread.sleep(1000);}throw new Exception("Timeout: "+name);}
 AccessibilityNodeInfo find(AccessibilityNodeInfo n,String name){if(n==null)return null;String t=""+n.getText(),d=""+n.getContentDescription();if((name.equalsIgnoreCase(t)||name.equalsIgnoreCase(d)) && n.isVisibleToUser()){android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);if(!b.isEmpty())return n;}for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=find(n.getChild(i),name);if(found!=null)return found;}return null;}
 void tapWeb(String selector)throws Exception{
 js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center',behavior:'instant'})");Thread.sleep(500);
 String raw=js("JSON.stringify((()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,innerWidth]})())");
 JSONArray a=new JSONArray((String)new JSONTokener(raw).nextValue());final int[] origin=new int[2];runOnMainSync(()->web.getLocationOnScreen(origin));
 float scale=web.getWidth()/(float)a.getDouble(2),x=origin[0]+(float)a.getDouble(0)*scale,y=origin[1]+(float)a.getDouble(1)*scale;
 long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),up=android.view.MotionEvent.obtain(now,now+80,1,x,y,0);
 runOnMainSync(()->{long t=SystemClock.uptimeMillis();android.view.MotionEvent d=android.view.MotionEvent.obtain(t,t,0,x-origin[0],y-origin[1],0);web.dispatchTouchEvent(d);d.recycle();android.view.MotionEvent u=android.view.MotionEvent.obtain(t,t+50,1,x-origin[0],y-origin[1],0);web.dispatchTouchEvent(u);u.recycle();});down.recycle();up.recycle();Thread.sleep(1000);
 }
 boolean scrollNative(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scrollNative(n.getChild(i)))return true;return false;}
 void clickNative(String name)throws Exception{
  long deadline=System.currentTimeMillis()+90000;int scrolls=0;
  while(System.currentTimeMillis()<deadline){
   AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow(),n=find(root,name);
   if(n!=null){
    android.graphics.Rect bounds=new android.graphics.Rect(),viewport=new android.graphics.Rect();n.getBoundsInScreen(bounds);if(root!=null)root.getBoundsInScreen(viewport);
    if(bounds.intersect(viewport) && !bounds.isEmpty()){
     long t=SystemClock.uptimeMillis();android.view.MotionEvent d=android.view.MotionEvent.obtain(t,t,0,bounds.centerX(),bounds.centerY(),0),u=android.view.MotionEvent.obtain(t,t+80,1,bounds.centerX(),bounds.centerY(),0);
     boolean sent=getUiAutomation().injectInputEvent(d,true) && getUiAutomation().injectInputEvent(u,true);d.recycle();u.recycle();
     if(!sent)throw new Exception("Native touch injection refused: "+name);
     Thread.sleep(1000);log("CLICK "+name);return;
    }
   }
   AccessibilityNodeInfo wait=find(root,"Wait");if(wait!=null){wait.performAction(AccessibilityNodeInfo.ACTION_CLICK);log("ENV System UI wait dialog dismissed");}
   else if(scrolls<12 && scrollNative(root)){scrolls++;log("SCROLL to native control "+name);}
   Thread.sleep(1000);
  }
  log("ROOT "+getUiAutomation().getRootInActiveWindow());throw new Exception("Visible native control not found: "+name);
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  android.accessibilityservice.AccessibilityServiceInfo asi=getUiAutomation().getServiceInfo();asi.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;getUiAutomation().setServiceInfo(asi);
  Intent i=new Intent().setClassName(getTargetContext(),"io.github.himangshurajg.lecture.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  log("STARTING_ACTIVITY"); activity=startActivitySync(i);java.lang.reflect.Field f=activity.getClass().getDeclaredField("web");f.setAccessible(true);web=(WebView)f.get(activity);
  await("!!document.querySelector('#demo')","offline bundled UI launches");
  // Core native workflow segment; launch/persistence/style tested separately.
  js("localStorage.clear();location.reload()");Thread.sleep(3000);await("!!document.querySelector('#demo')","clean session ready");
  tapWeb("[data-pick=video-file]");Thread.sleep(5000);
  log("PICKER_READY");
  // A human-observed adb touch driver selects the fixture through the real OS picker.
  log("SELECT_VIDEO"); clickNative("Show roots"); clickNative("Downloads"); clickNative("lecture.mp4");
  await("document.querySelector('#video').readyState>=1","native picked video metadata");
  tapWeb("#video"); await("document.querySelector('#video').currentTime>0 && document.querySelector('#video').videoWidth>0","native video decode and playback advances");js("document.querySelector('#video').pause()");
  tapWeb("[data-pick=transcript-file]");Thread.sleep(2000);log("SELECT_TRANSCRIPT");clickNative("Show roots");clickNative("Downloads");clickNative("lecture.srt");
  await("document.querySelector('#cue-count').textContent==='3 PASSAGES'","native transcript import");
  js("const q=document.querySelector('#query');q.value='Mass and';q.dispatchEvent(new Event('input'))");await("document.querySelectorAll('.cue').length===1","search imported transcript");
  js("document.querySelector('.time').click()");await("!document.querySelector('#frame-box').hidden","decoded matching frame");
  check("Math.abs(document.querySelector('#video').currentTime-4)<0.5","timestamp seek");
  js("document.querySelector('.save').click()");tapWeb("#export");Thread.sleep(3000);log("SAVE_MARKDOWN");clickNative("Save");
  await("document.querySelector('#status').textContent.includes('Notes exported')","native Markdown save accepted");
  check("document.querySelector('#notes-count').textContent==='1'","notes retained after export");
  log("ALL_CHECKS_PASSED");result.putString("stream","Runtime workflow passed\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){log("FAIL "+e.toString());result.putString("stream",e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
