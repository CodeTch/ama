package ama.minutes;

import ama.access.MeetingAccess;
import ama.audit.JdbcAuditAdapter;
import ama.contract.AuditContracts.AuditEvent;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.Conflict;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Identity-locked personal CRUD. No generation, meeting mutation, or historical revision deletion. */
public final class JdbcPersonalTemplates {
 private static final String WORKSPACE="6621f7bd-88eb-4212-90a5-5c87501d87a5";
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 private final JdbcTemplate j;private final TransactionRunner tx;private final JdbcMeetingAccess access;private final JdbcAuditAdapter audit;
 public JdbcPersonalTemplates(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){access.requireProjectionSource(source);this.j=new JdbcTemplate(source);j.setQueryTimeout(3);this.tx=tx;this.access=access;audit=new JdbcAuditAdapter(source);}
 public record View(String id,String revisionId,String revisionToken,String name,List<String> themes,boolean deleted){
  public View{ama.contract.MinutesContracts.uuid(id);ama.contract.MinutesContracts.uuid(revisionId);long revision;try{revision=Long.parseLong(revisionToken);}catch(RuntimeException invalid){throw new IllegalArgumentException("Exact template revision required");}if(revision<=0||revision==Long.MAX_VALUE||!Long.toString(revision).equals(revisionToken))throw new IllegalArgumentException("Exact template revision required");PersonalTemplateDocument.name(name);themes=new PersonalTemplateDocument(themes).themes();}
 }
 public record Page(List<View> items,String nextCursor){public Page{items=List.copyOf(items);}}
 private record Command(String scope,String resource,String operation,String keyHash,String requestHash){}
 public Page list(Actor actor,int limit,String cursor){return list(actor,limit,cursor,bytes->{});}
 /** A composed reader charges its own acquisition ledger before any private LONGTEXT transfer. */
 public Page list(Actor actor,int limit,String cursor,java.util.function.LongConsumer beforeText){
  Objects.requireNonNull(beforeText);
  if(limit<1||limit>50)throw new IllegalArgumentException("Template page limit required");if(cursor!=null)ama.contract.MinutesContracts.uuid(cursor);
  return tx.required(()->{active(actor);var ids=j.queryForList("SELECT template_id FROM personal_template_state WHERE owner_id=? AND deleted_at IS NULL AND (? IS NULL OR template_id>?) ORDER BY template_id LIMIT ?",String.class,actor.enterpriseId(),cursor,cursor,limit+1);boolean more=ids.size()>limit;var items=new ArrayList<View>();for(String id:ids.subList(0,Math.min(limit,ids.size())))items.add(owned(actor,id,false,beforeText));return new Page(items,more?items.getLast().id():null);});
 }
 public View read(Actor actor,String id){ama.contract.MinutesContracts.uuid(id);return tx.required(()->{active(actor);return owned(actor,id,false);});}
 public View create(Actor actor,String key,String name,List<String> themes){
  String normalized=PersonalTemplateDocument.name(name);var document=new PersonalTemplateDocument(themes);String encoded=document.json(),nameHash=PersonalTemplateDocument.sha(normalized);var command=command(actor,WORKSPACE,"CREATE",key,"personal-template-create-v1",name,encoded);
  return tx.required(()->{active(actor);var replay=claim(actor,command);if(replay!=null)return replay;
   if(j.queryForObject("SELECT COUNT(*) FROM personal_template_state WHERE owner_id=?",Long.class,actor.enterpriseId())>=1000)throw new IllegalArgumentException("Template history limit reached");unique(actor,null,normalized,nameHash);
   String id=UUID.randomUUID().toString(),revision=UUID.randomUUID().toString();var now=j.queryForObject("SELECT UTC_TIMESTAMP(6)",java.time.LocalDateTime.class);
   j.update("INSERT INTO minutes_template(id,owner_id,name,name_normalized,name_hash,current_revision_id,revision,created_at) VALUES (?,?,?,?,?,?,1,?)",id,actor.enterpriseId(),name,normalized,nameHash,revision,now);
   j.update("INSERT INTO template_revision(id,template_id,revision,owner_id,name,themes,theme_count,created_at) VALUES (?,?,1,?,?,?,?,?)",revision,id,actor.enterpriseId(),name,encoded,themes.size(),now);
   j.update("INSERT INTO personal_template_state(template_id,owner_id,command_revision) VALUES (?,?,1)",id,actor.enterpriseId());
   var result=new View(id,revision,"1",name,document.themes(),false);record(actor,"PERSONAL_TEMPLATE_CREATED",result);accept(command,201,result);return result;
  });
 }
 public View update(Actor actor,String id,String key,long expected,String name,List<String> themes){
  ama.contract.MinutesContracts.uuid(id);revision(expected);String normalized=PersonalTemplateDocument.name(name);var document=new PersonalTemplateDocument(themes);String encoded=document.json(),nameHash=PersonalTemplateDocument.sha(normalized);var command=command(actor,id,"UPDATE",key,"personal-template-update-v1",Long.toString(expected),name,encoded);
  return tx.required(()->{active(actor);var replay=claim(actor,command);if(replay!=null)return replay;var old=owned(actor,id,true);if(!old.revisionToken().equals(Long.toString(expected)))throw new Conflict();if(expected>=1000)throw new IllegalArgumentException("Template revision limit reached");unique(actor,id,normalized,nameHash);
   String next=UUID.randomUUID().toString();long number=expected+1;
   j.update("INSERT INTO template_revision(id,template_id,revision,owner_id,name,themes,theme_count,created_at) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",next,id,number,actor.enterpriseId(),name,encoded,themes.size());
   j.update("UPDATE minutes_template SET name=?,name_normalized=?,name_hash=?,current_revision_id=?,revision=? WHERE id=?",name,normalized,nameHash,next,number,id);
   j.update("UPDATE personal_template_state SET command_revision=? WHERE template_id=?",number,id);
   var result=new View(id,next,Long.toString(number),name,document.themes(),false);record(actor,"PERSONAL_TEMPLATE_UPDATED",result);accept(command,200,result);return result;
  });
 }
 public View delete(Actor actor,String id,String key,long expected){
  ama.contract.MinutesContracts.uuid(id);revision(expected);var command=command(actor,id,"DELETE",key,"personal-template-delete-v1",Long.toString(expected));
  return tx.required(()->{active(actor);var replay=claim(actor,command);if(replay!=null)return replay;var old=owned(actor,id,true);if(!old.revisionToken().equals(Long.toString(expected)))throw new Conflict();
   j.update("UPDATE personal_template_state SET deleted_at=UTC_TIMESTAMP(6),command_revision=? WHERE template_id=?",expected+1,id);
   var result=new View(id,old.revisionId(),Long.toString(expected+1),old.name(),old.themes(),true);record(actor,"PERSONAL_TEMPLATE_DELETED",result);accept(command,200,result);return result;
  });
 }
 private void active(Actor actor){access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);}
 private View owned(Actor actor,String id,boolean lock){return owned(actor,id,lock,bytes->{});}
 private View owned(Actor actor,String id,boolean lock,java.util.function.LongConsumer beforeText){
  String suffix=lock?" FOR UPDATE":"";var rows=j.queryForList("SELECT p.current_revision_id,p.revision,p.owner_id,p.name_hash,s.command_revision,OCTET_LENGTH(p.name) pn,OCTET_LENGTH(p.name_normalized) pnn,OCTET_LENGTH(r.name) rn,OCTET_LENGTH(r.themes) rt,r.revision child_revision,r.owner_id child_owner,r.theme_count FROM personal_template_state s JOIN minutes_template p ON p.id=s.template_id JOIN template_revision r ON r.id=p.current_revision_id AND r.template_id=p.id WHERE s.template_id=? AND s.owner_id=? AND s.deleted_at IS NULL"+suffix,id,actor.enterpriseId());
  if(rows.size()!=1)throw new MeetingAccess.AccessDeniedException();var row=rows.getFirst();
  if(!actor.enterpriseId().equals(row.get("owner_id"))||!actor.enterpriseId().equals(row.get("child_owner"))||number(row,"revision")!=number(row,"command_revision")||number(row,"revision")!=number(row,"child_revision")||number(row,"revision")<1||number(row,"revision")>1000||number(row,"pn")>PersonalTemplateDocument.MAX_TEXT_BYTES||number(row,"pnn")>PersonalTemplateDocument.MAX_TEXT_BYTES*3||number(row,"rn")>PersonalTemplateDocument.MAX_TEXT_BYTES||number(row,"rt")>PersonalTemplateDocument.MAX_DOCUMENT_BYTES)throw new IllegalStateException("Template version unavailable");
  beforeText.accept(Math.addExact(1024,Math.addExact(number(row,"pn"),Math.addExact(number(row,"pnn"),Math.addExact(number(row,"rn"),number(row,"rt"))))));
  var content=j.queryForMap("SELECT p.name,p.name_normalized,r.name child_name,r.themes FROM minutes_template p JOIN template_revision r ON r.id=p.current_revision_id WHERE p.id=?",id);
  String name=(String)content.get("name"),normalized=PersonalTemplateDocument.name(name);if(!name.equals(content.get("child_name"))||!normalized.equals(content.get("name_normalized"))||!PersonalTemplateDocument.sha(normalized).equals(row.get("name_hash")))throw new IllegalStateException("Template name unavailable");
  var document=PersonalTemplateDocument.parse((String)content.get("themes"));if(document.themes().size()!=number(row,"theme_count"))throw new IllegalStateException("Template theme count unavailable");return new View(id,(String)row.get("current_revision_id"),Long.toString(number(row,"revision")),name,document.themes(),false);
 }
 private void unique(Actor actor,String id,String normalized,String hash){var names=j.queryForList("SELECT p.id,OCTET_LENGTH(p.name_normalized) bytes FROM personal_template_state s JOIN minutes_template p ON p.id=s.template_id WHERE s.owner_id=? AND s.deleted_at IS NULL AND p.name_hash=? AND (? IS NULL OR p.id<>?) LIMIT 1001",actor.enterpriseId(),hash,id,id);if(names.size()>1000)throw new IllegalStateException("Template name bucket unavailable");for(var row:names){if(number(row,"bytes")>PersonalTemplateDocument.MAX_TEXT_BYTES*3)throw new IllegalStateException("Template name unavailable");if(normalized.equals(j.queryForObject("SELECT name_normalized FROM minutes_template WHERE id=?",String.class,row.get("id"))))throw new Conflict();}}
 private static Command command(Actor actor,String resource,String operation,String key,String... fields){ama.transcript.JdbcProcessingIntake.key(key);String keyHash=PersonalTemplateDocument.digest(key);return new Command(PersonalTemplateDocument.digest("personal-template-command-v1",actor.enterpriseId(),resource,operation,key),resource,operation,keyHash,PersonalTemplateDocument.digest(fields));}
 private View claim(Actor actor,Command c){
  j.update("INSERT INTO personal_template_command_receipt(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,created_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=personal_template_command_receipt.scope_hash",c.scope(),actor.enterpriseId(),c.resource(),c.operation(),c.keyHash(),c.requestHash());
  var row=j.queryForMap("SELECT actor_id,resource_id,operation,key_hash,request_hash,http_status,OCTET_LENGTH(result_json) bytes,result_sha256 FROM personal_template_command_receipt WHERE scope_hash=? FOR UPDATE",c.scope());
  if(!actor.enterpriseId().equals(row.get("actor_id"))||!c.resource().equals(row.get("resource_id"))||!c.operation().equals(row.get("operation"))||!c.keyHash().equals(row.get("key_hash"))||!c.requestHash().equals(row.get("request_hash")))throw new Conflict();int status=((Number)row.get("http_status")).intValue();if(status==0)return null;
  if(status!=(c.operation().equals("CREATE")?201:200)||number(row,"bytes")>131072)throw new IllegalStateException("Template receipt unavailable");
  String raw=j.queryForObject("SELECT result_json FROM personal_template_command_receipt WHERE scope_hash=?",String.class,c.scope());if(!PersonalTemplateDocument.sha(raw).equals(row.get("result_sha256")))throw new IllegalStateException("Template receipt digest unavailable");
  try{var result=JSON.readValue(raw,View.class);if(!JSON.writeValueAsString(result).equals(raw)||!c.operation().equals("CREATE")&&!c.resource().equals(result.id())||result.deleted()!=c.operation().equals("DELETE"))throw new IllegalStateException("Template receipt binding unavailable");return result;}catch(java.io.IOException|IllegalArgumentException invalid){throw new IllegalStateException("Template receipt unavailable",invalid);}
 }
 private void accept(Command c,int status,View result){try{String raw=JSON.writeValueAsString(result);if(j.update("UPDATE personal_template_command_receipt SET http_status=?,result_json=?,result_sha256=? WHERE scope_hash=? AND http_status=0",status,raw,PersonalTemplateDocument.sha(raw),c.scope())!=1)throw new IllegalStateException("Template receipt unavailable");}catch(java.io.IOException impossible){throw new IllegalStateException(impossible);}}
 private void record(Actor actor,String action,View value){audit.append(new AuditEvent(actor.enterpriseId(),action,value.id(),Long.parseLong(value.revisionToken()),"SUCCEEDED",UUID.randomUUID().toString(),Instant.now()));}
 private static void revision(long expected){if(expected<1||expected>=Long.MAX_VALUE-1)throw new IllegalArgumentException("Exact template revision required");}
 private static long number(Map<String,Object> row,String name){var value=row.get(name);if(!(value instanceof Number n))throw new IllegalStateException("Template numeric metadata unavailable");return n.longValue();}
}
