package local.snapexplorer.mobile;
import android.app.*;import android.content.*;import android.database.*;import android.database.sqlite.*;import android.os.*;import java.io.*;import java.util.*;import java.security.*;
/** Synthetic portable export/import; never opens the user's backup or phone. */
public final class MigrationSmoke extends Instrumentation {
 private int checks;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{run();out.putString("stream","PASS "+checks+" portable-migration Android checks\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));out.putString("stream",s.toString());finish(Activity.RESULT_CANCELED,out);}}
 private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
 private long n(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}
 private Set<String> media(SQLiteDatabase db){Set<String> out=new HashSet<>();try(Cursor c=db.rawQuery("SELECT sha256 FROM media",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
 private Set<String> metadata(SQLiteDatabase db)throws Exception{Set<String> out=new HashSet<>();try(Cursor c=db.rawQuery("SELECT json FROM metadata",null)){while(c.moveToNext()){MessageDigest digest=MessageDigest.getInstance("SHA-256");try(Reader r=MetadataStore.reader(db,c.getString(0))){char[] b=new char[4096];int n;while((n=r.read(b))!=-1)digest.update(new String(b,0,n).getBytes("UTF-8"));}out.add(Arrays.toString(digest.digest()));}}return out;}
 private void run()throws Exception{
  Context c=getTargetContext();File sample=new File(c.getCacheDir(),"migration-synthetic.zip");try(InputStream in=c.getAssets().open("sample.zip");OutputStream out=new FileOutputStream(sample)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  ChatRepository repo=new ChatRepository(c);repo.importArchives(Collections.singletonList(new ChatRepository.Input(sample,"Synthetic migration source",false)),true,(a,b)->{});SQLiteDatabase db=repo.getReadableDatabase();long chats=n(db,"chats"),messages=n(db,"messages"),memories=n(db,"memories");Set<String> media=media(db),data=metadata(db);File backup=new File(c.getCacheDir(),"migration-portable.zip");
  try(OutputStream out=new FileOutputStream(backup)){ArchiveExporter.write(c,db,out,"zip",null,"Synthetic backup");}check(backup.length()>0,"Full portable ZIP generated locally");try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(backup)){check(zip.getEntry("json/chat_history.json")!=null&&zip.getEntry("manifest.json")!=null,"Canonical chats and checksum manifest included");}
  repo.importArchives(Collections.singletonList(new ChatRepository.Input(backup,"Synthetic portable restore",false)),true,(a,b)->{});db=repo.getReadableDatabase();check(n(db,"chats")==chats,"Every chat restored");check(n(db,"messages")==messages,"Messages restored without extra copies from retained sources");check(media(db).equals(media),"Media restored with original content hashes");check(n(db,"memories")==memories,"Memory records restored");check(metadata(db).containsAll(data),"Original raw metadata datasets retained");repo.close();
  // Coexistence is verified by the explicit emulator adb package inventory; Android 11 package visibility hides unrelated apps here.
  check(c.getPackageName().equals("local.snapexplorer.recovery"),"Restore happened in the separate Recovery package");
 }
}
