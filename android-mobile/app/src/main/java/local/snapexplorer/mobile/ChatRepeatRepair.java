package local.snapexplorer.mobile;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.io.*;
import java.util.*;

/** Reversible view repair proven against per-file multiplicities in retained exports. */
final class ChatRepeatRepair {
 static final class Result {long hidden,raw,visible,sourceMessages,htmlText,jsonText,storedText,pairedKeys,matchedKeys,candidates,protectedIds,protectedContext;int html,json;}
 private static final class Record {long id;String external;Record(long id,String external){this.id=id;this.external=external==null?"":external;}}
 static Result run(Context context,ChatRepository repo,String chat,String title)throws Exception{
  SQLiteDatabase db=repo.getWritableDatabase();Result result=new Result();db.beginTransaction();
  try{
   db.execSQL("DROP TABLE IF EXISTS temp.repeat_counts");db.execSQL("DROP TABLE IF EXISTS temp.repeat_rows");
   db.execSQL("CREATE TEMP TABLE repeat_counts(source TEXT,k TEXT,n INTEGER,PRIMARY KEY(source,k))");
   db.execSQL("CREATE TEMP TABLE repeat_rows(id INTEGER PRIMARY KEY,k TEXT,external_id TEXT)");db.execSQL("CREATE INDEX temp.repeat_key ON repeat_rows(k,id)");
   List<String[]> sources=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id,source_path FROM archives WHERE source_path IS NOT NULL",null)){while(c.moveToNext())sources.add(new String[]{c.getString(0),c.getString(1)});}
   String root=new File(context.getFilesDir(),"archives").getCanonicalPath()+File.separator;
   try(SQLiteStatement count=db.compileStatement("INSERT OR REPLACE INTO repeat_counts(source,k,n) VALUES (?,?,COALESCE((SELECT n FROM repeat_counts WHERE source=? AND k=?),0)+1)")){
    for(String[] source:sources){File file=new File(source[1]).getCanonicalFile();if(!file.getPath().startsWith(root)||!file.isFile())continue;
     ChatSourceReader.Result read=ChatSourceReader.read(file,source[0],chat,title,(name,m)->{if(Thread.currentThread().isInterrupted())throw new IOException("Repair interrupted");if(m.timestamp<=0||!"TEXT".equalsIgnoreCase(m.type.trim()))return;if(name.contains(":html:"))result.htmlText++;else result.jsonText++;String key=MessageIdentity.key(m);count.clearBindings();count.bindString(1,name);count.bindString(2,key);count.bindString(3,name);count.bindString(4,key);count.executeInsert();});result.html+=read.html;result.json+=read.json;result.sourceMessages+=read.messages;
    }
   }
   try(Cursor rows=db.rawQuery("SELECT id,sender,body,kind,date,epoch,external_id FROM messages WHERE chat=? ORDER BY id",new String[]{chat});SQLiteStatement insert=db.compileStatement("INSERT INTO repeat_rows(id,k,external_id) VALUES (?,?,?)")){
    while(rows.moveToNext()){result.raw++;if(rows.getLong(5)<=0||!"TEXT".equalsIgnoreCase(rows.getString(3).trim()))continue;result.storedText++;ChatMessage m=new ChatMessage(chat,title,rows.getString(1),rows.getString(2),rows.getString(3),rows.getString(4),rows.getLong(5));insert.clearBindings();insert.bindLong(1,rows.getLong(0));insert.bindString(2,MessageIdentity.key(m));insert.bindString(3,rows.getString(6)==null?"":rows.getString(6));insert.executeInsert();}
   }
   // Both representations must actually contain this logical event. Never infer
   // multiplicity from appearance, minute-level timestamps or a sample alone.
   String expected="SELECT k,MAX(n) n FROM repeat_counts GROUP BY k HAVING SUM(CASE WHEN instr(source,':html:')>0 THEN 1 ELSE 0 END)>0 AND SUM(CASE WHEN instr(source,':json:')>0 THEN 1 ELSE 0 END)>0";
   try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM ("+expected+")",null)){c.moveToFirst();result.pairedKeys=c.getLong(0);}
   try(Cursor c=db.rawQuery("SELECT COUNT(DISTINCT r.k) FROM repeat_rows r JOIN ("+expected+") source ON source.k=r.k",null)){c.moveToFirst();result.matchedKeys=c.getLong(0);}
   try(Cursor groups=db.rawQuery("SELECT expected.k,expected.n FROM ("+expected+") expected JOIN repeat_rows r ON r.k=expected.k GROUP BY expected.k,expected.n HAVING COUNT(r.id)>expected.n",null)){
    while(groups.moveToNext()){
     result.candidates++;String key=groups.getString(0);int keepCount=groups.getInt(1);List<Record> records=new ArrayList<>();try(Cursor rows=db.rawQuery("SELECT id,external_id FROM repeat_rows WHERE k=? ORDER BY id",new String[]{key})){while(rows.moveToNext())records.add(new Record(rows.getLong(0),rows.getString(1)));}
     LinkedHashMap<String,Record> identified=new LinkedHashMap<>();for(Record r:records)if(!r.external.isEmpty())identified.putIfAbsent(r.external,r);
     List<Record> keep=new ArrayList<>(identified.values());Set<Long> keepIds=new HashSet<>();for(Record r:keep)keepIds.add(r.id);
     for(Record r:records)if(keep.size()<keepCount&&r.external.isEmpty()&&keepIds.add(r.id))keep.add(r);
     // Different explicit message IDs always survive, even if one export omitted an event.
     if(keep.isEmpty()||keep.size()>=records.size()){result.protectedIds++;continue;}
     for(Record r:records){ContentValues value=new ContentValues();value.putNull("fingerprint");db.update("messages",value,"id=?",new String[]{Long.toString(r.id)});}
     for(int i=0;i<keep.size();i++){ContentValues value=new ContentValues();value.put("fingerprint",MessageIdentity.fingerprint(key,i));db.update("messages",value,"id=?",new String[]{Long.toString(keep.get(i).id)});}
     int next=0;for(Record r:records){if(keepIds.contains(r.id))continue;Record target=r.external.isEmpty()?keep.get(next++%keep.size()):identified.get(r.external);if(target==null)continue;
      String targetRefs="[]",aliasRefs="[]",targetReply="",aliasReply="";int own=0;
      try(Cursor c=db.rawQuery("SELECT id,media_refs,reply_to,own FROM messages WHERE id IN (?,?)",new String[]{Long.toString(target.id),Long.toString(r.id)})){while(c.moveToNext()){own|=c.getInt(3);if(c.getLong(0)==target.id){targetRefs=c.getString(1);targetReply=c.getString(2);}else{aliasRefs=c.getString(1);aliasReply=c.getString(2);}}}
      if(!targetReply.isEmpty()&&!aliasReply.isEmpty()&&!targetReply.equals(aliasReply)){result.protectedContext++;continue;}
      if(keep.size()>1&&r.external.isEmpty()&&(!targetRefs.equals("[]")||!aliasRefs.equals("[]"))&&!targetRefs.equals(aliasRefs)){result.protectedContext++;continue;}
      ContentValues enrich=new ContentValues();enrich.put("media_refs",ChatRepository.mergeRefs(targetRefs,aliasRefs));enrich.put("own",own);if(targetReply.isEmpty()&&!aliasReply.isEmpty())enrich.put("reply_to",aliasReply);db.update("messages",enrich,"id=?",new String[]{Long.toString(target.id)});
      ContentValues alias=new ContentValues();alias.put("alias_id",r.id);alias.put("canonical_id",target.id);alias.put("chat",chat);db.insertWithOnConflict("message_aliases",null,alias,SQLiteDatabase.CONFLICT_REPLACE);result.hidden++;}
    }
   }
   try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM messages m WHERE m.chat=? AND NOT EXISTS(SELECT 1 FROM message_aliases a WHERE a.alias_id=m.id)",new String[]{chat})){c.moveToFirst();result.visible=c.getLong(0);}
   db.setTransactionSuccessful();return result;
  }finally{try{db.execSQL("DROP TABLE IF EXISTS temp.repeat_counts");db.execSQL("DROP TABLE IF EXISTS temp.repeat_rows");}finally{db.endTransaction();}}
 }
 static void undo(ChatRepository repo,String chat){repo.getWritableDatabase().delete("message_aliases","chat=?",new String[]{chat});}
 private ChatRepeatRepair(){}
}
