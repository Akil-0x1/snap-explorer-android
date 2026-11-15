package local.snapexplorer.mobile;
import java.util.*;
public final class MessageCopiesTest {
 public static void main(String[] args){
  check(same("Hi","Hi",1000,1000,"","",Collections.emptyList(),Collections.emptyList()));
  check(same("Hi","Hi",1001,1999,"","",Collections.emptyList(),Collections.emptyList()));
  check(!same("Hi","Hi",1000,2000,"","",Collections.emptyList(),Collections.emptyList()));
  check(!same("Hi","Hi",1000,1000,"one","two",Collections.emptyList(),Collections.emptyList()));
  check(!same("Hi","Hi",0,0,"","",Collections.emptyList(),Collections.emptyList()));
  check(!same("Caption","Caption",1000,1000,"","",Arrays.asList("a"),Arrays.asList("b")));
  check(same("hello\r\nthere","hello\nthere",1000,1000,"","",Arrays.asList("a"),Arrays.asList("a")));
  check(!same("Hi","Hi!",1000,1000,"","",Collections.emptyList(),Collections.emptyList()));
  check(visible("same",61000,"a","").equals(visible("same",119000,"a","")));
  check(!visible("same",61000,"a","").equals(visible("same",120000,"a","")));
  check(!visible("same",61000,"a","").equals(visible("other",61000,"a","")));
  check(!visible("same",61000,"a","").equals(visible("same",61000,"b","")));
  check(!visible("same",61000,"a","one").equals(visible("same",61000,"a","two")));
  check(visible("same",0,"a","")==null);
  check(visible("hello  there",61000,"a","").equals(visible("hello\nthere",61000,"a","")));
  check(!visible("Hi",61000,"a","").equals(visible("hi",61000,"a","")));
  System.out.println("16 reversible grouping and compact-view checks passed.");
 }
 static String visible(String body,long time,String media,String reply){return MessageCopies.visibleKey("chat","sender",body,time,Arrays.asList(media),reply);}
 static boolean same(String a,String b,long ta,long tb,String ia,String ib,List<String> ma,List<String> mb){return MessageCopies.same("chat","sender",a,"TEXT",ta,ia,ma,"chat","sender",b,"text",tb,ib,mb);}
 static void check(boolean value){if(!value)throw new AssertionError("Grouping policy mismatch");}
}
