package ama.recording;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.SpeechContracts;
import ama.db.TransactionRunner;
import ama.model.HttpSpeechModelPort;
import ama.model.HttpSpeechModelPort.Stage;
import com.fasterxml.jackson.databind.ObjectMapper;
import ama.task.ControlledSpeechProfile;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A private, versioned caption writer. Audio ACK and archive never depend on it. */
public final class JdbcLiveSubtitles implements AutoCloseable {
 private static final class OutputBudget extends RuntimeException {}
 private static final class LeaseChanged extends RuntimeException {}
 private static final String PATH="/inference/asr-nano/modelserving/asr";
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final long TEXT_BYTES=33554432;
 private final JdbcTemplate j;private final TransactionRunner tx;private final MeetingAccess access;private final RecordingMutationGuard mutations;private final JdbcRecordingPersistence recordings;private final ControlledSpeechProfile profile;
 private final AtomicBoolean closed=new AtomicBoolean();private final ThreadLocal<Audio> activeAudio=new ThreadLocal<>();
 private final ConcurrentMap<String,Admission> admissions=new ConcurrentHashMap<>();
 private final Set<String> dispatched=ConcurrentHashMap.newKeySet();
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"recording-subtitle-poll");t.setDaemon(true);return t;});
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(16,16,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"recording-subtitle-work");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final ThreadPoolExecutor speakerWorkers=new ThreadPoolExecutor(0,16,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,"recording-subtitle-speakers");t.setDaemon(true);return t;});
 private final HttpSpeechModelPort model;private boolean started;
 private record Route(String session,String meeting,String owner) {Actor actor(){return new Actor(owner,Status.ACTIVE,Set.of(Role.USER));}}
 private record Job(String id,Route route,long epoch,int part,long seq,long samples,long offset,long windowSeq,long windowSample,int revision,long events,long textBytes,long attempt,String lease,String state){}
 private record Session(String id,String meeting,String part,String state){}
 private record Proof(String part,long seq,long start,long count,String sha,long bytes,String key){}
 private record Window(Job job,String part,List<Proof> proofs,long end,long seq,boolean closingTail){boolean full(){return seq-job.windowSeq()+1==3;}}
 private static final class Audio {final Window window;final byte[] wav;final SpeechContracts.WavClip clip;Stage expected=Stage.ASR;String execution,diarization;
  Audio(Window window,byte[] wav){this.window=window;this.wav=wav;clip=new SpeechContracts.WavClip(window.job().route().session(),window.job().windowSample(),window.end()-window.job().windowSample());}}
 private record VerifiedPrefix(List<Proof> proofs,byte[] wav){}
 // Each entry contains at most two verified chunks, keyed by the authorized session.
 private final LinkedHashMap<String,VerifiedPrefix> prefixes=new LinkedHashMap<>(32,0.75f,true);
 private record Admission(String scope,String path,String requestSha,String job,long attempt,String ownerSha){}
 private record Claim(boolean progressed,Window window){}
 public JdbcLiveSubtitles(DataSource source,MeetingAccess access,JdbcRecordingPersistence recordings,ControlledSpeechProfile profile){
  if(!(access instanceof ama.meeting.JdbcMeetingAccess actual))throw new IllegalArgumentException("Actual recording authorization required");actual.requireProjectionSource(source);
  this.access=access;this.recordings=Objects.requireNonNull(recordings);this.profile=Objects.requireNonNull(profile);j=new JdbcTemplate(source);j.setQueryTimeout(5);tx=new TransactionRunner(source,10);mutations=new RecordingMutationGuard(source,access);
  if(j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='059'",Integer.class)!=1)throw new IllegalStateException("Durable subtitles not installed");
  tx.required(()->{j.update("INSERT INTO speech_capacity(service_key,total_slots,realtime_slots,offline_slots,config_revision) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE service_key=service_key",ControlledSpeechProfile.SERVICE,profile.totalSlots(),profile.realtimeSlots(),profile.offlineSlots(),profile.quotaRevision());capacity();return null;});
  var config=profile.http();model=new HttpSpeechModelPort(new HttpSpeechModelPort.Config(config.endpoints(),192044,65536,32,config.timeout(),config.embeddingModelVersion(),config.embeddingDimension()),clip->{var audio=activeAudio.get();if(audio==null||!audio.clip.equals(clip))throw new IOException("Unissued recording window");return new ByteArrayInputStream(audio.wav);},new Observer());
 }
 /** Explicit creation/resume command; GET never creates model work. */
 public void start(Actor actor,String session){independent();String meeting=recordings.meetingId(session);tx.required(()->{var gate=access.lock(actor,meeting);attach(actor,meeting,gate,session);return null;});}
 /** The existing recording creation transaction passes its authentic owner gate. */
 public void attach(Actor actor,String meeting,MeetingAccess.Locked gate,String session){
  if(closed.get())throw new IllegalStateException("Subtitles closed");mutations.require(actor,meeting,gate);
  recordings.withinMeeting(session,meeting,()->{
   var current=session(session);if(!current.meeting().equals(meeting)||current.state().equals("FINISHED"))throw new IllegalArgumentException("Live recording required");long epoch=epoch(meeting);
   var old=jobs(session,false);if(old.isEmpty())j.update("INSERT INTO recording_subtitle_job(id,session_id,meeting_id,owner_id,cancel_epoch,state,next_run_at,created_at,updated_at) VALUES (?,?,?,?,?,'IDLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),session,meeting,actor.enterpriseId(),epoch);
   else{var value=old.getFirst();if(!value.route().owner().equals(actor.enterpriseId())||value.epoch()!=epoch)throw new MeetingAccess.AccessDeniedException();if(Set.of("CANCELLED","DEFERRED","DELAYED").contains(value.state()))j.update("UPDATE recording_subtitle_job SET state='IDLE',error_code=NULL,next_run_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",value.id());}
   return null;
  });
 }
 public synchronized void startScheduler(){if(started||closed.get())return;started=true;timer.scheduleWithFixedDelay(()->{try{dispatch();}catch(RuntimeException unavailable){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Recording subtitle scheduling deferred");}},0,100,TimeUnit.MILLISECONDS);}
 private void dispatch(){
  if(closed.get())return;
  var due=j.queryForList("""
    SELECT q.session_id FROM recording_subtitle_job q
    JOIN recording_session s ON s.session_id=q.session_id
    JOIN recording_part p ON p.session_id=s.session_id AND p.part_id=s.part_id
    WHERE q.next_run_at<=UTC_TIMESTAMP(6)
     AND (q.state IN ('IDLE','DELAYED') OR q.state='LEASED' AND q.lease_expires_at<=UTC_TIMESTAMP(6))
     AND (p.part_no>q.part_no OR s.state='FINISHED' OR s.state='PAUSED' AND q.window_revision>0
      OR (SELECT c.seq FROM recording_chunk c WHERE c.session_id=q.session_id AND c.part_id=p.part_id
          ORDER BY c.session_id DESC,c.part_id DESC,c.seq DESC LIMIT 1)>q.processed_seq)
    ORDER BY q.next_run_at,q.id LIMIT 128
    """,String.class);
  for(String session:due){
   if(!dispatched.add(session))continue;
   try{workers.execute(()->{
    try{
     // Process buffered contiguous audio immediately, then give other sessions a turn.
     for(int n=0;n<3&&runOnce(session);n++){
      if(n==2||j.queryForList("""
        SELECT 1 FROM recording_subtitle_job q
        JOIN recording_part p ON p.session_id=q.session_id AND p.part_no=q.part_no
        JOIN recording_chunk c ON c.session_id=p.session_id AND c.part_id=p.part_id AND c.seq=q.processed_seq+1
        WHERE q.session_id=? AND q.state='IDLE'
        """,Integer.class,session).isEmpty())break;
     }
    }finally{dispatched.remove(session);}
   });}catch(RejectedExecutionException full){dispatched.remove(session);}
  }
 }
 /** Same production work unit used by the scheduler; no arbitrary text or model output is accepted. */
 public boolean runOnce(String session){
  independent();if(closed.get())return false;Window window=null;
  try{
   var route=route(session);if(route==null)return false;var claim=tx.required(()->claim(route));if(claim.window()==null)return claim.progressed();window=claim.window();
   byte[] wav=audio(window);var active=new Audio(window,wav);activeAudio.set(active);
   try{
    // Partial ASR revisions share the same audio. Identify speakers once, when the window closes.
    if(window.closingTail()){
     var spans=speakers(active);
     return tx.required(()->commit(active,null,spans));
    }
    var speakerAudio=new Audio(window,wav);
    var pending=window.full()?startSpeakers(speakerAudio):null;
    try{
     var recognized=model.recognize(active.clip);
     if(recognized.text().length()>8192||active.execution==null)throw new IllegalStateException("Invalid caption execution");
     var spans=pending==null?List.<SpeechContracts.SpeakerSpan>of():awaitSpeakers(pending);
     active.diarization=speakerAudio.diarization;
     return tx.required(()->commit(active,recognized,spans));
    }finally{
     // Finish the optional model request before releasing or retrying its source lease.
     if(pending!=null)pending.handle((result,failure)->null).join();
    }
   }finally{activeAudio.remove();}
  }catch(OutputBudget exceeded){defer(window,session,false,"OUTPUT_BUDGET_EXCEEDED");return false;}
  catch(MeetingAccess.AccessDeniedException|RecordingMutationGuard.Frozen denied){defer(window,session,true,"ACCESS_CHANGED");return false;}
  catch(RuntimeException|IOException unavailable){defer(window,session,false,"SUBTITLE_RETRY_PENDING");return false;}
 }
 private CompletableFuture<List<SpeechContracts.SpeakerSpan>> startSpeakers(Audio audio){
  // ASR and VAD consume the same immutable WAV independently. Each request still obtains its own admission.
  try{return CompletableFuture.supplyAsync(()->{
   activeAudio.set(audio);try{return speakers(audio);}finally{activeAudio.remove();}
  },speakerWorkers);}catch(RejectedExecutionException full){return null;}
 }
 private static List<SpeechContracts.SpeakerSpan> awaitSpeakers(CompletableFuture<List<SpeechContracts.SpeakerSpan>> pending){
  try{return pending.join();}catch(CompletionException failure){
   if(failure.getCause() instanceof RuntimeException cause)throw cause;
   throw failure;
  }
 }
 private List<SpeechContracts.SpeakerSpan> speakers(Audio audio){
  try{
   audio.expected=Stage.VAD;
   var speech=model.vad(audio.clip);
   if(speech.isEmpty()||speech.size()>128)return List.of();
   audio.expected=Stage.DIARIZATION;
   return model.diarize(audio.clip,speech);
  }catch(MeetingAccess.AccessDeniedException|RecordingMutationGuard.Frozen denied){throw denied;}
  catch(HttpSpeechModelPort.Unavailable|HttpSpeechModelPort.InvalidResponse|ama.model.CompanySpeechAdapter.TimedOut|ama.model.CompanySpeechAdapter.RateLimited|IllegalArgumentException unavailable){return List.of();}
 }
 private Claim claim(Route route){
  var gate=access.lock(route.actor(),route.meeting());mutations.require(route.actor(),route.meeting(),gate);
  return recordings.withinMeeting(route.session(),route.meeting(),()->{
   var list=jobs(route.session(),false);if(list.size()!=1)return new Claim(false,null);var job=list.getFirst();sameRoute(job,route);if(job.epoch()!=epoch(route.meeting()))throw new MeetingAccess.AccessDeniedException();
   if(Set.of("DONE","CANCELLED","DEFERRED").contains(job.state()))return new Claim(false,null);
   if(job.state().equals("LEASED")&&j.queryForObject("SELECT lease_expires_at>UTC_TIMESTAMP(6) FROM recording_subtitle_job WHERE id=?",Boolean.class,job.id()))return new Claim(false,null);
   var s=session(route.session());if(!s.meeting().equals(route.meeting()))throw new IllegalStateException("Recording binding changed");var parts=j.queryForList("SELECT part_id FROM recording_part WHERE session_id=? AND part_no=? FOR SHARE",String.class,route.session(),job.part());if(parts.size()!=1)throw new IllegalStateException("Recording part unavailable");String part=parts.getFirst();
   var next=proofs(route.session(),part,job.seq()+1,job.seq()+1);boolean sealed=!s.part().equals(part)||!s.state().equals("OPEN");
   if(next.isEmpty()){
    if(sealed&&job.revision()>0)return leaseWindow(job,part,proofs(route.session(),part,job.windowSeq(),job.seq()),job.samples(),job.seq(),true);
    long maximum=j.queryForObject("SELECT COALESCE(MAX(c.seq),0) FROM (SELECT seq FROM recording_chunk WHERE session_id=? AND part_id=? FOR SHARE) c",Long.class,route.session(),part);
    if(maximum>job.seq()){idle(job,"DELAYED","AUDIO_GAP",200000);return new Claim(false,null);}
    if(!s.part().equals(part)){
     if(job.offset()+job.samples()>115200000)throw new IllegalStateException("Recording sample budget");
     j.update("UPDATE recording_subtitle_job SET part_no=part_no+1,part_offset_samples=part_offset_samples+processed_samples,processed_seq=0,processed_samples=0,window_start_seq=1,window_start_sample=0,window_revision=0,state='IDLE',lease_owner=NULL,lease_expires_at=NULL,next_run_at=UTC_TIMESTAMP(6),error_code=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=?",job.id());return new Claim(true,null);
    }
    idle(job,s.state().equals("FINISHED")?"DONE":"IDLE",null,200000);return new Claim(false,null);
   }
   var last=next.getFirst();if(last.start()!=job.samples()||job.seq()+1-job.windowSeq()>=3)throw new IllegalStateException("Noncontiguous caption window");
   var selected=proofs(route.session(),part,job.windowSeq(),last.seq());if(selected.size()!=last.seq()-job.windowSeq()+1)throw new IllegalStateException("Caption audio gap");long sample=job.windowSample(),seq=job.windowSeq();for(var proof:selected){if(proof.seq()!=seq++||proof.start()!=sample)throw new IllegalStateException("Caption source continuity");sample+=proof.count();}if(sample-last.start()!=last.count()||sample-job.windowSample()>96000)throw new IllegalStateException("Caption sample bound");
   return leaseWindow(job,part,selected,sample,last.seq(),false);
  });
 }
 private Claim leaseWindow(Job job,String part,List<Proof> source,long end,long seq,boolean closingTail){
  if(job.attempt()>=Long.MAX_VALUE-1)throw new IllegalStateException("Caption lease exhausted");
  String lease=UUID.randomUUID().toString();
  j.update("UPDATE recording_subtitle_job SET state='LEASED',attempt=attempt+1,lease_owner=?,lease_expires_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND,updated_at=UTC_TIMESTAMP(6) WHERE id=?",lease,job.id());
  var held=jobs(job.route().session(),false).getFirst();
  return new Claim(false,new Window(held,part,List.copyOf(source),end,seq,closingTail));
 }
 private byte[] audio(Window window)throws IOException{
  String session=window.job().route().session();
  VerifiedPrefix cached;
  synchronized(prefixes){cached=prefixes.remove(session);}
  var proofs=window.proofs();int first=0;
  var pcm=new ByteArrayOutputStream(Math.toIntExact((window.end()-window.job().windowSample())*2));
  if(cached!=null&&cached.proofs().size()<=proofs.size()&&proofs.subList(0,cached.proofs().size()).equals(cached.proofs())){
   pcm.write(cached.wav(),44,cached.wav().length-44);
   first=cached.proofs().size();
  }
  for(var proof:proofs.subList(first,proofs.size())){
   try(var input=recordings.readChunk(session,window.part(),proof.seq())){
    byte[] bytes=input.readNBytes(64001);
    if(bytes.length!=proof.bytes()||!sha(bytes).equals(proof.sha()))throw new IOException("Caption PCM integrity mismatch");
    pcm.write(bytes);
   }
  }
  byte[] value=pcm.toByteArray();
  if(value.length!=(window.end()-window.job().windowSample())*2||value.length>192000)throw new IOException("Caption PCM window mismatch");
  byte[] result=wav(value);
  if(!window.full()&&!window.closingTail()){
   synchronized(prefixes){prefixes.put(session,new VerifiedPrefix(proofs,result));if(prefixes.size()>32)prefixes.pollFirstEntry();}
  }
  return result;
 }
 private String speakerJson(SpeechContracts.RecognitionResult recognized,List<SpeechContracts.SpeakerSpan> spans,Window window){
  var turns=CaptionSpeakers.align(recognized,spans,window.job().offset(),window.job().windowSample());
  if(turns.isEmpty())return null;
  try{return JSON.writeValueAsString(turns);}catch(IOException invalid){throw new IllegalStateException("Invalid caption turns",invalid);}
 }
 private boolean commit(Audio audio,SpeechContracts.RecognitionResult recognized,List<SpeechContracts.SpeakerSpan> spans){
  var window=audio.window;
  var job=window.job();var route=job.route();var gate=access.lock(route.actor(),route.meeting());mutations.require(route.actor(),route.meeting(),gate);
  return recordings.withinMeeting(route.session(),route.meeting(),()->{
   var current=held(job);if(current.epoch()!=epoch(route.meeting())||!proofs(route.session(),window.part(),job.windowSeq(),window.seq()).equals(window.proofs()))throw new MeetingAccess.AccessDeniedException();
   String diarization=spans.isEmpty()?null:audio.diarization;
   if(diarization!=null)verifyExecution(job,diarization,"/diarize");
   if(window.closingTail()){closeTail(window,spans,diarization);return true;}
   String execution=audio.execution,text=recognized.text();
   verifyExecution(job,execution,PATH);
   String speakerJson=speakerJson(recognized,spans,window);
   boolean stable=window.full();emit(job,window.part(),job.revision()+1,stable?"stable":"temporary",text,window.seq(),job.offset()+job.windowSample(),job.offset()+window.end(),fingerprint(window),execution,speakerJson,speakerJson==null?null:diarization);
   j.update("UPDATE recording_subtitle_job SET processed_seq=?,processed_samples=?,window_start_seq=?,window_start_sample=?,window_revision=?,state='IDLE',lease_owner=NULL,lease_expires_at=NULL,error_code=NULL,next_run_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",window.seq(),window.end(),stable?window.seq()+1:job.windowSeq(),stable?window.end():job.windowSample(),stable?0:job.revision()+1,job.id());return true;
  });
 }
 private void verifyExecution(Job job,String execution,String path){
   var resource=j.queryForList("SELECT id FROM speech_execution WHERE id=? AND service_key=? AND scope_sha256=? AND lane='REALTIME' AND path=? AND state='COMPLETED' AND response_status=200 AND response_sha256 IS NOT NULL AND settled_at IS NOT NULL AND lease_task_id=? AND lease_attempt=? AND lease_owner_sha256=? FOR SHARE",String.class,execution,ControlledSpeechProfile.SERVICE,scope(job.route().session()),path,job.id(),job.attempt(),sha(job.lease().getBytes(StandardCharsets.UTF_8)));if(!resource.equals(List.of(execution)))throw new IllegalStateException("Caption model result unavailable");
 }
 private void closeTail(Window window,List<SpeechContracts.SpeakerSpan> spans,String diarization){
  var job=window.job();String part=window.part();
  var rows=j.queryForList("SELECT segment_id,revision,status,body,text_sha256,source_final_seq,start_ms,end_ms,source_fingerprint_sha256,execution_id,speaker_turns,diarization_execution_id FROM recording_subtitle_event WHERE session_id=? AND event_no=? FOR UPDATE",job.route().session(),job.events());if(rows.size()!=1)throw new IllegalStateException("Caption tail missing");var row=rows.getFirst();String text=(String)row.get("body");if(!segment(job,part).equals(row.get("segment_id"))||n(row,"revision")!=job.revision()||!"temporary".equals(row.get("status"))||n(row,"source_final_seq")!=job.seq()||text==null||!sha(text.getBytes(StandardCharsets.UTF_8)).equals(row.get("text_sha256")))throw new IllegalStateException("Caption tail integrity");
  var source=proofs(job.route().session(),part,job.windowSeq(),job.seq());long sample=job.windowSample(),sequence=job.windowSeq();
  for(var proof:source){if(proof.seq()!=sequence++||proof.start()!=sample)throw new IllegalStateException("Caption tail source gap");sample+=proof.count();}
  if(source.size()!=job.revision()||sequence!=job.seq()+1||sample!=job.samples()||!fingerprint(window).equals(row.get("source_fingerprint_sha256"))||n(row,"start_ms")!=(job.offset()+job.windowSample())/16||n(row,"end_ms")!=(job.offset()+job.samples()+15)/16)throw new IllegalStateException("Caption tail source changed");
  var execution=j.queryForList("SELECT id FROM speech_execution WHERE id=? AND service_key=? AND scope_sha256=? AND lane='REALTIME' AND path=? AND state='COMPLETED' AND response_status=200 AND response_sha256 IS NOT NULL AND settled_at IS NOT NULL AND lease_task_id=? FOR SHARE",String.class,row.get("execution_id"),ControlledSpeechProfile.SERVICE,scope(job.route().session()),PATH,job.id());
  if(!execution.equals(List.of(row.get("execution_id"))))throw new IllegalStateException("Caption tail execution proof missing");
  String speakerJson=(String)row.get("speaker_turns");
  if(speakerJson!=null)diarization=(String)row.get("diarization_execution_id");
  else speakerJson=speakerJson(new SpeechContracts.RecognitionResult(text,null),spans,window);
  emit(job,part,job.revision()+1,"stable",text,job.seq(),job.offset()+job.windowSample(),job.offset()+job.samples(),(String)row.get("source_fingerprint_sha256"),(String)row.get("execution_id"),speakerJson,speakerJson==null?null:diarization);j.update("UPDATE recording_subtitle_job SET window_start_seq=processed_seq+1,window_start_sample=processed_samples,window_revision=0,state='IDLE',lease_owner=NULL,lease_expires_at=NULL,error_code=NULL,next_run_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",job.id());
 }
 private void emit(Job job,String part,int revision,String status,String text,long seq,long start,long end,String input,String execution,String speakerJson,String diarization){
  byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length>65536||job.events()>=9232||bytes.length>TEXT_BYTES-job.textBytes()){throw new OutputBudget();}
  if(j.update("INSERT INTO recording_subtitle_event(session_id,event_no,job_id,meeting_id,part_id,segment_id,revision,status,body,text_sha256,source_fingerprint_sha256,engine_sha256,execution_id,source_final_seq,start_ms,end_ms,speaker_turns,diarization_execution_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",job.route().session(),job.events()+1,job.id(),job.route().meeting(),part,segment(job,part),revision,status,text,sha(bytes),input,profile.sha256(),execution,seq,start/16,(end+15)/16,speakerJson,diarization)!=1)throw new IllegalStateException("Caption event not committed");j.update("UPDATE recording_subtitle_job SET event_counter=event_counter+1,text_bytes=text_bytes+? WHERE id=?",bytes.length,job.id());
 }
 private final class Observer implements HttpSpeechModelPort.ExecutionObserver {
  @Override public String beforeSend(HttpSpeechModelPort.Stage stage,String requestSha){
   independent();var audio=activeAudio.get();if(audio==null||stage!=audio.expected||stage==Stage.ASR&&!sha(audio.wav).equals(requestSha))throw new IllegalStateException("Unissued realtime model input");String path=switch(stage){case ASR->PATH;case VAD->"/vad";case DIARIZATION->"/diarize";default->throw new IllegalStateException("Unexpected realtime stage");};var job=audio.window.job();var route=job.route();
   String id=tx.required(()->{
    var gate=access.lock(route.actor(),route.meeting());mutations.require(route.actor(),route.meeting(),gate);
    return recordings.withinMeeting(route.session(),route.meeting(),()->{
     held(job);if(job.epoch()!=epoch(route.meeting())||!proofs(route.session(),audio.window.part(),job.windowSeq(),audio.window.seq()).equals(audio.window.proofs()))throw new MeetingAccess.AccessDeniedException();capacity();
     var inflight=j.queryForList("SELECT id,lane FROM speech_execution WHERE service_key=? AND settled_at IS NULL ORDER BY created_at,id LIMIT 101 FOR UPDATE",ControlledSpeechProfile.SERVICE);if(inflight.size()>=profile.totalSlots()||inflight.stream().filter(r->"REALTIME".equals(r.get("lane"))).count()>=profile.realtimeSlots())throw new IllegalStateException("Realtime speech capacity unavailable");
     String execution=UUID.randomUUID().toString();j.update("INSERT INTO speech_execution(id,service_key,scope_sha256,lane,path,request_sha256,state,lease_task_id,lease_attempt,lease_owner_sha256,created_at) VALUES (?,?,?,'REALTIME',?,?,'RESERVED',?,?,?,UTC_TIMESTAMP(6))",execution,ControlledSpeechProfile.SERVICE,scope(route.session()),path,requestSha,job.id(),job.attempt(),sha(job.lease().getBytes(StandardCharsets.UTF_8)));return execution;
    });
   });admissions.put(id,new Admission(scope(route.session()),path,requestSha,job.id(),job.attempt(),sha(job.lease().getBytes(StandardCharsets.UTF_8))));if(stage==Stage.ASR)audio.execution=id;if(stage==Stage.DIARIZATION)audio.diarization=id;return id;
  }
  @Override public void completed(String id,int status,String responseSha){if(status<100||status>599||responseSha==null||!responseSha.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact model response proof required");settle(id,"COMPLETED",status,responseSha);}
  @Override public void notSent(String id){settle(id,"NOT_SENT",null,null);}
  @Override public void unknown(String id){settle(id,"UNKNOWN",null,null);}
 }
 private void settle(String id,String state,Integer status,String responseSha){
  var admission=admissions.get(id);if(admission==null)throw new IllegalArgumentException("Foreign realtime execution");tx.required(()->{
   capacity();var rows=j.queryForList("SELECT service_key,scope_sha256,path,request_sha256,lease_task_id,lease_attempt,lease_owner_sha256,settled_at FROM speech_execution WHERE id=? FOR UPDATE",id);if(rows.size()!=1)throw new IllegalStateException("Realtime execution missing");var row=rows.getFirst();
   if(!ControlledSpeechProfile.SERVICE.equals(row.get("service_key"))||!admission.scope().equals(row.get("scope_sha256"))||!admission.path().equals(row.get("path"))||!admission.requestSha().equals(row.get("request_sha256"))||!admission.job().equals(row.get("lease_task_id"))||n(row,"lease_attempt")!=admission.attempt()||!admission.ownerSha().equals(row.get("lease_owner_sha256")))throw new IllegalStateException("Realtime execution binding changed");
   if(row.get("settled_at")==null){if(state.equals("UNKNOWN"))j.update("UPDATE speech_execution SET state='UNKNOWN' WHERE id=? AND settled_at IS NULL",id);else j.update("UPDATE speech_execution SET state=?,response_status=?,response_sha256=?,settled_at=UTC_TIMESTAMP(6) WHERE id=? AND settled_at IS NULL",state,status,responseSha,id);}return null;
  });admissions.remove(id,admission);
 }
 private void capacity(){var rows=j.queryForList("SELECT total_slots,realtime_slots,offline_slots,config_revision FROM speech_capacity WHERE service_key=? FOR UPDATE",ControlledSpeechProfile.SERVICE);if(rows.size()!=1||n(rows.getFirst(),"total_slots")!=profile.totalSlots()||n(rows.getFirst(),"realtime_slots")!=profile.realtimeSlots()||n(rows.getFirst(),"offline_slots")!=profile.offlineSlots()||n(rows.getFirst(),"config_revision")!=profile.quotaRevision())throw new IllegalStateException("Realtime speech capacity installation mismatch");}
 private void defer(Window window,String session,boolean denied,String code){
  try{tx.required(()->{
   if(window!=null){var job=window.job();j.update("UPDATE recording_subtitle_job SET state=?,lease_owner=NULL,lease_expires_at=NULL,error_code=?,next_run_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND state='LEASED' AND attempt=? AND lease_owner=?",denied?"CANCELLED":code.equals("OUTPUT_BUDGET_EXCEEDED")?"DEFERRED":"DELAYED",code,job.id(),job.attempt(),job.lease());}
   else j.update("UPDATE recording_subtitle_job SET state=?,lease_owner=NULL,lease_expires_at=NULL,error_code=?,next_run_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND,updated_at=UTC_TIMESTAMP(6) WHERE session_id=? AND (state IN ('IDLE','DELAYED') OR state='LEASED' AND lease_expires_at<=UTC_TIMESTAMP(6))",denied?"CANCELLED":code.equals("OUTPUT_BUDGET_EXCEEDED")?"DEFERRED":"DELAYED",code,session);return null;
  });}catch(RuntimeException unavailable){/* The original lease expires; no event or resource success is invented. */}
 }
 /** Current READ authorization first; cursor replay is strictly read-only and session scoped. */
 public Map<String,Object> read(Actor actor,String session,String afterEventId,int limit){
  independent();ama.contract.MinutesContracts.uuid(session);if(limit<1||limit>64||afterEventId==null||!afterEventId.matches("0|[1-9][0-9]{0,18}"))throw new IllegalArgumentException("Bounded subtitle cursor required");long after;try{after=Long.parseLong(afterEventId);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid subtitle cursor");}String meeting=recordings.meetingId(session);
  return tx.required(()->{
   access.require(access.lock(actor,meeting),MeetingAccess.Action.READ);
   return recordings.withinMeeting(session,meeting,()->{
    var jobs=jobs(session,false);if(jobs.isEmpty()){if(after!=0)throw new IllegalArgumentException("Unknown subtitle cursor");return Map.of("meetingId",meeting,"sessionId",session,"events",List.of(),"nextCursor","0","hasMore",false,"delayed",false,"state","NOT_STARTED");}
    if(jobs.size()!=1||!jobs.getFirst().route().meeting().equals(meeting))throw new IllegalStateException("Subtitle job binding changed");var job=jobs.getFirst();if(after>job.events())throw new IllegalArgumentException("Unknown subtitle cursor");
    var rows=j.queryForList("SELECT event_no,meeting_id,part_id,segment_id,revision,status,IF(OCTET_LENGTH(body)<=65536,body,NULL) body,text_sha256,source_final_seq,start_ms,end_ms,speaker_turns,created_at FROM recording_subtitle_event WHERE session_id=? AND event_no>? ORDER BY event_no LIMIT ? FOR SHARE",session,after,limit);var events=new ArrayList<Map<String,Object>>();long cursor=after;
    for(var row:rows){if(n(row,"event_no")!=++cursor||!meeting.equals(row.get("meeting_id"))||!(row.get("body") instanceof String text)||!sha(text.getBytes(StandardCharsets.UTF_8)).equals(row.get("text_sha256")))throw new IllegalStateException("Subtitle replay integrity mismatch");var event=new LinkedHashMap<String,Object>();event.put("eventId",Long.toString(cursor));event.put("sessionId",session);event.put("partId",row.get("part_id"));event.put("segmentId",row.get("segment_id"));event.put("revision",n(row,"revision"));event.put("status",row.get("status"));event.put("text",text);
     if(row.get("speaker_turns") instanceof String turns){try{event.put("speakerTurns",List.of(JSON.readValue(turns,CaptionSpeakers.Turn[].class)));}catch(IOException invalid){throw new IllegalStateException("Invalid caption speaker turns");}}
     event.put("startMs",n(row,"start_ms"));event.put("endMs",n(row,"end_ms"));event.put("sourceFinalSeq",n(row,"source_final_seq"));event.put("createdAt",timestamp(row.get("created_at")));events.add(Collections.unmodifiableMap(event));}
    if(cursor>job.events()||cursor<job.events()&&events.size()<limit)throw new IllegalStateException("Subtitle cursor gap");
    // Bind all three primary-key components so even a caught-up long part starts at its cursor.
    boolean waiting=false;
    for(var part:j.queryForList("SELECT part_id,part_no FROM recording_part WHERE session_id=? AND part_no>=? ORDER BY part_no",session,job.part())){
     long afterSeq=n(part,"part_no")==job.part()?job.seq():0;
     if(!j.queryForList("""
       SELECT 1 FROM recording_chunk WHERE session_id=? AND part_id=? AND seq>?
        AND persisted_at<UTC_TIMESTAMP(6)-INTERVAL 3 SECOND LIMIT 1
       """,Integer.class,session,part.get("part_id"),afterSeq).isEmpty()){waiting=true;break;}
    }
    return Map.of("meetingId",meeting,"sessionId",session,"events",List.copyOf(events),"nextCursor",Long.toString(cursor),"hasMore",cursor<job.events(),"delayed",waiting||Set.of("DELAYED","DEFERRED").contains(job.state()),"state",job.state());
   });
  });
 }
 private Job held(Job expected){
  var rows=jobs(expected.route().session(),true);
  if(rows.size()!=1||!rows.getFirst().equals(expected))throw new LeaseChanged();
  return rows.getFirst();
 }
 private Route route(String session){var rows=j.query("SELECT session_id,meeting_id,owner_id FROM recording_subtitle_job WHERE session_id=?",(r,n)->new Route(r.getString(1),r.getString(2),r.getString(3)),session);if(rows.isEmpty())return null;if(rows.size()!=1)throw new IllegalStateException("Subtitle session ambiguous");return rows.getFirst();}
 private List<Job> jobs(String session,boolean requireLiveLease){return j.query("SELECT id,session_id,meeting_id,owner_id,cancel_epoch,part_no,processed_seq,processed_samples,part_offset_samples,window_start_seq,window_start_sample,window_revision,event_counter,text_bytes,attempt,lease_owner,state FROM recording_subtitle_job WHERE session_id=?"+(requireLiveLease?" AND state='LEASED' AND lease_expires_at>UTC_TIMESTAMP(6)":"")+" FOR UPDATE",(r,n)->new Job(r.getString(1),new Route(r.getString(2),r.getString(3),r.getString(4)),r.getLong(5),r.getInt(6),r.getLong(7),r.getLong(8),r.getLong(9),r.getLong(10),r.getLong(11),r.getInt(12),r.getLong(13),r.getLong(14),r.getLong(15),r.getString(16),r.getString(17)),session);}
 private Session session(String id){var rows=j.query("SELECT session_id,meeting_id,part_id,state FROM recording_session WHERE session_id=? FOR SHARE",(r,n)->new Session(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),id);if(rows.size()!=1)throw new IllegalArgumentException("Recording unavailable");return rows.getFirst();}
 private List<Proof> proofs(String session,String part,long first,long last){
  if(first<1||last<first||last-first>=3)throw new IllegalArgumentException("Bounded recording window required");return j.query("SELECT part_id,seq,sample_start,sample_count,sha256,bytes,pcm_storage_key FROM recording_chunk WHERE session_id=? AND part_id=? AND seq BETWEEN ? AND ? ORDER BY seq FOR SHARE",(r,n)->{var proof=new Proof(r.getString(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getString(5),r.getLong(6),r.getString(7));if(proof.count()<1||proof.count()>32000||proof.bytes()!=proof.count()*2||proof.sha()==null||!proof.sha().matches("[0-9a-f]{64}"))throw new IllegalStateException("Invalid committed recording proof");return proof;},session,part,first,last);
 }
 private long epoch(String meeting){return j.queryForObject("SELECT cancel_epoch FROM meeting WHERE id=? FOR SHARE",Long.class,meeting);}
 private static void sameRoute(Job job,Route route){if(!job.route().equals(route))throw new MeetingAccess.AccessDeniedException();}
 private void idle(Job job,String state,String code,long micros){j.update("UPDATE recording_subtitle_job SET state=?,lease_owner=NULL,lease_expires_at=NULL,error_code=?,next_run_at=TIMESTAMPADD(MICROSECOND,?,UTC_TIMESTAMP(6)),updated_at=UTC_TIMESTAMP(6) WHERE id=?",state,code,micros,job.id());}
 private static String segment(Job job,String part){return UUID.nameUUIDFromBytes(("ama.recording-segment.v1|"+job.route().session()+"|"+part+"|"+job.windowSeq()).getBytes(StandardCharsets.UTF_8)).toString();}
 private String fingerprint(Window window){var value=new StringBuilder("ama.recording-caption-source.v1|").append(window.job().route().session()).append('|').append(window.part()).append('|').append(profile.sha256());for(var proof:window.proofs())value.append('|').append(proof.seq()).append(':').append(proof.start()).append(':').append(proof.count()).append(':').append(proof.sha());return sha(value.toString().getBytes(StandardCharsets.UTF_8));}
 private static String scope(String session){return sha(("ama.recording-subtitles.v1|"+session).getBytes(StandardCharsets.UTF_8));}
 private static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static byte[] wav(byte[] pcm){var b=ByteBuffer.allocate(44+pcm.length).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+pcm.length).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);return b.array();}
 private static long n(Map<String,Object> row,String key){Object value=row.get(key);if(!(value instanceof Number number))throw new IllegalStateException("Subtitle numeric fact missing");return number.longValue();}
 private static String timestamp(Object value){if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime().toInstant(java.time.ZoneOffset.UTC).toString();if(value instanceof java.time.LocalDateTime t)return t.toInstant(java.time.ZoneOffset.UTC).toString();throw new IllegalStateException("Subtitle timestamp unavailable");}
 private static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Subtitle preparation requires an independent operation");}
 @Override public void close(){if(closed.compareAndSet(false,true)){synchronized(prefixes){prefixes.clear();}timer.shutdownNow();speakerWorkers.shutdownNow();workers.shutdownNow();model.close();try{workers.awaitTermination(5,TimeUnit.SECONDS);speakerWorkers.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}}
}
