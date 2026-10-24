package ama.transcript;

import ama.access.MeetingAccess;
import ama.config.*;
import ama.config.json.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.minutes.contract.TranscriptConfirmationHash;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.config.WireValues.*;

/** Standalone owner S/A/F acquisition. Detached results confer no transaction or write authority. */
public final class JdbcMinutesFactsReader {
 private final JdbcTranscriptAuthority authority;private final Supplier<ConfigArchiveSource.Snapshot> archives;private final String indexSha;private final TranscriptAcquisitionBudget limits;
 public JdbcMinutesFactsReader(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String indexSha,TranscriptAcquisitionBudget limits){this.authority=Objects.requireNonNull(authority);this.archives=Objects.requireNonNull(archives);hash(indexSha);this.indexSha=indexSha;this.limits=Objects.requireNonNull(limits);}
 public static final class Snapshot {
  private final MinutesGraphCapture.Graph graph;private final StoredProvenanceFacts facts;private final List<Ref> roots;
  private Snapshot(MinutesGraphCapture.Graph graph,StoredProvenanceFacts facts,List<Ref> roots){this.graph=graph;this.facts=facts;this.roots=List.copyOf(roots);}
  MinutesGraphCapture.Graph graph(){return graph;}
  public StoredProvenanceFacts facts(){return facts;}public List<Ref> roots(){return roots;}public Map<String,Object> observation(){return graph.discovery().root();}
 }
 public Snapshot readOwner(Actor actor,String meeting,String selector,String selected){
  independent();uuid(meeting);require(Set.of("CURRENT_GENERATION","STORED_INPUT","CANDIDATE","SAVED_EFFECTIVE","OWNER_REVISION","REVIEW_BODY","REVIEW_ELEMENT","FINAL").contains(selector),INVALID_ARGUMENT);
  var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);
  MinutesGraphDiscovery.Discovery discovered=null;RuntimeException discoveryError=null;
  try{discovered=MinutesGraphDiscovery.discover(authority.dataSource(),meeting,selector,selected,100000);charge(discovered,budget);}catch(RuntimeException failure){cancellation(failure);discoveryError=failure;}
  final var plan=discovered;final var failedDiscovery=discoveryError;
  var captured=authority.transactionRunner().required(()->{var gate=gate(actor,meeting,plan);if(failedDiscovery!=null)throw failedDiscovery;var scope=authority.minutesScope(gate,actor,meeting);return MinutesGraphCapture.capture(authority,scope,gate,actor,plan,budget);});
  Snapshot prepared=null;RuntimeException preparationError=null;
  try{prepared=prepare(captured,budget);}catch(RuntimeException failure){cancellation(failure);preparationError=failure;}
  final var result=prepared;final var failed=preparationError;
  return authority.transactionRunner().required(()->{var gate=gate(actor,meeting,plan);if(failed!=null)throw failed;var scope=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.capture(authority,scope,gate,actor,plan,budget);require(captured.equals(fresh),PROVENANCE_UNAVAILABLE);return result;});
 }
 private ama.storage.FileRegistry.LockedMeeting gate(Actor actor,String meeting,MinutesGraphDiscovery.Discovery plan){
  if(plan==null)return authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);
  require(authority.meetingAccess() instanceof ama.meeting.JdbcMeetingAccess,PROVENANCE_UNAVAILABLE);var access=(ama.meeting.JdbcMeetingAccess)authority.meetingAccess();var ids=access.tryLockHistoricalIdentities(actor,plan.identities(),false).orElseThrow();var root=access.lockPrepared(ids,meeting,false).orElseThrow();access.requirePrepared(root,actor,meeting,plan.identities(),MeetingAccess.Action.EDIT);return authority.fileRegistry().adopt(actor,meeting,root,MeetingAccess.Action.EDIT);
 }
 static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Minutes preparation requires no ambient transaction");}
 private static void cancellation(RuntimeException error){if(Thread.currentThread().isInterrupted()||error instanceof Failure f&&f.code()==CANCELLED)throw error;}
 static void charge(MinutesGraphDiscovery.Discovery graph,TranscriptAcquisitionBudget.Ledger budget){budget.row(graph.root().size());for(var value:graph.root().values())if(value instanceof String s)budget.capture(ama.canonical.CanonicalValueCodec.utf8Length(s,()->false),s.codePointCount(0,s.length()),s.length(),4);for(var group:graph.tables().values())for(var row:group){budget.row(row.size());for(var value:row.values())if(value instanceof String s)budget.capture(ama.canonical.CanonicalValueCodec.utf8Length(s,()->false),s.codePointCount(0,s.length()),s.length(),4);}for(var set:graph.memberships().values()){budget.row(set.size());for(var key:set)for(var value:key)if(value instanceof String s)budget.capture(s.length(),s.length(),s.length(),4);}}
 Snapshot prepare(MinutesGraphCapture.Graph graph,TranscriptAcquisitionBudget.Ledger budget){
  independent();budget.check();var read=authority.readBudget();var canonical=authority.canonicalBudget();String meeting=graph.discovery().meeting();
  // Each supported result family is admitted only after its original interpretation is implemented.
  require(graph.rows().stream().noneMatch(r->r.table()==Table.FINAL_CONFIRMATION),CAPABILITY_UNAVAILABLE);
  var files=new ArrayList<ContentFile>();for(var file:graph.auxiliary().getOrDefault("file_object",List.of()))files.add(new ContentFile((String)file.get("id"),(String)file.get("sha256"),num(file,"size_bytes"),meeting));
  var tasks=new ArrayList<StoredTask>();for(var task:graph.auxiliary().getOrDefault("durable_task",List.of()))tasks.add(new StoredTask((String)task.get("id"),TranscriptReadGraph.spec(task)));
  var anchors=MinutesStoredResults.anchors(graph,budget);var facts=new StoredProvenanceFacts(graph.rows(),files,List.of(),anchors,List.of(),MinutesStoredReviews.scopes(graph,budget),tasks,read.maxFactRows());
  var snapshot=archives.get();require(indexSha.equals(snapshot.indexSha256()),PROFILE_UNAVAILABLE);
  var bindings=new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader());var manifestState=new ArchiveReadState(read,()->false);var roots=new TreeMap<String,Ref>();
  for(var input:rows(graph,Table.TRANSCRIPTION_INPUT)){
   budget.check();byte[] bytes=input.string("config_manifest").getBytes(StandardCharsets.UTF_8);var wire=new StrictProcessingManifestReader().read(manifestState.capture(bytes,true),manifestState);
   var manifest=fields(wire,"schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps");var configs=fields(manifest.get("configs"),"processing","parser","model","prompt","profiles");Ref root=ref(configs.get("processing"));require(root.version().equals(input.string("processing_config_version")),PROFILE_UNAVAILABLE);
   require(snapshot.index().entries().stream().filter(e->e.ref().equals(root)).count()==1,PROFILE_UNAVAILABLE);Ref old=roots.putIfAbsent(root.artifactType()+"|"+root.version(),root);require(old==null||old.equals(root),PROFILE_UNAVAILABLE);
   var raw=snapshot.loadClosure(List.of(root),read,bytes.length,()->false);for(var archive:raw){byte[] value=archive.bytes();budget.capture(value.length,value.length,value.length,16);}
   var binding=bindings.verifyProcessing(input,facts,raw,read,()->false);
   var expected=new TaskSpec("TRANSCRIPT_IMPORT_V1","TRANSCRIPT_IMPORT",new SubjectRef("MEETING",meeting),TranscriptImportProducer.compute(meeting,input.string("id"),binding.fingerprint(),input.number("base_revision"),input.number("cancel_epoch"),canonical,()->false),new TypedRef("TRANSCRIPTION_INPUT",input.string("id")),binding.fingerprint(),input.number("base_revision"),0,input.number("cancel_epoch"),3);
   var producers=graph.auxiliary().getOrDefault("durable_task",List.of()).stream().filter(t->"TRANSCRIPTION_INPUT".equals(t.get("input_kind"))&&input.string("id").equals(t.get("input_id"))).toList();require(producers.size()==1&&TranscriptReadGraph.spec(producers.getFirst()).equals(expected),PROVENANCE_UNAVAILABLE);var producer=producers.getFirst();require("SUCCEEDED".equals(producer.get("state")),PROVENANCE_UNAVAILABLE);
   var attempts=graph.auxiliary().getOrDefault("task_attempt",List.of()).stream().filter(a->producer.get("id").equals(a.get("task_id"))).toList();require(!attempts.isEmpty()&&attempts.stream().anyMatch(a->Objects.equals(a.get("attempt"),producer.get("attempt"))&&a.get("ended_at")!=null&&"SUCCEEDED".equals(a.get("outcome"))),PROVENANCE_UNAVAILABLE);
   validateProcessingRows(graph,input,producer,binding,expected,files);
   var context=bindings.begin(facts,raw,read,()->false);var profile=context.confirmation(input);context.finish();var gb=new GraphBudget(canonical,limits.revisionDepth(),limits.revisionNodes(),read.maxFactRows());
   for(var confirmation:rows(graph,Table.TRANSCRIPT_CONFIRMATION)){var revision=one(graph,Table.TRANSCRIPT_REVISION,confirmation.string("revision_id"));if(!input.string("id").equals(revision.string("processing_input_id")))continue;require("WORKING".equals(revision.string("kind")),PROVENANCE_UNAVAILABLE);String actual=new TranscriptConfirmationHash(bindings,raw,read).hash(new ConfirmationFacts(confirmation.string("id"),facts),profile,gb,()->false);require(actual.equals(confirmation.string("content_hash")),FINGERPRINT_MISMATCH);}
  }
  var graphBudget=new GraphBudget(canonical,limits.revisionDepth(),limits.revisionNodes(),read.maxFactRows());var configurations=new ArrayList<GenerationConfiguration>();
  for(var input:rows(graph,Table.GENERATION_INPUT)){
   var selected=new ama.minutes.contract.GenerationArchiveRefs(facts,snapshot.index(),new StrictProcessingManifestReader(),read,graphBudget,()->false).binding(input.string("id"));for(var root:selected){var old=roots.putIfAbsent(root.artifactType()+"|"+root.version(),root);require(old==null||old.equals(root),PROFILE_UNAVAILABLE);}var raw=snapshot.loadClosure(selected,read,0,()->false);for(var archive:raw){var bytes=archive.bytes();budget.capture(bytes.length,bytes.length,bytes.length,16);}var context=bindings.begin(facts,raw,read,()->false);configurations.add(context.generation(input));context.finish();
  }
  facts=new StoredProvenanceFacts(facts.rows(),facts.files(),configurations,facts.evidenceAnchors(),facts.taskSpecs(),facts.reviewScopes(),facts.taskFacts(),read.maxFactRows());
  for(var input:rows(graph,Table.GENERATION_INPUT)){
   var candidates=facts.taskFacts().stream().filter(t->t.spec().input().kind().equals("GENERATION_INPUT")&&t.spec().input().id().equals(input.string("id"))).toList();require(candidates.size()==1,PROVENANCE_UNAVAILABLE);var task=candidates.getFirst().spec();MinutesTaskDomainPolicy.shape(task);require(task.producerKey().equals(ama.minutes.MinutesGenerateProducer.compute(meeting,input.string("id"),input.string("input_fingerprint"),input.number("base_revision"),task.cancelEpoch(),canonical,()->false)),PROVENANCE_UNAVAILABLE);
   var selected=new ama.minutes.contract.GenerationArchiveRefs(facts,snapshot.index(),new StrictProcessingManifestReader(),read,graphBudget,()->false).generation(input.string("id"));var raw=snapshot.loadClosure(selected,read,0,()->false);for(var archive:raw){var bytes=archive.bytes();budget.capture(bytes.length,bytes.length,bytes.length,16);}bindings.verifyGeneration(new GenerationFacts(input.string("id"),facts),task,raw,graphBudget,read,()->false);
  }
  for(var ref:MinutesStoredReviews.validate(facts,snapshot,bindings,read,graphBudget,budget)){var old=roots.putIfAbsent(ref.artifactType()+"|"+ref.version(),ref);require(old==null||old.equals(ref),PROFILE_UNAVAILABLE);}
  MinutesStoredResults.validate(graph,facts,graphBudget,budget);budget.check();return new Snapshot(graph,facts,new ArrayList<>(roots.values()));
 }
 private void validateProcessingRows(MinutesGraphCapture.Graph graph,FullRow input,Map<String,Object> producer,ConfigBindings.ValidatedProcessingBinding binding,TaskSpec expected,List<ContentFile> files){
  String meeting=graph.discovery().meeting(),batchId=input.string("batch_id");var batch=one(graph,Table.INPUT_BATCH,batchId);require("TRANSCRIPT".equals(batch.string("kind"))&&batch.number("base_revision")==input.number("base_revision"),PROVENANCE_UNAVAILABLE);
  var profiles=graph.auxiliary().getOrDefault("input_batch_fingerprint_profile",List.of()).stream().filter(r->batchId.equals(r.get("batch_id"))).toList();require(profiles.size()==1&&BatchFingerprint.PROFILE.equals(profiles.getFirst().get("profile")),PROFILE_UNAVAILABLE);
  var members=rows(graph,Table.INPUT_BATCH_FILE).stream().filter(r->batchId.equals(r.string("batch_id"))).sorted(Comparator.comparingLong(r->r.number("ordinal"))).toList();require(!members.isEmpty(),PROVENANCE_UNAVAILABLE);var byFile=new HashMap<String,ContentFile>();for(var file:files)byFile.put(file.id(),file);var hashes=new ArrayList<BatchFingerprint.Member>();var selectedFiles=new HashSet<String>();long ordinal=1;
  for(var member:members){String id=member.string("file_id");var file=byFile.get(id);require(file!=null&&selectedFiles.add(id)&&member.number("ordinal")==ordinal++&&file.sha256().equals(member.string("sha256")),PROVENANCE_UNAVAILABLE);hashes.add(new BatchFingerprint.Member(member.number("ordinal"),id,file.sha256(),file.sizeBytes()));
   var sessions=graph.auxiliary().getOrDefault("upload_session",List.of()).stream().filter(r->id.equals(r.get("completed_file_id"))).toList();require(sessions.size()==1,PROVENANCE_UNAVAILABLE);var session=sessions.getFirst();var stored=graph.auxiliary().get("file_object").stream().filter(r->id.equals(r.get("id"))).findFirst().orElseThrow();require("COMPLETED".equals(session.get("state"))&&"TRANSCRIPT_INPUT".equals(session.get("purpose"))&&meeting.equals(session.get("meeting_id"))&&meeting.equals(stored.get("meeting_id")),PROVENANCE_UNAVAILABLE);
   for(String key:List.of("backend_id","namespace","object_key"))require(Objects.equals(session.get(key),stored.get(key)),PROVENANCE_UNAVAILABLE);require(Objects.equals(session.get("expected_size"),stored.get("size_bytes"))&&Objects.equals(session.get("expected_sha256"),stored.get("sha256")),PROVENANCE_UNAVAILABLE);
   require(graph.auxiliary().getOrDefault("file_reference",List.of()).stream().filter(r->id.equals(r.get("file_id"))&&batchId.equals(r.get("owner_id"))&&"INPUT_BATCH_FILE".equals(r.get("owner_kind"))&&"TRANSCRIPT_INPUT".equals(r.get("purpose"))).count()==1,PROVENANCE_UNAVAILABLE);
  }
  require(new BatchFingerprint().compute(meeting,batchId,"TRANSCRIPT",batch.number("base_revision"),hashes,authority.canonicalBudget(),()->false).equals(batch.string("input_fingerprint")),FINGERPRINT_MISMATCH);
  var revisions=rows(graph,Table.TRANSCRIPT_REVISION).stream().filter(r->input.string("id").equals(r.string("processing_input_id"))).toList();require(revisions.stream().filter(r->"RAW".equals(r.string("kind"))).count()==1,PROVENANCE_UNAVAILABLE);
  for(var revision:revisions){require(batchId.equals(revision.string("batch_id"))&&binding.fingerprint().equals(revision.string("input_fingerprint")),PROVENANCE_UNAVAILABLE);var segments=segments(graph,revision.string("id"));require(!segments.isEmpty()&&segments.size()<=binding.parseBudget().maxSegments(),BUDGET_EXCEEDED);long position=1;boolean content=false;
   for(var segment:segments){require(segment.number("ordinal")==position++&&segment.nullableNumber("start_ms")==null&&segment.nullableNumber("end_ms")==null&&segment.nullableString("speaker_id")==null&&segment.nullableNumber("source_paragraph")!=null&&segment.number("source_paragraph")>0&&selectedFiles.contains(segment.string("source_file_id")),PROVENANCE_UNAVAILABLE);content|=!segment.string("body").isBlank();}require(content,INPUT_INVALID);
   var segmentIds=segments.stream().map(r->r.string("segment_id")).collect(java.util.stream.Collectors.toSet());for(var mark:rows(graph,Table.TRANSCRIPT_SEGMENT_MARK))if(revision.string("id").equals(mark.string("revision_id")))require(segmentIds.contains(mark.string("segment_id")),PROVENANCE_UNAVAILABLE);
   if("RAW".equals(revision.string("kind")))require(revision.number("revision")==expected.businessVersion()&&revision.nullableString("parent_id")==null&&revision.string("producer_task_id").equals(producer.get("id"))&&"TRANSCRIPT_REVISION".equals(producer.get("output_kind"))&&revision.string("id").equals(producer.get("output_id")),PROVENANCE_UNAVAILABLE);
   else{require("WORKING".equals(revision.string("kind"))&&revision.nullableString("producer_task_id")==null&&revision.nullableString("parent_id")!=null,PROVENANCE_UNAVAILABLE);var parent=one(graph,Table.TRANSCRIPT_REVISION,revision.string("parent_id"));require(parent.number("revision")<revision.number("revision")&&input.string("id").equals(parent.string("processing_input_id")),PROVENANCE_UNAVAILABLE);var prior=segments(graph,parent.string("id"));require(prior.size()==segments.size(),PROVENANCE_UNAVAILABLE);for(int n=0;n<prior.size();n++)for(String field:List.of("segment_id","ordinal","source_file_id","source_paragraph","start_ms","end_ms","speaker_id"))require(prior.get(n).value(field).equals(segments.get(n).value(field)),PROVENANCE_UNAVAILABLE);}
  }
 }
 static List<FullRow> rows(MinutesGraphCapture.Graph graph,Table table){return graph.rows().stream().filter(r->r.table()==table).toList();}
 static FullRow one(MinutesGraphCapture.Graph graph,Table table,String id){var found=rows(graph,table).stream().filter(r->id.equals(r.string("id"))).toList();require(found.size()==1,PROVENANCE_UNAVAILABLE);return found.getFirst();}
 static List<FullRow> segments(MinutesGraphCapture.Graph graph,String revision){return rows(graph,Table.TRANSCRIPT_SEGMENT).stream().filter(r->revision.equals(r.string("revision_id"))).sorted(Comparator.comparingLong(r->r.number("ordinal"))).toList();}
 static long num(Map<String,Object> row,String name){return SavedTranscriptGraph.num(row,name);}
}
