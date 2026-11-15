package local.snapexplorer.mobile;

import android.graphics.ImageFormat;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Device-native HEVC conversion through flexible YUV images. No native library or network. */
public final class HevcTranscoder {
    public interface Progress { void update(int percent); }
    private static final long WAIT_US = 10000, STALL_NS = 30000000000L;
    public static File convert(File source, File output, Progress progress) throws IOException {
        return convert(source,output,null,progress);
    }
    public static File convert(File source, File output, File overlayFile, Progress progress) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null, encoder = null;
        MediaMuxer muxer = null;
        boolean encoderStarted=false,decoderStarted=false,muxerStarted=false,success=false;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int videoTrack=-1;MediaFormat inputFormat=null;List<Integer> audioSources=new ArrayList<>();
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat format=extractor.getTrackFormat(i);String mime=format.getString(MediaFormat.KEY_MIME);
                if(videoTrack<0&&mime!=null&&mime.startsWith("video/")){videoTrack=i;inputFormat=format;}
                if(mime!=null&&mime.startsWith("audio/")){
                    if(!MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime)&&!MediaFormat.MIMETYPE_AUDIO_AMR_NB.equals(mime)&&!MediaFormat.MIMETYPE_AUDIO_AMR_WB.equals(mime))
                        throw new IOException("The source audio codec ("+mime+") cannot be preserved in this MP4 conversion.");
                    audioSources.add(i);
                }
            }
            if(videoTrack<0)throw new IOException("No video track found in this file.");
            int sourceWidth=inputFormat.getInteger(MediaFormat.KEY_WIDTH),sourceHeight=inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
            if(sourceWidth<2||sourceHeight<2)throw new IOException("Invalid video dimensions.");
            double ratio=Math.min(1.0,Math.min(1920.0/sourceWidth,1080.0/sourceHeight));
            int width=Math.max(2,((int)(sourceWidth*ratio)/2)*2),height=Math.max(2,((int)(sourceHeight*ratio)/2)*2);
            YuvOverlay overlay=null;
            int standard=inputFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD)?inputFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD):MediaFormat.COLOR_STANDARD_BT709;
            boolean fullRange=inputFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)&&inputFormat.getInteger(MediaFormat.KEY_COLOR_RANGE)==MediaFormat.COLOR_RANGE_FULL;
            if(inputFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)){
                int transfer=inputFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                if(transfer==MediaFormat.COLOR_TRANSFER_ST2084||transfer==MediaFormat.COLOR_TRANSFER_HLG)throw new IOException("HDR video needs tone mapping that this local conversion tool does not provide. Save the original instead.");
            }
            if(overlayFile!=null){
                Bitmap raw=MediaProcessor.bitmap(overlayFile,2048),scaled=null;
                try{scaled=Bitmap.createScaledBitmap(raw,width,height,true);int[] pixels=new int[width*height];scaled.getPixels(pixels,0,width,0,0,width,height);overlay=new YuvOverlay(width,height,pixels,standard==MediaFormat.COLOR_STANDARD_BT709,fullRange);}
                finally{if(scaled!=null&&scaled!=raw)scaled.recycle();raw.recycle();}
            }
            int fps=30;try{if(inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))fps=Math.max(1,Math.min(60,inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE)));}catch(RuntimeException ignored){}
            MediaFormat encodeFormat=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC,width,height);
            encodeFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD,standard);encodeFormat.setInteger(MediaFormat.KEY_COLOR_RANGE,fullRange?MediaFormat.COLOR_RANGE_FULL:MediaFormat.COLOR_RANGE_LIMITED);
            encodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE,Math.max(500000,Math.min(12000000,width*height*4)));
            encodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE,fps);encodeFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
            String encoderName=new MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(encodeFormat);
            if(encoderName==null)throw new IOException("This phone has no HEVC encoder supporting flexible YUV images at these dimensions. Save the original video instead.");
            encoder=MediaCodec.createByCodecName(encoderName);encoder.configure(encodeFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);encoder.start();encoderStarted=true;
            String mime=inputFormat.getString(MediaFormat.KEY_MIME);
            decoder=MediaCodec.createDecoderByType(mime);
            inputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            decoder.configure(inputFormat,null,null,0);decoder.start();decoderStarted=true;extractor.selectTrack(videoTrack);
            muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if(inputFormat.containsKey("rotation-degrees"))muxer.setOrientationHint(inputFormat.getInteger("rotation-degrees"));
            List<Integer> audioDestinations=new ArrayList<>();for(int sourceTrack:audioSources)audioDestinations.add(muxer.addTrack(extractor.getTrackFormat(sourceTrack)));
            long duration=inputFormat.containsKey(MediaFormat.KEY_DURATION)?inputFormat.getLong(MediaFormat.KEY_DURATION):0;
            boolean inputEnded=false,decodeEnded=false,encodeEnded=false;
            int pending=-1,videoDestination=-1;MediaCodec.BufferInfo decodedInfo=new MediaCodec.BufferInfo(),encodedInfo=new MediaCodec.BufferInfo();
            long lastProgress=System.nanoTime();int lastPercent=-1;
            while(!encodeEnded){
                if(Thread.currentThread().isInterrupted())throw new IOException("Video conversion interrupted.");
                boolean moved=false;
                while(true){
                    int encoded=encoder.dequeueOutputBuffer(encodedInfo,0);
                    if(encoded==MediaCodec.INFO_TRY_AGAIN_LATER)break;
                    if(encoded==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(muxerStarted)throw new IOException("Encoder changed its output format unexpectedly.");videoDestination=muxer.addTrack(encoder.getOutputFormat());muxer.start();muxerStarted=true;moved=true;continue;}
                    if(encoded<0)continue;
                    ByteBuffer buffer=encoder.getOutputBuffer(encoded);
                    if((encodedInfo.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)encodedInfo.size=0;
                    if(encodedInfo.size>0){if(!muxerStarted||buffer==null)throw new IOException("Encoder output was unavailable.");buffer.position(encodedInfo.offset);buffer.limit(encodedInfo.offset+encodedInfo.size);muxer.writeSampleData(videoDestination,buffer,encodedInfo);}
                    encodeEnded=(encodedInfo.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;encoder.releaseOutputBuffer(encoded,false);moved=true;
                }
                if(encodeEnded)break;
                if(pending>=0){
                    int target=encoder.dequeueInputBuffer(WAIT_US);
                    if(target>=0){
                        boolean end=(decodedInfo.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        int size=0;
                        if(decodedInfo.size>0){
                            try(Image from=decoder.getOutputImage(pending);Image to=encoder.getInputImage(target)){
                                if(from==null||to==null)throw new IOException("This device codec does not expose YUV frames. Original file is unchanged.");
                                copyYuv(from,to,width,height);
                                if(overlay!=null){Image.Plane[] planes=to.getPlanes();for(int plane=0;plane<3;plane++)overlay.blend(plane,planes[plane].getBuffer(),planes[plane].getRowStride(),planes[plane].getPixelStride());}
                            }
                            size=width*height*3/2;
                        }
                        encoder.queueInputBuffer(target,0,size,decodedInfo.presentationTimeUs,end?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);
                        decoder.releaseOutputBuffer(pending,false);pending=-1;if(end)decodeEnded=true;moved=true;
                        if(duration>0){int percent=(int)Math.min(99,decodedInfo.presentationTimeUs*100/duration);if(percent!=lastPercent){lastPercent=percent;progress.update(percent);}}
                    }
                }
                if(!decodeEnded&&pending<0){int result=decoder.dequeueOutputBuffer(decodedInfo,WAIT_US);if(result>=0){pending=result;moved=true;}else if(result==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)moved=true;}
                if(!inputEnded){
                    int slot=decoder.dequeueInputBuffer(0);
                    if(slot>=0){ByteBuffer buffer=decoder.getInputBuffer(slot);if(buffer==null)throw new IOException("Decoder input buffer unavailable.");buffer.clear();int size=extractor.readSampleData(buffer,0);
                        if(size<0){decoder.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true;}
                        else{decoder.queueInputBuffer(slot,0,size,extractor.getSampleTime(),0);extractor.advance();}moved=true;
                    }
                }
                if(moved)lastProgress=System.nanoTime();else if(System.nanoTime()-lastProgress>STALL_NS)throw new IOException("The device codec stopped responding. Try saving the original video.");
            }
            if(!muxerStarted)throw new IOException("The video encoder produced no output.");
            // MP4 requires timestamps to increase per track, not across unrelated tracks.
            for(int a=0;a<audioSources.size();a++){
                MediaExtractor audio=new MediaExtractor();
                try{audio.setDataSource(source.getAbsolutePath());audio.selectTrack(audioSources.get(a));ByteBuffer buffer=ByteBuffer.allocate(2*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                    while(true){if(Thread.currentThread().isInterrupted())throw new IOException("Video conversion interrupted.");buffer.clear();int size=audio.readSampleData(buffer,0);if(size<0)break;info.set(0,size,audio.getSampleTime(),audio.getSampleFlags());muxer.writeSampleData(audioDestinations.get(a),buffer,info);audio.advance();}
                }finally{audio.release();}
            }
            muxer.stop();muxerStarted=false;success=true;progress.update(100);return output;
        }catch(IOException e){throw e;}
        catch(RuntimeException e){throw new IOException("This phone’s codecs could not complete HEVC conversion. The original file is unchanged.",e);}
        finally{
            extractor.release();
            if(decoder!=null){try{if(decoderStarted)decoder.stop();}catch(RuntimeException ignored){}decoder.release();}
            if(encoder!=null){try{if(encoderStarted)encoder.stop();}catch(RuntimeException ignored){}encoder.release();}
            if(muxer!=null){try{if(muxerStarted)muxer.stop();}catch(RuntimeException ignored){}muxer.release();}
            if(!success)output.delete();
        }
    }
    static void copyYuv(Image source,Image target,int width,int height)throws IOException{
        if(source.getFormat()!=ImageFormat.YUV_420_888||target.getFormat()!=ImageFormat.YUV_420_888)throw new IOException("Device codec returned an unsupported image format.");
        Image.Plane[] from=source.getPlanes(),to=target.getPlanes();if(from.length!=3||to.length!=3)throw new IOException("Unsupported YUV plane layout.");
        Rect crop=source.getCropRect();
        for(int plane=0;plane<3;plane++){
            int shift=plane==0?0:1,targetWidth=width>>shift,targetHeight=height>>shift;
            int sourceWidth=crop.width()>>shift,sourceHeight=crop.height()>>shift,left=crop.left>>shift,top=crop.top>>shift;
            if(sourceWidth<1||sourceHeight<1)throw new IOException("Invalid decoded frame crop.");
            ByteBuffer input=from[plane].getBuffer(),output=to[plane].getBuffer();
            YuvPlanes.copy(input,from[plane].getRowStride(),from[plane].getPixelStride(),left,top,
                    sourceWidth,sourceHeight,output,to[plane].getRowStride(),to[plane].getPixelStride(),targetWidth,targetHeight);
        }
    }
    private HevcTranscoder(){}
}
