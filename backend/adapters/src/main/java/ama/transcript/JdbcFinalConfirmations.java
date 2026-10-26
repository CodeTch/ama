package ama.transcript;

import ama.config.*;
import ama.config.json.*;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.SubjectRef;
import ama.minutes.contract.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Final confirmation owns S/A/F acquisition and an issuer-bound atomic write. */
public final class JdbcFinalConfirmations {
 private final JdbcTranscriptAuthority authority;private final Supplier<ConfigArchiveSource.Snapshot> archives;private final String pin;
 private final TranscriptAcquisitionBudget limits;private final JdbcSavedTranscripts transcripts;private final JdbcTemplate j;
 private final JdbcMinutesCommandReceipts receipts;private final JdbcMinutesFactsReader reader;private final JdbcMinutesSavedQueries queries;
 private record Plan(MinutesGraphCapture.Graph graph,FinalDraft draft,String hash,CorrectionInstallation correction,PublicationGraph.Plan publication){}
 public JdbcFinalConfirmations(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits,JdbcSavedTranscripts transcripts){
  this.authority=Objects.requireNonNull(authority);this.archives=Objects.requireNonNull(archives);hash(pin);this.pin=pin;this.limits=Objects.requireNonNull(limits);this.transcripts=Objects.requireNonNull(transcripts);transcripts.correctionFor(authority);
  j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);receipts=new JdbcMinutesCommandReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());reader=new JdbcMinutesFactsReader(authority,archives,pin,limits);queries=new JdbcMinutesSavedQueries(authority,archives,pin,limits,transcripts);
 }
 public Map<String,Object> readiness(Actor actor,String meeting){
  JdbcMinutesFactsReader.independent();var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);var declaration=MinutesGraphDiscovery.withPending(authority.dataSource(),MinutesGraphDiscovery.discover(authority.dataSource(),meeting,"READINESS",null,limits,authority.readBudget()),limits.tasks());JdbcMinutesFactsReader.charge(declaration,budget);var publication=PublicationGraph.discover(authority.dataSource(),meeting,budget);
  var captured=authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);var cap=authority.minutesScope(gate,actor,meeting);return MinutesGraphCapture.capturePublicationCurrent(authority,cap,gate,actor,declaration,budget,()->PublicationGraph.lock(authority.dataSource(),publication,true),()->PublicationGraph.lock(authority.dataSource(),publication,false));});
  var snapshot=reader.prepare(captured,budget);var result=queries.finalReadiness(snapshot,publication);var correction=transcripts.correctionFor(authority);
  authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);var cap=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.capturePublicationCurrent(authority,cap,gate,actor,declaration,budget,()->PublicationGraph.lock(authority.dataSource(),publication,true),()->PublicationGraph.lock(authority.dataSource(),publication,false));require(fresh.equals(captured),PROVENANCE_UNAVAILABLE);transcripts.requireCorrection(authority,correction,"NOT_APPLICABLE".equals(correction.projection().readiness()));return null;});return result;
 }
 public Map<String,Object> confirm(Actor actor,String meeting,String key,long expected,List<String> optionalReviewIds,String trace){
  JdbcMinutesFactsReader.independent();uuid(trace);var optional=new ArrayList<>(optionalReviewIds);for(String id:optional)uuid(id);optional.sort(String::compareTo);require(new HashSet<>(optional).size()==optional.size()&&optional.size()<=256,INVALID_ARGUMENT);
  var identity=JdbcMinutesCommandReceipts.identity(actor,meeting,"FINAL_CONFIRM",key,expected,optional.toArray(String[]::new));var replay=receipts.lookup(actor,meeting,identity);if(replay.isPresent())return replay.orElseThrow();
  var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);MinutesGraphDiscovery.Discovery declaration=null;Plan planned=null;RuntimeException error=null;
  try{
   declaration=MinutesGraphDiscovery.withPending(authority.dataSource(),MinutesGraphDiscovery.discover(authority.dataSource(),meeting,"READINESS",null,limits,authority.readBudget()),limits.tasks());JdbcMinutesFactsReader.charge(declaration,budget);var declared=declaration;var publication=PublicationGraph.discover(authority.dataSource(),meeting,budget);
   var graph=authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declared);var cap=authority.minutesScope(gate,actor,meeting);var captured=MinutesGraphCapture.capturePublicationCurrent(authority,cap,gate,actor,declared,budget,()->PublicationGraph.lock(authority.dataSource(),publication,true),()->PublicationGraph.lock(authority.dataSource(),publication,false));writable(captured,expected);return captured;});
   var snapshot=reader.prepare(graph,budget);var readiness=queries.finalReadiness(snapshot,publication);if(!Boolean.TRUE.equals(readiness.get("ready")))throw new ama.contract.TranscriptContracts.Failure(409,"FINAL_NOT_READY");
   var eligible=(List<?>)readiness.get("optionalReviewIds");if(!eligible.containsAll(optional))throw new ama.contract.TranscriptContracts.Failure(422,"INVALID_OPTIONAL_REVIEW");var members=new TreeSet<String>();for(Object id:(List<?>)readiness.get("requiredReviewIds"))members.add((String)id);members.addAll(optional);
   var root=graph.discovery().root();var body=JdbcMinutesFactsReader.one(graph,Table.MINUTES_REVISION,(String)root.get("minutes_revision_id"));var input=JdbcMinutesFactsReader.one(graph,Table.GENERATION_INPUT,body.string("generation_input_id"));
   String now=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").format(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
   var draft=new FinalDraft(UUID.randomUUID().toString(),meeting,expected+1,(String)root.get("info_revision_id"),(String)root.get("roster_revision_id"),(String)root.get("transcript_confirmation_id"),body.string("id"),(String)root.get("material_snapshot_id"),(String)root.get("element_set_id"),(String)root.get("template_revision_id"),(String)root.get("exclusion_revision_id"),input.string("input_fingerprint"),actor.enterpriseId(),now,List.copyOf(members),snapshot.facts());
   var archive=archives.get();require(pin.equals(archive.indexSha256()),PROFILE_UNAVAILABLE);var gb=new GraphBudget(authority.canonicalBudget(),limits.revisionDepth(),limits.revisionNodes(),authority.readBudget().maxFactRows());var refs=new GenerationArchiveRefs(snapshot.facts(),archive.index(),new StrictProcessingManifestReader(),authority.readBudget(),gb,()->false).finalSources(draft);var raw=MinutesStoredReviews.load(archive,refs,authority.readBudget(),budget);
   var configurations=snapshot.facts().configurations().stream().filter(c->c.inputId().equals(input.string("id"))).toList();require(configurations.size()==1,PROFILE_UNAVAILABLE);
   String computed=new ResultHashProfiles(new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader()),raw,authority.readBudget()).finalSources(draft,configurations.getFirst().profiles().definition(ProfileKind.FINAL_SOURCES),gb,()->false);
   planned=new Plan(graph,draft,computed,transcripts.correctionFor(authority),publication);
  }catch(RuntimeException failure){if(Thread.currentThread().isInterrupted()||failure instanceof Failure f&&f.code()==CANCELLED)throw failure;error=failure;}
  final var selected=declaration;final var plan=planned;final var failed=error;
  return authority.transactionRunner().required(()->{
   var stored=receipts.claim(actor,meeting,identity);var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,selected);JdbcMinutesCommandReceipts.match(stored);MinutesDeferredPreparation.beforeReplay(failed);if(stored.result()!=null)return receipts.restore(meeting,identity,stored,true);JdbcMinutesCommandReceipts.requireRevision(j,meeting,expected);if(failed!=null)throw failed;
   var cap=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.capturePublicationCurrent(authority,cap,gate,actor,selected,budget,()->PublicationGraph.lock(authority.dataSource(),plan.publication(),true),()->PublicationGraph.lock(authority.dataSource(),plan.publication(),false));require(fresh.equals(plan.graph()),PROVENANCE_UNAVAILABLE);writable(fresh,expected);transcripts.requireCorrection(authority,plan.correction(),true);
   authority.claimMinutesWrite(cap,gate,actor,fresh,JdbcTranscriptAuthority.MinutesWritePurpose.FINAL_CONFIRM,plan);authority.requireMinutesInvocation(cap,gate,actor,fresh,JdbcTranscriptAuthority.MinutesWritePurpose.FINAL_CONFIRM,plan);
   var d=plan.draft();require(j.update("INSERT INTO final_confirmation(id,meeting_id,meeting_revision,info_revision_id,roster_revision_id,transcript_confirmation_id,minutes_revision_id,material_snapshot_id,element_set_id,template_revision_id,exclusion_revision_id,source_manifest_hash,input_fingerprint,confirmed_by,confirmed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",d.confirmationId(),meeting,d.meetingRevision(),d.infoRevisionId(),d.rosterRevisionId(),d.transcriptConfirmationId(),d.minutesRevisionId(),d.materialSnapshotId(),d.elementSetId(),d.templateRevisionId(),d.exclusionRevisionId(),plan.hash(),d.inputFingerprint(),d.confirmedBy(),java.time.LocalDateTime.ofInstant(java.time.Instant.parse(d.confirmedAt()),java.time.ZoneOffset.UTC))==1,PROVENANCE_UNAVAILABLE);
   for(String review:d.reviewIds())require(j.update("INSERT INTO final_result_review(confirmation_id,review_id) VALUES (?,?)",d.confirmationId(),review)==1,PROVENANCE_UNAVAILABLE);
   var actual=new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes()).rows(Table.FINAL_CONFIRMATION,"id=?","id",1,true,d.confirmationId());require(actual.size()==1,PROVENANCE_UNAVAILABLE);var row=actual.getFirst();
   var values=new LinkedHashMap<String,Object>();values.put("id",d.confirmationId());values.put("meeting_id",meeting);values.put("meeting_revision",d.meetingRevision());values.put("info_revision_id",d.infoRevisionId());values.put("roster_revision_id",d.rosterRevisionId());values.put("transcript_confirmation_id",d.transcriptConfirmationId());values.put("minutes_revision_id",d.minutesRevisionId());values.put("material_snapshot_id",d.materialSnapshotId());values.put("element_set_id",d.elementSetId());values.put("template_revision_id",d.templateRevisionId());values.put("exclusion_revision_id",d.exclusionRevisionId());values.put("source_manifest_hash",plan.hash());values.put("input_fingerprint",d.inputFingerprint());values.put("confirmed_by",d.confirmedBy());values.put("confirmed_at",d.confirmedAt());
   for(var entry:values.entrySet()){var v=row.value(entry.getKey());Object actualValue=v instanceof NullCell?null:v instanceof UIntCell n?n.value():((TextCell)v).value();require(Objects.equals(actualValue,entry.getValue()),PROVENANCE_UNAVAILABLE);}
   require(j.queryForList("SELECT review_id FROM final_result_review WHERE confirmation_id=? ORDER BY review_id",String.class,d.confirmationId()).equals(d.reviewIds()),PROVENANCE_UNAVAILABLE);
   var fences=new ama.task.JdbcSubjectFences(authority.dataSource(),Set.of("MEETING"));var subject=fences.lock(new SubjectRef("MEETING",meeting),false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));require(subject.businessVersion()==expected,PROVENANCE_UNAVAILABLE);
   require(j.update("UPDATE meeting SET revision=?,final_confirmation_id=?,stage='READY_TO_PUBLISH',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=?",expected+1,d.confirmationId(),meeting,expected,subject.cancelEpoch())==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",expected+1,meeting)==1,PROVENANCE_UNAVAILABLE);fences.advance(subject,expected+1,subject.cancelEpoch(),false);authority.finishMinutesGraphTransition(cap,gate,actor,fresh,expected+1);
   receipts.accepted(identity,d.confirmationId());new ama.audit.JdbcAuditAdapter(authority.dataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),"FINAL_CONFIRMED",meeting,expected+1,"SUCCEEDED",trace,java.time.Instant.now()));
   var outbox=new ama.task.JdbcOutbox(authority.dataSource(),new MinutesEventReferences(authority.dataSource()).validators());outbox.append(new ama.contract.OutboxContracts.EventDraft("FINAL_CONFIRMED",null,new SubjectRef("MEETING",meeting),expected+1,new ama.contract.OutboxContracts.Reference("FINAL_CONFIRMATION",d.confirmationId())));outbox.flush();
   return receipts.restore(meeting,identity,new JdbcMinutesCommandReceipts.Stored(d.confirmationId(),200,true),false);
  });
 }
 public Map<String,Object> current(Actor actor,String meeting){
  String id=authority.transactionRunner().required(()->{authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),ama.access.MeetingAccess.Action.READ);return j.queryForObject("SELECT final_confirmation_id FROM meeting WHERE id=?",String.class,meeting);});
  if(id==null)return JdbcMinutesSavedQueries.map("confirmation",null);return detail(actor,meeting,id);
 }
 public Map<String,Object> detail(Actor actor,String meeting,String id){
  uuid(id);boolean owner=authority.transactionRunner().required(()->{authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),ama.access.MeetingAccess.Action.READ);if(j.queryForList("SELECT id FROM final_confirmation WHERE id=? AND meeting_id=?",String.class,id,meeting).isEmpty())throw new ama.contract.TranscriptContracts.Failure(404,"FINAL_NOT_FOUND");return actor.enterpriseId().equals(j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,meeting));});var snapshot=owner?reader.readOwner(actor,meeting,"FINAL",id):reader.readEffectiveFinal(actor,meeting,id);var row=JdbcMinutesFactsReader.one(snapshot.graph(),Table.FINAL_CONFIRMATION,id);
  return FrozenFinalProjection.project(snapshot,id);
 }
 private static void writable(MinutesGraphCapture.Graph graph,long expected){
  var root=graph.discovery().root();if(((Number)root.get("revision")).longValue()!=expected)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");if(expected>=Long.MAX_VALUE-1)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_EXHAUSTED");
  require("ACTIVE".equals(root.get("lifecycle"))&&"ACTIVE".equals(root.get("governance_state"))&&((Number)root.get("ever_published")).intValue()==0,CAPABILITY_UNAVAILABLE);
 }
}
