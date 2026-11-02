package ama.governance;
import ama.contract.IdentityContracts.*;
import ama.contract.GovernanceContracts;
import ama.contract.MeetingContracts.Conflict;
import ama.access.MeetingAccess.AccessDeniedException;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Supplier;
/** Shared current-identity check, immutable command receipts and audit; no content authority. */
public class JdbcOperationsSupport {
 protected final JdbcTemplate j;protected final TransactionRunner tx;protected final JdbcMeetingAccess access;
 protected final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
 public JdbcOperationsSupport(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=tx;this.access=access;}
 protected static String digest(String... fields){try{var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);for(String field:fields){byte[] value=field.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(value.length);out.write(value);}return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(java.io.IOException|java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 protected void active(Actor actor){access.tryLockIdentities(actor,List.of(),false).orElseThrow(AccessDeniedException::new);}
 protected boolean admin(Actor actor){return !j.queryForList("SELECT role FROM role_assignment WHERE enterprise_id=? AND role='PLATFORM_ADMIN' FOR SHARE",String.class,actor.enterpriseId()).isEmpty();}
 protected void requireAdmin(Actor actor){active(actor);if(!admin(actor))throw new AccessDeniedException();}
 protected void audit(Actor actor,String action,String id,long revision){new ama.audit.JdbcAuditAdapter(j.getDataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),action,id,revision,"SUCCEEDED",UUID.randomUUID().toString(),java.time.Instant.now()));}
 protected String write(Object value){try{return json.writeValueAsString(value);}catch(Exception bad){throw new IllegalArgumentException("Invalid governance data");}}
 protected <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception bad){throw new IllegalArgumentException("Invalid governance data");}}
 @SuppressWarnings("unchecked") protected Map<String,Object> map(String value){return read(value,LinkedHashMap.class);}
 /** Receipt metadata belongs to its original active actor even after source rights end. */
 protected Map<String,Object> original(Actor actor,String key,String operation,Object request){
  ama.transcript.JdbcProcessingIntake.key(key);String scope=digest("operations-v1",actor.enterpriseId(),operation,key),hash=digest(write(request));
  return tx.required(()->{active(actor);var rows=j.queryForList("SELECT actor_id,operation,request_hash,CAST(result_json AS CHAR) result FROM governance_command WHERE scope_hash=? FOR SHARE",scope);if(rows.isEmpty())return null;var row=rows.getFirst();if(!actor.enterpriseId().equals(row.get("actor_id"))||!operation.equals(row.get("operation"))||!hash.equals(row.get("request_hash")))throw new Conflict();return row.get("result") instanceof String result?map(result):null;});
 }
 protected Map<String,Object> command(Actor actor,String key,String operation,Object request,boolean administrative,Supplier<Map<String,Object>> mutation){
  ama.transcript.JdbcProcessingIntake.key(key);String scope=digest("operations-v1",actor.enterpriseId(),operation,key),hash=digest(write(request));
  return tx.required(()->{if(administrative)requireAdmin(actor);else active(actor);
   j.update("INSERT INTO governance_command(scope_hash,actor_id,operation,request_hash) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE scope_hash=governance_command.scope_hash",scope,actor.enterpriseId(),operation,hash);
   var row=j.queryForMap("SELECT request_hash,CAST(result_json AS CHAR) result FROM governance_command WHERE scope_hash=? FOR UPDATE",scope);if(!hash.equals(row.get("request_hash")))throw new Conflict();
   if(row.get("result") instanceof String result)return map(result);
   var result=mutation.get();j.update("UPDATE governance_command SET result_json=? WHERE scope_hash=?",write(result),scope);return map(write(result));
  });
 }
 protected static void expected(long value){if(value<0||value==Long.MAX_VALUE)throw new IllegalArgumentException("Exact revision required");}
 protected static void id(String value){ama.contract.MinutesContracts.uuid(value);}
 protected static void reason(String value){GovernanceContracts.bounded(value,2048);}
 public static final class NotAvailable extends RuntimeException {public NotAvailable(){super("Operations integration unavailable");}}
}
