package local.snapexplorer.mobile;
import java.io.*;import java.util.*;import java.util.zip.*;import javax.xml.parsers.*;
public final class ExportFormatsTest {
 public static void main(String[] args)throws Exception {
  require(ExportFormats.html("<script>&\"").equals("&lt;script&gt;&amp;&quot;"),"HTML escaping");
  require(ExportFormats.csv("=SUM(1,2)").startsWith("\"'="),"CSV formula neutralisation");
  require(ExportFormats.csv("\t@evil").startsWith("\"'"),"CSV whitespace attack");
  require(ExportFormats.json("a\n\"b").equals("\"a\\n\\\"b\""),"JSON escaping");
  require(ExportFormats.sha256("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"SHA256");
  List<String[]> rows=Arrays.asList(new String[]{"Metric","Value"},new String[]{"unsafe <&","=formula"});
  for(String type:new String[]{"ods","xlsx"}){
   ByteArrayOutputStream data=new ByteArrayOutputStream();ExportFormats.spreadsheet(data,type,rows);
   int files=0;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data.toByteArray()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){ByteArrayOutputStream text=new ByteArrayOutputStream();byte[] b=new byte[1000];int n;while((n=zip.read(b))!=-1)text.write(b,0,n);if(entry.getName().endsWith(".xml")||entry.getName().endsWith(".rels")){DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.newDocumentBuilder().parse(new ByteArrayInputStream(text.toByteArray()));}files++;}}
   require(files>1,type+" archive entries");
  }
  System.out.println("7 portable export tests passed.");
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
