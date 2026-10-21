package ama.task;

import ama.contract.TaskContracts.Permit;
import ama.contract.ExternalContracts.OperationToken;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.task.JdbcTaskQueue.*;

/** No constructor writes or default company capacity. Configuration is an explicit administration action. */
public final class JdbcModelPermitPool implements ModelPermitPool {
 private final JdbcExternalOperations external;private final JdbcTaskQueue q;private final JdbcTemplate j;
 private final Map<String,ModelConfiguration> configurations;private final DeadlineVerifier verifier;
 public JdbcModelPermitPool(JdbcExternalOperations external,Map<String,ModelConfiguration> configurations,DeadlineVerifier verifier){
  this.external=external;this.q=external.q;this.j=external.j;this.configurations=Map.copyOf(configurations);this.verifier=Objects.requireNonNull(verifier);
  configurations.forEach((key,value)->{if(!key.equals(value.model()))throw new IllegalArgumentException("Mismatched model key");});
 }
 public void configure(){independent();q.tx.required(()->{
  var models=new TreeSet<>(configurations.keySet());
  // Lock the COMPLETE sorted model set before the first reservation consistent read.
  // Otherwise A's count creates an RR ReadView that misses an admission committed before B's lock.
  // Upsert also avoids missing-key gap-lock/insert deadlocks; configuration never locks operations.
  for(var model:models){
   var config=configurations.get(model);
   j.update("INSERT INTO model_capacity(model,capacity,config_revision,enabled,cutoff_policy) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE model=model",model,config.capacity(),config.revision(),config.enabled(),config.cutoffPolicy().name());
  }
  for(var model:models){
   var config=configurations.get(model);
   var old=j.queryForList("SELECT * FROM model_capacity WHERE model=? FOR UPDATE",model);
   if(!old.isEmpty()){
    var row=old.getFirst();long revision=((Number)row.get("config_revision")).longValue();
    if(config.revision()<revision)throw new IllegalStateException("Stale model configuration");
    if(config.revision()==revision){if(!matches(row,config))throw new IllegalStateException("Configuration revision cannot change meaning");continue;}
    if(inflight(model)>config.capacity())throw new IllegalStateException("Capacity cannot shrink below in-flight executions");
    j.update("UPDATE model_capacity SET capacity=?,config_revision=?,enabled=?,cutoff_policy=? WHERE model=?",config.capacity(),config.revision(),config.enabled(),config.cutoffPolicy().name(),model);
   }else j.update("INSERT INTO model_capacity(model,capacity,config_revision,enabled,cutoff_policy) VALUES (?,?,?,?,?)",model,config.capacity(),config.revision(),config.enabled(),config.cutoffPolicy().name());
  }return null;
 });}
 private boolean matches(Map<String,Object> row,ModelConfiguration c){return ((Number)row.get("capacity")).intValue()==c.capacity()&&((Number)row.get("config_revision")).longValue()==c.revision()&&((Number)row.get("enabled")).intValue()==(c.enabled()?1:0)&&row.get("cutoff_policy").equals(c.cutoffPolicy().name());}
 private long inflight(String model){return j.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE model=? AND settled_at IS NULL AND (safe_deadline IS NULL OR safe_deadline>UTC_TIMESTAMP(6))",Long.class,model);}
 @Override public Optional<Permit> acquire(OperationToken token,String model,Instant deadline){
  independent();var config=configurations.get(model);if(config==null||!config.enabled())return Optional.empty();
  Instant normalized=deadline==null?null:deadline.truncatedTo(ChronoUnit.MICROS);
  if(config.cutoffPolicy()==ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT&&deadline!=null)throw new IllegalArgumentException("Unproven deadline cannot release capacity");
  if(normalized!=null)verifier.verify(token,model,normalized);
  if(!external.hasCallAuthority(token))return Optional.empty();
  return q.tx.required(()->{
   var o=external.operation(token.operationId(),true,false);if(o.isEmpty())return Optional.empty();var e=external.execution(token.operationId(),token.executionNo(),true,false);
   if(e.isEmpty()||!external.valid(o.orElseThrow(),e.orElseThrow(),token)||!o.orElseThrow().state().equals("MARKED"))return Optional.empty();
   var rows=j.queryForList("SELECT * FROM model_capacity WHERE model=? FOR UPDATE",model);if(rows.isEmpty()||!matches(rows.getFirst(),config))return Optional.empty();
   if(normalized!=null&&!normalized.isAfter(q.now()))return Optional.empty();
   var existing=j.query("SELECT * FROM model_reservation WHERE execution_id=? FOR UPDATE",(r,n)->new Permit(r.getString("id"),r.getString("model"),r.getString("execution_id"),r.getLong("config_revision"),instant(r,"safe_deadline")),token.executionId());
   // One admission, one send. A duplicate acquire must never be interpreted as fresh call authority.
   if(!existing.isEmpty()||inflight(model)>=config.capacity())return Optional.empty();
   var permit=new Permit(UUID.randomUUID().toString(),model,token.executionId(),config.revision(),normalized);
   j.update("INSERT INTO model_reservation(id,model,execution_id,config_revision,safe_deadline,created_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP(6))",permit.id(),model,token.executionId(),config.revision(),sqlTime(normalized));return Optional.of(permit);
  });
 }
 @Override public boolean settled(Permit permit){
  independent();return q.tx.required(()->{
   var ids=j.queryForList("SELECT operation_id,execution_no FROM external_execution WHERE id=?",permit.executionId());if(ids.isEmpty())return false;
   String oid=ids.getFirst().get("operation_id").toString();long number=((Number)ids.getFirst().get("execution_no")).longValue();
   var o=external.operation(oid,true,false);if(o.isEmpty())return false;var execution=external.execution(oid,number,true,false).orElseThrow();
   j.queryForList("SELECT model FROM model_capacity WHERE model=? FOR UPDATE",permit.model());
   var rows=j.queryForList("SELECT * FROM model_reservation WHERE id=? FOR UPDATE",permit.id());if(rows.isEmpty())return false;var row=rows.getFirst();
   if(!row.get("execution_id").equals(permit.executionId())||!row.get("model").equals(permit.model())||((Number)row.get("config_revision")).longValue()!=permit.configRevision())return false;
   if(row.get("settled_at")!=null)return true;
   boolean terminal=execution.state().equals("SUCCEEDED")||(execution.state().equals("FAILED")&&execution.basis()!=null&&execution.evidence()!=null&&execution.verified()!=null);
   if(!terminal){Integer passed=j.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE id=? AND safe_deadline IS NOT NULL AND safe_deadline<=UTC_TIMESTAMP(6)",Integer.class,permit.id());if(passed!=1)return false;}
   j.update("UPDATE model_reservation SET settled_at=UTC_TIMESTAMP(6) WHERE id=? AND settled_at IS NULL",permit.id());return true;
  });
 }
}
