package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.db.TransactionRunner;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** DB-only saved navigation references, not content provenance, readiness, or write authority.
 * One genuine meeting gate, one holder, one cumulative budget. No standalone producer is nested.
 * This primitive is not yet the complete workspace HTTP composition (owner task/input/final sections).
 */
public final class JdbcWorkspaceNavigationReader {
 public record SavedReferences(String infoRevisionId,String rosterRevisionId,String transcriptRevisionId,
   String transcriptConfirmationId,String minutesRevisionId,String elementSetId){}
 public record Navigation(String meetingId,String revisionToken,String accessRevisionToken,String stage,
   String relation,String navigationState,String contentEligibility,SavedReferences saved){}
 private record Pointers(String transcript,String confirmation,String body,String elements){}
 private record Parent(String id,String meeting,Long revision,String qualifier,String revisionId){}
 private final TransactionRunner tx;private final JdbcMeetingAccess access;private final JdbcTemplate jdbc;private final WorkspaceReadBudget.Limits limits;
 public JdbcWorkspaceNavigationReader(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,WorkspaceReadBudget.Limits limits){
  access.requireProjectionSource(source);this.tx=Objects.requireNonNull(tx);this.access=access;this.limits=Objects.requireNonNull(limits);jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);
 }
 public Navigation read(Actor actor,String meeting){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw unavailable();
  ama.contract.OutboxContracts.uuid(meeting);WorkspaceText.check(actor.enterpriseId(),1020,true);
  var budget=new WorkspaceReadBudget(limits,()->Thread.currentThread().isInterrupted());
  // Actual status-only identity/root/grant acquisition, including require()'s held-root recheck.
  budget.reserve(12,60,6144,2304,2816,4,false);
  try{return tx.required(()->readHeld(actor,meeting,budget));}
  catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException failure){throw new WorkspaceReadBudget.Unavailable(failure);}
 }
 private Navigation readHeld(Actor actor,String meeting,WorkspaceReadBudget budget){
  budget.check();var gate=(JdbcMeetingAccess.Held)access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.READ);var root=gate.root;
  counter(root.revision());counter(root.accessRevision());if(!MeetingListScan.STAGES.contains(root.stage()))throw unavailable();
  // Root is already X-held; no text bodies, private input/history/settings or producer manifests are selected.
  budget.reserve(1,4,144,144,144,4,false);
  var pointers=jdbc.query("SELECT transcript_revision_id,transcript_confirmation_id,minutes_revision_id,element_set_id FROM meeting WHERE id=?",(r,n)->new Pointers(r.getString("transcript_revision_id"),r.getString("transcript_confirmation_id"),r.getString("minutes_revision_id"),r.getString("element_set_id")),meeting);
  budget.check();if(pointers.size()!=1||root.infoId()==null)throw unavailable();var current=pointers.getFirst();
  for(String id:Arrays.asList(root.infoId(),root.rosterId(),current.transcript(),current.confirmation(),current.body(),current.elements()))if(id!=null)storedId(id);
  // Same control/domain order as deployed saved producers. PK parents remain locked through commit.
  parent("meeting_info_revision",root.infoId(),meeting,root.revision(),null,budget);
  parent("roster_revision",root.rosterId(),meeting,root.revision(),"CONFIRMED",budget);
  parent("transcript_revision",current.transcript(),meeting,root.revision(),"WORKING",budget);
  var confirmation=parent("transcript_confirmation",current.confirmation(),meeting,root.revision(),null,budget);
  if(confirmation!=null&&(current.transcript()==null||!current.transcript().equals(confirmation.revisionId())))throw unavailable();
  parent("minutes_revision",current.body(),meeting,root.revision(),null,budget);
  parent("element_set",current.elements(),meeting,root.revision(),null,budget);
  // A candidate's objects are private even after disposition; adoption creates distinct saved IDs.
  rejectCandidate("minutes_revision_id",current.body(),budget);rejectCandidate("element_set_id",current.elements(),budget);
  budget.reserve(1,12,1024,1024,1024,4,true);budget.check();boolean owner=root.owner().equals(actor.enterpriseId());
  return new Navigation(meeting,Long.toString(root.revision()),Long.toString(root.accessRevision()),root.stage(),owner?"OWNER":"MEMBER",
   !owner?"MEMBER_READ_ONLY":root.published()?"PUBLISHED_READ_ONLY":"CHECK_PRODUCER","RECHECK_REQUIRED",
   new SavedReferences(root.infoId(),root.rosterId(),current.transcript(),current.confirmation(),current.body(),current.elements()));
 }
 private Parent parent(String table,String id,String meeting,long maximum,String qualifier,WorkspaceReadBudget budget){
  if(id==null)return null;
  // The selected columns are DDL-bounded UUIDs, one ASCII kind/state, and a counter; never content.
  budget.reserve(1,5,256,256,256,4,false);
  String extra=table.equals("roster_revision")?",state":table.equals("transcript_revision")?",kind":table.equals("transcript_confirmation")?",revision_id":"";
  boolean confirming=table.equals("transcript_confirmation");
  var rows=jdbc.query("SELECT id,meeting_id"+(confirming?"":",revision")+extra+" FROM "+table+" WHERE id=? FOR UPDATE",(r,n)->new Parent(r.getString("id"),r.getString("meeting_id"),confirming?null:requiredCounter(r),qualifier==null?null:r.getString(table.equals("roster_revision")?"state":"kind"),table.equals("transcript_confirmation")?r.getString("revision_id"):null),id);
  budget.check();if(rows.size()!=1)throw unavailable();var value=rows.getFirst();if(value.revision()!=null)counter(value.revision());
  boolean rootSequence=table.equals("meeting_info_revision")||table.equals("roster_revision")||table.equals("transcript_revision");
  if(!id.equals(value.id())||!meeting.equals(value.meeting())||rootSequence&&value.revision()!=null&&value.revision()>maximum||qualifier!=null&&!qualifier.equals(value.qualifier()))throw unavailable();
  if(value.revisionId()!=null)storedId(value.revisionId());return value;
 }
 private void rejectCandidate(String column,String id,WorkspaceReadBudget budget){
  if(id==null)return;budget.reserve(1,1,36,36,36,4,false);
  // Existing FK-backed reverse indexes bound each lookup. No candidate text or private IDs leave this method.
  var matches=jdbc.queryForList("SELECT id FROM generation_candidate WHERE "+column+"=? LIMIT 1 FOR UPDATE",String.class,id);
  budget.check();if(!matches.isEmpty())throw unavailable();
 }
 private static long requiredCounter(java.sql.ResultSet row)throws java.sql.SQLException{long value=row.getLong("revision");if(row.wasNull())throw unavailable();return value;}
 private static void counter(long value){if(value<0||value==Long.MAX_VALUE)throw unavailable();}
 private static void storedId(String id){try{ama.contract.OutboxContracts.uuid(id);}catch(IllegalArgumentException invalid){throw unavailable();}}
 private static WorkspaceReadBudget.Unavailable unavailable(){return new WorkspaceReadBudget.Unavailable();}
}
