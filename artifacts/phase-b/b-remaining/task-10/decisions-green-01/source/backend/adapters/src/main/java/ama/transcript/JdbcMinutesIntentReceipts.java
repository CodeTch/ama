package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TranscriptContracts.Failure;
import ama.db.TransactionRunner;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Owner authorization and durable intent acknowledgments need no current model or archive. */
public final class JdbcMinutesIntentReceipts {
 private final JdbcTemplate j;private final TransactionRunner tx;private final MeetingAccess access;
 public JdbcMinutesIntentReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){this.j=new JdbcTemplate(source);j.setQueryTimeout(2);this.tx=tx;this.access=access;}
 record Identity(String scope,String key,String request){}
 record Stored(String result,int status,boolean matches){}
 static Identity identity(Actor actor,String meeting,String key,long expected){
  JdbcProcessingIntake.key(key);ama.contract.MinutesContracts.uuid(meeting);
  if(expected<0||expected>Long.MAX_VALUE-1)throw new Failure(400,"INVALID_REVISION");
  return new Identity(JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,"EDIT_INTENT",key),JdbcProcessingIntake.digest(key),JdbcProcessingIntake.digest("minutes-edit-intent-v1",Long.toString(expected)));
 }
 public Map<String,Object> read(Actor actor,String meeting){ama.contract.MinutesContracts.uuid(meeting);return tx.required(()->{access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);return projection(meeting,false);});}
 public Optional<Map<String,Object>> lookup(Actor actor,String meeting,String key,long expected){var identity=identity(actor,meeting,key,expected);return tx.required(()->{
  var stored=record(actor,meeting,identity);access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);
  if(stored.isEmpty())return Optional.empty();match(stored.orElseThrow());return Optional.of(restore(meeting,expected,stored.orElseThrow(),true));
 });}
 private Optional<Stored> record(Actor actor,String meeting,Identity id){return j.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",(r,n)->new Stored(r.getString(6),r.getInt(7),actor.enterpriseId().equals(r.getString(1))&&meeting.equals(r.getString(2))&&"EDIT_INTENT".equals(r.getString(3))&&id.key().equals(r.getString(4))&&id.request().equals(r.getString(5))&&"SUCCEEDED".equals(r.getString(8))),id.scope()).stream().findFirst();}
 Stored claim(Actor actor,String meeting,Identity id){j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'EDIT_INTENT',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",id.scope(),actor.enterpriseId(),meeting,id.key(),id.request());return record(actor,meeting,id).orElseThrow(JdbcMinutesIntentReceipts::unavailable);}
 static void match(Stored stored){if(!stored.matches())throw new Failure(409,"IDEMPOTENCY_CONFLICT");}
 void accepted(Identity id,String meeting){if(j.update("UPDATE idempotency_record SET result_id=?,http_status=200 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",meeting,id.scope())!=1)throw unavailable();}
 Map<String,Object> restore(String meeting,long expected,Stored stored,boolean replayed){
  if(!meeting.equals(stored.result())||stored.status()!=200)throw unavailable();var current=projection(meeting,true);
  if(((Number)((Map<?,?>)current.get("observation")).get("revision")).longValue()!=expected)throw new StaleIntent(current);
  return Map.of("result",Map.of("kind","EDIT_INTENT","meetingId",meeting),"replayed",replayed,"current",current);
 }
 Map<String,Object> projection(String meeting,boolean positive){
  var guard=j.queryForList("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=? FOR UPDATE",Long.class,meeting);
  if(guard.size()>1||!guard.isEmpty()&&(guard.getFirst()<0||guard.getFirst()>Long.MAX_VALUE-1)||positive&&(guard.isEmpty()||guard.getFirst()==0))throw unavailable();
  var value=new LinkedHashMap<String,Object>();value.put("meetingId",meeting);value.put("state",guard.isEmpty()?"UNKNOWN":"KNOWN");value.put("intentEpochToken",guard.isEmpty()?null:Long.toString(guard.getFirst()));
  value.put("observation",JdbcMinutesSettingsReceipts.observation(j.queryForMap("SELECT revision,access_revision,transcript_confirmation_id,minutes_revision_id FROM meeting WHERE id=?",meeting)));return Collections.unmodifiableMap(value);
 }
 public static final class StaleIntent extends RuntimeException {private final Map<String,Object> current;StaleIntent(Map<String,Object> current){super("REVISION_CONFLICT",null,false,false);this.current=current;}public Map<String,Object> response(){return Map.of("error",Map.of("code","REVISION_CONFLICT"),"current",current);}}
 private static Failure unavailable(){return new Failure(503,"PROVENANCE_UNAVAILABLE");}
}
