package local.snapexplorer.mobile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded PCM normalization and frame timing, independent of Android for JVM tests. */
public final class PcmAudio {
    public static final int PCM_16=2,PCM_8=3,PCM_FLOAT=4,PCM_24=21,PCM_32=22;
    private static final int MAX_BYTES=4*1024*1024;
    public static ByteBuffer to16Bit(ByteBuffer source,int encoding,int channels)throws IOException{
        if(channels<1||channels>8)throw new IOException("Unsupported audio channel count.");
        int bytes;
        switch(encoding){case PCM_8:bytes=1;break;case PCM_16:bytes=2;break;case PCM_24:bytes=3;break;case PCM_32:case PCM_FLOAT:bytes=4;break;default:throw new IOException("The decoder returned an unsupported PCM format.");}
        int length=source.remaining();
        if(length%bytes!=0||(length/bytes)%channels!=0)throw new IOException("The decoder returned an incomplete PCM frame.");
        long size=(long)(length/bytes)*2;if(size>MAX_BYTES)throw new IOException("The decoder PCM buffer exceeds the local memory limit.");
        ByteBuffer input=source.slice().order(ByteOrder.LITTLE_ENDIAN),output=ByteBuffer.allocate((int)size).order(ByteOrder.LITTLE_ENDIAN);
        while(input.hasRemaining()){
            short sample;
            switch(encoding){
                case PCM_16:sample=input.getShort();break;
                case PCM_8:sample=(short)(((input.get()&255)-128)<<8);break;
                case PCM_24:{int value=(input.get()&255)|((input.get()&255)<<8)|(input.get()<<16);sample=(short)(value>>8);break;}
                case PCM_32:sample=(short)(input.getInt()>>16);break;
                default:{float value=input.getFloat();if(Float.isNaN(value))value=0;sample=value<=-1?Short.MIN_VALUE:value>=1?Short.MAX_VALUE:(short)Math.round(value*32767);break;}
            }
            output.putShort(sample);
        }
        output.flip();return output;
    }
    public static long timestamp(long baseUs,long frames,int sampleRate)throws IOException{
        if(frames<0||sampleRate<1||frames>Long.MAX_VALUE/1000000L)throw new IOException("Invalid audio frame timing.");
        long delta=frames*1000000L/sampleRate;
        if(baseUs>Long.MAX_VALUE-delta)throw new IOException("Audio timestamp overflow.");
        return Math.max(0,baseUs+delta);
    }
    private PcmAudio(){}
}
