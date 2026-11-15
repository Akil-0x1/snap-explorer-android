package local.snapexplorer.mobile;
import java.util.*;
public final class SearchGroupingTest {
 static int n;static void check(boolean value){if(!value)throw new AssertionError("Search grouping policy mismatch");n++;}
 static String k(boolean group,long id,String sender,String body,long time,String media,String reply){return SearchGrouping.key(group,id,"chat",sender,body,time,Arrays.asList(media),reply);}
 public static void main(String[] args){
  check(k(true,1,"sender","hello",61000,"a","").equals(k(true,2,"sender","hello",85000,"a","")));
  check(!k(false,1,"sender","hello",61000,"a","").equals(k(false,2,"sender","hello",85000,"a","")));
  check(!k(true,1,"sender","hello",61000,"a","").equals(k(true,2,"sender","hello",121000,"a","")));
  check(!k(true,1,"sender","hello",61000,"a","").equals(k(true,2,"other","hello",61000,"a","")));
  check(!k(true,1,"sender","hello",61000,"a","").equals(k(true,2,"sender","hello",61000,"b","")));
  check(!k(true,1,"sender","hello",61000,"a","quote A").equals(k(true,2,"sender","hello",61000,"a","quote B")));
  check(!k(true,1,"sender","hello",0,"a","").equals(k(true,2,"sender","hello",0,"a","")));
  check(!k(true,1,"sender","Hi",61000,"a","").equals(k(true,2,"sender","hi",61000,"a","")));
  check(k(true,1,"sender","hello\nthere",61000,"a","").equals(k(true,2,"sender","hello there",61000,"a","")));
  Map<String,Integer> groups=new LinkedHashMap<>();for(int i=0;i<1005;i++)for(int copy=0;copy<2;copy++){String key=k(true,i*2+copy,"sender","needle "+i,60000L*(i+1)+copy*1000,"a","");groups.put(key,groups.getOrDefault(key,0)+1);}check(groups.size()==1005);check(groups.values().stream().allMatch(x->x==2));check(SearchGrouping.SCHEMA.contains("group_key TEXT NOT NULL UNIQUE"));
  System.out.println(n+" grouped-search identity checks passed.");
 }
}
