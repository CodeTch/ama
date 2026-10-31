package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.meeting.*;
import ama.task.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One genuine gate and cumulative metadata budget. Persisted references never confer producer readiness. */
public final class JdbcWorkspaceOverview {
 public record Candidate(String id,String inputId,String taskId,String state) {}
 public record CandidatePage(List<Candidate> items,boolean hasMore,String detailPath){public CandidatePage{items=List.copyOf(items);}}
 public record PrivateSections(String savedProcessingInputId,String savedGenerationInputId,String templateRevisionId,String exclusionRevisionId,String finalConfirmationId,String publicationId,String publicationState,JdbcWorkspaceTasks.Page tasks,CandidatePage candidates,String verification){}
 private final JdbcTranscriptAuthority authority;private final JdbcMeetingAccess access;private final JdbcTemplate jdbc;private final WorkspaceCursor cursors;private final WorkspaceReadBudget.Limits limits;private final WorkspaceTaskScan scan;
 public JdbcWorkspaceOverview(JdbcTranscriptAuthority authority,JdbcTaskQueue queue,WorkspaceCursor cursors,WorkspaceReadBudget.Limits limits){
  TranscriptImportRuntime.requireCombined(queue,authority);if(!(authority.meetingAccess() instanceof JdbcMeetingAccess actual))throw new IllegalArgumentException("Genuine JDBC gate required");actual.requireProjectionSource(authority.dataSource());this.authority=authority;access=actual;this.cursors=Objects.requireNonNull(cursors);this.limits=Objects.requireNonNull(limits);jdbc=new JdbcTemplate(authority.dataSource());jdbc.setQueryTimeout(5);scan=new WorkspaceTaskScan(jdbc);
 }
 public Map<String,Object> read(Actor actor,String meeting){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw unavailable();ama.contract.OutboxContracts.uuid(meeting);WorkspaceText.check(actor.enterpriseId(),1020,true);
  var budget=new WorkspaceReadBudget(limits,()->Thread.currentThread().isInterrupted());budget.reserve(36,180,18432,6912,8448,4,false);
  try{return authority.transactionRunner().required(()->held(actor,meeting,budget));}catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException failure){throw new WorkspaceReadBudget.Unavailable(failure);}
 }
 private Map<String,Object> held(Actor actor,String meeting,WorkspaceReadBudget budget){
  var gate=authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.READ);var authentic=authority.fileRegistry().access(gate);var status=access.workspaceStatus(authentic,actor,meeting);budget.check();
  if(!Set.of("ENTRY","TRANSCRIPT_PENDING","TRANSCRIPT_FAILED","MINUTES_PENDING","MINUTES_FAILED","READY_TO_PUBLISH","PUBLISH_FAILED","PUBLISHED").contains(status.stage())||!status.revisionToken().matches("0|[1-9][0-9]{0,18}")||!status.accessRevisionToken().matches("0|[1-9][0-9]{0,18}")||Long.parseLong(status.revisionToken())==Long.MAX_VALUE||Long.parseLong(status.accessRevisionToken())==Long.MAX_VALUE)throw unavailable();
  if(status.owner())access.requirePrepared(authentic,actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
  String columns="transcript_revision_id,transcript_confirmation_id,minutes_revision_id,element_set_id"+(status.owner()?",template_revision_id,exclusion_revision_id,final_confirmation_id":"");
  var roots=metadata("SELECT "+columns+" FROM meeting WHERE id=?",1,512,budget,meeting);if(roots.size()!=1||status.infoId()==null)throw unavailable();var root=roots.getFirst();for(String key:columns.split(","))nullableId(root.get(key));
  String transcript=(String)root.get("transcript_revision_id"),confirmation=(String)root.get("transcript_confirmation_id"),body=(String)root.get("minutes_revision_id"),set=(String)root.get("element_set_id");
  var taskPage=status.owner()?scan.discover(meeting,20,null,budget):List.<WorkspaceTaskScan.Metadata>of();var allTasks=new TreeMap<String,WorkspaceTaskScan.Metadata>();for(var row:taskPage)allTasks.put(row.id(),row);
  var candidates=status.owner()?candidateRows(meeting,budget):List.<Map<String,Object>>of();
  for(var candidate:candidates){String task=id(candidate.get("task_id"));var taskRow=allTasks.get(task);if(taskRow==null){taskRow=scan.captureExact(meeting,task,budget);allTasks.put(task,taskRow);}if(!taskRow.spec().type().equals("MINUTES_GENERATE_V1")||!Objects.equals(taskRow.spec().input().id(),candidate.get("input_id"))||!Objects.equals(taskRow.spec().inputFingerprint(),candidate.get("input_fingerprint")))throw unavailable();}
  var parents=new WorkspaceTaskRelations(jdbc,budget,status.owner());parents.capture(List.copyOf(allTasks.values()));
  var info=parent(parents,"meeting_info_revision",status.infoId(),meeting);sequence(info,status.revisionToken());
  var roster=parent(parents,"roster_revision",status.rosterId(),meeting);if(roster!=null){same(roster,"state","CONFIRMED");sequence(roster,status.revisionToken());}
  var trans=parent(parents,"transcript_revision",transcript,meeting);if(trans!=null){same(trans,"kind","WORKING");sequence(trans,status.revisionToken());}
  var confirmed=parent(parents,"transcript_confirmation",confirmation,meeting);if(confirmed!=null){if(transcript==null)throw unavailable();same(confirmed,"revision_id",transcript);}
  var minutes=parent(parents,"minutes_revision",body,meeting);var elements=parent(parents,"element_set",set,meeting);if(minutes!=null)counter(minutes.get("revision"));if(elements!=null)counter(elements.get("revision"));
  rejectCandidate("minutes_revision_id",body,budget);rejectCandidate("element_set_id",set,budget);
  String processing=null,generation=null,publicationId=null,publicationState=null;
  if(status.owner()){
   processing=trans==null?null:nullableId(trans.get("processing_input_id"));generation=minutes==null?null:nullableId(minutes.get("generation_input_id"));
   parent(parents,"transcription_input",processing,meeting);parent(parents,"generation_input",generation,meeting);
   String template=(String)root.get("template_revision_id"),exclusion=(String)root.get("exclusion_revision_id"),finalId=(String)root.get("final_confirmation_id");
   if(template!=null)parents.declare("template_revision",template);parent(parents,"exclusion_revision",exclusion,meeting);var finalRow=parent(parents,"final_confirmation",finalId,meeting);
   if(finalRow!=null){same(finalRow,"info_revision_id",status.infoId());same(finalRow,"roster_revision_id",status.rosterId());same(finalRow,"transcript_confirmation_id",confirmation);same(finalRow,"minutes_revision_id",body);same(finalRow,"element_set_id",set);same(finalRow,"template_revision_id",template);same(finalRow,"exclusion_revision_id",exclusion);counter(finalRow.get("meeting_revision"));}
   var publications=metadata("SELECT id FROM publication WHERE meeting_id=? LIMIT 2",2,36,budget,meeting);if(publications.size()>1)throw unavailable();
   if(!publications.isEmpty()){publicationId=id(publications.getFirst().get("id"));var publication=parent(parents,"publication",publicationId,meeting);publicationState=(String)publication.get("state");if(!Set.of("READY","RUNNING","RECONCILING","FAILED","SUCCEEDED").contains(publicationState))throw unavailable();counter(publication.get("revision"));parent(parents,"final_confirmation",id(publication.get("confirmation_id")),meeting);}
   for(var candidate:candidates){var row=parent(parents,"generation_candidate",id(candidate.get("id")),meeting);for(String key:List.of("input_id","task_id","minutes_revision_id","element_set_id","state","input_fingerprint"))same(row,key,candidate.get(key));same(row,"state","PENDING");parent(parents,"generation_input",id(row.get("input_id")),meeting);parent(parents,"minutes_revision",nullableId(row.get("minutes_revision_id")),meeting);parent(parents,"element_set",id(row.get("element_set_id")),meeting);}
  }
  // All control/domain sets are now complete. Tasks are acquired only after this single parent phase.
  parents.lockAndCompare();for(var row:allTasks.values())scan.lockExact(row,budget);
  budget.reserve(1,24,2048,2048,2048,4,true);var result=new LinkedHashMap<String,Object>();result.put("meetingId",meeting);result.put("revisionToken",status.revisionToken());result.put("accessRevisionToken",status.accessRevisionToken());result.put("stage",status.stage());result.put("relation",status.owner()?"OWNER":"MEMBER");result.put("navigationState",!status.owner()?"MEMBER_READ_ONLY":status.published()?"PUBLISHED_READ_ONLY":"CHECK_PRODUCER");result.put("contentEligibility","RECHECK_REQUIRED");result.put("lifecycle",status.lifecycle());result.put("governanceState",status.governance());
  result.put("saved",new JdbcWorkspaceNavigationReader.SavedReferences(status.infoId(),status.rosterId(),transcript,confirmation,body,set));
  if(status.owner()){
   var page=page(actor,meeting,taskPage,budget,parents);var items=new ArrayList<Candidate>();for(var row:candidates.subList(0,Math.min(20,candidates.size()))){budget.reserve(1,5,256,256,256,4,true);items.add(new Candidate(id(row.get("id")),id(row.get("input_id")),id(row.get("task_id")),"PENDING"));}
   result.put("privateSectionsState","AVAILABLE");result.put("privateSections",new PrivateSections(processing,generation,(String)root.get("template_revision_id"),(String)root.get("exclusion_revision_id"),(String)root.get("final_confirmation_id"),publicationId,publicationState,page,new CandidatePage(items,candidates.size()>20,"/api/v1/meetings/"+meeting+"/minutes/candidates"),"PERSISTED_REFERENCES_RECHECK_PRODUCER"));
  }
  budget.check();return Collections.unmodifiableMap(result);
 }
 private JdbcWorkspaceTasks.Page page(Actor actor,String meeting,List<WorkspaceTaskScan.Metadata> rows,WorkspaceReadBudget budget,WorkspaceTaskRelations parents){
  var items=new ArrayList<JdbcWorkspaceTasks.Item>();for(var row:rows.subList(0,Math.min(20,rows.size()))){budget.reserve(1,8,1024,1024,1024,4,true);String path=parents.detailPath(row);items.add(new JdbcWorkspaceTasks.Item(row.id(),row.spec().type(),row.state(),Long.toString(row.attempt()),Long.toString(row.spec().businessVersion()),row.spec().input().id(),path));}
  budget.reserve(1,2,2048,2048,2048,4,true);boolean more=rows.size()>20;return new JdbcWorkspaceTasks.Page(items,more?cursors.encode(actor.enterpriseId(),"tasks:"+meeting+":ALL",rows.get(19).position().encode()):null,more,"PERSISTED_RELATION");
 }
 private List<Map<String,Object>> candidateRows(String meeting,WorkspaceReadBudget budget){return metadata("SELECT id,meeting_id,input_id,task_id,minutes_revision_id,element_set_id,state,input_fingerprint FROM generation_candidate FORCE INDEX (candidate_pending) WHERE meeting_id=? AND state='PENDING' ORDER BY created_at DESC,id DESC LIMIT 21",21,512,budget,meeting);}
 private List<Map<String,Object>> metadata(String sql,int maximum,int bytes,WorkspaceReadBudget budget,Object...args){budget.reserve(maximum,maximum*8L,maximum*(long)bytes,maximum*(long)bytes,maximum*(long)bytes,4,false);String[] columns=sql.substring(7,sql.indexOf(" FROM ")).split(",");var rows=jdbc.query(sql,rs->{var result=new ArrayList<Map<String,Object>>();while(rs.next()){budget.check();if(result.size()>=maximum)throw unavailable();var row=new LinkedHashMap<String,Object>();for(String column:columns)row.put(column,rs.getObject(column));result.add(Collections.unmodifiableMap(row));}return List.copyOf(result);},args);budget.check();return rows;}
 private void rejectCandidate(String column,String id,WorkspaceReadBudget budget){if(id!=null&&!metadata("SELECT id FROM generation_candidate WHERE "+column+"=? LIMIT 1",1,36,budget,id).isEmpty())throw unavailable();}
 private static Map<String,Object> parent(WorkspaceTaskRelations plan,String table,String id,String meeting){if(id==null)return null;var row=plan.declare(table,id);same(row,"meeting_id",meeting);return row;}
 private static String id(Object value){if(!(value instanceof String text))throw unavailable();try{ama.contract.OutboxContracts.uuid(text);}catch(RuntimeException invalid){throw unavailable();}return text;}
 private static String nullableId(Object value){return value==null?null:id(value);}
 private static void same(Map<String,Object> row,String key,Object expected){if(!Objects.equals(row.get(key),expected))throw unavailable();}
 private static long counter(Object value){if(!(value instanceof Number number))throw unavailable();long n=number.longValue();if(n<0||n==Long.MAX_VALUE||!number.toString().equals(Long.toString(n)))throw unavailable();return n;}
 private static void sequence(Map<String,Object> row,String maximum){if(row==null||counter(row.get("revision"))>Long.parseLong(maximum))throw unavailable();}
 private static WorkspaceReadBudget.Unavailable unavailable(){return new WorkspaceReadBudget.Unavailable();}
}
