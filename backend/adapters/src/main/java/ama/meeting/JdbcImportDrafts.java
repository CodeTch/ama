package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.Conflict;
import ama.contract.TaskContracts.SubjectRef;
import ama.contract.AuditContracts.AuditEvent;
import ama.audit.JdbcAuditAdapter;
import ama.db.TransactionRunner;
import ama.task.SubjectFencePort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** A saved selection is server state, never an instruction to retain private browser requests. */
public final class JdbcImportDrafts {
 private final JdbcTemplate j;private final TransactionRunner tx;private final JdbcMeetingAccess access;private final SubjectFencePort fences;private final JdbcAuditAdapter audit;private final ObjectMapper json=new ObjectMapper();
 public JdbcImportDrafts(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,SubjectFencePort fences){j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=tx;this.access=access;this.fences=fences;audit=new JdbcAuditAdapter(source);}
 public Map<String,Object> read(Actor actor,String meeting){uuid(meeting);return tx.required(()->{var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);return snapshot(meeting);});}
 public Map<String,Object> save(Actor actor,String meeting,String key,long expected,List<String> selected){
  uuid(meeting);uuid(key);validate(expected,selected);String hash=hash("SAVE",Long.toString(expected),String.join(",",selected));
  return tx.required(()->{
   var gate=(JdbcMeetingAccess.Held)access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);var original=replay(actor,meeting,"SAVE",key,hash);if(original!=null)return original;
   writable(gate.root);long current=revision(meeting);if(current!=expected||current>=Long.MAX_VALUE-1)throw new Conflict();
   // Single root serialization keeps source membership and the replacement selection atomic.
   for(String file:selected.stream().sorted().toList())source(meeting,file);
   if(current==0)j.update("INSERT INTO meeting_import_draft(meeting_id,revision,saved_by,saved_at) VALUES (?,1,?,UTC_TIMESTAMP(6))",meeting,actor.enterpriseId());
   else j.update("UPDATE meeting_import_draft SET revision=revision+1,saved_by=?,saved_at=UTC_TIMESTAMP(6) WHERE meeting_id=?",actor.enterpriseId(),meeting);
   j.update("DELETE FROM meeting_import_draft_file WHERE meeting_id=?",meeting);int ordinal=1;for(String file:selected)j.update("INSERT INTO meeting_import_draft_file(meeting_id,file_id,ordinal) VALUES (?,?,?)",meeting,file,ordinal++);
   j.update("UPDATE meeting SET updated_at=UTC_TIMESTAMP(6) WHERE id=?",meeting);var result=snapshot(meeting);record(actor,meeting,"SAVE",key,hash,result);audit.append(new AuditEvent(actor.enterpriseId(),"MEETING_IMPORT_SAVED",meeting,gate.root.revision(),"SUCCEEDED",UUID.randomUUID().toString(),Instant.now()));return result;
  });
 }
 public Map<String,Object> discard(Actor actor,String meeting,String key,long expected){
  uuid(meeting);uuid(key);if(expected<0||expected==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid revision");String hash=hash("DISCARD",Long.toString(expected));
  return tx.required(()->{
   // A deleted draft still admits its exact owner receipt replay, never another operation or actor.
   var identities=access.tryLockFactIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);if(!access.factActorActive(identities))throw new MeetingAccess.AccessDeniedException();access.lockFactRoot(identities,meeting,false).orElseThrow(MeetingAccess.AccessDeniedException::new);
   var root=j.queryForMap("SELECT owner_id,revision,cancel_epoch,lifecycle,stage,ever_published,final_confirmation_id FROM meeting WHERE id=? FOR UPDATE",meeting);if(!actor.enterpriseId().equals(root.get("owner_id")))throw new MeetingAccess.AccessDeniedException();var original=replay(actor,meeting,"DISCARD",key,hash);if(original!=null)return original;
   long revision=((Number)root.get("revision")).longValue(),epoch=((Number)root.get("cancel_epoch")).longValue();if(!"ACTIVE".equals(root.get("lifecycle"))||!"ENTRY".equals(root.get("stage"))||((Number)root.get("ever_published")).intValue()!=0||root.get("final_confirmation_id")!=null||revision!=expected||revision>=Long.MAX_VALUE-1||epoch>=Long.MAX_VALUE-1)throw new Conflict();
   // A discard cannot race a worker or hide an unconfirmed upload. Preserve all original receipts.
   if(j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_kind='MEETING' AND subject_id=?",Long.class,meeting)!=0||j.queryForObject("SELECT COUNT(*) FROM upload_session WHERE meeting_id=? AND state IN ('OPEN','FINALIZING')",Long.class,meeting)!=0)throw new Conflict();
   var subject=fences.lock(new SubjectRef("MEETING",meeting),false).orElseThrow();j.update("UPDATE meeting SET lifecycle='DELETED',revision=revision+1,cancel_epoch=cancel_epoch+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",meeting);fences.advance(subject,revision+1,epoch+1,true);
   var result=Map.<String,Object>of("meetingId",meeting,"state","DISCARDED","revisionToken",Long.toString(revision+1));record(actor,meeting,"DISCARD",key,hash,result);audit.append(new AuditEvent(actor.enterpriseId(),"MEETING_IMPORT_DISCARDED",meeting,revision+1,"SUCCEEDED",UUID.randomUUID().toString(),Instant.now()));return result;
  });
 }
 public static void validate(long expected,List<String> files){if(expected<0||expected>=Long.MAX_VALUE||files==null||files.isEmpty()||files.size()>16||new HashSet<>(files).size()!=files.size())throw new IllegalArgumentException("Invalid import selection");files.forEach(JdbcImportDrafts::uuid);}
 private void writable(JdbcMeetingAccess.Root root){if(!root.stage().equals("ENTRY")||root.published())throw new Conflict();}
 private long revision(String meeting){var rows=j.queryForList("SELECT revision FROM meeting_import_draft WHERE meeting_id=? FOR UPDATE",Long.class,meeting);return rows.isEmpty()?0:rows.getFirst();}
 private Map<String,Object> source(String meeting,String file){var rows=j.queryForList("SELECT u.id AS uploadId,u.completed_file_id AS fileId FROM upload_session u JOIN file_object f ON f.id=u.completed_file_id WHERE u.meeting_id=? AND f.meeting_id=? AND u.completed_file_id=? AND u.purpose='TRANSCRIPT_INPUT' AND u.state='COMPLETED' AND f.state='AVAILABLE' AND f.sha256=u.expected_sha256 AND f.size_bytes=u.expected_size FOR UPDATE",meeting,meeting,file);if(rows.size()!=1)throw new Conflict();return rows.getFirst();}
 private Map<String,Object> snapshot(String meeting){long revision=revision(meeting);var ids=j.queryForList("SELECT file_id FROM meeting_import_draft_file WHERE meeting_id=? ORDER BY ordinal",String.class,meeting);if(ids.size()>16)throw new IllegalStateException("Import selection exceeds budget");var sources=ids.stream().map(id->source(meeting,id)).toList();return Map.of("meetingId",meeting,"revisionToken",Long.toString(revision),"meetingRevisionToken",j.queryForObject("SELECT CAST(revision AS CHAR) FROM meeting WHERE id=?",String.class,meeting),"orderedFileIds",ids,"files",sources);}
 private Map<String,Object> replay(Actor actor,String meeting,String operation,String key,String hash){var rows=j.queryForList("SELECT request_hash,result_json FROM meeting_import_command WHERE meeting_id=? AND actor_id=? AND operation=? AND command_key=? FOR UPDATE",meeting,actor.enterpriseId(),operation,key);if(rows.isEmpty())return null;var row=rows.getFirst();if(!hash.equals(row.get("request_hash")))throw new Conflict();try{return json.readValue((String)row.get("result_json"),new TypeReference<Map<String,Object>>(){});}catch(java.io.IOException e){throw new IllegalStateException("Invalid saved import receipt");}}
 private void record(Actor actor,String meeting,String operation,String key,String hash,Map<String,Object> result){try{j.update("INSERT INTO meeting_import_command(meeting_id,actor_id,operation,command_key,request_hash,result_json,created_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6))",meeting,actor.enterpriseId(),operation,key,hash,json.writeValueAsString(result));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 private static void uuid(String value){ama.contract.MinutesContracts.uuid(value);}
 private static String hash(String... parts){try{var d=MessageDigest.getInstance("SHA-256");for(String part:parts){byte[] b=part.getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}return HexFormat.of().formatHex(d.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
