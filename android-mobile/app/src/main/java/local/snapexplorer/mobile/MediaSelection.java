package local.snapexplorer.mobile;

import java.util.*;

/** One filter implementation shared by gallery and full filtered exports. */
public final class MediaSelection {
    public final String where;
    public final String[] args;
    public MediaSelection(String source,String kind,String year,String chat) {
        List<String> values=new ArrayList<>();StringBuilder sql=new StringBuilder("1=1");
        if(source!=null&&!source.isEmpty()) {
            sql.append(" AND (media.source=?");values.add(source);
            if(source.equals("memories"))sql.append(" OR EXISTS(SELECT 1 FROM memories memory WHERE memory.media_id=media.id)");
            if(source.equals("chat"))sql.append(" OR EXISTS(SELECT 1 FROM message_media link WHERE link.media_id=media.id)");
            sql.append(')');
        }
        if(kind!=null&&!kind.isEmpty()){
            if(kind.equals("sticker")||kind.equals("avatar")||kind.equals("overlay")){sql.append(" AND EXISTS(SELECT 1 FROM asset_tags tag WHERE tag.media_id=media.id AND tag.role=?)");values.add(kind);}
            else{sql.append(" AND media.kind=?");values.add(kind);}
        }
        if(year!=null&&!year.isEmpty()){sql.append(" AND strftime('%Y',media.epoch/1000,'unixepoch')=?");values.add(year);}
        if(chat!=null&&!chat.isEmpty()){
            sql.append(" AND EXISTS(SELECT 1 FROM message_media link JOIN messages message ON message.id=link.message_id WHERE message.chat=? AND link.media_id=media.id)");values.add(chat);
        }
        where=sql.toString();args=values.toArray(new String[0]);
    }
}
