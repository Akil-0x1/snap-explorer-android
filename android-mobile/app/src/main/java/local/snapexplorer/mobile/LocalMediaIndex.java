package local.snapexplorer.mobile;
import java.util.*;
import java.util.regex.*;
/** Exact/identifier aliases only. An ambiguous alias never selects an arbitrary file. */
public final class LocalMediaIndex {
 private static final Pattern TILDE=Pattern.compile("(?:^|_)([A-Za-z0-9]+~[A-Za-z0-9_-]+)(?=\\.|$)");
 private final Map<String,String> aliases=new HashMap<>();
 private static final Pattern IDENTIFIERS=Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{32,64}");
 public LocalMediaIndex(List<MediaItem> files){for(MediaItem file:files){put(file.id,file.id);path(file.originalPath,file.id);path(file.name,file.id);}}
 private void put(String alias,String id){if(alias==null||alias.trim().isEmpty())return;String key=alias.trim().toLowerCase(Locale.ROOT);if(!aliases.containsKey(key))aliases.put(key,id);else if(!id.equals(aliases.get(key)))aliases.put(key,"");}
 private void path(String value,String id){if(value==null)return;put(value,id);String leaf=value.substring(value.lastIndexOf('/')+1);put(leaf,id);int dot=leaf.lastIndexOf('.');if(dot>0)put(leaf.substring(0,dot),id);Matcher tilde=TILDE.matcher(leaf);while(tilde.find())put(tilde.group(1),id);Matcher matcher=IDENTIFIERS.matcher(leaf);while(matcher.find())put(matcher.group(),id);for(String token:leaf.split("[^A-Za-z0-9]+"))if(token.length()>=8)put(token,id);}
 public String resolve(String reference){LinkedHashSet<String> matches=new LinkedHashSet<>();for(String key:MediaReferences.localKeys(reference)){String id=aliases.get(key.toLowerCase(Locale.ROOT));if(id!=null&&!id.isEmpty())matches.add(id);if(reference.equals(key)&&id!=null&&id.isEmpty())return "";if(id==null){String leaf=key.substring(key.lastIndexOf('/')+1);id=aliases.get(leaf.toLowerCase(Locale.ROOT));if(id!=null&&!id.isEmpty())matches.add(id);}}return matches.size()==1?matches.iterator().next():"";}
 private LocalMediaIndex(){ }
}
