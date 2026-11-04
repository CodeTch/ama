package ama.recording;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Function;

/** A playable view over real, immutable PCM proofs. No synthetic registry file ID. */
public final class RecordingPartWave {
 private RecordingPartWave(){}
 public record Part(String partId,int partNo,long startSample,long sampleCount,boolean available,List<RecordingService.Proof> chunks){public Part{chunks=List.copyOf(chunks);}}
 public record Download(InputStream body,long size,long offset,long length) implements AutoCloseable{public void close()throws IOException{body.close();}}
 public static final class InvalidRange extends IllegalArgumentException{public final long size;public InvalidRange(long size){super("Invalid recording part range");this.size=size;}}
 public static List<Part> parts(RecordingService.Snapshot snapshot){
  var result=new ArrayList<Part>();long start=0;int ordinal=0;
  for(String id:snapshot.parts()){
   var chunks=snapshot.chunks().stream().filter(p->p.partId().equals(id)).sorted(Comparator.comparingLong(RecordingService.Proof::seq)).toList();
   long samples=0,sequence=0;for(var p:chunks){if(p.seq()!=++sequence||p.sampleStart()!=samples||p.sampleCount()<1||p.sampleCount()>32000)throw new RecordingService.Gap();samples=Math.addExact(samples,p.sampleCount());}
   boolean sealed=snapshot.finished()||snapshot.paused()||!id.equals(snapshot.session().partId());
   result.add(new Part(id,++ordinal,start,samples,sealed&&samples>0,chunks));start=Math.addExact(start,samples);
  }
  if(start>ama.contract.RecordingContracts.MAX_SESSION_SAMPLES||snapshot.chunks().size()>4096)throw new IllegalArgumentException("Recording part budget exceeded");
  return List.copyOf(result);
 }
 public static Download open(Part part,String range,Function<RecordingService.Proof,InputStream> read){
  if(!part.available())throw new IllegalStateException("Recording part is not sealed");
  long size=44+part.sampleCount()*2;var selection=range(range,size);long offset=selection[0],length=selection[1];
  byte[] header=header(part.sampleCount());
  var stream=new InputStream(){
   long position=offset,remaining=length;byte[] chunk=null;long chunkStart=-1;boolean closed;
   @Override public int read()throws IOException{byte[] one=new byte[1];return read(one,0,1)<0?-1:Byte.toUnsignedInt(one[0]);}
   @Override public int read(byte[] bytes,int off,int len)throws IOException{
    Objects.checkFromIndexSize(off,len,bytes.length);if(closed)throw new IOException("Part stream closed");if(len==0)return 0;if(remaining==0)return -1;
    int count;if(position<44){count=(int)Math.min(Math.min(44-position,remaining),len);System.arraycopy(header,(int)position,bytes,off,count);}
    else{
     long pcmOffset=position-44;
     if(chunk==null||pcmOffset<chunkStart||pcmOffset>=chunkStart+chunk.length){
      var proof=part.chunks().stream().filter(p->pcmOffset>=p.sampleStart()*2&&pcmOffset<(p.sampleStart()+p.sampleCount())*2).findFirst().orElseThrow(()->new IOException("Part proof gap"));
      try(var source=read.apply(proof)){chunk=source.readNBytes(Math.toIntExact(proof.sampleCount()*2)+1);if(chunk.length!=proof.sampleCount()*2||!hash(chunk).equals(proof.sha256()))throw new IOException("Recording part PCM integrity mismatch");}
      chunkStart=proof.sampleStart()*2;
     }
     int inside=Math.toIntExact(pcmOffset-chunkStart);count=(int)Math.min(Math.min(chunk.length-inside,remaining),len);System.arraycopy(chunk,inside,bytes,off,count);
    }
    position+=count;remaining-=count;return count;
   }
   @Override public void close(){closed=true;chunk=null;}
  };
  return new Download(stream,size,offset,length);
 }
 private static long[] range(String value,long size){
  if(value==null)return new long[]{0,size};
  try{if(!value.matches("bytes=[0-9]*-[0-9]*"))throw new IllegalArgumentException();var ends=value.substring(6).split("-",-1);long first,last;
   if(ends[0].isEmpty()){long suffix=Long.parseLong(ends[1]);if(suffix<1)throw new IllegalArgumentException();first=Math.max(0,size-suffix);last=size-1;}
   else{first=Long.parseLong(ends[0]);last=ends[1].isEmpty()?size-1:Math.min(Long.parseLong(ends[1]),size-1);if(first>=size||last<first)throw new IllegalArgumentException();}
   return new long[]{first,last-first+1};
  }catch(IllegalArgumentException error){throw new InvalidRange(size);}
 }
 private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static byte[] header(long samples){int bytes=Math.toIntExact(samples*2);var b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+bytes).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));b.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);return b.array();}
}
