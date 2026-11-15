package local.snapexplorer.mobile;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;

/** Reads known Snapchat layouts, as text. Does not fetch URLs or execute scripts. */
public final class ChatParser {
    public interface Sink { void accept(ChatMessage message) throws Exception; }
    public static final class Result { public int messages, skipped; }
    private static final String[] DATE_FORMATS = {
        "yyyy-MM-dd HH:mm:ss", "MMM dd, yyyy HH:mm:ss", "MMM d, yyyy HH:mm:ss",
        "MM/dd/yyyy HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss"
    };
    public static long timestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        String value = raw.trim().replace(" UTC", "");
        for (String pattern : DATE_FORMATS) {
            try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.US))
                    .toInstant(ZoneOffset.UTC).toEpochMilli(); }
            catch (DateTimeParseException ignored) { }
        }
        try { return Instant.parse(raw.trim()).toEpochMilli(); }
        catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(); }
        catch (DateTimeParseException ignored) { }
        // Some metadata exports use Unix seconds/milliseconds. Never guess short IDs as dates.
        if (value.matches("[0-9]{10}|[0-9]{13}")) {
            try { long number = Long.parseLong(value); return value.length() == 10 ? number * 1000L : number; }
            catch (NumberFormatException ignored) { }
        }
        return 0;
    }
    public static Result html(String sourceName, String html, Sink sink) throws Exception {
        Document document = Jsoup.parse(html);
        String file = sourceName.substring(sourceName.lastIndexOf('/') + 1);
        String id = file.replaceFirst("^subpage_", "").replaceFirst("\\.html$", "");
        Element title = document.selectFirst("h1");
        String name = title == null ? id : title.text().replaceFirst("^Chat History with\\s+", "").trim();
        if (name.isEmpty()) name = id;
        Result result = new Result();
        for (Element node : document.select(".rightpanel > div")) {
            Element sender = node.selectFirst("h4"), date = node.selectFirst("h6");
            if (sender == null || date == null) { result.skipped++; continue; }
            Element content = null;
            for (Element child : node.children()) if (child.tagName().equals("p")) { content = child; break; }
            if (content == null) content = node.selectFirst("p");
            Element kind = node.selectFirst("span");
            String type = kind == null ? "UNKNOWN" : kind.text();
            String text = content == null ? "" : content.wholeText().trim();
            if (text.isEmpty()) text = "[" + (type.isEmpty() ? "Media or event" : type) + "]";
            ChatMessage message = new ChatMessage(id, name, sender.text(), text, type, date.text(), timestamp(date.text()));
            message.origin="html:"+sourceName;
            message.externalId = attr(node, "data-message-id", "data-msg-id", "message-id");
            message.replyTo = attr(node, "data-reply-to", "data-parent-message-id", "data-replied-to");
            message.own = "true".equalsIgnoreCase(attr(node, "data-is-sender", "data-own"));
            for (Element media : node.select("img[src],video[src],audio[src],source[src],a[href]")) {
                String value = media.hasAttr("src") ? media.attr("src") : media.attr("href");
                String ref = localReference(sourceName, value);
                if (!ref.isEmpty() && (!media.tagName().equals("a") || mediaExtension(ref))) add(message, ref);
                String mediaId = attr(media, "data-media-id");
                if (!mediaId.isEmpty()) add(message, mediaId);
            }
            sink.accept(message);
            result.messages++;
        }
        return result;
    }
    public static Result htmlStream(String sourceName,java.io.Reader reader,Sink sink)throws Exception{
        Result total=new Result();HtmlBudget budget=new HtmlBudget(reader);
        try(org.jsoup.parser.StreamParser parser=new org.jsoup.parser.StreamParser(org.jsoup.parser.Parser.htmlParser()).parse(budget,"")){
            Element heading=parser.selectFirst("h1");String title=heading==null?"":heading.text();Element entry;
            while(true){budget.resetBudget();entry=parser.selectNext(".rightpanel > div");if(entry==null)break;String node=entry.outerHtml();entry.remove();
                Result result=html(sourceName,"<h1>"+org.jsoup.nodes.Entities.escape(title)+"</h1><div class=\"rightpanel\">"+node+"</div>",sink);total.messages+=result.messages;total.skipped+=result.skipped;
            }
        }return total;
    }
    private static final class HtmlBudget extends java.io.FilterReader{
        long remaining=16L*1024*1024;HtmlBudget(java.io.Reader reader){super(reader);}void resetBudget(){remaining=16L*1024*1024;}
        @Override public int read(char[] b,int off,int len)throws java.io.IOException{if(remaining<=0)throw new java.io.IOException("A single HTML message or unsupported page section is too large to parse safely.");int n=super.read(b,off,(int)Math.min(remaining,len));if(n>0)remaining-=n;return n;}
    }
    private static String attr(Element node, String... names) {
        for (String key : names) if (node.hasAttr(key) && !node.attr(key).trim().isEmpty()) return node.attr(key).trim();
        return "";
    }
    static boolean mediaExtension(String value) {
        return value.toLowerCase(Locale.ROOT).matches(".*\\.(?:jpe?g|png|gif|webp|heic|heif|avif|bmp|mp4|mov|m4v|webm|mkv|mp3|m4a|aac|ogg|opus|wav|flac|svg|tgs|lottie|pdf|vcf|csv|txt)$");
    }
    /** Resolves only archive-relative paths. Remote, absolute, and escaping refs are ignored. */
    public static String localReference(String sourceName, String raw) {
        if (raw == null || raw.isEmpty() || raw.indexOf('\\') >= 0 || raw.indexOf('\0') >= 0) return "";
        try {
            java.net.URI uri = new java.net.URI(raw.trim().replace(" ", "%20"));
            if (uri.isAbsolute() || uri.getRawAuthority() != null) return "";
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.startsWith("/")) return "";
            String parent = sourceName.contains("/") ? sourceName.substring(0, sourceName.lastIndexOf('/') + 1) : "";
            Deque<String> parts = new ArrayDeque<>();
            for (String part : (parent + path).split("/")) {
                if (part.isEmpty() || part.equals(".")) continue;
                if (part.equals("..")) { if (parts.isEmpty()) return ""; parts.removeLast(); }
                else { if (part.indexOf(':') >= 0 || part.indexOf('\\') >= 0 || part.indexOf('\0') >= 0) return ""; parts.addLast(part); }
            }
            String result = String.join("/", parts);
            return ArchiveImporter.safeName(result) ? result : "";
        } catch (java.net.URISyntaxException ignored) { return ""; }
    }
    public static Result json(String json, Sink sink) throws Exception {
        requireJsonDepth(json);
        JSONObject root = new JSONObject(json);
        for (String key : new String[]{"Received Saved Chat History", "Chat History", "Saved Chat History"}) {
            JSONObject wrapped = root.optJSONObject(key); if (wrapped != null) { root = wrapped; break; }
        }
        Result result = new Result();
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            JSONArray messages = root.optJSONArray(id);
            if (messages == null) continue;
            for (int i = 0; i < messages.length(); i++) {
                JSONObject entry = messages.optJSONObject(i);
                if (entry == null) { result.skipped++; continue; }
                String date = value(entry, "Created", "Date", "Timestamp"), sender = value(entry, "From", "Sender", "Username");
                if (date.isEmpty() || sender.isEmpty()) { result.skipped++; continue; }
                String type = value(entry, "Media Type", "Type"); if (type.isEmpty()) type = "TEXT";
                String text = value(entry, "Content", "Text", "Message");
                if (text.isEmpty()) text = "[" + type + "]";
                String name = value(entry, "Conversation Title", "Conversation Name", "Chat Name");
                ChatMessage message = new ChatMessage(id, name.isEmpty() ? id : name, sender, text, type, date, timestamp(date));
                message.externalId = value(entry, "Message ID", "Message Id", "message_id", "MessageId");
                message.replyTo = value(entry, "Reply To Message ID", "Parent Message ID", "Reply To", "reply_to", "Replied To Message ID");
                if (message.replyTo.isEmpty()) {
                    JSONObject reply = entry.optJSONObject("Reply To");
                    if (reply != null) message.replyTo = value(reply, "Message ID", "message_id", "id");
                }
                message.own = entry.optBoolean("IsSender", false);message.origin="json";
                message.mediaRefs.addAll(MediaReferences.collect(entry));
                sink.accept(message); result.messages++;
            }
        }
        return result;
    }
    static void requireJsonDepth(String json) throws java.io.IOException {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 128) throw new java.io.IOException("JSON nesting exceeds the safety limit.");
            } else if (c == '}' || c == ']') depth--;
        }
    }
    static String value(JSONObject entry, String... names) {
        for (String name : names) {
            Object value = entry.opt(name);
            if (value instanceof String || value instanceof Number) {
                String result = value.toString().trim(); if (!result.isEmpty()) return result;
            }
        }
        return "";
    }
    private static void addJsonReference(ChatMessage message, String raw) {
        String value = raw.trim();
        if (value.isEmpty() || value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0 || value.startsWith("/")) return;
        if (value.startsWith("[")) {
            try { JSONArray refs = new JSONArray(value); for (int n = 0; n < refs.length(); n++) addJsonReference(message, refs.optString(n, "")); return; }
            catch (org.json.JSONException ignored) { return; }
        }
        if (value.contains(",")) { for (String item : value.split(",")) addJsonReference(message, item); return; }
        if (ArchiveImporter.safeName(value)) add(message, value);
    }
    private static void add(ChatMessage message, String value) { if (!message.mediaRefs.contains(value)) message.mediaRefs.add(value); }
    private ChatParser() { }
}
