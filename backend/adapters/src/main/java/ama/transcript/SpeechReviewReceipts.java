package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.db.TransactionRunner;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;

/** Accepted proposal batches use the existing no-FK idempotency rank before the owner root. */
public final class SpeechReviewReceipts {
 private final JdbcTemplate j;private final TransactionRunner tx;private final MeetingAccess access;private final JdbcSavedTranscriptReceipts stable;
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 SpeechReviewReceipts(JdbcTranscriptAuthority authority){this(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());}
 public SpeechReviewReceipts(javax.sql.DataSource source,TransactionRunner tx,MeetingAccess access){j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=tx;this.access=access;stable=new JdbcSavedTranscriptReceipts(source,tx,access);}
 public Optional<Map<String,Object>> lookup(Actor actor,String meeting,String key,long expected,String source){return lookup(actor,meeting,identity(actor,meeting,key,expected,source),expected,source);}
 static JdbcSavedTranscriptReceipts.Identity identity(Actor actor,String meeting,String key,long expected,String source){
  JdbcProcessingIntake.key(key);uuid(meeting);uuid(source);if(expected<0||expected>=Long.MAX_VALUE)throw new IllegalArgumentException("Valid proposal revision required");String operation="TRANSCRIPT_SPEECH_REVIEW_PROPOSE";
  return new JdbcSavedTranscriptReceipts.Identity(JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,operation,key),JdbcProcessingIntake.digest(key),JdbcProcessingIntake.digest("speech-review-proposal-v1",Long.toString(expected),source),operation);
 }
 Optional<Map<String,Object>> lookup(Actor actor,String meeting,JdbcSavedTranscriptReceipts.Identity id,long expected,String source){return tx.required(()->{
  var stored=j.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",(r,n)->new JdbcSavedTranscriptReceipts.Stored(r.getString(6),r.getInt(7),actor.enterpriseId().equals(r.getString(1))&&meeting.equals(r.getString(2))&&id.operation().equals(r.getString(3))&&id.key().equals(r.getString(4))&&id.request().equals(r.getString(5))&&"SUCCEEDED".equals(r.getString(8))),id.scope());
  access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);if(stored.isEmpty())return Optional.empty();if(stored.size()!=1)throw unavailable();JdbcSavedTranscriptReceipts.match(stored.getFirst());return Optional.of(restore(actor,meeting,id,stored.getFirst(),expected,source,true));
 });}
 JdbcSavedTranscriptReceipts.Stored claim(Actor actor,String meeting,JdbcSavedTranscriptReceipts.Identity id){return stable.claim(actor,meeting,id);}
 void accepted(JdbcSavedTranscriptReceipts.Identity id,String batch){stable.accepted(id,batch);}
 Map<String,Object> restore(Actor actor,String meeting,JdbcSavedTranscriptReceipts.Identity id,JdbcSavedTranscriptReceipts.Stored stored,long expected,String source,boolean replayed){
  if(stored.result()==null||stored.status()!=200)throw unavailable();
  var rows=j.queryForList("SELECT id,scope_sha256,request_sha256,meeting_id,source_revision_id,expected_revision,actor_id,created_count,IF(OCTET_LENGTH(CAST(candidate_ids AS CHAR CHARACTER SET utf8mb4))<=4096,CAST(candidate_ids AS CHAR CHARACTER SET utf8mb4),NULL) candidate_ids FROM speech_review_batch WHERE id=? FOR UPDATE",stored.result());
  if(rows.size()!=1)throw unavailable();var row=rows.getFirst();if(!actor.enterpriseId().equals(row.get("actor_id"))||!meeting.equals(row.get("meeting_id"))||!id.scope().equals(row.get("scope_sha256"))||!id.request().equals(row.get("request_sha256"))||!source.equals(row.get("source_revision_id"))||SpeechReviewRepository.number(row,"expected_revision")!=expected)throw unavailable();
  var ids=new ArrayList<String>();try{Object value=row.get("candidate_ids");if(!(value instanceof String text)||text.length()>4096)throw unavailable();var list=JSON.readTree(text);if(!list.isArray()||list.size()>60)throw unavailable();for(var item:list){if(!item.isTextual())throw unavailable();String candidate=item.textValue();uuid(candidate);if(ids.contains(candidate))throw unavailable();ids.add(candidate);}}catch(java.io.IOException|RuntimeException invalid){throw unavailable();}
  long created=SpeechReviewRepository.number(row,"created_count");if(created<0||created>ids.size())throw unavailable();
  var roots=j.queryForList("SELECT revision,access_revision,transcript_revision_id,transcript_confirmation_id FROM meeting WHERE id=?",meeting);if(roots.size()!=1)throw unavailable();var root=roots.getFirst();
  var result=Map.of("reviewBatchId",stored.result(),"sourceRevisionId",source,"candidateIds",List.copyOf(ids),"createdCount",created);
  return Map.of("result",result,"replayed",replayed,"resultIsCurrent",source.equals(root.get("transcript_revision_id"))&&SpeechReviewRepository.number(root,"revision")==expected,"observation",JdbcSavedTranscripts.observation(root));
 }
 private static ama.contract.TranscriptContracts.Failure unavailable(){return new ama.contract.TranscriptContracts.Failure(503,"PROVENANCE_UNAVAILABLE");}
}
