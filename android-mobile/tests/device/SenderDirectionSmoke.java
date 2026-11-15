package local.snapexplorer.mobile;
import android.app.*;import android.content.*;import android.database.*;import android.database.sqlite.*;import android.os.*;import android.view.*;import android.view.accessibility.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
/** Synthetic same-sender copies with contradictory source direction flags. */
public final class SenderDirectionSmoke extends Instrumentation {
 private int checks;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{run();out.putString("stream","PASS "+checks+" sender-direction Android checks\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));out.putString("stream",s.toString());finish(Activity.RESULT_CANCELED,out);}}
 private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private void idle(Activity a)throws Exception{for(int i=0;i<200;i++){waitForIdleSync();if(!(Boolean)field(a,"busy")&&!(Boolean)field(a,"finding")){SystemClock.sleep(150);return;}SystemClock.sleep(100);}throw new AssertionError("Reader busy");}
 private int direction(Activity a,ChatRepository.Row r)throws Exception{Method m=a.getClass().getDeclaredMethod("direction",ChatRepository.Row.class);m.setAccessible(true);return (Integer)m.invoke(a,r);}
 private void walk(View v,List<View> out){out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)walk(((ViewGroup)v).getChildAt(i),out);}
 private int senderLabels(Activity a,String name){int[] n={0};runOnMainSync(()->{List<View> views=new ArrayList<>();walk(a.getWindow().getDecorView(),views);for(View v:views)if(v.isShown()&&v instanceof TextView&&name.contentEquals(((TextView)v).getText()))n[0]++;});return n[0];}
 private View description(Activity a,String value){View[] hit={null};runOnMainSync(()->{List<View> views=new ArrayList<>();walk(a.getWindow().getDecorView(),views);for(View v:views)if(value.equals(v.getContentDescription()))hit[0]=v;});return hit[0];}
 private void choice(String label){for(int i=0;i<150;i++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(label)){if(!label.contentEquals(n.getText()))continue;while(n!=null&&!n.isClickable())n=n.getParent();if(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(200);return;}}SystemClock.sleep(100);}throw new AssertionError("Missing identity choice "+label);}
 private long flagSum(SQLiteDatabase db){try(Cursor c=db.rawQuery("SELECT SUM(own) FROM messages WHERE chat='flags'",null)){c.moveToFirst();return c.getLong(0);}}
 private void run()throws Exception{
  getUiAutomation();Context c=getTargetContext();File zip=new File(c.getCacheDir(),"sender-direction-sample.zip");try(InputStream in=c.getAssets().open("sample.zip");OutputStream out=new FileOutputStream(zip)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  ChatRepository repo=new ChatRepository(c);repo.importArchives(Collections.singletonList(new ChatRepository.Input(zip,"Synthetic direction flags",false)),true,(a,b)->{});SQLiteDatabase db=repo.getWritableDatabase();db.execSQL("INSERT INTO chats(id,title) VALUES ('flags','Direction test')");
  for(int i=0;i<3;i++)db.execSQL("INSERT INTO messages(chat,sender,body,kind,date,epoch,external_id,own) VALUES ('flags',?,'Synthetic repeated message','TEXT','date',1800000001000,?,?)",new Object[]{i==2?"OtherExample":"OwnerExample","direction-"+i,i==0?1:0});db.execSQL("INSERT OR REPLACE INTO message_search(docid,body,sender) SELECT id,body,sender FROM messages");
  List<ChatRepository.Row> rows=repo.search("Synthetic","flags",null,null);ChatRepository.Row ownerA=null,ownerB=null,other=null;for(ChatRepository.Row r:rows)if(r.sender.equals("OtherExample"))other=r;else if(r.own)ownerA=r;else ownerB=r;final ChatRepository.Row a=ownerA,b=ownerB,different=other;
  c.getSharedPreferences("display",0).edit().remove("my_sender").putString("theme","dark").commit();c.getSharedPreferences("chat_reader",0).edit().putBoolean("compact:flags",false).commit();
  Activity chat=startActivitySync(new Intent(c,ChatActivity.class).putExtra("chat","flags").putExtra("title","Direction test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle(chat);
  check(senderLabels(chat,"OwnerExample")==2,"Both copies must show their real sender name instead of looking like unnamed opposite participants");
  check(direction(chat,a)==0&&direction(chat,b)==0,"Conflicting flags stay neutral until the user identifies their sender");check(direction(chat,different)==1,"Different actual sender retains incoming direction");
  try(ConversationResults unset=ConversationResults.build(repo,"flags","Synthetic",true,"",()->false,n->{})){check(unset.count==2&&unset.rawCount==3,"Unconfigured direction flags cannot split same-sender search groups");}
  View identify=description(chat,"Identify my messages");check(identify!=null&&identify.isShown(),"Conflict notice offers explicit local identity selection");runOnMainSync(()->identify.performClick());choice("OwnerExample");idle(chat);
  check("OwnerExample".equals(c.getSharedPreferences("display",0).getString("my_sender","")),"Explicit selected sender persists");check(direction(chat,a)==2&&direction(chat,b)==2,"Both copies from the selected sender align as outgoing");check(direction(chat,different)==1,"Same text from the other participant remains incoming");check(!description(chat,"Identify my messages").isShown(),"Resolved identity removes conflict notice");check(flagSum(db)==1,"Source own flags are unchanged");
  try(ConversationResults search=ConversationResults.build(repo,"flags","Synthetic",true,"OwnerExample",()->false,n->{})){check(search.count==2&&search.rawCount==3,"Search groups only the same sender's pair");}
  runOnMainSync(()->{try{Method m=chat.getClass().getDeclaredMethod("applySender",String.class);m.setAccessible(true);m.invoke(chat,"");}catch(Exception e){throw new RuntimeException(e);}});idle(chat);check(direction(chat,a)==0&&direction(chat,b)==0,"Clearing identity restores neutral conflicts, not misleading opposite sides");
  check(flagSum(db)==1&&repo.searchCount("flags","Synthetic")==3,"Every original record and sender flag survives presentation changes");runOnMainSync(chat::finish);repo.close();
 }
}
