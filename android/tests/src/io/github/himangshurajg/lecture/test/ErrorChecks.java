package io.github.himangshurajg.lecture.test;
import android.app.*;import android.os.*;import android.net.Uri;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Error handling against the installed decoder/model: no synthetic PASS shortcuts. */
public final class ErrorChecks extends Instrumentation {
 void log(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  ClassLoader cl=getTargetContext().getClassLoader();Class<?> engine=cl.loadClass("io.github.himangshurajg.lecture.OfflineTranscriber"),progress=cl.loadClass("io.github.himangshurajg.lecture.OfflineTranscriber$Progress");
  Object listener=Proxy.newProxyInstance(cl,new Class[]{progress},(p,m,a)->null);
  Object runner=engine.getConstructor(android.content.Context.class,AtomicBoolean.class,progress).newInstance(getTargetContext(),new AtomicBoolean(false),listener);
  Method transcribe=engine.getMethod("transcribe",Uri.class,String.class);
  File noAudio=new File(getTargetContext().getCacheDir(),"no-audio.mp4");
  try(InputStream in=getContext().getAssets().open("no-audio.mp4");OutputStream out=new FileOutputStream(noAudio)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  try{transcribe.invoke(runner,Uri.fromFile(noAudio),"en");throw new Exception("No-audio video unexpectedly accepted");}
  catch(InvocationTargetException e){if(!(e.getCause() instanceof IOException)||!e.getCause().getMessage().contains("no audio track"))throw e;log("PASS NO_AUDIO_ERROR");}
  try{transcribe.invoke(runner,Uri.fromFile(noAudio),"unsupported");throw new Exception("Unsupported language accepted");}
  catch(InvocationTargetException e){if(!(e.getCause() instanceof IOException)||!e.getCause().getMessage().contains("Unsupported language"))throw e;log("PASS UNSUPPORTED_LANGUAGE_ERROR");}
  File invalid=new File(getTargetContext().getCacheDir(),"invalid-video.mp4");try(FileWriter f=new FileWriter(invalid)){f.write("not a media container");}
  try{transcribe.invoke(runner,Uri.fromFile(invalid),"en");throw new Exception("Invalid media accepted");}
  catch(InvocationTargetException e){if(!(e.getCause() instanceof IOException))throw e;log("PASS INVALID_MEDIA_ERROR "+e.getCause().getClass().getName());}
  // A handled failure must not poison the same runner/model path.
  File valid=new File(getTargetContext().getCacheDir(),"valid-after-error.m4a");try(InputStream in=getContext().getAssets().open("en-clip.m4a");OutputStream out=new FileOutputStream(valid)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  String text=(String)transcribe.invoke(runner,Uri.fromFile(valid),"en");if(!text.contains("country"))throw new Exception("No valid result after handled errors");
  log("ALL_ERROR_CHECKS_PASSED");result.putString("stream","Handled no-audio, invalid media, unsupported language and recovered valid input.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){log("FAIL "+e);if(e instanceof InvocationTargetException)log("CAUSE "+((InvocationTargetException)e).getCause());result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
