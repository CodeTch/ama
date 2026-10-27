package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.*;
import ama.db.TransactionRunner;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** B03 saved projection: one genuine gate, canonical parents/complete child sets, then admitted exact transfers. */
public final class JdbcMeetingReadProjection {
 private final DataSource source;private final JdbcTemplate jdbc;private final TransactionRunner tx;private final JdbcMeetingAccess access;private final WorkspaceReadBudget.Limits limits;
 public JdbcMeetingReadProjection(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){this(source,tx,access,WorkspaceReadBudget.DEFAULT);}
 public JdbcMeetingReadProjection(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,WorkspaceReadBudget.Limits limits){access.requireProjectionSource(source);this.source=source;jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);this.tx=tx;this.access=access;this.limits=limits;}
 public Saved saved(Actor actor,String meeting) {
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new WorkspaceReadBudget.Unavailable();
  ama.contract.OutboxContracts.uuid(meeting);var budget=new WorkspaceReadBudget(limits,()->Thread.currentThread().isInterrupted());
  try{return tx.required(()->{
   // Status-only identity/subject/root/grant rows have fixed schema bounds; reserve before gate transfer.
   budget.reserve(12,60,6144,2304,2816,4,false);
   var held=(JdbcMeetingAccess.Held)access.lock(actor,meeting);access.require(held,MeetingAccess.Action.READ);var root=held.root;
   String draftId=null;
   if(root.owner().equals(actor.enterpriseId())) {
    budget.reserve(1,3,128,128,128,4,false);
    var latest=jdbc.queryForList("SELECT id,state FROM roster_revision WHERE meeting_id=? ORDER BY revision DESC LIMIT 1",meeting);
    if(!latest.isEmpty()&&"DRAFT".equals(latest.getFirst().get("state")))draftId=(String)latest.getFirst().get("id");
   }
   var read=new BoundedMeetingMetadata(source,access,actor,meeting,held,budget);
   var info=read.capture("meeting_info_revision","id meeting_id name category_id summary actual_meeting_date","id=? AND meeting_id=?",1,root.infoId(),meeting);one(info);
   var requested=new TreeMap<String,String>();if(root.rosterId()!=null)requested.put(root.rosterId(),"CONFIRMED");if(draftId!=null&&requested.putIfAbsent(draftId,"DRAFT")!=null)throw new WorkspaceReadBudget.Unavailable();
   // Domain capture follows MinutesGraphCapture table order. Initial member authorization already holds
   // effective roster/member provenance; the same meeting root X lock serializes legal graph writers.
   for(var entry:requested.entrySet())one(read.capture("roster_revision","id","id=? AND meeting_id=? AND state=?",1,entry.getKey(),meeting,entry.getValue()));
   var rosterSelections=new TreeMap<String,BoundedMeetingMetadata.Selection>();
   for(String roster:requested.keySet())rosterSelections.put(roster,read.capture("roster_member","member_id enterprise_id display_name member_kind","roster_id=?",128,roster));
   // Every requested parent, complete set and cumulative field/copy/JSON reservation now exists.
   var value=read.read(info).getFirst();var members=new HashMap<String,List<Member>>();
   for(var entry:rosterSelections.entrySet())members.put(entry.getKey(),read.read(entry.getValue()).stream().map(row->new Member((String)row.get("member_id"),(String)row.get("enterprise_id"),(String)row.get("display_name"),(String)row.get("member_kind"))).toList());
   String date=(String)value.get("actual_meeting_date");budget.check();
   return new Saved(meeting,root.revision(),root.accessRevision(),root.stage(),(String)value.get("name"),(String)value.get("category_id"),(String)value.get("summary"),date==null?null:LocalDate.parse(date),root.rosterId(),root.rosterId()==null?List.of():members.get(root.rosterId()),draftId,draftId==null?null:members.get(draftId));
  });}catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException failure){throw new WorkspaceReadBudget.Unavailable(failure);}
 }
 private static void one(BoundedMeetingMetadata.Selection selected){if(selected.size()!=1)throw new WorkspaceReadBudget.Unavailable();}
}
