package ama.task;

import ama.contract.SpeechContracts.WavClip;
import ama.contract.StorageContracts.StorageKey;
import ama.contract.TaskContracts.TaskLease;
import ama.media.FfmpegNormalizer.SourceProof;
import ama.storage.FileBackendCatalog;
import ama.transcript.JdbcTranscriptAuthority;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Private normalized bytes. Intent is durable before I/O and deletion retires even absent keys. */
final class JdbcSpeechAudio {
 record Source(SourceProof proof,List<WavClip> clips){Source{clips=List.copyOf(clips);}}
 private final JdbcTaskQueue q;private final JdbcTranscriptAuthority authority;private final TaskLease lease;
 private final FileBackendCatalog backends;private final String engine;private final BooleanSupplier cancelled;
 JdbcSpeechAudio(JdbcTaskQueue q,JdbcTranscriptAuthority.Components components,TaskLease lease,String engine,BooleanSupplier cancelled){
  TranscriptImportRuntime.requireCombined(q,components.authority());ControlledSpeechProfile.hash(engine);
  this.q=q;authority=components.authority();backends=components.backends();this.lease=lease;this.engine=engine;this.cancelled=Objects.requireNonNull(cancelled);
 }
 private void current(String file,String sha){
  if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Speech audio cancelled");
  var valid=q.validatedPreparationLease(lease).orElseThrow(()->new CancellationException("Speech lease changed"));authority.requireImportPreparation(List.of(lease.spec()),valid.domain().prepared());
  Long n=q.jdbc.queryForObject("SELECT COUNT(*) FROM transcription_input i JOIN input_batch_file f ON f.batch_id=i.batch_id WHERE i.id=? AND i.meeting_id=? AND i.input_fingerprint=? AND i.parser_version=? AND f.file_id=? AND f.sha256=?",Long.class,lease.spec().input().id(),lease.spec().subject().id(),lease.spec().inputFingerprint(),ControlledSpeechProfile.PARSER,file,sha);
  if(n==null||n!=1)throw unavailable();
 }
 Optional<Source> retained(String file,String sha){return q.preparation(()->{
  current(file,sha);var rows=rows(file);if(rows.isEmpty())return Optional.empty();var first=rows.getFirst();SourceProof proof=proof(first);identity(first,proof);
  if(!file.equals(proof.fileId())||!sha.equals(proof.sourceSha256()))throw unavailable();long end=0;var clips=new ArrayList<WavClip>();
  for(var row:rows){identity(row,proof);if(!"READY".equals(row.get("state"))||number(row,"start_sample")!=end)return Optional.empty();var clip=clip(row);clips.add(clip);end=clip.endSampleExclusive();}
  return end==proof.sampleCount()?Optional.of(new Source(proof,clips)):Optional.empty();
 });}
 void retain(SourceProof source,WavClip clip,InputStream wav)throws IOException {
  var checkpoint=new JdbcSpeechCheckpoints(q,authority,lease,source,clip,engine,cancelled);byte[] bytes=wav.readNBytes(Math.toIntExact(45+clip.sampleCount()*2));
  if(bytes.length!=44+clip.sampleCount()*2)throw unavailable();String sha=ControlledSpeechProfile.digest(bytes);
  var row=q.preparation(()->{
   current(source.fileId(),source.sourceSha256());var existing=rows(source.fileId());for(var other:existing)identity(other,source);
   for(var other:existing)if(checkpoint.key().equals(other.get("clip_key"))){if(number(other,"byte_count")!=bytes.length||!sha.equals(other.get("wav_sha256")))throw unavailable();return other;}
   if(existing.size()>=60)throw unavailable();var backend=backends.defaultWrite();String id=UUID.randomUUID().toString();
   q.jdbc.update("INSERT INTO speech_pcm_artifact(id,meeting_id,input_id,task_id,file_id,clip_key,source_sha256,pcm_sha256,engine_sha256,source_samples,start_sample,sample_count,backend_id,namespace,object_key,wav_sha256,byte_count,state,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'speech-pcm',?,?,?,'RESERVED',UTC_TIMESTAMP(6))",id,lease.spec().subject().id(),lease.spec().input().id(),lease.taskId(),source.fileId(),checkpoint.key(),source.sourceSha256(),source.pcmSha256(),engine,source.sampleCount(),clip.startSample(),clip.sampleCount(),backend.id(),id,sha,bytes.length);
   return q.jdbc.queryForMap("SELECT * FROM speech_pcm_artifact WHERE id=?",id);
  });
  var key=key(row);var stored=backends.require(key.backendId(),false).storage().put(key,new ByteArrayInputStream(bytes),bytes.length,sha);
  if(!stored.key().equals(key)||stored.size()!=bytes.length||!stored.sha256().equals(sha))throw unavailable();
  q.preparation(()->{current(source.fileId(),source.sourceSha256());var actual=q.jdbc.queryForList("SELECT * FROM speech_pcm_artifact WHERE id=? FOR UPDATE",row.get("id"));if(actual.size()!=1)throw unavailable();identity(actual.getFirst(),source);if(!key(actual.getFirst()).equals(key)||!sha.equals(actual.getFirst().get("wav_sha256")))throw unavailable();q.jdbc.update("UPDATE speech_pcm_artifact SET state='READY' WHERE id=?",row.get("id"));return null;});
 }
 InputStream open(SourceProof proof,WavClip clip){
  var checkpoint=new JdbcSpeechCheckpoints(q,authority,lease,proof,clip,engine,cancelled);
  var row=q.preparation(()->{checkpoint.current();var found=q.jdbc.queryForList("SELECT * FROM speech_pcm_artifact WHERE input_id=? AND clip_key=? FOR UPDATE",lease.spec().input().id(),checkpoint.key());if(found.size()!=1)throw unavailable();var value=found.getFirst();identity(value,proof);if(!"READY".equals(value.get("state")))throw unavailable();return value;});
  int length=Math.toIntExact(number(row,"byte_count"));byte[] bytes;
  try(var in=backends.require(text(row,"backend_id"),false).storage().read(key(row),0,length)){bytes=in.readNBytes(length+1);}catch(IOException failure){throw unavailable();}
  if(bytes.length!=length||!ControlledSpeechProfile.digest(bytes).equals(row.get("wav_sha256")))throw unavailable();
  q.preparation(()->{checkpoint.current();return null;});return new ByteArrayInputStream(bytes);
 }
 private List<Map<String,Object>> rows(String file){var rows=q.jdbc.queryForList("SELECT * FROM speech_pcm_artifact WHERE input_id=? AND file_id=? ORDER BY start_sample LIMIT 61 FOR UPDATE",lease.spec().input().id(),file);if(rows.size()>60)throw unavailable();return rows;}
 private void identity(Map<String,Object> row,SourceProof source){
  if(!lease.spec().subject().id().equals(row.get("meeting_id"))||!lease.spec().input().id().equals(row.get("input_id"))||!lease.taskId().equals(row.get("task_id"))||!source.equals(proof(row))||!engine.equals(row.get("engine_sha256"))||!"speech-pcm".equals(row.get("namespace")))throw unavailable();
  var clip=clip(row);if(source.sampleCount()<1||source.sampleCount()>115200000||clip.sampleCount()>1920000||clip.endSampleExclusive()>source.sampleCount()||number(row,"byte_count")!=44+clip.sampleCount()*2)throw unavailable();
  var expected=new JdbcSpeechCheckpoints(q,authority,lease,source,clip,engine,cancelled);if(!expected.key().equals(row.get("clip_key")))throw unavailable();ControlledSpeechProfile.hash(text(row,"wav_sha256"));
 }
 private static WavClip clip(Map<String,Object> row){return new WavClip(text(row,"file_id"),number(row,"start_sample"),number(row,"sample_count"));}
 private static SourceProof proof(Map<String,Object> row){return new SourceProof(text(row,"file_id"),text(row,"source_sha256"),text(row,"pcm_sha256"),number(row,"source_samples"));}
 private static StorageKey key(Map<String,Object> row){return new StorageKey(text(row,"backend_id"),text(row,"namespace"),text(row,"object_key"));}
 private static String text(Map<String,Object> row,String name){if(!(row.get(name) instanceof String value)||value.isEmpty())throw unavailable();return value;}
 private static long number(Map<String,Object> row,String name){if(!(row.get(name) instanceof Number value))throw unavailable();return value.longValue();}
 private static IllegalStateException unavailable(){return new IllegalStateException("Normalized speech audio unavailable");}
}
