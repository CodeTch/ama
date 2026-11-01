package ama.task;

import ama.contract.TaskContracts.TaskLease;
import ama.model.HttpSpeechModelPort;
import ama.transcript.JdbcTranscriptAuthority;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private import collaborator. Persistent admissions are shared across processes;
 * neither an elapsed client deadline nor an expired task lease settles remote work. */
final class JdbcSpeechExecutionObserver implements HttpSpeechModelPort.ExecutionObserver {
 private record Held(String path,String requestSha){}
 private final JdbcTaskQueue q;private final TaskLease lease;private final String scope;
 private final ControlledSpeechProfile profile;private final ControlledSpeechProofClient proofs;private final BooleanSupplier cancelled;
 private final Map<String,Held> held=new ConcurrentHashMap<>();
 private final java.util.concurrent.locks.ReentrantLock recoveryLock=new java.util.concurrent.locks.ReentrantLock();
 private String recoveryAfter="",recoveryUpper;
 JdbcSpeechExecutionObserver(JdbcTaskQueue q,JdbcTranscriptAuthority authority,TaskLease lease,String scope,ControlledSpeechProfile profile,ControlledSpeechProofClient proofs,BooleanSupplier cancelled){
  TranscriptImportRuntime.requireCombined(q,authority);if(!lease.spec().type().equals("TRANSCRIPT_IMPORT_V1"))throw new IllegalArgumentException("Installed import required");
  ControlledSpeechProfile.hash(scope);this.q=q;this.lease=lease;this.scope=scope;this.profile=Objects.requireNonNull(profile);this.proofs=Objects.requireNonNull(proofs);this.cancelled=Objects.requireNonNull(cancelled);
 }
 /** Recovery has no task admission capability and cannot write source checkpoints. */
 JdbcSpeechExecutionObserver(JdbcTaskQueue q,JdbcTranscriptAuthority authority,ControlledSpeechProfile profile,ControlledSpeechProofClient proofs,BooleanSupplier cancelled){
  TranscriptImportRuntime.requireCombined(q,authority);this.q=q;this.lease=null;this.scope=null;this.profile=Objects.requireNonNull(profile);this.proofs=Objects.requireNonNull(proofs);this.cancelled=Objects.requireNonNull(cancelled);
 }
 /** Explicit installed local profile; no constructor configures company capacities. */
 void configure(){q.tx.required(()->{
  q.jdbc.update("INSERT INTO speech_capacity(service_key,total_slots,realtime_slots,offline_slots,config_revision) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE service_key=service_key",ControlledSpeechProfile.SERVICE,profile.totalSlots(),profile.realtimeSlots(),profile.offlineSlots(),profile.quotaRevision());
  capacity();return null;
 });}
 private void capacity(){
  var rows=q.jdbc.queryForList("SELECT total_slots,realtime_slots,offline_slots,config_revision FROM speech_capacity WHERE service_key=? FOR UPDATE",ControlledSpeechProfile.SERVICE);
  if(rows.size()!=1)throw unavailable();var row=rows.getFirst();
  if(number(row,"total_slots")!=profile.totalSlots()||number(row,"realtime_slots")!=profile.realtimeSlots()||number(row,"offline_slots")!=profile.offlineSlots()||number(row,"config_revision")!=profile.quotaRevision())throw unavailable();
 }
 @Override public String beforeSend(HttpSpeechModelPort.Stage stage,String requestSha){
  if(lease==null)throw new IllegalStateException("Recovery cannot admit model work");
  independent();ControlledSpeechProfile.hash(requestSha);String path=path(stage);long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
  for(;;){check();String id=q.preparation(()->{
   if(q.validatedPreparationLease(lease).isEmpty())throw new CancellationException("Import lease changed");capacity();
   var active=q.jdbc.queryForList("SELECT id,lane FROM speech_execution WHERE service_key=? AND settled_at IS NULL ORDER BY created_at,id LIMIT 101 FOR UPDATE",ControlledSpeechProfile.SERVICE);
   if(active.size()>=profile.totalSlots()||active.stream().filter(r->"OFFLINE".equals(r.get("lane"))).count()>=profile.offlineSlots())return null;
   String value=UUID.randomUUID().toString();q.jdbc.update("INSERT INTO speech_execution(id,service_key,scope_sha256,lane,path,request_sha256,state,lease_task_id,lease_attempt,lease_owner_sha256,created_at) VALUES (?,?,?,'OFFLINE',?,?,'RESERVED',?,?,?,UTC_TIMESTAMP(6))",value,ControlledSpeechProfile.SERVICE,scope,path,requestSha,lease.taskId(),lease.attempt(),ownerHash(lease.leaseOwner()));return value;
  });
   if(id!=null){held.put(id,new Held(path,requestSha));return id;}
   recover(4);if(System.nanoTime()-deadline>=0)throw unavailable();
   try{Thread.sleep(100);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException("Speech admission interrupted");}
  }
 }
 @Override public void completed(String id,int status,String responseSha){ControlledSpeechProfile.hash(responseSha);if(status<100||status>599)throw unavailable();settleHeld(id,"COMPLETED",status,responseSha);}
 @Override public void notSent(String id){settleHeld(id,"NOT_SENT",null,null);}
 @Override public void unknown(String id){var actual=requireHeld(id);q.tx.required(()->{
  capacity();var row=execution(id);same(row,scope,actual.path(),actual.requestSha());
  if(row.get("settled_at")==null)q.jdbc.update("UPDATE speech_execution SET state='UNKNOWN' WHERE id=? AND settled_at IS NULL",id);return null;
 });held.remove(id);}
 private void settleHeld(String id,String state,Integer status,String sha){var actual=requireHeld(id);q.tx.required(()->{
  capacity();var row=execution(id);same(row,scope,actual.path(),actual.requestSha());settle(row,state,status,sha);return null;
 });held.remove(id);}
 private Held requireHeld(String id){var value=held.get(id);if(value==null)throw new IllegalArgumentException("Foreign speech execution");return value;}
 private Map<String,Object> execution(String id){var rows=q.jdbc.queryForList("SELECT * FROM speech_execution WHERE id=? FOR UPDATE",id);if(rows.size()!=1)throw unavailable();return rows.getFirst();}
 private static void same(Map<String,Object> row,String scope,String path,String sha){if(!ControlledSpeechProfile.SERVICE.equals(row.get("service_key"))||!scope.equals(row.get("scope_sha256"))||!path.equals(row.get("path"))||!sha.equals(row.get("request_sha256")))throw unavailable();}
 private void settle(Map<String,Object> row,String state,Integer status,String sha){if(row.get("settled_at")!=null)return;q.jdbc.update("UPDATE speech_execution SET state=?,response_status=?,response_sha256=?,settled_at=UTC_TIMESTAMP(6) WHERE id=? AND settled_at IS NULL",state,status,sha,row.get("id"));}
 int recover(int limit){
  independent();if(limit<1||limit>16)throw new IllegalArgumentException("Bounded speech recovery required");
  if(!recoveryLock.tryLock())return 0;
  try{
  if(recoveryUpper==null){var upper=q.jdbc.queryForList("SELECT id FROM speech_execution WHERE service_key=? AND settled_at IS NULL ORDER BY id DESC LIMIT 1",String.class,ControlledSpeechProfile.SERVICE);if(upper.isEmpty())return 0;recoveryUpper=upper.getFirst();recoveryAfter="";}
  var rows=q.jdbc.queryForList("SELECT id,scope_sha256,path,request_sha256,state,lease_task_id,lease_attempt,lease_owner_sha256 FROM speech_execution WHERE service_key=? AND settled_at IS NULL AND id>? AND id<=? ORDER BY id LIMIT ?",ControlledSpeechProfile.SERVICE,recoveryAfter,recoveryUpper,limit);int recovered=0;
  for(var row:rows){check();recoveryAfter=text(row,"id");try{
   String id=text(row,"id"),path=text(row,"path"),sha=text(row,"request_sha256");var observed=proofs.lookup(id);
   if(observed.isEmpty()&&("UNKNOWN".equals(row.get("state"))||!ownerCurrent(row)))observed=Optional.of(proofs.fence(id,path,sha));
   if(observed.isEmpty()||!observed.get().terminal())continue;var proof=observed.get();
   if(!proof.id().equals(id)||!proof.path().equals(path)||proof.requestSha()!=null&&!proof.requestSha().equals(sha)||proof.requestSha()==null&&!proof.state().equals("TERMINATED"))throw unavailable();
   q.tx.required(()->{capacity();var current=execution(id);same(current,text(row,"scope_sha256"),path,sha);settle(current,proof.state(),proof.status(),proof.responseSha());return null;});recovered++;
  }catch(CancellationException interrupted){throw interrupted;}catch(RuntimeException unavailable){/* Evidence remains unsettled; revisit on the next bounded pass. */}}
  if(rows.size()<limit||recoveryAfter.equals(recoveryUpper))recoveryUpper=null;return recovered;
  }finally{recoveryLock.unlock();}
 }
 private boolean ownerCurrent(Map<String,Object> row){
  var owners=q.jdbc.queryForList("SELECT lease_owner FROM durable_task WHERE id=? AND state='RUNNING' AND attempt=? AND expires_at>UTC_TIMESTAMP(6)",String.class,row.get("lease_task_id"),row.get("lease_attempt"));
  return owners.size()==1&&ownerHash(owners.getFirst()).equals(row.get("lease_owner_sha256"));
 }
 private static String ownerHash(String owner){return ControlledSpeechProfile.digest(owner.getBytes(StandardCharsets.UTF_8));}
 private static String path(HttpSpeechModelPort.Stage stage){return switch(stage){case VAD->"/vad";case DIARIZATION->"/diarize";case ASR->"/inference/asr-nano/modelserving/asr";case PUNCTUATION->"/inference/asr-punc/modelserving/punc";case EMBEDDING->"/embed";};}
 private void check(){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Speech preparation cancelled");}
 private static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Speech HTTP requires independent execution");}
 private static long number(Map<String,Object> row,String name){var value=row.get(name);if(!(value instanceof Number number))throw unavailable();return number.longValue();}
 private static String text(Map<String,Object> row,String name){var value=row.get(name);if(!(value instanceof String string)||string.isEmpty())throw unavailable();return string;}
 private static IllegalStateException unavailable(){return new IllegalStateException("Speech capacity unavailable");}
}
