package io.github.himangshurajg.lecture;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
/** Private JNI bridge to the bundled multilingual Whisper CPU runtime. */
final class WhisperEngine {
    static {System.loadLibrary("lecture_asr");}
    static native long open(String path)throws IOException;
    static native String recognize(long model,float[] pcm,int count,String language,AtomicBoolean cancelled)throws IOException,InterruptedException;
    static native void close(long model);
}
