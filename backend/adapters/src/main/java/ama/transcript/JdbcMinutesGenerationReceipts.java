package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TaskContracts.TaskSpec;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.db.TransactionRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
/** Independent accepted generation lookup: no archive, model, worker or current-source dependency. */
public final class JdbcMinutesGenerationReceipts {
 private final JdbcTemplate j;private final TransactionRunner tx;private final MeetingAccess access;
 private static final String SPEC="task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts";
 record Identity(String scope,String key,String body){}
 record Stored(String task,int status,boolean matches){}
 record Proof(TaskSpec spec,Map<String,Object> input){}
 public JdbcMinutesGenerationReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){j=new JdbcTemplate(source);j.setQueryTimeout(2);this.tx=tx;this.access=access;}
 static Identity identity(Actor actor,String meeting,String key,long expected){ama.contract.MinutesContracts.uuid(meeting);JdbcProcessingIntake.key(key);if(expected<0||expected>Long.MAX_VALUE-1)throw new ama.contract.TranscriptContracts.Failure(400,"INVALID_REVISION");return new Identity(JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,"MINUTES_GENERATE",key),JdbcProcessingIntake.digest(key),JdbcProcessingIntake.digest("minutes-generate-request-v1",Long.toString(expected),"FULL"));}
 public Optional<Map<String,Object>> lookup(Actor actor,String meeting,String key,long expected){return lookup(actor,meeting,identity(actor,meeting,key,expected),expected);}
 Optional<Map<String,Object>> lookup(Actor actor,String meeting,Identity id,long expected){return tx.required(()->{var stored=record(actor,meeting,id);var proof=stored.isPresent()?observe(meeting,stored.orElseThrow(),expected):null;access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);if(stored.isEmpty())return Optional.empty();match(stored.orElseThrow());return Optional.of(restore(meeting,stored.orElseThrow(),proof,true));});}
 private Optional<Stored> record(Actor actor,String meeting,Identity id){return j.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,state,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",(r,n)->new Stored(r.getString(7),r.getInt(8),actor.enterpriseId().equals(r.getString(1))&&meeting.equals(r.getString(2))&&"MINUTES_GENERATE".equals(r.getString(3))&&id.key().equals(r.getString(4))&&id.body().equals(r.getString(5))&&"SUCCEEDED".equals(r.getString(6))),id.scope()).stream().findFirst();}
 Stored claim(Actor actor,String meeting,Identity id){j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'MINUTES_GENERATE',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",id.scope(),actor.enterpriseId(),meeting,id.key(),id.body());return record(actor,meeting,id).orElseThrow(JdbcMinutesSettingsReceipts::unavailable);}
 static void match(Stored stored){if(!stored.matches())throw new ama.contract.TranscriptContracts.Failure(409,"IDEMPOTENCY_CONFLICT");}
 Proof observe(String meeting,Stored stored,long expected){
  if(!stored.matches()||stored.task()==null||stored.status()!=202)return null;var found=j.queryForList("SELECT "+SPEC+" FROM durable_task WHERE id=?",stored.task());if(found.size()!=1)return null;
  try{var spec=TranscriptReadGraph.spec(found.getFirst());MinutesTaskDomainPolicy.shape(spec);if(!meeting.equals(spec.subject().id())||expected!=spec.businessVersion())return null;String producer=ama.minutes.MinutesGenerateProducer.compute(meeting,spec.input().id(),spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch(),new CanonicalBudget(65536,8,128,1024,4096),()->false);if(!producer.equals(spec.producerKey()))return null;return inputProof(spec);}catch(ama.contract.MinutesContracts.Failure|IllegalArgumentException malformed){return null;}
 }
 private Proof inputProof(TaskSpec spec){var inputs=j.queryForList("SELECT * FROM generation_input WHERE id=?",spec.input().id());if(inputs.size()!=1)return null;var input=inputs.getFirst();if(!spec.subject().id().equals(input.get("meeting_id"))||((Number)input.get("base_revision")).longValue()!=spec.businessVersion()||!spec.inputFingerprint().equals(input.get("input_fingerprint"))||!"FULL".equals(input.get("scope"))||input.get("target_element_id")!=null)return null;return new Proof(spec,Collections.unmodifiableMap(new LinkedHashMap<>(input)));}
 void accepted(Identity id,String task){if(j.update("UPDATE idempotency_record SET result_id=?,http_status=202 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",task,id.scope())!=1)throw JdbcMinutesSettingsReceipts.unavailable();}
 Map<String,Object> written(String meeting,String task,TaskSpec spec){return restore(meeting,new Stored(task,202,true),inputProof(spec),false);}
 Map<String,Object> restore(String meeting,Stored stored,Proof proof,boolean replay){
  if(stored.task()==null||stored.status()!=202||proof==null)throw JdbcMinutesSettingsReceipts.unavailable();var inputs=j.queryForList("SELECT * FROM generation_input WHERE id=? FOR UPDATE",proof.spec().input().id());if(inputs.size()!=1||!inputs.getFirst().equals(proof.input()))throw JdbcMinutesSettingsReceipts.unavailable();var tasks=j.queryForList("SELECT "+SPEC+" FROM durable_task WHERE id=? FOR UPDATE",stored.task());if(tasks.size()!=1||!TranscriptReadGraph.spec(tasks.getFirst()).equals(proof.spec()))throw JdbcMinutesSettingsReceipts.unavailable();
  return Map.of("result",Map.of("kind","GENERATION_TASK","meetingId",meeting,"inputId",proof.spec().input().id(),"taskId",stored.task(),"inputFingerprint",proof.spec().inputFingerprint()),"replayed",replay,"observation",JdbcMinutesSettingsReceipts.observation(j.queryForMap("SELECT revision,access_revision,transcript_confirmation_id,minutes_revision_id FROM meeting WHERE id=?",meeting)));
 }
}
