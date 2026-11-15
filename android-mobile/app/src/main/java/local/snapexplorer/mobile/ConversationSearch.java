package local.snapexplorer.mobile;
import java.util.*;
/** Indexed, unbounded conversation search; one match is loaded per arrow press. */
public final class ConversationSearch {
    private static final String VISIBLE=" AND "+DatabaseQueries.VISIBLE+" AND (upper(trim(m.body)) NOT IN ('','[IMAGE]','[PHOTO]','[VIDEO]','[AUDIO]','[VOICE]','[VOICE_NOTE]','[GIF]','[STICKER]','[BITMOJI]','[MEDIA]','[SNAP]','[VIDEO_NOTE]') OR EXISTS(SELECT 1 FROM message_media linked WHERE linked.message_id=m.id))";
    public static String terms(String input){StringBuilder out=new StringBuilder();java.util.regex.Matcher words=java.util.regex.Pattern.compile("[\\p{L}\\p{N}_]+").matcher(input);while(words.find()){if(out.length()>0)out.append(" AND ");out.append('"').append(words.group()).append("\"*");}return out.toString();}
    public static DatabaseQueries.Query count(String chat,String input){String terms=terms(input);return new DatabaseQueries.Query("SELECT COUNT(*) FROM messages m JOIN message_search f ON f.docid=m.id WHERE m.chat=? AND "+(terms.isEmpty()?"0":"message_search MATCH ?")+VISIBLE,terms.isEmpty()?Arrays.asList(chat):Arrays.asList(chat,terms));}
    public static DatabaseQueries.Query match(String chat,String input,long time,long id,int direction){
        String terms=terms(input);List<String> args=new ArrayList<>();args.add(chat);String where="m.chat=? AND "+(terms.isEmpty()?"0":"message_search MATCH ?")+VISIBLE;if(!terms.isEmpty())args.add(terms);
        if(id>0){String op=direction>0?">":"<";where+=" AND (m.epoch"+op+"? OR (m.epoch=? AND m.id"+op+"?))";args.add(Long.toString(time));args.add(Long.toString(time));args.add(Long.toString(id));}
        return new DatabaseQueries.Query(DatabaseQueries.MESSAGES+"JOIN message_search f ON f.docid=m.id WHERE "+where+" ORDER BY m.epoch "+(direction>0?"ASC":"DESC")+",m.id "+(direction>0?"ASC":"DESC")+" LIMIT 1",args);
    }
    public static DatabaseQueries.Query page(String chat,String input,long time,long id){DatabaseQueries.Query one=match(chat,input,time,id,-1);return new DatabaseQueries.Query(one.sql.replace(" LIMIT 1"," LIMIT 100"),Arrays.asList(one.args));}
    public static DatabaseQueries.Query all(String chat,String input){DatabaseQueries.Query one=match(chat,input,0,0,-1);return new DatabaseQueries.Query(one.sql.replace(" LIMIT 1",""),Arrays.asList(one.args));}
    private ConversationSearch(){}
}
