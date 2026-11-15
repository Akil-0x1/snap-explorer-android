package local.snapexplorer.mobile;

import android.graphics.*;
import android.media.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Platform-only operations on explicit local files. Never mutates imported originals. */
public final class MediaProcessor {
    public static Bitmap bitmap(File file, int bound) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("This device cannot decode that image format.");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = MediaNames.sampleSize(bounds.outWidth, bounds.outHeight, bound);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (image == null) throw new IOException("This image could not be decoded.");
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix m = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.setScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: m.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.setScale(1,-1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: m.setRotate(90); m.postScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: m.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: m.setRotate(270); m.postScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: m.setRotate(270); break;
            }
            if (!m.isIdentity()) {
                Bitmap corrected = Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);
                if (corrected != image) image.recycle(); image = corrected;
            }
        } catch (IOException ignored) { }
        return image;
    }
    public static File overlay(File base, File overlay, File output, Double latitude, Double longitude) throws IOException {
        Bitmap background = bitmap(base, 4096), foreground = null;
        try {
            foreground = bitmap(overlay, 4096);
            Bitmap combined = Bitmap.createBitmap(background.getWidth(), background.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(combined); canvas.drawBitmap(background,0,0,null);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(foreground,null,new Rect(0,0,combined.getWidth(),combined.getHeight()),paint);
                try (OutputStream out = new FileOutputStream(output)) {
                    if (!combined.compress(Bitmap.CompressFormat.JPEG, 94, out)) throw new IOException("Could not write composited image.");
                }
            } finally { combined.recycle(); }
            copyExif(base,output,latitude,longitude);
            return output;
        } catch (IOException | RuntimeException e) { output.delete(); throw e; }
        finally { background.recycle(); if (foreground != null) foreground.recycle(); }
    }
    public static void copyExif(File original, File output, Double latitude, Double longitude) throws IOException {
        ExifInterface target = new ExifInterface(output.getAbsolutePath());
        try {
            ExifInterface source = new ExifInterface(original.getAbsolutePath());
            for (String tag : new String[]{ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF}) {
                String value = source.getAttribute(tag); if (value != null) target.setAttribute(tag,value);
            }
        } catch (IOException ignored) { }
        if (latitude != null && longitude != null && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180) {
            target.setAttribute(ExifInterface.TAG_GPS_LATITUDE, coordinate(latitude));
            target.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitude < 0 ? "S" : "N");
            target.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, coordinate(longitude));
            target.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitude < 0 ? "W" : "E");
        }
        target.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(ExifInterface.ORIENTATION_NORMAL));
        target.saveAttributes();
    }
    private static String coordinate(double value) {
        double absolute = Math.abs(value); int degrees = (int) absolute;
        double minutesRaw = (absolute - degrees) * 60; int minutes = (int) minutesRaw;
        long seconds = Math.round((minutesRaw - minutes) * 60 * 10000);
        return degrees + "/1," + minutes + "/1," + seconds + "/10000";
    }
    /** Makes an oriented JPEG copy, preserving source EXIF and adding imported GPS. */
    public static File jpegWithMetadata(File source, File output, Double latitude, Double longitude, long epoch) throws IOException {
        Bitmap image = bitmap(source,4096);
        try (OutputStream out = new FileOutputStream(output)) {
            if (!image.compress(Bitmap.CompressFormat.JPEG,94,out)) throw new IOException("Could not write image.");
        } finally { image.recycle(); }
        try {
            copyExif(source,output,latitude,longitude);
            if (epoch > 0) {
                ExifInterface exif = new ExifInterface(output.getAbsolutePath());
                String date = java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss",java.util.Locale.US)
                        .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(epoch));
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,date);
                exif.setAttribute(ExifInterface.TAG_DATETIME,date);exif.saveAttributes();
                output.setLastModified(epoch);
            }
            return output;
        }
        catch(IOException e) {output.delete();throw e;}
    }
    /** Audio-only MP4/M4A is preserved by remuxing; no transcoding or fabricated MP3. */
    public static File extractAudio(File input, File output) throws IOException {
        MediaExtractor extractor = new MediaExtractor(); MediaMuxer muxer = null; boolean started = false;
        try {
            extractor.setDataSource(input.getAbsolutePath()); int track = -1; MediaFormat format = null;
            for (int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat candidate=extractor.getTrackFormat(i); String mime=candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {track=i;format=candidate;break;}
            }
            if (track < 0) throw new IOException("This file contains no audio track.");
            String mime=format.getString(MediaFormat.KEY_MIME);
            if (!MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime)) throw new IOException("Audio extraction currently preserves AAC tracks as M4A. This file uses " + mime + ".");
            extractor.selectTrack(track); muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int target=muxer.addTrack(format);muxer.start();started=true;
            ByteBuffer buffer=ByteBuffer.allocate(1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(true){
                if(Thread.currentThread().isInterrupted())throw new IOException("Audio extraction interrupted.");
                int size=extractor.readSampleData(buffer,0);if(size<0)break;
                info.set(0,size,extractor.getSampleTime(),extractor.getSampleFlags());muxer.writeSampleData(target,buffer,info);extractor.advance();
            }
            return output;
        } catch(IOException | RuntimeException e){ output.delete();throw e; }
        finally {extractor.release();if(muxer!=null){try{if(started)muxer.stop();}catch(RuntimeException ignored){}muxer.release();}}
    }
    public static List<String> codecCapabilities() {
        List<String> result=new ArrayList<>();
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) if (type.equals("video/hevc") || type.equals("video/avc") || type.equals("audio/mp4a-latm") || type.equals("audio/mpeg"))
                result.add(type + " · " + info.getName());
        }
        return result;
    }
    public static String details(File file, String mime) {
        StringBuilder text = new StringBuilder();text.append("File: ").append(file.getName()).append("\nSize: ").append(file.length()).append(" bytes\nType: ").append(mime);
        try {
            if (mime.startsWith("image/")) {
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
                text.append("\nDimensions: ").append(bounds.outWidth).append(" × ").append(bounds.outHeight);
                try {ExifInterface exif=new ExifInterface(file.getAbsolutePath());String taken=exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);if(taken!=null)text.append("\nCamera date: ").append(taken);} catch(IOException ignored){}
            } else {
                MediaMetadataRetriever retriever=new MediaMetadataRetriever();
                try {retriever.setDataSource(file.getAbsolutePath());
                    String duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if(duration!=null)text.append("\nDuration: ").append(MediaNames.duration(Long.parseLong(duration)));
                    String width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    if(width!=null&&height!=null)text.append("\nDimensions: ").append(width).append(" × ").append(height);
                } finally {try{retriever.release();}catch(IOException ignored){}}
            }
        } catch(RuntimeException e){text.append("\nSome metadata is unavailable on this device.");}
        return text.toString();
    }
    private MediaProcessor() { }
}
