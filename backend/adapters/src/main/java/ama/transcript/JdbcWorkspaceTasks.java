package ama.transcript;
import ama.access.MeetingAccess;import ama.contract.IdentityContracts.Actor;import ama.meeting.*;import ama.storage.FileRegistry;import ama.task.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.support.TransactionSynchronizationManager;import java.util.*;
/** Owner-only navigation over registered persisted relations. No content, ACK or readiness proof. */
public final class JdbcWorkspaceTasks {
 public record Item(String taskId,String kind,String state,String attemptToken,String businessRevisionToken,String inputId,String detailPath){}
 public record Page(List<Item> items,String nextCursor,boolean hasMore,String verification){public Page{items=List.copyOf(items);}}
 private final JdbcTranscriptAuthority authority;private final WorkspaceCursor cursors;private final WorkspaceReadBudget.Limits limits;private final WorkspaceTaskScan scan;private final JdbcTemplate jdbc;
 private static final Set<String> KINDS=Set.of("ALL","TRANSCRIPT_IMPORT_V1","MINUTES_GENERATE_V1","KNOWLEDGE_PUBLISH_V1");
 public JdbcWorkspaceTasks(JdbcTranscriptAuthority authority,JdbcTaskQueue queue,WorkspaceCursor cursors,WorkspaceReadBudget.Limits limits){
  TranscriptImportRuntime.requireCombined(queue,authority);if(!(authority.meetingAccess() instanceof JdbcMeetingAccess access))throw new IllegalArgumentException("Actual JDBC owner gate required");access.requireProjectionSource(authority.dataSource());this.authority=authority;this.cursors=Objects.requireNonNull(cursors);this.limits=Objects.requireNonNull(limits);jdbc=new JdbcTemplate(authority.dataSource());jdbc.setQueryTimeout(5);scan=new WorkspaceTaskScan(jdbc);
 }
 public Page read(Actor actor,String meeting,String kind,int limit,String cursor){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw unavailable();request(actor,meeting,kind,limit);String scope="tasks:"+meeting+":"+kind;
  var after=cursor==null?null:WorkspaceTaskScan.Position.parse(cursors.decode(actor.enterpriseId(),scope,cursor));var budget=new WorkspaceReadBudget(limits,()->Thread.currentThread().isInterrupted());gateBudget(budget);
  try{return authority.transactionRunner().required(()->{var gate=authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);var plan=captureHeld(actor,meeting,kind,limit,after,gate,budget);lockParents(plan);return finish(plan);});}
  catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException failure){throw new WorkspaceReadBudget.Unavailable(failure);}
 }
 /** Composition exposes separate parent/task phases so an outer workspace never locks parents after tasks. */
 final class Plan {
  private final Object holder;private final Actor actor;private final String meeting,kind;private final int limit;private final WorkspaceReadBudget budget;private final WorkspaceTaskRelations relations;private final List<WorkspaceTaskScan.Metadata> rows;private int phase;
  private Plan(Actor actor,String meeting,String kind,int limit,WorkspaceReadBudget budget,WorkspaceTaskRelations relations,List<WorkspaceTaskScan.Metadata> rows){this.holder=holder();this.actor=actor;this.meeting=meeting;this.kind=kind;this.limit=limit;this.budget=budget;this.relations=relations;this.rows=rows;}
  private JdbcWorkspaceTasks issuer(){return JdbcWorkspaceTasks.this;}
 }
 Plan captureHeld(Actor actor,String meeting,String kind,int limit,WorkspaceTaskScan.Position after,FileRegistry.LockedMeeting gate,WorkspaceReadBudget budget){
  request(actor,meeting,kind,limit);holder();gateBudget(budget);authority.meetingAccess().requirePrepared(authority.fileRegistry().access(gate),actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
  var rows=scan.discover(meeting,limit,after,budget);var relations=new WorkspaceTaskRelations(jdbc,budget);relations.capture(rows);return new Plan(actor,meeting,kind,limit,budget,relations,rows);
 }
 void lockParents(Plan plan){guard(plan,0);plan.phase=1;plan.relations.lockAndCompare();plan.phase=2;}
 Page finish(Plan plan){guard(plan,2);plan.phase=3;for(var row:plan.rows.stream().sorted(Comparator.comparing(WorkspaceTaskScan.Metadata::id)).toList())scan.lockExact(row,plan.budget);
  int count=Math.min(plan.limit,plan.rows.size());var items=new ArrayList<Item>();for(var row:plan.rows.subList(0,count)){if(!plan.kind.equals("ALL")&&!plan.kind.equals(row.spec().type()))continue;
   plan.budget.reserve(1,8,1024,1024,1024,4,true);String path="/api/v1/meetings/"+plan.meeting;path+=switch(row.spec().type()){case "TRANSCRIPT_IMPORT_V1"->"/processing-tasks/"+row.id();case "MINUTES_GENERATE_V1"->"/minutes/generation-tasks/"+row.id();case "KNOWLEDGE_PUBLISH_V1"->"/publications/"+row.spec().input().id();default->throw unavailable();};
   items.add(new Item(row.id(),row.spec().type(),row.state(),Long.toString(row.attempt()),Long.toString(row.spec().businessVersion()),row.spec().input().id(),path));
  }
  boolean more=plan.rows.size()>plan.limit;plan.budget.reserve(1,2,2048,2048,2048,4,true);String next=more?cursors.encode(plan.actor.enterpriseId(),"tasks:"+plan.meeting+":"+plan.kind,plan.rows.get(count-1).position().encode()):null;plan.budget.check();plan.phase=4;return new Page(items,next,more,"PERSISTED_RELATION");
 }
 private void guard(Plan plan,int phase){if(plan==null||plan.issuer()!=this||plan.holder!=holder()||plan.phase!=phase)throw unavailable();plan.budget.check();}
 private Object holder(){Object value=TransactionSynchronizationManager.getResource(authority.dataSource());if(value==null||!TransactionSynchronizationManager.isActualTransactionActive())throw unavailable();return value;}
 private static void request(Actor actor,String meeting,String kind,int limit){ama.contract.OutboxContracts.uuid(meeting);WorkspaceText.check(actor.enterpriseId(),1020,true);if(!KINDS.contains(kind)||limit<1||limit>50)throw new IllegalArgumentException("Invalid task page");}
 private static void gateBudget(WorkspaceReadBudget budget){budget.reserve(12,60,6144,2304,2816,4,false);}
 private static WorkspaceReadBudget.Unavailable unavailable(){return new WorkspaceReadBudget.Unavailable();}
}
