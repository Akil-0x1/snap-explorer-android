package local.snapexplorer.mobile;
import android.graphics.*;import android.graphics.pdf.PdfDocument;import java.io.*;import java.util.*;

/** Bounded-page local PDF layout; bitmaps are recycled by callers after drawing. */
final class PdfBook implements AutoCloseable {
 private final PdfDocument document=new PdfDocument();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private PdfDocument.Page page;private int pages,y;private final String title;
 PdfBook(String title){this.title=title;paint.setTextSize(11);paint.setColor(Color.BLACK);}
 private void ensure(int height)throws IOException{if(page==null||y+height>790){finish();if(pages>=2000)throw new IOException("PDF exceeds 2,000 pages. Export a smaller selection.");page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,++pages).create());y=38;paint.setTextSize(14);page.getCanvas().drawText(title.length()>70?title.substring(0,70):title,30,y,paint);y+=27;paint.setTextSize(11);}}
 void text(String value)throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("Export interrupted");for(String raw:value.split("\n",-1)){String remaining=raw;do{ensure(16);int length=remaining.isEmpty()?0:paint.breakText(remaining,true,535,null);if(length==0&&!remaining.isEmpty())length=1;page.getCanvas().drawText(remaining.substring(0,length),30,y,paint);remaining=remaining.substring(length);y+=16;}while(!remaining.isEmpty());}}
 void image(Bitmap image)throws IOException{float scale=Math.min(535f/image.getWidth(),460f/image.getHeight());int height=Math.max(1,Math.round(image.getHeight()*scale));ensure(height+15);page.getCanvas().drawBitmap(image,null,new RectF(30,y,30+image.getWidth()*scale,y+height),paint);y+=height+15;}
 void chart(String title,Map<String,Long> data)throws IOException{if(data.isEmpty())return;text("\n"+title);long max=1;for(long n:data.values())max=Math.max(max,n);int count=0;for(Map.Entry<String,Long> row:data.entrySet()){if(count++>=30)break;ensure(33);String label=row.getKey();if(label.length()>65)label=label.substring(0,62)+"…";paint.setColor(Color.BLACK);page.getCanvas().drawText(label+" · "+row.getValue(),30,y,paint);y+=6;paint.setColor(Color.rgb(92,112,48));page.getCanvas().drawRect(30,y,30+Math.max(1,535f*row.getValue()/max),y+9,paint);paint.setColor(Color.BLACK);y+=23;}}
 private void finish(){if(page!=null){document.finishPage(page);page=null;}}
 void write(OutputStream out)throws IOException{if(pages==0)text("No records in this selection.");finish();document.writeTo(out);}
 @Override public void close(){finish();document.close();}
}
