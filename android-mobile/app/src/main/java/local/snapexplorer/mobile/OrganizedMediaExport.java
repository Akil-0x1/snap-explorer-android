package local.snapexplorer.mobile;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Streaming media-only archive: clean year/month paths, source metadata and SHA256 manifest. */
public final class OrganizedMediaExport {
    public interface Progress {void update(int complete);}
    public static File write(Context context,SQLiteDatabase db,File output,String source,String kind,String year,String chat,Progress progress)throws Exception{
        MediaSelection selection=new MediaSelection(source,kind,year,chat);String where=selection.where;List<String> args=Arrays.asList(selection.args);
        File manifest=File.createTempFile("snap-media-manifest-",".json",context.getCacheDir());boolean success=false;
        String filesRoot=context.getFilesDir().getCanonicalPath()+File.separator;
        ZoneId zone;try{zone=ZoneId.of(context.getSharedPreferences("display",0).getString("timezone","UTC"));}catch(Exception ignored){zone=ZoneOffset.UTC;}
        DateTimeFormatter folders=DateTimeFormatter.ofPattern("uuuu/MM",Locale.US).withZone(zone),nameDate=DateTimeFormatter.ofPattern("uuuu-MM-dd_HHmmss",Locale.US).withZone(zone);
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)));Writer json=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(manifest),StandardCharsets.UTF_8));Cursor c=db.rawQuery("SELECT id,stored_path,name,source,epoch,sha256,original_path,latitude,longitude FROM media WHERE "+where+" ORDER BY epoch,id",args.toArray(new String[0]))){
            zip.setLevel(1);json.write("{\"format\":\"Snap Explorer organized media v1\",\"timezone\":"+JSONObject.quote(zone.getId())+",\"files\":[");int count=0;byte[] buffer=new byte[65536];
            while(c.moveToNext()){
                if(Thread.currentThread().isInterrupted())throw new IOException("Media export interrupted.");
                File input=new File(c.getString(1)).getCanonicalFile();if(!input.getPath().startsWith(filesRoot)||!input.isFile())throw new IOException("An imported media file is missing. Run integrity verification first.");
                if(output.getParentFile().getUsableSpace()<input.length()+16L*1024*1024)throw new IOException("Not enough free space to prepare this media archive.");
                String id=c.getString(0),hash=c.getString(5),name=MediaNames.safeFileName(c.getString(2));long epoch=c.getLong(4);
                int dot=name.lastIndexOf('.');String ext=dot>0&&name.length()-dot<12?name.substring(dot).toLowerCase(Locale.ROOT):"";
                String prefix=epoch>0?folders.format(Instant.ofEpochMilli(epoch)):"undated";
                String date=epoch>0?nameDate.format(Instant.ofEpochMilli(epoch)):"undated";
                String target=prefix+"/"+date+"-"+MediaNames.safeFileName(id)+ext;
                ZipEntry entry=new ZipEntry(target);if(epoch>0)entry.setTime(epoch);zip.putNextEntry(entry);MessageDigest digest=MessageDigest.getInstance("SHA-256");long written=0;
                try(InputStream in=new BufferedInputStream(new FileInputStream(input))){int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Media export interrupted.");zip.write(buffer,0,n);digest.update(buffer,0,n);written+=n;}}
                zip.closeEntry();String actual=hex(digest.digest());if(hash!=null&&!hash.isEmpty()&&!actual.equalsIgnoreCase(hash))throw new IOException("A media checksum did not match. The imported archive is unchanged.");
                if(count>0)json.write(',');json.write("{\"path\":"+JSONObject.quote(target)+",\"sha256\":"+JSONObject.quote(actual)+",\"bytes\":"+written+",\"original_path\":"+JSONObject.quote(c.getString(6))+",\"source\":"+JSONObject.quote(c.getString(3)));
                if(!c.isNull(7)&&!c.isNull(8))json.write(",\"latitude\":"+c.getDouble(7)+",\"longitude\":"+c.getDouble(8));json.write('}');count++;progress.update(count);
            }
            if(count==0)throw new IOException("No matching media files to export.");json.write("]}");json.flush();zip.putNextEntry(new ZipEntry("manifest.json"));try(InputStream in=new FileInputStream(manifest)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}zip.closeEntry();success=true;
        }finally{manifest.delete();if(!success)output.delete();}
        return output;
    }
    private static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format(Locale.US,"%02x",b&255));return text.toString();}
    private OrganizedMediaExport(){}
}
