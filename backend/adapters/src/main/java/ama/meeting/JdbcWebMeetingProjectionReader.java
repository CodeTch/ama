package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.WorkspaceContracts.*;
import ama.db.TransactionRunner;
import java.time.*;import java.util.*;import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Independently authorized bounded list rows, sharing one cumulative request ledger. */
public final class JdbcWebMeetingProjectionReader {
 private final DataSource source;private final TransactionRunner tx;private final JdbcMeetingAccess access;private final JdbcTemplate jdbc;private final WorkspaceCursor cursors;private final WorkspaceReadBudget.Limits limits;
 public JdbcWebMeetingProjectionReader(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,WorkspaceCursor cursors,WorkspaceReadBudget.Limits limits){access.requireProjectionSource(source);this.source=source;this.tx=tx;this.access=access;this.cursors=cursors;this.limits=limits;jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);}
 public MeetingPage list(Actor actor,String scope,int limit,String cursor) {return list(actor,scope,limit,cursor,MeetingListFilter.NONE);}
 public MeetingPage list(Actor actor,String scope,int limit,String cursor,MeetingListFilter filter) {
  Objects.requireNonNull(filter);
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new WorkspaceReadBudget.Unavailable();
  if(!Set.of("owned","joined").contains(scope)||limit<1||limit>50)throw new IllegalArgumentException("Invalid list request");
  if(filter.topic()!=null&&!scope.equals("owned"))throw new IllegalArgumentException("Personal topics require owned scope");
  var budget=new WorkspaceReadBudget(limits,()->Thread.currentThread().isInterrupted());String cursorScope="meeting-list-v2/"+scope+"/"+filter.cursorKey();
  budget.reserve(2,4,4096,2048,2048,4,true);String decoded=cursor==null?null:cursors.decode(actor.enterpriseId(),cursorScope,cursor);var after=decoded==null?null:MeetingListScan.Position.parse(decoded);
  try {
   var candidates=tx.required(()->{budget.check();access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);if(filter.topic()!=null&&!filter.topic().equals("__none")){budget.reserve(1,1,64,64,64,2,false);if(jdbc.queryForObject("SELECT COUNT(*) FROM topic WHERE id=? AND owner_id=?",Long.class,filter.topic(),actor.enterpriseId())!=1)throw new MeetingAccess.AccessDeniedException();}return new MeetingListScan(jdbc).discover(actor,scope,limit,after,budget);});
   var items=new ArrayList<MeetingItem>();int scanned=Math.min(limit,candidates.size());
   for(int i=0;i<scanned;i++){
    var candidate=candidates.get(i);budget.check();
    try {var item=tx.required(()->{var current=row(actor,scope,candidate,budget);if(current!=null&&filter.topic()!=null&&!topicMatches(actor,current.id(),filter.topic(),budget))return null;return current;});if(item!=null&&filter.matches(item))items.add(item);}
    catch(MeetingAccess.AccessDeniedException denied){/* No denied candidate metadata is emitted. Cursor still advances over it. */}
   }
   boolean more=candidates.size()>scanned;String next=more?cursors.encode(actor.enterpriseId(),cursorScope,candidates.get(scanned-1).position().encode()):null;budget.check();return new MeetingPage(items,next,more);
  }catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException e){throw new WorkspaceReadBudget.Unavailable(e);}
 }
 private boolean topicMatches(Actor actor,String meeting,String topic,WorkspaceReadBudget budget){
  budget.reserve(2,4,1024,1024,1024,2,false);var rows=jdbc.queryForList("SELECT m.topic_id,t.owner_id FROM meeting_topic m LEFT JOIN topic t ON t.id=m.topic_id WHERE m.meeting_id=? ORDER BY m.topic_id LIMIT 2",meeting);
  if(rows.size()>1||rows.stream().anyMatch(row->!actor.enterpriseId().equals(row.get("owner_id"))))throw new WorkspaceReadBudget.Unavailable();return topic.equals("__none")?rows.isEmpty():rows.size()==1&&topic.equals(rows.getFirst().get("topic_id"));
 }
 static String workspaceStage(String stage,boolean transcript,boolean confirmed,boolean minutes,boolean frozen){if(!stage.equals("ENTRY"))return stage;return frozen?"READY_TO_PUBLISH":minutes?"MINUTES_PENDING":confirmed?"MINUTES_PENDING":transcript?"TRANSCRIPT_PENDING":"ENTRY";}
 private MeetingItem row(Actor actor,String scope,MeetingListScan.Candidate candidate,WorkspaceReadBudget budget) {
  budget.reserve(12,60,6144,2304,2816,4,false);String meeting=candidate.position().id();var held=(JdbcMeetingAccess.Held)access.lock(actor,meeting);access.require(held,MeetingAccess.Action.READ);var root=held.root;
  if(root.accessRevision()<0||root.accessRevision()==Long.MAX_VALUE)throw new WorkspaceReadBudget.Unavailable();
  if(scope.equals("owned")&&!root.owner().equals(actor.enterpriseId()))return null;
  budget.reserve(1,10,768,768,768,4,false);
  record Phase(LocalDateTime updated,boolean transcript,boolean confirmed,boolean minutes,boolean frozen){}
  var phases=jdbc.query("SELECT updated_at,(transcript_revision_id IS NOT NULL OR EXISTS(SELECT 1 FROM transcript_revision t WHERE t.meeting_id=meeting.id LIMIT 1)) transcript_ready,(transcript_confirmation_id IS NOT NULL) confirmed,(minutes_revision_id IS NOT NULL AND element_set_id IS NOT NULL) minutes_ready,(final_confirmation_id IS NOT NULL) final_ready FROM meeting WHERE id=?",(r,n)->new Phase(r.getObject("updated_at",LocalDateTime.class),r.getBoolean("transcript_ready"),r.getBoolean("confirmed"),r.getBoolean("minutes_ready"),r.getBoolean("final_ready")),meeting);
  if(phases.size()!=1||!phases.getFirst().updated().equals(candidate.position().updated())||root.revision()!=candidate.revision()||!Objects.equals(root.infoId(),candidate.info())||!root.stage().equals(candidate.stage()))throw new WorkspaceReadBudget.Unavailable();
  if(scope.equals("joined")){budget.reserve(9,36,2560,1536,1792,4,false);if(!access.confirmedParticipant(held,actor,meeting))return null;}
  if(root.infoId()==null)throw new WorkspaceReadBudget.Unavailable();
  // Complete response metadata is reserved before selected name/category/date text is fetched.
  budget.reserve(1,8,512,512,512,4,true);
  var reader=new BoundedMeetingMetadata(source,access,actor,meeting,held,budget);var selection=reader.capture("meeting_info_revision","name category_id actual_meeting_date","id=? AND meeting_id=?",1,root.infoId(),meeting);
  if(selection.size()!=1)throw new WorkspaceReadBudget.Unavailable();
  BoundedMeetingMetadata.Selection roster=null;
  if(root.rosterId()!=null){var parent=reader.capture("roster_revision","id","id=? AND meeting_id=? AND state=?",1,root.rosterId(),meeting,"CONFIRMED");if(parent.size()!=1)throw new WorkspaceReadBudget.Unavailable();roster=reader.capture("roster_member","display_name","roster_id=?",128,root.rosterId());}
  var value=reader.read(selection).getFirst();var names=roster==null?List.<String>of():reader.read(roster).stream().map(row->(String)row.get("display_name")).toList();budget.reserve(1,2,128,128,128,4,true);boolean owner=root.owner().equals(actor.enterpriseId());String date=(String)value.get("actual_meeting_date");
  // List does not evaluate producer readiness or confer edit permission; owner opens workspace for that decision.
  return new MeetingItem(meeting,(String)value.get("name"),(String)value.get("category_id"),date==null?null:LocalDate.parse(date),root.stage(),root.lifecycle(),owner?"OWNER":"MEMBER",!owner?"MEMBER_READ_ONLY":root.published()?"PUBLISHED_READ_ONLY":"CHECK_WORKSPACE",Long.toString(root.revision()),Long.toString(root.accessRevision()),names,candidate.position().updated().toInstant(ZoneOffset.UTC).toString(),workspaceStage(root.stage(),phases.getFirst().transcript(),phases.getFirst().confirmed(),phases.getFirst().minutes(),phases.getFirst().frozen()));
 }
}
