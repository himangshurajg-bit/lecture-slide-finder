package io.github.himangshurajg.lecture;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
/** Single-worker session. Cancellation never frees memory while a native call is running. */
public final class VoskSession implements AutoCloseable {
    private long handle;
    private final AtomicBoolean cancelled;
    public VoskSession(String model, AtomicBoolean cancelled)throws IOException,InterruptedException {
        this.cancelled=cancelled;check();handle=VoskEngine.open(model);
        try{check();}catch(InterruptedException error){close();throw error;}
    }
    private void check()throws InterruptedException {
        if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedException("Transcription cancelled");
    }
    public String accept(short[] pcm,int count,boolean last)throws IOException,InterruptedException {
        check();if(handle==0)throw new IOException("Speech session closed");
        String result=VoskEngine.accept(handle,pcm,count,last);check();return result;
    }
    @Override public void close(){if(handle!=0){VoskEngine.close(handle);handle=0;}}
}
