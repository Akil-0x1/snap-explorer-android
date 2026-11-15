package local.snapexplorer.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** JVM tests with synthetic inputs only. No Android device or private data required. */
public final class ImporterTest {
    static int passed;
    interface Check { void run() throws Exception; }
    static void check(String name, Check check) throws Exception { check.run(); passed++; System.out.println("PASS " + name); }
    static void require(boolean truth, String reason) { if(!truth) throw new AssertionError(reason); }
    static String page(String message) {
        return "<html><h1>Chat History with Alex</h1><div class='rightpanel'><div><h4>Alex</h4><span>TEXT</span><p>"+message+"</p><h6>2026-11-01 12:00:00 UTC</h6></div></div></html>";
    }
    static File zip(String... pairs) throws Exception {
        File file=File.createTempFile("synthetic-snap-", ".zip"); file.deleteOnExit();
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))) {
            for(int i=0;i<pairs.length;i+=2){z.putNextEntry(new ZipEntry(pairs[i]));z.write(pairs[i+1].getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        }
        return file;
    }
    static ArchiveImporter.Report read(File zip,List<ChatMessage> messages) throws Exception { return ArchiveImporter.read(zip,messages::add,(done,total)->{}); }
    static void rejects(File zip) throws Exception {
        try {read(zip,new ArrayList<>());}catch(IOException expected){return;}
        throw new AssertionError("Expected import rejection");
    }
    public static void main(String[] args) throws Exception {
        check("HTML-only ZIP works without JSON or media",()->{
            List<ChatMessage> out=new ArrayList<>(); ArchiveImporter.Report r=read(zip("index.html","<html></html>","html/chat_history/subpage_alex.html",page("Hello &amp; goodbye")),out);
            require(r.usedHtml&&r.messages==1,"one HTML message");require(out.get(0).text.equals("Hello & goodbye"),"entity decode");require(out.get(0).conversationName.equals("Alex"),"conversation name");
        });
        check("Wrapper directory supported",()->{List<ChatMessage> out=new ArrayList<>();read(zip("export/html/chat_history/subpage_alex.html",page("wrapped")),out);require(out.size()==1,"wrapper import");});
        check("HTML wins when JSON duplicates it",()->{List<ChatMessage> out=new ArrayList<>();read(zip("html/chat_history/subpage_alex.html",page("one"),"json/chat_history.json","{\"Alex\":[{\"From\":\"Alex\",\"Content\":\"one\",\"Created\":\"2026-11-01 12:00:00 UTC\"}]}"),out);require(out.size()==1,"no duplicates across formats");});
        check("JSON-only chat ZIP",()->{List<ChatMessage> out=new ArrayList<>();ArchiveImporter.Report r=read(zip("json/chat_history.json","{\"Alex\":[{\"From\":\"Alex\",\"Content\":\"JSON example\",\"Created\":\"2026-11-01 12:00:00 UTC\"}]}"),out);require(!r.usedHtml&&out.get(0).text.equals("JSON example"),"JSON parse");});
        check("Wrapped JSON layout",()->{List<ChatMessage> out=new ArrayList<>();ChatParser.json("{\"Received Saved Chat History\":{\"Alex\":[{\"From\":\"Alex\",\"Content\":\"wrapped\",\"Created\":\"2026-11-01 12:00:00 UTC\"}]}}",out::add);require(out.size()==1,"wrapped JSON");});
        check("HTML scripts are not message text",()->{List<ChatMessage> out=new ArrayList<>();ChatParser.html("subpage_alex.html",page("Hi<script>alert('x')</script><b> there</b>"),out::add);require(!out.get(0).text.contains("alert"),"script content omitted");});
        check("Unsafe paths rejected",()->rejects(zip("../outside.txt","x","html/chat_history/subpage_alex.html",page("safe"))));
        check("Windows paths rejected",()->require(!ArchiveImporter.safeName("C:\\private.txt")&&!ArchiveImporter.safeName("/absolute/file"),"unsafe path check"));
        check("Unsupported archive rejected",()->rejects(zip("index.html","<html>index only</html>")));
        check("Zero-message import rejected",()->rejects(zip("html/chat_history/subpage_alex.html","<html>No known messages</html>")));
        check("Media placeholders kept",()->{List<ChatMessage> out=new ArrayList<>();ChatParser.html("subpage_alex.html",page("").replace("<span>TEXT</span>","<span>MEDIA</span>"),out::add);require(out.get(0).text.equals("[MEDIA]"),"placeholder");});
        check("Unrecognised date preserves message",()->{List<ChatMessage> out=new ArrayList<>();ChatParser.html("subpage_alex.html",page("kept").replace("2026-11-01 12:00:00 UTC","Unknown date"),out::add);require(out.size()==1&&out.get(0).timestamp==0&&out.get(0).date.equals("Unknown date"),"preserve raw date");});
        check("Known timestamps are UTC",()->require(ChatParser.timestamp("2026-11-01 12:00:00 UTC")==java.time.Instant.parse("2026-11-01T12:00:00Z").toEpochMilli(),"UTC conversion"));
        check("Expanded text read is bounded",()->{try{ArchiveImporter.readBounded(new ByteArrayInputStream(new byte[20]),10);}catch(IOException expected){return;}throw new AssertionError("size limit");});
        check("Bundled demo has two chats and five messages",()->{List<ChatMessage> out=new ArrayList<>();ArchiveImporter.Report r=read(new File(args[0]),out);require(r.messages==5&&r.files==2,"demo import");});
        System.out.println(passed+" importer tests passed.");
    }
}
