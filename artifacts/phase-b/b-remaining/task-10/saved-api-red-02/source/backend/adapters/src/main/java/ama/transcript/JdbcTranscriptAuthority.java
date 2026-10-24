package ama.transcript;

import ama.access.MeetingAccess;
import ama.canonical.*;
import ama.config.*;
import ama.config.json.*;
import ama.contract.IdentityContracts.Actor;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.db.TransactionRunner;
import ama.storage.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** One private issuer for first bind and source provenance. No detached value grants authority. */
public final class JdbcTranscriptAuthority {
 public static final String REFERENCE="INPUT_BATCH_FILE",PURPOSE="TRANSCRIPT_INPUT";
 private final DataSource source;private final JdbcTemplate j;private final TransactionRunner tx;
 private final MeetingAccess access;private final ArchiveReadBudget budget;private final CanonicalBudget canonical;
 private FileRegistry registry;
 private FileBackendCatalog savedBackends;
 private FilePurposePolicy purposes;
 private final ImportTaskDomainPolicy taskPolicy;private final MinutesTaskDomainPolicy minutesTaskPolicy;
 private final InputBatchFileReferencePolicy policy;
 private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
 public interface ReservedIntake extends FileRegistry.ReferenceContext {}
 public interface HeldProcessingFacts {}
 private enum Phase { RESERVED, INSERTED, BOUND, FAILED }
 private final class Reservation implements ReservedIntake {
  final Object holder=transaction();final FileRegistry.LockedMeeting gate;final PreparedIntake prepared;
  Phase phase=Phase.RESERVED;Held held;
  Reservation(FileRegistry.LockedMeeting gate,PreparedIntake prepared){this.gate=gate;this.prepared=prepared;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 private final class Held implements HeldProcessingFacts {
  final Reservation reserved;final StoredProvenanceFacts facts;
  Held(Reservation reserved,StoredProvenanceFacts facts){this.reserved=reserved;this.facts=facts;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 public static final class PreparedIntake {
  private final JdbcTranscriptAuthority issuer;private final ProcessingIntakeFacts.Draft draft;
  private final String batch,input,manifest,batchHash,processingVersion,parserVersion,time;private final TaskSpec spec;
  private final StoredProvenanceFacts expected;
  private PreparedIntake(JdbcTranscriptAuthority issuer,ProcessingIntakeFacts.Draft draft,String batch,String input,String manifest,String batchHash,String processingVersion,String parserVersion,String time,TaskSpec spec,StoredProvenanceFacts expected){
   this.issuer=issuer;this.draft=draft;this.batch=batch;this.input=input;this.manifest=manifest;this.batchHash=batchHash;this.processingVersion=processingVersion;this.parserVersion=parserVersion;this.time=time;this.spec=spec;this.expected=expected;
  }
  public String batchId(){return batch;}public String inputId(){return input;}public TaskSpec spec(){return spec;}
 }
 public record Components(FileRegistry registry,JdbcTranscriptAuthority authority,ProcessingIntakeFacts intake,ProcessingSourceReader sources) {}
 private JdbcTranscriptAuthority(DataSource source,TransactionRunner tx,MeetingAccess access,ArchiveReadBudget budget,CanonicalBudget canonical){
  this.source=Objects.requireNonNull(source);j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=Objects.requireNonNull(tx);this.access=Objects.requireNonNull(access);this.budget=Objects.requireNonNull(budget);this.canonical=Objects.requireNonNull(canonical);policy=new InputBatchFileReferencePolicy(this);taskPolicy=new ImportTaskDomainPolicy(this,source);minutesTaskPolicy=new MinutesTaskDomainPolicy(this,source);
 }
 MeetingAccess savedAccess(){return access;}
 public static Components create(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,
         FileBackendCatalog backends,Map<String,FileReferencePolicy> legacy,ArchiveReadBudget budget,CanonicalBudget canonical){
  if(legacy.containsKey(REFERENCE))throw new IllegalArgumentException("Production transcript authority owns its complete policy registration");
  var authority=new JdbcTranscriptAuthority(source,tx,access,budget,canonical);authority.purposes=Objects.requireNonNull(purposes);authority.savedBackends=Objects.requireNonNull(backends);var policies=new HashMap<>(legacy);policies.put(REFERENCE,authority.policy);
  var registry=new JdbcFileRegistry(source,tx,access,purposes,backends,policies,budget);authority.attach(registry);
  return new Components(registry,authority,new JdbcProcessingIntakeFacts(authority),new JdbcProcessingSourceReader(authority));
 }
 private void attach(FileRegistry registry){if(this.registry!=null)throw new IllegalStateException("Duplicate authority attachment");this.registry=Objects.requireNonNull(registry);}
 ProcessingIntakeFacts.Draft readDraft(Actor actor,String meeting,List<String> files,BooleanSupplier cancelled){
  independent();ids(files);check(cancelled);
  var draft=tx.required(()->{
   var gate=registry.lock(actor,meeting,MeetingAccess.Action.EDIT);var root=root(meeting);writable(root);
   var held=registry.prelockSources(gate,files,false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));
   var actual=registry.sources(gate,held,files);var byId=new HashMap<String,FileRegistry.CompletedSource>();
   for(var file:actual){check(cancelled);require(PURPOSE.equals(file.originalPurpose()),INPUT_INVALID);byId.put(file.file().fileId(),file);}
   var ordered=new ArrayList<FileRegistry.CompletedSource>();for(String file:files){check(cancelled);ordered.add(byId.get(file));}
   return new ProcessingIntakeFacts.Draft(actor,meeting,number(root,"revision"),number(root,"cancel_epoch"),ordered);
  });check(cancelled);return draft;
 }
 public PreparedIntake prepare(ProcessingIntakeFacts.Draft draft,String batch,String input,ConfigArchiveSource.Snapshot archives,Ref processing,BooleanSupplier cancelled){
  independent();Objects.requireNonNull(draft);Objects.requireNonNull(archives);check(cancelled);uuid(batch);uuid(input);uuid(draft.meetingId());
  require(!draft.orderedSources().isEmpty()&&draft.orderedSources().size()<=budget.maxBatchFiles(),BUDGET_EXCEEDED);
  var fileIds=draft.orderedSources().stream().map(s->s.file().fileId()).toList();ids(fileIds);
  require(draft.revision()>=0&&draft.revision()<Long.MAX_VALUE-1&&draft.cancelEpoch()>=0,INPUT_INVALID);
  long target=draft.revision()+1;String time=TIME.format(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
  var members=new ArrayList<BatchFingerprint.Member>();var files=new ArrayList<ContentFile>();long ordinal=1;
  for(var s:draft.orderedSources()){
   check(cancelled);require(PURPOSE.equals(s.originalPurpose())&&draft.meetingId().equals(s.file().meetingId())&&s.uploadCancelEpoch()==draft.cancelEpoch(),INPUT_INVALID);
   members.add(new BatchFingerprint.Member(ordinal++,s.file().fileId(),s.file().sha256(),s.file().size()));files.add(new ContentFile(s.file().fileId(),s.file().sha256(),s.file().size(),draft.meetingId()));
  }
  String batchHash=new BatchFingerprint().compute(draft.meetingId(),batch,"TRANSCRIPT",target,members,canonical,cancelled);
  // The actual pinned snapshot supplies the exact original closure; no latest/default substitution.
  var raw=archives.loadClosure(List.of(processing),budget,0,cancelled);
  var refs=new EnumMap<ArtifactType,Ref>(ArtifactType.class);
  for(var a:raw){check(cancelled);String hash=ArchiveBytes.capture(a.bytes(),budget.maxArtifactBytes(),budget.maxTotalBytes(),cancelled).sha256(cancelled);require(refs.put(a.claimedArtifactType(),new Ref(a.claimedArtifactType(),a.claimedVersion(),hash))==null,PROFILE_UNAVAILABLE);}
  require(processing.equals(refs.get(ArtifactType.PROCESSING_CONFIG)),PROFILE_UNAVAILABLE);
  Ref parser=Objects.requireNonNull(refs.get(ArtifactType.PARSER_CONFIG));
  var ordered=new ArrayList<CanonicalValue>();for(var m:members)ordered.add(obj("ordinal",m.ordinal(),"fileId",m.fileId(),"sha256",m.sha256(),"sizeBytes",m.sizeBytes()));
  var manifestValue=obj("schemaVersion",1,"profile","ama.transcription-input.v1","meetingId",draft.meetingId(),"inputId",input,"kind","IMPORT",
   "batch",obj("id",batch,"kind","TRANSCRIPT","files",new Sequence(ordered)),"sourceRevisionId",null,"materials",obj("mode","EMPTY","items",list()),
   "configs",obj("processing",wire(processing),"parser",wire(parser),"model",null,"prompt",null,"profiles",list(obj("kind","CANONICAL_CODEC","ref",wire(refs.get(ArtifactType.CANONICAL_CODEC))),obj("kind","CONFIRMATION_HASH","ref",wire(refs.get(ArtifactType.CONFIRMATION_HASH))))),
   "base",obj("meetingRevision",target,"subjectBusinessVersion",target,"cancelEpoch",draft.cancelEpoch()),
   "steps",list(obj("ordinal",1,"kind","IMPORT_TXT","parentInput",null,"priorOutput",null,"source",obj("kind","INPUT_BATCH","id",batch),"config",wire(processing),"parser",wire(parser),"model",null,"prompt",null)));
  byte[] bytes=CanonicalJson.encode(manifestValue,budget.maxManifestBytes(),cancelled);String manifest=new String(bytes,StandardCharsets.UTF_8);
  var rows=new ArrayList<FullRow>();rows.add(row(Table.INPUT_BATCH,batch,draft.meetingId(),"TRANSCRIPT",batchHash,target,time));
  for(var m:members)rows.add(row(Table.INPUT_BATCH_FILE,batch,m.ordinal(),m.fileId(),m.sha256()));
  var prerequisites=facts(rows,files);
  var binding=new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader()).computeProcessing(bytes,prerequisites,raw,budget,cancelled);
  String key=producer(draft.meetingId(),input,binding.fingerprint(),target,draft.cancelEpoch(),cancelled);
  var spec=new TaskSpec("TRANSCRIPT_IMPORT_V1","TRANSCRIPT_IMPORT",new SubjectRef("MEETING",draft.meetingId()),key,new TypedRef("TRANSCRIPTION_INPUT",input),binding.fingerprint(),target,0,draft.cancelEpoch(),3);
  rows.add(row(Table.TRANSCRIPTION_INPUT,input,draft.meetingId(),batch,"IMPORT",null,null,"EMPTY",processing.version(),parser.version(),null,null,1L,manifest,binding.fingerprint(),target,draft.cancelEpoch(),time));
  check(cancelled);return new PreparedIntake(this,draft,batch,input,manifest,batchHash,processing.version(),parser.version(),time,spec,facts(rows,files));
 }
 String producer(String meeting,String input,String fingerprint,long version,long epoch,BooleanSupplier cancel){
  independent();return producerHash(meeting,input,fingerprint,version,epoch,cancel);
 }
 String producerBeforeRoots(String meeting,String input,String fingerprint,long version,long epoch){
  return producerHash(meeting,input,fingerprint,version,epoch,()->false);
 }
 private String producerHash(String meeting,String input,String fingerprint,long version,long epoch,BooleanSupplier cancelled){
  return TranscriptImportProducer.compute(meeting,input,fingerprint,version,epoch,canonical,cancelled);
 }
 public ReservedIntake reserveIntake(FileRegistry.LockedMeeting gate,PreparedIntake prepared){
  transaction();if(prepared==null||prepared.issuer!=this)throw new IllegalArgumentException("Foreign prepared intake");
  var d=prepared.draft;access.requirePrepared(registry.access(gate),d.actor(),d.meetingId(),List.of(d.actor().enterpriseId()),MeetingAccess.Action.EDIT);
  var root=root(d.meetingId());writable(root);require(d.actor().enterpriseId().equals(root.get("owner_id"))&&number(root,"revision")==d.revision()&&number(root,"cancel_epoch")==d.cancelEpoch(),FINGERPRINT_MISMATCH);
  // Fresh IDs may not adopt existing input/producer/output rows. No old domain is acquired after sources.
  for(String table:List.of("input_batch","transcription_input")){
   String id=table.equals("input_batch")?prepared.batch:prepared.input;
   require(j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Long.class,id)==0,INPUT_INVALID);
  }
  var reservation=new Reservation(gate,prepared);
  // Authentic registry phase check; these are only reserved member selectors, no source locks.
  for(var source:d.orderedSources())registry.prelock(gate,new FileRegistry.Reference(REFERENCE,prepared.batch,PURPOSE),source.file().fileId(),reservation);
  return reservation;
 }
 public HeldProcessingFacts insertAndReadNew(ReservedIntake reserved,FileRegistry.LockedSources sources){
  var r=reservation(reserved);require(r.phase==Phase.RESERVED,INPUT_INVALID);
  return tx.required(()->{
   try {
    var p=r.prepared;var d=p.draft;var actual=registry.sources(r.gate,sources,d.orderedSources().stream().map(s->s.file().fileId()).toList());
    var expected=new HashMap<String,FileRegistry.CompletedSource>();for(var s:d.orderedSources())expected.put(s.file().fileId(),s);
    for(var s:actual)require(s.equals(expected.remove(s.file().fileId())),FINGERPRINT_MISMATCH);require(expected.isEmpty(),FINGERPRINT_MISMATCH);
    var timestamp=LocalDateTime.parse(p.time,TIME);
    one(j.update("INSERT INTO input_batch(id,meeting_id,kind,input_fingerprint,base_revision,created_at) VALUES (?,?,'TRANSCRIPT',?,?,?)",p.batch,d.meetingId(),p.batchHash,p.spec.businessVersion(),timestamp));
    one(j.update("INSERT INTO input_batch_fingerprint_profile(batch_id,profile) VALUES (?,?)",p.batch,BatchFingerprint.PROFILE));
    long ordinal=1;for(var s:d.orderedSources())one(j.update("INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,?,?,?)",p.batch,ordinal++,s.file().fileId(),s.file().sha256()));
    one(j.update("INSERT INTO transcription_input(id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY',?,?,1,?,?,?,?,?)",p.input,d.meetingId(),p.batch,p.processingVersion,p.parserVersion,p.manifest,p.spec.inputFingerprint(),p.spec.businessVersion(),p.spec.cancelEpoch(),timestamp));
    var reader=new JdbcTranscriptRows(j,budget,()->false);var rows=new ArrayList<FullRow>();
    // These are only the newly inserted rows, whose complete existing FK parents were already held.
    rows.addAll(reader.rows(Table.INPUT_BATCH,"id=?","id",p.batch));
    var profile=j.queryForList("SELECT profile FROM input_batch_fingerprint_profile WHERE batch_id=?",String.class,p.batch);require(profile.equals(List.of(BatchFingerprint.PROFILE)),PROFILE_UNAVAILABLE);
    rows.addAll(reader.rows(Table.INPUT_BATCH_FILE,"batch_id=?","ordinal",p.batch));rows.addAll(reader.rows(Table.TRANSCRIPTION_INPUT,"id=?","id",p.input));
    require(rows.equals(p.expected.rows()),FINGERPRINT_MISMATCH);
    require(j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE input_kind='TRANSCRIPTION_INPUT' AND input_id=?",Long.class,p.input)==0,INPUT_INVALID);
    require(j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE processing_input_id=?",Long.class,p.input)==0,INPUT_INVALID);
    var result=facts(rows,p.expected.files());r.phase=Phase.INSERTED;r.held=new Held(r,result);return r.held;
   }catch(RuntimeException failure){r.phase=Phase.FAILED;throw failure;}
  });
 }
 public void requireHeld(HeldProcessingFacts token,FileRegistry.LockedMeeting gate,TaskSpec exact){
  var held=held(token);var r=held.reserved;if(r.gate!=gate||!r.prepared.spec.equals(exact))throw new IllegalArgumentException("Wrong held processing scope");
  require(r.phase==Phase.INSERTED||r.phase==Phase.BOUND,INPUT_INVALID);
  for(var s:r.prepared.draft.orderedSources()){
   require(j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind=? AND owner_id=? AND purpose=?",Long.class,s.file().fileId(),REFERENCE,r.prepared.batch,PURPOSE)==1,PROVENANCE_UNAVAILABLE);
   require(j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind='UPLOAD_SESSION' AND owner_id=?",Long.class,s.file().fileId(),s.uploadId())==0,PROVENANCE_UNAVAILABLE);
   require(j.queryForObject("SELECT reference_epoch FROM file_object WHERE id=?",Long.class,s.file().fileId())==s.file().referenceEpoch()+1,FINGERPRINT_MISMATCH);
  }
  r.phase=Phase.BOUND;
 }
 public StoredProvenanceFacts detached(HeldProcessingFacts token){return held(token).facts;}
 DataSource dataSource(){return source;}TransactionRunner transactionRunner(){return tx;}
 /** Deny-only verification of the exact retained complete graph; no acquisition or authority issuance. */
 public void requireImportCompletion(List<TaskSpec> specs,ama.task.TaskDomainPolicy.PreparedDomain prepared){taskPolicy.requireCompletion(specs,prepared);}
 public void requireImportSource(DataSource expected){if(source!=expected)throw new IllegalArgumentException("Foreign import source");}
 public StoredProvenanceFacts readWorkerFacts(TaskLease lease,BooleanSupplier cancelled){
  independent();Objects.requireNonNull(lease);check(cancelled);
  var actual=j.queryForList("SELECT owner_id FROM meeting WHERE id=?",String.class,lease.spec().subject().id());
  require(actual.size()==1,PROVENANCE_UNAVAILABLE);
  var actor=new Actor(actual.getFirst(),ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  var facts=new JdbcProcessingFactsReader(this).readWorker(actor,lease,budget,cancelled);
  require(facts.taskFacts().size()==1&&facts.taskFacts().getFirst().taskId().equals(lease.taskId())&&facts.taskFacts().getFirst().spec().equals(lease.spec()),PROVENANCE_UNAVAILABLE);
  return facts;
 }
 public ama.task.TaskDomainPolicy taskPolicy(){return taskPolicy;}
 public ama.task.TaskDomainPolicy minutesTaskPolicy(){return minutesTaskPolicy;}
 MinutesTaskDomainPolicy minutesDomain(){return minutesTaskPolicy;}
 public ama.task.TaskDomainPolicy.PreparedDomain adoptCreated(FileRegistry.LockedMeeting gate,HeldProcessingFacts held,TaskSpec spec){return taskPolicy.adopt(gate,held,spec);}
 public List<FileRegistry.CompletedSource> originals(PreparedIntake prepared){
  if(prepared==null||prepared.issuer!=this)throw new IllegalArgumentException("Foreign preparation");return prepared.draft.orderedSources();
 }
 void requireCreatedTransition(FileRegistry.LockedMeeting gate,HeldProcessingFacts value,TaskSpec spec){
  requireHeld(value,gate,spec);var h=held(value);var prepared=h.reserved.prepared;var draft=prepared.draft;
  var root=root(draft.meetingId());writable(root);
  access.requirePrepared(registry.access(gate),draft.actor(),draft.meetingId(),List.of(draft.actor().enterpriseId()),MeetingAccess.Action.EDIT);
  require(number(root,"revision")==spec.businessVersion()&&number(root,"cancel_epoch")==spec.cancelEpoch()&&draft.revision()+1==spec.businessVersion()&&Objects.equals(root.get("owner_id"),draft.actor().enterpriseId()),FINGERPRINT_MISMATCH);
  require(j.queryForObject("SELECT COUNT(*) FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? AND business_version=? AND cancel_epoch=? AND deleted=0",Long.class,draft.meetingId(),spec.businessVersion(),spec.cancelEpoch())==1,FINGERPRINT_MISMATCH);
 }

 public interface PreparedMinutesTransition {}
 public interface HeldMinutesTransition {}
 private final class TransitionPlan implements PreparedMinutesTransition {
  final Actor actor;final String meeting;final MinutesPendingDiscovery.Plan pending;
  TransitionPlan(Actor actor,String meeting,Collection<String> tasks){this.actor=actor;this.meeting=meeting;pending=MinutesPendingDiscovery.discover(source,meeting,tasks);}
  TransitionPlan(Actor actor,String meeting,MinutesPendingDiscovery.Plan pending){this.actor=actor;this.meeting=meeting;this.pending=pending;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 private enum TransitionPhase { CONTROL, DOMAIN, TASKS, CONSUMED }
 private final class TransitionScope implements HeldMinutesTransition {
  final Object holder=transaction();final TransitionPlan plan;final FileRegistry.LockedMeeting gate;final long before;TransitionPhase phase=TransitionPhase.CONTROL;
  TransitionScope(TransitionPlan plan,FileRegistry.LockedMeeting gate,long before){this.plan=plan;this.gate=gate;this.before=before;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 public PreparedMinutesTransition prepareMinutesTransition(Actor actor,String meeting){return prepareMinutesTransition(actor,meeting,List.of());}
 PreparedMinutesTransition prepareMinutesTransition(Actor actor,String meeting,Collection<String> tasks){independent();uuid(meeting);return new TransitionPlan(actor,meeting,tasks);}
 /** Existing B03 root is adopted, never independently reacquired. All prior controls must already be held. */
 public HeldMinutesTransition lockMinutesTransition(PreparedMinutesTransition prepared,Actor actor,String meeting,MeetingAccess.Locked held){
  access.requirePrepared(held,actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);var gate=registry.adopt(actor,meeting,held,MeetingAccess.Action.EDIT);
  var value=beginMinutesTransition(prepared,actor,meeting,gate);lockMinutesTransitionDomain(value,gate);lockMinutesTransitionTasks(value,gate);return value;
 }
 HeldMinutesTransition beginMinutesTransition(PreparedMinutesTransition prepared,Actor actor,String meeting,FileRegistry.LockedMeeting gate){
  if(!(prepared instanceof JdbcTranscriptAuthority.TransitionPlan plan)||plan.issuer()!=this||!plan.actor.equals(actor)||!plan.meeting.equals(meeting))throw new IllegalArgumentException("Foreign minutes transition declaration");
  access.requirePrepared(registry.access(gate),actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
  var root=j.queryForMap("SELECT revision,owner_id,ever_published,stage,final_confirmation_id FROM meeting WHERE id=?",meeting);if(!actor.enterpriseId().equals(root.get("owner_id")))throw new MeetingAccess.AccessDeniedException();
  MinutesPendingDiscovery.lock(source,plan.pending,List.of("publication","final_confirmation"));
  if(!plan.pending.rows().get("publication").isEmpty()||((Number)root.get("ever_published")).intValue()!=0||"PUBLISHED".equals(root.get("stage")))throw new ama.contract.TranscriptContracts.Failure(409,"MEETING_NOT_WRITABLE");
  var finalId=root.get("final_confirmation_id");if(finalId!=null&&plan.pending.rows().get("final_confirmation").stream().filter(row->finalId.equals(row.get("id"))).count()!=1)throw MinutesPendingDiscovery.unavailable();
  return new TransitionScope(plan,gate,((Number)root.get("revision")).longValue());
 }
 private TransitionScope transition(HeldMinutesTransition value,FileRegistry.LockedMeeting gate,TransitionPhase phase){
  if(!(value instanceof JdbcTranscriptAuthority.TransitionScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||scope.phase!=phase)throw new IllegalArgumentException("Foreign, expired or out-of-phase minutes transition");registry.access(gate);return scope;
 }
 void lockMinutesTransitionDomain(HeldMinutesTransition value,FileRegistry.LockedMeeting gate){var scope=transition(value,gate,TransitionPhase.CONTROL);MinutesPendingDiscovery.lock(source,scope.plan.pending,MinutesPendingDiscovery.DOMAIN);scope.phase=TransitionPhase.DOMAIN;}
 void lockMinutesTransitionTasks(HeldMinutesTransition value,FileRegistry.LockedMeeting gate){var scope=transition(value,gate,TransitionPhase.DOMAIN);MinutesPendingDiscovery.lock(source,scope.plan.pending,MinutesPendingDiscovery.TASKS);MinutesPendingDiscovery.validate(scope.plan.pending,scope.before);scope.phase=TransitionPhase.TASKS;}
 public void finishMinutesTransition(HeldMinutesTransition value,long revision){finishMinutesTransition(value,revision,null);}
 private void finishMinutesTransition(HeldMinutesTransition value,long revision,String selected){
  if(!(value instanceof JdbcTranscriptAuthority.TransitionScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.phase!=TransitionPhase.TASKS||revision!=scope.before+1)throw new IllegalArgumentException("Foreign, expired or consumed minutes transition");registry.access(scope.gate);
  if(j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,scope.plan.meeting)!=revision||j.queryForObject("SELECT business_version FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",Long.class,scope.plan.meeting)!=revision)throw MinutesPendingDiscovery.unavailable();scope.phase=TransitionPhase.CONSUMED;
  for(var candidate:scope.plan.pending.rows().get("generation_candidate")){if(Objects.equals(selected,candidate.get("id"))){var actual=j.queryForMap("SELECT * FROM generation_candidate WHERE id=?",selected);if(!Set.of("ADOPTED","REJECTED").contains(actual.get("state"))||actual.get("decided_at")==null)throw MinutesPendingDiscovery.unavailable();for(var field:candidate.entrySet())if(!Set.of("state","decided_at").contains(field.getKey())){Object actualValue=actual.get(field.getKey());if(actualValue instanceof Number number)actualValue=new java.math.BigDecimal(number.toString()).longValueExact();else if(actualValue instanceof java.sql.Timestamp timestamp)actualValue=timestamp.toLocalDateTime().toString();if(!Objects.equals(field.getValue(),actualValue))throw MinutesPendingDiscovery.unavailable();}continue;}if(j.update("UPDATE generation_candidate SET state='STALE',decided_at=UTC_TIMESTAMP(6) WHERE id=? AND meeting_id=? AND state='PENDING' AND base_revision=?",candidate.get("id"),scope.plan.meeting,candidate.get("base_revision"))!=1)throw MinutesPendingDiscovery.unavailable();}
 }

 /** A prepared intent is issuer-owned data; only its actual held gate can consume the write. */
 private final class IntentPlan {
  final Actor actor;final String meeting;final long expected;final Map<String,List<Map<String,Object>>> controls;
  IntentPlan(Actor actor,String meeting,long expected){this.actor=actor;this.meeting=meeting;this.expected=expected;controls=MinutesIntentControl.read(j,meeting,false);}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 private enum EditorPurpose { EDIT_INTENT }
 private final class IntentScope {
  final Object holder=transaction();final FileRegistry.LockedMeeting gate;final IntentPlan plan;final EditorPurpose purpose=EditorPurpose.EDIT_INTENT;final Long before;boolean consumed;
  IntentScope(FileRegistry.LockedMeeting gate,IntentPlan plan,Long before){this.gate=gate;this.plan=plan;this.before=before;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 Object prepareMinutesIntent(Actor actor,String meeting,long expected){independent();return new IntentPlan(actor,meeting,expected);}
 Object captureMinutesIntent(Object prepared,FileRegistry.LockedMeeting gate,Actor actor,String meeting,long expected){
  if(!(prepared instanceof JdbcTranscriptAuthority.IntentPlan plan)||plan.issuer()!=this||!plan.actor.equals(actor)||!plan.meeting.equals(meeting)||plan.expected!=expected)throw new IllegalArgumentException("Foreign intent plan");
  access.requirePrepared(registry.access(gate),actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
  var root=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);if(!actor.enterpriseId().equals(root.get("owner_id")))throw new ama.access.MeetingAccess.AccessDeniedException();
  var guard=j.queryForList("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=? FOR UPDATE",Long.class,meeting);
  var controls=MinutesIntentControl.read(j,meeting,true);if(!controls.equals(plan.controls))throw new ama.contract.TranscriptContracts.Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");MinutesIntentControl.requireControlled(controls,root);
  if(((Number)root.get("revision")).longValue()!=expected)throw new JdbcMinutesIntentReceipts.StaleIntent(new JdbcMinutesIntentReceipts(source,tx,access).projection(meeting,false));
  if(guard.size()>1||!guard.isEmpty()&&guard.getFirst()<0)throw new ama.contract.TranscriptContracts.Failure(503,"PROVENANCE_UNAVAILABLE");
  if(!guard.isEmpty()&&guard.getFirst()>=Long.MAX_VALUE-1)throw new ama.contract.TranscriptContracts.Failure(409,"INTENT_EXHAUSTED");
  return new IntentScope(gate,plan,guard.isEmpty()?null:guard.getFirst());
 }
 void writeMinutesIntent(Object value,Object prepared,FileRegistry.LockedMeeting gate){
  if(!(value instanceof JdbcTranscriptAuthority.IntentScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||scope.plan!=prepared||scope.purpose!=EditorPurpose.EDIT_INTENT||scope.consumed)throw new IllegalArgumentException("Foreign, expired or consumed intent write");
  registry.access(gate);scope.consumed=true;long next=scope.before==null?1:scope.before+1;int changed=scope.before==null?j.update("INSERT INTO meeting_minutes_editor_guard(meeting_id,intent_epoch) VALUES (?,1)",scope.plan.meeting):j.update("UPDATE meeting_minutes_editor_guard SET intent_epoch=? WHERE meeting_id=? AND intent_epoch=?",next,scope.plan.meeting,scope.before);
  if(changed!=1||!j.queryForList("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Long.class,scope.plan.meeting).equals(List.of(next)))throw new ama.contract.TranscriptContracts.Failure(503,"PROVENANCE_UNAVAILABLE");
 }

 /** Minutes capture shares this exact issuer, DataSource holder and genuine B03/B04 gate. */
 enum MinutesWritePurpose { INITIAL_SETTINGS, GENERATION_INPUT, GENERATION_COMPLETION, STALE_RETENTION, ADOPT, REJECT, DISPOSE, EDIT, REVIEW, SETTINGS }
 private final class MinutesScope {
  final Object holder=transaction();final FileRegistry.LockedMeeting gate;final Actor actor;final String meeting;
  MinutesGraphCapture.Graph graph;TransitionScope transition;MinutesWritePurpose writePurpose;Object preparedWrite;String decidedCandidate;boolean writing;
  MinutesScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting){this.gate=gate;this.actor=actor;this.meeting=meeting;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 Object minutesScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting){
  access.requirePrepared(registry.access(gate),actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
  require(actor.enterpriseId().equals(j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,meeting)),PROVENANCE_UNAVAILABLE);
  return new MinutesScope(gate,actor,meeting);
 }
 void requireMinutesScope(Object value,FileRegistry.LockedMeeting gate,Actor actor,String meeting){
  if(!(value instanceof JdbcTranscriptAuthority.MinutesScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||!scope.actor.equals(actor)||!scope.meeting.equals(meeting)||scope.graph!=null)throw new IllegalArgumentException("Foreign, expired or consumed minutes capture scope");
  registry.access(gate); // Identity/root authorization was established before domain locks.
 }
 void sealMinutesCapture(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph){
  requireMinutesScope(value,gate,actor,graph.discovery().meeting());var scope=(MinutesScope)value;scope.graph=graph;
  if(graph.discovery().pending()!=null){var pending=graph.discovery().pending();long before=((Number)graph.discovery().root().get("revision")).longValue();MinutesPendingDiscovery.validate(pending,before);scope.transition=new TransitionScope(new TransitionPlan(actor,graph.discovery().meeting(),pending),gate,before);scope.transition.phase=TransitionPhase.TASKS;}
 }
 void claimMinutesWrite(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,MinutesWritePurpose purpose,Object prepared){claimMinutesWrite(value,gate,actor,graph,purpose,prepared,null);}
 void claimMinutesWrite(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,MinutesWritePurpose purpose,Object prepared,String selected){
  if(!(value instanceof JdbcTranscriptAuthority.MinutesScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||!scope.actor.equals(actor)||!scope.meeting.equals(graph.discovery().meeting())||scope.graph!=graph||scope.writing)throw new IllegalArgumentException("Foreign, expired or reused minutes write invocation");registry.access(gate);Objects.requireNonNull(purpose);Objects.requireNonNull(prepared);String kind=graph.discovery().kind();boolean valid=switch(purpose){case INITIAL_SETTINGS->kind.equals("INITIAL_SETTINGS");case GENERATION_INPUT->kind.equals("CURRENT_GENERATION");case GENERATION_COMPLETION,STALE_RETENTION->kind.equals("STORED_INPUT");case ADOPT->kind.equals("ADOPT");case SETTINGS->Set.of("SETTINGS_TEMPLATE","SETTINGS_EXCLUSIONS").contains(kind);case REVIEW->Set.of("REVIEW_BODY","REVIEW_ELEMENT").contains(kind);case EDIT->Set.of("BODY_EDIT","BODY_RESTORE","SET_EDIT","SET_RESTORE").contains(kind);default->false;};if(!valid)throw new IllegalArgumentException("Wrong minutes invocation purpose");if(selected!=null&&!Set.of(MinutesWritePurpose.ADOPT,MinutesWritePurpose.REJECT,MinutesWritePurpose.DISPOSE).contains(purpose))throw new IllegalArgumentException("Unexpected candidate decision");if(selected!=null&&graph.auxiliary().getOrDefault("generation_candidate",List.of()).stream().filter(row->selected.equals(row.get("id"))).count()!=1)throw new IllegalArgumentException("Undeclared candidate decision");scope.writePurpose=purpose;scope.preparedWrite=prepared;scope.decidedCandidate=selected;scope.writing=true;
 }
 void requireMinutesWrite(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph){
  if(!(value instanceof JdbcTranscriptAuthority.MinutesScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||!scope.actor.equals(actor)||scope.graph!=graph||!scope.writing)throw new IllegalArgumentException("Foreign or inactive minutes write scope");registry.access(gate);
 }
 void requireMinutesInvocation(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,MinutesWritePurpose purpose,Object prepared){requireMinutesWrite(value,gate,actor,graph);var scope=(MinutesScope)value;if(scope.writePurpose!=purpose||scope.preparedWrite!=prepared)throw new IllegalArgumentException("Foreign minutes purpose or prepared plan");}
 void finishMinutesGraphTransition(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,long revision){requireMinutesWrite(value,gate,actor,graph);var scope=(MinutesScope)value;if(scope.transition==null)throw MinutesPendingDiscovery.unavailable();finishMinutesTransition(scope.transition,revision,scope.decidedCandidate);}
 enum SavedPurpose { OWNER_INSPECTION, CURRENT, MUTATION }
 public interface HeldSavedTranscript {}
 private final class SavedScope implements HeldSavedTranscript {
  SavedTranscriptGraph complete;
  final Object holder=transaction();final FileRegistry.LockedMeeting gate;final Actor actor;final String meeting;final SavedPurpose purpose;
  SavedScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting,SavedPurpose purpose){this.gate=gate;this.actor=actor;this.meeting=meeting;this.purpose=purpose;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 Object savedScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting,SavedPurpose purpose){
  access.requirePrepared(registry.access(gate),actor,meeting,List.of(actor.enterpriseId()),purpose==SavedPurpose.CURRENT?MeetingAccess.Action.READ:MeetingAccess.Action.EDIT);
  return new SavedScope(gate,actor,meeting,purpose);
 }
 void requireSavedScope(Object value,FileRegistry.LockedMeeting gate,Actor actor,String meeting,SavedPurpose purpose){
  if(!(value instanceof JdbcTranscriptAuthority.SavedScope s)||s.issuer()!=this||s.holder!=transaction()||s.gate!=gate||!s.actor.equals(actor)||!s.meeting.equals(meeting)||s.purpose!=purpose)throw new IllegalArgumentException("Foreign or expired saved transcript scope");
  access.requirePrepared(registry.access(gate),actor,meeting,List.of(actor.enterpriseId()),purpose==SavedPurpose.CURRENT?MeetingAccess.Action.READ:MeetingAccess.Action.EDIT);
 }
 HeldSavedTranscript sealSavedGraph(Object value,FileRegistry.LockedMeeting gate,Actor actor,String meeting,SavedTranscriptGraph graph){
  requireSavedScope(value,gate,actor,meeting,SavedPurpose.MUTATION);var scope=(SavedScope)value;require(scope.complete==null&&graph.meeting().equals(meeting)&&graph.selected()!=null,PROVENANCE_UNAVAILABLE);scope.complete=graph;return scope;
 }
 /** Future disposition/freeze integration must consume this exact retained root/graph handle. */
 public void requireSavedHeld(HeldSavedTranscript value,FileRegistry.LockedMeeting gate,Actor actor,String meeting){
  requireSavedScope(value,gate,actor,meeting,SavedPurpose.MUTATION);require(((SavedScope)value).complete!=null,PROVENANCE_UNAVAILABLE);
 }
 String savedRouteStatus(String backend){return savedBackends.historicalStatus(backend);}
 ArchiveReadBudget readBudget(){return budget;}
 CanonicalBudget canonicalBudget(){return canonical;}
 MeetingAccess meetingAccess(){return access;}
 FileRegistry fileRegistry(){return registry;}
 void requireTaskPurpose(Actor actor,String meeting){purposes.require(actor,meeting,PURPOSE);}

 Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,FileRegistry.ReferenceContext context){
  if(context instanceof JdbcTranscriptAuthority.ReadScope)return prelockReadMember(gate,meeting,reference,file,context);
  if(!(context instanceof ReservedIntake reserved))throw new ama.contract.FileContracts.NotFound();
  var r=reservation(reserved);require(r.gate==gate&&r.prepared.draft.meetingId().equals(meeting)&&r.phase==Phase.RESERVED,INPUT_INVALID);
  require(reference.kind().equals(REFERENCE)&&reference.ownerId().equals(r.prepared.batch)&&reference.purpose().equals(PURPOSE)&&r.prepared.draft.orderedSources().stream().anyMatch(s->s.file().fileId().equals(file)),INPUT_INVALID);return r;
 }
 void validate(Object token,String meeting,FileRegistry.Reference reference,FileRegistry.FileRecord file,String originalPurpose){
  if(token instanceof JdbcTranscriptAuthority.ReadScope){validateRead(token,meeting,reference,file,originalPurpose);return;}
  if(!(token instanceof ReservedIntake reserved))throw new ama.contract.FileContracts.NotFound();
  var r=reservation(reserved);require(r.phase==Phase.INSERTED&&r.held!=null,PROVENANCE_UNAVAILABLE);
  require(meeting.equals(r.prepared.draft.meetingId())&&reference.ownerId().equals(r.prepared.batch)&&reference.kind().equals(REFERENCE)&&reference.purpose().equals(PURPOSE)&&PURPOSE.equals(originalPurpose),INPUT_INVALID);
  var matches=r.prepared.draft.orderedSources().stream().filter(s->s.file().fileId().equals(file.fileId())).toList();require(matches.size()==1&&matches.getFirst().file().equals(file),FINGERPRINT_MISMATCH);
 }
 private Reservation reservation(ReservedIntake value){
  if(!(value instanceof JdbcTranscriptAuthority.Reservation r)||r.issuer()!=this||r.holder!=transaction())throw new IllegalArgumentException("Foreign or expired reservation");registry.access(r.gate);return r;
 }
 private Held held(HeldProcessingFacts value){
  if(!(value instanceof JdbcTranscriptAuthority.Held h)||h.issuer()!=this)throw new IllegalArgumentException("Foreign held facts");reservation(h.reserved);require(h.reserved.held==h&&h.reserved.phase!=Phase.FAILED,INPUT_INVALID);return h;
 }
 private Map<String,Object> root(String meeting){return j.queryForMap("SELECT owner_id,revision,cancel_epoch,stage,ever_published,lifecycle,governance_state,transcript_revision_id FROM meeting WHERE id=?",meeting);}
 private void writable(Map<String,Object> root){require(number(root,"ever_published")==0&&!"PUBLISHED".equals(root.get("stage"))&&"ACTIVE".equals(root.get("lifecycle"))&&"ACTIVE".equals(root.get("governance_state")),PROVENANCE_UNAVAILABLE);}
 private static long number(Map<String,Object> row,String name){try{return new java.math.BigDecimal(row.get(name).toString()).longValueExact();}catch(ArithmeticException e){throw fail(INPUT_INVALID);}}
 private void ids(List<String> ids){require(ids!=null&&!ids.isEmpty()&&ids.size()<=budget.maxBatchFiles(),BUDGET_EXCEEDED);var set=new HashSet<String>();for(String id:ids){uuid(id);require(set.add(id),INPUT_INVALID);}}
 private Object transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Authority requires its DataSource holder");return TransactionSynchronizationManager.getResource(source);}
 private static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Preparation requires no ambient transaction");}
 private static void one(int rows){require(rows==1,INPUT_INVALID);}
 private StoredProvenanceFacts facts(List<FullRow> rows,List<ContentFile> files){return new StoredProvenanceFacts(rows,files,List.of(),List.of(),List.of(),List.of(),List.of(),budget.maxFactRows());}
 private static FullRow row(Table table,Object... values){var cells=new ArrayList<Cell>();for(var v:values)cells.add(v==null?new NullCell():v instanceof Number n?cell(n.longValue()):cell((String)v));return new FullRow(table,cells);}
 private static CanonicalValue.ObjectValue obj(Object... pairs){var entries=new ArrayList<CanonicalValue.Entry>();for(int i=0;i<pairs.length;i+=2){Object value=pairs[i+1];entries.add(field((String)pairs[i],value==null?nil():value instanceof CanonicalValue v?v:value instanceof Number n?CanonicalValue.number(n.longValue()):string((String)value)));}return new ObjectValue(entries);}
 private static CanonicalValue wire(Ref ref){Objects.requireNonNull(ref);return obj("artifactType",ref.artifactType().name(),"version",ref.version(),"sha256",ref.sha256());}
 private enum ReadPhase { DOMAIN, SOURCES_VALIDATED, TASKS_HELD, FINALIZED }
 private final class PreparedRead implements CompleteReadPolicy.ReadPreparation {
  final Actor actor;final String meeting;final FileRegistry.ReadRequest request;final TranscriptReadGraph graph;
  private ReadScope scope;
  PreparedRead(Actor actor,String meeting,FileRegistry.ReadRequest request,TranscriptReadGraph graph){this.actor=actor;this.meeting=meeting;this.request=request;this.graph=graph;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 private final class ReadScope implements FileRegistry.ReferenceContext {
  final Object holder=transaction();final FileRegistry.LockedMeeting gate;final PreparedRead prepared;
  final Set<String> validated=new HashSet<>();ReadPhase phase=ReadPhase.DOMAIN;TaskUnion union;
  ReadScope(FileRegistry.LockedMeeting gate,PreparedRead prepared){this.gate=gate;this.prepared=prepared;}
  JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
 }
 private final class TaskUnion {
  final List<ReadScope> scopes;boolean attemptsHeld;
  TaskUnion(List<ReadScope> scopes){this.scopes=scopes;}
 }
 CompleteReadPolicy.ReadPreparation prepareRead(Actor actor,String meeting,FileRegistry.ReadRequest request){
  independent();check(()->false);uuid(meeting);String revision=null,input=null,task=null,file;
  if(request instanceof FileRegistry.CurrentReadRequest current){
   require(REFERENCE.equals(current.referenceKind()),INPUT_INVALID);file=current.fileId();
   var reader=new BoundedTranscriptMetadata(j,budget);var root=reader.one("meeting",BoundedTranscriptMetadata.ROOT,meeting);
   if(root.get("transcript_revision_id")==null)throw new ama.contract.FileContracts.NotFound();
   revision=(String)root.get("transcript_revision_id");Map<String,Object> row;
   try{row=reader.one("transcript_revision",BoundedTranscriptMetadata.REVISION,revision);}
   catch(ama.contract.FileContracts.NotFound dangling){throw fail(PROVENANCE_UNAVAILABLE);}
   if(!"WORKING".equals(row.get("kind")))throw new ama.contract.FileContracts.NotFound();
  }else if(request instanceof ProcessingSourceReader.HistoryRequest history){revision=history.revisionId();file=history.fileId();}
  else if(request instanceof ProcessingSourceReader.WorkerRequest worker){input=worker.inputId();task=worker.lease().taskId();file=worker.fileId();}
  else throw new IllegalArgumentException("Unknown transcript source selector");
  uuid(file);
  try {
   var graph=TranscriptReadGraph.discover(j,budget,canonical,meeting,revision,input,task,file,
    row->producer(meeting,TranscriptReadGraph.text(row,"id"),TranscriptReadGraph.text(row,"input_fingerprint"),number(row,"base_revision"),number(row,"cancel_epoch"),()->false));
   if(request instanceof FileRegistry.CurrentReadRequest&&(!Objects.equals(revision,graph.root.get("transcript_revision_id"))))throw fail(PROVENANCE_UNAVAILABLE);
   if(request instanceof ProcessingSourceReader.WorkerRequest worker)require(worker.lease().spec().equals(graph.expected),PROVENANCE_UNAVAILABLE);
   return new PreparedRead(actor,meeting,request,graph);
  }catch(Failure failure){if(failure.code()==SOURCE_NOT_IN_INPUT&&request instanceof FileRegistry.CurrentReadRequest)throw new ama.contract.FileContracts.NotFound();throw failure;}catch(ama.contract.FileContracts.NotFound absent){throw fail(PROVENANCE_UNAVAILABLE);}
 }
 void authorizeRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request){
  var action=request instanceof ProcessingSourceReader.HistoryRequest||request instanceof ProcessingSourceReader.WorkerRequest?MeetingAccess.Action.EDIT:MeetingAccess.Action.READ;
  access.require(registry.access(gate),action);
 }
 private PreparedRead preparation(CompleteReadPolicy.ReadPreparation value,String meeting,FileRegistry.ReadRequest request){
  if(!(value instanceof JdbcTranscriptAuthority.PreparedRead p)||p.issuer()!=this||!p.meeting.equals(meeting)||!p.request.equals(request))throw new IllegalArgumentException("Foreign read preparation");return p;
 }
 private ReadScope lockRead(FileRegistry.LockedMeeting gate,PreparedRead p){
  transaction();access.requirePrepared(registry.access(gate),p.actor,p.meeting,List.of(p.actor.enterpriseId()),p.request instanceof FileRegistry.CurrentReadRequest?MeetingAccess.Action.READ:MeetingAccess.Action.EDIT);
  if(p.scope!=null){var s=readScope(p.scope);if(s.gate!=gate||s.phase!=ReadPhase.DOMAIN)throw new IllegalStateException("Consumed read preparation");return s;}
  var graph=p.graph;var reader=new BoundedTranscriptMetadata(j,budget,true);
  require(reader.one("meeting",BoundedTranscriptMetadata.ROOT,p.meeting).equals(graph.root),PROVENANCE_UNAVAILABLE);
  if(p.request instanceof ProcessingSourceReader.WorkerRequest){writable(graph.root);require(number(graph.root,"revision")==graph.expected.businessVersion()&&number(graph.root,"cancel_epoch")==graph.expected.cancelEpoch(),PROVENANCE_UNAVAILABLE);}
  // Acquire the complete parent closure by UUID before any input, even if parent order differs.
  for(String id:graph.revisions.keySet())lockId("transcript_revision",id);
  for(var entry:graph.revisions.entrySet())require(reader.one("transcript_revision",BoundedTranscriptMetadata.REVISION,entry.getKey()).equals(entry.getValue()),PROVENANCE_UNAVAILABLE);
  lockId("transcription_input",TranscriptReadGraph.text(graph.input,"id"));
  require(reader.one("transcription_input",BoundedTranscriptMetadata.INPUT,TranscriptReadGraph.text(graph.input,"id")).equals(graph.input),PROVENANCE_UNAVAILABLE);
  String batch=TranscriptReadGraph.text(graph.batch,"id");lockId("input_batch",batch);
  require(reader.one("input_batch",BoundedTranscriptMetadata.BATCH,batch).equals(graph.batch),PROVENANCE_UNAVAILABLE);
  var profiles=reader.select("input_batch_fingerprint_profile","batch_id profile","batch_id=?","batch_id",2,batch);
  require(profiles.equals(List.of(graph.profile)),PROFILE_UNAVAILABLE);
  require(reader.select("input_batch_file",BoundedTranscriptMetadata.MEMBER,"batch_id=?","ordinal",budget.maxBatchFiles(),batch).equals(graph.members),PROVENANCE_UNAVAILABLE);
  // Compare the complete declared task set under the root, without acquiring new task authority.
  var declared=new BoundedTranscriptMetadata(j,budget).select("durable_task",BoundedTranscriptMetadata.TASK,"(input_kind='TRANSCRIPTION_INPUT' AND input_id=?) OR id=?","id",(int)Math.min(Integer.MAX_VALUE-1L,budget.maxFactRows()),TranscriptReadGraph.text(graph.input,"id"),graph.tasks.firstKey());
  require(declared.equals(List.copyOf(graph.tasks.values())),PROVENANCE_UNAVAILABLE);
  var scope=new ReadScope(gate,p);p.scope=scope;return scope;
 }
 Object prelockCurrent(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference ref,String file,CompleteReadPolicy.ReadPreparation prepared){
  if(!(prepared instanceof JdbcTranscriptAuthority.PreparedRead p)||!(p.request instanceof FileRegistry.CurrentReadRequest current))throw new IllegalArgumentException("Current preparation required");
  preparation(prepared,meeting,current);if(!file.equals(current.fileId()))throw new IllegalArgumentException("Changed current selector");
  if(!ref.kind().equals(REFERENCE)||!ref.ownerId().equals(p.graph.batch.get("id"))||!ref.purpose().equals(PURPOSE))throw new ama.contract.FileContracts.NotFound();
  return lockRead(gate,p);
 }
 CompleteReadPolicy.ReadPlan prelockRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request,CompleteReadPolicy.ReadPreparation prepared){
  var p=preparation(prepared,meeting,request);var scope=lockRead(gate,p);
  return new CompleteReadPolicy.ReadPlan(scope,readMembers(scope,new FileRegistry.Reference(REFERENCE,TranscriptReadGraph.text(p.graph.batch,"id"),PURPOSE),p.graph.requested),p.graph.requested);
 }
 List<CompleteReadPolicy.ReadMember> readMembers(Object domain,FileRegistry.Reference ref,String file){
  var scope=readScope(domain);require(scope.phase==ReadPhase.DOMAIN&&scope.prepared.graph.files.containsKey(file)&&ref.equals(reference(scope)),PROVENANCE_UNAVAILABLE);
  return scope.prepared.graph.members.stream().map(m->new CompleteReadPolicy.ReadMember(reference(scope),TranscriptReadGraph.text(m,"file_id"))).toList();
 }
 private Object prelockReadMember(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference reference,String file,Object context){
  var scope=readScope(context);require(scope.gate==gate&&scope.phase==ReadPhase.DOMAIN&&scope.prepared.meeting.equals(meeting)&&reference.equals(reference(scope))&&scope.prepared.graph.files.containsKey(file),PROVENANCE_UNAVAILABLE);return scope;
 }
 private void validateRead(Object token,String meeting,FileRegistry.Reference ref,FileRegistry.FileRecord file,String purpose){
  var scope=readScope(token);require((scope.phase==ReadPhase.DOMAIN||scope.phase==ReadPhase.SOURCES_VALIDATED)&&scope.prepared.meeting.equals(meeting)&&ref.equals(reference(scope))&&PURPOSE.equals(purpose),PROVENANCE_UNAVAILABLE);
  var expected=scope.prepared.graph.files.get(file.fileId());require(expected!=null&&meeting.equals(file.meetingId())&&Objects.equals(expected.get("sha256"),file.sha256())&&number(expected,"size_bytes")==file.size(),PROVENANCE_UNAVAILABLE);
  require(scope.validated.add(file.fileId()),PROVENANCE_UNAVAILABLE);
  if(scope.validated.equals(scope.prepared.graph.files.keySet()))scope.phase=ReadPhase.SOURCES_VALIDATED;
 }
 void prelockReadTasks(List<Object> values){
  require(values!=null&&!values.isEmpty()&&values.size()<=budget.maxFactRows(),BUDGET_EXCEEDED);var unique=Collections.newSetFromMap(new IdentityHashMap<ReadScope,Boolean>());var scopes=new ArrayList<ReadScope>();
  var tasks=new TreeMap<String,Map<String,Object>>();FileRegistry.LockedMeeting gate=null;
  for(var value:values){
   var scope=readScope(value);require(unique.add(scope)&&scope.phase==ReadPhase.SOURCES_VALIDATED,PROVENANCE_UNAVAILABLE);if(gate==null)gate=scope.gate;require(scope.gate==gate,PROVENANCE_UNAVAILABLE);scopes.add(scope);
   for(var entry:scope.prepared.graph.tasks.entrySet()){require(tasks.size()<budget.maxFactRows()||tasks.containsKey(entry.getKey()),BUDGET_EXCEEDED);var old=tasks.putIfAbsent(entry.getKey(),entry.getValue());require(old==null||old.equals(entry.getValue()),PROVENANCE_UNAVAILABLE);}
  }
  for(String task:tasks.keySet())lockId("durable_task",task);
  var reader=new BoundedTranscriptMetadata(j,budget,true);for(var entry:tasks.entrySet())require(reader.one("durable_task",BoundedTranscriptMetadata.TASK,entry.getKey()).equals(entry.getValue()),PROVENANCE_UNAVAILABLE);
  var union=new TaskUnion(List.copyOf(scopes));for(var scope:scopes){scope.union=union;scope.phase=ReadPhase.TASKS_HELD;}
 }
 void finishRead(Object domain,List<FileRegistry.CompletedSource> sources){
  var scope=readScope(domain);require(scope.phase==ReadPhase.TASKS_HELD&&scope.union!=null,PROVENANCE_UNAVAILABLE);var graph=scope.prepared.graph;
  require(sources.size()==graph.files.size(),PROVENANCE_UNAVAILABLE);var ids=new HashSet<String>();for(var source:sources){require(ids.add(source.file().fileId())&&graph.files.containsKey(source.file().fileId())&&PURPOSE.equals(source.originalPurpose()),PROVENANCE_UNAVAILABLE);}
  if(!scope.union.attemptsHeld){
   var attempts=new TreeMap<String,SortedSet<Long>>();for(var member:scope.union.scopes)if(member.prepared.request instanceof ProcessingSourceReader.WorkerRequest worker)attempts.computeIfAbsent(worker.lease().taskId(),k->new TreeSet<>()).add(worker.lease().attempt());
   for(var entry:attempts.entrySet())for(long attempt:entry.getValue())require(j.queryForList("SELECT attempt FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE",Long.class,entry.getKey(),attempt).equals(List.of(attempt)),PROVENANCE_UNAVAILABLE);
   scope.union.attemptsHeld=true;
  }
  String task=graph.tasks.firstKey();
  if(scope.prepared.request instanceof ProcessingSourceReader.WorkerRequest worker){
   var lease=worker.lease();require(lease.spec().equals(graph.expected),PROVENANCE_UNAVAILABLE);
   require(j.queryForObject("SELECT (state='RUNNING' AND attempt=? AND lease_owner=? AND expires_at>UTC_TIMESTAMP(6)) FROM durable_task WHERE id=? FOR UPDATE",Long.class,lease.attempt(),lease.leaseOwner(),task)==1,PROVENANCE_UNAVAILABLE);
   require(j.queryForObject("SELECT (owner=? AND ended_at IS NULL) FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE",Long.class,lease.leaseOwner(),task,lease.attempt())==1,PROVENANCE_UNAVAILABLE);
  }else require(j.queryForObject("SELECT (state='SUCCEEDED' AND output_kind='TRANSCRIPT_REVISION' AND output_id=?) FROM durable_task WHERE id=? FOR UPDATE",Long.class,graph.raw,task)==1,PROVENANCE_UNAVAILABLE);
  scope.phase=ReadPhase.FINALIZED;
 }
 private ReadScope readScope(Object value){
  if(!(value instanceof JdbcTranscriptAuthority.ReadScope scope)||scope.issuer()!=this||scope.holder!=transaction())throw new IllegalArgumentException("Foreign or expired read scope");registry.access(scope.gate);return scope;
 }
 private FileRegistry.Reference reference(ReadScope scope){return new FileRegistry.Reference(REFERENCE,TranscriptReadGraph.text(scope.prepared.graph.batch,"id"),PURPOSE);}
 private void lockId(String table,String id){check(()->false);require(j.queryForList("SELECT id FROM "+table+" WHERE id=? FOR UPDATE",String.class,id).equals(List.of(id)),PROVENANCE_UNAVAILABLE);}
 FileRegistry.Download history(Actor actor,String meeting,String revision,String file,String range){independent();return registry.read(actor,meeting,new ProcessingSourceReader.HistoryRequest(revision,file),range);}
 FileRegistry.Download worker(TaskLease lease,String input,String file){
  independent();Objects.requireNonNull(lease);uuid(input);uuid(file);
  // The actual task fixes the target meeting; actor is only a claim checked by the fresh B03 gate.
  var tasks=j.queryForList("SELECT subject_kind,subject_id FROM durable_task WHERE id=?",lease.taskId());
  if(tasks.size()!=1||!"MEETING".equals(tasks.getFirst().get("subject_kind")))throw new ama.contract.FileContracts.NotFound();
  String meeting=(String)tasks.getFirst().get("subject_id");uuid(meeting);
  var owners=j.queryForList("SELECT owner_id FROM meeting WHERE id=?",String.class,meeting);if(owners.size()!=1)throw new ama.contract.FileContracts.NotFound();
  var actor=new Actor(owners.getFirst(),ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  return registry.read(actor,meeting,new ProcessingSourceReader.WorkerRequest(lease,input,file),null);
 }


}
