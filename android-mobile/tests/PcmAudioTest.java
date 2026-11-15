package local.snapexplorer.mobile;
import java.io.*;import java.nio.*;
public final class PcmAudioTest {
 static void require(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  ByteBuffer b=ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);b.putShort(Short.MIN_VALUE).putShort((short)1234).putShort(Short.MAX_VALUE).flip();ByteBuffer out=PcmAudio.to16Bit(b,PcmAudio.PCM_16,1);require(out.getShort()==Short.MIN_VALUE&&out.getShort()==1234&&out.getShort()==Short.MAX_VALUE);
  out=PcmAudio.to16Bit(ByteBuffer.wrap(new byte[]{0,(byte)128,(byte)255}),PcmAudio.PCM_8,1);require(out.getShort()==Short.MIN_VALUE&&out.getShort()==0&&out.getShort()==32512);
  b=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);b.putFloat(-2).putFloat(Float.NaN).putFloat(.5f).putFloat(2).flip();out=PcmAudio.to16Bit(b,PcmAudio.PCM_FLOAT,2);require(out.getShort()==Short.MIN_VALUE&&out.getShort()==0&&out.getShort()==16384&&out.getShort()==Short.MAX_VALUE);
  require(PcmAudio.timestamp(500000,48000,48000)==1500000);
  try{PcmAudio.to16Bit(ByteBuffer.wrap(new byte[]{1}),PcmAudio.PCM_16,1);throw new AssertionError();}catch(IOException expected){}
  try{PcmAudio.to16Bit(ByteBuffer.allocate(0),PcmAudio.PCM_16,9);throw new AssertionError();}catch(IOException expected){}
  try{PcmAudio.timestamp(0,Long.MAX_VALUE,1);throw new AssertionError();}catch(IOException expected){}
  System.out.println("7 PCM audio conversion/timing tests passed.");
 }
}
