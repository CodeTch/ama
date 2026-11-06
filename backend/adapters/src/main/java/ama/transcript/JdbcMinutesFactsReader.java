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
  private final MinutesGraphCapture.Graph graph;private final StoredProvenanceFacts facts;private final List<Ref> roots;private final Map<String,String> generationManifests;private final TranscriptAcquisitionBudget.Ledger budget;
  private Snapshot(MinutesGraphCapture.Graph graph,StoredProvenanceFacts facts,List<Ref> roots,Map<String,String> manifests,TranscriptAcquisitionBudget.Ledger budget){this.budget=budget;this.graph=graph;this.facts=facts;this.roots=List.copyOf(roots);this.generationManifests=Map.copyOf(manifests);}
  MinutesGraphCapture.Graph graph(){return graph;}TranscriptAcquisitionBudget.Ledger budget(){return budget;}
  public String generationManifestJson(String input){String value=generationManifests.get(input);require(value!=null,PROVENANCE_UNAVAILABLE);return value;}
  public StoredProvenanceFacts facts(){return facts;}public List<Ref> roots(){return roots;}public Map<String,Object> observation(){return graph.discovery().root();}
 }
 /** Authorized structural reuse selection; it never changes the current saved graph or adopts a candidate. */
 public Snapshot readReuseSources(Actor actor,String meeting,String candidate,String exclusion){uuid(candidate);uuid(exclusion);return readOwner(actor,meeting,"REUSE_SOURCES",candidate+"/"+exclusion);}
 private record DisplayKey(Actor actor,String meeting){}
 private record DisplayProof(Snapshot snapshot,long expires){}
 private final Map<DisplayKey,DisplayProof> recentDisplay=new LinkedHashMap<>(16,0.75f,true);
 /** Only the deterministic preparation of an identical graph may be reused.
  * Reopen the pinned archives and reacquire READ, file/source eligibility, selector membership
  * and every full cell before transferring anything, including on a cache hit. */
 Snapshot readDisplay(Actor actor,String meeting,Runnable sealReaders){
  independent();uuid(meeting);
  var key=new DisplayKey(actor,meeting);
  DisplayProof proof;
  synchronized(recentDisplay){
   long now=System.nanoTime();recentDisplay.values().removeIf(p->now-p.expires()>=0);
   proof=recentDisplay.get(key);
  }
  var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);
  if(proof!=null){
   var prior=proof.snapshot();var plan=prior.graph.discovery();
   var jdbc=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());jdbc.setQueryTimeout(2);
   record DisplayRoot(long revision,boolean localOnly){}
   var root=jdbc.query("SELECT m.revision,m.ever_published=0 AND NOT EXISTS(SELECT 1 FROM material_reference mr WHERE mr.meeting_id=m.id AND mr.kind='KNOWLEDGE') FROM meeting m WHERE m.id=?",
    r->r.next()?new DisplayRoot(r.getLong(1),r.getBoolean(2)):null,meeting);
   if(root!=null&&Objects.equals(root.revision(),prior.observation().get("revision"))){
    try{
     var config=archives.get();require(indexSha.equals(config.indexSha256()),PROFILE_UNAVAILABLE);
     for(var raw:config.loadClosure(prior.roots,authority.readBudget(),0,()->false)){
      var bytes=raw.bytes();budget.capture(bytes.length,bytes.length,bytes.length,2);
     }
     sealReaders.run();
     return captureRead(actor,meeting,plan,true,budget,root.localOnly(),gate->{
      var scope=authority.minutesReadScope(gate,actor,meeting);
      var checked=MinutesGraphCapture.compareCaptured(authority,scope,gate,actor,plan,budget,prior.graph,true);
      require(checked.equals(prior.graph),PROVENANCE_UNAVAILABLE);
      return new Snapshot(checked,prior.facts,prior.roots,prior.generationManifests,budget);
     });
    }catch(Failure changed){if(changed.code()!=PROVENANCE_UNAVAILABLE)throw changed;}
   }
   synchronized(recentDisplay){recentDisplay.remove(key,proof);}
  }
  var verified=read(actor,meeting,"SAVED_EFFECTIVE",null,true,budget,sealReaders);
  if(verified.observation().get("minutes_revision_id")!=null&&budget.usage()[3]<=8*1024*1024L){
   synchronized(recentDisplay){
    recentDisplay.put(key,new DisplayProof(verified,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90)));
    while(recentDisplay.size()>8)recentDisplay.remove(recentDisplay.keySet().iterator().next());
   }
  }
  return verified;
 }
 public Snapshot readCurrent(Actor actor,String meeting){return read(actor,meeting,"SAVED_EFFECTIVE",null,true);}
 Snapshot readEffectiveFinal(Actor actor,String meeting,String selected){return read(actor,meeting,"FINAL",selected,true);}
 static void requireEffectiveFinal(Map<String,Object> root,Map<String,Object> frozen){
  if(!Objects.equals(root.get("final_confirmation_id"),frozen.get("id")))throw new ama.contract.TranscriptContracts.Failure(404,"FINAL_NOT_FOUND");
  for(String field:List.of("info_revision_id","roster_revision_id","transcript_confirmation_id","minutes_revision_id","material_snapshot_id","element_set_id","template_revision_id","exclusion_revision_id"))if(!Objects.equals(root.get(field),frozen.get(field)))throw new ama.contract.TranscriptContracts.Failure(404,"FINAL_NOT_FOUND");
 }
 public Snapshot readOwner(Actor actor,String meeting,String selector,String selected){return read(actor,meeting,selector,selected,false);}
 Snapshot readOwner(Actor actor,String meeting,String selector,String selected,TranscriptAcquisitionBudget.Ledger budget){return read(actor,meeting,selector,selected,false,budget);}
 private Snapshot read(Actor actor,String meeting,String selector,String selected,boolean member){return read(actor,meeting,selector,selected,member,limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false));}
 private Snapshot read(Actor actor,String meeting,String selector,String selected,boolean member,TranscriptAcquisitionBudget.Ledger budget){
  return read(actor,meeting,selector,selected,member,budget,()->{});
 }
 private Snapshot read(Actor actor,String meeting,String selector,String selected,boolean member,TranscriptAcquisitionBudget.Ledger budget,Runnable sealReaders){
  independent();uuid(meeting);Objects.requireNonNull(budget);
  var jdbc=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());jdbc.setQueryTimeout(2);
  var retry=selector.equals("STORED_INPUT_READ")?new StoredReadAttempt():null;
  java.util.function.Supplier<Object> version=()->MinutesReadVersion.capture(jdbc,meeting,selector,selected,(rows,bytes)->{budget.row(rows);budget.capture(bytes,bytes,bytes,2);});
  return TranscriptReadRetry.read(()->{var current=version.get();if(retry!=null)retry.version=current;return current;},
      ()->readOnce(actor,meeting,selector,selected,member,budget,retry,version,sealReaders));
 }
 private static final class StoredReadAttempt {Object version;MinutesGraphCapture.Graph previous;}
 private Snapshot readOnce(Actor actor,String meeting,String selector,String selected,boolean member,TranscriptAcquisitionBudget.Ledger budget,StoredReadAttempt retry,java.util.function.Supplier<Object> version,Runnable sealReaders){
  independent();uuid(meeting);require(Set.of("INITIAL_SETTINGS","CURRENT_GENERATION","STORED_INPUT","STORED_INPUT_READ","CANDIDATE","SAVED_EFFECTIVE","OWNER_REVISION","WORKSPACE_READ","REVIEW_BODY","REVIEW_ELEMENT","FINAL","REUSE_SOURCES","OWNER_SET","REVIEW_ID","READINESS","SETTINGS_READ").contains(selector),INVALID_ARGUMENT);
  Objects.requireNonNull(budget);authority.savedAccess().prepareExternalRead(actor,meeting);
  long readStart=System.nanoTime();
  MinutesGraphDiscovery.Discovery discovered=null;RuntimeException discoveryError=null;
  try{discovered=authority.transactionRunner().required(()->MinutesGraphDiscovery.discover(authority.dataSource(),meeting,selector,selected,limits,authority.readBudget()));if(selector.equals("READINESS"))discovered=MinutesGraphDiscovery.withPending(authority.dataSource(),discovered,limits.tasks(),budget);charge(discovered,budget);}catch(RuntimeException failure){cancellation(failure);discoveryError=failure;}
  long discoveredAt=System.nanoTime();
  final var plan=discovered;final var failedDiscovery=discoveryError;
  var captured=captureRead(actor,meeting,plan,member,budget,gate->{if(failedDiscovery!=null)throw failedDiscovery;var scope=member?authority.minutesReadScope(gate,actor,meeting):authority.minutesScope(gate,actor,meeting);if(retry!=null){
   require(Objects.equals(retry.version,version.get()),PROVENANCE_UNAVAILABLE);
   if(retry.previous!=null)return MinutesGraphCapture.reacquireStoredRead(authority,scope,gate,actor,plan,budget,retry.previous);
  }
  var graph=member&&!selector.equals("FINAL")||selector.equals("READINESS")?MinutesGraphCapture.captureCurrent(authority,scope,gate,actor,plan,budget):MinutesGraphCapture.capture(authority,scope,gate,actor,plan,budget);if(member&&selector.equals("FINAL"))requireEffectiveFinal(plan.root(),JdbcMinutesSavedQueries.project(one(graph,Table.FINAL_CONFIRMATION,selected)));return graph;});
  long capturedAt=System.nanoTime();
  if(retry!=null)retry.previous=captured;
  Snapshot prepared=null;RuntimeException preparationError=null;
  try{prepared=prepare(captured,budget);}catch(RuntimeException failure){cancellation(failure);preparationError=failure;}
  final var result=prepared;final var failed=preparationError;
  long preparedAt=System.nanoTime();
  sealReaders.run();
  var checked=captureRead(actor,meeting,plan,member,budget,gate->{if(failed!=null)throw failed;var scope=member?authority.minutesReadScope(gate,actor,meeting):authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.compareCaptured(authority,scope,gate,actor,plan,budget,captured,member&&!selector.equals("FINAL")||selector.equals("READINESS"));if(member&&selector.equals("FINAL"))requireEffectiveFinal(fresh.discovery().root(),JdbcMinutesSavedQueries.project(one(fresh,Table.FINAL_CONFIRMATION,selected)));require(captured.equals(fresh),PROVENANCE_UNAVAILABLE);return result;});
  long completedAt=System.nanoTime();
  if(completedAt-readStart>250_000_000L)System.getLogger(getClass().getName()).log(System.Logger.Level.INFO,
    "Minutes read {0} ms: discovery={1}, capture={2}, preparation={3}, verification={4}",selector,(discoveredAt-readStart)/1000000,(capturedAt-discoveredAt)/1000000,(preparedAt-capturedAt)/1000000,(completedAt-preparedAt)/1000000);
  return checked;
 }
 /** A proof can expire while waiting for another reader's root lock. Retry only before
  * content acquisition, after rollback and a new external proof, using the same ledger. */
 private <T>T captureRead(Actor actor,String meeting,MinutesGraphDiscovery.Discovery plan,boolean member,TranscriptAcquisitionBudget.Ledger budget,java.util.function.Function<ama.storage.FileRegistry.LockedMeeting,T> capture){
  return captureRead(actor,meeting,plan,member,budget,false,capture);
 }
 private <T>T captureRead(Actor actor,String meeting,MinutesGraphDiscovery.Discovery plan,boolean member,TranscriptAcquisitionBudget.Ledger budget,boolean localOnly,java.util.function.Function<ama.storage.FileRegistry.LockedMeeting,T> capture){
  for(int attempt=0;attempt<3;attempt++){
   // The hint skips only external *preparation*. The actual locked READ still checks all
   // current dependencies. A newly introduced dependency can require a fresh preparation.
   budget.check();if(!localOnly||attempt>0)authority.savedAccess().prepareExternalRead(actor,meeting);boolean[] admitted={false};
   try{return authority.transactionRunner().required(()->{var gate=lockGraph(authority,actor,meeting,plan,member?MeetingAccess.Action.READ:MeetingAccess.Action.EDIT);admitted[0]=true;return capture.apply(gate);});}
   catch(MeetingAccess.AuthorityUnavailableException unavailable){if(admitted[0]||attempt==2)throw unavailable;}
  }
  throw new IllegalStateException("Unreachable read acquisition");
 }
 static ama.storage.FileRegistry.LockedMeeting lockGraph(JdbcTranscriptAuthority authority,Actor actor,String meeting,MinutesGraphDiscovery.Discovery plan){return lockGraph(authority,actor,meeting,plan,MeetingAccess.Action.EDIT);}
 static ama.storage.FileRegistry.LockedMeeting lockGraph(JdbcTranscriptAuthority authority,Actor actor,String meeting,MinutesGraphDiscovery.Discovery plan,MeetingAccess.Action action){
  if(plan==null)return authority.fileRegistry().lock(actor,meeting,action);
  require(authority.meetingAccess() instanceof ama.meeting.JdbcMeetingAccess,PROVENANCE_UNAVAILABLE);var access=(ama.meeting.JdbcMeetingAccess)authority.meetingAccess();var ids=access.tryLockHistoricalIdentities(actor,plan.identities(),false).orElseThrow();var root=access.lockPrepared(ids,meeting,false).orElseThrow();access.requirePrepared(root,actor,meeting,plan.identities(),action);return authority.fileRegistry().adopt(actor,meeting,root,action);
 }
 void recheck(Actor actor,String meeting,Snapshot snapshot){recheck(actor,meeting,snapshot,null,null,false);}
 void recheck(Actor actor,String meeting,Snapshot snapshot,JdbcSavedTranscripts transcripts,CorrectionInstallation installation,boolean ready){authority.transactionRunner().required(()->{var gate=lockGraph(authority,actor,meeting,snapshot.graph().discovery());var scope=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.compareCaptured(authority,scope,gate,actor,snapshot.graph().discovery(),snapshot.budget(),snapshot.graph(),snapshot.graph().discovery().kind().equals("READINESS"));require(fresh.equals(snapshot.graph()),PROVENANCE_UNAVAILABLE);if(transcripts!=null)transcripts.requireCorrection(authority,installation,ready);return null;});}
 static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Minutes preparation requires no ambient transaction");}
 private static void cancellation(RuntimeException error){if(Thread.currentThread().isInterrupted()||error instanceof Failure f&&f.code()==CANCELLED)throw error;}
 static void charge(MinutesGraphDiscovery.Discovery graph,TranscriptAcquisitionBudget.Ledger budget){budget.row(graph.root().size());for(var value:graph.root().values())if(value instanceof String s)budget.capture(ama.canonical.CanonicalValueCodec.utf8Length(s,()->false),s.codePointCount(0,s.length()),s.length(),4);for(var group:graph.tables().values())for(var row:group){budget.row(row.size());for(var value:row.values())if(value instanceof String s)budget.capture(ama.canonical.CanonicalValueCodec.utf8Length(s,()->false),s.codePointCount(0,s.length()),s.length(),4);}for(var set:graph.memberships().values()){budget.row(set.size());for(var key:set)for(var value:key)if(value instanceof String s)budget.capture(s.length(),s.length(),s.length(),4);}}
 Snapshot prepare(MinutesGraphCapture.Graph graph,TranscriptAcquisitionBudget.Ledger budget){
  independent();budget.check();var read=authority.readBudget();var canonical=authority.canonicalBudget();String meeting=graph.discovery().meeting();
  var files=new ArrayList<ContentFile>();for(var file:graph.auxiliary().getOrDefault("file_object",List.of()))files.add(new ContentFile((String)file.get("id"),(String)file.get("sha256"),num(file,"size_bytes"),meeting));
  var tasks=new ArrayList<StoredTask>();for(var task:graph.auxiliary().getOrDefault("durable_task",List.of()))tasks.add(new StoredTask((String)task.get("id"),TranscriptReadGraph.spec(task)));
  var anchors=MinutesStoredResults.anchors(graph,budget);var facts=new StoredProvenanceFacts(graph.rows(),files,List.of(),anchors,List.of(),MinutesStoredReviews.scopes(graph,budget),tasks,read.maxFactRows());
  var snapshot=archives.get();require(indexSha.equals(snapshot.indexSha256()),PROFILE_UNAVAILABLE);
  var bindings=new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader());var manifestState=new ArchiveReadState(read,()->false);var roots=new TreeMap<String,Ref>();var manifests=new HashMap<String,String>();
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
  // Inputs frequently share the identical archived configuration closure. Decode that
  // closure once, bind every input separately, and finish the same exact-closure check.
  var bindingGroups=new LinkedHashMap<List<Ref>,List<FullRow>>();
  for(var input:rows(graph,Table.GENERATION_INPUT)){
   var selected=new ama.minutes.contract.GenerationArchiveRefs(facts,snapshot.index(),new StrictProcessingManifestReader(),read,graphBudget,()->false).binding(input.string("id"));
   for(var root:selected){var old=roots.putIfAbsent(root.artifactType()+"|"+root.version(),root);require(old==null||old.equals(root),PROFILE_UNAVAILABLE);}
   bindingGroups.computeIfAbsent(List.copyOf(selected),unused->new ArrayList<>()).add(input);
  }
  for(var group:bindingGroups.entrySet()){
   var raw=snapshot.loadClosure(group.getKey(),read,0,()->false);for(var archive:raw){var bytes=archive.bytes();budget.capture(bytes.length,bytes.length,bytes.length,16);}
   var context=bindings.begin(facts,raw,read,()->false);for(var input:group.getValue()){budget.check();configurations.add(context.generation(input));}context.finish();
  }
  facts=new StoredProvenanceFacts(facts.rows(),facts.files(),configurations,facts.evidenceAnchors(),facts.taskSpecs(),facts.reviewScopes(),facts.taskFacts(),read.maxFactRows());
  var verificationGroups=new LinkedHashMap<List<Ref>,List<TaskSpec>>();
  for(var input:rows(graph,Table.GENERATION_INPUT)){
   var candidates=facts.taskFacts().stream().filter(t->t.spec().input().kind().equals("GENERATION_INPUT")&&t.spec().input().id().equals(input.string("id"))).toList();require(candidates.size()==1,PROVENANCE_UNAVAILABLE);var task=candidates.getFirst().spec();MinutesTaskDomainPolicy.shape(task);require(task.producerKey().equals(ama.minutes.MinutesGenerateProducer.compute(meeting,input.string("id"),input.string("input_fingerprint"),input.number("base_revision"),task.cancelEpoch(),canonical,()->false)),PROVENANCE_UNAVAILABLE);
   var selected=new ama.minutes.contract.GenerationArchiveRefs(facts,snapshot.index(),new StrictProcessingManifestReader(),read,graphBudget,()->false).generation(input.string("id"));verificationGroups.computeIfAbsent(List.copyOf(selected),unused->new ArrayList<>()).add(task);
  }
  for(var group:verificationGroups.entrySet()){
   var raw=snapshot.loadClosure(group.getKey(),read,0,()->false);for(var archive:raw){var bytes=archive.bytes();budget.capture(bytes.length,bytes.length,bytes.length,16);}
   var verified=bindings.verifyGenerations(facts,group.getValue(),raw,graphBudget,read,()->false);
   for(var entry:verified.entrySet()){budget.check();byte[] manifestBytes=CanonicalJson.encode(entry.getValue().manifest(),65536,()->false);budget.capture(manifestBytes.length,manifestBytes.length,manifestBytes.length,4);manifests.put(entry.getKey(),new String(manifestBytes,StandardCharsets.UTF_8));}
  }
  for(var ref:MinutesStoredReviews.validate(facts,snapshot,bindings,read,graphBudget,budget)){var old=roots.putIfAbsent(ref.artifactType()+"|"+ref.version(),ref);require(old==null||old.equals(ref),PROFILE_UNAVAILABLE);}
  for(var ref:MinutesStoredFinals.validate(graph,facts,snapshot,bindings,read,graphBudget,budget)){var old=roots.putIfAbsent(ref.artifactType()+"|"+ref.version(),ref);require(old==null||old.equals(ref),PROFILE_UNAVAILABLE);}
  MinutesStoredResults.validate(graph,facts,graphBudget,budget);budget.check();return new Snapshot(graph,facts,new ArrayList<>(roots.values()),manifests,budget);
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
  TranscriptSpeakerProof.validate(graph.rows());
  var revisions=rows(graph,Table.TRANSCRIPT_REVISION).stream().filter(r->input.string("id").equals(r.string("processing_input_id"))).toList();require(revisions.stream().filter(r->"RAW".equals(r.string("kind"))).count()==1,PROVENANCE_UNAVAILABLE);
  for(var revision:revisions){require(batchId.equals(revision.string("batch_id"))&&binding.fingerprint().equals(revision.string("input_fingerprint")),PROVENANCE_UNAVAILABLE);var segments=segments(graph,revision.string("id"));require(!segments.isEmpty()&&segments.size()<=binding.parseBudget().maxSegments(),BUDGET_EXCEEDED);long position=1;boolean content=false;
   for(var segment:segments){require(segment.number("ordinal")==position++&&TranscriptSegmentProvenance.timing(input.string("parser_version"),segment.nullableNumber("start_ms"),segment.nullableNumber("end_ms"),null)&&segment.nullableNumber("source_paragraph")!=null&&segment.number("source_paragraph")>0&&selectedFiles.contains(segment.string("source_file_id")),PROVENANCE_UNAVAILABLE);content|=!segment.string("body").isBlank();}require(content,INPUT_INVALID);
   var segmentIds=segments.stream().map(r->r.string("segment_id")).collect(java.util.stream.Collectors.toSet());for(var mark:rows(graph,Table.TRANSCRIPT_SEGMENT_MARK))if(revision.string("id").equals(mark.string("revision_id")))require(segmentIds.contains(mark.string("segment_id")),PROVENANCE_UNAVAILABLE);
   if("RAW".equals(revision.string("kind")))require(revision.number("revision")==expected.businessVersion()&&revision.nullableString("parent_id")==null&&revision.string("producer_task_id").equals(producer.get("id"))&&"TRANSCRIPT_REVISION".equals(producer.get("output_kind"))&&revision.string("id").equals(producer.get("output_id")),PROVENANCE_UNAVAILABLE);
   else{require("WORKING".equals(revision.string("kind"))&&revision.nullableString("producer_task_id")==null&&revision.nullableString("parent_id")!=null,PROVENANCE_UNAVAILABLE);var parent=one(graph,Table.TRANSCRIPT_REVISION,revision.string("parent_id"));require(parent.number("revision")<revision.number("revision")&&input.string("id").equals(parent.string("processing_input_id")),PROVENANCE_UNAVAILABLE);var prior=segments(graph,parent.string("id"));require(prior.size()==segments.size(),PROVENANCE_UNAVAILABLE);for(int n=0;n<prior.size();n++)for(String field:List.of("segment_id","ordinal","source_file_id","source_paragraph","start_ms","end_ms"))require(prior.get(n).value(field).equals(segments.get(n).value(field)),PROVENANCE_UNAVAILABLE);}
  }
 }
 static List<FullRow> rows(MinutesGraphCapture.Graph graph,Table table){return graph.rows().stream().filter(r->r.table()==table).toList();}
 static FullRow one(MinutesGraphCapture.Graph graph,Table table,String id){var found=rows(graph,table).stream().filter(r->id.equals(r.string("id"))).toList();require(found.size()==1,PROVENANCE_UNAVAILABLE);return found.getFirst();}
 static List<FullRow> segments(MinutesGraphCapture.Graph graph,String revision){return rows(graph,Table.TRANSCRIPT_SEGMENT).stream().filter(r->revision.equals(r.string("revision_id"))).sorted(Comparator.comparingLong(r->r.number("ordinal"))).toList();}
 static long num(Map<String,Object> row,String name){return SavedTranscriptGraph.num(row,name);}
}
