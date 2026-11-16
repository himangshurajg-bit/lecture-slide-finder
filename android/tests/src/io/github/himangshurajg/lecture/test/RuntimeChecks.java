package io.github.himangshurajg.lecture.test;
import android.app.*;import android.content.*;import android.os.*;import android.webkit.*;import android.view.accessibility.*;import android.graphics.Rect;import android.media.*;import android.net.Uri;import org.json.*;import java.util.concurrent.*;
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
 AccessibilityNodeInfo findLast(AccessibilityNodeInfo n,String name){if(n==null)return null;AccessibilityNodeInfo result=null;String t=""+n.getText(),d=""+n.getContentDescription();if((name.equalsIgnoreCase(t)||name.equalsIgnoreCase(d))&&n.isVisibleToUser()){Rect b=new Rect();n.getBoundsInScreen(b);if(!b.isEmpty())result=n;}for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=findLast(n.getChild(i),name);if(found!=null)result=found;}return result;}
 AccessibilityNodeInfo findDocumentsToolbar(AccessibilityNodeInfo node){
  if(node==null)return null;
  String resourceId=node.getViewIdResourceName();
  // AOSP DocumentsUI uses com.android.documentsui, while some emulator images
  // expose the Google package. Match the stable resource suffix, not a vendor.
  if(resourceId!=null&&resourceId.endsWith(":id/toolbar"))return node;
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findDocumentsToolbar(node.getChild(i));if(found!=null)return found;}
  return null;
 }
 String documentsPickerTitle(AccessibilityNodeInfo root){
  return firstVisibleText(findDocumentsToolbar(root));
 }
 String firstVisibleText(AccessibilityNodeInfo node){
  if(node==null)return null;
  CharSequence text=node.getText();if(text!=null&&!text.toString().trim().isEmpty())return text.toString().trim();
  for(int i=0;i<node.getChildCount();i++){String found=firstVisibleText(node.getChild(i));if(found!=null)return found;}
  return null;
 }
 String visibleDownloadsFixture(AccessibilityNodeInfo root){
  if(root==null)return null;Rect window=new Rect();root.getBoundsInScreen(window);
  for(String name:new String[]{"frame-study.mp4","speech-en.mp4","speech-hi.mp4","cancel-long.mp4","lecture.mp4"}){
   AccessibilityNodeInfo node=find(root,name);if(node==null)continue;Rect bounds=new Rect();node.getBoundsInScreen(bounds);
   if(bounds.intersect(window)&&!bounds.isEmpty())return name;
  }
  return null;
 }
 void tapWeb(String selector)throws Exception{tapWeb(selector,false);}
 void tapWeb(String selector,boolean immediate)throws Exception{
  js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center',behavior:'instant'})");if(!immediate)Thread.sleep(1000);
  js("window.__testTouch=[];for(const t of ['pointerdown','pointerup','click'])document.addEventListener(t,e=>window.__testTouch.push([e.type,e.isTrusted,e.clientX,e.clientY,e.target.outerHTML?e.target.outerHTML.slice(0,150):null,!!e.target.closest("+JSONObject.quote(selector)+")]),{capture:true,once:true})");
  android.graphics.Bitmap before=immediate?null:getUiAutomation().takeScreenshot();if(before!=null){try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"web-tap.png"))){before.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}before.recycle();}
  String raw=js("JSON.stringify((()=>{const e=document.querySelector("+JSONObject.quote(selector)+");const r=e.getBoundingClientRect();const x=r.left+r.width/2,y=r.top+r.height/2;const hit=document.elementFromPoint(x,y);return [x,y,innerWidth,!!hit&&(e===hit||e.contains(hit)),e.disabled,(hit&&hit.outerHTML?hit.outerHTML.slice(0,250):null),window.visualViewport?visualViewport.offsetLeft:0,window.visualViewport?visualViewport.offsetTop:0,window.visualViewport?visualViewport.scale:1,devicePixelRatio]})())");
  log("WEB_TAP "+selector+" "+raw);
  if(raw==null)throw new Exception("WebView returned no tap geometry for "+selector);Object parsed=new JSONTokener(raw).nextValue();if(parsed instanceof JSONArray){}else if(parsed instanceof String)parsed=new JSONArray((String)parsed);else throw new Exception("Unexpected WebView tap geometry for "+selector+": "+raw);JSONArray a=(JSONArray)parsed;
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
  log("WEB_TOUCH_RECEIPT "+selector+" "+js("JSON.stringify({events:window.__testTouch,viewport:[innerWidth,innerHeight,devicePixelRatio,window.visualViewport?visualViewport.offsetTop:0,scrollY]})"));
  // Chromium's built-in media controls can consume pointer events in UA shadow
  // DOM. For video, require the actual native touch to start decoded playback,
  // rather than incorrectly requiring a document-level pointerdown receipt.
  if("#video".equals(selector))await("!document.querySelector('#video').paused && document.querySelector('#video').currentTime>0","trusted video touch starts playback");
  else {
   check("window.__testTouch.some(e=>e[0]==='pointerdown'&&e[1]&&e[5])","trusted touch reached "+selector);
   check("window.__testTouch.some(e=>e[0]==='click'&&e[1]&&e[5])","trusted click dispatched to "+selector);
  }
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
  if(list!=null){clickNative("List view");log("PICKER list view confirmed by Grid view toggle");}
 }
 boolean rootsDrawerOpen(AccessibilityNodeInfo root){return find(root,"Open from")!=null;}
 void closePickerRootsDrawer()throws Exception{
  AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
  if(!isDocumentsPicker(root)||!rootsDrawerOpen(root))return;
  String selectedTitle=documentsPickerTitle(root);
  if(selectedTitle==null||!isPickerRoot(selectedTitle))
   throw new Exception("DocumentsUI current root is unknown in the open drawer; title="+selectedTitle+" controls="+nativeTree(root,0));
  // Back exits DocumentsUI entirely on older APIs instead of dismissing its
  // roots drawer. Touch the selected visible root row to return to that folder;
  // this is the same real row interaction used when choosing Downloads.
  AccessibilityNodeInfo selectedRow=findLast(root,selectedTitle);
  if(selectedRow==null||!visiblePickerRoot(root,selectedTitle))
   throw new Exception("Selected picker root row is not visible in open drawer: "+selectedTitle+" controls="+nativeTree(root,0));
  Rect rowBounds=new Rect();selectedRow.getBoundsInScreen(rowBounds);
  if(!injectNativeTap(rowBounds))throw new Exception("Native selected-root touch refused: "+selectedTitle);
  log("PICKER_SELECTED_ROOT_TOUCHED "+selectedTitle+" bounds="+rowBounds);
  long deadline=System.currentTimeMillis()+8000;
  while(System.currentTimeMillis()<deadline){
   root=getUiAutomation().getRootInActiveWindow();
   if(isDocumentsPicker(root)&&!rootsDrawerOpen(root)&&selectedTitle.equals(documentsPickerTitle(root))){
    log("PICKER_ROOTS_DRAWER_CLOSED title="+selectedTitle+" via=selected-root-touch");return;
   }
   Thread.sleep(200);
  }
  root=getUiAutomation().getRootInActiveWindow();String title=documentsPickerTitle(root);
  capturePickerEvidence("drawer-close-selected-root");
  throw new Exception("DocumentsUI roots drawer did not close after touching selected root; expected="+selectedTitle+" actual="+title+" controls="+nativeTree(root,0));
 }
 boolean inPickerWindow(AccessibilityNodeInfo root,AccessibilityNodeInfo node){
  if(root==null||node==null)return false;Rect window=new Rect(),bounds=new Rect();root.getBoundsInScreen(window);node.getBoundsInScreen(bounds);
  return bounds.intersect(window)&&!bounds.isEmpty();
 }
 boolean visiblePickerText(AccessibilityNodeInfo root,String text){return inPickerWindow(root,find(root,text));}
 boolean visiblePickerRoot(AccessibilityNodeInfo root,String text){return rootsDrawerOpen(root)&&inPickerWindow(root,findLast(root,text));}
 boolean isPickerRoot(String name){return "Downloads".equals(name)||"Download".equals(name)||"Videos".equals(name)||"Recent".equals(name);}
 boolean atDownloadsDirectory(AccessibilityNodeInfo root){
  return isDocumentsPicker(root)&&!rootsDrawerOpen(root)&&("Downloads".equals(documentsPickerTitle(root))||"Download".equals(documentsPickerTitle(root)));
 }
 void waitForDocumentsPicker()throws Exception{
  long deadline=System.currentTimeMillis()+20000;
  while(System.currentTimeMillis()<deadline){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(isDocumentsPicker(root)){String pkg=root.getPackageName()==null?"":root.getPackageName().toString();log("DOCUMENTS_PICKER_ACTIVE package="+pkg);return;}Thread.sleep(250);}
  AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();String pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
  capturePickerEvidence("picker-not-active");throw new Exception("DocumentsUI did not become active; package="+pkg+" controls="+nativeTree(root,0));
 }
 void waitForPickerRootChoice(String... labels)throws Exception{
  long deadline=System.currentTimeMillis()+12000;
  while(System.currentTimeMillis()<deadline){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
   if(isDocumentsPicker(root)&&rootsDrawerOpen(root))for(String label:labels)if(visiblePickerRoot(root,label)){log("PICKER_ROOT_VISIBLE "+label);return;}
   Thread.sleep(300);
  }
  AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();throw new Exception("DocumentsUI roots drawer did not expose a usable source; controls="+nativeTree(root,0));
 }
 void waitForPickerFixture(String name)throws Exception{
  long deadline=System.currentTimeMillis()+15000,lastScroll=0;int scrolls=0;
  while(System.currentTimeMillis()<deadline){
   AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
   if(isDocumentsPicker(root)&&!rootsDrawerOpen(root)&&visiblePickerText(root,name)){log("PICKER_FIXTURE_VISIBLE "+name+" title="+documentsPickerTitle(root)+" scrolls="+scrolls);return;}
   // DocumentsUI exposes rows beyond the viewport in some versions, but they
   // cannot be tapped until scrolled into view. Search only by the exact seeded
   // name; selection remains a separate real native touch after this wait.
   long now=System.currentTimeMillis();
   if(isDocumentsPicker(root)&&!rootsDrawerOpen(root)&&scrolls<12&&now-lastScroll>=900&&scrollNative(root)){
    scrolls++;lastScroll=now;log("SCROLL picker list looking for "+name+" count="+scrolls);
   }
   Thread.sleep(300);
  }
  AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();capturePickerEvidence("fixture-"+name);throw new Exception("Picker did not show expected fixture "+name+" after scrolls="+scrolls+"; controls="+nativeTree(root,0));
 }
 void navigateToDownloads(String fixture)throws Exception{
  waitForDocumentsPicker();AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
  if(!rootsDrawerOpen(root)&&visiblePickerText(root,fixture)){log("PICKER_FIXTURE_VISIBLE "+fixture+" title="+documentsPickerTitle(root)+" already-open=true");return;}
  if(!rootsDrawerOpen(root)){AccessibilityNodeInfo show=find(root,"Show roots");if(show==null)throw new Exception("DocumentsUI is active but Show roots is unavailable; controls="+nativeTree(root,0));clickNative("Show roots");}
  waitForPickerRootChoice("Downloads","Download","Videos","Recent");root=getUiAutomation().getRootInActiveWindow();
  String title=documentsPickerTitle(root);
  // If the drawer is open over the requested directory, close it rather than
  // tapping the same root again. Rows behind the drawer are not a valid route.
  if("Downloads".equals(title)||"Download".equals(title)){
   closePickerRootsDrawer();waitForPickerFixture(fixture);log("PICKER_NAVIGATION_CONFIRMED "+title+" fixture="+fixture);return;
  }
  String entry=null;
  // Choose a root from the drawer itself, not matching text in the obscured
  // page. Prefer seeded Downloads; Videos and Recent are picker fallbacks.
  if(visiblePickerRoot(root,"Downloads"))entry="Downloads";
  else if(visiblePickerRoot(root,"Download"))entry="Download";
  else if(fixture.endsWith(".mp4")&&visiblePickerRoot(root,"Videos"))entry="Videos";
  else if(visiblePickerRoot(root,"Recent"))entry="Recent";
  if(entry==null)throw new Exception("DocumentsUI has no visible Videos, Recent, or Downloads source for "+fixture+"; controls="+nativeTree(root,0));
  clickNative(entry);
  long deadline=System.currentTimeMillis()+12000;
  while(System.currentTimeMillis()<deadline){
   root=getUiAutomation().getRootInActiveWindow();
   if(isDocumentsPicker(root)&&!rootsDrawerOpen(root)&&entry.equals(documentsPickerTitle(root)))break;
   if(isDocumentsPicker(root)&&rootsDrawerOpen(root)&&entry.equals(documentsPickerTitle(root))){closePickerRootsDrawer();continue;}
   Thread.sleep(250);
  }
  root=getUiAutomation().getRootInActiveWindow();
  if(!isDocumentsPicker(root)||rootsDrawerOpen(root)||!entry.equals(documentsPickerTitle(root)))throw new Exception("DocumentsUI did not navigate to "+entry+"; controls="+nativeTree(root,0));
  waitForPickerFixture(fixture);log("PICKER_NAVIGATION_CONFIRMED "+entry+" fixture="+fixture);
 }
 void verifyNativeControl(String name)throws Exception{
  if(!"Downloads".equals(name)&&!"Download".equals(name)&&!"List view".equals(name))return;
  long deadline=System.currentTimeMillis()+12000;
  while(System.currentTimeMillis()<deadline){
   AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
   // Confirm the actual toolbar directory and a visible fixture after the
   // roots drawer has closed; a matching row underneath the drawer is not enough.
   if(("Downloads".equals(name)||"Download".equals(name))&&atDownloadsDirectory(root)){log("PICKER_NAVIGATION_CONFIRMED "+documentsPickerTitle(root)+" fixture="+visibleDownloadsFixture(root));return;}
   if("List view".equals(name)&&isDocumentsPicker(root)&&find(root,"Grid view")!=null&&find(root,"List view")==null){log("PICKER_LAYOUT_CONFIRMED list");return;}
   Thread.sleep(400);
  }
  AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
  capturePickerEvidence("action-"+name);
  String tree=nativeTree(root,0);log("PICKER_ACTION_UNCONFIRMED "+name+" controls="+tree);
  throw new Exception("Picker did not confirm "+name+"; controls="+tree);
 }
 String nativeTree(AccessibilityNodeInfo n,int depth){
  if(n==null||depth>20)return "";StringBuilder b=new StringBuilder();CharSequence t=n.getText(),d=n.getContentDescription();
  if((t!=null&&!t.toString().isEmpty())||(d!=null&&!d.toString().isEmpty())){android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);b.append("["+n.getClassName()+" "+t+"|"+d+" bounds="+r+" clickable="+n.isClickable()+" enabled="+n.isEnabled()+" selected="+n.isSelected()+"] ");}
  for(int i=0;i<n.getChildCount();i++)b.append(nativeTree(n.getChild(i),depth+1));return b.toString();
 }
 void logExtractorPts(Uri uri,String label,int limit)throws Exception{
  MediaExtractor probe=new MediaExtractor();
  try{
   probe.setDataSource(getTargetContext(),uri,null);int track=-1;MediaFormat format=null;
   for(int i=0;i<probe.getTrackCount();i++){MediaFormat candidate=probe.getTrackFormat(i);String mime=candidate.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("video/")){track=i;format=candidate;break;}}
   if(track<0)throw new Exception("No video track in PTS probe: "+label);probe.selectTrack(track);
   log("VIDEO_TRACK_PROBE label="+label+" api="+Build.VERSION.SDK_INT+" track="+track+" format="+format);
   for(int i=0;i<limit&&probe.getSampleTrackIndex()>=0;i++){log("INPUT_SAMPLE_PTS label="+label+" index="+i+" pts_us="+probe.getSampleTime()+" flags="+probe.getSampleFlags());if(!probe.advance())break;}
  }finally{probe.release();}
 }
 boolean injectNativeTap(Rect bounds){
  if(bounds==null||bounds.isEmpty())return false;long t=SystemClock.uptimeMillis();
  android.view.MotionEvent down=android.view.MotionEvent.obtain(t,t,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
  android.view.MotionEvent up=android.view.MotionEvent.obtain(t,t+90,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
  down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
  try{return getUiAutomation().injectInputEvent(down,true)&&getUiAutomation().injectInputEvent(up,true);}finally{down.recycle();up.recycle();}
 }
 boolean isDocumentsPicker(AccessibilityNodeInfo root){return root!=null&&root.getPackageName()!=null&&root.getPackageName().toString().toLowerCase().contains("documentsui");}
 void capturePickerEvidence(String name){
  try{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image==null)return;String safe=name.replaceAll("[^A-Za-z0-9_-]","_");java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir(null),"picker-capture-"+safe+".png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}log("PICKER_SCREENSHOT "+file.getName());}catch(Exception e){log("PICKER_SCREENSHOT_ERROR "+e.getClass().getSimpleName()+": "+e.getMessage());}
 }
 void afterNativeFileClick(String name)throws Exception{
  if(!name.endsWith(".mp4")&&!name.endsWith(".srt"))return;
  Thread.sleep(1200);AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
  String pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
  if(!isDocumentsPicker(root)){log("PICKER_CLOSED_AFTER_FILE_CLICK "+name+" package="+pkg);return;}
  log("PICKER_REMAINS_AFTER_FILE_CLICK "+name+" controls="+nativeTree(root,0));capturePickerEvidence(name);
  AccessibilityNodeInfo confirm=find(root,"Open");if(confirm==null)confirm=find(root,"Select");
  if(confirm==null){
   // Recent DocumentsUI versions expose the filename inside a clickable
   // item_root. Activating that row is more reliable than tapping the child
   // TextView bounds (which can be clipped or inert in the system picker).
   AccessibilityNodeInfo selected=find(root,name),row=selected;
   while(row!=null&&!row.isClickable())row=row.getParent();
   if(row!=null&&row.isEnabled()){
    Rect rowBounds=new Rect();row.getBoundsInScreen(rowBounds);
    boolean activated=row.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    Thread.sleep(1200);root=getUiAutomation().getRootInActiveWindow();pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
    log("PICKER_ROW_ACTION "+name+" activated="+activated+" bounds="+rowBounds+" package="+pkg);
    if(!isDocumentsPicker(root))return;
    confirm=find(root,"Open");if(confirm==null)confirm=find(root,"Select");
    if(confirm==null){
     selected=find(root,name);row=selected;while(row!=null&&!row.isClickable())row=row.getParent();
     Rect rowTap=new Rect();if(row!=null)row.getBoundsInScreen(rowTap);
     boolean tapped=injectNativeTap(rowTap);Thread.sleep(1200);root=getUiAutomation().getRootInActiveWindow();pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
     log("PICKER_ROW_TOUCH "+name+" tapped="+tapped+" bounds="+rowTap+" package="+pkg);
     if(!isDocumentsPicker(root))return;
     confirm=find(root,"Open");if(confirm==null)confirm=find(root,"Select");
    }
   }
   if(confirm==null){
    // Fall back to the visible filename itself on older DocumentsUI variants.
    selected=find(root,name);
    if(selected!=null){Rect labelBounds=new Rect();selected.getBoundsInScreen(labelBounds);boolean tapped=injectNativeTap(labelBounds);
     Thread.sleep(1200);root=getUiAutomation().getRootInActiveWindow();pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
     log("PICKER_COORDINATE_FILE_TAP "+name+" tapped="+tapped+" package="+pkg);
     if(!isDocumentsPicker(root))return;
     confirm=find(root,"Open");if(confirm==null)confirm=find(root,"Select");
    }
   }
   if(confirm==null){String tree=nativeTree(root,0);log("PICKER_NO_OPEN_OR_SELECT_BUTTON "+name+" file="+tree);throw new Exception("Picker remained open after "+name+"; no Open/Select control; "+tree);}
  }
  AccessibilityNodeInfo button=confirm;while(button!=null&&!button.isClickable())button=button.getParent();
  if(button==null||!button.isEnabled()){log("PICKER_CONFIRM_DISABLED "+name);throw new Exception("Picker confirmation is disabled for "+name);}
  boolean clicked=button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
  if(!clicked){Rect b=new Rect();button.getBoundsInScreen(b);if(!b.isEmpty()){long t=SystemClock.uptimeMillis();android.view.MotionEvent d=android.view.MotionEvent.obtain(t,t,0,b.centerX(),b.centerY(),0),u=android.view.MotionEvent.obtain(t,t+80,1,b.centerX(),b.centerY(),0);clicked=getUiAutomation().injectInputEvent(d,true)&&getUiAutomation().injectInputEvent(u,true);d.recycle();u.recycle();}}
  if(!clicked)throw new Exception("Picker confirmation tap failed for "+name);
  Thread.sleep(1200);root=getUiAutomation().getRootInActiveWindow();pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
  if(isDocumentsPicker(root)){String tree=nativeTree(root,0);capturePickerEvidence(name+"-confirm");log("PICKER_CONFIRM_DID_NOT_CLOSE "+name+" controls="+tree);throw new Exception("Picker confirmation did not close for "+name+"; "+tree);}
  log("CLICK picker confirmation for "+name+" package="+pkg);
 }
 boolean dismissInputMethod(){
  for(AccessibilityWindowInfo window:getUiAutomation().getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){
   boolean dismissed=getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
   log("SOFT_KEYBOARD_PRESENT before native Save; backAction="+dismissed);
   return true;
  }
  return false;
 }
 void clickNative(String name)throws Exception{
  long deadline=System.currentTimeMillis()+90000;int scrolls=0;boolean keyboardDismissed=false;
  while(System.currentTimeMillis()<deadline){
   if("Save".equals(name)&&!keyboardDismissed&&dismissInputMethod()){keyboardDismissed=true;Thread.sleep(500);continue;}
   AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
   // Some picker versions open directly in Downloads. Confirm the actual
   // toolbar title and a visible known fixture; don't click a non-clickable
   // title or weaken the later real file-selection check.
   if(("Downloads".equals(name)||"Download".equals(name))&&atDownloadsDirectory(root)){
    log("PICKER_NAVIGATION_CONFIRMED "+documentsPickerTitle(root)+" fixture="+visibleDownloadsFixture(root)+" already-open=true");return;
   }
   AccessibilityNodeInfo n=find(root,name);
   // The toolbar title and drawer row may share a name. When the roots drawer
   // is open, choose its later entry rather than treating the toolbar as a tap.
   if(isPickerRoot(name)&&isDocumentsPicker(root)&&rootsDrawerOpen(root)){
    AccessibilityNodeInfo drawerEntry=findLast(root,name);if(drawerEntry!=null)n=drawerEntry;
   }
   if(n!=null){
    android.graphics.Rect bounds=new android.graphics.Rect(),viewport=new android.graphics.Rect();n.getBoundsInScreen(bounds);if(root!=null)root.getBoundsInScreen(viewport);
    if(bounds.intersect(viewport) && !bounds.isEmpty()){
     // Keep the exact file label and its clickable ancestor: old DocumentsUI
     // versions need a real touch at the label, while toolbar controls use accessibility.
     AccessibilityNodeInfo target=n;
     while(target!=null && !target.isClickable())target=target.getParent();
     boolean fileItem=name.endsWith(".mp4")||name.endsWith(".srt");
     boolean pickerRootItem=isPickerRoot(name)&&isDocumentsPicker(root)&&rootsDrawerOpen(root);
     if(fileItem){
      Rect labelBounds=new Rect();n.getBoundsInScreen(labelBounds);
      if(injectNativeTap(labelBounds)){afterNativeFileClick(name);log("TOUCH native file label "+name);return;}
     }else if(pickerRootItem){
      // ACTION_CLICK can report success for the obscured toolbar label on old
      // DocumentsUI. A real touch on the drawer's own visible row is required.
      Rect rowBounds=new Rect();n.getBoundsInScreen(rowBounds);
      if(!injectNativeTap(rowBounds))throw new Exception("Native drawer-row touch refused: "+name);
      long navDeadline=System.currentTimeMillis()+8000;
      while(System.currentTimeMillis()<navDeadline){
       AccessibilityNodeInfo current=getUiAutomation().getRootInActiveWindow();
       if(isDocumentsPicker(current)&&name.equals(documentsPickerTitle(current))){
        if(rootsDrawerOpen(current))closePickerRootsDrawer();
        current=getUiAutomation().getRootInActiveWindow();
        if(isDocumentsPicker(current)&&!rootsDrawerOpen(current)&&name.equals(documentsPickerTitle(current))){
         log("PICKER_ROOT_ROW_CONFIRMED "+name+" via=touch bounds="+rowBounds);return;
        }
       }
       Thread.sleep(250);
      }
      throw new Exception("DocumentsUI did not confirm drawer root after real row touch: "+name+" controls="+nativeTree(getUiAutomation().getRootInActiveWindow(),0));
     }else if(target!=null && target.isEnabled() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
      verifyNativeControl(name);afterNativeFileClick(name);log("CLICK accessible control "+name);return;
     }
     Rect tapBounds=new Rect();(target==null?n:target).getBoundsInScreen(tapBounds);
     if(!tapBounds.intersect(viewport)||tapBounds.isEmpty())tapBounds.set(bounds);
     long t=SystemClock.uptimeMillis();android.view.MotionEvent d=android.view.MotionEvent.obtain(t,t,0,tapBounds.centerX(),tapBounds.centerY(),0),u=android.view.MotionEvent.obtain(t,t+80,1,tapBounds.centerX(),tapBounds.centerY(),0);
     boolean sent=getUiAutomation().injectInputEvent(d,true) && getUiAutomation().injectInputEvent(u,true);d.recycle();u.recycle();
     if(!sent)throw new Exception("Native touch injection refused: "+name);
     Thread.sleep(1000);verifyNativeControl(name);afterNativeFileClick(name);log("CLICK "+name);return;
    }
   }
   AccessibilityNodeInfo wait=find(root,"Wait");if(wait!=null){wait.performAction(AccessibilityNodeInfo.ACTION_CLICK);log("ENV System UI wait dialog dismissed");}
   else if(scrolls<12 && scrollNative(root)){scrolls++;log("SCROLL to native control "+name);}
   Thread.sleep(1000);
  }
  if("Save".equals(name))capturePickerEvidence("missing-control-Save");
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
  log("SELECT_VIDEO"); navigateToDownloads("lecture.mp4"); clickNative("lecture.mp4");
  await("document.querySelector('#video').readyState>=1","native picked video metadata");
  tapWeb("#video"); await("document.querySelector('#video').currentTime>0 && document.querySelector('#video').videoWidth>0","native video decode and playback advances");js("document.querySelector('#video').pause()");
  tapWeb("[data-pick=transcript-file]");Thread.sleep(2000);log("SELECT_TRANSCRIPT");navigateToDownloads("lecture.srt");clickNative("lecture.srt");
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
