package local.snapexplorer.mobile;

import android.content.Context;import android.graphics.*;import android.view.*;import java.util.*;

/** Local equirectangular coordinate map. No tiles, webview, URLs or network services. */
public final class OfflineMapView extends View {
 public static final class Point {public double lat,lon;public long time;public String label;public Point(double lat,double lon,long time,String label){this.lat=lat;this.lon=lon;this.time=time;this.label=label;}}
 private final List<Point> points;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private float zoom=1,panX,panY,lastX,lastY;private int visible;private boolean playing;private String selected="Drag to pan · pinch to zoom · tap a point";private final ScaleGestureDetector scale;
 private final Runnable tick=new Runnable(){public void run(){if(!playing)return;visible=Math.min(points.size(),visible+Math.max(1,points.size()/150));invalidate();if(visible<points.size())postDelayed(this,80);else playing=false;}};
 public OfflineMapView(Context context,List<Point> data){super(context);points=new ArrayList<>(data);Collections.sort(points,Comparator.comparingLong(p->p.time));visible=points.size();setContentDescription("Offline coordinate map with "+points.size()+" points. Drag or pinch to explore.");scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoom=Math.max(1,Math.min(24,zoom*d.getScaleFactor()));invalidate();return true;}});setMinimumHeight(500);setFocusable(true);}
 public void animateRoute(){removeCallbacks(tick);visible=0;playing=true;post(tick);}
 public void setVisibleFraction(float fraction){playing=false;removeCallbacks(tick);visible=Math.max(0,Math.min(points.size(),Math.round(points.size()*fraction)));invalidate();}
 public void showAll(){playing=false;removeCallbacks(tick);visible=points.size();zoom=1;panX=0;panY=0;invalidate();}
 @Override protected void onDetachedFromWindow(){removeCallbacks(tick);playing=false;super.onDetachedFromWindow();}
 private float x(double lon){return (float)((lon+180)/360*getWidth()*zoom)+panX;}
 private float y(double lat){return (float)((90-lat)/180*(getHeight()-90)*zoom)+panY;}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(Color.rgb(16,27,23));paint.setTextSize(22);paint.setStrokeWidth(1);paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.rgb(49,73,60));
  for(int lon=-180;lon<=180;lon+=30)canvas.drawLine(x(lon),y(90),x(lon),y(-90),paint);for(int lat=-90;lat<=90;lat+=30)canvas.drawLine(x(-180),y(lat),x(180),y(lat),paint);
  // Deliberately coarse local outlines provide context without claiming street-map detail.
  double[][] shapes={{-168,70,-130,72,-110,58,-60,48,-82,9,-110,25,-140,60},{-82,12,-50,8,-35,-6,-55,-55,-75,-40},{-17,35,12,37,50,12,35,-35,18,-35,-5,5},{-10,36,-5,60,30,72,100,75,178,65,140,35,105,5,70,8,40,35},{112,-12,150,-10,153,-39,115,-35}};
  paint.setColor(Color.rgb(74,100,80));for(double[] shape:shapes){Path path=new Path();path.moveTo(x(shape[0]),y(shape[1]));for(int i=2;i<shape.length;i+=2)path.lineTo(x(shape[i]),y(shape[i+1]));path.close();canvas.drawPath(path,paint);}
  paint.setColor(Color.rgb(245,230,66));paint.setStrokeWidth(2);Point previous=null;for(int i=0;i<visible;i++){Point p=points.get(i);if(previous!=null&&Math.abs(previous.lon-p.lon)<180)canvas.drawLine(x(previous.lon),y(previous.lat),x(p.lon),y(p.lat),paint);previous=p;}
  paint.setStyle(Paint.Style.FILL);for(int i=0;i<visible;i++){Point p=points.get(i);canvas.drawCircle(x(p.lon),y(p.lat),5,paint);}
  paint.setColor(Color.rgb(16,23,19));canvas.drawRect(0,getHeight()-85,getWidth(),getHeight(),paint);paint.setColor(Color.rgb(230,242,233));paint.setTextSize(22);canvas.drawText(visible+" / "+points.size()+" locations · offline coordinate view",12,getHeight()-52,paint);paint.setTextSize(19);canvas.drawText(selected.length()>70?selected.substring(0,70):selected,12,getHeight()-20,paint);
 }
 @Override public boolean onTouchEvent(MotionEvent event){scale.onTouchEvent(event);switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:lastX=event.getX();lastY=event.getY();return true;case MotionEvent.ACTION_MOVE:if(!scale.isInProgress()&&event.getPointerCount()==1){panX+=event.getX()-lastX;panY+=event.getY()-lastY;invalidate();}lastX=event.getX();lastY=event.getY();return true;case MotionEvent.ACTION_UP:Point nearest=null;double distance=40;for(int i=0;i<visible;i++){Point p=points.get(i);double d=Math.hypot(x(p.lon)-event.getX(),y(p.lat)-event.getY());if(d<distance){nearest=p;distance=d;}}if(nearest!=null)selected=String.format(Locale.ROOT,"%.5f, %.5f · %s",nearest.lat,nearest.lon,nearest.label);performClick();invalidate();return true;}return true;}
 @Override public boolean performClick(){super.performClick();return true;}
}
