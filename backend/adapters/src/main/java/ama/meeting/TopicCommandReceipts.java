package ama.meeting;

import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.Conflict;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Immutable historical acknowledgements, committed with the personal topic mutation. */
final class TopicCommandReceipts {
 static final String WORKSPACE="2bcefbf7-c9cf-4b01-b4ad-de375d12e16f";
 private final JdbcTemplate j;
 TopicCommandReceipts(JdbcTemplate j){this.j=j;}
 record Claim(String scope,int status,Map<String,Object> result){boolean replay(){return status!=0;}}
 Claim claim(Actor actor,String resource,String operation,String key,String request){
  ama.contract.MinutesContracts.uuid(resource);ama.transcript.JdbcProcessingIntake.key(key);
  String hash=MeetingDigests.fields(key),scope=MeetingDigests.fields("topic-command-scope-v1",actor.enterpriseId(),resource,operation,key);
  j.update("INSERT INTO topic_command_receipt(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,created_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=topic_command_receipt.scope_hash",scope,actor.enterpriseId(),resource,operation,hash,request);
  var row=j.queryForMap("SELECT actor_id,resource_id,operation,key_hash,request_hash,http_status,result_id,result_name,result_description,result_revision FROM topic_command_receipt WHERE scope_hash=? FOR UPDATE",scope);
  if(!actor.enterpriseId().equals(row.get("actor_id"))||!resource.equals(row.get("resource_id"))||!operation.equals(row.get("operation"))||!hash.equals(row.get("key_hash"))||!request.equals(row.get("request_hash")))throw new Conflict();
  int status=((Number)row.get("http_status")).intValue();Map<String,Object> result=null;
  if(status==200||status==201){
   if(!(row.get("result_id") instanceof String id)||!(row.get("result_name") instanceof String name)||!(row.get("result_revision") instanceof Number revision)||revision.longValue()<0||revision.longValue()==Long.MAX_VALUE)throw new IllegalStateException("Topic receipt unavailable");
   ama.contract.MinutesContracts.uuid(id);result=new LinkedHashMap<>();result.put("id",id);result.put("name",name);result.put("description",row.get("result_description"));result.put("revision",revision.longValue());result.put("revisionToken",Long.toString(revision.longValue()));
  }else if(status!=0&&status!=204)throw new IllegalStateException("Topic receipt unavailable");
  return new Claim(scope,status,result);
 }
 void accept(Claim claim,int status,Map<String,Object> result){
  if(claim.replay()||status!=204&&result==null)throw new IllegalStateException("Fresh topic command required");
  int count=j.update("UPDATE topic_command_receipt SET http_status=?,result_id=?,result_name=?,result_description=?,result_revision=? WHERE scope_hash=? AND http_status=0",status,result==null?null:result.get("id"),result==null?null:result.get("name"),result==null?null:result.get("description"),result==null?null:result.get("revision"),claim.scope());
  if(count!=1)throw new IllegalStateException("Topic receipt unavailable");
 }
}
