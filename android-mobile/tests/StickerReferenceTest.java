package local.snapexplorer.mobile;
import org.json.*;import java.util.*;
/** Synthetic reproduction of a custom_sticker.json Content filename. */
public final class StickerReferenceTest {
 public static void main(String[] args)throws Exception{
  JSONObject row=new JSONObject("{\"Sticker ID\":\"sticker-record-123\",\"Content\":\"x+x-123456789.png\"}");MediaItem asset=new MediaItem();asset.id="local-sticker";asset.name="x+x-123456789.png";asset.originalPath="chat_media/x+x-123456789.png";LocalMediaIndex index=new LocalMediaIndex(Arrays.asList(asset));
  boolean matched=false;for(String ref:MediaReferences.collect(row,true))if(index.resolve(ref).equals(asset.id))matched=true;
  if(!matched)throw new AssertionError("Stickers filter stays empty although custom sticker Content names an imported PNG");
  if(MediaReferences.collect(row,false).contains(asset.name))throw new AssertionError("Ordinary chat text must not become an attachment");
  if(MediaReferences.collect(new JSONObject("{\"Content\":\"a message about photo.png\"}"),true).contains("a message about photo.png"))throw new AssertionError("Sentence must not become asset path");
  if(!MediaReferences.collect(new JSONObject("{\"Content\":\"data:image/png;base64,ignored\"}"),true).isEmpty())throw new AssertionError("Embedded content is not a local path");
  System.out.println("4 custom-sticker Content reference checks passed.");
 }
}
