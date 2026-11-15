package local.snapexplorer.mobile;
import java.util.Set;
/** Display-only direction. Conflicting flags never identify a person automatically. */
public final class SenderDirection {
 public static final int UNKNOWN=0,INCOMING=1,OUTGOING=2;
 public static int resolve(String selected,String sender,boolean own,Set<String> conflicting){
  if(selected!=null&&!selected.isEmpty())return selected.equals(sender)?OUTGOING:INCOMING;
  if(conflicting.contains(sender))return UNKNOWN;
  return own?OUTGOING:INCOMING;
 }
 public static String groupSender(String selected,String sender){return selected!=null&&!selected.isEmpty()&&selected.equals(sender)?"\u0001local-account":sender;}
 private SenderDirection(){}
}
