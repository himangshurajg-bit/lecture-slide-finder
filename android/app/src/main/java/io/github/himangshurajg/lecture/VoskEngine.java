package io.github.himangshurajg.lecture;
import java.io.IOException;
/** Chunked offline Vosk C API. Owner thread alone owns and releases a session. */
public final class VoskEngine {
    static { System.loadLibrary("vosk"); System.loadLibrary("lecture_vosk"); }
    private VoskEngine() {}
    public static native long open(String path) throws IOException;
    public static native String accept(long session, short[] pcm, int count, boolean last) throws IOException;
    public static native void close(long session);
}
