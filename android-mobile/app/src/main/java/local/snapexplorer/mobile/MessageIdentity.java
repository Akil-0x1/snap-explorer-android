package local.snapexplorer.mobile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
/** Stable identity across export formats; multiplicity is counted separately per source. */
public final class MessageIdentity {
 public static String key(ChatMessage m)throws Exception{
  String body=normalize(m.text),type=normalize(m.type).toUpperCase(Locale.ROOT);
  if(body.matches("\\[[A-Za-z_ ]+\\]"))body=body.toUpperCase(Locale.ROOT);
  String value=normalize(m.conversationId).toLowerCase(Locale.ROOT)+"\u0000"+normalize(m.sender).toLowerCase(Locale.ROOT)+"\u0000"+(m.timestamp==0?normalize(m.date):m.timestamp/1000L)+"\u0000"+body+"\u0000"+type;
  byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));char[] hex="0123456789abcdef".toCharArray(),out=new char[bytes.length*2];for(int i=0;i<bytes.length;i++){int valueByte=bytes[i]&255;out[i*2]=hex[valueByte>>>4];out[i*2+1]=hex[valueByte&15];}return new String(out);
 }
 static String normalize(String value){return Normalizer.normalize(value==null?"":value.replace("\r\n","\n").replace('\u00a0',' '),Normalizer.Form.NFC).trim();}
 public static String fingerprint(String key,int occurrence){return "logical-v2:"+key+":"+occurrence;}
 private MessageIdentity(){}
}
