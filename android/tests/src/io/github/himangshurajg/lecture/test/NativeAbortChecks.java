package io.github.himangshurajg.lecture.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/** Model preparation, Java pre-cancel and actual JNI abort, without an accuracy claim. */
public final class NativeAbortChecks extends Instrumentation {
    private void log(String text) { Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b); }
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    @Override public void onStart() {
        Bundle result=new Bundle();long handle=0;Method close=null;Thread trigger=null;
        try {
            ClassLoader loader=getTargetContext().getClassLoader();
            Class<?> decoder=loader.loadClass("io.github.himangshurajg.lecture.OfflineTranscriber");
            Class<?> progress=loader.loadClass("io.github.himangshurajg.lecture.OfflineTranscriber$Progress");
            AtomicBoolean cancellation=new AtomicBoolean(true);
            Object listener=Proxy.newProxyInstance(loader,new Class[]{progress},(p,m,a)->null);
            Object runner=decoder.getConstructor(android.content.Context.class,AtomicBoolean.class,progress).newInstance(getTargetContext(),cancellation,listener);
            try {
                decoder.getMethod("transcribe",Uri.class,String.class).invoke(runner,Uri.EMPTY,"en");
                throw new Exception("Java pre-cancel was ignored");
            } catch(InvocationTargetException expected) {
                if(!(expected.getCause() instanceof InterruptedException))throw expected;
            }
            log("PASS JAVA_PRE_CANCEL");
            cancellation.set(false);
            Method model=decoder.getDeclaredMethod("model",String.class);model.setAccessible(true);
            File installed=(File)model.invoke(runner,"en");
            Class<?> engine=loader.loadClass("io.github.himangshurajg.lecture.VoskSession");
            Object session=engine.getConstructor(String.class,AtomicBoolean.class).newInstance(installed.getAbsolutePath(),cancellation);
            Method accept=engine.getMethod("accept",short[].class,int.class,boolean.class);
            log("PASS ANDROID_VOSK_MODEL_LOAD");
            try {
                long started=SystemClock.elapsedRealtime();accept.invoke(session,new short[4000],4000,false);
                log("PASS SHORT_250MS elapsed_ms="+(SystemClock.elapsedRealtime()-started));
                trigger=new Thread(()->{try{Thread.sleep(250);}catch(InterruptedException ignored){}cancellation.set(true);},"asr-test-cancel");
                started=SystemClock.elapsedRealtime();trigger.start();
                try {for(int i=0;i<1000;i++)accept.invoke(session,new short[4000],4000,false);throw new Exception("Cancellation ignored");}
                catch(InvocationTargetException expected){if(!(expected.getCause() instanceof InterruptedException))throw expected;}
                long elapsed=SystemClock.elapsedRealtime()-started;
                if(elapsed>15000)throw new Exception("Cancellation exceeded 15 seconds: "+elapsed);
                log("PASS CHUNKED_NATIVE_CANCEL elapsed_ms="+elapsed);
            } finally {engine.getMethod("close").invoke(session);}
            result.putString("stream","Native Vosk loading, short chunk and streaming cancellation passed; transcription and language quality are separate.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            log("FAIL "+error);
            if(error instanceof InvocationTargetException)log("CAUSE "+((InvocationTargetException)error).getCause());
            result.putString("stream",error.toString());finish(Activity.RESULT_CANCELED,result);
        } finally {
            if(trigger!=null)trigger.interrupt();
            if(handle!=0 && close!=null)try{close.invoke(null,handle);}catch(Exception ignored){}
        }
    }
}
