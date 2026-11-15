package local.snapexplorer.mobile;
import android.app.*;import android.content.*;import android.database.Cursor;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.zip.*;import java.nio.charset.StandardCharsets;
/** Opaque filename fixtures reproduce an empty sticker gallery without private data. */
public final class StickerSmoke extends Instrumentation {
 private int checks;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{run();out.putString("stream","PASS "+checks+" sticker-gallery Android checks\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));out.putString("stream",s.toString());finish(Activity.RESULT_CANCELED,out);}}
 private void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;}
 private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private long count(ChatRepository repo,String sql){try(Cursor c=repo.getReadableDatabase().rawQuery(sql,null)){c.moveToFirst();return c.getLong(0);}}
 private void add(ZipOutputStream z,String name,byte[] b)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(b);z.closeEntry();}
 private void waitIdle(Activity a)throws Exception{for(int i=0;i<200;i++){waitForIdleSync();if(!(Boolean)field(a,"loading")&&!(Boolean)field(a,"cataloging")){SystemClock.sleep(150);return;}SystemClock.sleep(100);}throw new AssertionError("Gallery busy");}
 private void run()throws Exception{
  Context c=getTargetContext();byte[] png;try(InputStream in=getContext().getAssets().open("sticker.png");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);png=out.toByteArray();}
  for(int scenario=0;scenario<3;scenario++){
   File zip=new File(c.getCacheDir(),"sticker-case-"+scenario+".zip");String file="x+x-123456789.png";try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(zip))){add(z,"chat_media/"+(scenario==2?"unrelated.png":file),png);String json=scenario==1?"{\"pal\":[{\"From\":\"pal\",\"Created\":\"2024-01-02 10:00:00 UTC\",\"Media Type\":\"STICKER\",\"Media IDs\":\"chat_media/"+file+"\"}]}":"{\"My Custom Stickers\":[{\"Sticker ID\":\"different-metadata-id\",\"Content\":\""+file+"\"}]}";add(z,"json/"+(scenario==1?"chat_history.json":"custom_sticker.json"),json.getBytes(StandardCharsets.UTF_8));}
   ChatRepository repo=new ChatRepository(c);repo.importArchives(Collections.singletonList(new ChatRepository.Input(zip,"Opaque sticker reproduction",false)),true,(a,b)->{});ExportCatalog.ensure(repo,(a,b)->{});int expected=scenario==2?0:1;check(count(repo,"SELECT COUNT(*) FROM asset_tags WHERE role='sticker'")==expected,"Sticker tags for scenario "+scenario+" (Content path / chat kind / missing file)");
   if(scenario==0){repo.getWritableDatabase().delete("asset_tags",null,null);repo.putSetting("export_catalog","1:1:1:v3");ExportCatalog.ensure(repo,(a,b)->{});check(count(repo,"SELECT COUNT(*) FROM asset_tags WHERE role='sticker'")==1,"Old catalog is rebuilt without reimporting source copies");}
   repo.close();Activity gallery=startActivitySync(new Intent(c,MediaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitIdle(gallery);runOnMainSync(()->{try{Field f=gallery.getClass().getDeclaredField("kind");f.setAccessible(true);f.set(gallery,"sticker");Method m=gallery.getClass().getDeclaredMethod("reset");m.setAccessible(true);m.invoke(gallery);}catch(Exception e){throw new RuntimeException(e);}});waitIdle(gallery);check(((List<?>)field(gallery,"items")).size()==expected,"Stickers filter includes only existing local assets in scenario "+scenario);
   if(scenario==2)check(((TextView)field(gallery,"emptyLabel")).getText().toString().contains("1 custom-sticker record"),"Empty gallery explains metadata-only sticker record");else{GridView grid=(GridView)field(gallery,"grid");for(int i=0;i<100&&(grid.getChildCount()==0||!decoded(grid.getChildAt(0)));i++)SystemClock.sleep(100);check(grid.getChildCount()>0&&decoded(grid.getChildAt(0)),"Sticker thumbnail decodes from imported local bytes");}
   runOnMainSync(gallery::finish);
  }
 }
 private boolean decoded(View v){if(v instanceof ImageView&&((ImageView)v).getDrawable()!=null)return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(decoded(((ViewGroup)v).getChildAt(i)))return true;return false;}
}
