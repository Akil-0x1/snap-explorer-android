package local.snapexplorer.mobile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded archive reader. No URLs are fetched and archive paths are never used as output paths. */
public final class ArchiveImporter {
    public static final long MAX_ZIP = 16L * 1024 * 1024 * 1024;
    public static final int MAX_ENTRY = 32 * 1024 * 1024;
    private static final long MAX_TEXT_TOTAL = 128L * 1024 * 1024;
    private static final long MAX_MEDIA_ENTRY = 2048L * 1024 * 1024;
    private static final long MAX_MEDIA_TOTAL = 32L * 1024 * 1024 * 1024;
    private static final int MAX_MESSAGES = 200000;
    public interface Progress { void update(int complete, int total); }
    public static final class Report {
        public int messages, skipped, files;
        public boolean usedHtml;
        public final List<MediaItem> media = new ArrayList<>();
        public final List<MemoryItem> memories = new ArrayList<>();
        public final Map<String,String> metadata = new LinkedHashMap<>();
        public final List<String> warnings = new ArrayList<>();
        public String sourceHash = "";
        public long bytes;
    }
    private static final class Index {
        int repeatedMediaNames;
        final List<ZipArchiveEntry> html = new ArrayList<>(), chats = new ArrayList<>(), json = new ArrayList<>(), media = new ArrayList<>(), nested = new ArrayList<>();
    }
    public static boolean safeName(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || name.contains("\\") || name.contains(":") || name.indexOf('\0') >= 0) return false;
        for (String part : name.split("/")) if (part.equals("..")) return false;
        return true;
    }
    static boolean assetPath(String path){return path.matches("(?:.*/)?(?:chat_media|media|memories|custom[_ -]?stickers?|stickers?|bitmojis?|avatars?|profile_media)/.+");}
    private static Index index(ZipFile zip) throws IOException {
        Index index = new Index();
        boolean portable=false;ZipArchiveEntry manifest=zip.getEntry("manifest.json");
        if(manifest!=null&&!manifest.isDirectory()&&manifest.getSize()<=4L*1024*1024){try{portable=new JSONObject(new String(text(zip,manifest),StandardCharsets.UTF_8)).optString("format").equals("snap-explorer-portable-v1");}catch(org.json.JSONException ignored){}}
        Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries(); Set<String> names = new HashSet<>(); int count = 0;
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (++count > 100000) throw new IOException("Too many files in this ZIP.");
            String name = entry.getName();
            if (!safeName(name)) throw new IOException("Unsafe path in archive. Import cancelled.");
            if (entry.isDirectory()) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            boolean mediaEntry=assetPath(lower)&&!entry.isDirectory();
            if(!names.add(name)){
                if(!mediaEntry)throw new IOException("Duplicate chat or metadata path in archive. Import cancelled because the intended data is ambiguous.");
                index.repeatedMediaNames++;
            }

            if (lower.matches("(?:.*/)?subpage_[^/]+\\.html")) index.html.add(entry);
            if (lower.endsWith(".json") && !lower.startsWith("raw-metadata/") && !lower.contains("/raw-metadata/") && !lower.equals("manifest.json")) {
                index.json.add(entry);
                if (leaf(lower).equals("chat_history.json")) index.chats.add(entry);
            }
            if((mediaEntry || ChatParser.mediaExtension(lower))&&!(portable&&lower.equals("statistics.csv")))index.media.add(entry);
            if(lower.endsWith(".zip")&&!lower.startsWith("originals/")&&!lower.contains("/originals/")&&!lower.startsWith("chat_media/")&&!lower.contains("/chat_media/"))index.nested.add(entry);
        }
        return index;
    }
    /** Compatibility entry point for text-only callers; full imports should use readFull. */
    public static Report read(File archive, ChatParser.Sink sink, Progress progress) throws Exception {
        requireArchiveSize(archive);
        Report report = new Report(); long textBytes = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Index index = index(zip); report.usedHtml = !index.html.isEmpty();
            List<ZipArchiveEntry> chosen = report.usedHtml ? index.html : index.chats;
            if (chosen.isEmpty()) throw new IOException("No supported chat files found. Expected html/chat_history/subpage_*.html or json/chat_history.json.");
            for (ZipArchiveEntry entry : chosen) {
                interrupted(); byte[] bytes = text(zip, entry); textBytes += bytes.length;
                if (textBytes > MAX_TEXT_TOTAL) throw new IOException("Chat text exceeds the 128 MB limit.");
                String text = new String(bytes, StandardCharsets.UTF_8);
                ChatParser.Result result = report.usedHtml ? ChatParser.html(entry.getName(), text, sink) : ChatParser.json(text, sink);
                report.messages += result.messages; report.skipped += result.skipped; report.files++;
                progress.update(report.files, chosen.size());
            }
        }
        if (report.messages == 0) throw new IOException("No messages could be read. Your export may use a different layout. Your previous import is unchanged.");
        return report;
    }
    /** Stages local media and source records for source-aware repository deduplication. */
    public static Report readFull(File archive, File stagingDir, ChatParser.Sink sink, Progress progress) throws Exception {
        return readFull(archive,stagingDir,sink,progress,null);
    }
    public static Report readFull(File archive,File stagingDir,ChatParser.Sink sink,Progress progress,String knownSourceHash)throws Exception {
        return readFull(archive,stagingDir,sink,progress,knownSourceHash,0);
    }
    private static Report readFull(File archive,File stagingDir,ChatParser.Sink sink,Progress progress,String knownSourceHash,int nesting)throws Exception {
        if(nesting>2)throw new IOException("Nested ZIP folders exceed the supported depth.");
        requireArchiveSize(archive);
        if ((!stagingDir.exists() && !stagingDir.mkdirs()) || !stagingDir.isDirectory()) throw new IOException("Could not create private import staging folder.");
        File blobs = new File(stagingDir, "media");
        if ((!blobs.exists() && !blobs.mkdirs()) || !blobs.isDirectory()) throw new IOException("Could not create private media staging folder.");
        Report report = new Report(); report.sourceHash = knownSourceHash==null?hashFile(archive):knownSourceHash;
        long textBytes = 0, mediaBytes = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Index index = index(zip);
            if(index.repeatedMediaNames>0)warning(report,index.repeatedMediaNames+" repeated media filenames were handled by archive entry identity; different contents were kept and identical contents deduplicated.");
            int total = index.json.size() + index.html.size() + index.media.size(); int done = 0;
            if(!index.nested.isEmpty()){
                if(index.nested.size()>32)throw new IOException("Choose at most32 ZIP parts in one folder.");
                java.util.Map<String,MediaItem> unique=new java.util.LinkedHashMap<>();
                for(ZipArchiveEntry part:index.nested){
                    File temporary=File.createTempFile("nested-input-",".zip",stagingDir);
                    try{copyEntry(zip,part,temporary,MAX_ZIP);Report child=readFull(temporary,stagingDir,message->{message.origin="part:"+part.getName()+":"+message.origin;sink.accept(message);},progress,null,nesting+1);report.messages+=child.messages;report.skipped+=child.skipped;report.files+=child.files;report.bytes+=child.bytes;report.usedHtml|=child.usedHtml;for(MediaItem m:child.media)unique.put(m.id,m);report.memories.addAll(child.memories);for(Map.Entry<String,String> m:child.metadata.entrySet())report.metadata.put(part.getName()+"/"+m.getKey(),m.getValue());for(String note:child.warnings)warning(report,note);if(report.bytes>MAX_MEDIA_TOTAL)throw new IOException("Expanded media exceeds32GB in selected folder.");}finally{temporary.delete();}
                }
                report.media.addAll(unique.values());if(total==0)return report;mediaBytes=report.bytes;
            }
            if (total == 0) throw new IOException("No readable export data found. Choose a folder containing Snapchat ZIPs, JSON files, chat pages or local media.");
            List<ChatMessage> htmlMessages = new ArrayList<>(), jsonMessages = new ArrayList<>();
            String metadataIndex = null, metadataIndexPath = null;
            Map<File,String> largeLabels=new HashMap<>();List<File> largeChats=new ArrayList<>();List<String[]> largeHtml=new ArrayList<>();
            for (ZipArchiveEntry entry : index.json) {
                interrupted();
                if(entry.getSize()>1024*1024 && !leaf(entry.getName()).equalsIgnoreCase("snap_explorer_metadata.json")){
                    File data=File.createTempFile("dataset-",".json",stagingDir);String sha=copyEntry(zip,entry,data,4L*1024*1024*1024);String name=leaf(entry.getName()),key=report.metadata.containsKey(name)?entry.getName():name;report.metadata.put(key,MetadataFiles.reference(data,sha));if(index.chats.contains(entry)){largeChats.add(data);largeLabels.put(data,entry.getName());}report.files++;progress.update(++done,total);continue;
                }
                byte[] bytes = text(zip, entry); textBytes += bytes.length; checkTextBudget(textBytes);
                String value = new String(bytes, StandardCharsets.UTF_8);
                ChatParser.requireJsonDepth(value);
                String name = leaf(entry.getName());
                if (name.equalsIgnoreCase("snap_explorer_metadata.json")) {
                    if (metadataIndex != null) throw new IOException("Several portable metadata indexes were found.");
                    if (bytes.length > 4 * 1024 * 1024) throw new IOException("Portable metadata index exceeds the 4 MB limit.");
                    metadataIndex = value; metadataIndexPath = entry.getName();
                    report.files++; progress.update(++done, total); continue;
                }
                if (report.metadata.containsKey(name)) {
                    // Preserve both raw payloads without silently changing which dataset analytics uses.
                    report.metadata.put(entry.getName(), value);
                    warning(report, "Several JSON files share the name " + name + "; the first is used for summary metadata.");
                } else report.metadata.put(name, value);
                if (index.chats.contains(entry)) {
                    ChatParser.Result result = ChatParser.json(value, message -> {message.origin="json:"+report.sourceHash+":"+entry.getName();boundedAdd(jsonMessages,message);});
                    report.skipped += result.skipped;
                } else {
                    try { new JSONObject(value); }
                    catch (org.json.JSONException objectError) {
                        try { new JSONArray(value); }
                        catch (org.json.JSONException arrayError) { warning(report, "Unrecognised JSON metadata: " + name); }
                    }
                }
                report.files++; progress.update(++done, total);
            }
            for (ZipArchiveEntry entry : index.html) {
                interrupted();
                if(entry.getSize()>1024*1024){File data=File.createTempFile("chat-page-",".html",stagingDir);copyEntry(zip,entry,data,4L*1024*1024*1024);largeHtml.add(new String[]{data.getAbsolutePath(),entry.getName()});report.usedHtml=true;report.files++;progress.update(++done,total);continue;}
                byte[] bytes = text(zip, entry); textBytes += bytes.length; checkTextBudget(textBytes);
                ChatParser.Result result = ChatParser.html(entry.getName(), new String(bytes, StandardCharsets.UTF_8), message -> {message.origin="html:"+report.sourceHash+":"+entry.getName();boundedAdd(htmlMessages,message);});
                report.skipped += result.skipped; report.files++; progress.update(++done, total);
            }
            report.usedHtml = !htmlMessages.isEmpty()||!largeHtml.isEmpty();
            Map<String,String> friends = friendNames(report.metadata);
            for(Map.Entry<String,String> dataset:report.metadata.entrySet())if(leaf(dataset.getKey()).equalsIgnoreCase("friends.json")&&MetadataFiles.isReference(dataset.getValue())){
                try(java.io.Reader reader=new java.io.InputStreamReader(new java.io.FileInputStream(MetadataFiles.referencedFile(dataset.getValue())),StandardCharsets.UTF_8)){JsonRecords.read(reader,(group,person)->{String user=ChatParser.value(person,"Username"),display=ChatParser.value(person,"Display Name");if(!user.isEmpty()&&!display.isEmpty())friends.put(normalized(user),display);});}
            }
            List<ChatMessage> merged = mergeMessages(htmlMessages, jsonMessages, friends);
            Set<String> uniqueMedia = new HashSet<>();
            Map<String,String> aliases = new HashMap<>();
            for(MediaItem prior:report.media){uniqueMedia.add(prior.id);addMediaAliases(aliases,prior);}
            for (ZipArchiveEntry entry : index.media) {
                interrupted();
                if (entry.getSize() > MAX_MEDIA_ENTRY) throw new IOException("A media file exceeds the 2 GB limit.");
                if (entry.getSize() >= 0 && mediaBytes > MAX_MEDIA_TOTAL - entry.getSize()) throw new IOException("Expanded media exceeds the 32 GB import limit.");
                long available = stagingDir.getUsableSpace();
                if (available > 0 && entry.getSize() > 0 && available < entry.getSize() + 64L * 1024 * 1024) throw new IOException("Not enough free storage to import media safely.");
                MediaItem media = copyMedia(zip, entry, blobs); mediaBytes += media.bytes;
                if (mediaBytes > MAX_MEDIA_TOTAL) throw new IOException("Expanded media exceeds the 32 GB import limit.");
                addMediaAliases(aliases, media);
                if (uniqueMedia.add(media.id)) report.media.add(media);
                else {
                    // Keep path aliases in memory for linking, while report.media contains one physical item per hash.
                    for (MediaItem existing : report.media) if (existing.id.equals(media.id)) {
                        if (existing.source.equals("chat") && media.source.equals("memories")) existing.source = "memories";
                        break;
                    }
                }
                report.files++; progress.update(++done, total);
            }
            // Large JSON messages are parsed record-by-record, and matched against
            // bounded HTML candidates without retaining a second full message list.
            LargeMerge matcher=new LargeMerge(merged,friends);
            for(File data:largeChats){try(java.io.Reader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(data),StandardCharsets.UTF_8))){
                JsonRecords.read(reader,(group,record)->{if(ChatParser.value(record,"From","Sender","Username").isEmpty()||ChatParser.value(record,"Created","Date","Timestamp").isEmpty())return;
                    ChatParser.Result parsed=ChatParser.json("{"+JSONObject.quote(group)+":["+record.toString()+"]}",message->{message.origin="json:"+report.sourceHash+":"+largeLabels.get(data);ChatMessage extra=matcher.merge(message);if(extra!=null){enrichMediaRefs(extra,report.media,aliases);sink.accept(extra);report.messages++;}});report.skipped+=parsed.skipped;
                });
            }}
            for(String[] page:largeHtml){File data=new File(page[0]);try(java.io.Reader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(data),StandardCharsets.UTF_8))){
                ChatParser.Result parsed=ChatParser.htmlStream(page[1],reader,message->{message.origin="html:"+report.sourceHash+":"+page[1];ChatMessage extra=matcher.merge(message);if(extra!=null){enrichMediaRefs(extra,report.media,aliases);sink.accept(extra);report.messages++;}});report.skipped+=parsed.skipped;
            }finally{data.delete();}}
            // Link using all original paths, including duplicates whose bytes were deduplicated.
            for (ChatMessage message : merged) {
                enrichMediaRefs(message, report.media, aliases);
                sink.accept(message); report.messages++;
                if (message.timestamp == 0) warning(report, "Some message dates were not recognised; the original date text was preserved.");
            }
            readMemories(report, aliases, mediaById(report.media));
            // Canonical merged chat/Memory JSON remains authoritative for actual
            // records; this index restores only the complete raw dataset variants.
            if (metadataIndex != null) restoreMetadataIndex(zip, metadataIndexPath, metadataIndex, report, textBytes,stagingDir);
            // Record filename-derived media coordinates/times from memory metadata when uniquely linked.
            for (MemoryItem memory : report.memories) for (MediaItem media : report.media) if (media.id.equals(memory.mediaId)) {
                if (memory.epoch != 0) media.epoch = memory.epoch;
                media.latitude = memory.latitude; media.longitude = memory.longitude;
            }
            if (report.messages == 0 && report.media.isEmpty() && report.memories.isEmpty() && report.metadata.isEmpty()) throw new IOException("No readable archive data found. Your previous import is unchanged.");
            if (!index.html.isEmpty() && htmlMessages.isEmpty() && largeHtml.isEmpty()) warning(report, "The HTML chat pages use an unsupported or empty layout.");
            if (report.skipped > 0) warning(report, report.skipped + " chat entries were incomplete and could not be indexed.");
            report.bytes = mediaBytes;
        }
        return report;
    }
    private static void restoreMetadataIndex(ZipFile zip, String indexPath, String rawIndex, Report report, long textBytes,File stagingDir) throws Exception {
        JSONObject index = new JSONObject(rawIndex);
        JSONArray datasets = index.optJSONArray("datasets");
        if (datasets == null || datasets.length() > 1000) throw new IOException("Portable metadata index must contain at most 1,000 datasets.");
        Map<String,String> restored = new LinkedHashMap<>();
        String lowerIndex = indexPath.toLowerCase(Locale.ROOT);
        int jsonFolder = lowerIndex.lastIndexOf("json/");
        String prefix = jsonFolder < 0 ? "" : indexPath.substring(0, jsonFolder);
        for (int i = 0; i < datasets.length(); i++) {
            interrupted(); JSONObject dataset = datasets.optJSONObject(i);
            if (dataset == null) throw new IOException("Invalid entry in portable metadata index.");
            String filename = ChatParser.value(dataset, "filename");
            if (filename.length() > 512 || !safeName(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".json") || leaf(filename).equalsIgnoreCase("snap_explorer_metadata.json")) throw new IOException("Invalid filename in portable metadata index.");
            byte[] bytes;
            String path = ChatParser.value(dataset, "path");
            if (!path.isEmpty()) {
                if (!safeName(path) || !path.startsWith("raw-metadata/") || !path.toLowerCase(Locale.ROOT).endsWith(".json")) throw new IOException("Unsafe dataset path in portable metadata index.");
                ZipArchiveEntry entry = zip.getEntry(path);
                if (entry == null && !prefix.isEmpty()) entry = zip.getEntry(prefix + path);
                if (entry == null || entry.isDirectory()) throw new IOException("A portable metadata payload is missing.");
                if(entry.getSize()>1024*1024){File data=File.createTempFile("restored-dataset-",".json",stagingDir);String actual=copyEntry(zip,entry,data,4L*1024*1024*1024),expected=ChatParser.value(dataset,"sha256");if(!expected.matches("(?i)[0-9a-f]{64}")||!expected.equalsIgnoreCase(actual))throw new IOException("A portable metadata payload failed its SHA-256 integrity check.");String key=filename;while(restored.containsKey(key))key="variant/"+key;restored.put(key,MetadataFiles.reference(data,actual));continue;}
                bytes = text(zip, entry);
                String expected = ChatParser.value(dataset, "sha256");
                String actual = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!expected.matches("(?i)[0-9a-f]{64}") || !expected.equalsIgnoreCase(actual)) throw new IOException("A portable metadata payload failed its SHA-256 integrity check.");
            } else {
                Object embedded = dataset.opt("json");
                if (!(embedded instanceof String)) throw new IOException("Portable metadata payload is missing.");
                bytes = ((String) embedded).getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_ENTRY) throw new IOException("A portable metadata payload exceeds the 32 MB limit.");
                String expected = ChatParser.value(dataset, "sha256");
                if (!expected.isEmpty() && !expected.equalsIgnoreCase(hex(MessageDigest.getInstance("SHA-256").digest(bytes)))) throw new IOException("A portable metadata payload failed its integrity check.");
            }
            textBytes += bytes.length; checkTextBudget(textBytes);
            String payload = new String(bytes, StandardCharsets.UTF_8); ChatParser.requireJsonDepth(payload);
            String key = filename;
            if (restored.containsKey(key)) key = "variant-" + (i + 1) + "/" + filename;
            while (restored.containsKey(key)) key = "variant/" + key;
            restored.put(key, payload);
        }
        report.metadata.clear(); report.metadata.putAll(restored);
    }
    private static void requireArchiveSize(File archive) throws IOException {
        if (!archive.isFile()) throw new IOException("The selected archive is not a readable local file.");
        if (archive.length() > MAX_ZIP) throw new IOException("This build supports ZIPs up to 16 GB.");
    }
    private static void interrupted() throws IOException { if (Thread.currentThread().isInterrupted()) throw new IOException("Import interrupted."); }
    private static void checkTextBudget(long bytes) throws IOException { if (bytes > MAX_TEXT_TOTAL) throw new IOException("Archive text exceeds the 128 MB limit."); }
    private static void boundedAdd(List<ChatMessage> messages, ChatMessage message) throws IOException {
        if (messages.size() >= MAX_MESSAGES) throw new IOException("More than 200,000 messages in one input format. Split this export before importing.");
        messages.add(message);
    }
    private static String copyEntry(ZipFile zip,ZipArchiveEntry entry,File target,long limit)throws Exception{
        if(entry.getSize()>limit)throw new IOException("A source entry exceeds the streaming storage limit.");CRC32 crc=new CRC32();MessageDigest sha=MessageDigest.getInstance("SHA-256");long bytes=0,nextSpace=8L*1024*1024;
        try(InputStream input=zip.getInputStream(entry);java.io.OutputStream output=new java.io.BufferedOutputStream(new FileOutputStream(target))){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1){interrupted();bytes+=n;if(bytes>limit)throw new IOException("A source entry exceeds the streaming storage limit.");crc.update(buffer,0,n);sha.update(buffer,0,n);output.write(buffer,0,n);if(bytes>=nextSpace){if(target.getParentFile().getUsableSpace()<64L*1024*1024)throw new IOException("Not enough free space for the selected source.");nextSpace+=8L*1024*1024;}}}
        checkEntry(entry,bytes,crc.getValue());return hex(sha.digest());
    }
    private static final class LargeMerge {
        final Map<String,List<ChatMessage>> candidates=new HashMap<>();final Map<String,ChatMessage> conversations=new HashMap<>();final Set<ChatMessage> used=Collections.newSetFromMap(new java.util.IdentityHashMap<ChatMessage,Boolean>());final Map<String,String> friends;
        LargeMerge(List<ChatMessage> messages,Map<String,String> friends){this.friends=friends;for(ChatMessage m:messages){candidates.computeIfAbsent(identity(m),k->new ArrayList<>()).add(m);conversationAlias(conversations,normalized(m.conversationId),m);conversationAlias(conversations,normalized(m.conversationName),m);}}
        ChatMessage merge(ChatMessage extra){List<ChatMessage> list=candidates.get(identity(extra));if(list!=null)for(ChatMessage m:list)if(!used.contains(m)&&sameConversation(m,extra,friends)){used.add(m);for(String ref:extra.mediaRefs)if(!m.mediaRefs.contains(ref))m.mediaRefs.add(ref);if(m.externalId.isEmpty())m.externalId=extra.externalId;if(m.replyTo.isEmpty())m.replyTo=extra.replyTo;m.own|=extra.own;return null;}
            ChatMessage target=conversations.get(normalized(extra.conversationId));if(target==null)target=conversations.get(normalized(extra.conversationName));ChatMessage resolved=target==null?withDisplayName(extra,friends):withConversation(extra,target.conversationId,target.conversationName);ChatMessage header=new ChatMessage(resolved.conversationId,resolved.conversationName,"","","","",0);conversationAlias(conversations,normalized(header.conversationId),header);conversationAlias(conversations,normalized(header.conversationName),header);return resolved;
        }
    }
    private static byte[] text(ZipFile zip, ZipArchiveEntry entry) throws IOException {
        if (entry.getSize() > MAX_ENTRY) throw new IOException("A metadata or chat file exceeds the 32 MB limit.");
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] value=readBounded(input,MAX_ENTRY);CRC32 crc=new CRC32();crc.update(value);checkEntry(entry,value.length,crc.getValue());return value;
        }
    }
    static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[16384]; int size;
        while ((size = input.read(buffer)) != -1) {
            interrupted(); if (output.size() > limit - size) throw new IOException("A chat file exceeds the size limit.");
            output.write(buffer, 0, size);
        }
        return output.toByteArray();
    }
    private static MediaItem copyMedia(ZipFile zip, ZipArchiveEntry entry, File blobs) throws Exception {
        CRC32 crc=new CRC32();
        MediaItem media = new MediaItem(); media.originalPath = entry.getName(); media.name = leaf(entry.getName());
        String lower = media.originalPath.toLowerCase(Locale.ROOT);
        media.source = lower.contains("overlay") ? "overlay" : lower.startsWith("memories/") || lower.contains("/memories/") ? "memories" : "chat";
        String extension = extension(media.name); media.kind = kind(extension); media.mime = mime(extension);
        media.epoch = filenameTimestamp(media.name);
        File temporary = File.createTempFile("blob-", ".part", blobs); MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try {
            try (InputStream input = zip.getInputStream(entry); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) {
                    interrupted(); media.bytes += count;
                    if (media.bytes > MAX_MEDIA_ENTRY) throw new IOException("A media file exceeds the 2 GB limit.");
                    hash.update(buffer, 0, count);crc.update(buffer,0,count);output.write(buffer, 0, count);
                }
            }
            checkEntry(entry,media.bytes,crc.getValue());
            media.sha256 = hex(hash.digest()); media.id = media.sha256;
            File finalFile = new File(blobs, media.id);
            if (finalFile.exists()) {
                if (finalFile.length() != media.bytes || !hashFile(finalFile).equals(media.sha256)) throw new IOException("Media staging conflict. Import cancelled.");
            } else if (!temporary.renameTo(finalFile)) throw new IOException("Could not stage an imported media file.");
            media.storedPath = finalFile.getAbsolutePath(); return media;
        } finally { if (temporary.exists()) temporary.delete(); }
    }
    private static void checkEntry(ZipArchiveEntry entry,long size,long crc)throws IOException{
        if(entry.getSize()>=0&&entry.getSize()!=size)throw new IOException("A ZIP entry did not match its declared size. Import cancelled.");
        if(entry.getCrc()>=0&&entry.getCrc()!=crc)throw new IOException("A ZIP entry checksum failed. The archive may be damaged; import cancelled.");
    }
    static String leaf(String name) { return name.substring(name.lastIndexOf('/') + 1); }
    private static String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? "bin" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private static String kind(String extension) {
        if (extension.matches("jpe?g|png|gif|webp|heic|heif|avif|bmp")) return "image";
        if (extension.matches("mp4|mov|m4v|webm|mkv")) return "video";
        if (extension.matches("mp3|m4a|aac|ogg|opus|wav|flac")) return "audio";
        return "other";
    }
    private static String mime(String extension) {
        if (extension.equals("jpg") || extension.equals("jpeg")) return "image/jpeg";
        if (extension.equals("heic") || extension.equals("heif")) return "image/heif";
        if (extension.equals("mp4") || extension.equals("m4v")) return "video/mp4";
        if (extension.equals("mov")) return "video/quicktime";
        if (extension.equals("mp3")) return "audio/mpeg";
        if (extension.equals("m4a")) return "audio/mp4";
        if (extension.equals("wav")) return "audio/wav";
        if (extension.equals("ogg") || extension.equals("opus")) return "audio/ogg";
        String kind = kind(extension); return kind.equals("other") ? "application/octet-stream" : kind + "/" + extension;
    }
    private static long filenameTimestamp(String name) {
        java.util.regex.Matcher date = java.util.regex.Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})(?:[_ -](\\d{2})(\\d{2})(?:[_-]?(\\d{2}))?)?.*").matcher(name);
        if (!date.matches()) return 0;
        String stamp = date.group(1);
        if (date.group(2) != null) stamp += " " + date.group(2) + ":" + date.group(3) + ":" + (date.group(4) == null ? "00" : date.group(4));
        return ChatParser.timestamp(stamp);
    }
    static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(bytes.length * 2); for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 255)); return out.toString(); }
    public static String hashFile(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) { byte[] buffer = new byte[65536]; int size; while ((size = input.read(buffer)) != -1) { interrupted(); digest.update(buffer, 0, size); } }
        return hex(digest.digest());
    }
    private static void warning(Report report, String warning) { if (report.warnings.size() < 100 && !report.warnings.contains(warning)) report.warnings.add(warning); }
    private static String normalized(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static String identity(ChatMessage message) { return normalized(message.sender) + "\u0000" + (message.timestamp == 0 ? message.date : message.timestamp) + "\u0000" + normalized(message.text); }
    private static List<ChatMessage> mergeMessages(List<ChatMessage> html, List<ChatMessage> json, Map<String,String> friends) {
        // Match formats one-for-one, preserving repeated identical messages inside either source.
        List<ChatMessage> result = new ArrayList<>(html);
        Set<ChatMessage> consumed = Collections.newSetFromMap(new java.util.IdentityHashMap<ChatMessage,Boolean>());
        Map<String,List<ChatMessage>> candidates = new HashMap<>(), placeholders = new HashMap<>();
        Map<String,ChatMessage> conversations = new HashMap<>();
        for (ChatMessage message : html) {
            candidates.computeIfAbsent(identity(message), key -> new ArrayList<>()).add(message);
            if (isPlaceholder(message)) placeholders.computeIfAbsent(eventKey(message), key -> new ArrayList<>()).add(message);
            conversationAlias(conversations, normalized(message.conversationId), message);
            conversationAlias(conversations, normalized(message.conversationName), message);
            String friendly = friends.get(normalized(message.conversationId));
            if (friendly != null) conversationAlias(conversations, normalized(friendly), message);
        }
        for (ChatMessage extra : json) {
            ChatMessage found = null;
            List<ChatMessage> possible = candidates.get(identity(extra));
            if (possible != null) for (ChatMessage candidate : possible) {
                if (!consumed.contains(candidate) && sameConversation(candidate, extra, friends)) { found = candidate; break; }
            }
            if (found == null && isPlaceholder(extra)) {
                List<ChatMessage> mediaCandidates = placeholders.get(eventKey(extra));
                if (mediaCandidates != null) for (ChatMessage candidate : mediaCandidates) {
                    if (!consumed.contains(candidate) && sameConversation(candidate, extra, friends)) { found = candidate; break; }
                }
            }
            if (found != null) {
                consumed.add(found);
                for (String ref : extra.mediaRefs) if (!found.mediaRefs.contains(ref)) found.mediaRefs.add(ref);
                if (found.externalId.isEmpty()) found.externalId = extra.externalId;
                if (found.replyTo.isEmpty()) found.replyTo = extra.replyTo;
                found.own |= extra.own;
            } else {
                ChatMessage target = conversations.get(normalized(extra.conversationId));
                if (target == null) target = conversations.get(normalized(extra.conversationName));
                String friendly = friends.get(normalized(extra.conversationId));
                if (target == null && friendly != null) target = conversations.get(normalized(friendly));
                result.add(target == null ? withDisplayName(extra, friends) : withConversation(extra, target.conversationId, target.conversationName));
            }
        }
        return result;
    }
    private static boolean isPlaceholder(ChatMessage message) { return message.text.startsWith("[") && message.text.endsWith("]"); }
    private static String eventKey(ChatMessage message) { return normalized(message.sender) + "\u0000" + (message.timestamp == 0 ? message.date : message.timestamp); }
    private static void conversationAlias(Map<String,ChatMessage> aliases, String key, ChatMessage message) {
        if (key.isEmpty()) return;
        if (!aliases.containsKey(key)) aliases.put(key, message);
        else if (aliases.get(key) != null && !aliases.get(key).conversationId.equals(message.conversationId)) aliases.put(key, null);
    }
    private static boolean sameConversation(ChatMessage a, ChatMessage b, Map<String,String> friends) {
        String ai = normalized(a.conversationId), bi = normalized(b.conversationId), an = normalized(a.conversationName), bn = normalized(b.conversationName);
        return ai.equals(bi) || an.equals(bi) || an.equals(bn) || ai.equals(bn) || an.equals(normalized(friends.get(bi))) || bn.equals(normalized(friends.get(ai)));
    }
    private static ChatMessage withConversation(ChatMessage original, String id, String display) {
        ChatMessage result = new ChatMessage(id, display, original.sender, original.text, original.type, original.date, original.timestamp);
        result.origin=original.origin;result.mediaRefs.addAll(original.mediaRefs); result.externalId = original.externalId; result.replyTo = original.replyTo; result.own = original.own; return result;
    }
    private static ChatMessage withDisplayName(ChatMessage original, Map<String,String> friends) {
        String display = friends.get(normalized(original.conversationId)); if (display == null || display.isEmpty()) return original;
        return withConversation(original, original.conversationId, display);
    }
    private static Map<String,String> friendNames(Map<String,String> metadata) {
        Map<String,String> names = new HashMap<>();
        for (Map.Entry<String,String> raw : metadata.entrySet()) if (raw.getKey().equalsIgnoreCase("friends.json")) {
            try {
                JSONObject root = new JSONObject(raw.getValue());
                for (String group : new String[]{"Friends", "Blocked Users", "Deleted Friends", "Hidden Friend Suggestions"}) {
                    JSONArray entries = root.optJSONArray(group); if (entries == null) continue;
                    for (int i = 0; i < entries.length(); i++) { JSONObject friend = entries.optJSONObject(i); if (friend == null) continue;
                        String username = ChatParser.value(friend, "Username"), display = ChatParser.value(friend, "Display Name");
                        if (!username.isEmpty() && !display.isEmpty()) names.put(normalized(username), display);
                    }
                }
            } catch (org.json.JSONException ignored) { }
        }
        return names;
    }
    /** Re-links split/merged exports after every part's media has reached the database. */
    public static List<MemoryItem> relinkMemories(Map<String,String> metadata, List<MediaItem> allMedia) {
        Map<String,String> aliases = new HashMap<>();
        for (MediaItem media : allMedia) addMediaAliases(aliases, media);
        Map<String,MediaItem> indexedMedia = mediaById(allMedia);
        Map<String,MemoryItem> combined = new LinkedHashMap<>();
        for (Map.Entry<String,String> dataset : metadata.entrySet()) {
            if (!leaf(dataset.getKey()).equalsIgnoreCase("memories_history.json")) continue;
            Report partial = new Report(); partial.metadata.put("memories_history.json", dataset.getValue()); partial.media.addAll(allMedia);
            readMemories(partial, aliases, indexedMedia);
            for (MemoryItem memory : partial.memories) {
                MemoryItem previous = combined.get(memory.id);
                if (previous == null || (previous.mediaId.isEmpty() && !memory.mediaId.isEmpty())) combined.put(memory.id, memory);
            }
        }
        return new ArrayList<>(combined.values());
    }
    /** Builds the alias index once for a streamed cursor, rather than once per message. */
    public static ChatParser.Sink mediaLinker(List<MediaItem> allMedia, ChatParser.Sink output) {
        LocalMediaIndex index=new LocalMediaIndex(allMedia);
        return message -> {List<String> ids=new ArrayList<>();for(String raw:message.mediaRefs)for(String ref:MediaReferences.expand(raw)){String id=index.resolve(ref);if(!id.isEmpty()&&!message.mediaRefs.contains(id)&&!ids.contains(id))ids.add(id);}message.mediaRefs.addAll(ids);output.accept(message);};
    }
    private static Map<String,MediaItem> mediaById(List<MediaItem> media) {
        Map<String,MediaItem> result = new HashMap<>();
        for (MediaItem item : media) result.put(item.id, item);
        return result;
    }
    /** Appends deterministic local content IDs to existing archive references. */
    public static void relinkMessage(ChatMessage message, List<MediaItem> allMedia) {
        Map<String,String> aliases = new HashMap<>();
        for (MediaItem media : allMedia) addMediaAliases(aliases, media);
        enrichMediaRefs(message, allMedia, aliases);
    }
    private static void addMediaAliases(Map<String,String> aliases, MediaItem media) {
        mediaAlias(aliases, media.originalPath.toLowerCase(Locale.ROOT), media.id);
        mediaAlias(aliases, media.name.toLowerCase(Locale.ROOT), media.id);
        mediaAlias(aliases, media.id.toLowerCase(Locale.ROOT), media.id);
        java.util.regex.Matcher identifier = java.util.regex.Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{32,64}").matcher(media.name);
        while (identifier.find()) mediaAlias(aliases, identifier.group().toLowerCase(Locale.ROOT), media.id);
        for (String token : media.name.split("[^A-Za-z0-9]+")) if (token.length() >= 8) mediaAlias(aliases, token.toLowerCase(Locale.ROOT), media.id);
        if (media.source.equals("memories") && media.epoch != 0) mediaAlias(aliases, "__minute__:" + (media.epoch / 60000L) + ":" + media.kind, media.id);
    }
    private static void mediaAlias(Map<String,String> aliases, String key, String id) {
        if (!aliases.containsKey(key)) aliases.put(key, id);
        else if (!id.equals(aliases.get(key))) aliases.put(key, "");
    }
    private static void enrichMediaRefs(ChatMessage message, List<MediaItem> media, Map<String,String> aliases) {
        List<String> add = new ArrayList<>();
        for (String ref : message.mediaRefs) {
            String key = ref.toLowerCase(Locale.ROOT); String linked = aliases.get(key);
            if (linked == null) linked = aliases.get(leaf(key));
            if (linked != null && !linked.isEmpty() && !message.mediaRefs.contains(linked) && !add.contains(linked)) add.add(linked);
        }
        message.mediaRefs.addAll(add);
    }
    private static void readMemories(Report report, Map<String,String> aliases, Map<String,MediaItem> indexedMedia) {
        String source = report.metadata.get("memories_history.json");
        if (source == null) for (Map.Entry<String,String> raw : report.metadata.entrySet()) if (raw.getKey().equalsIgnoreCase("memories_history.json")) { source = raw.getValue(); break; }
        if (source == null) return;
        if(MetadataFiles.isReference(source)){
            Map<String,Integer> occurrences=new HashMap<>();
            try(java.io.Reader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(MetadataFiles.referencedFile(source)),StandardCharsets.UTF_8))){JsonRecords.read(reader,(group,record)->{
                if(!group.equals("Saved Media"))return;Report part=new Report();part.metadata.put("memories_history.json","{\"Saved Media\":["+record.toString()+"]}");readMemories(part,aliases,indexedMedia);for(MemoryItem memory:part.memories){if(ChatParser.value(record,"Media ID","Media Id","ID","Id").isEmpty()){int occurrence=occurrences.containsKey(memory.id)?occurrences.get(memory.id):0;occurrences.put(memory.id,occurrence+1);if(occurrence>0)memory.id+="-"+occurrence;}report.memories.add(memory);}for(String note:part.warnings)warning(report,note);
            });}catch(Exception error){throw new IllegalStateException("Large Memories metadata could not be parsed safely.",error);}return;
        }
        try {
            JSONObject root = new JSONObject(source); JSONArray saved = root.optJSONArray("Saved Media");
            if (saved == null) { warning(report, "Memories metadata has an unrecognised layout."); return; }
            int missing = 0; Map<String,Integer> occurrences = new HashMap<>();
            for (int i = 0; i < saved.length(); i++) {
                JSONObject raw = saved.optJSONObject(i); if (raw == null) continue;
                MemoryItem memory = new MemoryItem(); memory.date = ChatParser.value(raw, "Date", "Created", "Timestamp"); memory.epoch = ChatParser.timestamp(memory.date);
                memory.mediaType = ChatParser.value(raw, "Media Type", "Type");
                String rawId = ChatParser.value(raw, "Media ID", "Media Id", "ID", "Id");
                String path = ChatParser.value(raw, "Media Path", "File Name", "Filename", "Path");
                Double[] location = coordinates(raw); memory.latitude = location[0]; memory.longitude = location[1];
                memory.id = rawId.isEmpty() ? shaText(memory.date + "\u0000" + memory.mediaType + "\u0000" + path + "\u0000" + memory.latitude + "," + memory.longitude) : rawId;
                if (rawId.isEmpty()) {
                    int occurrence = occurrences.containsKey(memory.id) ? occurrences.get(memory.id) : 0;
                    occurrences.put(memory.id, occurrence + 1);
                    if (occurrence > 0) memory.id += "-" + occurrence;
                }
                MediaItem linked = findMemoryMedia(rawId, path, memory, aliases, indexedMedia);
                if (linked != null) memory.mediaId = linked.id; else missing++;
                report.memories.add(memory);
            }
            if (missing > 0) warning(report, missing + " Memories have metadata but no included local file. Offline mode does not download missing files.");
        } catch (org.json.JSONException malformed) { warning(report, "Memories metadata could not be parsed."); }
    }
    private static MediaItem findMemoryMedia(String id, String path, MemoryItem memory, Map<String,String> aliases, Map<String,MediaItem> indexedMedia) {
        String wanted = path.toLowerCase(Locale.ROOT), key = id.toLowerCase(Locale.ROOT);
        String alias = wanted.isEmpty() ? null : aliases.get(wanted);
        if (alias == null && !wanted.isEmpty()) alias = aliases.get(leaf(wanted));
        if (alias == null && !key.isEmpty()) alias = aliases.get(key);
        if (alias != null && !alias.isEmpty()) return indexedMedia.get(alias);
        if (memory.epoch == 0) return null;
        String memoryKind = memory.mediaType.toLowerCase(Locale.ROOT);
        String kind = memoryKind.contains("video") ? "video" : memoryKind.contains("image") || memoryKind.contains("photo") ? "image" : "";
        if (kind.isEmpty()) return null;
        alias = aliases.get("__minute__:" + (memory.epoch / 60000L) + ":" + kind);
        return alias == null || alias.isEmpty() ? null : indexedMedia.get(alias);
    }
    private static Double[] coordinates(JSONObject raw) {
        Double lat = finiteCoordinate(raw.opt("Latitude"), -90, 90), lon = finiteCoordinate(raw.opt("Longitude"), -180, 180);
        if (lat == null || lon == null) {
            String location = ChatParser.value(raw, "Location");
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Latitude\\s*[,=:]?\\s*Longitude\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(location);
            if (matcher.find()) { lat = finiteCoordinate(matcher.group(1), -90, 90); lon = finiteCoordinate(matcher.group(2), -180, 180); }
            else {
                matcher = java.util.regex.Pattern.compile("Latitude\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?).*Longitude\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(location);
                if (matcher.find()) { lat = finiteCoordinate(matcher.group(1), -90, 90); lon = finiteCoordinate(matcher.group(2), -180, 180); }
            }
        }
        return lat == null || lon == null ? new Double[]{null, null} : new Double[]{lat, lon};
    }
    private static Double finiteCoordinate(Object value, double min, double max) {
        try { double result = Double.parseDouble(String.valueOf(value)); return Double.isNaN(result) || Double.isInfinite(result) || result < min || result > max ? null : result; }
        catch (NumberFormatException ignored) { return null; }
    }
    private static String shaText(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private ArchiveImporter() { }
}
