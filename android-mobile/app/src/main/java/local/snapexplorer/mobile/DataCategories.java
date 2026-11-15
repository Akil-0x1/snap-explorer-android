package local.snapexplorer.mobile;
import java.util.Locale;
/** Consistent labels for the full export, including unrecognized datasets. */
public final class DataCategories {
 public static String dataset(String path){String p=path.toLowerCase(Locale.ROOT);if(p.endsWith("chat_history.json"))return "Chats";if(p.contains("sticker"))return "Stickers";if(p.contains("bitmoji")||p.contains("avatar"))return "Avatars";if(p.contains("memories"))return "Memories";if(p.contains("friend")||p.contains("contact"))return "Friends";if(p.contains("location")||p.contains("map_history"))return "Locations";if(p.contains("snap_history")||p.contains("story")||p.contains("spotlight"))return "Snaps & stories";if(p.contains("account")||p.contains("profile")||p.contains("login")||p.contains("device"))return "Account & devices";if(p.contains("search"))return "Search history";if(p.contains("purchase")||p.contains("shop"))return "Purchases";return "Other data";}
 public static String asset(String path){String p=path==null?"":path.toLowerCase(Locale.ROOT);if(p.contains("overlay"))return "overlay";if(p.contains("sticker"))return "sticker";if(p.contains("bitmoji")||p.contains("avatar")||p.contains("profile_media"))return "avatar";return "";}
 public static String role(String dataset){if(dataset.equals("Stickers"))return "sticker";if(dataset.equals("Avatars"))return "avatar";return "";}
 private DataCategories(){}
}
