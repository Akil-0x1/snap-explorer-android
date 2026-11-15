package local.snapexplorer.mobile;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;
import java.util.*;import java.util.concurrent.CancellationException;import java.util.function.BooleanSupplier;import java.util.function.LongConsumer;
/** Per-query memory-only index: stable grouped counts, pages and arrow positions. */
final class ConversationResults implements AutoCloseable {
 final SQLiteDatabase index=SQLiteDatabase.create(null);long rawCount,count;
 static final class Page {final List<ChatRepository.Row> rows=new ArrayList<>();final Map<Long,Long> copies=new HashMap<>();}
 private ConversationResults(){index.execSQL(SearchGrouping.SCHEMA);}
 static ConversationResults build(ChatRepository repo,String chat,String query,boolean grouped,String me,BooleanSupplier cancelled,LongConsumer progress){
  ConversationResults out=new ConversationResults();boolean success=false;out.index.beginTransaction();
  try{DatabaseQueries.Query q=ConversationSearch.all(chat,query);try(Cursor c=repo.getReadableDatabase().rawQuery(q.sql,q.args)){
   while(c.moveToNext()){
    if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException();
    ChatRepository.Row r=ChatRepository.readRow(c);List<String> media=new ArrayList<>();String quote="";
    if(grouped){for(MediaItem m:repo.attachments(r))media.add(m.id);ChatRepository.Row reply=repo.replyTarget(r);if(reply!=null)quote=reply.sender+"\n"+(ChatPresentation.mediaOnly(reply.text,reply.kind)?"Attachment":reply.text);}
    String groupSender=SenderDirection.groupSender(me,r.sender);
    String key=SearchGrouping.key(grouped,r.id,r.chat,groupSender,ChatPresentation.body(r.text,r.kind),r.time,media,quote);
    ContentValues v=new ContentValues();v.put("message_id",r.id);v.put("group_key",key);v.put("copies",1);
    if(out.index.insertWithOnConflict("hits",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0)out.index.execSQL("UPDATE hits SET copies=copies+1 WHERE group_key=?",new Object[]{key});
    out.rawCount++;if(out.rawCount%500==0)progress.accept(out.rawCount);
   }
  }
  try(Cursor c=out.index.rawQuery("SELECT COUNT(*) FROM hits",null)){c.moveToFirst();out.count=c.getLong(0);}out.index.setTransactionSuccessful();success=true;
  }finally{out.index.endTransaction();if(!success)out.close();}
  return out;
 }
 Page page(ChatRepository repo,long after,int limit){Page out=new Page();try(Cursor c=index.rawQuery("SELECT message_id,copies FROM hits WHERE position>? ORDER BY position LIMIT ?",new String[]{Long.toString(after),Integer.toString(limit)})){
  while(c.moveToNext()){ChatRepository.Row r=repo.message(c.getLong(0));if(r!=null){out.rows.add(r);out.copies.put(r.id,c.getLong(1));}}
 }return out;}
 ChatRepository.Row at(ChatRepository repo,long position){try(Cursor c=index.rawQuery("SELECT message_id FROM hits WHERE position=?",new String[]{Long.toString(position)})){return c.moveToFirst()?repo.message(c.getLong(0)):null;}}
 public void close(){if(index.isOpen())index.close();}
}
