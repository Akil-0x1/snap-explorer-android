package local.snapexplorer.mobile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.compress.archivers.zip.*;
import org.json.JSONObject;
/** Streams only chat metadata from retained ZIPs, including folder ZIP parts. */
public final class ChatSourceReader {
 public interface Sink{void accept(String source,ChatMessage message)throws Exception;}
 public static final class Result{public int html,json;public long messages;}
 public static Result read(File file,String prefix,String chat,String title,Sink sink)throws Exception{
  Result result=new Result();try(ZipFile zip=new ZipFile(file)){Enumeration<ZipArchiveEntry> entries=zip.getEntries();while(entries.hasMoreElements()){ZipArchiveEntry entry=entries.nextElement();if(entry.isDirectory()||!ArchiveImporter.safeName(entry.getName())||!candidate(entry.getName()))continue;try(InputStream input=zip.getInputStream(entry)){visit(input,entry.getName(),prefix,chat,title,sink,result,0);}}}return result;
 }
 private static void visit(InputStream input,String path,String prefix,String chat,String title,Sink sink,Result result,int depth)throws Exception{
  if(Thread.currentThread().isInterrupted())throw new IOException("Repair interrupted");String leaf=path.substring(path.lastIndexOf('/')+1),lower=leaf.toLowerCase(Locale.ROOT);
  if(lower.startsWith("subpage_")&&lower.endsWith(".html")){
   String id=leaf.substring(8,leaf.length()-5);if(!matches(id,chat,title))return;final long[] seen={0};
   try(Reader reader=new BufferedReader(new InputStreamReader(new NonClosing(input),StandardCharsets.UTF_8))){ChatParser.htmlStream(path,reader,m->{if(matches(m.conversationId,chat,title)||matches(m.conversationName,chat,title)){sink.accept(prefix+":html:"+path,canonical(m,chat,title));seen[0]++;}});}if(seen[0]>0)result.html++;result.messages+=seen[0];
  }else if(lower.equals("chat_history.json")&&!path.contains("raw-metadata/")){
   final long[] seen={0};try(Reader reader=new BufferedReader(new InputStreamReader(new NonClosing(input),StandardCharsets.UTF_8))){JsonRecords.read(reader,(group,record)->{
    if(!matches(group,chat,title)&&!matches(ChatParser.value(record,"Conversation Title","Conversation Name","Chat Name"),chat,title))return;
    if(ChatParser.value(record,"From","Sender","Username").isEmpty()||ChatParser.value(record,"Created","Date","Timestamp").isEmpty())return;
    ChatParser.json("{"+JSONObject.quote(group)+":["+record.toString()+"]}",m->{sink.accept(prefix+":json:"+path,canonical(m,chat,title));seen[0]++;});
   });}if(seen[0]>0)result.json++;result.messages+=seen[0];
  }else if(lower.endsWith(".zip")&&!path.toLowerCase(Locale.ROOT).contains("chat_media/")&&!path.toLowerCase(Locale.ROOT).contains("originals/")){
   if(depth>=2)throw new IOException("Retained nested ZIP depth is unsupported");int files=0;try(ZipArchiveInputStream nested=new ZipArchiveInputStream(new NonClosing(input))){ZipArchiveEntry entry;while((entry=nested.getNextZipEntry())!=null){if(++files>100000)throw new IOException("Too many retained source entries");if(!entry.isDirectory()&&ArchiveImporter.safeName(entry.getName()))visit(nested,entry.getName(),prefix+":part:"+path,chat,title,sink,result,depth+1);}}
  }
 }
 private static boolean candidate(String path){String lower=path.toLowerCase(Locale.ROOT);String name=lower.substring(lower.lastIndexOf('/')+1);return name.equals("chat_history.json")||name.startsWith("subpage_")&&name.endsWith(".html")||name.endsWith(".zip")&&!lower.contains("chat_media/")&&!lower.contains("originals/");}
 private static final class NonClosing extends FilterInputStream{NonClosing(InputStream in){super(in);}@Override public void close(){}}
 private static boolean matches(String value,String chat,String title){String v=normal(value);return !v.isEmpty()&&(v.equals(normal(chat))||v.equals(normal(title))||v.equals(normal(title).replace(' ','_')));}
 private static String normal(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
 private static ChatMessage canonical(ChatMessage m,String chat,String title){ChatMessage result=new ChatMessage(chat,title,m.sender,m.text,m.type,m.date,m.timestamp);result.externalId=m.externalId;result.replyTo=m.replyTo;result.own=m.own;result.mediaRefs.addAll(m.mediaRefs);return result;}
 private ChatSourceReader(){}
}
