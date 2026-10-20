package io.github.himangshurajg.lecture;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.net.Uri;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Local file decoding and chunked English/Hindi Vosk inference. No microphone, server, network or media upload. */
public final class OfflineTranscriber {
    public interface Progress { void update(String stage, int percent); }
    private final Context context;
    private final AtomicBoolean cancelled;
    private final Progress progress;
    public OfflineTranscriber(Context context, AtomicBoolean cancelled, Progress progress) {
        this.context=context.getApplicationContext();this.cancelled=cancelled;this.progress=progress;
    }
    private void checkpoint() throws InterruptedException {
        if(cancelled.get() || Thread.currentThread().isInterrupted())throw new InterruptedException("Transcription cancelled. Your previous transcript and notes are unchanged.");
    }
    private static void erase(File file) {
        if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)erase(child);}
        file.delete();
    }
    private File model(String language)throws Exception {
        String name="en".equals(language)?"vosk-model-small-en-us-0.15":"vosk-model-small-hi-0.22";
        String expected="en".equals(language)?"30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498":"7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48";
        File root=new File(context.getFilesDir(),"speech-models");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot prepare model storage");
        File destination=new File(root,name),marker=new File(destination,".ready");
        if(marker.isFile()){try(BufferedReader in=new BufferedReader(new FileReader(marker))){if(expected.equals(in.readLine()))return destination;}}
        File stage=new File(root,name+".partial");erase(stage);if(!stage.mkdirs())throw new IOException("Cannot prepare language model");
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            // Verify compressed input before using any of its entries.
            try(InputStream source=context.getAssets().open("asr/"+name+".zip")){
                byte[] buffer=new byte[65536];int n;while((n=source.read(buffer))!=-1){checkpoint();digest.update(buffer,0,n);}
            }
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));
            if(!expected.equals(hash.toString()))throw new IOException("Bundled language model checksum mismatch");
            long bytes=0;int files=0;
            try(ZipInputStream zip=new ZipInputStream(context.getAssets().open("asr/"+name+".zip"))){
                ZipEntry entry;byte[] buffer=new byte[65536];
                while((entry=zip.getNextEntry())!=null){checkpoint();String path=entry.getName();
                    if(!path.startsWith(name+"/"))throw new IOException("Unexpected model archive path");
                    String relative=path.substring(name.length()+1);if(relative.isEmpty())continue;
                    File output=new File(stage,relative);
                    if(!output.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("Invalid model archive path");
                    if(entry.isDirectory()){if(!output.isDirectory()&&!output.mkdirs())throw new IOException("Cannot create model folder");continue;}
                    if(++files>1000)throw new IOException("Too many model files");
                    File parent=output.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create model folder");
                    try(OutputStream out=new FileOutputStream(output)){int n;while((n=zip.read(buffer))!=-1){checkpoint();bytes+=n;if(bytes>300L*1024*1024)throw new IOException("Model archive too large");out.write(buffer,0,n);}}
                }
            }
            if(!new File(stage,"am/final.mdl").isFile()||!new File(stage,"conf/model.conf").isFile())throw new IOException("Incomplete speech model");
            checkpoint();try(FileWriter out=new FileWriter(new File(stage,".ready"))){out.write(expected+"\n");}
            erase(destination);if(!stage.renameTo(destination))throw new IOException("Cannot install language model");return destination;
        }catch(Exception e){erase(stage);throw e;}
    }
    private void append(JSONArray cues,VoskSession model,short[] pcm,int count,boolean last,double offset)throws Exception {
        checkpoint();JSONObject result=new JSONObject(model.accept(pcm,count,last));checkpoint();
        JSONArray words=result.optJSONArray("result");if(words==null||words.length()==0)return;
        StringBuilder text=new StringBuilder();double start=0,end=0;
        for(int i=0;i<words.length();i++){
            JSONObject word=words.getJSONObject(i);double wordStart=offset+word.getDouble("start"),wordEnd=offset+word.getDouble("end");
            if(text.length()==0)start=wordStart;else text.append(' ');text.append(word.getString("word"));end=wordEnd;
            if(end-start>=8||i==words.length()-1){if(end>start)cues.put(new JSONObject().put("start",start).put("end",end).put("text",text.toString()));text.setLength(0);}
            if(cues.length()>20000)throw new IOException("Transcript too long. Use a shorter recording.");
        }
    }
    private void correctAacPriming(Uri uri,MediaFormat format) {
        // Android's older MP4 extractor can convert elst media_time using the movie
        // timescale instead of the audio track timescale, cutting real speech.
        // Inspect the matching track; do not guess or strip valid gapless metadata.
        if(!"audio/mp4a-latm".equals(format.getString(MediaFormat.KEY_MIME)) ||
           !format.containsKey(MediaFormat.KEY_ENCODER_DELAY) || !format.containsKey(MediaFormat.KEY_TRACK_ID))return;
        try(AssetFileDescriptor descriptor=context.getContentResolver().openAssetFileDescriptor(uri,"r")) {
            if(descriptor==null)return;
            try(FileInputStream in=new FileInputStream(descriptor.getFileDescriptor())) {
                Integer delay=Mp4AudioEdits.correctedDelay(in.getChannel(),descriptor.getStartOffset(),descriptor.getDeclaredLength(),
                    format.getInteger(MediaFormat.KEY_TRACK_ID),format.getInteger(MediaFormat.KEY_SAMPLE_RATE),format.getInteger(MediaFormat.KEY_ENCODER_DELAY));
                if(delay!=null)format.setInteger(MediaFormat.KEY_ENCODER_DELAY,delay);
            }
        }catch(IOException|IllegalArgumentException ignored){/* Pipes/unknown containers keep platform metadata unchanged. */}
    }
    public String transcribe(Uri uri,String language)throws Exception {
        checkpoint();progress.update("Preparing offline speech model. Keep the app open.",-1);
        if(!"en".equals(language)&&!"hi".equals(language))throw new IOException("Unsupported language");
        File installed=model(language);checkpoint();VoskSession model=null;
        MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
        try {
            model=new VoskSession(installed.getAbsolutePath(),cancelled);checkpoint();extractor.setDataSource(context,uri,null);
            int track=-1;MediaFormat input=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;input=f;break;}}
            if(track<0)throw new IOException("This video has no audio track. Choose a lecture containing speech.");
            extractor.selectTrack(track);long duration=input.containsKey(MediaFormat.KEY_DURATION)?input.getLong(MediaFormat.KEY_DURATION):0;
            correctAacPriming(uri,input);
            decoder=MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));decoder.configure(input,null,null,0);decoder.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputDone=false,outputDone=false;
            PcmResampler resampler=null;int encoding=AudioFormat.ENCODING_PCM_16BIT,channels=1;long lastProgress=0,lastOutput=SystemClock.elapsedRealtime();double offset=0;boolean haveOffset=false;
            JSONArray cues=new JSONArray();short[] chunk=new short[4000];int chunkCount=0;long samplesDone=0;
            while(!outputDone) {
                checkpoint();
                if(!inputDone){int idx=decoder.dequeueInputBuffer(10000);if(idx>=0){ByteBuffer buffer=decoder.getInputBuffer(idx);buffer.clear();int n=extractor.readSampleData(buffer,0);if(n<0){decoder.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{decoder.queueInputBuffer(idx,0,n,extractor.getSampleTime(),0);extractor.advance();}}}
                int idx=decoder.dequeueOutputBuffer(info,10000);
                if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat output=decoder.getOutputFormat();channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);int rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);encoding=output.containsKey(MediaFormat.KEY_PCM_ENCODING)?output.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;resampler=new PcmResampler(rate,channels);}
                else if(idx>=0) {
                    lastOutput=SystemClock.elapsedRealtime();
                    try {
                        if(info.size>0){
                            if(resampler==null)throw new IOException("Audio decoder did not report its format");
                            ByteBuffer buffer=decoder.getOutputBuffer(idx).order(ByteOrder.LITTLE_ENDIAN);buffer.position(info.offset);buffer.limit(info.offset+info.size);
                            int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                            if(encoding!=AudioFormat.ENCODING_PCM_FLOAT && encoding!=AudioFormat.ENCODING_PCM_16BIT)throw new IOException("Unsupported decoded audio. Try an AAC MP4 lecture.");
                            if(info.size%(channels*bytes)!=0)throw new IOException("Incomplete decoded audio frame");
                            short[] pcm=new short[info.size/bytes];
                            for(int j=0;j<pcm.length;j++)pcm[j]=bytes==2?buffer.getShort():(short)Math.round(Math.max(-1,Math.min(1,buffer.getFloat()))*32767);
                            short[] mono=resampler.convert(pcm);
                            if(!haveOffset){offset=Math.max(0,info.presentationTimeUs/1000000.0);haveOffset=true;}
                            for(short value:mono){chunk[chunkCount++]=value;if(chunkCount==chunk.length){

                                append(cues,model,chunk,chunkCount,false,offset);samplesDone+=chunkCount;chunkCount=0;
                            }}
                            // Inference can take longer than the decoder timeout; reset only after successful inference.
                            lastOutput=SystemClock.elapsedRealtime();
                        }
                        outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    }finally{decoder.releaseOutputBuffer(idx,false);}
                    if(SystemClock.elapsedRealtime()-lastProgress>1000){lastProgress=SystemClock.elapsedRealtime();progress.update("Transcribing on this phone. Keep the app open.",duration>0?(int)Math.max(0,Math.min(99,info.presentationTimeUs*100/duration)):-1);}
                }
                if(SystemClock.elapsedRealtime()-lastOutput>60000)throw new IOException("Audio decoding stalled. Your previous notes are unchanged.");
            }
            checkpoint();if(chunkCount>0)append(cues,model,chunk,chunkCount,false,offset);append(cues,model,chunk,0,true,offset);
            if(cues.length()==0)throw new IOException("No speech was recognised. Check the chosen language and recording; your previous notes are unchanged.");
            String json=cues.toString();if(json.getBytes("UTF-8").length>5*1024*1024)throw new IOException("Transcript too large. Use a shorter lecture.");return json;
        }finally{if(model!=null)model.close();if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}extractor.release();}
    }
}
