package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.db.TransactionRunner;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcFeedback {
    private final JdbcTemplate j; private final TransactionRunner tx; private final JdbcMeetingAccess access;
    private static final String FIELDS="id,meeting_id meetingId,category,comment,state,CAST(revision AS CHAR) revisionToken";
    // At most ~700KiB of explicitly submitted text, including history, in a detail response.
    private static final int MAX_HISTORY=50;
    public static final Set<String> TYPES=Set.of("PERSON","DEPARTMENT","PRODUCT","FINANCIAL_TERM","PRODUCT_ALIAS","TERM_PRONUNCIATION");
    public JdbcFeedback(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=tx;this.access=access;}
    public Map<String,Object> create(Actor actor,String key,String meeting,String category,String comment){
        return create(actor,key,meeting,category,comment,null,null,null);
    }
    public Map<String,Object> createStructured(Actor actor,String key,String meeting,String type,String original,String suggested,String note){
        if(!TYPES.contains(Objects.requireNonNullElse(type,"")))throw new IllegalArgumentException("Prototype feedback type required");
        bounded(original,2048);bounded(suggested,2048);
        if(note==null||note.length()>4096||note.indexOf(0)>=0)throw new IllegalArgumentException("Bounded optional note required");
        return create(actor,key,meeting,type,note,original,suggested,note);
    }
    private Map<String,Object> create(Actor actor,String key,String meeting,String category,String comment,String original,String suggested,String note){
        ama.contract.MinutesContracts.uuid(meeting);ama.transcript.JdbcProcessingIntake.key(key);
        if(original==null&&(category==null||!Set.of("WORDING","LABEL","ACTION_OWNER").contains(category)||comment==null||comment.isBlank()||comment.length()>4096||comment.indexOf(0)>=0))
            throw new IllegalArgumentException("Bounded feedback content required");
        String scope=MeetingDigests.fields("feedback-command-scope-v1",actor.enterpriseId(),meeting,"CREATE",key);
        String keyHash=MeetingDigests.fields(key),request=original==null?MeetingDigests.fields("feedback-create-v1",meeting,category,comment):MeetingDigests.fields("feedback-create-v2",meeting,category,original,suggested,note);
        return tx.required(()->{
            j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'FEEDBACK_CREATE',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",scope,actor.enterpriseId(),meeting,keyHash,request);
            var stored=j.queryForMap("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);
            var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);
            if(!actor.enterpriseId().equals(stored.get("actor_id"))||!meeting.equals(stored.get("resource_id"))||!"FEEDBACK_CREATE".equals(stored.get("operation"))||!keyHash.equals(stored.get("key_hash"))||!request.equals(stored.get("request_hash"))||!"SUCCEEDED".equals(stored.get("state")))throw new ama.contract.MeetingContracts.Conflict();
            if(stored.get("result_id") instanceof String id){
                if(((Number)stored.get("http_status")).intValue()!=201)throw new IllegalStateException("Feedback receipt unavailable");
                var row=j.queryForMap("SELECT "+FIELDS+" FROM feedback WHERE id=? AND meeting_id=? AND submitted_by=?",id,meeting,actor.enterpriseId());
                if(!category.equals(row.get("category"))||!comment.equals(row.get("comment")))throw new IllegalStateException("Feedback receipt changed");
                // A creation ACK records its original pending submission even after a later review.
                row.put("state","PENDING");row.put("revisionToken","0");return row;
            }
            if(((Number)stored.get("http_status")).intValue()!=0)throw new IllegalStateException("Feedback receipt unavailable");
            String id=UUID.randomUUID().toString();
            j.update("INSERT INTO feedback(id,meeting_id,submitted_by,category,comment,state,revision,created_at) VALUES (?,?,?,?,?,'PENDING',0,UTC_TIMESTAMP(6))",id,meeting,actor.enterpriseId(),category,comment);
            if(original!=null)j.update("UPDATE feedback SET feedback_type=?,original_value=?,suggested_value=?,note=? WHERE id=?",category,original,suggested,note,id);
            if(j.update("UPDATE idempotency_record SET result_id=?,http_status=201 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",id,scope)!=1)throw new IllegalStateException("Feedback receipt unavailable");
            new ama.audit.JdbcAuditAdapter(j.getDataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),"FEEDBACK_SUBMITTED",id,0,"SUCCEEDED",UUID.randomUUID().toString(),java.time.Instant.now()));
            return j.queryForMap("SELECT "+FIELDS+" FROM feedback WHERE id=?",id);
        });
    }
    public Map<String,Object> list(Actor actor,String cursor){
        if(cursor!=null)ama.contract.MinutesContracts.uuid(cursor);
        return tx.required(()->{
            access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);
            var args=new ArrayList<Object>();args.add(actor.enterpriseId());String before="";
            if(cursor!=null){
                var rows=j.queryForList("SELECT created_at FROM feedback WHERE id=? AND submitted_by=?",cursor,actor.enterpriseId());
                if(rows.size()!=1)throw new IllegalArgumentException("Invalid feedback cursor");
                Object time=rows.getFirst().get("created_at");before=" AND (created_at<? OR (created_at=? AND id<?))";Collections.addAll(args,time,time,cursor);
            }
            var rows=j.queryForList("SELECT "+FIELDS+" FROM feedback WHERE submitted_by=?"+before+" ORDER BY created_at DESC,id DESC LIMIT 21",args.toArray());
            boolean more=rows.size()>20;var selected=List.copyOf(rows.subList(0,Math.min(20,rows.size())));
            var result=new HashMap<String,Object>();result.put("items",selected);result.put("nextCursor",more?selected.getLast().get("id"):null);return result;
        });
    }

    /** Submitted feedback is its own private resource; reviewing it never grants source access. */
    public Map<String,Object> detail(Actor actor,String id){
        ama.contract.MinutesContracts.uuid(id);
        return tx.required(()->{
            active(actor);boolean admin=admin(actor);var row=locked(id,false);
            if(!actor.enterpriseId().equals(row.get("submitted_by"))&&!admin)throw new MeetingAccess.AccessDeniedException();
            var history=j.queryForList("SELECT id,action,actor_id actorId,CAST(revision_after AS CHAR) revisionToken,state_after state,text,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') createdAt FROM feedback_history WHERE feedback_id=? ORDER BY revision_after LIMIT 51",id);
            if(history.size()>MAX_HISTORY)throw new IllegalStateException("Feedback history limit exceeded");
            var result=new LinkedHashMap<String,Object>();var displayed=view(row);if(admin&&!actor.enterpriseId().equals(row.get("submitted_by"))){displayed.put("comment",ama.governance.CorpusFeedbackService.redact((String)displayed.get("comment")));history.forEach(entry->entry.put("text",ama.governance.CorpusFeedbackService.redact((String)entry.get("text"))));}result.put("feedback",displayed);result.put("submittedAt",row.get("submittedAt"));result.put("reviewReason",row.get("review_reason"));result.put("reviewedBy",row.get("reviewed_by"));result.put("history",history);
            var structured=j.queryForList("SELECT f.feedback_type feedbackType,f.original_value originalValue,f.suggested_value suggestedValue,f.note,CAST(b.source_revision AS CHAR) sourceRevisionToken,CAST(b.segment_ids AS CHAR) segmentIds FROM feedback f LEFT JOIN corpus_feedback_binding b ON b.feedback_id=f.id WHERE f.id=? AND f.feedback_type IS NOT NULL",id);
            if(!structured.isEmpty()){var submitted=structured.getFirst();if(admin&&!actor.enterpriseId().equals(row.get("submitted_by")))for(String field:List.of("originalValue","suggestedValue","note"))submitted.put(field,ama.governance.CorpusFeedbackService.redact((String)submitted.get(field)));result.put("submission",submitted);}
            result.put("usage",ama.governance.GovernanceUsage.feedback(j,id,actor.enterpriseId()));
            return result;
        });
    }

    public Map<String,Object> reviewQueue(Actor actor,String cursor){
        if(cursor!=null)ama.contract.MinutesContracts.uuid(cursor);
        return tx.required(()->{
            active(actor);requireAdmin(actor);var args=new ArrayList<Object>();String before="";
            if(cursor!=null){var rows=j.queryForList("SELECT created_at FROM feedback WHERE id=?",cursor);if(rows.size()!=1)throw new IllegalArgumentException("Invalid review cursor");Object time=rows.getFirst().get("created_at");before=" WHERE (created_at<? OR (created_at=? AND id<?))";Collections.addAll(args,time,time,cursor);}
            var rows=j.queryForList("SELECT "+FIELDS+" FROM feedback"+before+" ORDER BY created_at DESC,id DESC LIMIT 21",args.toArray());
            var selected=List.copyOf(rows.subList(0,Math.min(20,rows.size())));var result=new LinkedHashMap<String,Object>();selected.forEach(entry->entry.put("comment",ama.governance.CorpusFeedbackService.redact((String)entry.get("comment"))));result.put("items",selected);result.put("nextCursor",rows.size()>20?selected.getLast().get("id"):null);return result;
        });
    }

    /** Metadata-only overview; avoid reading every history merely to paint the feedback table. */
    public Map<String,Object> summaries(Actor actor,String cursor,String selectedState,boolean reviewing){
        return tx.required(()->{active(actor);if(reviewing)requireAdmin(actor);return new JdbcMyFeedbackOverview(j).read(actor,cursor,selectedState,reviewing);});
    }

    public Map<String,Object> supplement(Actor actor,String id,String key,long expected,String text){
        bounded(text,4096);return command(actor,id,key,expected,"SUPPLEMENT",null,text);
    }
    public Map<String,Object> review(Actor actor,String id,String key,long expected,String decision,String reason){
        if(!Set.of("ACCEPTED","REJECTED","NEEDS_MORE").contains(Objects.requireNonNullElse(decision,"")))throw new IllegalArgumentException("Review decision required");
        bounded(reason,2048);return command(actor,id,key,expected,"REVIEW",decision,reason);
    }
    public Map<String,Object> undoReview(Actor actor,String id,String key,long expected){return command(actor,id,key,expected,"UNDO_REVIEW",null,"");}

    private Map<String,Object> command(Actor actor,String id,String key,long expected,String action,String decision,String text){
        ama.contract.MinutesContracts.uuid(id);ama.transcript.JdbcProcessingIntake.key(key);
        if(expected<0||expected==Long.MAX_VALUE)throw new IllegalArgumentException("Exact feedback revision required");
        String operation="FEEDBACK_"+action,scope=MeetingDigests.fields("feedback-command-scope-v2",actor.enterpriseId(),id,action,key),keyHash=MeetingDigests.fields(key),request=MeetingDigests.fields("feedback-history-v1",id,action,Long.toString(expected),decision,text);
        return tx.required(()->{
            j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,?,?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",scope,actor.enterpriseId(),id,operation,keyHash,request);
            var receipt=j.queryForMap("SELECT actor_id,resource_id,operation,key_hash,request_hash,state,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);
            if(!actor.enterpriseId().equals(receipt.get("actor_id"))||!id.equals(receipt.get("resource_id"))||!operation.equals(receipt.get("operation"))||!keyHash.equals(receipt.get("key_hash"))||!request.equals(receipt.get("request_hash"))||!"SUCCEEDED".equals(receipt.get("state")))throw new ama.contract.MeetingContracts.Conflict();
            Map<String,Object> row;
            if(action.equals("SUPPLEMENT")){
                var found=j.queryForList("SELECT meeting_id FROM feedback WHERE id=?",String.class,id);if(found.size()!=1)throw new MeetingAccess.AccessDeniedException();
                var gate=access.lock(actor,found.getFirst());access.require(gate,MeetingAccess.Action.READ);row=locked(id,true);
                if(!actor.enterpriseId().equals(row.get("submitted_by"))||!found.getFirst().equals(row.get("meetingId")))throw new MeetingAccess.AccessDeniedException();
            }else{active(actor);requireAdmin(actor);row=locked(id,true);}
            if(receipt.get("result_id") instanceof String history){
                if(((Number)receipt.get("http_status")).intValue()!=200)throw new IllegalStateException("Feedback receipt unavailable");
                return receipt(history,id,actor.enterpriseId(),action);
            }
            if(((Number)receipt.get("http_status")).intValue()!=0)throw new IllegalStateException("Feedback receipt unavailable");
            long revision=Long.parseLong((String)row.get("revisionToken"));String state=(String)row.get("state");
            if(j.queryForObject("SELECT COUNT(*) FROM corpus_feedback_binding WHERE feedback_id=? AND withdrawn=TRUE",Integer.class,id)>0)throw new ama.contract.MeetingContracts.Conflict();
            if(expected!=revision||revision>=MAX_HISTORY||j.queryForObject("SELECT COUNT(*) FROM feedback_history WHERE feedback_id=?",Integer.class,id)>=MAX_HISTORY)throw new ama.contract.MeetingContracts.Conflict();
            String next=action.equals("SUPPLEMENT")?(state.equals("NEEDS_MORE")?"PENDING":state):action.equals("UNDO_REVIEW")?"PENDING":decision;
            if(action.equals("REVIEW")&&!state.equals("PENDING")||action.equals("UNDO_REVIEW")&&state.equals("PENDING"))throw new ama.contract.MeetingContracts.Conflict();
            if(action.equals("REVIEW"))j.update("UPDATE feedback SET state=?,revision=revision+1,review_reason=?,reviewed_by=?,reviewed_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=?",next,text,actor.enterpriseId(),id,expected);
            else if(action.equals("UNDO_REVIEW")||state.equals("NEEDS_MORE"))j.update("UPDATE feedback SET state=?,revision=revision+1,review_reason=NULL,reviewed_by=NULL,reviewed_at=NULL WHERE id=? AND revision=?",next,id,expected);
            else j.update("UPDATE feedback SET revision=revision+1 WHERE id=? AND revision=?",id,expected);
            String history=UUID.randomUUID().toString();j.update("INSERT INTO feedback_history(id,feedback_id,actor_id,action,revision_before,revision_after,state_after,text,created_at) VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",history,id,actor.enterpriseId(),action,revision,revision+1,next,text);
            if(j.update("UPDATE idempotency_record SET result_id=?,http_status=200 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",history,scope)!=1)throw new IllegalStateException("Feedback receipt unavailable");
            new ama.audit.JdbcAuditAdapter(j.getDataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),action.equals("SUPPLEMENT")?"FEEDBACK_SUPPLEMENTED":action.equals("REVIEW")?"FEEDBACK_REVIEWED":"FEEDBACK_REVIEW_UNDONE",id,revision+1,"SUCCEEDED",UUID.randomUUID().toString(),java.time.Instant.now()));
            return receipt(history,id,actor.enterpriseId(),action);
        });
    }
    private Map<String,Object> receipt(String history,String id,String actor,String action){
        var rows=j.queryForList("SELECT feedback_id feedbackId,id historyId,CAST(revision_after AS CHAR) revisionToken,state_after state,action operation FROM feedback_history WHERE id=? AND feedback_id=? AND actor_id=? AND action=?",history,id,actor,action);
        if(rows.size()!=1)throw new IllegalStateException("Feedback receipt unavailable");return rows.getFirst();
    }
    private Map<String,Object> locked(String id,boolean write){
        var rows=j.queryForList("SELECT "+FIELDS+",submitted_by,review_reason,reviewed_by,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') submittedAt FROM feedback WHERE id=? "+(write?"FOR UPDATE":"FOR SHARE"),id);
        if(rows.size()!=1)throw new MeetingAccess.AccessDeniedException();return rows.getFirst();
    }
    private static Map<String,Object> view(Map<String,Object> row){var result=new LinkedHashMap<String,Object>();for(String name:List.of("id","meetingId","category","comment","state","revisionToken"))result.put(name,row.get(name));return result;}
    private void active(Actor actor){access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);}
    private boolean admin(Actor actor){return !j.queryForList("SELECT role FROM role_assignment WHERE enterprise_id=? AND role='PLATFORM_ADMIN' FOR SHARE",String.class,actor.enterpriseId()).isEmpty();}
    private void requireAdmin(Actor actor){if(!admin(actor))throw new MeetingAccess.AccessDeniedException();}
    private static void bounded(String text,int maximum){if(text==null||text.isBlank()||text.length()>maximum||text.indexOf(0)>=0)throw new IllegalArgumentException("Bounded feedback text required");}
}
