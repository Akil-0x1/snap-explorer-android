package local.snapexplorer.mobile;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
public final class MetadataFilesTest {
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("snap-metadata-private-").toFile(),stage=new File(root,"archives/test");stage.mkdirs();
  String small="{\"Friends\":[]}";if(!MetadataFiles.store(stage,small).equals(small))throw new AssertionError("small JSON inline");
  StringBuilder b=new StringBuilder("{\"data\":\"");for(int i=0;i<200000;i++)b.append("sample ");b.append("\"}");String json=b.toString(),ref=MetadataFiles.store(stage,json);
  if(ref.length()>1000||!MetadataFiles.read(root,ref).equals(json))throw new AssertionError("large JSON roundtrip without cursor-sized value");
  try{MetadataFiles.read(new File(root,"unrelated"),ref);throw new AssertionError("private root");}catch(IOException expected){}
  String path=ref.substring(ref.lastIndexOf(':')+1);Files.write(Paths.get(path),"changed".getBytes(StandardCharsets.UTF_8));
  try{MetadataFiles.read(root,ref);throw new AssertionError("checksum");}catch(IOException expected){}
  Files.delete(Paths.get(path));try{MetadataFiles.read(root,ref);throw new AssertionError("missing file");}catch(IOException expected){}
  if(!MetadataFiles.read(root,small).equals(small))throw new AssertionError("inline read");
  System.out.println("6 large-metadata private storage tests passed.");
 }
}
