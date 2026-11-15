package local.snapexplorer.mobile;

import java.util.Locale;

/** Archive event labels are not message text. Never hide a real caption. */
public final class ChatPresentation {
    public static boolean mediaOnly(String text,String kind) {
        String value=text==null?"":text.trim().toUpperCase(Locale.ROOT);
        if(value.isEmpty())return true;
        return value.matches("\\[(?:IMAGE|PHOTO|VIDEO|AUDIO|VOICE|VOICE_NOTE|GIF|STICKER|CUSTOM[_ ]?STICKER|BITMOJI|MEDIA|SNAP|VIDEO_NOTE)\\]");
    }
    public static String body(String text,String kind) {
        if(kind!=null&&kind.toUpperCase(Locale.ROOT).contains("STATUSERASEDMESSAGE"))return "Message removed";
        return text;
    }
    public static boolean visible(String text,String kind,int localAttachments) {
        return localAttachments>0||!mediaOnly(text,kind);
    }
    private ChatPresentation(){}
}
