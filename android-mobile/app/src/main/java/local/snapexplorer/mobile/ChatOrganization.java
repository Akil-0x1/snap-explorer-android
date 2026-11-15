package local.snapexplorer.mobile;

import android.content.*;
import java.util.*;

/** Local display organization; never changes exported Snapchat conversations. */
final class ChatOrganization {
    private final SharedPreferences prefs;
    ChatOrganization(Context context){prefs=context.getSharedPreferences("chat_organization",0);}
    boolean pinned(String chat){return prefs.getBoolean("pin:"+chat,false);}
    void pin(String chat,boolean value){prefs.edit().putBoolean("pin:"+chat,value).apply();}
    String group(String chat){return prefs.getString("group:"+chat,"");}
    List<String> groups(){List<String> result=new ArrayList<>(prefs.getStringSet("groups",Collections.emptySet()));result.sort(String.CASE_INSENSITIVE_ORDER);return result;}
    void add(String name){Set<String> all=new HashSet<>(groups());all.add(name);prefs.edit().putStringSet("groups",all).apply();}
    void move(String chat,String name){if(!name.isEmpty())add(name);prefs.edit().putString("group:"+chat,name).apply();}
    void rename(String before,String after){Set<String> all=new HashSet<>(groups());all.remove(before);all.add(after);SharedPreferences.Editor edit=prefs.edit().putStringSet("groups",all);for(Map.Entry<String,?> row:prefs.getAll().entrySet())if(row.getKey().startsWith("group:")&&before.equals(row.getValue()))edit.putString(row.getKey(),after);edit.apply();}
    void remove(String name){Set<String> all=new HashSet<>(groups());all.remove(name);SharedPreferences.Editor edit=prefs.edit().putStringSet("groups",all);for(Map.Entry<String,?> row:prefs.getAll().entrySet())if(row.getKey().startsWith("group:")&&name.equals(row.getValue()))edit.remove(row.getKey());edit.apply();}
    List<ChatRepository.Row> arrange(List<ChatRepository.Row> source,String filter){List<ChatRepository.Row> rows=new ArrayList<>();for(ChatRepository.Row row:source)if(filter.isEmpty()||filter.equals("#pinned")&&pinned(row.chat)||filter.equals(group(row.chat)))rows.add(row);rows.sort((a,b)->{int p=Boolean.compare(pinned(b.chat),pinned(a.chat));if(p!=0)return p;int time=Long.compare(b.time,a.time);return time!=0?time:a.chat.compareTo(b.chat);});return rows;}
}
