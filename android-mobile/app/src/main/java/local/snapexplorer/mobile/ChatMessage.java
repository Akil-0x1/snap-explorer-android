package local.snapexplorer.mobile;

import java.util.ArrayList;
import java.util.List;

/** Framework-free import model. Archive text is never rendered as active HTML. */
public final class ChatMessage {
    public final String conversationId, conversationName, sender, text, type, date;
    public final long timestamp;
    public final List<String> mediaRefs = new ArrayList<>();
    public String replyTo = "", externalId = "";
    public boolean own;
    public String origin="";
    public ChatMessage(String id, String name, String sender, String text, String type, String date, long timestamp) {
        this.conversationId = id; this.conversationName = name; this.sender = sender;
        this.text = text; this.type = type; this.date = date; this.timestamp = timestamp;
    }
}
