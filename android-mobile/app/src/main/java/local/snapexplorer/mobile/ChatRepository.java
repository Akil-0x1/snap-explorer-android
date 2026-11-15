package local.snapexplorer.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Private database with non-destructive v1 migration and atomic multi-archive imports. */
public final class ChatRepository extends SQLiteOpenHelper {
    private final Context context;
    public static final class Row {
        public long id,time; public String chat,title,sender,text,date,kind,mediaRefs="[]",replyTo="",externalId=""; public boolean own;
    }
    public static final class Summary { public long chats,messages,media,memories,archives,bytes; }
    public static final class Input {
        public final File file; public final String label; public final boolean temporary;
        public Input(File file,String label,boolean temporary){this.file=file;this.label=label;this.temporary=temporary;}
    }
    public static final class ImportResult {
        public int messages,media,memories,duplicateMessages,duplicateArchives,archives;
        public final List<String> warnings=new ArrayList<>();
    }
    public ChatRepository(Context context){super(context,"chats.db",null,DatabaseSchema.VERSION);this.context=context.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){for(String sql:DatabaseSchema.CORE)db.execSQL(sql);for(String sql:DatabaseSchema.EXTENSIONS)db.execSQL(sql);for(String sql:DatabaseSchema.FROM_V2)db.execSQL(sql);for(String sql:DatabaseSchema.FROM_V3)db.execSQL(sql);for(String sql:DatabaseSchema.FROM_V4)db.execSQL(sql);}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion==1){for(String sql:DatabaseSchema.FROM_V1)db.execSQL(sql);for(String sql:DatabaseSchema.EXTENSIONS)db.execSQL(sql);}
        if(oldVersion<3)for(String sql:DatabaseSchema.FROM_V2)db.execSQL(sql);
        if(oldVersion<4)for(String sql:DatabaseSchema.FROM_V3)db.execSQL(sql);
        if(oldVersion<5)for(String sql:DatabaseSchema.FROM_V4)db.execSQL(sql);
    }
    public ImportResult importArchives(List<Input> inputs,boolean replace,ArchiveImporter.Progress progress)throws Exception{
        if(inputs.isEmpty())throw new IOException("Choose at least one ZIP.");
        SQLiteDatabase db=getWritableDatabase(); ImportResult result=new ImportResult();
        List<File> created=new ArrayList<>(),previous=new ArrayList<>();
        if(replace){try(Cursor c=db.rawQuery("SELECT source_path FROM archives",null)){while(c.moveToNext()){if(c.getString(0)!=null)previous.add(new File(c.getString(0)).getParentFile());}}}
        boolean committed=false,successful=false;
        db.beginTransaction();
        try{
            if(replace)clearTables(db);
            db.delete("settings","key=?",new String[]{"export_catalog"});
            for(Input input:inputs){
                if(Thread.currentThread().isInterrupted())throw new IOException("Import interrupted. Your previous data is unchanged.");
                String hash=hash(input.file);
                try(Cursor c=db.rawQuery("SELECT id FROM archives WHERE source_hash=?",new String[]{hash})){if(c.moveToFirst()){result.duplicateArchives++;continue;}}
                String archiveId=UUID.randomUUID().toString();
                File stage=new File(context.getFilesDir(),"archives/"+archiveId);
                if(!stage.mkdirs())throw new IOException("Could not prepare private import storage.");created.add(stage);
                Map<String,Integer> occurrences=new HashMap<>();
                int priorCount=result.messages;
                ArchiveImporter.Report report=ArchiveImporter.readFull(input.file,stage,message->{
                    String identity=MessageIdentity.key(message);String occurrenceKey=message.origin+":"+identity;int n=occurrences.containsKey(occurrenceKey)?occurrences.get(occurrenceKey):0;occurrences.put(occurrenceKey,n+1);
                    String fingerprint=MessageIdentity.fingerprint(identity,n);
                    ContentValues chat=new ContentValues();chat.put("id",message.conversationId);chat.put("title",message.conversationName);
                    db.insertWithOnConflict("chats",null,chat,SQLiteDatabase.CONFLICT_IGNORE);
                    long existing=findExisting(db,message,fingerprint);
                    if(existing<0){fingerprint+=":id:"+digest(message.externalId.getBytes(StandardCharsets.UTF_8));existing=findFingerprint(db,fingerprint);}
                    long id;
                    if(existing>0){
                        id=existing;result.duplicateMessages++;
                        ContentValues enrich=new ContentValues();
                        // Merge references instead of replacing good data with an emptier export.
                        if(findFingerprint(db,fingerprint)==0||findFingerprint(db,fingerprint)==id)enrich.put("fingerprint",fingerprint);
                        String refs="[]";try(Cursor c=db.rawQuery("SELECT media_refs,reply_to,external_id FROM messages WHERE id=?",new String[]{Long.toString(id)})){
                            if(c.moveToFirst()){
                                refs=mergeRefs(c.getString(0),new JSONArray(message.mediaRefs).toString());
                                if(c.getString(1).isEmpty()&&!message.replyTo.isEmpty())enrich.put("reply_to",message.replyTo);
                                if(c.getString(2).isEmpty()&&!message.externalId.isEmpty())enrich.put("external_id",message.externalId);
                            }
                        }
                        enrich.put("media_refs",refs);db.update("messages",enrich,"id=?",new String[]{Long.toString(id)});
                    }else{
                        ContentValues row=new ContentValues();row.put("chat",message.conversationId);row.put("sender",message.sender);row.put("body",message.text);row.put("kind",message.type);row.put("date",message.date);row.put("epoch",message.timestamp);
                        row.put("media_refs",new JSONArray(message.mediaRefs).toString());row.put("reply_to",message.replyTo);row.put("external_id",message.externalId);row.put("own",message.own?1:0);row.put("fingerprint",fingerprint);
                        id=db.insertOrThrow("messages",null,row);
                        ContentValues search=new ContentValues();search.put("docid",id);search.put("body",message.text);search.put("sender",message.sender);db.insertOrThrow("message_search",null,search);result.messages++;
                    }
                    ContentValues relation=new ContentValues();relation.put("archive_id",archiveId);relation.put("message_id",id);db.insertWithOnConflict("archive_messages",null,relation,SQLiteDatabase.CONFLICT_IGNORE);
                },progress,hash);
                for(MediaItem item:report.media){
                    ContentValues values=new ContentValues();values.put("id",item.id);values.put("stored_path",item.storedPath);values.put("original_path",item.originalPath);values.put("name",item.name);values.put("mime",item.mime);values.put("kind",item.kind);values.put("source",item.source);values.put("epoch",item.epoch);put(values,"latitude",item.latitude);put(values,"longitude",item.longitude);values.put("bytes",item.bytes);values.put("sha256",item.sha256);values.put("archive_id",archiveId);
                    if(db.insertWithOnConflict("media",null,values,SQLiteDatabase.CONFLICT_IGNORE)!=-1)result.media++;
                    else {try(Cursor c=db.rawQuery("SELECT stored_path FROM media WHERE id=?",new String[]{item.id})){if(c.moveToFirst()&&!item.storedPath.equals(c.getString(0)))new File(item.storedPath).delete();}}
                }
                for(MemoryItem memory:report.memories){ContentValues values=new ContentValues();values.put("id",memory.id);values.put("epoch",memory.epoch);values.put("date",memory.date);values.put("media_type",memory.mediaType);put(values,"latitude",memory.latitude);put(values,"longitude",memory.longitude);values.put("media_id",memory.mediaId);values.put("archive_id",archiveId);if(db.insertWithOnConflict("memories",null,values,SQLiteDatabase.CONFLICT_IGNORE)!=-1)result.memories++;}
                for(Map.Entry<String,String> entry:report.metadata.entrySet()){
                    ContentValues values=new ContentValues();values.put("id",archiveId+":"+entry.getKey());values.put("filename",entry.getKey());values.put("json",MetadataFiles.store(stage,entry.getValue()));values.put("archive_id",archiveId);db.insertOrThrow("metadata",null,values);
                    if(entry.getKey().endsWith("friends.json"))enrichNames(db,entry.getValue());
                }
                File original=new File(stage,"source.zip");
                if(!input.temporary||!input.file.renameTo(original)){
                    // All inputs are app-owned private source copies. A hard link avoids a
                    // second 6GB temporary copy; both names remain read-only in our flows.
                    try{Files.createLink(original.toPath(),input.file.toPath());}
                    catch(IOException|UnsupportedOperationException|SecurityException linkUnavailable){
                        if(stage.getUsableSpace()<input.file.length()+64L*1024*1024)throw new IOException("Not enough free space to retain the source ZIP safely. Free more storage and retry.");
                        Files.copy(input.file.toPath(),original.toPath());
                    }
                }
                ContentValues values=new ContentValues();values.put("id",archiveId);values.put("label",input.label);values.put("source_hash",hash);values.put("imported_at",System.currentTimeMillis());values.put("messages",result.messages-priorCount);values.put("media",report.media.size());values.put("bytes",original.length());values.put("source_path",original.getAbsolutePath());org.json.JSONObject quality=new org.json.JSONObject();quality.put("messages",report.messages);quality.put("skipped",report.skipped);quality.put("files",report.files);quality.put("warnings",new JSONArray(report.warnings));values.put("report",quality.toString());db.insertOrThrow("archives",null,values);
                result.archives++;result.warnings.addAll(report.warnings);
            }
            if(result.archives>0){reconcileMediaLinks(db);rebuildAttachmentIndex(db);}
            db.setTransactionSuccessful();successful=true;
        }finally{try{db.endTransaction();committed=successful;}catch(RuntimeException failure){for(File dir:created)deleteOwned(dir);throw failure;}if(!committed)for(File dir:created)deleteOwned(dir);}
        if(committed&&replace)for(File dir:previous)deleteOwned(dir);
        return result;
    }
    private List<MediaItem> mediaCatalog(SQLiteDatabase db){
        List<MediaItem> allMedia=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,sha256,stored_path,original_path,name,mime,kind,source,epoch,bytes,latitude,longitude FROM media",null)){while(c.moveToNext()){MediaItem m=new MediaItem();m.id=c.getString(0);m.sha256=c.getString(1);m.storedPath=c.getString(2);m.originalPath=c.getString(3);m.name=c.getString(4);m.mime=c.getString(5);m.kind=c.getString(6);m.source=c.getString(7);m.epoch=c.getLong(8);m.bytes=c.getLong(9);m.latitude=c.isNull(10)?null:c.getDouble(10);m.longitude=c.isNull(11)?null:c.getDouble(11);allMedia.add(m);}}
        return allMedia;
    }
    private void reconcileMediaLinks(SQLiteDatabase db)throws Exception{
        List<MediaItem> allMedia=mediaCatalog(db);
        Map<String,String> metadata=new LinkedHashMap<>();
        try(Cursor c=db.rawQuery("SELECT archive_id,filename,json FROM metadata WHERE lower(filename) LIKE '%memories_history.json'",null)){while(c.moveToNext())metadata.put(c.getString(0)+"/"+c.getString(1),c.getString(2));}
        for(MemoryItem m:ArchiveImporter.relinkMemories(metadata,allMedia)){
            if(m.mediaId.isEmpty())continue;ContentValues v=new ContentValues();v.put("media_id",m.mediaId);db.update("memories",v,"id=? AND COALESCE(media_id,'')=''",new String[]{m.id});
            ContentValues details=new ContentValues();if(m.epoch>0)details.put("epoch",m.epoch);if(m.latitude!=null)details.put("latitude",m.latitude);if(m.longitude!=null)details.put("longitude",m.longitude);if(details.size()>0)db.update("media",details,"id=?",new String[]{m.mediaId});
        }
        reconcileChatRefs(db,allMedia);
    }
    private void reconcileChatRefs(SQLiteDatabase db,List<MediaItem> allMedia)throws Exception{
        final long[] current={0};final String[] before={""};
        ChatParser.Sink linker=ArchiveImporter.mediaLinker(allMedia,m->{String refs=new JSONArray(m.mediaRefs).toString();if(!refs.equals(before[0])){ContentValues v=new ContentValues();v.put("media_refs",refs);db.update("messages",v,"id=?",new String[]{Long.toString(current[0])});}});
        try(Cursor c=db.rawQuery("SELECT id,chat,sender,body,kind,date,epoch,media_refs FROM messages WHERE media_refs!='[]'",null)){while(c.moveToNext()){
            current[0]=c.getLong(0);before[0]=c.getString(7);ChatMessage m=new ChatMessage(c.getString(1),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6));JSONArray refs=new JSONArray(before[0]);for(int i=0;i<refs.length();i++)m.mediaRefs.add(refs.optString(i));linker.accept(m);
        }}
    }
    private static void put(ContentValues values,String key,Double value){if(value==null)values.putNull(key);else values.put(key,value);}
    private static void enrichNames(SQLiteDatabase db,String json){
        try{org.json.JSONObject root=new org.json.JSONObject(json);Iterator<String> keys=root.keys();while(keys.hasNext()){JSONArray list=root.optJSONArray(keys.next());if(list==null)continue;for(int i=0;i<list.length();i++){org.json.JSONObject person=list.optJSONObject(i);if(person==null)continue;String user=person.optString("Username",""),name=person.optString("Display Name","");if(!user.isEmpty()&&!name.isEmpty()){ContentValues values=new ContentValues();values.put("title",name);db.update("chats",values,"id=?",new String[]{user});}}}}catch(Exception ignored){ }
    }
    private long findFingerprint(SQLiteDatabase db,String fingerprint){try(Cursor c=db.rawQuery("SELECT id FROM messages WHERE fingerprint=?",new String[]{fingerprint})){return c.moveToFirst()?c.getLong(0):0;}}
    private long findExisting(SQLiteDatabase db,ChatMessage m,String fingerprint)throws Exception{
        if(!m.externalId.isEmpty())try(Cursor c=db.rawQuery("SELECT id FROM messages WHERE chat=? AND external_id=? LIMIT 1",new String[]{m.conversationId,m.externalId})){if(c.moveToFirst())return c.getLong(0);}
        try(Cursor c=db.rawQuery("SELECT id,external_id FROM messages WHERE fingerprint=?",new String[]{fingerprint})){if(c.moveToFirst()){String prior=c.getString(1);return !m.externalId.isEmpty()&&!prior.isEmpty()&&!m.externalId.equals(prior)?-1:c.getLong(0);}}
        String old=fingerprint(m)+fingerprint.substring(fingerprint.lastIndexOf(':'));
        long previous=findFingerprint(db,old);if(previous>0){try(Cursor c=db.rawQuery("SELECT external_id FROM messages WHERE id=?",new String[]{Long.toString(previous)})){if(c.moveToFirst()&&!m.externalId.isEmpty()&&!c.getString(0).isEmpty()&&!m.externalId.equals(c.getString(0)))return -1;}return previous;}
        // Upgrade legacy v0.1 rows once, preserving same-second duplicate occurrences.
        try(Cursor c=db.rawQuery("SELECT id FROM messages WHERE fingerprint IS NULL AND id NOT IN (SELECT alias_id FROM message_aliases) AND chat=? AND sender=? AND body=? AND epoch=? AND (epoch!=0 OR date=?) AND upper(trim(kind))=upper(trim(?)) ORDER BY id LIMIT 1",new String[]{m.conversationId,m.sender,m.text,Long.toString(m.timestamp),m.date,m.type})){if(c.moveToFirst()){long id=c.getLong(0);ContentValues v=new ContentValues();v.put("fingerprint",fingerprint);db.update("messages",v,"id=?",new String[]{Long.toString(id)});return id;}}
        return 0;
    }
    public static String mergeRefs(String old,String added)throws Exception{LinkedHashSet<String> refs=new LinkedHashSet<>();for(String json:new String[]{old,added}){JSONArray array=new JSONArray(json==null?"[]":json);for(int i=0;i<array.length();i++)refs.add(array.optString(i));}return new JSONArray(refs).toString();}
    private static String fingerprint(ChatMessage m)throws Exception{return digest((m.conversationId+"\u0000"+m.sender+"\u0000"+m.timestamp+"\u0000"+(m.timestamp==0?m.date:"")+"\u0000"+m.text+"\u0000"+m.type).getBytes(StandardCharsets.UTF_8));}
    public static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Interrupted");digest.update(b,0,n);}}return hex(digest.digest());}
    private static String digest(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void clearTables(SQLiteDatabase db){db.delete("settings","key=?",new String[]{"export_catalog"});for(String table:new String[]{"asset_tags","dataset_catalog","message_aliases","message_media","message_search","archive_messages","messages","chats","memories","media","metadata","archives"})db.execSQL("DELETE FROM "+table);}
    public void clear(){SQLiteDatabase db=getWritableDatabase();List<File> dirs=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT source_path FROM archives",null)){while(c.moveToNext())if(c.getString(0)!=null)dirs.add(new File(c.getString(0)).getParentFile());}db.beginTransaction();try{clearTables(db);db.setTransactionSuccessful();}finally{db.endTransaction();}for(File dir:dirs)deleteOwned(dir);db.execSQL("VACUUM");}
    private void deleteOwned(File directory){
        try{File root=new File(context.getFilesDir(),"archives").getCanonicalFile();File dir=directory.getCanonicalFile();if(!dir.getPath().startsWith(root.getPath()+File.separator))return;deleteTree(dir);}catch(IOException ignored){ }
    }
    private static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
    public void cleanupOrphans(){
        Set<String> retained=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT source_path FROM archives",null)){while(c.moveToNext())try{if(c.getString(0)!=null)retained.add(new File(c.getString(0)).getParentFile().getCanonicalPath());}catch(IOException ignored){}}
        File root=new File(context.getFilesDir(),"archives");File[] folders=root.listFiles();if(folders==null)return;
        for(File folder:folders)try{if(folder.isDirectory()&&folder.getName().matches("[0-9a-fA-F-]{36}")&&!retained.contains(folder.getCanonicalPath()))deleteOwned(folder);}catch(IOException ignored){}
    }
    public List<Input> retainedSources()throws Exception{List<Input> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT source_path,label,source_hash FROM archives ORDER BY imported_at",null)){while(c.moveToNext()){String path=c.getString(0);if(path==null)throw new IOException("A retained source is missing. Rebuild cancelled to preserve your archive.");File f=new File(path);if(!f.isFile()||!hash(f).equals(c.getString(2)))throw new IOException("A retained source is missing or changed. Rebuild cancelled to preserve all existing data.");result.add(new Input(f,c.getString(1),false));}}return result;}
    public Summary summary(){Summary r=new Summary();try(Cursor c=getReadableDatabase().rawQuery("SELECT (SELECT COUNT(*) FROM chats),(SELECT COUNT(*) FROM messages),(SELECT COUNT(*) FROM media),(SELECT COUNT(*) FROM memories),(SELECT COUNT(*) FROM archives),(SELECT COALESCE(SUM(bytes),0) FROM media)",null)){if(c.moveToFirst()){r.chats=c.getLong(0);r.messages=c.getLong(1);r.media=c.getLong(2);r.memories=c.getLong(3);r.archives=c.getLong(4);r.bytes=c.getLong(5);}}return r;}
    public List<Row> chats(){return chatList("");}
    public List<Row> chatList(String filter){List<Row> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT c.id,c.title,COUNT(m.id),MAX(m.epoch) FROM chats c JOIN messages m ON m.chat=c.id WHERE (c.title LIKE ? OR c.id LIKE ?) AND NOT EXISTS(SELECT 1 FROM message_aliases a WHERE a.alias_id=m.id) GROUP BY c.id,c.title ORDER BY MAX(m.epoch) DESC,c.title",new String[]{"%"+filter+"%","%"+filter+"%"})){while(c.moveToNext()){Row r=new Row();r.chat=c.getString(0);r.title=c.getString(1);r.text=c.getLong(2)+" messages";r.time=c.getLong(3);rows.add(r);}}return rows;}
    private static final String COLUMNS=DatabaseQueries.MESSAGES;
    private List<Row> rows(DatabaseQueries.Query q){return queryRows(q.sql,q.args);}
    public List<Row> messages(String chat,long time,long id,long through){return rows(DatabaseQueries.before(chat,time,id,through));}
    public List<Row> messagesAfter(String chat,long time,long id){return rows(DatabaseQueries.after(chat,time,id));}
    public List<Row> search(String query){return search(query,null,null,null);}
    public List<Row> search(String query,String chat,Long since,Long until){return rows(DatabaseQueries.search(query,chat,since,until));}
    public long messageAtDate(String chat,long epoch){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM messages WHERE chat=? AND epoch>=? AND id NOT IN (SELECT alias_id FROM message_aliases) ORDER BY epoch,id LIMIT 1",new String[]{chat,Long.toString(epoch)})){if(c.moveToFirst())return c.getLong(0);}return 0;}
    public Row replyTarget(Row row){if(row.replyTo==null||row.replyTo.isEmpty())return null;List<Row> rows=queryRows(COLUMNS+"WHERE m.chat=? AND (m.external_id=? OR CAST(m.id AS TEXT)=?) LIMIT 1",new String[]{row.chat,row.replyTo,row.replyTo});return rows.isEmpty()?null:rows.get(0);}
    static Row readRow(Cursor c){Row r=new Row();r.id=c.getLong(0);r.chat=c.getString(1);r.title=c.getString(2);r.sender=c.getString(3);r.text=c.getString(4);r.date=c.getString(5);r.time=c.getLong(6);r.kind=c.getString(7);r.mediaRefs=c.getString(8);r.replyTo=c.getString(9);r.externalId=c.getString(10);r.own=c.getInt(11)!=0;return r;}
    public Row message(long id){List<Row> result=queryRows(COLUMNS+"WHERE m.id=?",new String[]{Long.toString(id)});return result.isEmpty()?null:result.get(0);}
    private List<Row> queryRows(String sql,String[] args){List<Row> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext())rows.add(readRow(c));}return rows;}
    /** Resolve only unambiguous local references, never an unrelated same-day file. */
    public List<MediaItem> attachments(Row row){
        List<MediaItem> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        try{JSONArray refs=new JSONArray(row.mediaRefs);
            for(int i=0;i<refs.length();i++){
                String ref=refs.optString(i);if(ref.startsWith("http:")||ref.startsWith("https:"))continue;
                String name=ref.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);if(name.isEmpty())continue;
                String columns="SELECT id,stored_path,original_path,name,mime,kind,source,epoch,latitude,longitude,bytes,sha256 FROM media WHERE ";
                List<MediaItem> matches=attachmentQuery(columns+"id=? OR original_path=?",new String[]{ref,ref});
                if(matches.isEmpty())matches=attachmentQuery(columns+"name=?",new String[]{name});
                if(matches.isEmpty()&&name.length()>=12){String pattern="%"+name.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";matches=attachmentQuery(columns+"name LIKE ? ESCAPE '\\'",new String[]{pattern});}
                if(matches.size()!=1)continue;
                MediaItem item=matches.get(0);File local=new File(item.storedPath).getCanonicalFile();
                if(local.isFile()&&local.getPath().startsWith(context.getFilesDir().getCanonicalPath()+File.separator)&&ids.add(item.id))result.add(item);
            }
        }catch(Exception ignored){}return result;
    }
    private List<MediaItem> attachmentQuery(String sql,String[] args){
        List<MediaItem> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql+" LIMIT 2",args)){
            while(c.moveToNext()){MediaItem item=new MediaItem();item.id=c.getString(0);item.storedPath=c.getString(1);item.originalPath=c.getString(2);item.name=c.getString(3);item.mime=c.getString(4);item.kind=c.getString(5);item.source=c.getString(6);item.epoch=c.getLong(7);item.latitude=c.isNull(8)?null:c.getDouble(8);item.longitude=c.isNull(9)?null:c.getDouble(9);item.bytes=c.getLong(10);item.sha256=c.getString(11);result.add(item);}
        }return result;
    }
    private void rebuildAttachmentIndex(SQLiteDatabase db){
        db.delete("message_media",null,null);
        try(Cursor cursor=db.rawQuery("SELECT id,media_refs FROM messages WHERE media_refs!='[]'",null)){
            while(cursor.moveToNext()){Row row=new Row();row.id=cursor.getLong(0);row.mediaRefs=cursor.getString(1);for(MediaItem item:attachments(row)){ContentValues link=new ContentValues();link.put("message_id",row.id);link.put("media_id",item.id);db.insertWithOnConflict("message_media",null,link,SQLiteDatabase.CONFLICT_IGNORE);}}
        }
    }
    public long canonicalId(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT canonical_id FROM message_aliases WHERE alias_id=?",new String[]{Long.toString(id)})){return c.moveToFirst()?c.getLong(0):id;}}
    public Set<String> conflictingSenderFlags(String chat){Set<String> result=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT sender FROM messages m WHERE chat=? AND "+DatabaseQueries.VISIBLE+" GROUP BY sender HAVING MIN(own)<>MAX(own)",new String[]{chat})){while(c.moveToNext())result.add(c.getString(0));}return result;}
    public List<String> senders(String chat){List<String> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT sender FROM messages WHERE chat=? ORDER BY sender LIMIT 500",new String[]{chat})){while(c.moveToNext())result.add(c.getString(0));}return result;}
    public long searchCount(String chat,String query){DatabaseQueries.Query q=ConversationSearch.count(chat,query);try(Cursor c=getReadableDatabase().rawQuery(q.sql,q.args)){return c.moveToFirst()?c.getLong(0):0;}}
    public List<Row> searchMatches(String chat,String query,long time,long id){return rows(ConversationSearch.page(chat,query,time,id));}
    public Row searchMatch(String chat,String query,long time,long id,int direction){List<Row> found=rows(ConversationSearch.match(chat,query,time,id,direction));return found.isEmpty()?null:found.get(0);}
    public void refreshMediaLinks()throws Exception{SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{reconcileChatRefs(db,mediaCatalog(db));rebuildAttachmentIndex(db);db.setTransactionSuccessful();}finally{db.endTransaction();}}

    public String setting(String key,String fallback){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):fallback;}}
    public void putSetting(String key,String value){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
}
