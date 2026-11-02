package ama.mail;
import ama.governance.JdbcOperationsSupport;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.Conflict;
import ama.access.MeetingAccess;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import javax.sql.DataSource;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
/** UNKNOWN is persisted before the first network send. Recovery only queries the original key. */
public final class JdbcMailJobs extends JdbcOperationsSupport {
 private final MailPort mail;private final Function<String,String> email;private final URI web;
 private static final String FIELDS="id,meeting_id meetingId,publication_id publicationId,CAST(source_revision AS CHAR) sourceRevisionToken,state,message_id messageId,last_error lastError,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') createdAt";
 public JdbcMailJobs(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,MailPort mail,Function<String,String> email,URI web){super(source,tx,access);this.mail=mail;this.email=email;this.web=web;if(web==null||!Set.of("http","https").contains(web.getScheme())||web.getHost()==null||web.getRawQuery()!=null||web.getUserInfo()!=null||web.getFragment()!=null)throw new IllegalArgumentException("Operator web origin required");}
 public Map<String,Object> enqueue(Actor actor,String key,String meeting,long revision,String publication,List<String> recipients){
  id(meeting);id(publication);expected(revision);recipients=List.copyOf(recipients);if(recipients.isEmpty()||recipients.size()>20||recipients.stream().distinct().count()!=recipients.size())throw new IllegalArgumentException("1 to 20 unique enterprise recipients required");recipients.forEach(ama.contract.IdentityContracts::stableId);var sorted=recipients.stream().sorted().toList();var request=List.of(meeting,revision,publication,sorted);
  var previous=original(actor,key,"MINUTES_MAIL",request);if(previous!=null)return previous;
  try{access.prepareExternalRead(actor,meeting);}catch(MeetingAccess.AccessDeniedException|MeetingAccess.AuthorityUnavailableException lost){var recovered=original(actor,key,"MINUTES_MAIL",request);if(recovered!=null)return recovered;throw lost;}
  return command(actor,key,"MINUTES_MAIL",request,false,()->{authorize(actor,meeting,revision,publication,sorted);String id=UUID.randomUUID().toString();j.update("INSERT INTO minutes_mail_job(id,meeting_id,publication_id,source_revision,actor_id,recipient_ids,state) VALUES (?,?,?,?,?,?,'PENDING')",id,meeting,publication,revision,actor.enterpriseId(),write(sorted));audit(actor,"MINUTES_MAIL_QUEUED",id,0);return j.queryForMap("SELECT "+FIELDS+" FROM minutes_mail_job WHERE id=?",id);});
 }
 private void authorize(Actor actor,String meeting,long revision,String publication,List<String> recipients){
  var identities=access.tryLockIdentities(actor,recipients,false).orElseThrow(MeetingAccess.AccessDeniedException::new);var held=access.lockPrepared(identities,meeting,false).orElseThrow(MeetingAccess.AccessDeniedException::new);access.require(held,MeetingAccess.Action.MANAGE);
  var root=j.queryForMap("SELECT owner_id,revision,final_confirmation_id,roster_revision_id,stage,lifecycle,governance_state FROM meeting WHERE id=?",meeting);
  if(!actor.enterpriseId().equals(root.get("owner_id")))throw new MeetingAccess.AccessDeniedException();
  if(((Number)root.get("revision")).longValue()!=revision||!"PUBLISHED".equals(root.get("stage"))||!"ACTIVE".equals(root.get("lifecycle"))||!"ACTIVE".equals(root.get("governance_state")))throw new Conflict();
  if(j.queryForObject("SELECT COUNT(*) FROM publication WHERE id=? AND meeting_id=? AND confirmation_id=? AND state='SUCCEEDED'",Integer.class,publication,meeting,root.get("final_confirmation_id"))!=1)throw new Conflict();
  for(String recipient:recipients){if(recipient.equals(root.get("owner_id")))continue;var rows=j.queryForList("SELECT g.roster_id FROM meeting_access_grant g JOIN roster_revision r ON r.id=g.roster_id AND r.meeting_id=g.meeting_id AND r.state='CONFIRMED' JOIN roster_member p ON p.roster_id=r.id AND p.enterprise_id=g.enterprise_id AND p.member_kind='ENTERPRISE' WHERE g.meeting_id=? AND g.enterprise_id=? AND g.action='READ' AND g.roster_id=? FOR SHARE",String.class,meeting,recipient,root.get("roster_revision_id"));if(rows.size()!=1)throw new MeetingAccess.AccessDeniedException();}
 }
 public Map<String,Object> context(Actor actor,String meeting){id(meeting);access.prepareExternalRead(actor,meeting);return tx.required(()->{active(actor);var held=access.lock(actor,meeting);access.require(held,MeetingAccess.Action.MANAGE);var root=j.queryForMap("SELECT CAST(m.revision AS CHAR) sourceRevisionToken,p.id publicationId,m.owner_id,m.roster_revision_id FROM meeting m JOIN publication p ON p.meeting_id=m.id AND p.confirmation_id=m.final_confirmation_id AND p.state='SUCCEEDED' WHERE m.id=? AND m.owner_id=? AND m.stage='PUBLISHED'",meeting,actor.enterpriseId());var people=j.queryForList("SELECT i.enterprise_id enterpriseId,LEFT(i.display_name,255) displayName FROM enterprise_identity i WHERE i.status='ACTIVE' AND (i.enterprise_id=? OR EXISTS(SELECT 1 FROM meeting_access_grant g JOIN roster_member r ON r.roster_id=g.roster_id AND r.enterprise_id=g.enterprise_id AND r.member_kind='ENTERPRISE' JOIN roster_revision rr ON rr.id=r.roster_id AND rr.state='CONFIRMED' WHERE g.meeting_id=? AND g.enterprise_id=i.enterprise_id AND g.action='READ' AND g.roster_id=?)) ORDER BY i.enterprise_id LIMIT 100",actor.enterpriseId(),meeting,root.get("roster_revision_id"));return Map.of("sourceRevisionToken",root.get("sourceRevisionToken"),"publicationId",root.get("publicationId"),"recipients",people);});}
 public List<Map<String,Object>> history(Actor actor,String meeting){id(meeting);return tx.required(()->{active(actor);if(j.queryForObject("SELECT COUNT(*) FROM meeting WHERE id=? AND owner_id=?",Integer.class,meeting,actor.enterpriseId())!=1)throw new MeetingAccess.AccessDeniedException();return j.queryForList("SELECT "+FIELDS+" FROM minutes_mail_job WHERE meeting_id=? AND actor_id=? ORDER BY created_at DESC,id DESC LIMIT 100",meeting,actor.enterpriseId());});}
 public Map<String,Object> get(Actor actor,String id){id(id);return tx.required(()->{active(actor);var rows=j.queryForList("SELECT "+FIELDS+" FROM minutes_mail_job WHERE id=? AND actor_id=?",id,actor.enterpriseId());if(rows.size()!=1)throw new MeetingAccess.AccessDeniedException();return rows.getFirst();});}
 public void process(){
  String lease=UUID.randomUUID().toString();var job=tx.required(()->{var rows=j.queryForList("SELECT id,meeting_id,publication_id,source_revision,actor_id,CAST(recipient_ids AS CHAR) recipient_ids,CAST(request_json AS CHAR) request_json,state FROM minutes_mail_job WHERE state IN ('PENDING','UNKNOWN') AND next_check_at<=UTC_TIMESTAMP(6) AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6)) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");if(rows.isEmpty())return null;var row=rows.getFirst();j.update("UPDATE minutes_mail_job SET lease_owner=?,lease_until=TIMESTAMPADD(SECOND,60,UTC_TIMESTAMP(6)) WHERE id=?",lease,row.get("id"));return row;});if(job==null)return;
  String id=(String)job.get("id"),meeting=(String)job.get("meeting_id"),actorId=(String)job.get("actor_id");boolean first="PENDING".equals(job.get("state"));MailPort.Request request;
  try{
   if(first){var actor=new Actor(actorId,Status.ACTIVE,Set.of(Role.USER));var recipients=List.of(read((String)job.get("recipient_ids"),String[].class));access.prepareExternalRead(actor,meeting);var addresses=recipients.stream().map(email).distinct().sorted().toList();
    if(addresses.size()!=recipients.size())throw new IllegalArgumentException("Unique active mailboxes required");request=new MailPort.Request("ama-minutes-"+id,addresses,"会议纪要已发布","你收到一份会议纪要通知。请登录企业账号并在授权范围内查看：\n"+web.resolve("/meeting/"+meeting+"/published"));var original=request;
    boolean admitted=tx.required(()->{authorize(actor,meeting,((Number)job.get("source_revision")).longValue(),(String)job.get("publication_id"),recipients);if(j.update("UPDATE minutes_mail_job SET state='UNKNOWN',request_json=?,lease_until=TIMESTAMPADD(SECOND,60,UTC_TIMESTAMP(6)) WHERE id=? AND state='PENDING' AND lease_owner=? AND lease_until>UTC_TIMESTAMP(6)",write(original),id,lease)!=1)return false;return true;});if(!admitted)return;
   }else request=read((String)job.get("request_json"),MailPort.Request.class);
  }catch(MeetingAccess.AuthorityUnavailableException unavailable){defer(id,lease);return;}
  catch(MeetingAccess.AccessDeniedException|Conflict denied){finish(id,lease,"BLOCKED",null,"SOURCE_OR_RECIPIENT_REVOKED");return;}
  catch(RuntimeException unavailable){if(first)finish(id,lease,"FAILED",null,"RECIPIENT_OR_SOURCE_UNAVAILABLE");else defer(id,lease);return;}
  var outcome=MailService.attempt(request,mail,first);
  if(outcome.state()==MailPort.State.SENT||outcome.state()==MailPort.State.FAILED)finish(id,lease,outcome.state().name(),outcome.messageId(),outcome.state()==MailPort.State.FAILED?"DELIVERY_FAILED":null);else defer(id,lease);
 }
 private void finish(String id,String lease,String state,String message,String error){tx.required(()->{int changed=j.update("UPDATE minutes_mail_job SET state=?,message_id=?,last_error=?,finished_at=UTC_TIMESTAMP(6),lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=? AND lease_until>UTC_TIMESTAMP(6)",state,message,error,id,lease);if(changed==1){String actor=j.queryForObject("SELECT actor_id FROM minutes_mail_job WHERE id=?",String.class,id);new ama.audit.JdbcAuditAdapter(j.getDataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor,"MINUTES_MAIL_"+state,id,0,state,UUID.randomUUID().toString(),java.time.Instant.now()));}return null;});}
 private void defer(String id,String lease){j.update("UPDATE minutes_mail_job SET next_check_at=TIMESTAMPADD(SECOND,15,UTC_TIMESTAMP(6)),lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",id,lease);}
}
