package io.github.himangshurajg.lecture.test;
import android.app.*;import android.content.*;import android.content.pm.ActivityInfo;import android.os.*;import android.webkit.*;
/** Native lifecycle and cancellation checks, separate from happy-path provider workflow. */
public final class EdgeChecks extends RuntimeChecks {
 String segment="all";
 @Override public void onCreate(Bundle args){segment=args.getString("segment","all");super.onCreate(args);}
 void connect()throws Exception { Intent i=new Intent().setClassName(getTargetContext(),"io.github.himangshurajg.lecture.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=startActivitySync(i);java.lang.reflect.Field f=activity.getClass().getDeclaredField("web");f.setAccessible(true);web=(WebView)f.get(activity);await("!!document.querySelector('#demo')","edge UI launched"); }
 void back()throws Exception{try(android.os.ParcelFileDescriptor command=getUiAutomation().executeShellCommand("input keyevent 4");java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)){while(input.read()!=-1){}}Thread.sleep(2000);}
 void cancelDocument()throws Exception {
  // DocumentsUI may consume the first Back to hide its filename keyboard.
  // A second Back is appropriate only while that external picker still owns the window.
  android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
  if(root==null || root.getPackageName()==null || !root.getPackageName().toString().contains("documentsui"))throw new Exception("Document picker did not open");
  back();root=getUiAutomation().getRootInActiveWindow();
  if(root!=null && root.getPackageName()!=null && root.getPackageName().toString().contains("documentsui"))back();
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
 connect();js("localStorage.clear();location.reload()");Thread.sleep(2000);await("!!document.querySelector('#demo')","edge clean launch");
 js("document.querySelector('#demo').click();document.querySelector('.save').click()");check("document.querySelector('#notes-count').textContent==='1'","edge note seeded");
 if(!"cancel".equals(segment)){runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(3000);await("innerWidth>innerHeight && document.querySelector('#notes-count').textContent==='1'","landscape preserves notes");
 runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(3000);await("innerHeight>innerWidth && document.querySelector('#notes-count').textContent==='1'","portrait preserves notes");}
 tapWeb("[data-pick=transcript-file]");Thread.sleep(2000);back();check("document.querySelector('#notes-count').textContent==='1'","picker cancellation preserves notes");
 tapWeb("#export");Thread.sleep(2000);cancelDocument();await("document.querySelector('#status').textContent.includes('Export cancelled')","export cancellation reported");check("document.querySelector('#notes-count').textContent==='1'","export cancellation preserves notes");
 back();clickNative("Keep studying");check("document.querySelector('#notes-count').textContent==='1'","back cancellation preserves session");
 runOnMainSync(()->activity.finish());Thread.sleep(2000);connect();check("document.querySelector('#notes-count').textContent==='1' && document.querySelector('#cue-count').textContent==='3 PASSAGES'","Activity close and reopen recovers session");
 js("localStorage.setItem('lecture-session-v1','broken');location.reload()");Thread.sleep(2000);await("document.querySelector('#status').textContent.includes('could not be restored')","corrupt saved state handled");
 js("document.querySelector('#demo').click()");check("document.querySelector('#cue-count').textContent==='3 PASSAGES'","usable after corrupt state");
 log("ALL_EDGE_CHECKS_PASSED");result.putString("stream","Native edge checks passed\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){log("FAIL "+e.toString());result.putString("stream",e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
