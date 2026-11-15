package local.snapexplorer.mobile;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Explicit user-initiated, local-only exports. Database cursors are streamed. */
public final class ArchiveExporter {
 private static final String COLUMNS="m.id,m.chat,c.title,m.sender,m.body,m.kind,m.date,m.epoch,m.media_refs,m.reply_to,m.external_id,m.own";
 public static void write(Context context,SQLiteDatabase db,OutputStream out,String format,String chat,String title)throws Exception {
  if(format.equals("csv")||format.equals("xlsx")||format.equals("ods")){
   List<String[]> rows=ArchiveStats.read(context,db).rows();if(format.equals("csv"))ExportFormats.csv(new OutputStreamWriter(out,StandardCharsets.UTF_8),rows);else ExportFormats.spreadsheet(out,format,rows);return;
  }
  if(format.equals("pdf")){pdf(context,db,out,chat,title);return;}
  if(format.equals("media-pdf")||format.equals("memories-pdf")){galleryPdf(context,db,out,format.equals("memories-pdf"));return;}
  if(format.equals("stats-pdf")){statsPdf(context,db,out);return;}
  if(format.equals("zip")){portable(context,db,out);return;}
  Writer writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
  if(format.equals("json"))json(db,writer,chat,null);
  else if(format.equals("html"))html(db,writer,chat,title,null);
  else text(db,writer,chat,title);
  writer.flush();
 }
 private static Cursor messages(SQLiteDatabase db,String chat){return db.rawQuery("SELECT "+COLUMNS+" FROM messages m JOIN chats c ON c.id=m.chat"+(chat==null?"":" WHERE m.chat=?")+" ORDER BY m.chat,m.epoch,m.id",chat==null?null:new String[]{chat});}
 private static void text(SQLiteDatabase db,Writer out,String chat,String title)throws Exception {
  out.write((title==null?"Snap Explorer archive":title)+"\nExported locally. Dates as recorded in the original export.\n\n");
  try(Cursor c=messages(db,chat)){String current="";while(c.moveToNext()){if(!c.getString(1).equals(current)){current=c.getString(1);out.write("\n=== "+c.getString(2)+" ===\n");}out.write("["+c.getString(6)+"] "+c.getString(3)+": "+c.getString(4)+"\n");if(!c.getString(9).isEmpty())out.write("Reply reference: "+c.getString(9)+"\n");}}
 }
 private static void json(SQLiteDatabase db,Writer out,String chat,Map<String,String> mediaIds)throws Exception {
  out.write('{');String previous=null;boolean firstChat=true,firstMessage=true;
  try(Cursor c=messages(db,chat)){while(c.moveToNext()){
   String id=c.getString(1);if(!id.equals(previous)){if(previous!=null)out.write(']');if(!firstChat)out.write(',');out.write(ExportFormats.json(id)+":[");previous=id;firstChat=false;firstMessage=true;}
   if(!firstMessage)out.write(',');firstMessage=false;
   out.write("{\"Conversation Title\":"+ExportFormats.json(c.getString(2))+",\"From\":"+ExportFormats.json(c.getString(3))+",\"Content\":"+ExportFormats.json(c.getString(4))+",\"Media Type\":"+ExportFormats.json(c.getString(5))+",\"Created\":"+ExportFormats.json(c.getString(6))+",\"IsSender\":"+(c.getInt(11)!=0)+",\"Media IDs\":");
   String refs=c.getString(8);try{JSONArray raw=new JSONArray(refs),normal=new JSONArray();Set<String> unique=new LinkedHashSet<>();for(int i=0;i<raw.length();i++){String ref=raw.optString(i);unique.add(mediaIds!=null&&mediaIds.containsKey(ref)?mediaIds.get(ref):ref);}for(String ref:unique)normal.put(ref);out.write(normal.toString());}catch(Exception ignored){out.write("[]");}
   out.write(",\"Reply To\":"+ExportFormats.json(c.getString(9))+",\"Message ID\":"+ExportFormats.json(c.getString(10))+"}");
  }}
  if(previous!=null)out.write(']');out.write('}');
 }
 private static String header(String title){return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'\"><title>"+ExportFormats.html(title)+"</title><style>body{background:#101713;color:#f0f5ef;font:16px system-ui;max-width:850px;margin:auto;padding:24px}article{background:#1b251f;border-radius:14px;padding:16px;margin:12px 0}small{color:#a6b9aa}b,a{color:#f5e642}p{white-space:pre-wrap;overflow-wrap:anywhere}img,video{max-width:100%;max-height:500px}audio{max-width:100%}</style></head><body><h1>"+ExportFormats.html(title)+"</h1><small>Private offline archive · Dates as recorded</small><p><input id=\"filter\" placeholder=\"Search this page\" style=\"width:95%;padding:12px\"></p><script>addEventListener('DOMContentLoaded',function(){document.getElementById('filter').addEventListener('input',function(){var q=this.value.toLocaleLowerCase();document.querySelectorAll('article,li').forEach(function(item){item.hidden=item.textContent.toLocaleLowerCase().indexOf(q)===-1;});});});</script>";}
 private static void html(SQLiteDatabase db,Writer out,String chat,String title,Map<String,String> media)throws Exception {
  out.write(header(title==null?"Snap Explorer archive":title));
  try(Cursor c=messages(db,chat)){String previous="";while(c.moveToNext()){
   if(!c.getString(1).equals(previous)){previous=c.getString(1);out.write("<h2>"+ExportFormats.html(c.getString(2))+"</h2>");}
   out.write("<article><b>"+ExportFormats.html(c.getString(3))+"</b> <small>"+ExportFormats.html(c.getString(6))+"</small><p>"+ExportFormats.html(c.getString(4))+"</p>");
   if(!c.getString(9).isEmpty())out.write("<small>Reply reference: "+ExportFormats.html(c.getString(9))+"</small>");
   if(media!=null)try{JSONArray refs=new JSONArray(c.getString(8));for(int i=0;i<refs.length();i++){String path=media.get(refs.optString(i));if(path!=null){String safe=ExportFormats.html("../"+path);if(path.matches("(?i).*\\.(jpg|jpeg|png|gif|webp)$"))out.write("<img loading=\"lazy\" src=\""+safe+"\" alt=\"Chat media\">");else if(path.matches("(?i).*\\.(mp4|webm|3gp|mov)$"))out.write("<video controls preload=\"none\" src=\""+safe+"\"></video>");else out.write("<a href=\""+safe+"\">Open local attachment</a>");}}}catch(Exception ignored){}
   out.write("</article>");
  }}out.write("</body></html>");
 }
 private static void pdf(Context context,SQLiteDatabase db,OutputStream out,String chat,String title)throws Exception {
  try(PdfBook book=new PdfBook(title==null?"Snap Explorer conversations":title);Cursor c=messages(db,chat)){
   while(c.moveToNext()){
    book.text("\n"+c.getString(2)+" · "+c.getString(3)+" · "+c.getString(6)+"\n"+c.getString(4));
    if(!c.getString(9).isEmpty())book.text("Reply reference: "+c.getString(9));
    Set<String> shown=new HashSet<>();try{JSONArray refs=new JSONArray(c.getString(8));for(int i=0;i<Math.min(32,refs.length());i++){String ref=refs.optString(i),name=ref.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);try(Cursor media=db.rawQuery("SELECT id,stored_path,kind,name FROM media WHERE id=? OR original_path=? OR name=? LIMIT 3",new String[]{ref,ref,name})){while(media.moveToNext()){if(!shown.add(media.getString(0)))continue;book.text("Attachment: "+media.getString(3));preview(context,book,media.getString(1),media.getString(2));}}}}catch(org.json.JSONException ignored){book.text("Attachment references were not readable.");}
   }
   book.write(out);
  }
 }
 private static void galleryPdf(Context context,SQLiteDatabase db,OutputStream out,boolean memories)throws Exception {
  String sql=memories?"SELECT COALESCE(f.name,'Missing memory media'),f.stored_path,f.kind,m.date,m.latitude,m.longitude FROM memories m LEFT JOIN media f ON f.id=m.media_id ORDER BY m.epoch":"SELECT name,stored_path,kind,CAST(epoch AS TEXT),latitude,longitude FROM media ORDER BY epoch,id";
  try(PdfBook book=new PdfBook(memories?"Snap Explorer Memories":"Snap Explorer media gallery");Cursor c=db.rawQuery(sql,null)){
   while(c.moveToNext()){book.text("\n"+c.getString(0)+"\n"+(memories?c.getString(3):AppStyle.date(context,Long.parseLong(c.getString(3)),"Unknown date")));if(!c.isNull(4)&&!c.isNull(5))book.text("Coordinates: "+c.getDouble(4)+", "+c.getDouble(5));if(c.isNull(1))book.text("No local file was included in the export.");else preview(context,book,c.getString(1),c.getString(2));}
   book.write(out);
  }
 }
 private static void preview(Context context,PdfBook book,String path,String kind)throws Exception {
  android.graphics.Bitmap image=null;
  try{
   File file=privateFile(context,path);
   if("image".equals(kind))image=MediaProcessor.bitmap(file,1100);
   else if("video".equals(kind)){
    android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();try{reader.setDataSource(file.getAbsolutePath());if(android.os.Build.VERSION.SDK_INT>=27)image=reader.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,1000,600);else{String w=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),h=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);if(w!=null&&h!=null&&(long)Integer.parseInt(w)*Integer.parseInt(h)<=16000000)image=reader.getFrameAtTime(0);}}finally{reader.release();}
   }
   if(image!=null)book.image(image);else book.text("["+kind+" attachment · open the local file for playback]");
  }catch(IOException|IllegalArgumentException error){book.text("[Local attachment preview unavailable]");}
  finally{if(image!=null)image.recycle();}
 }
 private static void statsPdf(Context context,SQLiteDatabase db,OutputStream out)throws Exception {
  ArchiveStats stats=ArchiveStats.read(context,db);try(PdfBook book=new PdfBook("Snap Explorer statistics")){
   book.text("Timezone: "+stats.timezone);book.chart("Overview",stats.summary);book.chart("Top conversations",stats.contacts);book.chart("Message types",stats.types);book.chart("Messages by weekday",stats.weekdays);book.chart("Messages by hour",stats.hours);
   for(ArchiveStats.Dataset dataset:stats.datasets){if(dataset.result!=null){book.chart(dataset.label+" · categories",dataset.result.categories);book.chart(dataset.label+" · contacts",dataset.result.contacts);}}
   book.text("\nComplete data tables");for(String[] row:stats.rows())book.text(row[0]+"    "+(row.length>1?row[1]:""));book.write(out);
  }
 }
 private interface Write {void write(OutputStream out)throws Exception;}
 private static void zipped(ZipOutputStream zip,String name,Write source,Map<String,String> hashes)throws Exception {
  zip.putNextEntry(new ZipEntry(name));MessageDigest digest=MessageDigest.getInstance("SHA-256");
  OutputStream sink=new FilterOutputStream(zip){public void write(int b)throws IOException{digest.update((byte)b);out.write(b);}public void write(byte[] b,int off,int len)throws IOException{digest.update(b,off,len);out.write(b,off,len);}public void close()throws IOException{flush();}};
  source.write(sink);sink.flush();zip.closeEntry();hashes.put(name,ExportFormats.hex(digest.digest()));
 }
 private static File privateFile(Context context,String path)throws IOException {
  File file=new File(path).getCanonicalFile(),root=context.getFilesDir().getCanonicalFile();
  if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.isFile())throw new IOException("A retained local file is missing or outside private storage.");return file;
 }
 private static void copy(File file,OutputStream out)throws IOException{try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Export interrupted");out.write(buffer,0,n);}}}
 private static void portable(Context context,SQLiteDatabase db,OutputStream out)throws Exception {
  ZipOutputStream zip=new ZipOutputStream(out);Map<String,String> hashes=new LinkedHashMap<>(),mediaPaths=new HashMap<>(),mediaIds=new HashMap<>();List<String[]> chats=new ArrayList<>(),metadataIndex=new ArrayList<>();
  try(Cursor c=db.rawQuery("SELECT id,title FROM chats ORDER BY title",null)){while(c.moveToNext())chats.add(new String[]{c.getString(0),c.getString(1)});}
  try(Cursor c=db.rawQuery("SELECT id,stored_path,original_path,name,mime FROM media ORDER BY id",null)){
   while(c.moveToNext()){
    File file=privateFile(context,c.getString(1));String extension=c.getString(3).replaceFirst("^.*\\.","").toLowerCase(Locale.ROOT);if(!extension.matches("[a-z0-9]{1,8}"))extension="bin";
    String destination="media/"+(c.getString(0).matches("[a-f0-9]{64}")?c.getString(0):ExportFormats.sha256(c.getString(0)))+"."+extension;
    zipped(zip,destination,sink->copy(file,sink),hashes);mediaPaths.put(c.getString(0),destination);mediaPaths.put(c.getString(2),destination);mediaPaths.put(c.getString(3),destination);mediaIds.put(c.getString(0),c.getString(0));mediaIds.put(c.getString(2),c.getString(0));mediaIds.put(c.getString(3),c.getString(0));
   }
  }
  for(String page:new String[]{"gallery","memories","statistics","map"}){
   zipped(zip,page+".html",sink->{Writer w=new OutputStreamWriter(sink,StandardCharsets.UTF_8);if(page.equals("map"))PortablePages.map(db,w);else if(page.equals("statistics"))PortablePages.statistics(context,db,w);else PortablePages.gallery(context,db,w,mediaPaths,page.equals("memories"));w.flush();},hashes);
  }
  zipped(zip,"json/chat_history.json",sink->{Writer w=new OutputStreamWriter(sink,StandardCharsets.UTF_8);json(db,w,null,mediaIds);w.flush();},hashes);
  zipped(zip,"json/memories_history.json",sink->{Writer w=new OutputStreamWriter(sink,StandardCharsets.UTF_8);w.write("{\"Saved Media\":[");boolean firstMemory=true;try(Cursor m=db.rawQuery("SELECT date,media_type,latitude,longitude,media_id FROM memories ORDER BY epoch",null)){while(m.moveToNext()){if(!firstMemory)w.write(',');firstMemory=false;w.write("{\"Date\":"+ExportFormats.json(m.getString(0))+",\"Media Type\":"+ExportFormats.json(m.getString(1))+",\"Media ID\":"+ExportFormats.json(m.getString(4)));if(!m.isNull(2)&&!m.isNull(3))w.write(",\"Latitude\":"+m.getDouble(2)+",\"Longitude\":"+m.getDouble(3));w.write('}');}}w.write("]}");w.flush();},hashes);
  for(String[] chat:chats){String destination="chats/"+ExportFormats.sha256(chat[0])+".html";zipped(zip,destination,sink->{Writer w=new OutputStreamWriter(sink,StandardCharsets.UTF_8);html(db,w,chat[0],chat[1],mediaPaths);w.flush();},hashes);}
  zipped(zip,"chats/all.html",sink->{Writer w=new OutputStreamWriter(sink,StandardCharsets.UTF_8);html(db,w,null,"All conversations",mediaPaths);w.flush();},hashes);
  StringBuilder index=new StringBuilder(header("Snap Explorer portable archive")+"<p><a href=\"gallery.html\">Media gallery</a> · <a href=\"memories.html\">Memories</a> · <a href=\"statistics.html\">Statistics</a> · <a href=\"map.html\">Offline locations</a></p><p><a href=\"chats/all.html\">Search all conversations</a></p><p>Open any conversation below. Every file is local.</p><ul>");for(String[] chat:chats)index.append("<li><a href=\"chats/").append(ExportFormats.sha256(chat[0])).append(".html\">").append(ExportFormats.html(chat[1])).append("</a></li>");index.append("</ul><p><a href=\"statistics.csv\">Statistics CSV</a></p></body></html>");
  zipped(zip,"index.html",sink->sink.write(index.toString().getBytes(StandardCharsets.UTF_8)),hashes);
  zipped(zip,"statistics.csv",sink->ExportFormats.csv(new OutputStreamWriter(sink,StandardCharsets.UTF_8),ArchiveStats.read(context,db).rows()),hashes);
  Set<String> canonical=new HashSet<>(Arrays.asList("chat_history.json","memories_history.json"));
  try(Cursor c=db.rawQuery("SELECT id,filename,json FROM metadata ORDER BY id",null)){while(c.moveToNext()){
   String stored=c.getString(2),file=c.getString(1).replaceFirst("^.*[/\\\\]", ""),name="raw-metadata/"+ExportFormats.sha256(c.getString(0))+"-"+file.replaceAll("[^A-Za-z0-9._-]","_");
   File raw=MetadataStore.file(db,stored);
   zipped(zip,name,sink->{if(raw!=null)copy(raw,sink);else sink.write(stored.getBytes(StandardCharsets.UTF_8));},hashes);metadataIndex.add(new String[]{c.getString(1),name,hashes.get(name)});
   if(file.matches("[A-Za-z0-9_.-]+\\.json")&&canonical.add(file))zipped(zip,"json/"+file,sink->{if(raw!=null)copy(raw,sink);else sink.write(stored.getBytes(StandardCharsets.UTF_8));},hashes);
  }}
  if(metadataIndex.size()>1000)throw new IOException("This portable backup contains more than 1,000 source datasets. Export smaller archive groups.");
  StringBuilder bundle=new StringBuilder("{\"version\":1,\"datasets\":[");boolean firstDataset=true;for(String[] item:metadataIndex){if(!firstDataset)bundle.append(',');firstDataset=false;bundle.append("{\"filename\":").append(ExportFormats.json(item[0])).append(",\"path\":").append(ExportFormats.json(item[1])).append(",\"sha256\":").append(ExportFormats.json(item[2])).append('}');}bundle.append("]}");
  byte[] bundleBytes=bundle.toString().getBytes(StandardCharsets.UTF_8);if(bundleBytes.length>4*1024*1024)throw new IOException("The metadata bundle index exceeds its 4 MB limit.");
  zipped(zip,"json/snap_explorer_metadata.json",sink->sink.write(bundleBytes),hashes);
  try(Cursor c=db.rawQuery("SELECT id,source_path FROM archives ORDER BY id",null)){while(c.moveToNext()){String path=c.getString(1);if(path==null||path.isEmpty())continue;File original=privateFile(context,path);zipped(zip,"originals/"+ExportFormats.sha256(c.getString(0))+".zip",sink->copy(original,sink),hashes);}}
  StringBuilder manifest=new StringBuilder("{\"format\":\"snap-explorer-portable-v1\",\"algorithm\":\"SHA-256\",\"files\":{");boolean first=true;for(Map.Entry<String,String> e:hashes.entrySet()){if(!first)manifest.append(',');first=false;manifest.append(ExportFormats.json(e.getKey())).append(':').append(ExportFormats.json(e.getValue()));}manifest.append("}}");ExportFormats.entry(zip,"manifest.json",manifest.toString());zip.finish();zip.flush();
 }
 public static List<String> verify(Context context,SQLiteDatabase db)throws Exception {
  List<String> problems=new ArrayList<>();int checked=0,sources=0;
  try(Cursor c=db.rawQuery("PRAGMA integrity_check",null)){while(c.moveToNext())if(!c.getString(0).equals("ok"))problems.add("Database: "+c.getString(0));}
  try(Cursor c=db.rawQuery("SELECT name,stored_path,sha256 FROM media",null)){while(c.moveToNext()){checked++;try{File f=privateFile(context,c.getString(1));if(!fileHash(f).equals(c.getString(2)))problems.add("Media checksum mismatch: "+c.getString(0));}catch(Exception e){problems.add("Missing or unreadable media: "+c.getString(0));}}}
  try(Cursor c=db.rawQuery("SELECT label,source_path,source_hash FROM archives",null)){while(c.moveToNext()){sources++;try{File f=privateFile(context,c.getString(1));if(!fileHash(f).equals(c.getString(2)))problems.add("Retained source checksum mismatch: "+c.getString(0));}catch(Exception e){problems.add("Missing or unreadable retained source: "+c.getString(0));}}}
  String[][] checks={{"Messages missing from search index","SELECT COUNT(*) FROM messages m LEFT JOIN message_search f ON f.docid=m.id WHERE f.docid IS NULL"},{"Search rows without a message","SELECT COUNT(*) FROM message_search f LEFT JOIN messages m ON m.id=f.docid WHERE m.id IS NULL"},{"Archive links without a message","SELECT COUNT(*) FROM archive_messages a LEFT JOIN messages m ON m.id=a.message_id WHERE m.id IS NULL"}};
  for(String[] check:checks){try(Cursor c=db.rawQuery(check[1],null)){if(c.moveToFirst()&&c.getLong(0)>0)problems.add(check[0]+": "+c.getLong(0));}catch(Exception error){problems.add(check[0]+": could not verify index linkage");}}
  if(problems.isEmpty())problems.add("Verified database, search linkage, "+checked+" local media files and "+sources+" retained sources. No integrity problems found.");
  try(Cursor c=db.rawQuery("SELECT label,report FROM archives ORDER BY imported_at",null)){while(c.moveToNext()){String raw=c.getString(1);if(raw==null||raw.isEmpty())continue;try{JSONObject report=new JSONObject(raw);int skipped=report.optInt("skipped",0);JSONArray warnings=report.optJSONArray("warnings");if(skipped>0||(warnings!=null&&warnings.length()>0)){problems.add("Import validation · "+c.getString(0)+": "+skipped+" skipped records");if(warnings!=null){for(int i=0;i<Math.min(20,warnings.length());i++){String warning=warnings.optString(i);problems.add("• "+(warning.length()>1000?warning.substring(0,1000):warning));}if(warnings.length()>20)problems.add("More import notes are retained in the archive report.");}}}catch(JSONException ignored){problems.add("Import validation report could not be read: "+c.getString(0));}}}
  try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM memories WHERE media_id IS NULL OR media_id=''",null)){if(c.moveToFirst()&&c.getLong(0)>0)problems.add("Export limitation: "+c.getLong(0)+" Memories have no local media file. This offline app cannot download them.");}
  return problems;
 }
 private static String fileHash(File file)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Verification interrupted");digest.update(b,0,n);}}return ExportFormats.hex(digest.digest());}
 private ArchiveExporter(){}
}
