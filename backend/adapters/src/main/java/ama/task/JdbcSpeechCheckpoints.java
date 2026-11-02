package ama.task;

import ama.contract.SpeechContracts.WavClip;
import ama.contract.TaskContracts.TaskLease;
import ama.media.FfmpegNormalizer.SourceProof;
import ama.transcript.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Private technical writer. RAW publication remains exclusively the import completion. */
final class JdbcSpeechCheckpoints implements SpeechPipeline.Store {
 private final JdbcTaskQueue q;private final JdbcTranscriptAuthority authority;private final TaskLease lease;
 private final SourceProof source;private final WavClip clip;private final String engine,key;private final BooleanSupplier cancelled;
 JdbcSpeechCheckpoints(JdbcTaskQueue q,JdbcTranscriptAuthority authority,TaskLease lease,SourceProof source,WavClip clip,String engine,BooleanSupplier cancelled){
  TranscriptImportRuntime.requireCombined(q,authority);if(!lease.spec().type().equals("TRANSCRIPT_IMPORT_V1")||!source.fileId().equals(clip.fileId())||source.sampleCount()<1||source.sampleCount()>115200000||clip.sampleCount()>1920000||clip.endSampleExclusive()>source.sampleCount())throw new IllegalArgumentException("Bounded original speech clip required");
  ControlledSpeechProfile.hash(source.sourceSha256());ControlledSpeechProfile.hash(source.pcmSha256());ControlledSpeechProfile.hash(engine);
  this.q=q;this.authority=authority;this.lease=lease;this.source=source;this.clip=clip;this.engine=engine;this.cancelled=Objects.requireNonNull(cancelled);
  key=ControlledSpeechProfile.key("ama.speech-clip.v1",lease.spec().input().id(),lease.spec().inputFingerprint(),source.fileId(),source.sourceSha256(),source.pcmSha256(),engine,Long.toString(clip.startSample()),Long.toString(clip.sampleCount()));
 }
 String key(){return key;}
 void current(){
  if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Speech checkpoint cancelled");
  var valid=q.validatedPreparationLease(lease).orElseThrow(()->new CancellationException("Speech lease changed"));authority.requireImportPreparation(List.of(lease.spec()),valid.domain().prepared());
  Long member=q.jdbc.queryForObject("SELECT COUNT(*) FROM transcription_input i JOIN input_batch_file f ON f.batch_id=i.batch_id WHERE i.id=? AND i.meeting_id=? AND i.input_fingerprint=? AND i.parser_version IN (?,?) AND f.file_id=? AND f.sha256=?",Long.class,lease.spec().input().id(),lease.spec().subject().id(),lease.spec().inputFingerprint(),ControlledSpeechProfile.PARSER,ama.transcript.RecordingTranscriptProfile.PARSER,source.fileId(),source.sourceSha256());
  if(member==null||member!=1)throw unavailable();
 }
 @Override public Optional<SpeechPipeline.Snapshot> load(SpeechPipeline.Stage stage){Objects.requireNonNull(stage);return q.preparation(()->{current();var row=read(stage);return row==null?Optional.empty():Optional.of(SpeechCheckpointCodec.decode(stage,payload(row)));});}
 @Override public void save(SpeechPipeline.Stage stage,SpeechPipeline.Snapshot value){
  byte[] bytes=SpeechCheckpointCodec.encode(stage,value);String hash=ControlledSpeechProfile.digest(bytes);
  q.preparation(()->{
   current();var existing=read(stage);if(existing!=null){if(!Arrays.equals(payload(existing),bytes))throw new IllegalStateException("Immutable speech checkpoint conflict");return null;}
   var totals=q.jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(n),0) FROM (SELECT OCTET_LENGTH(payload) n FROM speech_checkpoint WHERE input_id=? LIMIT 4801) s",(r,n)->new long[]{r.getLong(1),r.getLong(2)},lease.spec().input().id());
   if(totals==null||totals[0]>=4800||totals[1]>16777216-bytes.length)throw new IllegalStateException("Speech checkpoint budget exceeded");
   q.jdbc.update("INSERT INTO speech_checkpoint(input_id,clip_key,stage,file_id,source_sha256,pcm_sha256,engine_sha256,start_sample,sample_count,task_id,created_attempt,payload_sha256,payload,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",lease.spec().input().id(),key,stage.name(),source.fileId(),source.sourceSha256(),source.pcmSha256(),engine,clip.startSample(),clip.sampleCount(),lease.taskId(),lease.attempt(),hash,bytes);
   return null;
  });
 }
 private Map<String,Object> read(SpeechPipeline.Stage stage){
  var rows=q.jdbc.queryForList("SELECT file_id,source_sha256,pcm_sha256,engine_sha256,start_sample,sample_count,task_id,payload_sha256,IF(OCTET_LENGTH(payload)<=1048576,payload,NULL) payload FROM speech_checkpoint WHERE input_id=? AND clip_key=? AND stage=? FOR UPDATE",lease.spec().input().id(),key,stage.name());
  if(rows.isEmpty())return null;if(rows.size()!=1)throw unavailable();var row=rows.getFirst();
  if(!source.fileId().equals(row.get("file_id"))||!source.sourceSha256().equals(row.get("source_sha256"))||!source.pcmSha256().equals(row.get("pcm_sha256"))||!engine.equals(row.get("engine_sha256"))||number(row,"start_sample")!=clip.startSample()||number(row,"sample_count")!=clip.sampleCount()||!lease.taskId().equals(row.get("task_id")))throw unavailable();
  return row;
 }
 private static byte[] payload(Map<String,Object> row){var value=row.get("payload");if(!(value instanceof byte[] bytes)||bytes.length<1||bytes.length>SpeechCheckpointCodec.MAX_BYTES||!ControlledSpeechProfile.digest(bytes).equals(row.get("payload_sha256")))throw unavailable();return bytes;}
 private static long number(Map<String,Object> row,String key){var value=row.get(key);if(!(value instanceof Number number))throw unavailable();return number.longValue();}
 private static IllegalStateException unavailable(){return new IllegalStateException("Speech checkpoint unavailable");}
}
