package ama.task;

import ama.audit.JdbcAuditAdapter;
import ama.canonical.CanonicalValue;
import ama.config.*;
import ama.config.json.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.storage.FileRegistry;
import ama.transcript.*;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Shared per-process installation: one existing transcript issuer, one fixed confined registration. */
public final class TranscriptImportRuntime {
 private TranscriptImportRuntime() {}
 public record Settings(Supplier<ConfigArchiveSource.Snapshot> archives,ArchiveReadBudget archiveBudget,
                        TranscriptDocumentParser.ParseBudget aggregate,Duration batchDeadline) {
  public Settings {
   Objects.requireNonNull(archives);Objects.requireNonNull(archiveBudget);Objects.requireNonNull(aggregate);Objects.requireNonNull(batchDeadline);
   if(aggregate.maxSourceBytes()<=0||aggregate.maxUtf16CodeUnits()<=0||aggregate.maxSegments()<=0||batchDeadline.isZero()||batchDeadline.isNegative()||batchDeadline.compareTo(Duration.ofHours(1))>0)throw new IllegalArgumentException("Finite import bounds required");
  }
 }
 public static JdbcTaskQueue install(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,JdbcTranscriptAuthority.Components components) {
  components.authority().requireImportSource(source);
  return JdbcTaskQueue.installConfined(source,fences,outbox,q->Map.of("TRANSCRIPT_IMPORT_V1",new ImportPolicy(q,components)));
 }
 public static Supplier<TaskResult> prepare(JdbcTaskQueue queue,TaskLease lease,Settings settings,PreparationCancellation cancellation) throws Exception {
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Import preparation requires independent reads");
  if(!(queue.policy(lease.spec()) instanceof ImportPolicy policy)||policy.queue!=queue)throw new IllegalArgumentException("Production import not installed");
  return policy.prepareRaw(lease,settings,cancellation);
 }
 private record Raw(TaskLease lease,String id,String owner,String batch,Instant time,List<TranscriptDocumentParser.Segment> segments,TaskResult failure) {
  Raw {segments=List.copyOf(segments);}
 }
 private static final class ImportPolicy implements ConfinedTaskCompletionPolicy {
  final JdbcTaskQueue queue;final JdbcTranscriptAuthority.Components components;final TaskDomainPolicy domain;
  ImportPolicy(JdbcTaskQueue queue,JdbcTranscriptAuthority.Components components){this.queue=queue;this.components=components;domain=components.authority().taskPolicy();}
  public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){return domain.prepare(specs,purpose,skip);}
  public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){domain.requirePrepared(specs,purpose,held);}
  public boolean permitsDomainEffects(PreparedDomain held){return domain.permitsDomainEffects(held);}
  public boolean lockRoot(TaskSpec spec,boolean skip){return domain.lockRoot(spec,skip);}
  public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain held){return domain.lockRoots(specs,skip,held);}
  public void validateInput(TaskSpec spec){domain.validateInput(spec);}
  public void validateOutput(TaskSpec spec,TaskResult result){
   if(result.outcome()==Outcome.SUCCEEDED)throw new IllegalStateException("QUEUE_PATH_REQUIRED");domain.validateOutput(spec,result);
  }
  public void validateExternalRequest(TaskSpec spec,ama.contract.ExternalContracts.ExternalRequest request){domain.validateExternalRequest(spec,request);}
  private final class Carrier implements Supplier<TaskResult> {
   final ImportPolicy owner=ImportPolicy.this;final Raw raw;final BooleanSupplier cancelled;
   Carrier(Raw raw,BooleanSupplier cancelled){this.raw=raw;this.cancelled=cancelled;}
   public TaskResult get(){throw new IllegalStateException("QUEUE_PATH_REQUIRED");}
  }
  Supplier<TaskResult> prepareRaw(TaskLease lease,Settings settings,PreparationCancellation cancellation) throws Exception {
   long deadline=System.nanoTime()+settings.batchDeadline().toNanos();
   BooleanSupplier cancelled=()->cancellation.cancelled()||System.nanoTime()-deadline>=0;
   try {
    check(cancelled);
    var facts=components.authority().readWorkerFacts(lease,cancelled);
    var input=facts.rows().stream().filter(row->row.table()==Table.TRANSCRIPTION_INPUT&&row.string("id").equals(lease.spec().input().id())).findFirst().orElseThrow();
    byte[] original=input.string("config_manifest").getBytes(StandardCharsets.UTF_8);
    var state=new ArchiveReadState(settings.archiveBudget(),cancelled);
    var manifest=new StrictProcessingManifestReader().read(state.capture(original,true),state);
    var fields=fields(manifest,"schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps");
    var configs=fields(fields.get("configs"),"processing","parser","model","prompt","profiles");
    var archives=settings.archives().get().loadClosure(List.of(ref(configs.get("processing"))),settings.archiveBudget(),original.length,cancelled);
    var binding=new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader()).verifyProcessing(input,facts,archives,settings.archiveBudget(),cancelled);
    require(binding.parser().version().equals(TextTranscriptParser.PROFILE_VERSION),PROFILE_UNAVAILABLE);
    var members=facts.rows().stream().filter(row->row.table()==Table.INPUT_BATCH_FILE).sorted(Comparator.comparingLong(row->row.number("ordinal"))).toList();
    require(!members.isEmpty()&&members.size()<=settings.archiveBudget().maxBatchFiles(),BUDGET_EXCEEDED);
    var files=new HashMap<String,ContentFile>();for(var file:facts.files())require(files.put(file.id(),file)==null,PROVENANCE_UNAVAILABLE);
    long declared=0;for(var member:members){var file=files.get(member.string("file_id"));require(file!=null&&file.sizeBytes()<=settings.aggregate().maxSourceBytes()-declared,BUDGET_EXCEEDED);declared+=file.sizeBytes();}
    var parser=new TextTranscriptParser();var segments=new ArrayList<TranscriptDocumentParser.Segment>();long bytes=0,units=0;
    for(var member:members){
     check(cancelled);var file=files.get(member.string("file_id"));
     long remainingBytes=settings.aggregate().maxSourceBytes()-bytes,remainingUnits=settings.aggregate().maxUtf16CodeUnits()-units;
     int remainingSegments=settings.aggregate().maxSegments()-segments.size();
     require(remainingBytes>0&&remainingUnits>0&&remainingSegments>0&&file.sizeBytes()<=remainingBytes,BUDGET_EXCEEDED);
     var budget=new TranscriptDocumentParser.ParseBudget(Math.min(binding.parseBudget().maxSourceBytes(),remainingBytes),Math.min(binding.parseBudget().maxUtf16CodeUnits(),remainingUnits),Math.min(binding.parseBudget().maxSegments(),remainingSegments));
     MeasuredTranscriptDocumentParser.MeasuredResult measured;
     try(var download=components.sources().open(lease,lease.spec().input().id(),file.id());
         var registered=cancellation.register(()->{try{download.close();}catch(java.io.IOException ignored){}});
         var lifetime=new ama.storage.DeadlineInputStream(download.body(),Duration.ofNanos(Math.max(1,deadline-System.nanoTime())),()->{try{download.close();}catch(java.io.IOException ignored){}})) {
      require(download.file().fileId().equals(file.id())&&download.file().meetingId().equals(file.meetingId())&&download.file().sha256().equals(file.sha256())&&download.file().size()==file.sizeBytes()&&download.offset()==0&&download.length()==file.sizeBytes(),PROVENANCE_UNAVAILABLE);
      measured=parser.parseMeasured(lifetime,UUID.fromString(file.id()),file.sha256(),budget,cancelled);
     }
     check(cancelled);require(measured.usage().sourceBytes()==file.sizeBytes()&&measured.usage().segments()==measured.result().segments().size(),PROVENANCE_UNAVAILABLE);
     bytes=Math.addExact(bytes,measured.usage().sourceBytes());units=Math.addExact(units,measured.usage().decodedUtf16CodeUnits());segments.addAll(measured.result().segments());
    }
    check(cancelled);String owner=queue.jdbc.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,lease.spec().subject().id());
    return new Carrier(new Raw(lease,UUID.randomUUID().toString(),owner,input.string("batch_id"),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),segments,null),cancelled);
   } catch(TranscriptDocumentParser.ParseException failure){
    check(cancelled);return failed(lease,"TXT_"+failure.code().name(),cancelled);
   } catch(ama.contract.MinutesContracts.Failure|ama.contract.FileContracts.Unavailable|ama.contract.FileContracts.NotFound|java.io.IOException failure){
    check(cancelled);return failed(lease,"IMPORT_PREPARATION_FAILED",cancelled);
   }
  }
  private Carrier failed(TaskLease lease,String code,BooleanSupplier cancelled){return new Carrier(new Raw(lease,null,null,null,null,List.of(),new TaskResult(Outcome.FAILED,null,code)),cancelled);}
  public CompletionInvocation bindCompletion(JdbcTaskQueue.AfterTask scope,Supplier<TaskResult> carrier){
   if(!(carrier instanceof ImportPolicy.Carrier c)||c.owner!=this)throw new IllegalArgumentException("Foreign import carrier");
   var prepared=scope.preparedFor(queue,this,List.of(c.raw.lease().spec()));
   components.authority().requireImportCompletion(List.of(c.raw.lease().spec()),prepared);
   return new Invocation(scope,c,prepared);
  }
  private final class Invocation implements CompletionInvocation {
   final JdbcTaskQueue.AfterTask scope;final Carrier carrier;final PreparedDomain prepared;boolean written,validated;
   Invocation(JdbcTaskQueue.AfterTask scope,Carrier carrier,PreparedDomain prepared){this.scope=scope;this.carrier=carrier;this.prepared=prepared;}
   void guard(CompletionPhase phase){scope.requireInvocation(queue,ImportPolicy.this,this,carrier,carrier.raw.lease(),phase);components.authority().requireImportCompletion(List.of(carrier.raw.lease().spec()),prepared);check(carrier.cancelled);}
   public TaskResult write(){
    guard(CompletionPhase.WRITING);if(written)throw new IllegalStateException("Import writer already entered");written=true;
    var raw=carrier.raw;if(raw.failure()!=null)return raw.failure();var spec=raw.lease().spec();
    require(raw.owner().equals(queue.jdbc.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,spec.subject().id())),PROVENANCE_UNAVAILABLE);
    int count=queue.jdbc.update("INSERT INTO transcript_revision(id,meeting_id,revision,kind,batch_id,parent_id,input_fingerprint,created_by,created_at,processing_input_id,producer_task_id) VALUES (?,?,?,'RAW',?,NULL,?,?,?,?,?)",raw.id(),spec.subject().id(),spec.businessVersion(),raw.batch(),spec.inputFingerprint(),raw.owner(),JdbcTaskQueue.sqlTime(raw.time()),spec.input().id(),raw.lease().taskId());
    require(count==1,PROVENANCE_UNAVAILABLE);long ordinal=1;
    for(var segment:raw.segments()){
     check(carrier.cancelled);
     require(queue.jdbc.update("INSERT INTO transcript_segment(revision_id,segment_id,ordinal,body,source_file_id,source_paragraph,start_ms,end_ms,speaker_id) VALUES (?,?,?,?,?,?,NULL,NULL,NULL)",raw.id(),segment.stableId().toString(),ordinal++,segment.text(),segment.sourceFileId().toString(),segment.paragraphNo())==1,PROVENANCE_UNAVAILABLE);
    }
    new JdbcAuditAdapter(queue.source).append(new ama.contract.AuditContracts.AuditEvent(raw.owner(),"TRANSCRIPT_IMPORTED",spec.subject().id(),spec.businessVersion(),"SUCCESS",raw.id(),raw.time()));
    check(carrier.cancelled);return new TaskResult(Outcome.SUCCEEDED,new TypedRef("TRANSCRIPT_REVISION",raw.id()),null);
   }
   public void validateActual(TaskResult result){
    guard(CompletionPhase.VALIDATING);scope.requireResult(result);if(validated||!written)throw new IllegalStateException("Import validation phase");validated=true;
    var raw=carrier.raw;if(raw.failure()!=null){require(result==raw.failure()&&result.output()==null,PROVENANCE_UNAVAILABLE);return;}
    var spec=raw.lease().spec();require(result.outcome()==Outcome.SUCCEEDED&&result.output().equals(new TypedRef("TRANSCRIPT_REVISION",raw.id())),PROVENANCE_UNAVAILABLE);
    require(queue.jdbc.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE id=? AND meeting_id=? AND revision=? AND kind='RAW' AND batch_id=? AND parent_id IS NULL AND input_fingerprint=? AND created_by=? AND created_at=? AND processing_input_id=? AND producer_task_id=?",Long.class,raw.id(),spec.subject().id(),spec.businessVersion(),raw.batch(),spec.inputFingerprint(),raw.owner(),JdbcTaskQueue.sqlTime(raw.time()),spec.input().id(),raw.lease().taskId())==1,PROVENANCE_UNAVAILABLE);
    var sizes=queue.jdbc.queryForList("SELECT segment_id,ordinal,OCTET_LENGTH(CONVERT(body USING utf16)) DIV 2 AS units FROM transcript_segment WHERE revision_id=? ORDER BY ordinal LIMIT ?",raw.id(),raw.segments().size()+1);
    require(sizes.size()==raw.segments().size(),PROVENANCE_UNAVAILABLE);
    for(int i=0;i<sizes.size();i++){
     check(carrier.cancelled);var expected=raw.segments().get(i);var size=sizes.get(i);
     require(expected.stableId().toString().equals(size.get("segment_id"))&&((Number)size.get("ordinal")).longValue()==i+1&&((Number)size.get("units")).longValue()==expected.text().length(),PROVENANCE_UNAVAILABLE);
     var row=queue.jdbc.queryForMap("SELECT body,source_file_id,source_paragraph,start_ms,end_ms,speaker_id FROM transcript_segment WHERE revision_id=? AND segment_id=?",raw.id(),expected.stableId().toString());
     require(expected.text().equals(row.get("body"))&&expected.sourceFileId().toString().equals(row.get("source_file_id"))&&((Number)row.get("source_paragraph")).longValue()==expected.paragraphNo()&&row.get("start_ms")==null&&row.get("end_ms")==null&&row.get("speaker_id")==null,PROVENANCE_UNAVAILABLE);
    }
    domain.validateOutput(spec,result);check(carrier.cancelled);
   }
  }
 }
}
