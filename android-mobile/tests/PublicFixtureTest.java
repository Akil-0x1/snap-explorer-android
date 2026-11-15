package local.snapexplorer.mobile;
import java.io.*;import java.nio.file.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** Public MIT test records; never reads an account export. */
public final class PublicFixtureTest {
 static int checks;static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 public static void main(String[] args)throws Exception{
  File folder=new File(args[0]);List<ChatMessage> all=new ArrayList<>();ChatParser.json(new String(Files.readAllBytes(new File(folder,"chat_history.json").toPath()),StandardCharsets.UTF_8),all::add);ChatParser.json(new String(Files.readAllBytes(new File(folder,"chat_history-copy.json").toPath()),StandardCharsets.UTF_8),all::add);
  check(all.size()==5,"Both public fixture inputs retain all records");check(all.stream().filter(m->m.text.equals("hey")&&m.own).count()==2,"Sender direction retained across overlapping inputs");ChatMessage media=all.stream().filter(m->m.type.equals("MEDIA")).findFirst().get();check(media.mediaRefs.equals(Arrays.asList("b~abc","b~def")),"Public pipe-separated media IDs become separate references");check(media.timestamp==1704099600000L,"Explicit Created date remains correct alongside epoch field");
  MediaItem a=new MediaItem();a.id="local-a";a.name="2024-01-02_b~abc.jpg";a.originalPath="chat_media/"+a.name;LocalMediaIndex index=new LocalMediaIndex(Arrays.asList(a));check(index.resolve("b~abc").equals(a.id),"Public tilde media ID resolves within dated filename");check(index.resolve("b~def").isEmpty(),"Missing media ID remains unmatched");MediaItem b=new MediaItem();b.id="local-b";b.name="2024-01-03_b~abc.mp4";b.originalPath="chat_media/"+b.name;ChatMessage old=new ChatMessage("pal","pal","pal","[MEDIA]","MEDIA","date",0);old.mediaRefs.add("b~abc|b~def");ArchiveImporter.mediaLinker(Arrays.asList(a),m->{}).accept(old);check(old.mediaRefs.contains("local-a"),"Existing combined media references can be reindexed without reimport");check(new LocalMediaIndex(Arrays.asList(a,b)).resolve("b~abc").isEmpty(),"Colliding tilde IDs never choose an arbitrary attachment");
  System.out.println(checks+" public Snapchat fixture checks passed.");
 }
}
