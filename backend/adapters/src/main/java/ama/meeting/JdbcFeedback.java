package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.db.TransactionRunner;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcFeedback {
    private final JdbcTemplate j; private final TransactionRunner tx; private final JdbcMeetingAccess access;
    public JdbcFeedback(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){j=new JdbcTemplate(source);this.tx=tx;this.access=access;}
    public Map<String,Object> create(Actor actor,String key,String meeting,String category,String comment){return tx.required(()->{var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.READ);if(category==null||category.isBlank()||comment==null||comment.isBlank())throw new IllegalArgumentException("Feedback content required");String id=UUID.randomUUID().toString();j.update("INSERT INTO feedback(id,meeting_id,submitted_by,category,comment,state,revision,created_at) VALUES (?,?,?,?,?,'PENDING',0,UTC_TIMESTAMP(6))",id,meeting,actor.enterpriseId(),category,comment);return row(id);});}
    public Map<String,Object> list(Actor actor,String cursor){return tx.required(()->{var rows=j.queryForList("SELECT id,meeting_id meetingId,category,comment,state,CAST(revision AS CHAR) revisionToken FROM feedback WHERE submitted_by=? ORDER BY created_at DESC,id DESC LIMIT 20",actor.enterpriseId());return Map.of("items",rows,"nextCursor",null);});}
    private Map<String,Object> row(String id){return j.queryForMap("SELECT id,meeting_id meetingId,category,comment,state,CAST(revision AS CHAR) revisionToken FROM feedback WHERE id=?",id);}
}
