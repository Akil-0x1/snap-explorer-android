package local.snapexplorer.mobile;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Bounded static RGBA overlay converted once, then blended into YUV420 frames. */
public final class YuvOverlay {
    private final int width,height;
    private final byte[] y,a,u,v,ca;
    public YuvOverlay(int width,int height,int[] argb,boolean bt709,boolean fullRange){
        if(width<2||height<2||(width&1)!=0||(height&1)!=0||(long)width*height>1920L*1080||argb.length!=width*height)throw new IllegalArgumentException("Invalid overlay dimensions");
        this.width=width;this.height=height;int count=width*height;y=new byte[count];a=new byte[count];u=new byte[count/4];v=new byte[count/4];ca=new byte[count/4];
        for(int row=0;row<height;row+=2)for(int col=0;col<width;col+=2){
            int alphaSum=0,uSum=0,vSum=0;
            for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){
                int pos=(row+dy)*width+col+dx,pixel=argb[pos],alpha=(pixel>>>24)&255,r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
                int yy,uu,vv;
                if(fullRange){yy=bt709?(54*r+183*g+19*b+128)>>8:(77*r+150*g+29*b+128)>>8;uu=(bt709?(-29*r-99*g+128*b+128)>>8:(-43*r-85*g+128*b+128)>>8)+128;vv=(bt709?(128*r-116*g-12*b+128)>>8:(128*r-107*g-21*b+128)>>8)+128;}
                else{yy=((bt709?47*r+157*g+16*b:66*r+129*g+25*b)+128>>8)+16;uu=((bt709?-26*r-87*g+112*b:-38*r-74*g+112*b)+128>>8)+128;vv=((bt709?112*r-102*g-10*b:112*r-94*g-18*b)+128>>8)+128;}
                y[pos]=(byte)clamp(yy);a[pos]=(byte)alpha;alphaSum+=alpha;uSum+=clamp(uu)*alpha;vSum+=clamp(vv)*alpha;
            }
            int chroma=(row/2)*(width/2)+col/2;ca[chroma]=(byte)((alphaSum+2)/4);u[chroma]=(byte)(alphaSum==0?128:uSum/alphaSum);v[chroma]=(byte)(alphaSum==0?128:vSum/alphaSum);
        }
    }
    private static int clamp(int value){return Math.max(0,Math.min(255,value));}
    public void blend(int plane,ByteBuffer target,int rowStride,int pixelStride)throws IOException{
        if(plane<0||plane>2||rowStride<1||pixelStride<1)throw new IOException("Invalid YUV blend plane");
        int w=plane==0?width:width/2,h=plane==0?height:height/2,base=target.position();byte[] values=plane==0?y:plane==1?u:v,alpha=plane==0?a:ca;
        for(int row=0;row<h;row++){
            if(Thread.currentThread().isInterrupted())throw new IOException("Video overlay interrupted.");
            for(int col=0;col<w;col++){
                int i=row*w+col,opacity=alpha[i]&255;if(opacity==0)continue;
                long pos=base+(long)row*rowStride+(long)col*pixelStride;
                if(pos<0||pos>=target.limit())throw new IOException("Invalid encoder plane bounds");
                int old=target.get((int)pos)&255,value=values[i]&255;
                target.put((int)pos,(byte)((value*opacity+old*(255-opacity)+127)/255));
            }
        }
    }
}
