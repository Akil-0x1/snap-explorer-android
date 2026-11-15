package local.snapexplorer.mobile;
import java.io.*;
/** Bounded raw-text paging preserves unknown JSON structures and scalar arrays. */
public final class MetadataTextPage {
 public final String text;public final long next;public final boolean ended;
 private MetadataTextPage(String text,long next,boolean ended){this.text=text;this.next=next;this.ended=ended;}
 public static MetadataTextPage read(Reader reader,long offset,int limit)throws IOException{
  if(offset<0||limit<2||limit>65536)throw new IOException("Invalid page request");char[] skip=new char[8192];long remaining=offset;while(remaining>0){int n=reader.read(skip,0,(int)Math.min(skip.length,remaining));if(n<0)return new MetadataTextPage("",offset,true);remaining-=n;}
  char[] buffer=new char[limit+1];int count=0,n;while(count<buffer.length&&(n=reader.read(buffer,count,buffer.length-count))!=-1)count+=n;int length=Math.min(count,limit);if(length>0&&count>length&&Character.isHighSurrogate(buffer[length-1])&&Character.isLowSurrogate(buffer[length]))length--;return new MetadataTextPage(new String(buffer,0,length),offset+length,count<=limit);
 }
}
