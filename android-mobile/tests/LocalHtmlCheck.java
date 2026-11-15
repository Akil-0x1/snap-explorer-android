package local.snapexplorer.mobile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
/** Optional local-file check. Prints counts only; never stores or prints private messages. */
public final class LocalHtmlCheck {
 public static void main(String[] args)throws Exception{
  Path path=Paths.get(args[0]);String html=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);List<ChatMessage> parsed=new ArrayList<>();ChatParser.html(path.getFileName().toString(),html,parsed::add);
  List<Element> expected=new ArrayList<>();for(Element e:Jsoup.parse(html).select(".rightpanel > div"))if(e.selectFirst("h4")!=null&&e.selectFirst("h6")!=null)expected.add(e);
  if(parsed.size()!=expected.size())throw new AssertionError("Message count mismatch");int refs=0;
  for(int i=0;i<parsed.size();i++){ChatMessage m=parsed.get(i);Element e=expected.get(i);if(!m.sender.equals(e.selectFirst("h4").text())||!m.date.equals(e.selectFirst("h6").text()))throw new AssertionError("Sender/date mismatch");Element paragraph=e.selectFirst("p");if(paragraph!=null&&!paragraph.wholeText().trim().isEmpty()&&!m.text.equals(paragraph.wholeText().trim()))throw new AssertionError("Text mismatch");refs+=m.mediaRefs.size();}
  List<ChatMessage> streamed=new ArrayList<>();try(java.io.Reader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){ChatParser.htmlStream(path.getFileName().toString(),r,streamed::add);}if(streamed.size()!=parsed.size())throw new AssertionError("Streaming count mismatch");for(int i=0;i<parsed.size();i++)if(!parsed.get(i).text.equals(streamed.get(i).text)||parsed.get(i).timestamp!=streamed.get(i).timestamp)throw new AssertionError("Streaming content mismatch");
  System.out.println("PASS local HTML: "+parsed.size()+" messages; "+refs+" local attachment references; DOM and streaming results agree. Private content was not printed or copied into test assets.");
 }
}
