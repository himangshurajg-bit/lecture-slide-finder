package io.github.himangshurajg.lecture.test;
import android.app.*;import android.os.*;import android.net.Uri;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;
/** Exercises actual target APK model/decoder/JNI, with public speech fixtures only. */
public final class AsrChecks extends Instrumentation {
 String language="en",selectedFixture=null;
 @Override public void onCreate(Bundle args){language=args.getString("language","en");selectedFixture=args.getString("fixture");super.onCreate(args);start();}
 void log(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
 @Override public void onStart(){Bundle result=new Bundle();try{
  String fixture=selectedFixture==null?language+"-clip.m4a":selectedFixture;
  File source=new File(getTargetContext().getCacheDir(),"asr-fixture.m4a");
  try(InputStream in=getContext().getAssets().open(fixture);OutputStream out=new FileOutputStream(source)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  ClassLoader cl=getTargetContext().getClassLoader();Class<?> engine=cl.loadClass("io.github.himangshurajg.lecture.OfflineTranscriber"),progress=cl.loadClass("io.github.himangshurajg.lecture.OfflineTranscriber$Progress");
  Object listener=Proxy.newProxyInstance(cl,new Class[]{progress},(proxy,method,args)->{if(args!=null)log("PROGRESS "+args[0]+" percent="+args[1]);return null;});
  AtomicBoolean cancel=new AtomicBoolean(false);Object runner=engine.getConstructor(android.content.Context.class,AtomicBoolean.class,progress).newInstance(getTargetContext(),cancel,listener);
  long start=SystemClock.elapsedRealtime();String output=(String)engine.getMethod("transcribe",Uri.class,String.class).invoke(runner,Uri.fromFile(source),language);
  JSONArray cues=new JSONArray(output);if(cues.length()==0)throw new Exception("No cues");double last=-1;StringBuilder text=new StringBuilder();
  for(int n=0;n<cues.length();n++){JSONObject c=cues.getJSONObject(n);double from=c.getDouble("start"),to=c.getDouble("end");if(from<0||to<=from||from<last||Double.isNaN(from)||Double.isNaN(to))throw new Exception("Invalid timing");last=from;text.append(c.getString("text")).append(" ");}
  log("RESULT "+language+" elapsed_ms="+(SystemClock.elapsedRealtime()-start)+" json="+output);
  if("en".equals(language)&&!text.toString().toLowerCase().contains("country"))throw new Exception("English fixture key word missing");
  if("hi".equals(language)&&!text.toString().contains("सागर"))throw new Exception("Hindi fixture key word missing");
  cancel.set(true);try{engine.getMethod("transcribe",Uri.class,String.class).invoke(runner,Uri.fromFile(source),language);throw new Exception("Pre-cancel ignored");}catch(InvocationTargetException expected){if(!(expected.getCause() instanceof InterruptedException))throw expected;}
  log("PASS NATIVE_ASR_PIPELINE_AND_PRE_CANCEL "+language);result.putString("stream","Native ASR decoder/JNI passed; quality remains separately assessed.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){log("FAIL "+error);if(error instanceof InvocationTargetException)log("CAUSE "+((InvocationTargetException)error).getCause());result.putString("stream",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
