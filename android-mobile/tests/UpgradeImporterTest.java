package local.snapexplorer.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.json.JSONObject;
import org.json.JSONArray;

/** Full-import tests use synthetic bytes and metadata only; no personal archives. */
public final class UpgradeImporterTest {
    static int passed;
    interface Check { void run() throws Exception; }
    static void check(String name,Check action) throws Exception { action.run();passed++;System.out.println("PASS "+name); }
    static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static String page(String title,String sender,String text,String attributes){return "<h1>Chat History with "+title+"</h1><div class='rightpanel'><div "+attributes+"><h4>"+sender+"</h4><span>TEXT</span><p>"+text+"</p><h6>2026-11-01 12:00:00 UTC</h6></div></div>";}
    static File zip(String...pairs)throws Exception{File file=File.createTempFile("synthetic-full-",".zip");file.deleteOnExit();try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){for(int n=0;n<pairs.length;n+=2){zip.putNextEntry(new ZipEntry(pairs[n]));zip.write(pairs[n+1].getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return file;}
    static ArchiveImporter.Report read(File file,List<ChatMessage> messages)throws Exception{return ArchiveImporter.readFull(file,Files.createTempDirectory("synthetic-stage-").toFile(),messages::add,(done,total)->{});}
    static String payloadHash(String value)throws Exception{return ArchiveImporter.hex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    static JSONObject dataset(String filename,String path,String value)throws Exception{return new JSONObject().put("filename",filename).put("path",path).put("sha256",payloadHash(value));}
    public static void main(String[]args)throws Exception{
        check("Full HTML-only import",()->{List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report r=read(zip("html/chat_history/subpage_alex.html",page("Alex","Alex","hello","")),messages);require(r.messages==1&&r.usedHtml,"HTML full import");require(r.sourceHash.length()==64,"source SHA256");});
        check("HTML media refs resolve within archive",()->{List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report r=read(zip("wrapper/html/chat_history/subpage_alex.html",page("Alex","Alex","<img src='../../chat_media/a.jpg'>",""),"wrapper/chat_media/a.jpg","synthetic image"),messages);require(r.media.size()==1,"media copied");require(messages.get(0).mediaRefs.contains(r.media.get(0).id),"SHA media link");require(messages.get(0).mediaRefs.contains("wrapper/chat_media/a.jpg"),"resolved original path");});
        check("Remote references are never staged or attached",()->{List<ChatMessage> messages=new ArrayList<>();read(zip("html/chat_history/subpage_alex.html",page("Alex","Alex","<img src='https://example.invalid/private.jpg'><a href='javascript:alert(1)'>x</a>","")),messages);require(messages.get(0).mediaRefs.isEmpty(),"remote refs omitted");});
        check("Decoded traversal media refs rejected",()->{require(ChatParser.localReference("html/chat_history/subpage_x.html","../../../secret.jpg").isEmpty(),"escaping ref");require(ChatParser.localReference("html/chat_history/subpage_x.html","%2Fetc/passwd").isEmpty(),"encoded absolute");});
        check("Media-only ZIP imports",()->{ArchiveImporter.Report r=read(zip("memories/2026-11-01_1200-demo.jpg","synthetic bytes"),new ArrayList<>());require(r.messages==0&&r.media.size()==1,"media-only");require(r.media.get(0).epoch!=0,"filename time");});
        check("Duplicate media content has one canonical blob",()->{List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report r=read(zip("chat_media/a.jpg","same","memories/b.png","same","html/chat_history/subpage_alex.html",page("Alex","Alex","<img src='../../memories/b.png'>","")),messages);require(r.media.size()==1,"content dedup");File blob=new File(r.media.get(0).storedPath);require(blob.getParentFile().listFiles().length==1,"one physical blob");require(messages.get(0).mediaRefs.contains(r.media.get(0).id),"duplicate alias resolves");});
        check("Memory metadata links by date and coordinates",()->{String metadata="{\"Saved Media\":[{\"Date\":\"2026-11-01 12:00:00 UTC\",\"Media Type\":\"Image\",\"Location\":\"Latitude, Longitude: 40.5, -123.9\"}]}";ArchiveImporter.Report r=read(zip("json/memories_history.json",metadata,"memories/2026-11-01_1200-demo.jpg","image"),new ArrayList<>());require(r.memories.size()==1&&!r.memories.get(0).mediaId.isEmpty(),"memory link");require(r.memories.get(0).latitude==40.5&&r.memories.get(0).longitude==-123.9,"coordinates");require(r.media.get(0).latitude==40.5,"media location propagated");});
        check("Missing Memories remain metadata with warning",()->{ArchiveImporter.Report r=read(zip("json/memories_history.json","{\"Saved Media\":[{\"Date\":\"2026-11-01 12:00:00 UTC\",\"Media Type\":\"Image\",\"Media Download Url\":\"https://example.invalid/private\"}]}"),new ArrayList<>());require(r.memories.size()==1&&r.memories.get(0).mediaId.isEmpty(),"missing retained");require(!r.warnings.isEmpty(),"missing warning");});
        check("Invalid GPS is never treated as a location",()->{ArchiveImporter.Report r=read(zip("json/memories_history.json","{\"Saved Media\":[{\"Date\":\"2026-11-01\",\"Latitude\":900,\"Longitude\":0}]}"),new ArrayList<>());require(r.memories.get(0).latitude==null,"invalid GPS");});
        check("JSON enriches HTML without duplicate messages",()->{String json="{\"alex\":[{\"From\":\"Alex\",\"Content\":\"hello\",\"Created\":\"2026-11-01 12:00:00 UTC\",\"Media IDs\":[\"abc12345\"],\"Message ID\":\"message-one\",\"Reply To\":\"message-zero\",\"IsSender\":true}]}";List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report r=read(zip("html/chat_history/subpage_123.html",page("Alex","Alex","hello",""),"json/chat_history.json",json),messages);require(r.messages==1,"formats deduplicated");ChatMessage message=messages.get(0);require(message.mediaRefs.contains("abc12345")&&message.externalId.equals("message-one")&&message.replyTo.equals("message-zero")&&message.own,"JSON metadata enrichment");});
        check("JSON-only messages survive mixed exports in same chat",()->{String json="{\"alex\":[{\"From\":\"Alex\",\"Content\":\"hello\",\"Created\":\"2026-11-01 12:00:00 UTC\"},{\"From\":\"Alex\",\"Content\":\"extra\",\"Created\":\"2026-11-01 12:01:00 UTC\"}]}";List<ChatMessage> messages=new ArrayList<>();read(zip("html/chat_history/subpage_123.html",page("Alex","Alex","hello",""),"json/chat_history.json",json),messages);require(messages.size()==2&&messages.get(1).conversationId.equals("123"),"extra JSON message shares HTML chat");});
        check("Friends metadata gives JSON chat display names",()->{List<ChatMessage> messages=new ArrayList<>();read(zip("json/friends.json","{\"Friends\":[{\"Username\":\"alex\",\"Display Name\":\"Alex Example\"}]}","json/chat_history.json","{\"alex\":[{\"From\":\"alex\",\"Content\":\"hi\",\"Created\":\"2026-11-01 12:00:00 UTC\"}]}"),messages);require(messages.get(0).conversationName.equals("Alex Example"),"friendly chat title");});
        check("Explicit HTML reply metadata retained",()->{List<ChatMessage> messages=new ArrayList<>();ChatParser.html("subpage_x.html",page("Alex","Alex","hi","data-message-id='b' data-reply-to='a'"),messages::add);require(messages.get(0).externalId.equals("b")&&messages.get(0).replyTo.equals("a"),"explicit reply IDs");});
        check("Location and snap metadata retained without guessing messages",()->{ArchiveImporter.Report r=read(zip("json/location_history.json","{\"Locations\":[{\"Latitude\":10,\"Longitude\":20}]}","json/snap_history.json","{\"Sent\":[{\"From\":\"me\",\"Created\":\"2026-11-01\"}]}"),new ArrayList<>());require(r.metadata.size()==2&&r.messages==0,"raw datasets available to insights");});
        check("Dangerous path rejects full import",()->{try{read(zip("../outside.txt","bad","memories/a.jpg","image"),new ArrayList<>());}catch(IOException expected){return;}throw new AssertionError("unsafe full import");});
        check("Portable archive media IDs resolve by content hash",()->{String sha="2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881";String json="{\"alex\":[{\"From\":\"alex\",\"Content\":\"photo\",\"Created\":\"2026-11-01\",\"Media IDs\":[\""+sha+"\"]}]}";List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report r=read(zip("json/chat_history.json",json,"media/"+sha+".jpg","x"),messages);require(r.media.get(0).id.equals(sha)&&messages.get(0).mediaRefs.contains(sha),"portable media ID");});
        check("Quoted HTML does not replace the new message body",()->{List<ChatMessage> messages=new ArrayList<>();String html=page("Alex","Alex","new message","data-reply-to='old'").replace("<p>new message</p>","<blockquote><p>old quote</p></blockquote><p>new message</p>");ChatParser.html("subpage_x.html",html,messages::add);require(messages.get(0).text.equals("new message")&&messages.get(0).replyTo.equals("old"),"reply body");});
        check("Deep JSON is rejected before recursive parsing",()->{StringBuilder text=new StringBuilder();for(int i=0;i<200;i++)text.append('[');for(int i=0;i<200;i++)text.append(']');try{read(zip("json/account.json",text.toString()),new ArrayList<>());}catch(IOException expected){return;}throw new AssertionError("depth limit");});
        check("JSON group title is retained",()->{List<ChatMessage> messages=new ArrayList<>();ChatParser.json("{\"group-id\":[{\"From\":\"sam\",\"Content\":\"hi\",\"Created\":\"2026-11-01\",\"Conversation Title\":\"Weekend plans\"}]}",messages::add);require(messages.get(0).conversationName.equals("Weekend plans"),"group title");});
        check("Same-timestamp Memories without IDs are preserved",()->{ArchiveImporter.Report r=read(zip("json/memories_history.json","{\"Saved Media\":[{\"Date\":\"2026-11-01\",\"Media Type\":\"Image\"},{\"Date\":\"2026-11-01\",\"Media Type\":\"Image\"}]}"),new ArrayList<>());require(r.memories.size()==2&&!r.memories.get(0).id.equals(r.memories.get(1).id),"distinct occurrence IDs");});
        check("Portable index preserves metadata variants without importing old chats",()->{
            String old="{\"old\":[{\"From\":\"old\",\"Content\":\"old copy\",\"Created\":\"2026-11-01\"}]}";
            String other="{\"old\":[]}";
            String canonical="{\"alex\":[{\"From\":\"alex\",\"Content\":\"canonical merged chat\",\"Created\":\"2026-11-02\"}]}";
            String index=new JSONObject().put("datasets",new JSONArray().put(dataset("chat_history.json","raw-metadata/one.json",old)).put(dataset("chat_history.json","raw-metadata/two.json",other))).toString();
            List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report r=read(zip("json/chat_history.json",canonical,"json/snap_explorer_metadata.json",index,"raw-metadata/one.json",old,"raw-metadata/two.json",other),messages);
            require(r.metadata.size()==2&&r.metadata.containsValue(old)&&r.metadata.containsValue(other),"all variants retained");require(messages.size()==1&&messages.get(0).text.equals("canonical merged chat"),"canonical records authoritative");
        });
        check("Portable metadata checksum failure rejects import",()->{
            String payload="{\"Friends\":[]}";JSONObject row=dataset("friends.json","raw-metadata/one.json",payload).put("sha256",String.join("",Collections.nCopies(64,"0")));
            String index=new JSONObject().put("datasets",new JSONArray().put(row)).toString();
            try{read(zip("json/snap_explorer_metadata.json",index,"raw-metadata/one.json",payload),new ArrayList<>());}catch(IOException expected){return;}throw new AssertionError("payload hash");
        });
        check("Portable metadata index cannot reference arbitrary archive paths",()->{
            String payload="{}";String index=new JSONObject().put("datasets",new JSONArray().put(dataset("account.json","json/account.json",payload))).toString();
            try{read(zip("json/snap_explorer_metadata.json",index,"json/account.json",payload),new ArrayList<>());}catch(IOException expected){return;}throw new AssertionError("restricted payload paths");
        });
        check("Empty metadata bundle does not erase legacy canonical chat records",()->{
            List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report r=read(zip("json/snap_explorer_metadata.json","{\"datasets\":[]}","json/chat_history.json","{\"alex\":[{\"From\":\"alex\",\"Content\":\"legacy\",\"Created\":\"2026-11-01\"}]}"),messages);
            require(messages.size()==1&&r.metadata.isEmpty(),"legacy canonical rows preserved");
        });
        check("Split-part Memories can be relinked after media import",()->{
            ArchiveImporter.Report meta=read(zip("json/memories_history.json","{\"Saved Media\":[{\"Date\":\"2026-11-01 12:00:00 UTC\",\"Media Type\":\"Image\"}]}"),new ArrayList<>());
            ArchiveImporter.Report files=read(zip("memories/2026-11-01_1200-local.jpg","synthetic"),new ArrayList<>());
            require(meta.memories.get(0).mediaId.isEmpty(),"initial part is missing media");List<MemoryItem> linked=ArchiveImporter.relinkMemories(meta.metadata,files.media);require(linked.size()==1&&linked.get(0).mediaId.equals(files.media.get(0).id),"split relink");
        });
        check("All restored Memories variants can be relinked",()->{
            Map<String,String> meta=new LinkedHashMap<>();meta.put("memories_history.json","{\"Saved Media\":[{\"Date\":\"2026-11-01\",\"Media Type\":\"Image\"}]}");meta.put("variant-2/memories_history.json","{\"Saved Media\":[{\"Date\":\"2026-11-02\",\"Media Type\":\"Image\"}]}");require(ArchiveImporter.relinkMemories(meta,new ArrayList<>()).size()==2,"all variants");
        });
        check("Ambiguous same-time media does not guess a Memory link",()->{
            Map<String,String> meta=new LinkedHashMap<>();meta.put("memories_history.json","{\"Saved Media\":[{\"Date\":\"2026-11-01 12:00:00 UTC\",\"Media Type\":\"Image\"}]}");ArchiveImporter.Report files=read(zip("memories/2026-11-01_1200-one.jpg","one","memories/2026-11-01_1200-two.jpg","two"),new ArrayList<>());require(ArchiveImporter.relinkMemories(meta,files.media).get(0).mediaId.isEmpty(),"no ambiguous link");
        });
        check("Local stickers and Bitmoji assets are indexed without fetching URLs",()->{
            String json="{\"alex\":[{\"From\":\"alex\",\"Created\":\"2026-11-01 12:00:00 UTC\",\"Media Type\":\"STICKER\",\"Sticker Path\":\"stickers/example.png\"}]}";
            List<ChatMessage> out=new ArrayList<>();ArchiveImporter.Report r=read(zip("json/chat_history.json",json,"stickers/example.png","sticker","bitmoji/avatar.png","avatar","json/bitmoji.json","{\"Avatar URL\":\"https://example.invalid/private-avatar.png\"}"),out);
            require(r.media.size()==2&&out.get(0).mediaRefs.size()>=2&&r.metadata.containsKey("bitmoji.json"),"local assets and raw metadata");
        });
        check("Individual ZIP bound supports files above six gigabytes",()->require(ArchiveImporter.MAX_ZIP>6L*1024*1024*1024,"large archive bound"));
        System.out.println(passed+" full importer tests passed.");
    }
}
