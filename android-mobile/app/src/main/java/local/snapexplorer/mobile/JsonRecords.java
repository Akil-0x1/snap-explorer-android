package local.snapexplorer.mobile;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;
import java.io.*;
import org.json.JSONObject;

/** Visits one JSON record at a time, with bounded token/object memory. */
public final class JsonRecords {
 public interface Sink {void accept(String group,JSONObject record)throws Exception;}
 private static final int RECORD_LIMIT=8*1024*1024;
 public static void read(Reader source,Sink sink)throws Exception{
  Guard reader=new Guard(source);try(JsonReader json=new JsonReader(reader)){walk(json,reader,"",false,0,sink);if(json.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing data after JSON document.");}
 }
 private static void walk(JsonReader json,Guard input,String group,boolean item,int depth,Sink sink)throws Exception{
  if(depth>96)throw new IOException("JSON nesting exceeds the streaming safety limit.");if(Thread.currentThread().isInterrupted())throw new IOException("Import interrupted.");
  switch(json.peek()){
   case BEGIN_ARRAY:json.beginArray();while(json.hasNext())walk(json,input,group,true,depth+1,sink);json.endArray();return;
   case BEGIN_OBJECT:
    if(item){input.begin();Limited text=new Limited();JsonWriter out=new JsonWriter(text);copy(json,out,depth);out.flush();input.end();sink.accept(group,new JSONObject(text.toString()));return;}
    json.beginObject();JSONObject scalar=new JSONObject();while(json.hasNext()){String name=json.nextName();JsonToken token=json.peek();if(token==JsonToken.BEGIN_ARRAY||token==JsonToken.BEGIN_OBJECT)walk(json,input,name,false,depth+1,sink);else{input.begin();Object value;if(token==JsonToken.NULL){json.nextNull();value=JSONObject.NULL;}else if(token==JsonToken.BOOLEAN)value=json.nextBoolean();else value=json.nextString();input.end();scalar.put(name,value);}}json.endObject();if(scalar.length()>0)sink.accept(group,scalar);return;
   default:input.begin();json.skipValue();input.end();
  }
 }
 private static void copy(JsonReader in,JsonWriter out,int depth)throws Exception{
  if(depth>96)throw new IOException("JSON nesting exceeds the streaming safety limit.");
  switch(in.peek()){
   case BEGIN_OBJECT:in.beginObject();out.beginObject();while(in.hasNext()){out.name(in.nextName());copy(in,out,depth+1);}in.endObject();out.endObject();return;
   case BEGIN_ARRAY:in.beginArray();out.beginArray();while(in.hasNext())copy(in,out,depth+1);in.endArray();out.endArray();return;
   case STRING:out.value(in.nextString());return;case NUMBER:out.jsonValue(in.nextString());return;case BOOLEAN:out.value(in.nextBoolean());return;case NULL:in.nextNull();out.nullValue();return;default:throw new IOException("Invalid JSON value.");
  }
 }
 private static final class Limited extends StringWriter{
  private void room(int n)throws IOException{if(getBuffer().length()>RECORD_LIMIT-n)throw new IOException("A single JSON record is too large to process safely.");}
  @Override public void write(char[] b,int off,int len){try{room(len);}catch(IOException e){throw new IllegalStateException(e.getMessage(),e);}super.write(b,off,len);}
  @Override public void write(String s,int off,int len){try{room(len);}catch(IOException e){throw new IllegalStateException(e.getMessage(),e);}super.write(s,off,len);}
  @Override public void write(int c){try{room(1);}catch(IOException e){throw new IllegalStateException(e.getMessage(),e);}super.write(c);}
 }
 private static final class Guard extends FilterReader{
  private long left=-1;Guard(Reader r){super(r);}void begin(){left=RECORD_LIMIT;}void end(){left=-1;}
  @Override public int read(char[] b,int off,int len)throws IOException{if(left==0)throw new IOException("A single JSON value exceeds the memory safety limit.");int n=super.read(b,off,left<0?len:(int)Math.min(left,len));if(left>=0&&n>0)left-=n;return n;}
  @Override public int read()throws IOException{char[] c=new char[1];return read(c,0,1)<0?-1:c[0];}
 }
 private JsonRecords(){}
}
