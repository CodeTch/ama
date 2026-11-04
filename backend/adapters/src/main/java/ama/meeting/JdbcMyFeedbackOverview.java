package ama.meeting;

import ama.contract.IdentityContracts.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Merge both authoritative resources before filtering, counting and keyset pagination. */
final class JdbcMyFeedbackOverview {
 private final JdbcTemplate j;
 JdbcMyFeedbackOverview(JdbcTemplate j){this.j=j;}
 private static final String SOURCE="(SELECT 'CORPUS' kind,f.id,f.meeting_id meetingId,f.submitted_by,f.category,f.comment,f.state,CAST(f.revision AS CHAR) revisionToken,f.created_at,DATE_FORMAT(f.created_at,'%Y-%m-%dT%H:%i:%s.%fZ') submittedAt,f.review_reason reviewReason,f.feedback_type feedbackType,f.original_value originalValue,f.suggested_value suggestedValue,f.note FROM feedback f LEFT JOIN corpus_feedback_binding b ON b.feedback_id=f.id UNION ALL SELECT 'VOICEPRINT',v.id,v.meeting_id,v.applicant_id,'VOICEPRINT',v.subject_name,v.state,CAST(v.revision AS CHAR),v.created_at,DATE_FORMAT(v.created_at,'%Y-%m-%dT%H:%i:%s.%fZ'),v.review_reason,'VOICEPRINT','会议匿名说话人',v.subject_name,'' FROM meeting_voiceprint_application v) overview";
 Map<String,Object> read(Actor actor,String cursor,String state,boolean reviewing){
  var states=List.of("PENDING","ACCEPTED","REJECTED","NEEDS_MORE");if(state!=null&&!states.contains(state))throw new IllegalArgumentException("Invalid feedback state");
  String scope=reviewing?"1=1":"submitted_by=?";var args=new ArrayList<Object>();if(!reviewing)args.add(actor.enterpriseId());
  var counts=new LinkedHashMap<String,Object>();for(String s:states)counts.put(s,0L);long total=0;
  for(var row:j.queryForList("SELECT state,COUNT(*) amount FROM "+SOURCE+" WHERE "+scope+" GROUP BY state",args.toArray())){long n=((Number)row.get("amount")).longValue();counts.put((String)row.get("state"),n);total+=n;}counts.put("ALL",total);
  String filter=scope;if(state!=null){filter+=" AND state=?";args.add(state);}
  String binding=MeetingDigests.fields("my-feedback-v1",actor.enterpriseId(),Boolean.toString(reviewing),state);
  if(cursor!=null){String kind="CORPUS",id=cursor;
   if(cursor.startsWith("f1.")){try{var parts=new String(Base64.getUrlDecoder().decode(cursor.substring(3)),StandardCharsets.UTF_8).split("\\|",-1);if(parts.length!=3||!parts[0].equals(binding)||!Set.of("CORPUS","VOICEPRINT").contains(parts[1]))throw new IllegalArgumentException();kind=parts[1];id=parts[2];}catch(RuntimeException bad){throw new IllegalArgumentException("Invalid feedback cursor");}}
   ama.contract.MinutesContracts.uuid(id);var lookup=new ArrayList<>(args);Collections.addAll(lookup,kind,id);var previous=j.queryForList("SELECT created_at FROM "+SOURCE+" WHERE "+filter+" AND kind=? AND id=?",lookup.toArray());if(previous.size()!=1)throw new IllegalArgumentException("Invalid feedback cursor");Object time=previous.getFirst().get("created_at");
   filter+=" AND (created_at<? OR (created_at=? AND (kind<? OR (kind=? AND id<?))))";Collections.addAll(args,time,time,kind,kind,id);
  }
  var rows=j.queryForList("SELECT * FROM "+SOURCE+" WHERE "+filter+" ORDER BY created_at DESC,kind DESC,id DESC LIMIT 21",args.toArray());var items=new ArrayList<>(rows.subList(0,Math.min(20,rows.size())));
  String next=null;if(rows.size()>20){var last=items.getLast();next="f1."+Base64.getUrlEncoder().withoutPadding().encodeToString((binding+"|"+last.get("kind")+"|"+last.get("id")).getBytes(StandardCharsets.UTF_8));}
  for(var row:items){var usage="VOICEPRINT".equals(row.get("kind"))?ama.governance.GovernanceUsage.voice(j,(String)row.get("id")):ama.governance.GovernanceUsage.feedback(j,(String)row.get("id"),actor.enterpriseId());row.put("usageState",usage.get("usageState"));row.put("usageVersion",usage.get("usageVersion"));row.remove("created_at");Object submitter=row.remove("submitted_by");if(reviewing&&!actor.enterpriseId().equals(submitter))for(String field:List.of("comment","originalValue","suggestedValue","note"))if(row.get(field) instanceof String text)row.put(field,ama.governance.CorpusFeedbackService.redact(text));}
  var result=new LinkedHashMap<String,Object>();result.put("items",items);result.put("counts",counts);result.put("nextCursor",next);return result;
 }
}
