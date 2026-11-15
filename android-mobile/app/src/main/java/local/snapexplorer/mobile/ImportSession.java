package local.snapexplorer.mobile;
import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Durable source-copy checkpoint; successful DB imports remain atomic. */
public final class ImportSession {
    private final Context context;private final File directory;
    public ImportSession(Context context){this.context=context;directory=new File(context.getFilesDir(),"pending-import");}
    public boolean exists(){File base=new File(directory,"session.json");return base.isFile()||new File(directory,"session.json.bak").isFile();}
    public void start(List<Uri> sources,List<String> labels,boolean tree,boolean replace)throws Exception{
        discard();if(!directory.mkdirs())throw new IOException("Could not prepare import storage.");
        JSONObject session=new JSONObject();session.put("replace",replace);session.put("tree",tree);JSONArray entries=new JSONArray();
        for(int i=0;i<sources.size();i++){JSONObject entry=new JSONObject();entry.put("uri",sources.get(i).toString());entry.put("label",labels.get(i));entry.put("ready",false);entries.put(entry);}session.put("entries",entries);save(session);
    }
    public boolean replaces()throws Exception{return load().optBoolean("replace");}
    public List<ChatRepository.Input> prepare(DocumentArchive.Progress progress)throws Exception{
        JSONObject session=load();JSONArray entries=session.getJSONArray("entries");List<ChatRepository.Input> result=new ArrayList<>();
        for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.getJSONObject(i);File zip=new File(directory,"source-"+i+".zip");
            if(!entry.optBoolean("ready")||!zip.isFile()){
                zip.delete();DocumentArchive.copy(context,Uri.parse(entry.getString("uri")),session.optBoolean("tree"),zip,progress);
                entry.put("ready",true);save(session);release(entry.optString("uri"));
            }
            result.add(new ChatRepository.Input(zip,entry.getString("label"),false));
        }
        return result;
    }
    private JSONObject load()throws Exception{try(InputStream in=new AtomicFile(new File(directory,"session.json")).openRead()){return new JSONObject(new String(ArchiveImporter.readBounded(in,128*1024),StandardCharsets.UTF_8));}}
    private void save(JSONObject session)throws Exception{
        AtomicFile file=new AtomicFile(new File(directory,"session.json"));FileOutputStream out=file.startWrite();
        try{out.write(session.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}
    }
    private void release(String uri){try{if(!uri.isEmpty())context.getContentResolver().releasePersistableUriPermission(Uri.parse(uri),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    public void discard(){if(exists())try{JSONArray entries=load().getJSONArray("entries");for(int i=0;i<entries.length();i++)release(entries.getJSONObject(i).optString("uri"));}catch(Exception ignored){}File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();}
}
