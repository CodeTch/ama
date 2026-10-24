package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TranscriptContracts.*;
import ama.db.TransactionRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Stable, bounded acceptance lookup. No archive, runtime budget, source route or correction dependency. */
public final class JdbcSavedTranscriptReceipts {
 private final JdbcTemplate j;private final TransactionRunner tx;private final MeetingAccess access;
 public JdbcSavedTranscriptReceipts(DataSource ds,TransactionRunner tx,MeetingAccess access){j=new JdbcTemplate(ds);j.setQueryTimeout(5);this.tx=tx;this.access=access;}
 record Identity(String scope,String key,String request,String operation) {}
 record Stored(String result,int status,boolean matches) {}
 static Identity identity(Actor actor,String meeting,String key,Command command){
  JdbcProcessingIntake.key(key);var digest=new Digest();digest.add("saved-transcript-request-v1");digest.add(command.operation().name());digest.add(Long.toString(command.expectedRevision()));digest.add(command.sourceRevisionId());digest.add(Integer.toString(command.segments().size()));
  for(var segment:command.segments()){digest.add(segment.segmentId());digest.add(segment.body());digest.add(Integer.toString(segment.marks().size()));for(var mark:segment.marks())digest.add(mark.name());}
  String operation="TRANSCRIPT_"+command.operation().name();return new Identity(JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,operation,key),JdbcProcessingIntake.digest(key),digest.finish(),operation);
 }
 public Optional<Map<String,Object>> lookup(Actor actor,String meeting,String key,Command command){return lookup(actor,meeting,identity(actor,meeting,key,command),command);}
 Optional<Map<String,Object>> lookup(Actor actor,String meeting,Identity id,Command command){return tx.required(()->{
  var record=record(actor,meeting,id);access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);
  if(record.isEmpty())return Optional.empty();match(record.orElseThrow());return Optional.of(restore(meeting,record.orElseThrow(),command,true));
 });}
 Stored claim(Actor actor,String meeting,Identity id){
  j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,?,?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",id.scope(),actor.enterpriseId(),meeting,id.operation(),id.key(),id.request());
  return record(actor,meeting,id).orElseThrow(JdbcSavedTranscriptReceipts::unavailable);
 }
 private Optional<Stored> record(Actor actor,String meeting,Identity id){return j.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",(r,n)->new Stored(r.getString(6),r.getInt(7),actor.enterpriseId().equals(r.getString(1))&&meeting.equals(r.getString(2))&&id.operation().equals(r.getString(3))&&id.key().equals(r.getString(4))&&id.request().equals(r.getString(5))&&"SUCCEEDED".equals(r.getString(8))),id.scope()).stream().findFirst();}
 static void match(Stored stored){if(!stored.matches())throw new Failure(409,"IDEMPOTENCY_CONFLICT");}
 void accepted(Identity id,String result){if(j.update("UPDATE idempotency_record SET result_id=?,http_status=200 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",result,id.scope())!=1)throw unavailable();}
 Map<String,Object> restore(String meeting,Stored stored,Command command,boolean replayed){
  if(stored.result()==null||stored.status()!=200)throw unavailable();
  var result=new LinkedHashMap<String,Object>();boolean confirm=command.operation()==Operation.CONFIRM;
  if(confirm){
   var locked=j.queryForList("SELECT kind FROM transcript_revision WHERE id=? AND meeting_id=? FOR UPDATE",String.class,command.sourceRevisionId(),meeting);if(!locked.equals(List.of("WORKING")))throw unavailable();
   var rows=j.queryForList("SELECT id,revision_id,content_hash FROM transcript_confirmation WHERE id=? AND meeting_id=? FOR UPDATE",stored.result(),meeting);
   if(rows.size()!=1||!command.sourceRevisionId().equals(rows.getFirst().get("revision_id"))||!(rows.getFirst().get("content_hash") instanceof String hash)||!hash.matches("[0-9a-f]{64}"))throw unavailable();
   var revisions=j.queryForList("SELECT kind FROM transcript_revision WHERE id=? AND meeting_id=?",String.class,command.sourceRevisionId(),meeting);if(!revisions.equals(List.of("WORKING")))throw unavailable();
   result.put("kind","TRANSCRIPT_CONFIRMATION");result.put("meetingId",meeting);result.put("confirmationId",stored.result());result.put("revisionId",command.sourceRevisionId());result.put("contentHash",hash);
  }else{
   var rows=j.queryForList("SELECT id,revision,parent_id,kind,producer_task_id FROM transcript_revision WHERE id=? AND meeting_id=? FOR UPDATE",stored.result(),meeting);
   if(rows.size()!=1)throw unavailable();var r=rows.getFirst();long revision=((Number)r.get("revision")).longValue();
   if(!"WORKING".equals(r.get("kind"))||r.get("producer_task_id")!=null||!command.sourceRevisionId().equals(r.get("parent_id"))||revision!=command.expectedRevision()+1)throw unavailable();
   result.put("kind","SAVED_REVISION");result.put("meetingId",meeting);result.put("revisionId",stored.result());result.put("historyRevision",revision);result.put("historyRevisionToken",Long.toString(revision));
  }
  var roots=j.queryForList("SELECT revision,access_revision,transcript_revision_id,transcript_confirmation_id FROM meeting WHERE id=?",meeting);if(roots.size()!=1)throw unavailable();var root=roots.getFirst();
  return Map.of("result",Collections.unmodifiableMap(result),"replayed",replayed,"resultIsCurrent",stored.result().equals(root.get(confirm?"transcript_confirmation_id":"transcript_revision_id")),"observation",JdbcSavedTranscripts.observation(root));
 }
 private static Failure unavailable(){return new Failure(503,"PROVENANCE_UNAVAILABLE");}
 private static final class Digest {
  private final MessageDigest digest;Digest(){try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
  void add(String value){if(value==null){digest.update(ByteBuffer.allocate(4).putInt(-1).array());return;}ama.contract.MinutesContracts.scalars(value);long size=ama.canonical.CanonicalValueCodec.utf8Length(value,()->false);if(size>Integer.MAX_VALUE)throw new Failure(413,"REQUEST_TOO_LARGE");digest.update(ByteBuffer.allocate(4).putInt((int)size).array());
   // Bounded slices avoid retaining a second full request encoding.
   for(int offset=0;offset<value.length();){int end=Math.min(offset+4096,value.length());if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1)))end--;digest.update(value.substring(offset,end).getBytes(StandardCharsets.UTF_8));offset=end;}
  }
  String finish(){return HexFormat.of().formatHex(digest.digest());}
 }
}
