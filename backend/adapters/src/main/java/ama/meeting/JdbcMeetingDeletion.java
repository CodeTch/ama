package ama.meeting;

import ama.access.MeetingAccess.AccessDeniedException;
import ama.contract.IdentityContracts.Actor;
import ama.contract.StorageContracts.*;
import ama.db.TransactionRunner;
import ama.storage.FileBackendCatalog;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Independent cleanup ledger: a deleted MEETING subject cannot authorize a normal business task.
 * The accepted immutable root/epoch, exclusive inventory and lease authorize cleanup, not the
 * initiating user's later permissions. No business content or arbitrary paths enter the API. */
public final class JdbcMeetingDeletion implements AutoCloseable {
 public record Status(String deletionTaskId,String meetingId,String state,String phase,String revisionToken,long attempts,String errorCode) {}
 private record Job(String id,String meeting,String token,String phase,int cursor,long revision,long epoch) {}
 private record Item(String kind,String id,StorageKey key,String token) {}
 private final JdbcTemplate j;private final TransactionRunner tx;private final JdbcMeetingAccess access;private final FileBackendCatalog backends;
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"meeting-deletion");t.setDaemon(true);return t;});
 private final AtomicBoolean queued=new AtomicBoolean();
 public JdbcMeetingDeletion(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,FileBackendCatalog backends){this.j=new JdbcTemplate(source);j.setQueryTimeout(30);this.tx=tx;this.access=access;this.backends=backends;}
 public void start(){executor.scheduleWithFixedDelay(this::wake,0,1,TimeUnit.SECONDS);}
 /** Coalesce acceptance notifications; periodic polling still recovers work after a restart. */
 public void wake(){
  if(!queued.compareAndSet(false,true))return;
  try{executor.execute(()->{queued.set(false);try{
   for(int n=0;n<16;n++)if(!runOnce(null))return;
   // Yield to queued work without adding a one-second pause to a busy cleanup queue.
   wake();
  }catch(RuntimeException unavailable){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Meeting cleanup deferred: "+unavailable.getClass().getSimpleName());}});}
  catch(RejectedExecutionException closed){queued.set(false);}
 }
 @Override public void close(){executor.shutdownNow();}
 public Status forMeeting(Actor actor,String meeting){return read(actor,"meeting_id",meeting);}
 public Status status(Actor actor,String id){return read(actor,"id",id);}
 private Status read(Actor actor,String column,String id){
  return tx.required(()->{access.tryLockHistoricalIdentities(actor,List.of(),false).orElseThrow(AccessDeniedException::new);
   var rows=j.query("SELECT id,meeting_id,state,phase,accepted_revision,attempt,last_error FROM meeting_deletion WHERE "+column+"=? AND requested_by=?",(r,n)->new Status(r.getString(1),r.getString(2),r.getString(3),r.getString(4),Long.toString(r.getLong(5)),r.getLong(6),r.getString(7)),id,actor.enterpriseId());
   if(rows.size()!=1)throw new AccessDeniedException();return rows.getFirst();});
 }
 /** Optional exact job is used by controlled maintenance/integration; neither route accepts it from an unauthenticated user. */
 public boolean runOnce(String exactId){
  Job job=tx.required(()->{
   String clause=exactId==null?"":" AND id=?";Object[] args=exactId==null?new Object[0]:new Object[]{exactId};
   var rows=j.queryForList("SELECT id,meeting_id,phase,purge_cursor,accepted_revision,cancel_epoch FROM meeting_deletion WHERE state<>'COMPLETE' AND available_at<=UTC_TIMESTAMP(6) AND (lease_until IS NULL OR lease_until<=UTC_TIMESTAMP(6))"+clause+" ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",args);
   if(rows.isEmpty())return null;var r=rows.getFirst();String token=UUID.randomUUID().toString();j.update("UPDATE meeting_deletion SET state='RUNNING',lease_token=?,lease_until=TIMESTAMPADD(MINUTE,10,UTC_TIMESTAMP(6)),attempt=attempt+1,last_error=NULL WHERE id=?",token,r.get("id"));
   return new Job((String)r.get("id"),(String)r.get("meeting_id"),token,(String)r.get("phase"),((Number)r.get("purge_cursor")).intValue(),((Number)r.get("accepted_revision")).longValue(),((Number)r.get("cancel_epoch")).longValue());
  });if(job==null)return false;
  try{
   if(job.phase.equals("INVENTORY"))tx.required(()->{lock(job);MeetingDeletionRows.verifyCoverage(j);if(inventory(job)==0)j.update("UPDATE meeting_deletion SET phase='STORAGE' WHERE id=?",job.id);release(job);return null;});
   else if(job.phase.equals("STORAGE"))storage(job);
   else if(job.phase.equals("PURGE"))tx.required(()->{lock(job);MeetingDeletionRows.verifyCoverage(j);if(!MeetingDeletionRows.detachPage(j,job.meeting)){
    int next=MeetingDeletionRows.purgePage(j,job.meeting,job.cursor);j.update("UPDATE meeting_deletion SET purge_cursor=? WHERE id=?",next,job.id);
    if(next==MeetingDeletionRows.ORDER.size()){
     MeetingDeletionRows.finish(j,job.meeting);
     j.update("DELETE FROM meeting_deletion_item WHERE deletion_id=?",job.id);
     j.update("UPDATE meeting_deletion SET state='COMPLETE',phase='COMPLETE',completed_at=UTC_TIMESTAMP(6),lease_token=NULL,lease_until=NULL,last_error=NULL WHERE id=?",job.id);return null;
    }
   }release(job);return null;});else throw new IllegalStateException("Unknown cleanup phase");
  }catch(RuntimeException unavailable){j.update("UPDATE meeting_deletion SET state='FAILED',last_error='CLEANUP_UNAVAILABLE',lease_token=NULL,lease_until=NULL,available_at=TIMESTAMPADD(SECOND,5,UTC_TIMESTAMP(6)) WHERE id=? AND lease_token=? AND state<>'COMPLETE'",job.id,job.token);System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Meeting cleanup retry "+job.id+": "+unavailable.getClass().getSimpleName());}
  return true;
 }
 private void lock(Job job){
  var subjects=j.queryForList("SELECT deleted,business_version,cancel_epoch FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? FOR UPDATE",job.meeting);
  var roots=j.queryForList("SELECT lifecycle,ever_published,revision,cancel_epoch FROM meeting WHERE id=? FOR UPDATE",job.meeting);
  if(subjects.size()!=1||roots.size()!=1)throw new IllegalStateException("Deleted root missing");var s=subjects.getFirst();var r=roots.getFirst();
  if(((Number)s.get("deleted")).intValue()!=1||((Number)s.get("business_version")).longValue()!=job.revision||((Number)s.get("cancel_epoch")).longValue()!=job.epoch||!"DELETED".equals(r.get("lifecycle"))||((Number)r.get("ever_published")).intValue()!=0||((Number)r.get("revision")).longValue()!=job.revision||((Number)r.get("cancel_epoch")).longValue()!=job.epoch)throw new IllegalStateException("Deletion fence changed");
  if(j.queryForList("SELECT id FROM meeting_deletion WHERE id=? AND lease_token=? AND lease_until>UTC_TIMESTAMP(6) AND state='RUNNING' FOR UPDATE",String.class,job.id,job.token).size()!=1)throw new IllegalStateException("Cleanup lease lost");
 }
 private void release(Job job){j.update("UPDATE meeting_deletion SET state='PENDING',lease_token=NULL,lease_until=NULL,available_at=UTC_TIMESTAMP(6) WHERE id=? AND lease_token=?",job.id,job.token);}
 private int inventory(Job job){
  int added=0;
  added+=inventory(job,"FILE","SELECT f.id,f.backend_id,f.namespace,f.object_key,NULL token FROM file_object f WHERE f.meeting_id=?");
  added+=inventory(job,"UPLOAD","SELECT f.id,f.backend_id,f.namespace,f.object_key,f.provider_upload_token token FROM upload_session f WHERE f.meeting_id=?");
  added+=inventory(job,"EXPORT","SELECT f.id,f.backend_id,f.namespace,f.object_key,NULL token FROM minutes_export_artifact f JOIN minutes_export_input i ON i.id=f.input_id WHERE i.meeting_id=?");
  added+=inventory(job,"PCM","SELECT f.id,f.backend_id,f.namespace,f.object_key,NULL token FROM speech_pcm_artifact f WHERE f.meeting_id=?");
  added+=inventory(job,"RECORDING","SELECT f.session_id id,f.backend_id,'recordings' namespace,f.session_id object_key,NULL token FROM recording_session f WHERE f.meeting_id=?");
  return added;
 }
 private int inventory(Job job,String kind,String select){
  // Exact accepted database metadata only. Never a location inferred from display names.
  String sourceId=kind.equals("RECORDING")?"f.session_id":"f.id";
  return j.update("INSERT INTO meeting_deletion_item(deletion_id,kind,source_id,backend_id,namespace,object_key,upload_token) SELECT ?,?,x.id,x.backend_id,x.namespace,x.object_key,x.token FROM ("+select+" AND NOT EXISTS (SELECT 1 FROM meeting_deletion_item d WHERE d.deletion_id=? AND d.kind=? AND d.source_id="+sourceId+") ORDER BY "+sourceId+" LIMIT 100) x",job.id,kind,job.meeting,job.id,kind);
 }
 private void storage(Job job){
  List<Item> items=tx.required(()->{lock(job);return j.query("SELECT kind,source_id,backend_id,namespace,object_key,upload_token FROM meeting_deletion_item WHERE deletion_id=? AND state='PENDING' ORDER BY kind,source_id LIMIT 32",(r,n)->new Item(r.getString(1),r.getString(2),new StorageKey(r.getString(3),r.getString(4),r.getString(5)),r.getString(6)),job.id);});
  var completed=new ArrayList<Item>();
  // Storage calls remain outside database transactions. A failed batch is safe to replay:
  // key retirement and upload discard are durable, idempotent operations.
  for(Item item:items){
   var backend=backends.require(item.key.backendId(),false);if(!backend.type().equals("local"))throw new IllegalStateException("Online erasure is implemented for local storage only");var storage=backend.storage();
   if(item.kind.equals("RECORDING")){
    storage.fenceRecordingSession(item.key);var page=storage.list(item.key.backendId(),"recordings",item.key.objectKey()+"/",null,128);
    for(var blob:page.items()){var key=blob.key();if(!key.backendId().equals(item.key.backendId())||!key.namespace().equals("recordings")||!key.objectKey().startsWith(item.key.objectKey()+"/"))throw new IllegalStateException("Foreign prefix result");storage.retireKey(key);}
    if(page.items().isEmpty()&&page.nextCursor()==null)completed.add(item);
   }else{
    if(item.kind.equals("UPLOAD")){
     if(item.token==null||item.token.isBlank())throw new IllegalStateException("Upload inventory incomplete");
     // discardUpload already retires the key; a second retire RPC repeats fsyncs.
     storage.discardUpload(new UploadToken(item.key.backendId(),item.token),item.key);
    }else storage.retireKey(item.key);
    completed.add(item);
   }
  }
  tx.required(()->{lock(job);if(items.isEmpty())j.update("UPDATE meeting_deletion SET phase='PURGE' WHERE id=?",job.id);
   for(Item item:completed)j.update("UPDATE meeting_deletion_item SET state='COMPLETE' WHERE deletion_id=? AND kind=? AND source_id=?",job.id,item.kind,item.id);
   release(job);return null;
  });
 }
}
