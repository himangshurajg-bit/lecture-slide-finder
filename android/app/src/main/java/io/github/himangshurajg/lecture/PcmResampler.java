package io.github.himangshurajg.lecture;

/** Streaming linear PCM16 resampler; averages channels and keeps phase across decoder buffers. */
public final class PcmResampler {
    private final int rate, channels;
    private long inputFrames;
    private double nextFrame;
    private short previous;
    public PcmResampler(int rate, int channels) {
        if(rate<8000 || rate>192000 || channels<1 || channels>8) throw new IllegalArgumentException("Unsupported audio layout");
        this.rate=rate; this.channels=channels;
    }
    public short[] convert(short[] input) {
        if(input.length%channels!=0) throw new IllegalArgumentException("Incomplete PCM frame");
        short[] out=new short[(int)Math.ceil((input.length/channels+2)*16000.0/rate)+2]; int count=0;
        for(int i=0;i<input.length;i+=channels) {
            long sum=0; for(int c=0;c<channels;c++)sum+=input[i+c]; short current=(short)(sum/channels);
            long frame=inputFrames++;
            while(nextFrame<=frame) {
                double fraction=frame==0?1.0:nextFrame-(frame-1);
                out[count++]=(short)Math.round(previous+(current-previous)*fraction);
                nextFrame+=rate/16000.0;
            }
            previous=current;
        }
        return java.util.Arrays.copyOf(out,count);
    }
}
