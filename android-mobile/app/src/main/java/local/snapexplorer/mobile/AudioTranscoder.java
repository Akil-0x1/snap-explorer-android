package local.snapexplorer.mobile;

import android.media.*;
import java.io.*;
import java.nio.ByteBuffer;

/** Native decoder-to-encoder audio conversion. MP3 output requires a genuine device MP3 encoder. */
public final class AudioTranscoder {
    public enum Format { AAC_M4A,MP3 }
    public interface Progress {void update(int percent);}
    private static final long WAIT_US=10000,STALL_NS=30000000000L;
    private static MediaCodec encoder(int sampleRate,int channels,Format format)throws IOException{
        if(sampleRate<1||channels<1||channels>8)throw new IOException("The source audio format has invalid channels or sample rate.");
        String mime=format==Format.MP3?MediaFormat.MIMETYPE_AUDIO_MPEG:MediaFormat.MIMETYPE_AUDIO_AAC;
        MediaFormat output=MediaFormat.createAudioFormat(mime,sampleRate,channels);
        output.setInteger(MediaFormat.KEY_BIT_RATE,format==Format.MP3?192000:Math.min(512000,Math.max(96000,64000*channels)));
        output.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
        if(format==Format.AAC_M4A)output.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        String name=new MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(output);
        if(name==null)throw new IOException(format==Format.MP3?"This phone has no MP3 encoder supporting the source sample rate and channels. Choose Convert to AAC / M4A instead.":"This phone cannot encode AAC at the source sample rate and channel count. Save the original audio instead.");
        MediaCodec codec=MediaCodec.createByCodecName(name);boolean ready=false;
        try{codec.configure(output,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();ready=true;return codec;}
        finally{if(!ready)codec.release();}
    }
    public static File convert(File source,File output,Format target,Progress progress)throws IOException{
        MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null,encoder=null;MediaMuxer muxer=null;OutputStream mp3=null;
        boolean decoderStarted=false,muxerStarted=false,success=false;
        try{
            extractor.setDataSource(source.getAbsolutePath());int track=-1;MediaFormat sourceFormat=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat candidate=extractor.getTrackFormat(i);String mime=candidate.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){track=i;sourceFormat=candidate;break;}}
            if(track<0)throw new IOException("This file contains no supported audio track.");
            // Check MP3 availability before any expensive decode. This is not an extension rename.
            if(target==Format.MP3){boolean available=false;for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())if(info.isEncoder())for(String mime:info.getSupportedTypes())if(MediaFormat.MIMETYPE_AUDIO_MPEG.equals(mime))available=true;if(!available)throw new IOException("This phone does not provide an MP3 encoder. Convert to AAC / M4A instead; it works offline without extra libraries.");}
            long duration=sourceFormat.containsKey(MediaFormat.KEY_DURATION)?sourceFormat.getLong(MediaFormat.KEY_DURATION):0;
            String mime=sourceFormat.getString(MediaFormat.KEY_MIME);sourceFormat.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            decoder=MediaCodec.createDecoderByType(mime);decoder.configure(sourceFormat,null,null,0);decoder.start();decoderStarted=true;extractor.selectTrack(track);
            if(target==Format.AAC_M4A)muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);else mp3=new BufferedOutputStream(new FileOutputStream(output));
            int sampleRate=0,channels=0,pcmEncoding=PcmAudio.PCM_16,destination=-1;
            ByteBuffer pending=null;long pendingTimestamp=0,pendingFrames=0;boolean pendingEnd=false,inputEnded=false,encoderInputEnded=false,outputEnded=false;
            MediaCodec.BufferInfo decoded=new MediaCodec.BufferInfo(),encoded=new MediaCodec.BufferInfo();long lastProgress=System.nanoTime(),firstOutput=-1;int lastPercent=-1;long outputBytes=0;
            while(!outputEnded){
                if(Thread.currentThread().isInterrupted())throw new IOException("Audio conversion interrupted.");boolean moved=false;
                if(encoder!=null)while(true){
                    int slot=encoder.dequeueOutputBuffer(encoded,0);
                    if(slot==MediaCodec.INFO_TRY_AGAIN_LATER)break;
                    if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(target==Format.AAC_M4A){if(muxerStarted)throw new IOException("Audio encoder changed format unexpectedly.");destination=muxer.addTrack(encoder.getOutputFormat());muxer.start();muxerStarted=true;}moved=true;continue;}
                    if(slot<0)continue;
                    ByteBuffer data=encoder.getOutputBuffer(slot);
                    if((encoded.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)encoded.size=0;
                    if(encoded.size>0){
                        if(data==null)throw new IOException("Audio encoder output is unavailable.");data.position(encoded.offset);data.limit(encoded.offset+encoded.size);
                        if(target==Format.AAC_M4A){if(!muxerStarted)throw new IOException("AAC container is not ready.");if(firstOutput<0)firstOutput=encoded.presentationTimeUs;encoded.presentationTimeUs=Math.max(0,encoded.presentationTimeUs-firstOutput);muxer.writeSampleData(destination,data,encoded);}
                        else{byte[] block=new byte[Math.min(65536,encoded.size)];while(data.hasRemaining()){int n=Math.min(block.length,data.remaining());data.get(block,0,n);mp3.write(block,0,n);}}
                        outputBytes+=encoded.size;
                    }
                    outputEnded=(encoded.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;encoder.releaseOutputBuffer(slot,false);moved=true;
                }
                if(outputEnded)break;
                if(pending!=null&&encoder!=null&&!encoderInputEnded){
                    int slot=encoder.dequeueInputBuffer(WAIT_US);
                    if(slot>=0){
                        ByteBuffer input=encoder.getInputBuffer(slot);if(input==null)throw new IOException("Audio encoder input is unavailable.");input.clear();int frameBytes=channels*2,chunk=Math.min(input.remaining(),pending.remaining());chunk-=chunk%frameBytes;
                        if(chunk==0&&pending.hasRemaining())throw new IOException("The device encoder buffer is smaller than an audio frame.");
                        int limit=pending.limit();pending.limit(pending.position()+chunk);input.put(pending);pending.limit(limit);
                        long timestamp=PcmAudio.timestamp(pendingTimestamp,pendingFrames,sampleRate);boolean end=pendingEnd&&!pending.hasRemaining();
                        encoder.queueInputBuffer(slot,0,chunk,timestamp,end?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);pendingFrames+=chunk/frameBytes;
                        if(end)encoderInputEnded=true;if(!pending.hasRemaining())pending=null;moved=true;
                        if(duration>0){int percent=(int)Math.max(0,Math.min(99,timestamp*100/duration));if(percent!=lastPercent){lastPercent=percent;progress.update(percent);}}
                    }
                }
                if(pending==null&&!encoderInputEnded){
                    int slot=decoder.dequeueOutputBuffer(decoded,WAIT_US);
                    if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat pcm=decoder.getOutputFormat();int rate=pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE),count=pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int encoding=pcm.containsKey(MediaFormat.KEY_PCM_ENCODING)?pcm.getInteger(MediaFormat.KEY_PCM_ENCODING):PcmAudio.PCM_16;
                        if(encoder!=null&&(sampleRate!=rate||channels!=count))throw new IOException("Midstream audio format changes are unsupported.");sampleRate=rate;channels=count;pcmEncoding=encoding;if(encoder==null)encoder=encoder(sampleRate,channels,target);moved=true;
                    }else if(slot>=0){
                        if(encoder==null){MediaFormat pcm=decoder.getOutputFormat();sampleRate=pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT);pcmEncoding=pcm.containsKey(MediaFormat.KEY_PCM_ENCODING)?pcm.getInteger(MediaFormat.KEY_PCM_ENCODING):PcmAudio.PCM_16;encoder=encoder(sampleRate,channels,target);}
                        pendingEnd=(decoded.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;pendingTimestamp=Math.max(0,decoded.presentationTimeUs);pendingFrames=0;
                        if((decoded.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0&&!pendingEnd){decoder.releaseOutputBuffer(slot,false);moved=true;continue;}
                        ByteBuffer data=decoder.getOutputBuffer(slot);
                        if(decoded.size>0){if(data==null||decoded.size>2*1024*1024)throw new IOException("The device decoder returned an oversized audio frame.");data.position(decoded.offset);data.limit(decoded.offset+decoded.size);pending=PcmAudio.to16Bit(data,pcmEncoding,channels);}
                        else if(pendingEnd)pending=ByteBuffer.allocate(0);
                        decoder.releaseOutputBuffer(slot,false);moved=true;
                    }
                }
                if(!inputEnded){
                    int slot=decoder.dequeueInputBuffer(0);
                    if(slot>=0){ByteBuffer buffer=decoder.getInputBuffer(slot);if(buffer==null)throw new IOException("Audio decoder input is unavailable.");buffer.clear();int size=extractor.readSampleData(buffer,0);
                        if(size<0){decoder.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true;}
                        else{if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw new IOException("Encrypted audio is not supported.");decoder.queueInputBuffer(slot,0,size,extractor.getSampleTime(),0);extractor.advance();}moved=true;}
                }
                if(moved)lastProgress=System.nanoTime();else if(System.nanoTime()-lastProgress>STALL_NS)throw new IOException("The device audio codec stopped responding.");
            }
            if(outputBytes==0)throw new IOException("The device encoder produced no audio.");
            if(muxerStarted){muxer.stop();muxerStarted=false;}if(mp3!=null)mp3.flush();success=true;progress.update(100);return output;
        }catch(IOException e){throw e;}
        catch(RuntimeException e){throw new IOException("This phone could not convert the source audio with its native codecs. Try a different output format or save the original.",e);}
        finally{
            extractor.release();if(decoder!=null){try{if(decoderStarted)decoder.stop();}catch(RuntimeException ignored){}decoder.release();}
            if(encoder!=null){try{encoder.stop();}catch(RuntimeException ignored){}encoder.release();}
            if(muxer!=null){try{if(muxerStarted)muxer.stop();}catch(RuntimeException ignored){}muxer.release();}
            if(mp3!=null)try{mp3.close();}catch(IOException ignored){}if(!success)output.delete();
        }
    }
    private AudioTranscoder(){}
}
