package local.snapexplorer.mobile;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class DuplicateEntriesTest {
 public static void main(String[] args)throws Exception{
  File fixtures=new File(args[0]);
  ArchiveImporter.Report different=ArchiveImporter.readFull(new File(fixtures,"duplicate-media-different.zip"),Files.createTempDirectory("duplicate-distinct-").toFile(),m->{},(n,total)->{});
  if(different.media.size()!=2)throw new AssertionError("Different payloads under the same ZIP name must both survive");
  Set<String> bodies=new HashSet<>();for(MediaItem m:different.media)bodies.add(new String(Files.readAllBytes(new File(m.storedPath).toPath()),StandardCharsets.UTF_8));
  if(!bodies.contains("first distinct media")||!bodies.contains("second distinct media"))throw new AssertionError("Entry identity must use its offset, not last matching filename");
  ArchiveImporter.Report same=ArchiveImporter.readFull(new File(fixtures,"duplicate-media-identical.zip"),Files.createTempDirectory("duplicate-identical-").toFile(),m->{},(n,total)->{});
  if(same.media.size()!=1)throw new AssertionError("Identical repeated media should deduplicate by content");
  if(different.warnings.isEmpty())throw new AssertionError("Repeated names should be reported in import notes");
  List<ChatMessage> messages=new ArrayList<>();ArchiveImporter.Report ambiguous=ArchiveImporter.readFull(new File(fixtures,"duplicate-with-chat.zip"),Files.createTempDirectory("duplicate-reference-").toFile(),messages::add,(n,total)->{});
  for(MediaItem media:ambiguous.media)if(messages.get(0).mediaRefs.contains(media.id))throw new AssertionError("Ambiguous duplicate filename must not silently select a media payload");
  rejected(fixtures,"duplicate-metadata.zip");rejected(fixtures,"bad-media-crc.zip");
  ArchiveImporter.Report zip64=ArchiveImporter.readFull(new File(fixtures,"zip64-entry.zip"),Files.createTempDirectory("zip64-entry-").toFile(),m->{},(n,total)->{});if(zip64.media.size()!=1)throw new AssertionError("ZIP64 entry");
  System.out.println("8 duplicate-entry, CRC and ZIP64 regression checks passed.");
 }
 private static void rejected(File fixtures,String name)throws Exception{try{ArchiveImporter.readFull(new File(fixtures,name),Files.createTempDirectory("zip-reject-").toFile(),m->{},(n,total)->{});}catch(IOException expected){return;}throw new AssertionError("Expected rejection: "+name);}
}
