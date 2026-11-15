package local.snapexplorer.mobile;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.zip.*;
public final class PortableMediaIndexTest {
 static ArchiveImporter.Report sample(boolean portable,boolean explicitReport)throws Exception{
  File file=File.createTempFile("portable-index-",".zip");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){if(portable){z.putNextEntry(new ZipEntry("manifest.json"));z.write("{\"format\":\"snap-explorer-portable-v1\"}".getBytes(StandardCharsets.UTF_8));z.closeEntry();}z.putNextEntry(new ZipEntry("media/photo.png"));z.write("synthetic local image".getBytes(StandardCharsets.UTF_8));z.closeEntry();z.putNextEntry(new ZipEntry("statistics.csv"));z.write("derived statistics".getBytes(StandardCharsets.UTF_8));z.closeEntry();if(explicitReport){z.putNextEntry(new ZipEntry("media/statistics.csv"));z.write("original attached CSV".getBytes(StandardCharsets.UTF_8));z.closeEntry();}}
  return ArchiveImporter.readFull(file,Files.createTempDirectory("portable-index-stage-").toFile(),m->{},(a,b)->{});
 }
 public static void main(String[] args)throws Exception{
  if(sample(true,false).media.size()!=1)throw new AssertionError("Portable restore must not turn its generated statistics.csv into an extra media file");
  if(sample(false,false).media.size()!=2)throw new AssertionError("An ordinary export's CSV must still be retained");
  if(sample(true,true).media.size()!=2)throw new AssertionError("An explicit CSV asset inside media/ must still be retained");
  System.out.println("3 portable media-catalog round-trip checks passed.");
 }
}
