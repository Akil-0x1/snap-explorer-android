package local.snapexplorer.mobile;
import android.app.*;import android.content.*;import android.database.*;import android.database.sqlite.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
/** Synthetic duplicate matches spanning pages, with distinct contexts retained. */
public final class SearchGroupsSmoke extends Instrumentation {
 private int checks;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{run();out.putString("stream","PASS "+checks+" grouped-search Android checks\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));out.putString("stream",s.toString());finish(Activity.RESULT_CANCELED,out);}}
 private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private long count(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){c.moveToFirst();return c.getLong(0);}}
 private void add(SQLiteDatabase db,String body,String sender,long time,String external,String refs,String reply){db.execSQL("INSERT INTO messages(chat,sender,body,kind,date,epoch,external_id,media_refs,reply_to,own) VALUES ('double',?,?,'TEXT','date',?,?,?,?,?)",new Object[]{sender,body,time,external,refs,reply,sender.equals("You")?1:0});}
 private void idle(Activity a)throws Exception{for(int n=0;n<300;n++){waitForIdleSync();if(!(Boolean)field(a,"busy")&&!(Boolean)field(a,"finding")&&!(Boolean)field(a,"resultsLoading")){SystemClock.sleep(150);return;}SystemClock.sleep(100);}throw new AssertionError("Search stayed busy");}
 private void invoke(Activity a,String name){runOnMainSync(()->{try{Method m=a.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});}
 private void run()throws Exception{
  Context context=getTargetContext();File zip=new File(context.getCacheDir(),"grouped-search-sample.zip");try(InputStream in=context.getAssets().open("sample.zip");OutputStream out=new FileOutputStream(zip)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  ChatRepository repo=new ChatRepository(context);repo.importArchives(Collections.singletonList(new ChatRepository.Input(zip,"Synthetic grouped search",false)),true,(a,b)->{});SQLiteDatabase db=repo.getWritableDatabase();db.execSQL("INSERT INTO chats(id,title) VALUES ('double','Grouped search')");long base=1800000000000L;
  for(int copy=0;copy<2;copy++)for(int i=0;i<310;i++)add(db,"needle "+i,"You",base+i*120000L+copy*20000+1000,"copy-"+copy+"-"+i,"[]","");
  add(db,"Original A","Alex",base,"quote-a","[]","");add(db,"Original B","Alex",base,"quote-b","[]","");
  add(db,"needle quote","You",base+80000000,"q1","[]","quote-a");add(db,"needle quote","You",base+80000001,"q2","[]","quote-b");
  List<String> media=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id FROM media LIMIT 2",null)){while(c.moveToNext())media.add(c.getString(0));}
  add(db,"needle photo","You",base+81000000,"p1","[\""+media.get(0)+"\"]","");add(db,"needle photo","You",base+81000001,"p2","[\""+media.get(1)+"\"]","");
  add(db,"needle repeat","You",base+82000000,"r1","[]","");add(db,"needle repeat","You",base+82120000,"r2","[]","");
  add(db,"needle who","You",base+83000000,"w1","[]","");add(db,"needle who","Alex",base+83000000,"w2","[]","");
  add(db,"needle nodate","You",0,"n1","[]","");add(db,"needle nodate","You",0,"n2","[]","");
  db.execSQL("INSERT OR REPLACE INTO message_search(docid,body,sender) SELECT id,body,sender FROM messages");repo.refreshMediaLinks();long before=count(db,"SELECT COUNT(*) FROM messages");
  try(ConversationResults grouped=ConversationResults.build(repo,"double","needle",true,"You",()->false,n->{})){
   check(grouped.count==320&&grouped.rawCount==630,"All duplicate groups counted across the complete query");Set<Long> ids=new HashSet<>();long copies=0;for(int offset=0;offset<320;offset+=100){ConversationResults.Page page=grouped.page(repo,offset,100);for(ChatRepository.Row r:page.rows){check(ids.add(r.id),"No group repeated across result pages");copies+=page.copies.get(r.id);}}check(ids.size()==320&&copies==630,"Page contents and group counts cover every raw match");
   check(grouped.at(repo,101).id==grouped.page(repo,100,100).rows.get(0).id,"Arrow position agrees with second result page");check(grouped.at(repo,0)==null&&grouped.at(repo,321)==null,"Arrow boundaries stop at grouped ends");
  }
  try(ConversationResults standard=ConversationResults.build(repo,"double","needle",false,"You",()->false,n->{})){check(standard.count==630,"Show every entry restores genuine or duplicate source records");}
  check(count(db,"SELECT COUNT(*) FROM messages")==before,"Grouped search never removes or changes source messages");
  boolean cancelled=false;try{ConversationResults.build(repo,"double","needle",true,"You",()->true,n->{});}catch(java.util.concurrent.CancellationException expected){cancelled=true;}check(cancelled,"Stale search can be cancelled without a partial result index");repo.close();
  context.getSharedPreferences("display",0).edit().putString("my_sender","You").commit();context.getSharedPreferences("chat_reader",0).edit().putBoolean("search_groups:double",true).commit();
  Activity chat=startActivitySync(new Intent(context,ChatActivity.class).putExtra("chat","double").putExtra("title","Grouped search").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle(chat);invoke(chat,"search");runOnMainSync(()->{try{EditText q=(EditText)field(chat,"searchInput");q.setText("needle");q.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);}catch(Exception e){throw new RuntimeException(e);}});idle(chat);
  check((Long)field(chat,"totalMatches")==320&&(Long)field(chat,"rawMatches")==630,"UI count distinguishes groups and original entries");check(((List<?>)field(chat,"searchResults")).size()==100,"UI loads a bounded grouped result page");invoke(chat,"loadResults");idle(chat);check(((List<?>)field(chat,"searchResults")).size()==200,"UI appends second grouped page");
  ListView list=(ListView)field(chat,"resultsList");runOnMainSync(()->list.performItemClick(list.getAdapter().getView(100,null,list),100,list.getAdapter().getItemId(100)));idle(chat);long selected=((ChatRepository.Row)field(chat,"searchHit")).id;check((Long)field(chat,"matchNumber")==101,"Tapping result sets its grouped arrow position");
  runOnMainSync(()->{try{Method m=chat.getClass().getDeclaredMethod("moveMatch",int.class);m.setAccessible(true);m.invoke(chat,-1);}catch(Exception e){throw new RuntimeException(e);}});idle(chat);check((Long)field(chat,"matchNumber")==102&&((ChatRepository.Row)field(chat,"searchHit")).id!=selected,"Older arrow skips other copies and selects next group");
  invoke(chat,"toggleSearchGrouping");idle(chat);check((Long)field(chat,"totalMatches")==630,"UI Show every entry restores all matches");invoke(chat,"toggleSearchGrouping");idle(chat);check((Long)field(chat,"totalMatches")==320,"Regrouping returns consistent count");invoke(chat,"closeSearch");check(((View)field(chat,"resultsPanel")).getVisibility()==View.GONE,"Closing grouped search restores conversation");runOnMainSync(chat::finish);
 }
}
