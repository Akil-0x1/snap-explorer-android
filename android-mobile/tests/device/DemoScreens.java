package local.snapexplorer.mobile;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Captures the real released UI using only fictional sample records. Emulator only. */
public final class DemoScreens extends Instrumentation {
 private boolean allowed;
 public void onCreate(Bundle args){allowed=args!=null&&"synthetic-only".equals(args.getString("demo"));super.onCreate(args);start();}
 public void onStart(){Bundle out=new Bundle();try{if(!allowed||!(Build.FINGERPRINT.contains("generic")||Build.MODEL.toLowerCase(Locale.ROOT).contains("sdk")))throw new IllegalStateException("Synthetic demo requires explicit consent and an emulator");capture();out.putString("stream","PASS synthetic demo: chats, archive search, in-chat search screenshots\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){StringWriter w=new StringWriter();e.printStackTrace(new PrintWriter(w));out.putString("stream",w.toString());finish(Activity.RESULT_CANCELED,out);}}
 private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private void invoke(Activity a,String method,Class<?>[] types,Object... args){runOnMainSync(()->{try{Method m=a.getClass().getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(a,args);}catch(Exception e){throw new RuntimeException(e);}});}
 private void settle(){waitForIdleSync();SystemClock.sleep(1200);waitForIdleSync();}
 private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream in=new FileInputStream(fd.getFileDescriptor())){byte[] b=new byte[4096];while(in.read(b)!=-1){}}}
 private void shot(Activity a,String name)throws Exception{runOnMainSync(()->((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(a.getWindow().getDecorView().getWindowToken(),0));settle();shell("screencap -p /data/local/tmp/snap-demo-"+name+".png");}
 private void walk(View v,List<EditText> fields){if(v instanceof EditText)fields.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)walk(((ViewGroup)v).getChildAt(i),fields);}
 private void capture()throws Exception{
  getUiAutomation();Context c=getTargetContext();File zip=new File(c.getCacheDir(),"portfolio-synthetic.zip");try(InputStream in=c.getAssets().open("sample.zip");OutputStream out=new FileOutputStream(zip)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  ChatRepository repo=new ChatRepository(c);repo.importArchives(Collections.singletonList(new ChatRepository.Input(zip,"Fictional demo data",false)),true,(a,b)->{});SQLiteDatabase db=repo.getWritableDatabase();db.execSQL("INSERT INTO chats(id,title) VALUES ('portfolio','Project plans')");
  String[] bodies={"The project demo is ready for Saturday.","Great! Coffee after the project demo?","Coffee sounds good. Let's meet at 10.","I saved the checklist here so we can find it later.","Bring the prototype and your notes.","Perfect, see you on Saturday!"};
  for(int i=0;i<bodies.length;i++)db.execSQL("INSERT INTO messages(chat,sender,body,kind,date,epoch,own) VALUES ('portfolio',?,?,'TEXT',?,?,?)",new Object[]{i%2==0?"Alex":"You",bodies[i],"2026-11-03 10:0"+i+":00 UTC",1793700000000L+i*60000L,i%2});
  db.execSQL("INSERT OR REPLACE INTO message_search(docid,body,sender) SELECT id,body,sender FROM messages");repo.close();
  c.getSharedPreferences("chat_reader",0).edit().clear().commit();c.getSharedPreferences("chat_organization",0).edit().clear().commit();new ChatOrganization(c).pin("portfolio",true);
  c.getSharedPreferences("display",0).edit().putString("my_sender","You").putString("theme","dark").putString("timezone","UTC").commit();
  Activity home=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();shot(home,"chats");
  invoke(home,"searchScreen",new Class<?>[]{String.class,String.class},null,null);settle();List<EditText> fields=new ArrayList<>();runOnMainSync(()->walk(home.getWindow().getDecorView(),fields));if(fields.size()!=3)throw new AssertionError("Search fields unavailable");runOnMainSync(()->{fields.get(0).setText("coffee");fields.get(0).onEditorAction(EditorInfo.IME_ACTION_SEARCH);});settle();shot(home,"archive-search");
  Activity chat=startActivitySync(new Intent(c,ChatActivity.class).putExtra("chat","portfolio").putExtra("title","Project plans").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();invoke(chat,"search",new Class<?>[]{});runOnMainSync(()->{try{EditText input=(EditText)field(chat,"searchInput");input.setText("coffee");input.onEditorAction(EditorInfo.IME_ACTION_SEARCH);}catch(Exception e){throw new RuntimeException(e);}});settle();ListView results=(ListView)field(chat,"resultsList");runOnMainSync(()->results.performItemClick(results.getAdapter().getView(0,null,results),0,results.getAdapter().getItemId(0)));settle();shot(chat,"chat-search");runOnMainSync(chat::finish);runOnMainSync(home::finish);
 }
}
