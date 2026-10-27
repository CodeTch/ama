package ama.transcript;
import ama.meeting.*;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
/** Genuine combined authority/access/transaction with JDBC doubles; no MySQL acceptance. */
class Task12WorkspaceOverviewTest extends Task12WorkspaceTasksTest {
 boolean member;boolean candidatePending;boolean hideCandidateTaskFromPage;String candidateTask=T;String candidateInput=I;
 @Override List<Map<String,Object>> rows(String q,Map<Integer,Object> args){
  if(q.contains(" FROM meeting ")){var root=new LinkedHashMap<>(super.rows(q,args).getFirst());root.put("info_revision_id",C);root.put("roster_revision_id",member?B:null);for(String key:List.of("transcript_revision_id","transcript_confirmation_id","minutes_revision_id","element_set_id","template_revision_id","exclusion_revision_id","final_confirmation_id"))root.put(key,null);return List.of(root);}
  if(q.contains(" FROM meeting_info_revision "))return List.of(row("id",C,"meeting_id",M,"revision",1L));
  if(hideCandidateTaskFromPage&&q.contains(" FROM durable_task ")&&!q.contains(" WHERE id=?"))return List.of();
  if(candidatePending&&q.contains(" FROM generation_candidate "))return List.of(row("id",C,"meeting_id",M,"input_id",candidateInput,"task_id",candidateTask,"minutes_revision_id",null,"element_set_id",B,"state","PENDING","input_fingerprint","b".repeat(64)));
  if(q.contains(" FROM generation_candidate ")||q.contains(" FROM publication ")&&!parents.containsKey("publication"))return List.of();
  if(member&&q.contains(" FROM meeting_access_grant "))return List.of(row("roster_id",B));
  if(member&&q.startsWith("SELECT id FROM roster_revision"))return List.of(row("id",B));
  if(member&&q.contains(" FROM roster_revision "))return List.of(row("id",B,"meeting_id",M,"revision",1L,"state","CONFIRMED"));
  if(member&&q.contains(" FROM roster_member "))return List.of(row("member_id",I));
  return super.rows(q,args);
 }
 JdbcWorkspaceOverview overview(){return new JdbcWorkspaceOverview(authority,queue,new WorkspaceCursor(),WorkspaceReadBudget.DEFAULT);}
 @Test void ownerWorkspaceComposesSavedPrivateAndTaskReferencesWithOneGateAndTaskPhaseLast(){importTask();var value=overview().read(actor(),M);assertEquals("OWNER",value.get("relation"));assertEquals("9007199254740993",value.get("revisionToken"));var details=(JdbcWorkspaceOverview.PrivateSections)value.get("privateSections");assertNotNull(details);assertEquals(T,details.tasks().items().getFirst().taskId());assertEquals(1,sql.stream().filter(q->q.startsWith("SELECT enterprise_id,status")).count());
  var locks=sql.stream().filter(q->q.endsWith("FOR UPDATE")&&!q.contains(" FROM meeting ")).toList();int task=-1;for(int i=0;i<locks.size();i++)if(locks.get(i).contains(" FROM durable_task ")){task=i;break;}assertTrue(task>0);assertTrue(locks.subList(task,locks.size()).stream().allMatch(q->q.contains(" FROM durable_task ")));assertTrue(sql.stream().noneMatch(q->q.contains("SELECT *")||q.contains("summary")||q.contains("config_manifest")||q.contains("document_url")));
 }
 @Test void memberHasNoPrivateSectionsAndDoesNotAcquirePrivateTaskOrInputRows(){owner="different";member=true;var value=overview().read(actor(),M);assertEquals("MEMBER",value.get("relation"));assertFalse(value.containsKey("privateSections"));assertTrue(sql.stream().noneMatch(q->q.contains("durable_task")||q.contains("transcription_input")||q.contains(" FROM publication ")||q.contains("processing_input_id")||q.contains("generation_input_id")));}
 @Test void overviewRejectsAmbientTransactionBeforeAnySql(){assertThrows(WorkspaceReadBudget.Unavailable.class,()->tx.required(()->overview().read(actor(),M)));assertTrue(sql.isEmpty());}
 @Test void pendingCandidateCannotClaimAnImportTaskAsItsGenerationProducer(){importTask();candidatePending=true;assertThrows(WorkspaceReadBudget.Unavailable.class,()->overview().read(actor(),M));assertTrue(sql.stream().noneMatch(q->q.contains(" FROM durable_task WHERE id=? FOR UPDATE")));}
 @Test void lowBudgetAndDisabledIdentityStopBeforePrivateReads(){var limited=new JdbcWorkspaceOverview(authority,queue,new WorkspaceCursor(),new WorkspaceReadBudget.Limits(1,1,1,1,1,1,java.time.Duration.ofSeconds(5)));assertThrows(WorkspaceReadBudget.Unavailable.class,()->limited.read(actor(),M));assertTrue(sql.isEmpty());disabled=true;assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->overview().read(actor(),M));assertTrue(sql.stream().noneMatch(q->q.contains("generation_candidate")||q.contains("durable_task")));}
 @Test void candidateTaskOutsideDisplayedPageStillJoinsCompleteParentAndTaskLockSets(){
  importTask();var task=taskRows.getFirst();task.put("task_type","MINUTES_GENERATE_V1");task.put("pool","MINUTES_GENERATE");task.put("input_kind","GENERATION_INPUT");
  parents.put("generation_input",row("id",I,"meeting_id",M,"transcript_confirmation_id",C,"template_revision_id",B,"exclusion_revision_id",B,"base_revision",7L,"input_fingerprint","b".repeat(64),"scope","FULL","target_element_id",null,"material_snapshot_id",null));
  parents.put("transcript_confirmation",row("id",C,"meeting_id",M,"revision_id",B));parents.put("transcript_revision",row("id",B,"meeting_id",M,"kind","WORKING"));parents.put("template_revision",row("id",B));parents.put("exclusion_revision",row("id",B,"meeting_id",M));parents.put("element_set",row("id",B,"meeting_id",M,"revision",1L));
  candidatePending=true;hideCandidateTaskFromPage=true;var value=overview().read(actor(),M);var details=(JdbcWorkspaceOverview.PrivateSections)value.get("privateSections");assertTrue(details.tasks().items().isEmpty());assertEquals(C,details.candidates().items().getFirst().id());assertTrue(sql.stream().anyMatch(q->q.contains(" FROM generation_candidate ")&&q.endsWith("FOR UPDATE")));assertTrue(sql.stream().anyMatch(q->q.contains(" FROM durable_task WHERE id=? FOR UPDATE")));
 }
}
