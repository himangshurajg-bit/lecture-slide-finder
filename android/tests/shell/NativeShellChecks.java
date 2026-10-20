package io.github.himangshurajg.lecture;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.SystemClock;
import org.json.JSONArray;

/** Shell-only isolated Android JNI check; not an end-to-end installed-app test. */
public final class NativeShellChecks {
 public static void main(String[] args)throws Exception {
  long start=SystemClock.elapsedRealtime();
  long model=WhisperEngine.open(args[0]);
  System.out.println("PASS ANDROID_JNI_MODEL_LOAD elapsed_ms="+(SystemClock.elapsedRealtime()-start));System.out.flush();
  try {
   AtomicBoolean cancel=new AtomicBoolean(false);
   Thread trigger=new Thread(()->{try{Thread.sleep(500);}catch(InterruptedException ignored){}cancel.set(true);});
   trigger.start();start=SystemClock.elapsedRealtime();
   try{WhisperEngine.recognize(model,new float[480000],480000,"en",cancel);throw new Exception("Native abort ignored");}
   catch(InterruptedException expected){System.out.println("PASS ANDROID_JNI_ABORT elapsed_ms="+(SystemClock.elapsedRealtime()-start));System.out.flush();}
   trigger.join();
   if(args.length>1){
    DataInputStream in=new DataInputStream(new FileInputStream(args[1]));
    byte[] header=new byte[44];in.readFully(header);int count=(int)(new File(args[1]).length()-44)/2;
    float[] pcm=new float[count];for(int i=0;i<count;i++)pcm[i]=(short)(in.readUnsignedByte()|(in.readUnsignedByte()<<8))/32768.0f;in.close();
    start=SystemClock.elapsedRealtime();String out=WhisperEngine.recognize(model,pcm,count,"en",new AtomicBoolean(false));
    JSONArray cues=new JSONArray(out);if(cues.length()==0)throw new Exception("Empty transcript");
    System.out.println("RESULT ANDROID_JNI_EN elapsed_ms="+(SystemClock.elapsedRealtime()-start)+" "+out);System.out.flush();
    if(!out.toLowerCase().contains("country"))throw new Exception("Expected English keyword missing");
    System.out.println("PASS ANDROID_JNI_EN_FIXTURE");
   }
  }finally{WhisperEngine.close(model);}
 }
}
