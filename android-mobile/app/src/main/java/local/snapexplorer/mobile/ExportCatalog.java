package local.snapexplorer.mobile;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.io.*;
import java.util.*;

/** Derived local asset tags and complete dataset inventory. Never fetches references. */
final class ExportCatalog {
 interface Progress{void update(int done,int total);}
 static void ensure(ChatRepository repo,Progress progress)throws Exception{
  SQLiteDatabase db=repo.getWritableDatabase();String signature;
  try(Cursor c=db.rawQuery("SELECT (SELECT COUNT(*) FROM archives)||':'||(SELECT COUNT(*) FROM media)||':'||(SELECT COUNT(*) FROM metadata)||':v4'",null)){c.moveToFirst();signature=c.getString(0);}
  try(Cursor c=db.rawQuery("SELECT value FROM settings WHERE key='export_catalog'",null)){if(c.moveToFirst()&&signature.equals(c.getString(0)))return;}
  repo.refreshMediaLinks();refresh(repo,signature,progress);
 }
 static void refresh(ChatRepository repo,String signature,Progress progress)throws Exception{
  SQLiteDatabase db=repo.getWritableDatabase();db.beginTransaction();
  try{
   db.delete("asset_tags",null,null);db.delete("dataset_catalog",null,null);List<MediaItem> files=new ArrayList<>();
   try(Cursor c=db.rawQuery("SELECT id,name,original_path FROM media",null)){while(c.moveToNext()){MediaItem m=new MediaItem();m.id=c.getString(0);m.name=c.getString(1);m.originalPath=c.getString(2);files.add(m);String role=DataCategories.asset(m.originalPath);if(!role.isEmpty())tag(db,m.id,role);}}
   db.execSQL("INSERT OR IGNORE INTO asset_tags(media_id,role) SELECT DISTINCT link.media_id,'sticker' FROM message_media link JOIN messages m ON m.id=link.message_id WHERE upper(replace(replace(trim(m.kind),'_',''),' ','')) IN ('STICKER','CUSTOMSTICKER')");
   db.execSQL("INSERT OR IGNORE INTO asset_tags(media_id,role) SELECT DISTINCT link.media_id,'avatar' FROM message_media link JOIN messages m ON m.id=link.message_id WHERE upper(trim(m.kind))='BITMOJI'");
   LocalMediaIndex index=new LocalMediaIndex(files);List<String[]> datasets=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id,filename,json FROM metadata ORDER BY filename",null)){while(c.moveToNext())datasets.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});}
   int done=0;for(String[] data:datasets){if(Thread.currentThread().isInterrupted())throw new IOException("Catalog indexing interrupted");String category=DataCategories.dataset(data[1]),role=DataCategories.role(category);long[] counts={0,0,0};String error="";
    try(Reader reader=MetadataStore.reader(db,data[2])){JsonRecords.read(reader,(group,record)->{counts[0]++;String recordRole=role;if(recordRole.isEmpty()){String g=MediaReferences.name(group);if(g.contains("sticker"))recordRole="sticker";else if(g.contains("bitmoji")||g.contains("avatar"))recordRole="avatar";}List<String> refs=MediaReferences.collect(record,!recordRole.isEmpty()||category.equals("Memories"));for(String ref:refs){counts[1]++;String id=index.resolve(ref);if(!id.isEmpty()){counts[2]++;if(!recordRole.isEmpty())tag(db,id,recordRole);}}});}
    catch(Exception failure){if(Thread.currentThread().isInterrupted())throw failure;error="Some records could not be indexed. The original JSON is retained and can still be exported.";}
    ContentValues row=new ContentValues();row.put("metadata_id",data[0]);row.put("category",category);row.put("records",counts[0]);row.put("refs",counts[1]);row.put("matched",counts[2]);row.put("issue",error);db.insertOrThrow("dataset_catalog",null,row);progress.update(++done,datasets.size());
   }
   ContentValues stamp=new ContentValues();stamp.put("key","export_catalog");stamp.put("value",signature);db.insertWithOnConflict("settings",null,stamp,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 private static void tag(SQLiteDatabase db,String id,String role){ContentValues tag=new ContentValues();tag.put("media_id",id);tag.put("role",role);db.insertWithOnConflict("asset_tags",null,tag,SQLiteDatabase.CONFLICT_IGNORE);}
 private ExportCatalog(){}
}
