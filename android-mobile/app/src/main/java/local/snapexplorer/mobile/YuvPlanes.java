package local.snapexplorer.mobile;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Stride-aware bounded YUV plane copy; separate from Android codecs for JVM testing. */
public final class YuvPlanes {
    public static void copy(ByteBuffer input, int inputRow, int inputPixel, int left, int top,
            int sourceWidth, int sourceHeight, ByteBuffer output, int outputRow, int outputPixel,
            int targetWidth, int targetHeight) throws IOException {
        if(inputRow<1||inputPixel<1||outputRow<1||outputPixel<1||left<0||top<0||sourceWidth<1||sourceHeight<1||targetWidth<1||targetHeight<1)
            throw new IOException("Invalid YUV plane layout.");
        int startIn=input.position(),startOut=output.position();
        for(int y=0;y<targetHeight;y++){
            long row=top+(long)y*sourceHeight/targetHeight;
            for(int x=0;x<targetWidth;x++){
                long column=left+(long)x*sourceWidth/targetWidth;
                long i=startIn+row*inputRow+column*inputPixel,o=startOut+(long)y*outputRow+(long)x*outputPixel;
                if(i<0||i>=input.limit()||o<0||o>=output.limit())throw new IOException("Device codec returned an inconsistent YUV stride.");
                output.put((int)o,input.get((int)i));
            }
        }
    }
    private YuvPlanes(){}
}
