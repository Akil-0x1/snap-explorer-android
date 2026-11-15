package local.snapexplorer.mobile;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.*;
import java.util.*;

/** Aggregates message activity without retaining the messages in memory. */
public final class ArchiveStats {
 public final LinkedHashMap<String,Long> summary=new LinkedHashMap<>();
 public final Map<String,Long> days=new TreeMap<>(),hours=new LinkedHashMap<>(),weekdays=new LinkedHashMap<>(),types=new LinkedHashMap<>(),contacts=new LinkedHashMap<>(),metadata=new LinkedHashMap<>();
 public static final class Dataset {
  public String label,filename,error="";
  public MetadataAnalytics.Result result;
 }
 public final List<Dataset> datasets=new ArrayList<>();
 public String timezone="UTC";
 public static ArchiveStats read(android.content.Context context,SQLiteDatabase db)throws Exception {
  return read(db,context.getSharedPreferences("display",0).getString("timezone","UTC"));
 }
 public static ArchiveStats read(SQLiteDatabase db)throws Exception { return read(db,"UTC"); }
 private static ArchiveStats read(SQLiteDatabase db,String timezone)throws Exception {
  ArchiveStats stats=new ArchiveStats();stats.timezone=timezone;
  ZoneId zone;try{zone=stats.timezone.equals("device")?ZoneId.systemDefault():ZoneId.of(stats.timezone);}catch(Exception ignored){zone=ZoneOffset.UTC;stats.timezone="UTC";}
  String[][] counts={{"Conversations","chats"},{"Messages","messages"},{"Media files","media"},{"Memories","memories"},{"Imported archives","archives"}};
  for(String[] pair:counts)try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+pair[1],null)){c.moveToFirst();stats.summary.put(pair[0],c.getLong(0));}
  for(int i=0;i<24;i++)stats.hours.put(String.format(Locale.ROOT,"%02d",i),0L);
  for(String day:new String[]{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"})stats.weekdays.put(day,0L);
  try(Cursor c=db.rawQuery("SELECT epoch,kind FROM messages",null)){
   while(c.moveToNext()){
    add(stats.types,c.getString(1)==null?"Unknown":c.getString(1));
    long time=c.getLong(0);if(time<=0)continue;
    ZonedDateTime date=Instant.ofEpochMilli(time).atZone(zone);
    add(stats.days,date.toLocalDate().toString());add(stats.hours,String.format(Locale.ROOT,"%02d",date.getHour()));
    add(stats.weekdays,new String[]{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"}[date.getDayOfWeek().getValue()-1]);
   }
  }
  try(Cursor c=db.rawQuery("SELECT c.title,COUNT(*) AS n FROM messages m JOIN chats c ON c.id=m.chat GROUP BY c.id,c.title ORDER BY n DESC LIMIT 20",null)){while(c.moveToNext())stats.contacts.put(c.getString(0),c.getLong(1));}
  Set<String> seenDatasets=new HashSet<>();Map<String,Integer> variants=new HashMap<>();
  try(Cursor c=db.rawQuery("SELECT filename,json FROM metadata ORDER BY filename,archive_id",null)){
   while(c.moveToNext()){
    String filename=c.getString(0),json=c.getString(1),base=filename.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1);
    String identity=DatasetSnapshot.key(filename,MetadataFiles.isReference(json)?json.substring(0,json.lastIndexOf(':')):json);
    if(!seenDatasets.add(identity))continue;
    int variant=variants.containsKey(base)?variants.get(base)+1:1;variants.put(base,variant);
    Dataset dataset=new Dataset();dataset.filename=filename;
    try{try(java.io.Reader reader=MetadataStore.reader(db,json)){dataset.result=MetadataAnalytics.analyze(base,reader,zone);}dataset.label=dataset.result.label+" · source variant "+variant;stats.metadata.put(dataset.label,dataset.result.records);}
    catch(Exception error){dataset.label=base+" · source variant "+variant;dataset.error="This dataset could not be safely analysed. Its original JSON is still available in Export metadata.";}
    stats.datasets.add(dataset);
   }
  }
  return stats;
 }
 private static void add(Map<String,Long> data,String key){data.put(key,data.containsKey(key)?data.get(key)+1:1L);}
 public List<String[]> rows(){
  List<String[]> rows=new ArrayList<>();rows.add(new String[]{"Metric","Count"});
  for(Map.Entry<String,Long> entry:summary.entrySet())rows.add(new String[]{entry.getKey(),entry.getValue().toString()});
  append(rows,"Top conversations",contacts);append(rows,"Message types",types);append(rows,"Daily messages ("+timezone+")",days);append(rows,"Hour of day ("+timezone+")",hours);append(rows,"Weekday ("+timezone+")",weekdays);append(rows,"Exported metadata records",metadata);
  for(Dataset dataset:datasets){if(dataset.result==null){rows.add(new String[]{dataset.label,dataset.error});continue;}append(rows,dataset.label+" · records by day ("+timezone+")",dataset.result.daily);append(rows,dataset.label+" · supplied categories",dataset.result.categories);append(rows,dataset.label+" · supplied contacts",dataset.result.contacts);}
  return rows;
 }
 private static void append(List<String[]> rows,String heading,Map<String,Long> data){rows.add(new String[]{"", ""});rows.add(new String[]{heading,"Count"});for(Map.Entry<String,Long> entry:data.entrySet())rows.add(new String[]{entry.getKey(),entry.getValue().toString()});}
}
