package local.snapexplorer.mobile;
import java.text.Normalizer;
import java.util.*;
/** Reversible visual folding; raw records are never deleted. */
public final class MessageCopies {
    static String normalized(String text){return Normalizer.normalize(text==null?"":text.replace("\r\n","\n").replace('\u00a0',' '),Normalizer.Form.NFC).trim();}
    public static boolean same(String chatA,String senderA,String bodyA,String kindA,long timeA,String idA,Collection<String> mediaA,String chatB,String senderB,String bodyB,String kindB,long timeB,String idB,Collection<String> mediaB){
        if(timeA<=0||timeA/1000!=timeB/1000||!chatA.equals(chatB)||!senderA.equals(senderB))return false;
        if(idA!=null&&idB!=null&&!idA.isEmpty()&&!idB.isEmpty()&&!idA.equals(idB))return false;
        return normalized(bodyA).equals(normalized(bodyB))&&normalized(kindA).equalsIgnoreCase(normalized(kindB))&&new HashSet<>(mediaA).equals(new HashSet<>(mediaB));
    }
    /** Optional presentation key. Deliberately ignores source kinds/IDs and hidden seconds. */
    public static String visibleKey(String chat,String sender,String body,long time,Collection<String> media,String reply){
        if(time<=0)return null;
        String visible=normalized(body).replaceAll("[\\p{Z}\\s]+"," ");
        List<String> files=new ArrayList<>(new TreeSet<>(media));
        return field(chat)+field(normalized(sender).toLowerCase(Locale.ROOT))+field(Long.toString(time/60000L))+field(visible)+field(String.join(",",files))+field(reply==null?"":reply);
    }
    private static String field(String value){return value.length()+":"+value;}
    private MessageCopies(){}
}
