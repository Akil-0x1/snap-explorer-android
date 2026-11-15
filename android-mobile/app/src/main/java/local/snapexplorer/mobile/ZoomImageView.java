package local.snapexplorer.mobile;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Pinch, drag and double-tap local image viewer. Owns and recycles its bitmap. */
public final class ZoomImageView extends View {
    private Bitmap bitmap; private float zoom=1,panX,panY,lastX,lastY;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scale;private final GestureDetector taps;
    public ZoomImageView(Context context){super(context);
        setContentDescription("Image. Pinch to zoom, drag to pan, double tap to reset.");
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){zoom=Math.max(1,Math.min(8,zoom*detector.getScaleFactor()));invalidate();return true;}});
        taps=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){zoom=zoom>1?1:2.5f;panX=panY=0;invalidate();return true;}});
    }
    public void setImage(Bitmap image){if(bitmap!=null&&bitmap!=image)bitmap.recycle();bitmap=image;zoom=1;panX=panY=0;invalidate();}
    public void clear(){if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(bitmap==null)return;
        float fit=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight()),s=fit*zoom;
        float w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;
        panX=Math.max(-Math.max(0,(w-getWidth())/2),Math.min(Math.max(0,(w-getWidth())/2),panX));
        panY=Math.max(-Math.max(0,(h-getHeight())/2),Math.min(Math.max(0,(h-getHeight())/2),panY));
        canvas.drawBitmap(bitmap,null,new RectF((getWidth()-w)/2+panX,(getHeight()-h)/2+panY,(getWidth()+w)/2+panX,(getHeight()+h)/2+panY),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);taps.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!scale.isInProgress()){panX+=e.getX()-lastX;panY+=e.getY()-lastY;invalidate();lastX=e.getX();lastY=e.getY();}
        return true;
    }
    @Override protected void onDetachedFromWindow(){clear();super.onDetachedFromWindow();}
}
