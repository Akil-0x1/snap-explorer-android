package local.snapexplorer.mobile;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class MediaRulesTest {
    private static int passed;
    private static void expect(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        expect(MediaNames.safeFileName("../../secret.jpg").equals("secret.jpg"),"no export traversal");
        expect(MediaNames.safeFileName("C:\\photos\\sample.jpg").equals("sample.jpg"),"Windows filename sanitization");
        expect(MediaNames.safeFileName("\n\"<bad?>.jpg").equals("___bad__.jpg"),"unsafe characters stripped");
        expect(MediaNames.safeFileName("..").equals("media"),"dot filename fallback");
        expect(MediaNames.sampleSize(8000,4000,2000)==4,"image decode memory bound");
        expect(MediaNames.sampleSize(100,50,2000)==1,"no unnecessary image downsample");
        expect(MediaNames.duration(125000).equals("2:05"),"audio duration display");
        expect(MediaNames.duration(-1000).equals("0:00"),"negative duration clamp");
        ByteBuffer padded=ByteBuffer.wrap(new byte[]{0,1,99,99,2,3,99,99});ByteBuffer out=ByteBuffer.allocate(4);
        YuvPlanes.copy(padded,4,1,0,0,2,2,out,2,1,2,2);
        expect(out.get(0)==0&&out.get(1)==1&&out.get(2)==2&&out.get(3)==3,"YUV row padding removed");
        ByteBuffer interleaved=ByteBuffer.wrap(new byte[]{10,99,20,99,30,99,40,99});out=ByteBuffer.allocate(4);
        YuvPlanes.copy(interleaved,4,2,0,0,2,2,out,2,1,2,2);
        expect(out.get(0)==10&&out.get(1)==20&&out.get(2)==30&&out.get(3)==40,"YUV pixel stride respected");
        ByteBuffer large=ByteBuffer.wrap(new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15});out=ByteBuffer.allocate(4);
        YuvPlanes.copy(large,4,1,1,1,2,2,out,2,1,2,2);
        expect(out.get(0)==5&&out.get(1)==6&&out.get(2)==9&&out.get(3)==10,"YUV decoder crop respected");
        out=ByteBuffer.allocate(4);YuvPlanes.copy(large,4,1,0,0,4,4,out,2,1,2,2);
        expect(out.get(0)==0&&out.get(1)==2&&out.get(2)==8&&out.get(3)==10,"YUV nearest-neighbor downsample");
        boolean rejected=false;try{YuvPlanes.copy(ByteBuffer.allocate(1),4,2,0,0,2,2,ByteBuffer.allocate(4),2,1,2,2);}catch(IOException expected){rejected=true;}
        expect(rejected,"inconsistent codec plane rejected");
        rejected=false;try{YuvPlanes.copy(ByteBuffer.allocate(4),Integer.MAX_VALUE,Integer.MAX_VALUE,100,100,2,2,ByteBuffer.allocate(4),2,1,2,2);}catch(IOException expected){rejected=true;}
        expect(rejected,"YUV offset integer overflow avoided");
        YuvOverlay overlay=new YuvOverlay(2,2,new int[]{0xffff0000,0xffff0000,0xffff0000,0xffff0000},false,false);
        ByteBuffer luma=ByteBuffer.wrap(new byte[]{16,16,16,16}),chromaU=ByteBuffer.wrap(new byte[]{(byte)128}),chromaV=ByteBuffer.wrap(new byte[]{(byte)128});
        overlay.blend(0,luma,2,1);overlay.blend(1,chromaU,1,1);overlay.blend(2,chromaV,1,1);
        expect((luma.get(0)&255)==82&&(chromaU.get(0)&255)==90&&(chromaV.get(0)&255)==240,"opaque red overlay in limited-range BT601");
        overlay=new YuvOverlay(2,2,new int[]{0,0,0,0},true,false);luma=ByteBuffer.wrap(new byte[]{42,42,42,42});overlay.blend(0,luma,2,1);
        expect((luma.get(0)&255)==42,"transparent overlay leaves source frame unchanged");
        overlay=new YuvOverlay(2,2,new int[]{0x80ffffff,0x80ffffff,0x80ffffff,0x80ffffff},true,true);luma=ByteBuffer.wrap(new byte[4]);overlay.blend(0,luma,2,1);
        expect((luma.get(0)&255)==128,"half-transparent white overlay blends full-range luma");
        rejected=false;try{new YuvOverlay(4000,4000,new int[0],false,false);}catch(IllegalArgumentException expected){rejected=true;}
        expect(rejected,"overlay frame allocation bounded before use");
        System.out.println(passed+" media rules tests passed.");
    }
}
