package local.snapexplorer.mobile;
import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import org.json.*;import java.util.*;import java.util.regex.*;
/** Bounded coordinate extraction shared by native and portable offline views. */
public final class LocationRecords {
 public static List<OfflineMapView.Point> read(SQLiteDatabase db)throws Exception {List<OfflineMapView.Point> points=new ArrayList<>();Set<String> seen=new HashSet<>();
  try(Cursor c=db.rawQuery("SELECT latitude,longitude,epoch,date FROM memories WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY epoch LIMIT 25000",null)){while(c.moveToNext())addPoint(points,seen,c.getDouble(0),c.getDouble(1),c.getLong(2),c.getString(3));}
  try(Cursor c=db.rawQuery("SELECT filename,json FROM metadata WHERE lower(filename) LIKE '%location%' OR lower(filename) LIKE '%map%' OR lower(filename) LIKE '%memories%'",null)){while(c.moveToNext()&&points.size()<25000){try{String label=c.getString(0);try(java.io.Reader reader=MetadataStore.reader(db,c.getString(1))){JsonRecords.read(reader,(group,record)->scan(record,points,seen,label,0,new int[]{0}));}}catch(Exception ignored){}}}return points;
 }
 private static void scan(Object value,List<OfflineMapView.Point> out,Set<String> seen,String label,int depth,int[] visited)throws Exception {
  if(depth>16||out.size()>=25000||++visited[0]>100000)return;
  if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)scan(array.opt(i),out,seen,label,depth+1,visited);return;}
  if(!(value instanceof JSONObject))return;JSONObject object=(JSONObject)value;Double lat=null,lon=null;long time=0;String date=label;Iterator<String> keys=object.keys();
  while(keys.hasNext()){String key=keys.next(),lower=key.toLowerCase(Locale.ROOT).replace(" ","");Object v=object.opt(key);if(lower.equals("latitude")||lower.equals("lat"))lat=number(v);if(lower.equals("longitude")||lower.equals("lon")||lower.equals("lng"))lon=number(v);if(lower.equals("date")||lower.equals("time")||lower.equals("timestamp")||lower.equals("created")){date=String.valueOf(v);time=ChatParser.timestamp(date);if(time==0)try{long numeric=Long.parseLong(date);time=numeric<100000000000L?numeric*1000:numeric;}catch(Exception ignored){}}if(lower.equals("location")&&v instanceof String){Matcher match=Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)").matcher((String)v);if(match.find()){lat=Double.parseDouble(match.group(1));lon=Double.parseDouble(match.group(2));}}}
  if(lat!=null&&lon!=null)addPoint(out,seen,lat,lon,time,date);
  keys=object.keys();while(keys.hasNext())scan(object.opt(keys.next()),out,seen,date,depth+1,visited);
 }
 private static Double number(Object v){try{return Double.parseDouble(String.valueOf(v));}catch(Exception ignored){return null;}}
 private static void addPoint(List<OfflineMapView.Point> out,Set<String> seen,double lat,double lon,long time,String label){if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;String key=lat+":"+lon+":"+time;if(seen.add(key))out.add(new OfflineMapView.Point(lat,lon,time,label));}
 private LocationRecords(){}
}
