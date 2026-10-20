package io.github.himangshurajg.lecture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/** Bounded ISO-BMFF inspection for a known platform extractor edit-list timescale error.
 * Never changes audio unless the supplied delay exactly matches the erroneous movie-timescale
 * calculation and a single rate-1 edit on the same track establishes the correct value.
 */
public final class Mp4AudioEdits {
    private final FileChannel channel;
    private final long base, length;
    private int inspected;
    private Mp4AudioEdits(FileChannel c,long b,long n){channel=c;base=b;length=n;}
    private static final class Box {long data,end;String type;Box(long d,long e,String t){data=d;end=e;type=t;}}
    private ByteBuffer read(long offset,int count)throws IOException {
        if(offset<0||count<0||offset>length-count)throw new IOException("Invalid MP4 bounds");
        ByteBuffer b=ByteBuffer.allocate(count).order(ByteOrder.BIG_ENDIAN);
        while(b.hasRemaining()){int n=channel.read(b,base+offset+b.position());if(n<=0)throw new IOException("Truncated MP4");}b.flip();return b;
    }
    private Box box(long at,long end)throws IOException {
        if(++inspected>10000||end-at<8)throw new IOException("Invalid MP4 box table");
        ByteBuffer b=read(at,8);long size=b.getInt()&0xffffffffL;byte[] name=new byte[4];b.get(name);int header=8;
        if(size==1){size=read(at+8,8).getLong();header=16;}else if(size==0)size=end-at;
        if(size<header||size>end-at)throw new IOException("Invalid MP4 box size");
        return new Box(at+header,at+size,new String(name,"US-ASCII"));
    }
    private Box child(long from,long end,String type)throws IOException {
        for(long at=from;at<end;){Box b=box(at,end);if(b.type.equals(type))return b;at=b.end;}return null;
    }
    private long uint(Box b,int offset)throws IOException {if(b.data+offset+4>b.end)throw new IOException("Short MP4 field");return read(b.data+offset,4).getInt()&0xffffffffL;}
    private int version(Box b)throws IOException {if(b.data>=b.end)throw new IOException("Short MP4 version");return read(b.data,1).get()&255;}
    private long timescale(Box b)throws IOException {int v=version(b);if(v>1)throw new IOException("Unknown MP4 header");return uint(b,v==1?20:12);}
    private Integer inspect(int trackId,int rate,int reported)throws IOException {
        Box moov=child(0,length,"moov");if(moov==null)return null;Box mvhd=child(moov.data,moov.end,"mvhd");if(mvhd==null)return null;
        long movieScale=timescale(mvhd);if(movieScale==0)return null;
        for(long at=moov.data;at<moov.end;){Box trak=box(at,moov.end);at=trak.end;if(!trak.type.equals("trak"))continue;
            Box tkhd=child(trak.data,trak.end,"tkhd");if(tkhd==null)continue;int v=version(tkhd);if(v>1)continue;if(uint(tkhd,v==1?20:12)!=trackId)continue;
            Box mdia=child(trak.data,trak.end,"mdia"),edts=child(trak.data,trak.end,"edts");if(mdia==null||edts==null)return null;
            Box mdhd=child(mdia.data,mdia.end,"mdhd"),elst=child(edts.data,edts.end,"elst");if(mdhd==null||elst==null)return null;
            long mediaScale=timescale(mdhd);if(mediaScale==0||movieScale==mediaScale||uint(elst,4)!=1)return null;
            int ev=version(elst);if(ev>1)return null;int needed=ev==1?28:20;if(elst.end-elst.data<needed)return null;
            ByteBuffer edit=read(elst.data+8,ev==1?20:12);long mediaTime;
            if(ev==1){edit.getLong();mediaTime=edit.getLong();}else{edit.getInt();mediaTime=edit.getInt();}
            if(mediaTime<0||edit.getInt()!=0x00010000)return null;
            try{
                long numerator=Math.multiplyExact(mediaTime,(long)rate);
                long erroneous=Math.addExact(numerator,movieScale/2)/movieScale;
                long correct=Math.addExact(numerator,mediaScale/2)/mediaScale;
                if(erroneous!=reported||correct==reported||correct>Integer.MAX_VALUE)return null;
                return (int)correct;
            }catch(ArithmeticException ignored){return null;}
        }return null;
    }
    public static Integer correctedDelay(FileChannel channel,long start,long length,int trackId,int sampleRate,int reported)throws IOException {
        if(start<0||trackId<=0||sampleRate<=0||reported<0)return null;
        long available=channel.size()-start;if(available<8)return null;
        long bounded=length<0?available:Math.min(length,available);
        return new Mp4AudioEdits(channel,start,bounded).inspect(trackId,sampleRate,reported);
    }
}
