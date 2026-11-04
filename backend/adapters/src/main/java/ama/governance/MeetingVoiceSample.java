package ama.governance;

import ama.contract.StorageContracts.StorageKey;
import ama.storage.FileBackendCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Reads only retained, verified PCM for the authorized immutable transcript segments. */
final class MeetingVoiceSample {
 private final JdbcTemplate j;private final FileBackendCatalog backends;
 MeetingVoiceSample(JdbcTemplate j,FileBackendCatalog backends){this.j=j;this.backends=backends;}
 byte[] collect(String meeting,String transcript,List<Map<String,Object>> segments,long target){
  var pcm=new ByteArrayOutputStream();var covered=new HashMap<String,Long>();
  String input=j.queryForObject("SELECT processing_input_id FROM transcript_revision WHERE id=? AND meeting_id=?",String.class,transcript,meeting);
  for(var segment:segments){
   if(pcm.size()/2>=target)break;
   if(!(segment.get("start_ms") instanceof Number startMs)||!(segment.get("end_ms") instanceof Number endMs))throw new IllegalArgumentException("本次会议没有可用原录音，未提交声纹审核。");
   String file=(String)segment.get("source_file_id");long start=Math.max(startMs.longValue()*16,covered.getOrDefault(file,0L)),end=endMs.longValue()*16;
   if(start>=end)continue;end=Math.min(end,start+target-pcm.size()/2);
   var clips=j.queryForList("SELECT * FROM speech_pcm_artifact WHERE meeting_id=? AND input_id=? AND file_id=? AND state='READY' AND start_sample<? AND start_sample+sample_count>? ORDER BY start_sample LIMIT 61",meeting,input,file,end,start);
   for(var clip:clips){
    long offset=((Number)clip.get("start_sample")).longValue(),count=((Number)clip.get("sample_count")).longValue();
    if(offset>start)throw unavailable();if(offset+count<=start)continue;
    int length=Math.toIntExact(((Number)clip.get("byte_count")).longValue());if(count>1920000||length!=44+count*2)throw unavailable();
    var key=new StorageKey((String)clip.get("backend_id"),(String)clip.get("namespace"),(String)clip.get("object_key"));byte[] bytes;
    try(var in=backends.require(key.backendId(),false).storage().read(key,0,length)){bytes=in.readNBytes(length+1);}catch(IOException bad){throw unavailable();}
    if(bytes.length!=length||!sha(bytes).equals(clip.get("wav_sha256")))throw unavailable();
    long stop=Math.min(end,offset+count);pcm.write(bytes,Math.toIntExact(44+(start-offset)*2),Math.toIntExact((stop-start)*2));start=stop;if(start==end)break;
   }
   if(start!=end)throw unavailable();covered.put(file,end);
  }
  byte[] data=pcm.toByteArray();var out=ByteBuffer.allocate(44+data.length).order(ByteOrder.LITTLE_ENDIAN);
  out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+data.length).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);return out.array();
 }
 static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
 private static IllegalArgumentException unavailable(){return new IllegalArgumentException("原录音样本暂不可用，未提交声纹审核。");}
}
