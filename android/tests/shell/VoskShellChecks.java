package io.github.himangshurajg.lecture;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.SystemClock;
/** Isolated native tests, each short bounded mode. Not an installed-app decoder test. */
public final class VoskShellChecks {
 private static void log(String s){System.out.println(s);System.out.flush();}
 public static void main(String[] args)throws Exception {
  String mode=args[0];AtomicBoolean cancel=new AtomicBoolean("pre".equals(mode));long started=SystemClock.elapsedRealtime();
  if("pre".equals(mode)){
   try(VoskSession session=new VoskSession("/invalid-not-opened",cancel)){throw new Exception("Pre-cancel ignored");}
   catch(InterruptedException expected){log("PASS VOSK_PRE_CANCEL elapsed_ms="+(SystemClock.elapsedRealtime()-started));}return;
  }
  try(VoskSession session=new VoskSession(args[1],cancel)){
   log("PASS VOSK_MODEL_LOAD elapsed_ms="+(SystemClock.elapsedRealtime()-started));started=SystemClock.elapsedRealtime();
   if("short".equals(mode)){String out=session.accept(new short[4000],4000,false);log("PASS VOSK_SHORT_250MS elapsed_ms="+(SystemClock.elapsedRealtime()-started)+" "+out);log("FINAL "+session.accept(new short[0],0,true));return;}
   if("cancel".equals(mode)){
    Thread trigger=new Thread(()->{try{Thread.sleep(250);}catch(InterruptedException ignored){}cancel.set(true);});trigger.start();int chunks=0;
    try{while(chunks++<1000)session.accept(new short[4000],4000,false);throw new Exception("Cancel ignored");}
    catch(InterruptedException expected){long elapsed=SystemClock.elapsedRealtime()-started;if(elapsed>15000)throw new Exception("Cancellation over 15s: "+elapsed);log("PASS VOSK_STREAM_CANCEL elapsed_ms="+elapsed+" chunks="+chunks);}trigger.join();return;
   }
   try(DataInputStream in=new DataInputStream(new FileInputStream(args[2]))){
    // Input is fixture converted to raw 16kHz mono s16le, not an assumed WAV header.
    short[] pcm=new short[4000];int count=0;StringBuilder transcript=new StringBuilder();
    while(in.available()>=2){int lo=in.readUnsignedByte(),hi=in.readUnsignedByte();pcm[count++]=(short)(lo|(hi<<8));if(count==pcm.length){String out=session.accept(pcm,count,false);log("RESULT "+out);transcript.append(out);count=0;}}
    if(count>0){String out=session.accept(pcm,count,false);transcript.append(out);log("RESULT "+out);}
    String out=session.accept(pcm,0,true);transcript.append(out);log("FINAL "+out);
    if(!transcript.toString().contains(args[3]))throw new Exception("Expected fixture keyword missing: "+args[3]);
    log("PASS VOSK_FIXTURE elapsed_ms="+(SystemClock.elapsedRealtime()-started));
   }
  }
 }
}
