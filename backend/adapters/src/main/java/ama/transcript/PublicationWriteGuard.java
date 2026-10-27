package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.meeting.JdbcMeetingAccess;
import javax.sql.DataSource;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Same-authority phased contribution. A detached plan never permits a business write. */
public final class PublicationWriteGuard {
 public interface Prepared extends ama.storage.FileRegistry.WritePreparation {}
 public interface Held {}
 private enum Phase { CONTROLS, TASKS, CLAIMED }
 private final JdbcTranscriptAuthority authority;
 private final DataSource source;
 private final JdbcTemplate j;
 private final class Plan implements Prepared {
  final Actor actor;final String meeting;final TranscriptAcquisitionBudget.Ledger budget;
  Map<String,Object> root;List<Map<String,Object>> publications,finals;PublicationGraph.Plan graph;
  Map<String,String> originals=Map.of();Map<String,PublicationWriteHistory.ExecutionOriginal> executions=Map.of();
  RuntimeException failure;
  Plan(Actor actor,String meeting,TranscriptAcquisitionBudget.Ledger budget){this.actor=actor;this.meeting=meeting;this.budget=budget;}
  PublicationWriteGuard issuer(){return PublicationWriteGuard.this;}
 }
 private final class Scope implements Held {
  final Plan plan;final MeetingAccess.Locked gate;final Object holder;Phase phase=Phase.CONTROLS;boolean originalsChecked;
  Scope(Plan plan,MeetingAccess.Locked gate){this.plan=plan;this.gate=gate;holder=transaction();}
  PublicationWriteGuard issuer(){return PublicationWriteGuard.this;}
 }
 PublicationWriteGuard(JdbcTranscriptAuthority authority){this.authority=Objects.requireNonNull(authority);source=authority.dataSource();j=new JdbcTemplate(source);j.setQueryTimeout(2);}

 public Prepared prepare(Actor actor,String meeting){return prepare(actor,meeting,authority.publicationBudget());}
 public void requireStorageSource(DataSource source,MeetingAccess access){if(this.source!=source||authority.savedAccess()!=access)throw new IllegalArgumentException("Foreign publication guard storage assembly");}
 public void requireInstallation(ama.storage.FileRegistry registry,DataSource source,MeetingAccess access){requireStorageSource(source,access);if(authority.fileRegistry()!=registry)throw new IllegalArgumentException("Foreign publication guard registry");}
 public void requireRegistration(Held held,MeetingAccess.Locked gate){scope(held,gate,Phase.CONTROLS);}
 /** Storage calls only after its authentic complete source acquisition. Existing transition claims are reused. */
 public void claimAfterSources(Held held,MeetingAccess.Locked gate){
  if(!(held instanceof PublicationWriteGuard.Scope s)||s.issuer()!=this||s.gate!=gate||s.holder!=transaction())throw new IllegalArgumentException("Foreign publication storage guard");
  if(s.phase==Phase.CONTROLS)tasks(held,gate);if(s.phase==Phase.TASKS)claim(held,gate);requireClaimed(held,gate);
 }
 /** May run in a worker acquisition transaction, but always before its first root. */
 public Prepared prepare(Actor actor,String meeting,TranscriptAcquisitionBudget.Ledger budget){
  Objects.requireNonNull(actor);uuid(meeting);Objects.requireNonNull(budget);
  if(!(authority.savedAccess() instanceof JdbcMeetingAccess access))throw new IllegalStateException("Actual JDBC root tracker required");
  access.requireBeforeIdentityAcquisition();
  var plan=new Plan(actor,meeting,budget);
  try{
   plan.root=root(meeting,budget,false);plan.publications=publications(meeting,budget,false);plan.finals=finals(meeting,budget,false);
   plan.graph=PublicationGraph.discover(source,meeting,budget);
   // Freeze admission precedes original text transfer; unknown/live state is never a reason to parse more.
   writable(plan.root);require(plan.publications.size()<=1,PROVENANCE_UNAVAILABLE);
   if(!plan.publications.isEmpty()){require("FAILED".equals(plan.publications.getFirst().get("state")),CAPABILITY_UNAVAILABLE);PublicationLocalDecision.requireClosed(plan.graph,(String)plan.publications.getFirst().get("id"));}
   if(!plan.graph.rows().get("publication_submission").isEmpty())captureOriginals(plan,access);
   PublicationWriteHistory.verify(plan.root,plan.publications,plan.finals,plan.graph,plan.originals,plan.executions);
  }catch(RuntimeException failure){plan.failure=failure;}
  return plan;
 }

 /** S owns a separate transaction even during worker discovery. The outer holder has no
  * identity/root locks; releasing S precedes every parse/hash, including on failure. */
 private void captureOriginals(Plan plan,JdbcMeetingAccess access){
  var capture=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
  capture.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);capture.setTimeout(2);
  capture.execute(status->{
   j.execute("SET NAMES utf8mb4");j.execute("SET time_zone = '+00:00'");
   var gate=access.lock(plan.actor,plan.meeting);access.require(gate,MeetingAccess.Action.EDIT);
   var current=root(plan.meeting,plan.budget,true);writable(current);require(current.equals(plan.root),PROVENANCE_UNAVAILABLE);
   require(publications(plan.meeting,plan.budget,true).equals(plan.publications)&&finals(plan.meeting,plan.budget,true).equals(plan.finals),PROVENANCE_UNAVAILABLE);
   PublicationGraph.lock(source,plan.graph,true);PublicationGraph.lock(source,plan.graph,false);
   plan.originals=submissionOriginals(plan,true);plan.executions=executionOriginals(plan,true);
   return null;
  });
 }

 /** The caller supplies its authentic already-held owner gate, never asks this guard for a new root. */
 public Held controls(Prepared prepared,Actor actor,String meeting,MeetingAccess.Locked gate){
  if(!(prepared instanceof PublicationWriteGuard.Plan plan)||plan.issuer()!=this||!plan.actor.equals(actor)||!plan.meeting.equals(meeting))throw new IllegalArgumentException("Foreign publication preparation");
  transaction();authority.savedAccess().requirePrepared(gate,actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
  if(plan.graph==null||plan.publications==null||plan.finals==null){if(plan.failure!=null)throw plan.failure;throw fail(PROVENANCE_UNAVAILABLE);}
  require(publications(meeting,plan.budget,true).equals(plan.publications)&&finals(meeting,plan.budget,true).equals(plan.finals),PROVENANCE_UNAVAILABLE);
  PublicationGraph.lock(source,plan.graph,true);var scope=new Scope(plan,gate);authority.registerPublicationGuard(gate,scope);return scope;
 }

 /** Called at the common task rank, after all declared domain/session/file acquisitions. */
 public void tasks(Held held,MeetingAccess.Locked gate){var scope=scope(held,gate,Phase.CONTROLS);PublicationGraph.lock(source,scope.plan.graph,false);scope.phase=Phase.TASKS;}

 /** Consume once immediately before the actual domain effect. No parsing, hashing or network here. */
 public void claim(Held held,MeetingAccess.Locked gate){
  var scope=scope(held,gate,Phase.TASKS);var plan=scope.plan;
  authority.savedAccess().requirePrepared(gate,plan.actor,plan.meeting,List.of(plan.actor.enterpriseId()),MeetingAccess.Action.EDIT);
  writable(root(plan.meeting,plan.budget,true));if(plan.failure!=null)throw plan.failure;
  verifyOriginals(scope);
  scope.phase=Phase.CLAIMED;
 }
 void requirePermitted(Held held,MeetingAccess.Locked gate){var scope=scope(held,gate,Phase.TASKS);writable(root(scope.plan.meeting,scope.plan.budget,true));if(scope.plan.failure!=null)throw scope.plan.failure;verifyOriginals(scope);}
 private void verifyOriginals(Scope scope){if(!scope.originalsChecked){var plan=scope.plan;require(submissionOriginals(plan,true).equals(plan.originals)&&executionOriginals(plan,true).equals(plan.executions),PROVENANCE_UNAVAILABLE);scope.originalsChecked=true;}}
 boolean permits(Held held,MeetingAccess.Locked gate){try{requirePermitted(held,gate);return true;}catch(ama.contract.MinutesContracts.Failure denied){return false;}}
 void reserve(Prepared prepared,ama.contract.TaskContracts.TaskSpec prospective){var graph=declaration(prepared);PublicationGraph.reserve(graph,prospective!=null&&graph.rows().get("durable_task").stream().noneMatch(t->prospective.producerKey().equals(t.get("producer_key"))));}
 public void requireClaimed(Held held,MeetingAccess.Locked gate){scope(held,gate,Phase.CLAIMED);}
 /** Task7 keeps this authentic held scope, never a detached caller-supplied eligibility flag. */
 public boolean permitsRetention(Held held,MeetingAccess.Locked gate,DataSource expected,String meeting){
  if(expected!=source)throw new IllegalArgumentException("Foreign retention DataSource");var scope=scope(held,gate,Phase.TASKS);if(!scope.plan.meeting.equals(meeting))throw new IllegalArgumentException("Foreign retention meeting");return permits(held,gate);
 }


 /** A negative preparation may only reduce fact-observation authority; never grants source or write access. */
 boolean fencesOrdinary(Prepared prepared){if(!(prepared instanceof PublicationWriteGuard.Plan plan)||plan.issuer()!=this)throw new IllegalArgumentException("Foreign publication preparation");if(Thread.currentThread().isInterrupted()||plan.failure instanceof ama.contract.MinutesContracts.Failure f&&f.code()==CANCELLED)throw fail(CANCELLED);return plan.failure!=null;}
 PublicationGraph.Plan declaration(Prepared prepared){if(!(prepared instanceof PublicationWriteGuard.Plan plan)||plan.issuer()!=this||plan.graph==null)throw fail(PROVENANCE_UNAVAILABLE);return plan.graph;}
 void covers(Prepared prepared,Map<String,List<Map<String,Object>>> rows){
  var graph=declaration(prepared);for(String table:MinutesPendingDiscovery.TASKS){
   var keys=MinutesSourceRows.SHAPES.get(table).keys();
   for(var expected:rows.getOrDefault(table,List.of())){var matches=graph.rows().get(table).stream().filter(r->keys.stream().allMatch(k->Objects.equals(r.get(k),expected.get(k)))).toList();require(matches.size()==1,PROVENANCE_UNAVAILABLE);var actual=matches.getFirst();for(var cell:expected.entrySet())if(actual.containsKey(cell.getKey()))require(Objects.equals(cell.getValue(),actual.get(cell.getKey())),PROVENANCE_UNAVAILABLE);}
  }
 }
 private Scope scope(Held held,MeetingAccess.Locked gate,Phase phase){
  if(!(held instanceof PublicationWriteGuard.Scope scope)||scope.issuer()!=this||scope.gate!=gate||scope.holder!=transaction()||scope.phase!=phase)throw new IllegalArgumentException("Foreign, expired or out-of-phase publication guard");return scope;
 }
 private Object transaction(){Object holder=TransactionSynchronizationManager.getResource(source);if(holder==null||!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalArgumentException("Actual publication guard transaction required");return holder;}
 private static void writable(Map<String,Object> root){require(n(root,"ever_published")==0&&!"PUBLISHED".equals(root.get("stage"))&&"ACTIVE".equals(root.get("lifecycle"))&&"ACTIVE".equals(root.get("governance_state")),CAPABILITY_UNAVAILABLE);}
 private Map<String,Object> root(String meeting,TranscriptAcquisitionBudget.Ledger budget,boolean lock){var rows=headers("meeting","id,revision,ever_published,stage,lifecycle,governance_state","id",meeting,1,budget,lock);require(rows.size()==1,PROVENANCE_UNAVAILABLE);return rows.getFirst();}
 private List<Map<String,Object>> publications(String meeting,TranscriptAcquisitionBudget.Ledger budget,boolean lock){return headers("publication","id,meeting_id,confirmation_id,business_key,library_id,folder_id,state,revision","meeting_id",meeting,1,budget,lock);}
 private List<Map<String,Object>> finals(String meeting,TranscriptAcquisitionBudget.Ledger budget,boolean lock){return headers("final_confirmation","id,meeting_id,meeting_revision,source_manifest_hash,input_fingerprint","meeting_id",meeting,Math.min(256,budget.taskLimit()),budget,lock);}
 private List<Map<String,Object>> headers(String table,String columns,String key,String meeting,int maximum,TranscriptAcquisitionBudget.Ledger budget,boolean lock){
  budget.check();long count=j.queryForObject("SELECT COUNT(*) FROM (SELECT 1 FROM "+table+" WHERE "+key+"=? ORDER BY id LIMIT "+(maximum+1)+") write_history_count",Long.class,meeting);
  require(count<=maximum&&count<=budget.remainingRows(),BUDGET_EXCEEDED);int fields=columns.split(",").length;
  budget.admitCapture((count+1)*fields*2048,(count+1)*fields*512,(count+1)*fields*1024,2);
  var rows=j.queryForList("SELECT "+columns+" FROM "+table+" WHERE "+key+"=? ORDER BY id LIMIT "+(count+1)+(lock?" FOR UPDATE":""),meeting);require(rows.size()==count,PROVENANCE_UNAVAILABLE);
  var result=new ArrayList<Map<String,Object>>();for(var row:rows){budget.row(fields);var normalized=new LinkedHashMap<String,Object>();for(var cell:row.entrySet()){Object value=JdbcPublicationQueries.normalizeMetadata(cell.getValue());if(value instanceof String text){require(text.length()<=512,PROVENANCE_UNAVAILABLE);budget.capture(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,text.codePointCount(0,text.length()),text.length(),3);}normalized.put(cell.getKey(),value);}result.add(Collections.unmodifiableMap(normalized));}return List.copyOf(result);
 }
 private Map<String,String> submissionOriginals(Plan plan,boolean lock){var result=new TreeMap<String,String>();for(var row:plan.graph.rows().get("publication_submission")){
  String task=(String)row.get("task_id");long b=n(row,"request_manifest_bytes"),p=n(row,"request_manifest_scalars"),u=n(row,"request_manifest_utf16");admitText(plan.budget,b,p,u,false);
  var found=j.queryForList("SELECT request_manifest FROM publication_submission WHERE task_id=? AND publication_id=? AND OCTET_LENGTH(request_manifest)=? AND CHAR_LENGTH(request_manifest)=? AND OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2=?"+(lock?" FOR UPDATE":""),task,row.get("publication_id"),b,p,u);
  require(found.size()==1&&found.getFirst().get("request_manifest") instanceof String,PROVENANCE_UNAVAILABLE);require(result.put(task,(String)found.getFirst().get("request_manifest"))==null,PROVENANCE_UNAVAILABLE);
 }return Map.copyOf(result);}
 private Map<String,PublicationWriteHistory.ExecutionOriginal> executionOriginals(Plan plan,boolean lock){var result=new TreeMap<String,PublicationWriteHistory.ExecutionOriginal>();var tasks=plan.graph.rows().get("publication_submission").stream().map(r->r.get("task_id")).collect(java.util.stream.Collectors.toSet());
  for(var row:plan.graph.rows().get("external_execution")){if(!tasks.contains(row.get("task_id")))continue;plan.budget.row(6);
   var sizes=j.queryForList("SELECT OCTET_LENGTH(request_manifest) mb,CHAR_LENGTH(request_manifest) ms,OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2 mu,COALESCE(OCTET_LENGTH(no_effect_evidence),0) eb,COALESCE(CHAR_LENGTH(no_effect_evidence),0) es,COALESCE(OCTET_LENGTH(CONVERT(no_effect_evidence USING utf16))/2,0) eu FROM external_execution WHERE id=? AND task_id=? AND operation_id=? AND execution_no=?"+(lock?" FOR UPDATE":""),row.get("id"),row.get("task_id"),row.get("operation_id"),row.get("execution_no"));require(sizes.size()==1,PROVENANCE_UNAVAILABLE);var size=sizes.getFirst();long mb=n(size,"mb"),ms=n(size,"ms"),mu=n(size,"mu"),eb=n(size,"eb"),es=n(size,"es"),eu=n(size,"eu");admitText(plan.budget,mb,ms,mu,false);admitText(plan.budget,eb,es,eu,true);
   var found=j.queryForList("SELECT request_manifest,no_effect_evidence FROM external_execution WHERE id=? AND task_id=? AND operation_id=? AND execution_no=? AND OCTET_LENGTH(request_manifest)=? AND CHAR_LENGTH(request_manifest)=? AND OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2=? AND COALESCE(OCTET_LENGTH(no_effect_evidence),0)=? AND COALESCE(CHAR_LENGTH(no_effect_evidence),0)=? AND COALESCE(OCTET_LENGTH(CONVERT(no_effect_evidence USING utf16))/2,0)=?"+(lock?" FOR UPDATE":""),row.get("id"),row.get("task_id"),row.get("operation_id"),row.get("execution_no"),mb,ms,mu,eb,es,eu);require(found.size()==1&&found.getFirst().get("request_manifest") instanceof String,PROVENANCE_UNAVAILABLE);require(result.put((String)row.get("id"),new PublicationWriteHistory.ExecutionOriginal((String)found.getFirst().get("request_manifest"),(String)found.getFirst().get("no_effect_evidence")))==null,PROVENANCE_UNAVAILABLE);
  }return Map.copyOf(result);
 }
 private void admitText(TranscriptAcquisitionBudget.Ledger budget,long b,long p,long u,boolean optional){require((optional?b>=0:b>0)&&b<=Math.min(65536,authority.readBudget().maxManifestBytes())&&p>=0&&p<=b&&u>=p&&u<=b,BUDGET_EXCEEDED);if(b>0)budget.text(b,p,u,2,false);}
 private static long n(Map<String,Object> row,String key){require(row.get(key) instanceof Number,PROVENANCE_UNAVAILABLE);return ((Number)row.get(key)).longValue();}
}