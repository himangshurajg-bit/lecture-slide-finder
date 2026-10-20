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
 void tapWeb(String selector)throws Exception{tapWeb(selector,false);}
 void tapWeb(String selector,boolean immediate)throws Exception{
  js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center',behavior:'instant'})");if(!immediate)Thread.sleep(1000);
  js("window.__testTouch=[];for(const t of ['pointerdown','pointerup','click'])document.addEventListener(t,e=>window.__testTouch.push([e.type,e.isTrusted,e.clientX,e.clientY,e.target.outerHTML?.slice(0,150),!!e.target.closest("+JSONObject.quote(selector)+")]),{capture:true,once:true})");
  android.graphics.Bitmap before=immediate?null:getUiAutomation().takeScreenshot();if(before!=null){try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"web-tap.png"))){before.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}before.recycle();}
  String raw=js("JSON.stringify((()=>{const e=document.querySelector("+JSONObject.quote(selector)+");const r=e.getBoundingClientRect();const x=r.left+r.width/2,y=r.top+r.height/2;const hit=document.elementFromPoint(x,y);return [x,y,innerWidth,!!hit&&(e===hit||e.contains(hit)),e.disabled,hit?.outerHTML.slice(0,250),visualViewport?.offsetLeft||0,visualViewport?.offsetTop||0,visualViewport?.scale||1,devicePixelRatio]})())");
  log("WEB_TAP "+selector+" "+raw);
  JSONArray a=new JSONArray((String)new JSONTokener(raw).nextValue());
  if(!a.getBoolean(3)||a.optBoolean(4))throw new Exception("Web control obscured or disabled: "+selector);
  final int[] origin=new int[2];runOnMainSync(()->web.getLocationOnScreen(origin));
  // CSS client coordinates are layout-viewport coordinates. WebView's actual
  // page scale includes density/zoom, while innerWidth can exceed the visible
  // viewport. Remove visualViewport pan before scaling. WebView padding must
  // NOT be added: observed trusted receipts include it in the viewport transform.
  final float[] pageScale={0};runOnMainSync(()->pageScale[0]=web.getScale());
  float scale=pageScale[0],x=origin[0]+((float)a.getDouble(0)-(float)a.getDouble(6))*scale,y=origin[1]+((float)a.getDouble(1)-(float)a.getDouble(7))*scale;
  if(!(scale>0))throw new Exception("Invalid WebView page scale");
  log("WEB_SCREEN_TAP x="+x+" y="+y+" scale="+scale+" padding="+web.getPaddingLeft()+","+web.getPaddingTop()+","+web.getPaddingRight()+","+web.getPaddingBottom());
  // Dispatch through the OS input pipeline, with real down/up duration. Direct
  // WebView dispatch can race renderer/compositor scrolling and gives no receipt.
  long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0);
  down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
  boolean sent=getUiAutomation().injectInputEvent(down,true);down.recycle();Thread.sleep(100);
  android.view.MotionEvent up=android.view.MotionEvent.obtain(now,SystemClock.uptimeMillis(),1,x,y,0);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
  sent=getUiAutomation().injectInputEvent(up,true)&&sent;up.recycle();
  if(!sent)throw new Exception("Web touch injection refused: "+selector);
  if(!immediate)Thread.sleep(1500);
  log("WEB_TOUCH_RECEIPT "+selector+" "+js("JSON.stringify({events:window.__testTouch,viewport:[innerWidth,innerHeight,devicePixelRatio,visualViewport?.offsetTop,scrollY]})"));
  // Chromium's built-in media controls can consume pointer events in UA shadow
  // DOM. For video, require the actual native touch to start decoded playback,
  // rather than incorrectly requiring a document-level pointerdown receipt.
  if("#video".equals(selector))await("!document.querySelector('#video').paused && document.querySelector('#video').currentTime>0","trusted video touch starts playback");
  else check("window.__testTouch.some(e=>e[0]==='pointerdown'&&e[1]&&e[5])","trusted touch reached "+selector);
 }
 boolean scrollNative(AccessibilityNodeInfo n){
  if(n==null)return false;
  // DocumentsUI wraps dir_list in a scrollable container. Scroll the actual
  // child file list first: the wrapper can report success without moving rows.
  for(int i=0;i<n.getChildCount();i++)if(scrollNative(n.getChild(i)))return true;
  return n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
 }
 void preferNativeListView()throws Exception{
  AccessibilityNodeInfo list=find(getUiAutomation().getRootInActiveWindow(),"List view");
  if(list!=null){clickNative("List view");log("PICKER list view avoids clipped off-screen grid filename labels");}
 }
 void clickNative(String name)throws Exception{
  long deadline=System.currentTimeMillis()+90000;int scrolls=0;
  while(System.currentTimeMillis()<deadline){
   AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow(),n=find(root,name);
   if(n!=null){
    android.graphics.Rect bounds=new android.graphics.Rect(),viewport=new android.graphics.Rect();n.getBoundsInScreen(bounds);if(root!=null)root.getBoundsInScreen(viewport);
    if(bounds.intersect(viewport) && !bounds.isEmpty()){
     // DocumentsUI filename labels are not clickable; activate their real enabled
     // card ancestor through Android accessibility, avoiding moving scroll coordinates.
     AccessibilityNodeInfo target=n;
     while(target!=null && !target.isClickable())target=target.getParent();
     if(target!=null && target.isEnabled() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
      Thread.sleep(1500);log("CLICK accessible control "+name);return;
     }
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
