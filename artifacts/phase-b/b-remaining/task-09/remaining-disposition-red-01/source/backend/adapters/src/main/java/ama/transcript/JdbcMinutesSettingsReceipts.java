package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TranscriptContracts.Failure;
import ama.db.TransactionRunner;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** Replay remains available without an archive supplier, model/provider or installed new branch. */
public final class JdbcMinutesSettingsReceipts {
 private final JdbcTemplate j;private final TransactionRunner tx;private final MeetingAccess access;
 public JdbcMinutesSettingsReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){j=new JdbcTemplate(source);j.setQueryTimeout(2);this.tx=tx;this.access=access;}
 record Identity(String scope,String key,String request){}
 record Stored(String result,int status,boolean matches){}
 static Identity identity(Actor actor,String meeting,String key,long expected,String template){JdbcProcessingIntake.key(key);ama.contract.MinutesContracts.uuid(meeting);ama.contract.MinutesContracts.uuid(template);if(expected<0||expected>Long.MAX_VALUE-1)throw new Failure(400,"INVALID_REVISION");return new Identity(JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,"MINUTES_INITIAL_SETTINGS",key),JdbcProcessingIntake.digest(key),JdbcProcessingIntake.digest("minutes-initial-settings-v1",Long.toString(expected),template));}
 public Optional<Map<String,Object>> lookup(Actor actor,String meeting,String key,long expected,String template){return lookup(actor,meeting,identity(actor,meeting,key,expected,template),template);}
 Optional<Map<String,Object>> lookup(Actor actor,String meeting,Identity id,String template){return tx.required(()->{var record=record(actor,meeting,id);access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);if(record.isEmpty())return Optional.empty();match(record.get());return Optional.of(restore(meeting,template,record.get(),true));});}
 private Optional<Stored> record(Actor actor,String meeting,Identity id){return j.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",(r,n)->new Stored(r.getString(6),r.getInt(7),actor.enterpriseId().equals(r.getString(1))&&meeting.equals(r.getString(2))&&"MINUTES_INITIAL_SETTINGS".equals(r.getString(3))&&id.key().equals(r.getString(4))&&id.request().equals(r.getString(5))&&"SUCCEEDED".equals(r.getString(8))),id.scope()).stream().findFirst();}
 Stored claim(Actor actor,String meeting,Identity id){j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'MINUTES_INITIAL_SETTINGS',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",id.scope(),actor.enterpriseId(),meeting,id.key(),id.request());return record(actor,meeting,id).orElseThrow(JdbcMinutesSettingsReceipts::unavailable);}
 static void match(Stored stored){if(!stored.matches())throw new Failure(409,"IDEMPOTENCY_CONFLICT");}
 void accepted(Identity id,String exclusion){if(j.update("UPDATE idempotency_record SET result_id=?,http_status=200 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",exclusion,id.scope())!=1)throw unavailable();}
 Map<String,Object> restore(String meeting,String template,Stored stored,boolean replayed){
  if(stored.result()==null||stored.status()!=200)throw unavailable();var parentIds=j.queryForList("SELECT template_id FROM template_revision WHERE id=?",String.class,template);if(parentIds.size()!=1)throw unavailable();var parents=j.queryForList("SELECT id,owner_id FROM minutes_template WHERE id=? FOR UPDATE",parentIds.getFirst());if(parents.size()!=1||parents.getFirst().get("owner_id")!=null)throw unavailable();var revisions=j.queryForList("SELECT id,template_id,owner_id,revision FROM template_revision WHERE id=? FOR UPDATE",template);if(revisions.size()!=1||revisions.getFirst().get("owner_id")!=null||!parentIds.getFirst().equals(revisions.getFirst().get("template_id"))||((Number)revisions.getFirst().get("revision")).longValue()!=1)throw unavailable();
  var exclusions=j.queryForList("SELECT id,revision FROM exclusion_revision WHERE id=? AND meeting_id=? FOR UPDATE",stored.result(),meeting);if(exclusions.size()!=1||((Number)exclusions.getFirst().get("revision")).longValue()!=1)throw unavailable();
  if(!j.queryForList("SELECT id FROM sensitive_exclusion WHERE revision_id=? ORDER BY id LIMIT 1 FOR UPDATE",String.class,stored.result()).isEmpty())throw unavailable();
  return Map.of("result",Map.of("kind","INITIAL_MINUTES_SETTINGS","meetingId",meeting,"templateRevisionId",template,"exclusionRevisionId",stored.result()),"replayed",replayed,"observation",observation(j.queryForMap("SELECT revision,access_revision,transcript_confirmation_id,minutes_revision_id FROM meeting WHERE id=?",meeting)));
 }
 static Map<String,Object> observation(Map<String,Object> row){var out=new LinkedHashMap<String,Object>();for(String column:List.of("revision","access_revision")){long n=((Number)row.get(column)).longValue();String key=column.equals("revision")?"revision":"accessRevision";out.put(key,n);out.put(key+"Token",Long.toString(n));}out.put("currentConfirmationId",row.get("transcript_confirmation_id"));out.put("currentMinutesRevisionId",row.get("minutes_revision_id"));return Collections.unmodifiableMap(out);}
 static Failure unavailable(){return new Failure(503,"PROVENANCE_UNAVAILABLE");}
}
