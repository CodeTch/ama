package ama.export;

import ama.contract.IdentityContracts.Actor;
import ama.storage.*;
import ama.transcript.SavedMinutesExportSource;
import javax.sql.DataSource;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.FileContracts.*;

/** Generated-file reads prove the actual current saved graph, artifact and succeeded producer. */
public final class MinutesExportReferencePolicy implements CompleteReadPolicy {
 private final DataSource source;private final JdbcTemplate j;private final Supplier<SavedMinutesExportSource> sources;
 public MinutesExportReferencePolicy(DataSource source,Supplier<SavedMinutesExportSource> sources){this.source=Objects.requireNonNull(source);this.sources=Objects.requireNonNull(sources);j=new JdbcTemplate(source);j.setQueryTimeout(2);}
 private record Prepared(MinutesExportReferencePolicy issuer,Actor actor,String meeting,String file,String artifact,String input,SavedMinutesExportSource sources,SavedMinutesExportSource.Prepared saved) implements ReadPreparation{}
 private final class Proof implements FileRegistry.ReferenceContext {
  final Object holder=transaction();final FileRegistry.LockedMeeting gate;final Prepared prepared;final FileRegistry.Reference reference;
  Map<String,Object> input,artifact;FileRegistry.AcquiredSources acquired;boolean producerLocked,finished;
  Proof(FileRegistry.LockedMeeting gate,Prepared prepared,FileRegistry.Reference reference){this.gate=gate;this.prepared=prepared;this.reference=reference;}
  MinutesExportReferencePolicy issuer(){return MinutesExportReferencePolicy.this;}
 }
 private Object transaction(){var holder=TransactionSynchronizationManager.getResource(source);if(holder==null||!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Export read transaction required");return holder;}
 private Proof proof(Object value){if(!(value instanceof MinutesExportReferencePolicy.Proof p)||p.issuer()!=this||p.holder!=transaction())throw new NotFound();return p;}
 private Prepared prepared(ReadPreparation value,String meeting){if(!(value instanceof Prepared p)||p.issuer()!=this||!p.meeting().equals(meeting))throw new NotFound();return p;}
 private static FileRegistry.CurrentReadRequest current(FileRegistry.ReadRequest request){if(!(request instanceof FileRegistry.CurrentReadRequest current)||!"MINUTES_EXPORT".equals(current.referenceKind()))throw new NotFound();return current;}
 public ReadPreparation prepareRead(Actor actor,String meeting,FileRegistry.ReadRequest request){
  var current=current(request);var rows=j.queryForList("SELECT a.id,a.input_id,i.source_revision_id,i.requested_by FROM minutes_export_artifact a JOIN minutes_export_input i ON i.id=a.input_id WHERE a.file_id=? AND i.meeting_id=? AND a.state='REGISTERED' LIMIT 2",current.fileId(),meeting);
  if(rows.size()!=1||!actor.enterpriseId().equals(rows.getFirst().get("requested_by")))throw new NotFound();var row=rows.getFirst();var saved=sources.get();saved.requireSource(source);var selected=saved.prepare(actor,meeting,(String)row.get("source_revision_id"));
  return new Prepared(this,actor,meeting,current.fileId(),(String)row.get("id"),(String)row.get("input_id"),saved,selected);
 }
 public FileRegistry.LockedMeeting lockReadRoot(FileRegistry registry,Actor actor,String meeting,ReadPreparation value){var p=prepared(value,meeting);if(!p.actor().enterpriseId().equals(actor.enterpriseId()))throw new NotFound();return p.sources().lockRoot(p.saved(),actor,meeting);}
 public void authorizeRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request){current(request);}
 public Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file){throw new NotFound();}
 public Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,FileRegistry.ReferenceContext context){var p=proof(context);if(p.gate!=gate||!p.reference.equals(reference)||!p.prepared.meeting().equals(meeting)||!p.prepared.file().equals(file))throw new NotFound();return p;}
 public Object prelockCurrent(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,ReadPreparation value){
  var prepared=prepared(value,meeting);if(!prepared.file().equals(file)||!reference.equals(new FileRegistry.Reference("MINUTES_EXPORT",prepared.artifact(),"MINUTES_EXPORT")))throw new NotFound();var p=new Proof(gate,prepared,reference);
  p.acquired=prepared.sources().recheckRead(prepared.saved(),gate,List.of(file),()->lockControls(p),()->lockProducer(p));return p;
 }
 private void lockControls(Proof p){
  var requested=p.prepared;var inputs=j.queryForList("SELECT * FROM minutes_export_input WHERE id=? AND meeting_id=? FOR UPDATE",requested.input(),requested.meeting());if(inputs.size()!=1)throw new NotFound();p.input=Collections.unmodifiableMap(new LinkedHashMap<>(inputs.getFirst()));
  var artifacts=j.queryForList("SELECT * FROM minutes_export_artifact WHERE id=? AND input_id=? AND file_id=? AND state='REGISTERED' FOR UPDATE",requested.artifact(),requested.input(),requested.file());if(artifacts.size()!=1)throw new NotFound();p.artifact=Collections.unmodifiableMap(new LinkedHashMap<>(artifacts.getFirst()));
  var in=p.input;var saved=requested.saved();
  if(!requested.actor().enterpriseId().equals(in.get("requested_by"))||!saved.meetingId().equals(in.get("meeting_id"))||!saved.sourceRevision().equals(in.get("source_revision_id"))||!saved.sourceHash().equals(in.get("source_hash"))||saved.meetingRevision()!=number(in,"base_revision")||saved.accessRevision()!=number(in,"access_revision")||saved.cancelEpoch()!=number(in,"cancel_epoch")||!Objects.equals(saved.finalConfirmation(),in.get("final_confirmation_id"))||!in.get("backend_id").equals(p.artifact.get("backend_id")))throw new NotFound();
 }
 private void lockProducer(Proof p){
  var in=p.input;var a=p.artifact;
  var rows=j.queryForList("SELECT id FROM durable_task WHERE id=? AND task_type='MINUTES_EXPORT_V1' AND pool='MINUTES_EXPORT' AND subject_kind='MEETING' AND subject_id=? AND input_kind='MINUTES_EXPORT_INPUT' AND input_id=? AND producer_key=? AND input_fingerprint=? AND business_version=? AND cancel_epoch=? AND max_attempts=3 AND state='SUCCEEDED' AND output_kind='MINUTES_EXPORT' AND output_id=? AND attempt=? FOR UPDATE",String.class,a.get("task_id"),in.get("meeting_id"),in.get("id"),in.get("producer_key"),in.get("input_fingerprint"),in.get("base_revision"),in.get("cancel_epoch"),a.get("id"),a.get("attempt"));
  if(rows.size()!=1)throw new NotFound();p.producerLocked=true;
 }
 public ReadPlan prelockRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request,ReadPreparation value){var c=current(request);var prepared=prepared(value,meeting);var reference=new FileRegistry.Reference("MINUTES_EXPORT",prepared.artifact(),"MINUTES_EXPORT");var proof=(Proof)prelockCurrent(gate,meeting,reference,c.fileId(),prepared);return new ReadPlan(proof,List.of(new ReadMember(reference,c.fileId())),c.fileId());}
 public List<ReadMember> readMembers(Object value,FileRegistry.Reference reference,String file){var p=proof(value);if(!p.reference.equals(reference)||!p.prepared.file().equals(file))throw new NotFound();return List.of(new ReadMember(reference,file));}
 public Optional<FileRegistry.AcquiredSources> prelockedReadSources(List<Object> scopes){if(scopes.size()!=1)throw new NotFound();return Optional.of(Objects.requireNonNull(proof(scopes.getFirst()).acquired));}
 public void validate(Object value,String meeting,FileRegistry.Reference reference,FileRegistry.FileRecord file,String purpose){
  var p=proof(value);var a=p.artifact;
  if(!p.prepared.meeting().equals(meeting)||!p.reference.equals(reference)||!p.prepared.file().equals(file.fileId())||!"MINUTES_EXPORT".equals(purpose)||!"AVAILABLE".equals(file.state())||!file.meetingId().equals(meeting)||!file.key().backendId().equals(a.get("backend_id"))||!file.key().namespace().equals(a.get("namespace"))||!file.key().objectKey().equals(a.get("object_key"))||file.size()!=number(a,"size_bytes")||!file.sha256().equals(a.get("sha256")))throw new NotFound();
  if(j.queryForObject("SELECT COUNT(*) FROM generated_file_origin WHERE file_id=? AND meeting_id=? AND origin_kind='MINUTES_EXPORT' AND origin_id=? AND task_id=? AND purpose='MINUTES_EXPORT' AND cancel_epoch=?",Integer.class,file.fileId(),meeting,a.get("id"),a.get("task_id"),p.input.get("cancel_epoch"))!=1)throw new NotFound();
 }
 public void prelockReadTasks(List<Object> scopes){
  if(scopes.size()!=1)throw new NotFound();var p=proof(scopes.getFirst());if(!p.producerLocked||p.finished)throw new NotFound();
  // Producer was locked before source task attempts; only its exact already-declared attempt follows.
  if(j.queryForList("SELECT task_id FROM task_attempt WHERE task_id=? AND attempt=? AND ended_at IS NOT NULL AND outcome='SUCCEEDED' FOR UPDATE",String.class,p.artifact.get("task_id"),p.artifact.get("attempt")).size()!=1)throw new NotFound();p.finished=true;
 }
 public void finishRead(Object value,List<FileRegistry.CompletedSource> actual){var p=proof(value);if(!p.finished||actual.size()!=1)throw new NotFound();var file=actual.getFirst();validate(p,p.prepared.meeting(),p.reference,file.file(),file.originalPurpose());}
 private static long number(Map<String,Object> row,String name){var value=row.get(name);if(!(value instanceof Number number)||number.longValue()<0)throw new NotFound();return number.longValue();}
}
