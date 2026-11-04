package ama.recording;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.FileContracts.*;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.storage.*;
import ama.task.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicit archive-to-input copy. Real upload receipts, original SHA and current owner gate are mandatory. */
public final class JdbcRecordingTranscriptSources implements AutoCloseable {
 public static final long MAX_BYTES=230400044L;
 private static final int PART_BYTES=8388608;
 private static final class LeaseChanged extends RuntimeException {}
 private final JdbcTemplate j;private final TransactionRunner tx;private final JdbcMeetingAccess access;private final RecordingMutationGuard mutations;private final JdbcRecordingPersistence recordings;private final JdbcTaskQueue queue;private final FileUploads uploads;
 private final AtomicBoolean closed=new AtomicBoolean();private final Set<String> dispatched=ConcurrentHashMap.newKeySet();private final Set<FileRegistry.Download> downloads=ConcurrentHashMap.newKeySet();
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"recording-input-poll");t.setDaemon(true);return t;});
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"recording-input-copy");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());private boolean started;
 private record Archive(String id,String session,String meeting,String file,String sha,long bytes,long samples,String backend,long epoch) {}
 private record Job(String id,Archive original,String owner,String state,long attempt,String lease,String upload,String file) {Actor actor(){return new Actor(owner,Status.ACTIVE,Set.of(Role.USER));}}
 public JdbcRecordingTranscriptSources(DataSource source,JdbcMeetingAccess access,JdbcRecordingPersistence recordings,JdbcTaskQueue queue,FileUploads uploads){
  access.requireProjectionSource(source);this.access=access;this.recordings=Objects.requireNonNull(recordings);this.queue=Objects.requireNonNull(queue);this.uploads=Objects.requireNonNull(uploads);j=new JdbcTemplate(source);j.setQueryTimeout(5);tx=new TransactionRunner(source,10);mutations=new RecordingMutationGuard(source,access);
  if(j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='060' AND success=1",Integer.class)!=1)throw new IllegalStateException("Recording transcription sources not installed");
 }
 public Map<String,Object> accept(Actor actor,String session,String key){
  independent();ama.contract.OutboxContracts.uuid(key);ama.contract.OutboxContracts.uuid(session);if(closed.get())throw new IllegalStateException("Recording inputs closed");String meeting=recordings.meetingId(session);
  return tx.required(()->{var gate=access.lock(actor,meeting);mutations.require(actor,meeting,gate);return acceptAuthorized(actor,session,meeting);});
 }
 public Map<String,Object> acceptCurrent(Actor actor,String meeting,String key){
  independent();ama.contract.OutboxContracts.uuid(key);ama.contract.OutboxContracts.uuid(meeting);if(closed.get())throw new IllegalStateException("Recording inputs closed");
  return tx.required(()->{var gate=access.lock(actor,meeting);mutations.require(actor,meeting,gate);return acceptAuthorized(actor,currentSession(meeting),meeting);});
 }
 private Map<String,Object> acceptAuthorized(Actor actor,String session,String meeting){
  if(session==null)throw new Conflict();var original=archive(session,meeting);var current=jobs(session,true);
  if(current.isEmpty())j.update("INSERT INTO recording_transcript_source(id,session_id,archive_id,meeting_id,owner_id,cancel_epoch,original_file_id,source_sha256,source_bytes,source_samples,state,next_run_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),session,original.id(),meeting,actor.enterpriseId(),original.epoch(),original.file(),original.sha(),original.bytes(),original.samples());
  else {var job=current.getFirst();same(job,original,actor.enterpriseId());if(job.state().equals("CANCELLED"))j.update("UPDATE recording_transcript_source SET state='PENDING',error_code=NULL,next_run_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",job.id());}
  return projection(jobs(session,true).getFirst());
 }
 public Map<String,Object> read(Actor actor,String session){
  independent();ama.contract.OutboxContracts.uuid(session);String meeting=recordings.meetingId(session);
  return tx.required(()->{access.require(access.lock(actor,meeting),MeetingAccess.Action.READ);return readHeld(session,meeting);});
 }
 public RecordingArchiveRuntime.Status archiveStatus(Actor actor,String session){return RecordingArchiveRuntime.status(queue,actor,session);}
 public RecordingArchiveRuntime.Status retryArchive(Actor actor,String session){return RecordingArchiveRuntime.accept(queue,actor,session);}
 public Map<String,Object> readCurrent(Actor actor,String meeting){
  independent();ama.contract.OutboxContracts.uuid(meeting);return tx.required(()->{access.require(access.lock(actor,meeting),MeetingAccess.Action.READ);String session=currentSession(meeting);if(session==null)return Map.of("meetingId",meeting,"state","NOT_RECORDED","archiveAvailable",false);return readHeld(session,meeting);});
 }
 private String currentSession(String meeting){var sessions=j.queryForList("SELECT session_id FROM recording_meeting_capture WHERE meeting_id=? FOR SHARE",String.class,meeting);if(sessions.isEmpty())return null;if(sessions.size()!=1)throw new IllegalStateException("Recording capture is ambiguous");return sessions.getFirst();}
 private Map<String,Object> readHeld(String session,String meeting){
  if(session==null)throw new Conflict();return recordings.withinMeeting(session,meeting,()->recordings.locked(session,()->{
   var rows=jobs(session,true);if(rows.isEmpty()){boolean ready=!j.queryForList("SELECT a.id FROM recording_archive a JOIN recording_archive_current c ON c.session_id=a.session_id AND c.input_id=a.input_id JOIN durable_task t ON t.input_id=a.input_id AND t.input_kind='RECORDING_ARCHIVE_INPUT' AND t.task_type='RECORDING_ARCHIVE_V1' AND t.state='SUCCEEDED' WHERE a.session_id=? AND a.meeting_id=? AND a.state='READY' LIMIT 1 FOR SHARE",String.class,session,meeting).isEmpty();if(ready)archive(session,meeting);return Map.of("meetingId",meeting,"sessionId",session,"state","NOT_REQUESTED","archiveAvailable",ready);}
   var job=rows.getFirst();if(!job.original().meeting().equals(meeting))throw new MeetingAccess.AccessDeniedException();if(job.state().equals("READY")){same(job,archive(session,meeting),job.owner());verifyCopy(job,job.file());}return projection(job);
  }));
 }
 public synchronized void startScheduler(){if(started||closed.get())return;started=true;timer.scheduleWithFixedDelay(()->{try{dispatch();}catch(RuntimeException unavailable){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Recording input scheduling deferred");}},0,500,TimeUnit.MILLISECONDS);}
 private void dispatch(){
  var rows=j.queryForList("SELECT session_id FROM recording_transcript_source WHERE next_run_at<=UTC_TIMESTAMP(6) AND (state IN ('PENDING','DELAYED') OR state='LEASED' AND lease_expires_at<=UTC_TIMESTAMP(6)) ORDER BY next_run_at,id LIMIT 16",String.class);
  for(String session:rows)if(dispatched.add(session))try{workers.execute(()->{try{runOnce(session);}finally{dispatched.remove(session);}});}catch(RejectedExecutionException full){dispatched.remove(session);}
 }
 public boolean runOnce(String session){
  independent();if(closed.get())return false;Job held=null;
  try{
   var route=jobs(session,false);if(route.isEmpty())return false;var observed=route.getFirst();
   held=tx.required(()->{var gate=access.lock(observed.actor(),observed.original().meeting());mutations.require(observed.actor(),observed.original().meeting(),gate);var actual=archive(session,observed.original().meeting());var rows=jobs(session,true);if(rows.size()!=1)throw new LeaseChanged();var job=rows.getFirst();same(job,actual,observed.owner());if(Set.of("READY","CANCELLED").contains(job.state()))return null;if(job.state().equals("LEASED")&&j.queryForObject("SELECT lease_expires_at>UTC_TIMESTAMP(6) FROM recording_transcript_source WHERE id=?",Boolean.class,job.id()))return null;
    if(job.attempt()>=Long.MAX_VALUE-1)throw new IllegalStateException("Recording copy lease exhausted");j.update("UPDATE recording_transcript_source SET state='LEASED',attempt=attempt+1,lease_owner=?,lease_expires_at=UTC_TIMESTAMP(6)+INTERVAL 30 SECOND,error_code=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=?",UUID.randomUUID().toString(),job.id());return jobs(session,true).getFirst();});
   if(held==null)return false;copy(held);return true;
  }catch(MeetingAccess.AccessDeniedException|RecordingMutationGuard.Frozen denied){defer(held,session,true);return false;}
  catch(RuntimeException|IOException unavailable){defer(held,session,false);return false;}
 }
 private void copy(Job job)throws IOException {
  long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);guard(job,deadline);var original=job.original();String upload=job.upload();
  if(upload==null){var accepted=uploads.beginPinned(job.actor(),original.meeting(),job.id(),new Begin("TRANSCRIPT_INPUT","录音归档转写-"+original.session()+".wav",original.bytes(),original.sha()),"RECORDING_TRANSCRIPT_SOURCE",original.backend());String selected=accepted.uploadId();tx.required(()->{guardHeld(job);j.update("UPDATE recording_transcript_source SET upload_id=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND upload_id IS NULL",selected,job.id());var current=jobs(original.session(),true).getFirst();if(!selected.equals(current.upload()))throw new LeaseChanged();return null;});upload=selected;}
  final String target=upload;var receipts=new ArrayList<ClientPart>();var digest=digest();long total=0;
  try(var download=RecordingArchiveRuntime.download(queue,job.actor(),original.session(),null)){
   downloads.add(download);try{
    if(!download.file().fileId().equals(original.file())||!download.file().sha256().equals(original.sha())||download.file().size()!=original.bytes()||download.offset()!=0||download.length()!=original.bytes())throw new IOException("Recording archive source changed");
    for(int part=1;total<original.bytes();part++){
     guard(job,deadline);int size=(int)Math.min(PART_BYTES,original.bytes()-total);byte[] bytes=download.body().readNBytes(size);if(bytes.length!=size)throw new IOException("Recording source truncated");digest.update(bytes);String hash=sha(bytes);guard(job,deadline);uploads.part(job.actor(),target,part,new ByteArrayInputStream(bytes),bytes.length,hash,"RECORDING_TRANSCRIPT_SOURCE");receipts.add(new ClientPart(part,hash));total+=bytes.length;
    }
    if(download.body().read()!=-1||total!=original.bytes()||!HexFormat.of().formatHex(digest.digest()).equals(original.sha()))throw new IOException("Recording archive SHA mismatch");
   }finally{downloads.remove(download);}
  }
  guard(job,deadline);String file=uploads.complete(job.actor(),target,new Completion(receipts,original.sha()),"RECORDING_TRANSCRIPT_SOURCE").fileId();
  tx.required(()->{guardHeld(job);verifyCopy(new Job(job.id(),job.original(),job.owner(),job.state(),job.attempt(),job.lease(),target,file),file);if(j.update("UPDATE recording_transcript_source SET state='READY',copy_file_id=?,lease_owner=NULL,lease_expires_at=NULL,error_code=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND state='LEASED' AND attempt=? AND lease_owner=?",file,job.id(),job.attempt(),job.lease())!=1)throw new LeaseChanged();return null;});
 }
 private void guard(Job job,long deadline){if(closed.get()||Thread.currentThread().isInterrupted()||System.nanoTime()-deadline>=0)throw new CancellationException("Recording copy stopped");tx.required(()->{guardHeld(job);j.update("UPDATE recording_transcript_source SET lease_expires_at=UTC_TIMESTAMP(6)+INTERVAL 30 SECOND WHERE id=?",job.id());return null;});}
 private Job guardHeld(Job expected){
  var gate=access.lock(expected.actor(),expected.original().meeting());mutations.require(expected.actor(),expected.original().meeting(),gate);var actual=archive(expected.original().session(),expected.original().meeting());var rows=jobs(expected.original().session(),true);if(rows.size()!=1)throw new LeaseChanged();var current=rows.getFirst();same(current,actual,expected.owner());if(!current.id().equals(expected.id())||!current.state().equals("LEASED")||current.attempt()!=expected.attempt()||!Objects.equals(current.lease(),expected.lease())||!j.queryForObject("SELECT lease_expires_at>UTC_TIMESTAMP(6) FROM recording_transcript_source WHERE id=?",Boolean.class,current.id()))throw new LeaseChanged();return current;
 }
 private Archive archive(String session,String meeting){
  return recordings.withinMeeting(session,meeting,()->recordings.locked(session,()->{
   var rows=j.queryForList("SELECT a.id,a.session_id,a.meeting_id,a.file_id,a.sha256,a.size_bytes,a.sample_count,a.backend_id,m.cancel_epoch FROM recording_archive a JOIN recording_archive_current c ON c.session_id=a.session_id AND c.input_id=a.input_id JOIN recording_archive_input i ON i.id=a.input_id AND i.session_id=a.session_id AND i.meeting_id=a.meeting_id JOIN durable_task t ON t.input_id=i.id AND t.input_kind='RECORDING_ARCHIVE_INPUT' AND t.producer_key=i.producer_key JOIN recording_session s ON s.session_id=a.session_id JOIN meeting m ON m.id=a.meeting_id WHERE a.session_id=? AND a.meeting_id=? AND s.state='FINISHED' AND a.state='READY' AND t.state='SUCCEEDED' AND t.task_type='RECORDING_ARCHIVE_V1' AND t.pool='RECORDING_ARCHIVE' AND t.subject_kind='MEETING' AND t.subject_id=a.meeting_id AND t.output_kind='RECORDING_ARCHIVE' AND t.output_id=a.id AND t.business_version=i.base_revision AND t.cancel_epoch=i.cancel_epoch AND t.input_fingerprint=i.input_fingerprint AND i.cancel_epoch=m.cancel_epoch FOR UPDATE",session,meeting);
   if(rows.size()!=1)throw new Conflict();var r=rows.getFirst();var result=new Archive((String)r.get("id"),session,meeting,(String)r.get("file_id"),(String)r.get("sha256"),n(r,"size_bytes"),n(r,"sample_count"),(String)r.get("backend_id"),n(r,"cancel_epoch"));if(result.file()==null||result.sha()==null||!result.sha().matches("[0-9a-f]{64}")||result.samples()<1||result.samples()>115200000||result.bytes()!=44+result.samples()*2||result.bytes()>MAX_BYTES)throw new IllegalStateException("Invalid recording archive proof");return result;
  }));
 }
 private List<Job> jobs(String session,boolean lock){return j.query("SELECT q.id,q.session_id,q.archive_id,q.meeting_id,q.owner_id,q.cancel_epoch,q.original_file_id,q.source_sha256,q.source_bytes,q.source_samples,q.upload_id,q.copy_file_id,q.state,q.attempt,q.lease_owner,a.backend_id FROM recording_transcript_source q JOIN recording_archive a ON a.id=q.archive_id WHERE q.session_id=?"+(lock?" FOR UPDATE":""),(r,n)->new Job(r.getString("id"),new Archive(r.getString("archive_id"),r.getString("session_id"),r.getString("meeting_id"),r.getString("original_file_id"),r.getString("source_sha256"),r.getLong("source_bytes"),r.getLong("source_samples"),r.getString("backend_id"),r.getLong("cancel_epoch")),r.getString("owner_id"),r.getString("state"),r.getLong("attempt"),r.getString("lease_owner"),r.getString("upload_id"),r.getString("copy_file_id")),session);}
 private void same(Job job,Archive original,String owner){if(!job.original().equals(original)||!job.owner().equals(owner))throw new MeetingAccess.AccessDeniedException();}
 /** Exact immutable READY origin proof; caller already holds its current owner/meeting gate. No authorization is minted here. */
 public static boolean readyCopy(JdbcTemplate jdbc,String meeting,String owner,long epoch,String file,String hash){
  if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))throw new IllegalStateException("Current recording source transaction required");
  var rows=jdbc.queryForList("SELECT q.copy_file_id FROM recording_transcript_source q JOIN recording_archive a ON a.id=q.archive_id AND a.session_id=q.session_id AND a.meeting_id=q.meeting_id JOIN recording_archive_current c ON c.session_id=a.session_id AND c.input_id=a.input_id JOIN recording_archive_input i ON i.id=a.input_id AND i.session_id=a.session_id AND i.meeting_id=a.meeting_id JOIN durable_task t ON t.input_id=i.id AND t.input_kind='RECORDING_ARCHIVE_INPUT' AND t.producer_key=i.producer_key JOIN file_object f ON f.id=q.copy_file_id JOIN upload_session u ON u.id=q.upload_id AND u.completed_file_id=f.id JOIN file_object original ON original.id=q.original_file_id WHERE q.meeting_id=? AND q.owner_id=? AND q.cancel_epoch=? AND q.copy_file_id=? AND q.source_sha256=? AND q.state='READY' AND a.state='READY' AND a.file_id=q.original_file_id AND a.sha256=q.source_sha256 AND a.size_bytes=q.source_bytes AND a.sample_count=q.source_samples AND q.source_bytes=44+q.source_samples*2 AND q.source_samples BETWEEN 1 AND 115200000 AND f.meeting_id=q.meeting_id AND f.state='AVAILABLE' AND f.sha256=q.source_sha256 AND f.size_bytes=q.source_bytes AND u.meeting_id=q.meeting_id AND u.cancel_epoch=q.cancel_epoch AND u.state='COMPLETED' AND u.purpose='TRANSCRIPT_INPUT' AND u.expected_size=f.size_bytes AND u.expected_sha256=f.sha256 AND original.meeting_id=q.meeting_id AND original.state='AVAILABLE' AND original.sha256=q.source_sha256 AND original.size_bytes=q.source_bytes AND i.cancel_epoch=q.cancel_epoch AND t.state='SUCCEEDED' AND t.task_type='RECORDING_ARCHIVE_V1' AND t.pool='RECORDING_ARCHIVE' AND t.subject_kind='MEETING' AND t.subject_id=q.meeting_id AND t.output_kind='RECORDING_ARCHIVE' AND t.output_id=a.id AND t.business_version=i.base_revision AND t.cancel_epoch=i.cancel_epoch AND t.input_fingerprint=i.input_fingerprint FOR SHARE",String.class,meeting,owner,epoch,file,hash);
  return rows.equals(List.of(file));
 }
 private void verifyCopy(Job job,String file){
  if(file==null||job.upload()==null)throw new IllegalStateException("Recording copy receipt missing");var rows=j.queryForList("SELECT f.id FROM file_object f JOIN upload_session u ON u.completed_file_id=f.id WHERE f.id=? AND f.meeting_id=? AND f.sha256=? AND f.size_bytes=? AND f.state='AVAILABLE' AND u.id=? AND u.meeting_id=f.meeting_id AND u.state='COMPLETED' AND u.purpose='TRANSCRIPT_INPUT' AND u.cancel_epoch=? AND u.expected_size=f.size_bytes AND u.expected_sha256=f.sha256 FOR SHARE",String.class,file,job.original().meeting(),job.original().sha(),job.original().bytes(),job.upload(),job.original().epoch());if(!rows.equals(List.of(file)))throw new IllegalStateException("Recording copy provenance mismatch");
 }
 private Map<String,Object> projection(Job job){var value=new LinkedHashMap<String,Object>();value.put("meetingId",job.original().meeting());value.put("sessionId",job.original().session());value.put("sourceId",job.id());value.put("state",job.state());value.put("sourceSha256",job.original().sha());value.put("sourceBytes",job.original().bytes());value.put("sourceSamples",job.original().samples());value.put("archiveAvailable",true);if(job.state().equals("READY")){value.put("fileId",job.file());
  // This is an observation for navigation, never an import receipt or completion grant.
  var tasks=j.queryForList("SELECT t.id,t.state FROM input_batch_file b JOIN input_batch batch ON batch.id=b.batch_id JOIN transcription_input i ON i.batch_id=batch.id JOIN durable_task t ON t.input_kind='TRANSCRIPTION_INPUT' AND t.input_id=i.id WHERE b.file_id=? AND b.sha256=? AND batch.meeting_id=? AND batch.kind='TRANSCRIPT' AND i.meeting_id=batch.meeting_id AND i.kind='IMPORT' AND i.parser_version=? AND i.cancel_epoch=? AND t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND t.subject_kind='MEETING' AND t.subject_id=i.meeting_id AND t.business_version=i.base_revision AND t.cancel_epoch=i.cancel_epoch AND t.input_fingerprint=i.input_fingerprint AND EXISTS(SELECT 1 FROM file_reference r WHERE r.file_id=b.file_id AND r.owner_kind='INPUT_BATCH_FILE' AND r.owner_id=batch.id AND r.purpose='TRANSCRIPT_INPUT') ORDER BY i.created_at DESC,i.id DESC LIMIT 1 FOR SHARE",job.file(),job.original().sha(),job.original().meeting(),ama.transcript.RecordingTranscriptProfile.PARSER,job.original().epoch());if(tasks.size()==1){value.put("transcriptionTaskId",tasks.getFirst().get("id"));value.put("transcriptionState",tasks.getFirst().get("state"));}
  }return Collections.unmodifiableMap(value);}
 private void defer(Job held,String session,boolean denied){try{if(held==null)j.update("UPDATE recording_transcript_source SET state=?,lease_owner=NULL,lease_expires_at=NULL,error_code=?,next_run_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND,updated_at=UTC_TIMESTAMP(6) WHERE session_id=? AND (state IN ('PENDING','DELAYED') OR state='LEASED' AND lease_expires_at<=UTC_TIMESTAMP(6))",denied?"CANCELLED":"DELAYED",denied?"ACCESS_CHANGED":"COPY_RETRY_PENDING",session);else j.update("UPDATE recording_transcript_source SET state=?,lease_owner=NULL,lease_expires_at=NULL,error_code=?,next_run_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND state='LEASED' AND attempt=? AND lease_owner=?",denied?"CANCELLED":"DELAYED",denied?"ACCESS_CHANGED":"COPY_RETRY_PENDING",held.id(),held.attempt(),held.lease());}catch(RuntimeException unavailable){/* The original lease remains recoverable. */}}
 private static long n(Map<String,Object> r,String key){if(!(r.get(key) instanceof Number n))throw new IllegalStateException("Missing recording proof");return n.longValue();}
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static String sha(byte[] bytes){return HexFormat.of().formatHex(digest().digest(bytes));}
 private static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Recording copy requires an independent operation");}
 @Override public void close(){if(closed.compareAndSet(false,true)){timer.shutdownNow();workers.shutdownNow();for(var download:downloads)try{download.close();}catch(IOException ignored){}try{workers.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}}}
}
