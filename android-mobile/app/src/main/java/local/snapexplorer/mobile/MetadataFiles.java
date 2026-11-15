package local.snapexplorer.mobile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Stores oversized JSON outside SQLite cursor rows, with a checked private-file reference. */
public final class MetadataFiles {
 private static final String PREFIX="@snap-json-v1:";
 public static String store(File stage,String json)throws Exception{
  if(isReference(json)||json.length()<=128000)return json;
  File directory=new File(stage,"metadata");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Could not retain raw metadata.");
  File file=new File(directory,UUID.randomUUID()+".json");MessageDigest sha=MessageDigest.getInstance("SHA-256");
  try(Writer out=new BufferedWriter(new OutputStreamWriter(new java.security.DigestOutputStream(new FileOutputStream(file),sha),StandardCharsets.UTF_8))){out.write(json);}
  return PREFIX+hex(sha.digest())+":"+file.getAbsolutePath();
 }
 public static boolean isReference(String value){return value!=null&&value.startsWith(PREFIX);}
 public static String reference(File file,String sha){return PREFIX+sha+":"+file.getAbsolutePath();}
 public static File checkedFile(File filesRoot,String value)throws Exception{
  if(!isReference(value))return null;String spec=value.substring(PREFIX.length());int split=spec.indexOf(':');if(split!=64)throw new IOException("Invalid metadata reference.");String expected=spec.substring(0,split);File file=new File(spec.substring(split+1)).getCanonicalFile(),root=filesRoot.getCanonicalFile();
  if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.isFile())throw new IOException("A retained metadata file is missing or outside private storage.");
  if(!ArchiveImporter.hashFile(file).equals(expected))throw new IOException("A raw metadata checksum did not match.");return file;
 }
 public static File referencedFile(String value)throws IOException{if(!isReference(value))return null;String spec=value.substring(PREFIX.length());if(spec.indexOf(':')!=64)throw new IOException("Invalid metadata reference.");return new File(spec.substring(65));}
 public static Reader reader(File filesRoot,String value)throws Exception{File file=checkedFile(filesRoot,value);return file==null?new StringReader(value):new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8));}
 public static String read(File filesRoot,String value)throws Exception{
  if(value==null)return "";File file=checkedFile(filesRoot,value);if(file==null)return value;
  if(file.length()>ArchiveImporter.MAX_ENTRY)throw new IOException("This large dataset requires the streaming reader.");
  try(InputStream in=new FileInputStream(file)){return new String(ArchiveImporter.readBounded(in,ArchiveImporter.MAX_ENTRY),StandardCharsets.UTF_8);}
 }
 private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
 private MetadataFiles(){}
}
