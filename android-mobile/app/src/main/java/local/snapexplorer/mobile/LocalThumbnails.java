package local.snapexplorer.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.util.concurrent.*;

/** Small bounded previews; never decode full media on the UI thread. */
final class LocalThumbnails {
    private final Context context;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(64),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    private volatile boolean closed;
    LocalThumbnails(Context context){this.context=context.getApplicationContext();}
    void bind(ImageView view,String id,String path,String kind){
        view.setTag(id);view.setImageDrawable(null);Bitmap hit=cache.get(id);if(hit!=null){view.setImageBitmap(hit);return;}
        if(!kind.equals("image")&&!kind.equals("video"))return;
        worker.execute(()->{
            if(closed)return;Bitmap result=null;
            try{
                File file=new File(path).getCanonicalFile();String root=context.getFilesDir().getCanonicalPath()+File.separator;
                if(!file.getPath().startsWith(root)||!file.isFile())return;
                if(kind.equals("image"))result=MediaProcessor.bitmap(file,480);
                else {MediaMetadataRetriever retriever=new MediaMetadataRetriever();try{retriever.setDataSource(file.getPath());if(Build.VERSION.SDK_INT>=27)result=retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,480,480);else{result=retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);if(result!=null&&Math.max(result.getWidth(),result.getHeight())>480){float ratio=480f/Math.max(result.getWidth(),result.getHeight());Bitmap scaled=Bitmap.createScaledBitmap(result,Math.max(1,Math.round(result.getWidth()*ratio)),Math.max(1,Math.round(result.getHeight()*ratio)),true);if(scaled!=result)result.recycle();result=scaled;}}}finally{retriever.release();}}
            }catch(Exception|OutOfMemoryError ignored){}
            final Bitmap bitmap=result;
            if(bitmap!=null)ui.post(()->{if(closed){bitmap.recycle();return;}cache.put(id,bitmap);if(id.equals(view.getTag()))view.setImageBitmap(bitmap);});
        });
    }
    void close(){closed=true;worker.shutdownNow();ui.removeCallbacksAndMessages(null);cache.evictAll();}
}
