package ama.task;
import ama.access.MeetingAccess;
import ama.meeting.JdbcMeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import ama.task.ExternalOperationPort.*;
import ama.db.TransactionRunner;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled private policy only: production generation consumer remains uninstalled. */
class Task07GenerationRetentionIT {
 final DataSource source=Task07DataSource.create();final JdbcTemplate j=new JdbcTemplate(source);
 final TransactionRunner tx=new TransactionRunner(source);final JdbcSubjectFences fences=new JdbcSubjectFences(source,Set.of("MEETING"));
 final JdbcMeetingAccess access=new JdbcMeetingAccess(source,tx,fences);
 final String owner="t07-gen-"+id(),meeting=id(),input=id(),batch=id(),processing=id(),transcript=id(),confirmation=id(),template=id(),templateRevision=id(),exclusion=id(),file=id(),segment=id();
 final String candidate=id(),body=id(),set=id(),element=id(),locator=id(),evidence=id(),background=id(),detection=id();
 final Actor actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
 final TaskSpec spec=new TaskSpec("MINUTES_GENERATE_V1","T07_GEN_"+id(),new SubjectRef("MEETING",meeting),hash("producer"+input),new TypedRef("GENERATION_INPUT",input),hash(input),0,0,0,3);
 final PreparedRequest request=new PreparedRequest(){};final PreparedObservation observation=new PreparedObservation(){};
 final Policy policy=new Policy();final JdbcOutbox outbox=new JdbcOutbox(source,Map.of("GENERATION_INPUT",e->{if(!e.reference().id().equals(input))throw new IllegalArgumentException();}));
 final JdbcTaskQueue queue=new JdbcTaskQueue(source,fences,outbox,Map.of(spec.type(),policy));final JdbcExternalOperations operations=new JdbcExternalOperations(queue);
 TaskLease lease;OperationToken token;int writes;
 final class Policy implements GenerationRetentionPolicy {
  final JdbcTaskPreparations preparations=new JdbcTaskPreparations(source);final JdbcGenerationRetention retention=new JdbcGenerationRetention(source,preparations,access);
  final class Capture {final MeetingAccess.LockedIdentities identities;MeetingAccess.Locked root;Capture(MeetingAccess.LockedIdentities identities){this.identities=identities;}}
  public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){return access.tryLockIdentities(actor,List.of(),skip).map(ids->preparations.issue(specs,purpose,new Capture(ids),true));}
  public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){preparations.require(specs,purpose,held);}
  public boolean permitsDomainEffects(PreparedDomain held){return preparations.permitsEffects(held);}
  public boolean lockRoot(TaskSpec s,boolean skip){throw new IllegalStateException("Authentic preparation required");}
  public boolean lockRoot(TaskSpec s,boolean skip,PreparedDomain held){
   var c=(Capture)preparations.value(held);var root=access.lockPrepared(c.identities,meeting,skip);if(root.isEmpty())return false;c.root=root.orElseThrow();access.requirePrepared(c.root,actor,meeting,List.of(),MeetingAccess.Action.EDIT);
   // Complete controlled input graph, bounded singleton roots/children, all before durable tasks.
   for(var pair:List.of(new String[]{"input_batch",batch},new String[]{"transcription_input",processing},new String[]{"transcript_revision",transcript},new String[]{"transcript_confirmation",confirmation},new String[]{"minutes_template",template},new String[]{"template_revision",templateRevision},new String[]{"exclusion_revision",exclusion},new String[]{"generation_input",input}))
    if(j.queryForList("SELECT id FROM "+pair[0]+" WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),pair[1]).size()!=1)return false;
   if(j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? AND segment_id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),transcript,segment).size()!=1)return false;
   if(j.queryForList("SELECT id FROM file_object WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),file).size()!=1)return false;
   j.queryForList("SELECT id FROM generation_candidate WHERE task_id=? FOR UPDATE",lease==null?"":lease.taskId());
   return true;
  }
  public void validateInput(TaskSpec s){
   if(!s.equals(spec)||j.queryForObject("SELECT COUNT(*) FROM generation_input g JOIN transcript_confirmation c ON c.id=g.transcript_confirmation_id JOIN transcript_revision r ON r.id=c.revision_id JOIN transcription_input p ON p.id=r.processing_input_id WHERE g.id=? AND g.meeting_id=? AND g.base_revision=0 AND g.input_fingerprint=? AND c.meeting_id=g.meeting_id AND c.confirmed_by=? AND r.meeting_id=g.meeting_id AND r.kind='WORKING' AND p.meeting_id=g.meeting_id AND p.kind='IMPORT' AND p.material_mode='EMPTY'",Integer.class,input,meeting,s.inputFingerprint(),owner)!=1)throw new IllegalArgumentException("Original fixed input provenance");
  }
  public void validateOutput(TaskSpec s,TaskResult result){assertEquals("GENERATION_CANDIDATE",result.output().kind());validateGraph("PENDING");}
  public void validateExternalRequest(TaskSpec s,ExternalRequest r){if(!r.requestManifest().equals("task07-generation:"+input))throw new IllegalArgumentException();}
  public ExternalRequest requireRequest(TaskSpec s,PreparedRequest prepared){if(prepared!=request||!s.equals(spec))throw new IllegalArgumentException();return new ExternalRequest("T07_GENERATE",s.producerKey(),hash("logical"+input),hash("wire"+input),"task07-generation:"+input);}
  public void writeMarked(PreparedDomain held,ExecutionContext c,PreparedRequest r){preparations.requireExternal(held,c);}
  public ExternalObservation verifyRemoteFact(TaskSpec s,ExecutionContext c,PreparedObservation o){if(o!=observation||!s.equals(spec))throw new IllegalArgumentException();return new ExternalObservation(ExternalOutcome.SUCCEEDED,"T07_IMMUTABLE:"+input,null,null,null);}
  public TaskResult writeCurrent(PreparedDomain held,ExecutionContext c,PreparedObservation o){preparations.requireExternal(held,c);return new TaskResult(Outcome.SUCCEEDED,writeGraph("PENDING"),null);}
  public void validateStoredResult(PreparedDomain held,ExecutionContext c,PreparedObservation o,TaskResult r){preparations.requireExternal(held,c);validateGraph("PENDING");}
  public Optional<Retention> prepareRetention(PreparedDomain held,ExecutionContext c,PreparedObservation o){if(o!=observation)throw new IllegalArgumentException();return retention.prepare(held,c,((Capture)preparations.value(held)).root,actor);}
  public TypedRef retainStale(Retention r,PreparedObservation o){retention.require(r);return writeGraph("STALE");}
  public void validateRetained(Retention r,PreparedObservation o,TypedRef ref){retention.require(r);assertEquals(new TypedRef("GENERATION_CANDIDATE",candidate),ref);validateGraph("STALE");}
 }
 static String id(){return UUID.randomUUID().toString();}static String hash(String s){return Task08InheritedFixture.hash(s);}
 @BeforeEach void setup(){tx.required(()->{
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);fences.create(spec.subject(),0,0);
  j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);
  j.update("INSERT INTO storage_backend VALUES (?,'local','READ_ONLY',1)",file);
  j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','T07',?,'source',6,?,'AVAILABLE',UTC_TIMESTAMP(6))",file,meeting,file,file,hash("source"));
  j.update("INSERT INTO input_batch VALUES (?,?,'TRANSCRIPT',?,0,UTC_TIMESTAMP(6))",batch,meeting,hash(batch));j.update("INSERT INTO input_batch_file VALUES (?,1,?,?)",batch,file,hash("source"));
  j.update("INSERT INTO transcription_input(id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY','T07_FIXED','T07_FIXED',1,'T07_CONTROLLED_ORIGINAL',?,0,0,UTC_TIMESTAMP(6))",processing,meeting,batch,hash(processing));
  j.update("INSERT INTO transcript_revision(id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id) VALUES (?,?,0,'WORKING',?,?,?,UTC_TIMESTAMP(6),?)",transcript,meeting,batch,hash(processing),owner,processing);
  j.update("INSERT INTO transcript_segment(revision_id,segment_id,ordinal,body,source_file_id,source_paragraph) VALUES (?,?,1,'source',?,1)",transcript,segment,file);
  j.update("INSERT INTO transcript_confirmation VALUES (?,?,?,?,?,UTC_TIMESTAMP(6))",confirmation,meeting,transcript,hash("source"),owner);
  j.update("INSERT INTO minutes_template VALUES (?,?, 'Task7','task7',?,?,0,UTC_TIMESTAMP(6))",template,owner,hash(template),templateRevision);
  j.update("INSERT INTO template_revision VALUES (?,?,0,?,'Task7','theme',1,UTC_TIMESTAMP(6))",templateRevision,template,owner);
  j.update("INSERT INTO exclusion_revision VALUES (?,?,0,UTC_TIMESTAMP(6))",exclusion,meeting);
  j.update("INSERT INTO generation_input VALUES (?,?,?,NULL,?,?,NULL,'T07_FIXED','T07_FIXED',0,?,'FULL',NULL)",input,meeting,confirmation,templateRevision,exclusion,spec.inputFingerprint());return null;
 });queue.enqueue(spec);lease=queue.claim(spec.pool(),"T07_GENERATION").orElseThrow();token=operations.beginPrepared(lease,request).orElseThrow();}
 TypedRef writeGraph(String state){writes++;
  j.update("INSERT INTO element_set VALUES (?,?,0,UTC_TIMESTAMP(6))",set,meeting);
  j.update("INSERT INTO minutes_revision VALUES (?,?,0,NULL,'Generated','AI','complete body',?,?,UTC_TIMESTAMP(6))",body,meeting,input,owner);
  j.update("INSERT INTO minutes_element VALUES (?,?,'ACTION',1,'complete action','SUPPORTED')",element,set);
  j.update("INSERT INTO action_suggestion VALUES (?,?,'unaccepted',NULL)",element,input);
  j.update("INSERT INTO source_locator(id,meeting_id,kind,transcript_revision_id,segment_id) VALUES (?,?,'TRANSCRIPT',?,?)",locator,meeting,transcript,segment);
  j.update("INSERT INTO evidence_link VALUES (?,?,?,NULL,'SUPPORTED','WHOLE')",evidence,locator,element);
  j.update("INSERT INTO sensitive_detection VALUES (?,?,?,'INTERNAL_BUSINESS_DATA',1)",detection,set,input);j.update("INSERT INTO sensitive_detection_source VALUES (?,?)",detection,locator);
  j.update("INSERT INTO background_note VALUES (?,?,?,1,'complete background')",background,set,input);j.update("INSERT INTO background_note_source VALUES (?,?)",background,locator);
  j.update("INSERT INTO generation_candidate VALUES (?,?,?,?,?,?,0,?,?,UTC_TIMESTAMP(6),NULL)",candidate,meeting,input,lease.taskId(),body,set,spec.inputFingerprint(),state);
  return new TypedRef("GENERATION_CANDIDATE",candidate);
 }
 void validateGraph(String state){
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM generation_candidate c JOIN minutes_revision m ON m.id=c.minutes_revision_id JOIN element_set s ON s.id=c.element_set_id JOIN minutes_element e ON e.set_id=s.id JOIN action_suggestion a ON a.element_id=e.id JOIN evidence_link v ON v.element_id=e.id JOIN source_locator l ON l.id=v.locator_id JOIN sensitive_detection d ON d.set_id=s.id JOIN sensitive_detection_source ds ON ds.detection_id=d.id AND ds.locator_id=l.id JOIN background_note b ON b.set_id=s.id JOIN background_note_source bs ON bs.note_id=b.id AND bs.locator_id=l.id WHERE c.id=? AND c.state=? AND c.task_id=? AND c.input_id=? AND c.base_revision=0 AND c.input_fingerprint=? AND m.generation_input_id=c.input_id AND m.meeting_id=c.meeting_id AND s.meeting_id=c.meeting_id AND a.input_id=c.input_id AND d.input_id=c.input_id AND b.input_id=c.input_id AND l.transcript_revision_id=? AND l.segment_id=?",Integer.class,candidate,state,lease.taskId(),input,spec.inputFingerprint(),transcript,segment));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM action_item WHERE element_id=?",Integer.class,element));
 }
 void advance(){tx.required(()->{var f=fences.lock(spec.subject(),false).orElseThrow();j.update("UPDATE meeting SET revision=1 WHERE id=?",meeting);fences.advance(f,1,0,false);return null;});}
 @Test void businessOnlyStaleRetainsCompletePrivateGraphWithoutChangingRoot(){
  advance();var root=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);var subject=j.queryForMap("SELECT * FROM task_subject WHERE subject_id=?",meeting);
  assertEquals(ObservationResult.RECORDED_FENCED,operations.observePrepared(token,observation));
  assertEquals(1,writes,"Dedicated retention writer must run");validateGraph("STALE");
  assertEquals(root,j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));assertEquals(subject,j.queryForMap("SELECT * FROM task_subject WHERE subject_id=?",meeting));
  assertEquals("STALE",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));assertNull(j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,lease.taskId()));
 }
 @Test void fabricatedRetentionCannotInvokeTheRegisteredWriterDirectly(){
  assertThrows(IllegalArgumentException.class,()->tx.required(()->policy.retainStale(new GenerationRetentionPolicy.Retention(){},observation)));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Integer.class,lease.taskId()));
 }

 @Test void cancellationDoesNotBecomeRetentionAuthority(){advance();tx.required(()->{var h=fences.lock(spec.subject(),false).orElseThrow();j.update("UPDATE meeting SET cancel_epoch=1 WHERE id=?",meeting);fences.advance(h,1,1,false);return null;});assertEquals(ObservationResult.RECORDED_FENCED,operations.observePrepared(token,observation));assertEquals(0,writes);}
 @Test void publicationFreezeDeniesRetention(){
  advance();String info=id(),roster=id(),finalId=id(),publishedBody=id(),publishedSet=id();
  j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,created_by,created_at) VALUES (?,?,1,?,UTC_TIMESTAMP(6))",info,meeting,owner);
  j.update("INSERT INTO roster_revision VALUES (?,?,1,'CONFIRMED',UTC_TIMESTAMP(6),?)",roster,meeting,owner);
  j.update("INSERT INTO element_set VALUES (?,?,1,UTC_TIMESTAMP(6))",publishedSet,meeting);
  j.update("INSERT INTO minutes_revision VALUES (?,?,1,NULL,'Final','HUMAN','final',NULL,?,UTC_TIMESTAMP(6))",publishedBody,meeting,owner);
  j.update("INSERT INTO final_confirmation VALUES (?,?,1,?,?,?,?,NULL,?,?,?,?,?,?,UTC_TIMESTAMP(6))",finalId,meeting,info,roster,confirmation,publishedBody,publishedSet,templateRevision,exclusion,hash("manifest"),spec.inputFingerprint(),owner);
  j.update("INSERT INTO publication VALUES (?,?,?,?,?,?,'RUNNING',1,UTC_TIMESTAMP(6),NULL)",id(),meeting,finalId,"t07-publication:"+meeting,"t07-library","t07-folder");
  assertEquals(ObservationResult.RECORDED_FENCED,operations.observePrepared(token,observation));assertEquals(0,writes);
 }
 @Test void everPublishedDeniesRetention(){advance();j.update("UPDATE meeting SET ever_published=1 WHERE id=?",meeting);assertEquals(ObservationResult.RECORDED_FENCED,operations.observePrepared(token,observation));assertEquals(0,writes);}
 @Test void revokedOwnerNeverWritesCandidate(){advance();j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);assertThrows(MeetingAccess.AccessDeniedException.class,()->operations.observePrepared(token,observation));assertEquals(0,writes);}
 @Test void changedOwnerNeverReattributesTheOriginalGeneration(){advance();String other="t07-other-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",other,other);j.update("UPDATE meeting SET owner_id=? WHERE id=?",other,meeting);assertThrows(MeetingAccess.AccessDeniedException.class,()->operations.observePrepared(token,observation));assertEquals(0,writes);}
 @Test void receiptOnlyCannotRetainEvenBusinessOnlyStale(){advance();assertEquals(ObservationResult.RECORDED_FENCED,operations.observePreparedFact(token,observation));assertEquals(0,writes);}
 @Test void currentCompleteCandidateIsPendingAndTaskSuccessIsNotAdoption(){assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,observation));validateGraph("PENDING");assertEquals(1,writes);assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=? AND state='ADOPTED'",Integer.class,lease.taskId()));}
 @Test void rootRaceBeforeObservationProducesOnlyStaleImmutableGraph()throws Exception{
  var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{var finalization=executor.submit(()->tx.required(()->{var h=fences.lock(spec.subject(),false).orElseThrow();j.queryForMap("SELECT id FROM meeting WHERE id=? FOR UPDATE",meeting);locked.countDown();Task07ExternalWorkerIT.await(release);j.update("UPDATE meeting SET revision=1 WHERE id=?",meeting);fences.advance(h,1,0,false);return null;}));Task07ExternalWorkerIT.await(locked);
   var response=executor.submit(()->operations.observePrepared(token,observation));release.countDown();finalization.get(5,java.util.concurrent.TimeUnit.SECONDS);assertEquals(ObservationResult.RECORDED_FENCED,response.get(5,java.util.concurrent.TimeUnit.SECONDS));validateGraph("STALE");assertEquals(1L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));
  }finally{release.countDown();executor.shutdownNow();}
 }

}
