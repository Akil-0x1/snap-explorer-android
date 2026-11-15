package local.snapexplorer.mobile;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** SAF-only input adapter. Copies only a document/tree the user explicitly chose. */
public final class DocumentArchive {
    public interface Progress { void update(String detail); }
    public static void copy(Context context,Uri uri,boolean tree,File target,Progress progress)throws Exception{
        if(tree){
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){
                zip.setLevel(0);long[] total={0};int[] count={0};
                copyTree(context,uri,DocumentsContract.getTreeDocumentId(uri),"",zip,total,count,new HashSet<>(),0,progress);
                if(count[0]==0)throw new IOException("The selected folder is empty.");
            }
        }else{
            try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                if(in==null)throw new IOException("The selected ZIP is not available offline.");
                byte[] buffer=new byte[65536];long total=0;int n;long next=8*1024*1024;
                while((n=in.read(buffer))!=-1){interrupt();total+=n;if(total>ArchiveImporter.MAX_ZIP)throw new IOException("This ZIP exceeds the 16 GB import limit.");out.write(buffer,0,n);if(total>=next){progress.update("Copying selected ZIP · "+(total/(1024*1024))+" MB");next+=8*1024*1024;if(target.getParentFile().getUsableSpace()<32*1024*1024)throw new IOException("Not enough free space for this import.");}}
            }
        }
    }
    private static void copyTree(Context context,Uri tree,String id,String path,ZipOutputStream zip,long[] total,int[] count,Set<String> seen,int depth,Progress progress)throws Exception{
        interrupt();if(depth>24||!seen.add(id))throw new IOException("Folder nesting is not supported.");
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(Cursor cursor=context.getContentResolver().query(children,projection,null,null,null)){
            if(cursor==null)throw new IOException("Could not read the selected folder.");
            while(cursor.moveToNext()){
                interrupt();String child=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
                if(name==null||name.isEmpty()||name.contains("/")||name.contains("\\")||!ArchiveImporter.safeName(name)||name.equals("."))throw new IOException("An unsafe filename was found in the folder.");
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){copyTree(context,tree,child,path+name+"/",zip,total,count,seen,depth+1,progress);continue;}
                if(++count[0]>100000)throw new IOException("The selected folder has too many files.");
                zip.putNextEntry(new ZipEntry(path+name));
                try(InputStream input=context.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,child))){
                    if(input==null)throw new IOException("A selected file is unavailable offline.");byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1){interrupt();total[0]+=n;if(total[0]>ArchiveImporter.MAX_ZIP)throw new IOException("This folder exceeds the 16 GB import limit.");zip.write(b,0,n);}
                }
                zip.closeEntry();progress.update("Copying selected folder · "+count[0]+" files");
            }
        }
    }
    private static void interrupt()throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("Import paused. Reopen the app to retry.");}
    private DocumentArchive(){ }
}
