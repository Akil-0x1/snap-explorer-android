package local.snapexplorer.mobile;
import java.util.Locale;

/** Deduplicate only identical source snapshots, never assume updated datasets are disjoint. */
public final class DatasetSnapshot {
 public static String key(String filename,String json){String base=filename.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1);return base.toLowerCase(Locale.ROOT)+":"+ExportFormats.sha256(json);}
 private DatasetSnapshot(){}
}
