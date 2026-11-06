package ama.api;

import ama.contract.IdentityContracts.Actor;
import ama.contract.TranscriptContracts.Failure;
import ama.storage.FileRegistry;
import ama.storage.RevocableInputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Verify sealed upload parts before emitting a native media range, including a range starting inside a part. */
@Component
final class VerifiedTranscriptPlayback {
 private static final int MAX_PART=8*1024*1024;
 private record Part(int ordinal,int length,String sha){}
 private final JdbcTemplate jdbc;
 VerifiedTranscriptPlayback(DataSource source){jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(3);}
 private static Failure unsupported(){return new Failure(422,"PLAYBACK_MANIFEST_UNAVAILABLE");}
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private List<Part> parts(FileRegistry.FileRecord file){
  var parts=jdbc.query("""
   SELECT p.part_no,p.size_bytes,p.sha256 FROM upload_session u JOIN upload_part p ON p.upload_id=u.id
   WHERE u.completed_file_id=? AND u.meeting_id=? AND u.state='COMPLETED' AND u.expected_size=?
   AND (u.expected_sha256 IS NULL OR u.expected_sha256=?) ORDER BY p.part_no LIMIT 4097
   """,(r,n)->new Part(r.getInt(1),r.getInt(2),r.getString(3)),file.fileId(),file.meetingId(),file.size(),file.sha256());
  if(parts.isEmpty()||parts.size()>4096)throw unsupported();
  long size=0;int ordinal=1;
  for(var part:parts){
   if(part.ordinal!=ordinal++||part.length<1||part.length>MAX_PART||!part.sha.matches("[0-9a-f]{64}"))throw unsupported();
   size+=part.length;
  }
  if(size!=file.size()||parts.getFirst().length<44)throw unsupported();
  return parts;
 }
 void serve(FileRegistry files,String meeting,FileRegistry.ReadRequest selector,Supplier<Actor> identity,String range,String sha,boolean head,HttpServletResponse response)throws IOException{
  if(!sha.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Expected file digest");
  BiFunction<Actor,String,FileRegistry.Download> open=(actor,interval)->files.read(actor,meeting,selector,interval);
  try(var requested=open.apply(identity.get(),range)){
   var file=requested.file();long start=requested.offset(),end=start+requested.length();
   if(!sha.equals(file.sha256()))throw new Failure(409,"SOURCE_VERSION_CHANGED");
   if(file.size()<44||file.size()>230400044)throw unsupported();
   var parts=parts(file);
   if(head&&start==0&&end==44){
    // Format negotiation sends no audio. The following GET verifies every released byte against a sealed part.
    try(var probe=AuthorizedDownloads.guard(requested,identity,open)){
     var header=probe.body().readNBytes(44);
     if(header.length!=44||!pcmHeader(header,file.size()))throw unsupported();
     headers(response,file,start,end,range);return;
    }
   }
   // The native player's first request normally spans the whole file. Read and authorize that stream once.
   if(start==0&&end==file.size()){
    try(var stream=AuthorizedDownloads.guard(requested,identity,open)){
     long offset=0;
     for(var part:parts){
      writePart(stream,part,offset,start,end,range,response);offset+=part.length;
     }
    }
    return;
   }
   long offset=0;
   for(var part:parts){
    long next=offset+part.length;
    if(offset==0||next>start&&offset<end){
     String exact="bytes="+offset+"-"+(next-1);
     try(var stream=AuthorizedDownloads.guard(open.apply(identity.get(),exact),identity,open)){
      if(!file.equals(stream.file())||stream.offset()!=offset||stream.length()!=part.length)throw new Failure(409,"SOURCE_VERSION_CHANGED");
      writePart(stream,part,offset,start,end,range,response);
     }
    }
    offset=next;if(offset>=end)break;
   }
  }
 }
 private static void writePart(FileRegistry.Download stream,Part part,long offset,long start,long end,String range,HttpServletResponse response)throws IOException{
  byte[] chunk=new byte[part.length];
  if(stream.body().readNBytes(chunk,0,chunk.length)!=chunk.length||!HexFormat.of().formatHex(digest().digest(chunk)).equals(part.sha))throw new IOException("Source part integrity mismatch");
  if(offset==0&&!pcmHeader(chunk,stream.file().size()))throw unsupported();
  if(offset+part.length<=start)return;
  if(!response.isCommitted())headers(response,stream.file(),start,end,range);
  int from=(int)Math.max(0,start-offset),to=(int)Math.min(part.length,end-offset);
  ((RevocableInputStream)stream.body()).writeVerified(response.getOutputStream(),chunk,from,to-from);
  response.flushBuffer();
 }
 private static void headers(HttpServletResponse response,FileRegistry.FileRecord file,long start,long end,String range){
  response.setStatus(range==null?200:206);response.setContentType("audio/wav");response.setHeader("Cache-Control","private, no-store");
  response.setHeader("Content-Disposition",FileController.contentDisposition(file.displayName()).replaceFirst("^attachment","inline"));
  response.setHeader("Accept-Ranges","bytes");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("ETag","\""+file.sha256()+"\"");
  response.setContentLengthLong(end-start);if(range!=null)response.setHeader("Content-Range","bytes "+start+"-"+(end-1)+"/"+file.size());
 }
 private static boolean pcmHeader(byte[] bytes,long size){
  var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  return b.getInt(0)==0x46464952&&Integer.toUnsignedLong(b.getInt(4))==size-8&&b.getInt(8)==0x45564157&&b.getInt(12)==0x20746d66
    &&b.getInt(16)==16&&b.getShort(20)==1&&b.getShort(22)==1&&b.getInt(24)==16000&&b.getInt(28)==32000&&b.getShort(32)==2
    &&b.getShort(34)==16&&b.getInt(36)==0x61746164&&Integer.toUnsignedLong(b.getInt(40))==size-44&&(size-44)%2==0;
 }
}
