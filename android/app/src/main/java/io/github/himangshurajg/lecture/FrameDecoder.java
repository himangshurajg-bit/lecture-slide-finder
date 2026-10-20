package io.github.himangshurajg.lecture;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sequential decoded-output traversal. No FPS guesses, sync-frame sampling or whole-video cache. */
public final class FrameDecoder implements AutoCloseable {
    private MediaExtractor extractor;
    private MediaCodec codec;
    private boolean inputEnd, outputEnd;
    private int index, rotation;
    private long durationUs;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    public FrameDecoder(Context context, Uri uri) throws Exception {
        try {
            extractor=new MediaExtractor(); extractor.setDataSource(context,uri,null);
            MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat candidate=extractor.getTrackFormat(i);
                String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(mime!=null && mime.startsWith("video/")){extractor.selectTrack(i);format=candidate;break;}
            }
            if(format==null)throw new IOException("No video track");
            if(format.containsKey(MediaFormat.KEY_DURATION))durationUs=format.getLong(MediaFormat.KEY_DURATION);
            if(format.containsKey(MediaFormat.KEY_ROTATION))rotation=format.getInteger(MediaFormat.KEY_ROTATION);
            format.setInteger(MediaFormat.KEY_ROTATION,0);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0);codec.start();
        } catch(Exception e){close();throw e;}
    }
    public JSONObject page(int count,AtomicBoolean cancel) throws Exception {
        JSONArray frames=new JSONArray();
        for(int i=0;i<count && !outputEnd;i++) {
            JSONObject frame=next(cancel);
            if(frame!=null)frames.put(frame);
        }
        return new JSONObject().put("frames",frames).put("done",outputEnd).put("decoded",index).put("duration",durationUs/1000000.0);
    }
    private JSONObject next(AtomicBoolean cancel) throws Exception {
        long idle=SystemClock.elapsedRealtime();
        while(!outputEnd) {
            if(cancel.get())throw new InterruptedException();
            if(SystemClock.elapsedRealtime()-idle>20000)throw new IOException("Decoder stalled");
            if(!inputEnd) {
                int in=codec.dequeueInputBuffer(1000);
                if(in>=0){ByteBuffer buffer=codec.getInputBuffer(in);int size=extractor.readSampleData(buffer,0);
                    if(size<0){inputEnd=true;codec.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);}
                    else {long pts=extractor.getSampleTime();codec.queueInputBuffer(in,0,size,pts,0);extractor.advance();}
                }
            }
            int out=codec.dequeueOutputBuffer(info,1000);
            if(out<0)continue;
            idle=SystemClock.elapsedRealtime();
            boolean eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            try {
                if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)continue;
                if(info.size<=0){if(eos)outputEnd=true;continue;}
                Image image=codec.getOutputImage(out);
                if(image==null)throw new IOException("Decoder cannot expose frames on this device");
                Bitmap bitmap;
                try{bitmap=bitmap(image,1280,cancel);}finally{image.close();}
                if(rotation%360!=0){Matrix matrix=new Matrix();matrix.postRotate(rotation);Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try{bitmap.compress(Bitmap.CompressFormat.JPEG,80,bytes);}finally{bitmap.recycle();}
                if(bytes.size()>700000)throw new IOException("Frame too large");
                JSONObject result=new JSONObject().put("index",index++).put("time",Math.max(0,info.presentationTimeUs)/1000000.0)
                    .put("image","data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));
                if(eos)outputEnd=true;
                return result;
            } finally{codec.releaseOutputBuffer(out,false);}
        }
        return null;
    }
    static Bitmap bitmap(Image image,int limit,AtomicBoolean cancel) throws Exception {
        if(image.getFormat()!=ImageFormat.YUV_420_888)throw new IOException("Unsupported decoded image format");
        Rect crop=image.getCropRect();int cw=crop.width(),ch=crop.height();
        double scale=Math.min(1.0,limit/(double)Math.max(cw,ch));int w=Math.max(1,(int)(cw*scale)),h=Math.max(1,(int)(ch*scale));
        int[] pixels=new int[w*h];Image.Plane[] planes=image.getPlanes();
        ByteBuffer y=planes[0].getBuffer(),u=planes[1].getBuffer(),v=planes[2].getBuffer();
        for(int row=0;row<h;row++){
            if(cancel.get())throw new InterruptedException();
            int sy=crop.top+(int)((long)row*ch/h);
            for(int col=0;col<w;col++){
                int sx=crop.left+(int)((long)col*cw/w);
                int yy=(y.get(y.position()+sy*planes[0].getRowStride()+sx*planes[0].getPixelStride())&255)-16;
                int uu=(u.get(u.position()+(sy/2)*planes[1].getRowStride()+(sx/2)*planes[1].getPixelStride())&255)-128;
                int vv=(v.get(v.position()+(sy/2)*planes[2].getRowStride()+(sx/2)*planes[2].getPixelStride())&255)-128;
                int r=(298*yy+409*vv+128)>>8,g=(298*yy-100*uu-208*vv+128)>>8,b=(298*yy+516*uu+128)>>8;
                pixels[row*w+col]=Color.rgb(Math.max(0,Math.min(255,r)),Math.max(0,Math.min(255,g)),Math.max(0,Math.min(255,b)));
            }
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    public boolean isDone(){return outputEnd;}
    @Override public void close(){if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}codec=null;}if(extractor!=null){extractor.release();extractor=null;}}
}
