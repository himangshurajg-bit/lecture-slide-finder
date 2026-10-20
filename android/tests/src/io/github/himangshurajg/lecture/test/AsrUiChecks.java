package io.github.himangshurajg.lecture.test;
import android.app.*;import android.content.*;import android.os.*;import android.webkit.*;
/** Real installed-app picker -> on-device speech -> visible timestamped results. */
public final class AsrUiChecks extends RuntimeChecks {
 String language="en";
 @Override public void onCreate(Bundle args){language=args.getString("language","en");super.onCreate(args);}
 @Override void await(String code,String name)throws Exception{log("AWAIT "+name);long deadline=System.currentTimeMillis()+300000;while(System.currentTimeMillis()<deadline){if("true".equals(js(code))){log("PASS "+name);return;}Thread.sleep(1000);}throw new Exception("Timeout: "+name);}
 @Override public void onStart(){Bundle result=new Bundle();try {
  android.accessibilityservice.AccessibilityServiceInfo asi=getUiAutomation().getServiceInfo();asi.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;getUiAutomation().setServiceInfo(asi);
  Intent i=new Intent().setClassName(getTargetContext(),"io.github.himangshurajg.lecture.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=startActivitySync(i);java.lang.reflect.Field f=activity.getClass().getDeclaredField("web");f.setAccessible(true);web=(WebView)f.get(activity);
  await("!!document.querySelector('#demo')","speech UI launches");js("localStorage.clear();location.reload()");Thread.sleep(2000);await("!!document.querySelector('#demo')","speech clean state");
  check("document.querySelector('#asr-generate').disabled","speech requires chosen media");
  tapWeb("[data-pick=video-file]");clickNative("Show roots");clickNative("Downloads");clickNative("speech-"+language+".mp4");
  await("document.querySelector('#video').readyState>=1 && !document.querySelector('#asr-generate').disabled","chosen speech video accepted");
  js("document.querySelector('#asr-language').value='"+language+"'");tapWeb("#asr-generate");clickNative("Transcribe");
  await("document.querySelectorAll('.cue').length>0 && document.querySelector('#asr-status').textContent.includes('100%')","UI speech completion");
  String text=js("document.querySelector('#results').textContent");log("UI_RESULT "+language+" "+text);
  if("en".equals(language))check("document.querySelector('#results').textContent.toLowerCase().includes('country')","visible English speech keyword");
  else check("document.querySelector('#results').textContent.includes('सागर')","visible Hindi speech keyword (pipeline, not accuracy)");
  js("document.querySelector('.time').click()");await("!document.querySelector('#frame-box').hidden","speech cue seeks and captures actual video");
  js("document.querySelector('.save').click()");check("document.querySelector('#notes-count').textContent==='1'","speech passage saved");
  tapWeb("#asr-generate");clickNative("Transcribe");await("!document.querySelector('#asr-cancel').hidden","speech cancel visible");tapWeb("#asr-cancel");
  await("document.querySelector('#asr-status').textContent.includes('cancelled')","active UI speech cancellation");
  check("document.querySelector('#notes-count').textContent==='1' && document.querySelectorAll('.cue').length>0","speech cancel preserves previous notes and result");
  log("ALL_ASR_UI_CHECKS_PASSED "+language);result.putString("stream","Installed app speech UI pipeline passed; accuracy separately assessed.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){log("FAIL "+e);result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
