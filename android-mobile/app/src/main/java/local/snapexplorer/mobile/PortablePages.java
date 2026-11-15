package local.snapexplorer.mobile;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.Writer;
import java.util.*;

/** Standalone local archive views. No copied source HTML, remote URLs or tile services. */
public final class PortablePages {
 private static String h(String value){return ExportFormats.html(value==null?"":value);}
 public static void head(Writer out,String title)throws Exception{out.write("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'\"><title>"+h(title)+"</title><style>body{font:16px system-ui;background:#101713;color:#f0f5ef;max-width:1000px;margin:auto;padding:20px}a{color:#f5e642}article{padding:14px;margin:12px 0;background:#1b251f;border-radius:14px}img,video,svg{max-width:100%;max-height:65vh}audio{max-width:100%}small{color:#a6b9aa}table{border-collapse:collapse;width:100%}td,th{text-align:left;padding:10px;border-bottom:1px solid #49654c}p{overflow-wrap:anywhere}</style></head><body><a href=\"index.html\">Archive home</a><h1>"+h(title)+"</h1><small>Local files only. No online resources.</small><p><input id=\"filter\" placeholder=\"Filter this page\" style=\"padding:12px;width:90%\"></p><script>addEventListener('DOMContentLoaded',function(){document.getElementById('filter').addEventListener('input',function(){var q=this.value.toLocaleLowerCase();document.querySelectorAll('article,tbody tr').forEach(function(r){r.hidden=r.textContent.toLocaleLowerCase().indexOf(q)===-1;});});});</script>");}
 public static void gallery(Context context,SQLiteDatabase db,Writer out,Map<String,String> paths,boolean memories)throws Exception{
  head(out,memories?"Memories":"Media gallery");
  String sql=memories?"SELECT COALESCE(f.name,'Missing local file'),f.id,f.kind,m.date,m.latitude,m.longitude FROM memories m LEFT JOIN media f ON f.id=m.media_id ORDER BY m.epoch DESC":"SELECT name,id,kind,CAST(epoch AS TEXT),latitude,longitude FROM media ORDER BY epoch DESC";
  try(Cursor c=db.rawQuery(sql,null)){while(c.moveToNext()){
   String id=c.getString(1),kind=c.getString(2),path=id==null?null:paths.get(id);String date=memories?c.getString(3):AppStyle.date(context,c.getLong(3),"Unknown date");
   out.write("<article><h2>"+h(c.getString(0))+"</h2><small>"+h(date)+"</small><p>");
   if(path==null)out.write("No local file was included in the imported exports.");else if("image".equals(kind))out.write("<img loading=\"lazy\" src=\""+h(path)+"\" alt=\""+h(c.getString(0))+"\">");else if("video".equals(kind))out.write("<video controls preload=\"none\" src=\""+h(path)+"\"></video>");else if("audio".equals(kind))out.write("<audio controls preload=\"none\" src=\""+h(path)+"\"></audio>");
   out.write("</p>");if(path!=null)out.write("<a href=\""+h(path)+"\" download>Save original</a>");if(!c.isNull(4)&&!c.isNull(5))out.write("<p>Coordinates: "+c.getDouble(4)+", "+c.getDouble(5)+"</p>");out.write("</article>");
  }}out.write("</body></html>");
 }
 public static void statistics(Context context,SQLiteDatabase db,Writer out)throws Exception{
  head(out,"Archive statistics");ArchiveStats stats=ArchiveStats.read(context,db);out.write("<p>Timezone: "+h(stats.timezone)+"</p><table><thead><tr><th>Metric</th><th>Value</th></tr></thead><tbody>");for(String[] row:stats.rows())out.write("<tr><td>"+h(row[0])+"</td><td>"+h(row.length>1?row[1]:"")+"</td></tr>");out.write("</tbody></table></body></html>");
 }
 public static void map(SQLiteDatabase db,Writer out)throws Exception{
  head(out,"Offline locations");List<OfflineMapView.Point> points=LocationRecords.read(db);Collections.sort(points,Comparator.comparingLong(p->p.time));out.write("<p>Coordinate plot with no street-map tiles or geocoding. "+points.size()+" supplied coordinates.</p><svg viewBox=\"0 0 1000 500\" role=\"img\" aria-label=\"Local exported coordinate plot\"><rect width=\"1000\" height=\"500\" fill=\"#1b251f\"/>");for(int longitude=-180;longitude<=180;longitude+=30){double x=(longitude+180)/360.0*1000;out.write("<path d=\"M"+x+" 0V500\" stroke=\"#49654c\"/>");}for(int latitude=-90;latitude<=90;latitude+=30){double y=(90-latitude)/180.0*500;out.write("<path d=\"M0 "+y+"H1000\" stroke=\"#49654c\"/>");}for(OfflineMapView.Point p:points){double x=(p.lon+180)/360.0*1000,y=(90-p.lat)/180.0*500;out.write("<circle cx=\""+x+"\" cy=\""+y+"\" r=\"3\" fill=\"#f5e642\"><title>"+h(p.label)+" · "+p.lat+", "+p.lon+"</title></circle>");}out.write("</svg></body></html>");
 }
 private PortablePages(){}
}
