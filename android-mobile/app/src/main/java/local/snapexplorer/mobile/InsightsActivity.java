package local.snapexplorer.mobile;

import android.app.*;import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.concurrent.*;import java.util.regex.*;

/** Native analytics, integrity verification and location views with no network surfaces. */
public final class InsightsActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private ChatRepository repository;private LinearLayout content;private TextView status;private boolean destroyed;private int generation;private AppStyle style;
 @Override public void onCreate(Bundle state){AppStyle.apply(this);super.onCreate(state);AppStyle.systemBars(this);style=new AppStyle(this);repository=new ChatRepository(getApplicationContext());
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,20,20,20);root.setBackgroundColor(style.background);setContentView(root);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(20,20+i.getSystemWindowInsetTop(),20,20+i.getSystemWindowInsetBottom());return i;});
  status=label("Archive insights",25,style.ink);root.addView(status);LinearLayout tabs=new LinearLayout(this);for(String page:new String[]{"dashboard","map","integrity"}){Button b=button(page.equals("dashboard")?"Overview":page.equals("map")?"Locations":"Verify",()->show(page));tabs.addView(b,new LinearLayout.LayoutParams(0,56,1));}root.addView(tabs);
  content=new LinearLayout(this);content.setOrientation(1);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));Button back=button("Back",this::finish);root.addView(back);show(getIntent().getStringExtra("page")==null?"dashboard":getIntent().getStringExtra("page"));
 }
 private TextView label(String value,int size,int color){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setPadding(4,12,4,12);return text;}
 private Button button(String text,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setMinHeight(52);b.setOnClickListener(v->action.run());return b;}
 private void post(int token,Runnable action){runOnUiThread(()->{if(!destroyed&&token==generation)action.run();});}
 private LinearLayout scrolling(){content.removeAllViews();ScrollView scroll=new ScrollView(this);LinearLayout column=new LinearLayout(this);column.setOrientation(1);scroll.addView(column);content.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return column;}
 private void show(String page){int token=++generation;content.removeAllViews();status.setText(page.equals("map")?"Offline locations":page.equals("integrity")?"Integrity check":"Archive insights");content.addView(label("Working locally…",16,style.muted));
  worker.execute(()->{try{
   SQLiteDatabase db=repository.getReadableDatabase();
   if(page.equals("map")){List<OfflineMapView.Point> points=LocationRecords.read(db);post(token,()->renderMap(points));}
   else if(page.equals("integrity")){List<String> report=ArchiveExporter.verify(this,db);post(token,()->{LinearLayout column=scrolling();column.addView(label("SHA-256 and SQLite checks",19,style.accent));for(String line:report)column.addView(label(line,15,style.ink));column.addView(label("Checksums detect changes to retained local media. They do not prove that Snapchat exported every message or that a missing remote file can be recovered.",13,style.muted));});}
   else {ArchiveStats stats=ArchiveStats.read(this,db);List<String[]> data=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id,filename FROM metadata ORDER BY filename",null)){while(c.moveToNext())data.add(new String[]{c.getString(0),c.getString(1)});}post(token,()->renderStats(stats,data));}
  }catch(Exception e){post(token,()->{LinearLayout column=scrolling();column.addView(label("This view could not be loaded. Your imported data is unchanged.",16,style.ink));});}});
 }
 private void renderStats(ArchiveStats stats,List<String[]> metadata){LinearLayout column=scrolling();column.addView(label("YOUR ARCHIVE AT A GLANCE",12,style.accent));
  for(Map.Entry<String,Long> entry:stats.summary.entrySet())column.addView(label(entry.getValue()+"   "+entry.getKey(),23,style.ink));
  column.addView(label("Activity timezone: "+stats.timezone+". Change it in the main app’s display settings.",13,style.muted));
  chart(column,"Most active conversations",stats.contacts,12);chart(column,"Message types",stats.types,16);chart(column,"Messages by weekday",stats.weekdays,7);chart(column,"Messages by hour",stats.hours,24);
  LinkedHashMap<String,Long> recent=new LinkedHashMap<>();List<Map.Entry<String,Long>> days=new ArrayList<>(stats.days.entrySet());for(int i=Math.max(0,days.size()-30);i<days.size();i++)recent.put(days.get(i).getKey(),days.get(i).getValue());chart(column,"Daily activity · latest 30 active days",recent,30);
  chart(column,"Metadata records by export file",stats.metadata,40);
  column.addView(label("Patterns in supplied datasets",20,style.accent));column.addView(label("Identical copies of the same filename and JSON are counted once. Updated snapshots are shown as separate source variants; their overlapping events are not added together. Charts count supplied records, not inferred visits, views or relationships.",13,style.muted));
  for(ArchiveStats.Dataset dataset:stats.datasets){
   column.addView(label(dataset.label,19,style.ink));
   if(dataset.result==null){column.addView(label(dataset.error,13,style.muted));continue;}
   column.addView(label(dataset.result.records+" supplied records",13,style.muted));
   if(dataset.result.daily.isEmpty())column.addView(label("No recognised dates in these records; a timeline cannot be reconstructed.",13,style.muted));else{LinkedHashMap<String,Long> latest=new LinkedHashMap<>();List<Map.Entry<String,Long>> dates=new ArrayList<>(dataset.result.daily.entrySet());for(int i=Math.max(0,dates.size()-30);i<dates.size();i++)latest.put(dates.get(i).getKey(),dates.get(i).getValue());chart(column,"Dated records · latest 30 days with activity",latest,30);}
   if(dataset.result.categories.isEmpty())column.addView(label("No supported category fields were supplied.",13,style.muted));else chart(column,"Categories present in the export",dataset.result.categories,16);
   if(dataset.result.contacts.isEmpty())column.addView(label("No supported sender, username or recipient fields were supplied.",13,style.muted));else chart(column,"Contacts present in the export",dataset.result.contacts,16);
  }
column.addView(label("Export metadata",20,style.accent));column.addView(label("Open the supplied datasets, including friends, search, snaps, stories and account activity when present. Counts describe exported records, not recovered missing data.",13,style.muted));
  for(String[] item:metadata)column.addView(button(item[1],()->dataset(item[0],item[1])));
  column.addView(button("Export statistics and archive",()->startActivity(new Intent(this,ExportActivity.class))));
 }
 private void chart(LinearLayout column,String title,Map<String,Long> data,int limit){column.addView(label(title,18,style.ink));if(data.isEmpty()){column.addView(label("No records in this export.",13,style.muted));return;}column.addView(new Bars(data,limit),new LinearLayout.LayoutParams(-1,Math.min(limit,data.size())*54+20));}
 private final class Bars extends View {
  private final List<Map.Entry<String,Long>> rows;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private long max=1;
  Bars(Map<String,Long> data,int limit){super(InsightsActivity.this);rows=new ArrayList<>(data.entrySet());if(rows.size()>limit)rows.subList(limit,rows.size()).clear();for(Map.Entry<String,Long> e:rows)max=Math.max(max,e.getValue());StringBuilder description=new StringBuilder();for(Map.Entry<String,Long> e:rows)description.append(e.getKey()).append(' ').append(e.getValue()).append(". ");setContentDescription(description.toString());}
  @Override protected void onDraw(Canvas c){super.onDraw(c);int y=5;for(Map.Entry<String,Long> row:rows){paint.setColor(style.muted);paint.setTextSize(21);String name=row.getKey();if(name.length()>37)name=name.substring(0,34)+"…";c.drawText(name,4,y+21,paint);String n=row.getValue().toString();c.drawText(n,getWidth()-paint.measureText(n)-5,y+21,paint);paint.setColor(style.accent);c.drawRoundRect(4,y+29,Math.max(5,4+(getWidth()-10)*row.getValue()/(float)max),y+40,5,5,paint);y+=54;}}
 }
 private void dataset(String id,String filename){startActivity(new Intent(this,MetadataActivity.class).putExtra("dataset_id",id).putExtra("filename",filename));}
 private void renderMap(List<OfflineMapView.Point> points){content.removeAllViews();if(points.isEmpty()){content.addView(label("No exported coordinates were found. This offline app does not request your phone’s location or fetch missing records.",16,style.ink));return;}content.addView(label("Local coordinate plot. No street-map tiles or online requests.",13,style.muted));OfflineMapView map=new OfflineMapView(this,points);content.addView(map,new LinearLayout.LayoutParams(-1,0,1));SeekBar timeline=new SeekBar(this);timeline.setMax(1000);timeline.setProgress(1000);timeline.setContentDescription("Location timeline position");timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser)map.setVisibleFraction(value/1000f);}});content.addView(timeline);LinearLayout controls=new LinearLayout(this);controls.addView(button("Animate timeline",map::animateRoute),new LinearLayout.LayoutParams(0,58,1));controls.addView(button("Show all",map::showAll),new LinearLayout.LayoutParams(0,58,1));content.addView(controls);}
 @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();new Thread(()->{try{worker.awaitTermination(30,TimeUnit.SECONDS);}catch(InterruptedException ignored){}if(worker.isTerminated())repository.close();}).start();super.onDestroy();}
}
