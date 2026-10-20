package io.github.himangshurajg.lecture.test;
import android.app.*;import android.os.*;import android.net.Uri;import java.io.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;
/** One-minute synthetic stress pass, not lecture-domain/media compatibility qualification. */
public final class FrameStressChecks extends RuntimeChecks {
 @Override public void onStart(){Bundle result=new Bundle();Object decoder=null;try{
  File source=new File(getTargetContext().getCacheDir(),"frame-stress.mp4");
  try(InputStream in=getContext().getAssets().open("frame-stress.mp4");OutputStream out=new FileOutputStream(source)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
  Class<?> type=getTargetContext().getClassLoader().loadClass("io.github.himangshurajg.lecture.FrameDecoder");
  decoder=type.getConstructor(android.content.Context.class,Uri.class).newInstance(getTargetContext(),Uri.fromFile(source));
  AtomicBoolean cancel=new AtomicBoolean(false);int count=0,maxPss=0,maxBatchBytes=0;boolean done=false;long start=SystemClock.elapsedRealtime();
  while(!done){JSONObject batch=(JSONObject)type.getMethod("page",int.class,AtomicBoolean.class).invoke(decoder,4,cancel);JSONArray frames=batch.getJSONArray("frames");if(frames.length()>4)throw new Exception("Unbounded batch");maxBatchBytes=Math.max(maxBatchBytes,batch.toString().length());
   for(int i=0;i<frames.length();i++){JSONObject frame=frames.getJSONObject(i);if(frame.getInt("index")!=count||Math.abs(frame.getDouble("time")-count/15.0)>.001)throw new Exception("Missing or reordered frame "+count);count++;}done=batch.getBoolean("done");if(count>900)throw new Exception("Duplicate frame");
   if(count%100==0){android.os.Debug.MemoryInfo mem=new android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(mem);maxPss=Math.max(maxPss,mem.getTotalPss());log("STRESS_PROGRESS frames="+count+" pss_kib="+mem.getTotalPss());}
  }
  if(count!=900)throw new Exception("Expected 900 frames, got "+count);
  log("PASS ONE_MINUTE_900_EXACT_FRAMES elapsed_ms="+(SystemClock.elapsedRealtime()-start)+" observed_peak_pss_kib="+maxPss+" max_batch_chars="+maxBatchBytes);
  type.getMethod("close").invoke(decoder);decoder=null;
  log("ALL_FRAME_STRESS_CHECKS_PASSED");result.putString("stream","One-minute 900-frame exact traversal passed; sampled PSS is emulator evidence, not a device requirement.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){log("FAIL "+e);result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(decoder!=null)try{decoder.getClass().getMethod("close").invoke(decoder);}catch(Exception ignored){}}}
}
