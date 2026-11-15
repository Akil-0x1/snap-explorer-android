package local.snapexplorer.mobile;
import java.util.*;
public final class SenderDirectionTest {
 static int n;static void c(boolean value){if(!value)throw new AssertionError("Sender direction must not guess another person's identity");n++;}
 public static void main(String[] args){Set<String> conflict=new HashSet<>(Arrays.asList("Owner"));Set<String> clear=Collections.emptySet();
  c(SenderDirection.resolve("","Owner",true,conflict)==0);c(SenderDirection.resolve("","Owner",false,conflict)==0);
  c(SenderDirection.resolve("Owner","Owner",true,conflict)==2);c(SenderDirection.resolve("Owner","Owner",false,conflict)==2);
  c(SenderDirection.resolve("Owner","Other",true,conflict)==1);c(SenderDirection.resolve("Owner","Other",false,conflict)==1);
  c(SenderDirection.resolve("","Other",false,clear)==1);c(SenderDirection.resolve("","Owner",true,clear)==2);
  c(!SenderDirection.groupSender("","Owner").equals(SenderDirection.groupSender("","Other")));
  c(SenderDirection.groupSender("Owner","Owner").equals("\u0001local-account"));c(SenderDirection.groupSender("Owner","Other").equals("Other"));
  c(!SenderDirection.groupSender("Owner","owner").equals("\u0001local-account"));
  System.out.println(n+" sender-direction identity checks passed.");
 }
}
