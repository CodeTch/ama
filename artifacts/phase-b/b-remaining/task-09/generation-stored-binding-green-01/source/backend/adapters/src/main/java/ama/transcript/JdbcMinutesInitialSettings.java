package ama.transcript;
import ama.access.MeetingAccess;
import ama.config.ConfigArchiveSource;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.SubjectRef;
import ama.minutes.*;
import ama.task.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Supplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** First-only settings command. Formal installation is owned by the combined runtime assembly. */
public final class JdbcMinutesInitialSettings {
 private final JdbcTranscriptAuthority authority;private final Supplier<ConfigArchiveSource.Snapshot> archives;private final Ref prompt,model;
 private final TranscriptAcquisitionBudget limits;private final SubjectFencePort fences;private final OutboxPort outbox;private final ama.audit.AuditPort audit;private final JdbcMinutesSettingsReceipts receipts;private final JdbcTemplate j;
 public JdbcMinutesInitialSettings(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,Ref prompt,Ref model){this(authority,archives,prompt,model,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256),new JdbcSubjectFences(authority.dataSource(),Set.of("MEETING")),new JdbcOutbox(authority.dataSource(),new MinutesEventReferences(authority.dataSource()).validators()),new ama.audit.JdbcAuditAdapter(authority.dataSource()));}
 public JdbcMinutesInitialSettings(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,Ref prompt,Ref model,TranscriptAcquisitionBudget limits,SubjectFencePort fences,OutboxPort outbox,ama.audit.AuditPort audit){this.authority=Objects.requireNonNull(authority);this.archives=Objects.requireNonNull(archives);this.prompt=Objects.requireNonNull(prompt);this.model=Objects.requireNonNull(model);this.limits=Objects.requireNonNull(limits);this.fences=Objects.requireNonNull(fences);this.outbox=Objects.requireNonNull(outbox);this.audit=Objects.requireNonNull(audit);receipts=new JdbcMinutesSettingsReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);}
 public Map<String,Object> initialize(Actor actor,String meeting,String key,long expectedRevision,String template,String trace){
  JdbcMinutesFactsReader.independent();uuid(trace);var identity=JdbcMinutesSettingsReceipts.identity(actor,meeting,key,expectedRevision,template);var replay=receipts.lookup(actor,meeting,identity,template);if(replay.isPresent())return replay.orElseThrow();
  var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);
  MinutesGraphDiscovery.Discovery discovered=null;RuntimeException discoveryError=null;
  try{discovered=MinutesGraphDiscovery.discover(authority.dataSource(),meeting,"INITIAL_SETTINGS",template,100000);JdbcMinutesFactsReader.charge(discovered,budget);}catch(RuntimeException failure){cancellation(failure);discoveryError=failure;}
  final var plan=discovered;final var badDiscovery=discoveryError;MinutesGraphCapture.Graph snapshot;
  try{snapshot=authority.transactionRunner().required(()->{var gate=authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);if(badDiscovery!=null)throw badDiscovery;var scope=authority.minutesScope(gate,actor,meeting);var graph=MinutesGraphCapture.capture(authority,scope,gate,actor,plan,budget);firstOnly(graph,expectedRevision);return graph;});}
  catch(RuntimeException failure){cancellation(failure);if(!ordinary(failure))throw failure;var winner=receipts.lookup(actor,meeting,identity,template);if(winner.isPresent())return winner.orElseThrow();throw failure;}
  RuntimeException preparationError=null;
  try{var selection=MinutesCatalogAdmission.capture(archives.get(),prompt,model,authority.readBudget());require(template.equals(selection.defaultTemplateRevisionId()),PROFILE_UNAVAILABLE);for(var raw:selection.archives()){String text=new String(raw.bytes(),java.nio.charset.StandardCharsets.UTF_8);budget.capture(raw.bytes().length,text.codePointCount(0,text.length()),text.length(),16);}validateTemplate(snapshot,template);}
  catch(RuntimeException failure){cancellation(failure);preparationError=failure;}
  if(preparationError!=null){var failed=preparationError;if(ordinary(failed)){var winner=receipts.lookup(actor,meeting,identity,template);if(winner.isPresent())return winner.orElseThrow();}else authority.transactionRunner().required(()->{authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);return null;});throw failed;}
  String exclusion=UUID.randomUUID().toString();var now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);String time=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").format(now);
  budget.row(Table.EXCLUSION_REVISION.columns().size());for(String text:List.of(exclusion,meeting,time))budget.text(text.length(),text.length(),text.length(),8,true);var planned=new FullRow(Table.EXCLUSION_REVISION,List.of(cell(exclusion),cell(meeting),cell(1L),cell(time)));
  return authority.transactionRunner().required(()->{
   var stored=receipts.claim(actor,meeting,identity);var gate=authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);JdbcMinutesSettingsReceipts.match(stored);if(stored.result()!=null)return receipts.restore(meeting,template,stored,true);
   var scope=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.capture(authority,scope,gate,actor,plan,budget);require(snapshot.equals(fresh),PROVENANCE_UNAVAILABLE);firstOnly(fresh,expectedRevision);
   var subject=fences.lock(new SubjectRef("MEETING",meeting),false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));require(!subject.deleted()&&subject.businessVersion()==expectedRevision&&subject.cancelEpoch()==number(fresh.discovery().root(),"cancel_epoch"),PROVENANCE_UNAVAILABLE);authority.claimMinutesWrite(scope,gate,actor,fresh);
   require(j.update("INSERT INTO exclusion_revision(id,meeting_id,revision,created_at) VALUES (?,?,1,?)",exclusion,meeting,now)==1,PROVENANCE_UNAVAILABLE);
   require(new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes()).rows(Table.EXCLUSION_REVISION,"id=?","id",1,true,exclusion).equals(List.of(planned)),PROVENANCE_UNAVAILABLE);
   long revision=expectedRevision+1;
   if(j.update("UPDATE meeting SET template_revision_id=?,exclusion_revision_id=?,revision=?,needs_generation=1,generation_fingerprint=NULL,final_confirmation_id=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=? AND lifecycle='ACTIVE' AND governance_state='ACTIVE' AND ever_published=0 AND stage<>'PUBLISHED'",template,exclusion,revision,meeting,expectedRevision,subject.cancelEpoch())!=1)throw conflict("REVISION_CONFLICT");
   require(j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",revision,meeting)==1,PROVENANCE_UNAVAILABLE);fences.advance(subject,revision,subject.cancelEpoch(),false);
   receipts.accepted(identity,exclusion);var response=receipts.restore(meeting,template,new JdbcMinutesSettingsReceipts.Stored(exclusion,200,true),false);
   audit.append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),"MINUTES_SETTINGS_INITIALIZED",meeting,revision,"SUCCEEDED",trace,java.time.Instant.now()));outbox.append(new ama.contract.OutboxContracts.EventDraft("MINUTES_SETTINGS_INITIALIZED",null,new SubjectRef("MEETING",meeting),revision,new ama.contract.OutboxContracts.Reference("EXCLUSION_REVISION",exclusion)));outbox.flush();return response;
  });
 }
 private void firstOnly(MinutesGraphCapture.Graph graph,long expected){
  var root=graph.discovery().root();if(number(root,"revision")!=expected)throw conflict("REVISION_CONFLICT");if(expected>=Long.MAX_VALUE-1)throw conflict("REVISION_EXHAUSTED");
  if(number(root,"ever_published")!=0||!"ACTIVE".equals(root.get("lifecycle"))||!"ACTIVE".equals(root.get("governance_state"))||"PUBLISHED".equals(root.get("stage")))throw conflict("MEETING_NOT_WRITABLE");
  for(String field:List.of("template_revision_id","exclusion_revision_id","minutes_revision_id","element_set_id","final_confirmation_id","material_snapshot_id","generation_fingerprint"))if(root.get(field)!=null)throw conflict("INITIAL_SETTINGS_ALREADY_ESTABLISHED");
  if(!graph.auxiliary().getOrDefault("publication",List.of()).isEmpty())throw conflict("MEETING_NOT_WRITABLE");
  for(String table:List.of("generation_input","generation_candidate","minutes_revision","element_set","exclusion_revision","result_review"))if(!j.queryForList("SELECT id FROM "+table+" WHERE meeting_id=? ORDER BY id LIMIT 257",String.class,graph.discovery().meeting()).isEmpty())throw conflict("INITIAL_SETTINGS_ALREADY_ESTABLISHED");
  if(!j.queryForList("SELECT id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? AND (task_type='MINUTES_GENERATE_V1' OR input_kind='GENERATION_INPUT') ORDER BY id LIMIT 257",String.class,graph.discovery().meeting()).isEmpty())throw conflict("INITIAL_SETTINGS_ALREADY_ESTABLISHED");
  if(!j.queryForList("SELECT id FROM external_operation WHERE subject_kind='MEETING' AND subject_id=? AND operation_kind='MINUTES_GENERATE_V1' ORDER BY id LIMIT 257",String.class,graph.discovery().meeting()).isEmpty())throw conflict("INITIAL_SETTINGS_ALREADY_ESTABLISHED");
 }
 private static void validateTemplate(MinutesGraphCapture.Graph graph,String template){
  var revisions=graph.rows().stream().filter(r->r.table()==Table.TEMPLATE_REVISION).toList();require(revisions.size()==1,PROVENANCE_UNAVAILABLE);var r=revisions.getFirst();var parents=graph.auxiliary().getOrDefault("minutes_template",List.of());require(parents.size()==1,PROVENANCE_UNAVAILABLE);var parent=parents.getFirst();
  String normalized=java.text.Normalizer.normalize(ControlledMinutesTemplateInstaller.NAME,java.text.Normalizer.Form.NFC);String hash;try{hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  require(r.string("id").equals(template)&&r.value("owner_id") instanceof NullCell&&r.number("revision")==1&&r.number("theme_count")==7&&ControlledMinutesTemplateInstaller.NAME.equals(r.string("name"))&&ControlledMinutesTemplateInstaller.THEMES.equals(r.string("themes"))&&r.string("template_id").equals(parent.get("id"))&&parent.get("owner_id")==null&&number(parent,"revision")==1&&template.equals(parent.get("current_revision_id"))&&ControlledMinutesTemplateInstaller.NAME.equals(parent.get("name"))&&normalized.equals(parent.get("name_normalized"))&&hash.equals(parent.get("name_hash"))&&r.string("created_at").equals(parent.get("created_at")),PROVENANCE_UNAVAILABLE);
 }
 private static long number(Map<String,Object> row,String name){return ((Number)row.get(name)).longValue();}
 private static ama.contract.TranscriptContracts.Failure conflict(String code){return new ama.contract.TranscriptContracts.Failure(409,code);}
 private static boolean ordinary(RuntimeException failure){return failure instanceof Failure f&&Set.of(PROFILE_UNAVAILABLE,CAPABILITY_UNAVAILABLE,BUDGET_EXCEEDED,READ_FAILED).contains(f.code())||failure instanceof ama.contract.TranscriptContracts.Failure||failure instanceof ama.contract.FileContracts.Unavailable;}
 private static void cancellation(RuntimeException failure){if(failure instanceof Failure f&&f.code()==CANCELLED)throw failure;}
}
