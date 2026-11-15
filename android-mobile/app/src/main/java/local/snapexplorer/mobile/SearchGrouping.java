package local.snapexplorer.mobile;
import java.util.*;
/** Search presentation groups only; source messages are never changed. */
public final class SearchGrouping {
 public static final String SCHEMA="CREATE TABLE hits(position INTEGER PRIMARY KEY,message_id INTEGER NOT NULL,group_key TEXT NOT NULL UNIQUE,copies INTEGER NOT NULL)";
 public static String key(boolean grouped,long id,String chat,String sender,String body,long time,Collection<String> media,String reply){
  if(!grouped)return "row:"+id;
  String key=MessageCopies.visibleKey(chat,sender,body,time,media,reply);
  return key==null?"row:"+id:"group:"+ExportFormats.sha256(key);
 }
 private SearchGrouping(){}
}
