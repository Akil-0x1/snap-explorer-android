package local.snapexplorer.mobile;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import java.io.*;
/** Called only on API 28+. File format is detected from bytes, not hashed file extensions. */
final class AnimatedMedia {
 static Drawable decode(File file)throws IOException{
  if(file.length()>64L*1024*1024)throw new IOException("Animation is too large for live preview");
  return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),(decoder,info,source)->{int width=info.getSize().getWidth(),height=info.getSize().getHeight();if(width<=0||height<=0)throw new IllegalArgumentException("Invalid image dimensions");float scale=Math.min(1f,1920f/Math.max(width,height));if(scale<1f)decoder.setTargetSize(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));});
 }
 private AnimatedMedia(){}
}
