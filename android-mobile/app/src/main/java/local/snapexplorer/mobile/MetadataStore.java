package local.snapexplorer.mobile;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
public final class MetadataStore {
 public static File filesRoot(SQLiteDatabase db){return new File(new File(db.getPath()).getParentFile().getParentFile(),"files");}
 public static String read(SQLiteDatabase db,String stored)throws Exception{return MetadataFiles.read(filesRoot(db),stored);}
 public static File file(SQLiteDatabase db,String stored)throws Exception{return MetadataFiles.checkedFile(filesRoot(db),stored);}
 public static Reader reader(SQLiteDatabase db,String stored)throws Exception{return MetadataFiles.reader(filesRoot(db),stored);}
 public static String preview(SQLiteDatabase db,String stored,int limit)throws Exception{try(Reader in=reader(db,stored)){char[] buffer=new char[limit+1];int count=0,n;while(count<buffer.length&&(n=in.read(buffer,count,buffer.length-count))!=-1)count+=n;return new String(buffer,0,Math.min(count,limit))+(count>limit?"\n[Preview limited. Export the complete raw dataset in a portable ZIP.]":"");}}
 private MetadataStore(){}
}
