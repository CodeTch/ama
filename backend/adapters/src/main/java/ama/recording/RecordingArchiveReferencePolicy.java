package ama.recording;

import ama.contract.IdentityContracts.Actor;
import ama.storage.*;
import ama.task.RecordingArchiveRuntime;
import javax.sql.DataSource;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.FileContracts.*;

/** One exact registered recording file; writes require a genuine queue WRITING invocation. */
public final class RecordingArchiveReferencePolicy implements CompleteReadPolicy {
 private final DataSource source;private final JdbcTemplate j;
 public RecordingArchiveReferencePolicy(DataSource source){this.source=source;j=new JdbcTemplate(source);j.setQueryTimeout(2);}
 private record Prepared(RecordingArchiveReferencePolicy issuer,String meeting,String archive,String file) implements ReadPreparation {}
 private final class Proof implements FileRegistry.ReferenceContext {
  final Object holder;final FileRegistry.LockedMeeting gate;final String meeting,file;final FileRegistry.Reference reference;final Map<String,Object> row;
  final FileRegistry.ReferenceContext write;boolean tasksLocked;
  Proof(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,Map<String,Object> row,FileRegistry.ReferenceContext write){holder=transaction();this.gate=gate;this.meeting=meeting;this.reference=reference;this.file=file;this.row=Collections.unmodifiableMap(new LinkedHashMap<>(row));this.write=write;}
  RecordingArchiveReferencePolicy issuer(){return RecordingArchiveReferencePolicy.this;}
 }
 private Object transaction(){var holder=TransactionSynchronizationManager.getResource(source);if(holder==null||!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Actual recording reference transaction required");return holder;}
 private Proof proof(Object value){if(!(value instanceof RecordingArchiveReferencePolicy.Proof p)||p.issuer()!=this||p.holder!=transaction())throw new NotFound();return p;}
 private Proof lock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,FileRegistry.ReferenceContext write) {
  if(!reference.kind().equals("RECORDING_ARCHIVE")||!reference.purpose().equals("RECORDING_ARCHIVE"))throw new NotFound();
  if(write!=null)RecordingArchiveRuntime.requireReferenceDeclaration(write,source,meeting,reference.ownerId(),file);
  var session=j.queryForList("SELECT session_id FROM recording_archive WHERE id=? AND meeting_id=?",String.class,reference.ownerId(),meeting);if(session.size()!=1)throw new NotFound();
  if(j.queryForList("SELECT session_id FROM recording_session WHERE session_id=? AND meeting_id=? AND state='FINISHED' FOR UPDATE",String.class,session.getFirst(),meeting).size()!=1)throw new NotFound();
  String states=write==null?"a.state='READY'":"a.state='PREPARED'";
  var rows=j.queryForList("SELECT a.id,a.file_id,a.backend_id,a.size_bytes,a.sha256,a.sample_count,a.state,a.input_id,i.producer_key,i.input_fingerprint,i.base_revision,i.cancel_epoch FROM recording_archive a JOIN recording_archive_current c ON c.session_id=a.session_id AND c.input_id=a.input_id JOIN recording_archive_input i ON i.id=a.input_id AND i.session_id=a.session_id AND i.meeting_id=a.meeting_id WHERE a.id=? AND a.meeting_id=? AND a.file_id=? AND "+states+" FOR UPDATE",reference.ownerId(),meeting,file);
  if(rows.size()!=1)throw new NotFound();return new Proof(gate,meeting,reference,file,rows.getFirst(),write);
 }
 public Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file){return lock(gate,meeting,reference,file,null);}
 public Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,FileRegistry.ReferenceContext context){
  if(context instanceof RecordingArchiveReferencePolicy.Proof){var p=proof(context);if(p.gate!=gate||!p.meeting.equals(meeting)||!p.reference.equals(reference)||!p.file.equals(file))throw new NotFound();return p;}
  return lock(gate,meeting,reference,file,context);
 }
 public void validate(Object value,String meeting,FileRegistry.Reference reference,FileRegistry.FileRecord file,String purpose) {
  var p=proof(value);if(!p.meeting.equals(meeting)||!p.reference.equals(reference)||!p.file.equals(file.fileId())||!purpose.equals("RECORDING_ARCHIVE"))throw new NotFound();var r=p.row;
  if(p.write!=null)RecordingArchiveRuntime.requireReferenceCompletion(p.write,source,meeting,reference.ownerId(),file.fileId());
  if(!file.meetingId().equals(meeting)||!file.state().equals("AVAILABLE")||!file.key().backendId().equals(r.get("backend_id"))||file.size()!=((Number)r.get("size_bytes")).longValue()||!file.sha256().equals(r.get("sha256"))||file.size()!=44+2*((Number)r.get("sample_count")).longValue())throw new NotFound();
  if(j.queryForObject("SELECT COUNT(*) FROM recording_archive a JOIN recording_archive_current c ON c.session_id=a.session_id AND c.input_id=a.input_id WHERE a.id=? AND a.input_id=? AND a.file_id=? AND a.state IN ('PREPARED','READY')",Integer.class,reference.ownerId(),r.get("input_id"),file.fileId())!=1)throw new NotFound();
 }
 public ReadPreparation prepareRead(Actor actor,String meeting,FileRegistry.ReadRequest request){
  if(!(request instanceof FileRegistry.CurrentReadRequest current)||!current.referenceKind().equals("RECORDING_ARCHIVE"))throw new NotFound();
  var ids=j.queryForList("SELECT id FROM recording_archive WHERE file_id=? AND meeting_id=? AND state='READY' LIMIT 2",String.class,current.fileId(),meeting);if(ids.size()!=1)throw new NotFound();return new Prepared(this,meeting,ids.getFirst(),current.fileId());
 }
 public void authorizeRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request){if(!(request instanceof FileRegistry.CurrentReadRequest current)||!current.referenceKind().equals("RECORDING_ARCHIVE"))throw new NotFound();}
 public Object prelockCurrent(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,ReadPreparation prepared){
  if(!(prepared instanceof Prepared p)||p.issuer()!=this||!p.meeting().equals(meeting)||!p.archive().equals(reference.ownerId())||!p.file().equals(file))throw new NotFound();return lock(gate,meeting,reference,file,null);
 }
 public ReadPlan prelockRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request,ReadPreparation prepared){
  if(!(request instanceof FileRegistry.CurrentReadRequest current)||!(prepared instanceof Prepared p))throw new NotFound();var reference=new FileRegistry.Reference("RECORDING_ARCHIVE",p.archive(),"RECORDING_ARCHIVE");var proof=(Proof)prelockCurrent(gate,meeting,reference,current.fileId(),prepared);return new ReadPlan(proof,List.of(new ReadMember(reference,current.fileId())),current.fileId());
 }
 public List<ReadMember> readMembers(Object value,FileRegistry.Reference reference,String file){var p=proof(value);if(!p.reference.equals(reference)||!p.file.equals(file))throw new NotFound();return List.of(new ReadMember(reference,file));}
 public void prelockReadTasks(List<Object> scopes){
  if(scopes.size()!=1)throw new NotFound();var p=proof(scopes.getFirst());if(p.write!=null||p.tasksLocked)throw new NotFound();var r=p.row;
  var tasks=j.queryForList("SELECT id FROM durable_task WHERE producer_key=? AND input_kind='RECORDING_ARCHIVE_INPUT' AND input_id=? AND subject_kind='MEETING' AND subject_id=? AND task_type='RECORDING_ARCHIVE_V1' AND pool='RECORDING_ARCHIVE' AND business_version=? AND cancel_epoch=? AND input_fingerprint=? AND state='SUCCEEDED' AND output_kind='RECORDING_ARCHIVE' AND output_id=? FOR UPDATE",String.class,r.get("producer_key"),r.get("input_id"),p.meeting,r.get("base_revision"),r.get("cancel_epoch"),r.get("input_fingerprint"),p.reference.ownerId());if(tasks.size()!=1)throw new NotFound();p.tasksLocked=true;
 }
 public void finishRead(Object value,List<FileRegistry.CompletedSource> sources){var p=proof(value);if(!p.tasksLocked||p.write!=null||sources.size()!=1)throw new NotFound();var actual=sources.getFirst();validate(p,p.meeting,p.reference,actual.file(),actual.originalPurpose());}
}
