package ama.meeting;

import ama.access.MeetingAccess;
import ama.audit.JdbcAuditAdapter;
import ama.contract.AuditContracts.AuditEvent;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.Conflict;
import ama.db.TransactionRunner;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Owner-scoped personal topics and same-owner meeting grouping. */
public final class JdbcTopics {
    private final JdbcTemplate j; private final TransactionRunner tx; private final JdbcMeetingAccess access; private final JdbcAuditAdapter audit;
    public JdbcTopics(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){j=new JdbcTemplate(source);this.tx=tx;this.access=access;audit=new JdbcAuditAdapter(source);}
    public List<Map<String,Object>> list(Actor actor){return tx.required(()->{active(actor);return j.queryForList("SELECT id,name,description,revision,CAST(revision AS CHAR) revisionToken FROM topic WHERE owner_id=? ORDER BY updated_at DESC,id DESC",actor.enterpriseId());});}
    public Map<String,Object> create(Actor actor,String key,String name,String description){return tx.required(()->{active(actor);requireName(name);if(j.queryForObject("SELECT COUNT(*) FROM topic WHERE owner_id=? AND name=?",Long.class,actor.enterpriseId(),name)>0)throw new Conflict();String id=UUID.randomUUID().toString();j.update("INSERT INTO topic(id,owner_id,name,description,revision,created_at,updated_at) VALUES (?,?,?, ?,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,actor.enterpriseId(),name,description);audit.append(new AuditEvent(actor.enterpriseId(),"TOPIC_CREATED",id,0,"SUCCEEDED",UUID.randomUUID().toString(),Instant.now()));return row(id);});}
    public Map<String,Object> update(Actor actor,String id,String key,long expected,String name,String description){return tx.required(()->{active(actor);requireName(name);var old=owned(actor,id);if(old.revision()!=expected)throw new Conflict();j.update("UPDATE topic SET name=?,description=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",name,description,id);return row(id);});}
    public Map<String,Object> delete(Actor actor,String id,String key,long expected){return tx.required(()->{active(actor);var old=owned(actor,id);if(old.revision()!=expected||j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE topic_id=?",Long.class,id)>0)throw new Conflict();j.update("DELETE FROM topic WHERE id=?",id);var result=new HashMap<String,Object>();result.put("id",id);result.put("name",old.name());result.put("description",old.description());result.put("revision",expected+1);result.put("revisionToken",Long.toString(expected+1));return result;});}
    public void assign(Actor actor,String meeting,String topic){tx.required(()->{var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);var t=owned(actor,topic);j.update("INSERT INTO meeting_topic(topic_id,meeting_id,added_at) VALUES (?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE topic_id=VALUES(topic_id)",topic,meeting);return null;});}
    public void unassign(Actor actor,String meeting,String topic){tx.required(()->{var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);owned(actor,topic);j.update("DELETE FROM meeting_topic WHERE topic_id=? AND meeting_id=?",topic,meeting);return null;});}
    private void active(Actor actor){if(j.queryForObject("SELECT COUNT(*) FROM enterprise_identity WHERE enterprise_id=? AND status='ACTIVE'",Long.class,actor.enterpriseId())!=1)throw new MeetingAccess.AccessDeniedException();}
    private Topic owned(Actor actor,String id){var rows=j.query("SELECT id,name,description,revision FROM topic WHERE id=? AND owner_id=? FOR UPDATE",(r,n)->new Topic(r.getString(1),r.getString(2),r.getString(3),r.getLong(4)),id,actor.enterpriseId());if(rows.size()!=1)throw new MeetingAccess.AccessDeniedException();return rows.getFirst();}
    private Map<String,Object> row(String id){return j.queryForMap("SELECT id,name,description,revision,CAST(revision AS CHAR) revisionToken FROM topic WHERE id=?",id);}
    private static void requireName(String name){if(name==null||name.isBlank()||name.length()>255)throw new IllegalArgumentException("Topic name required");}
    private record Topic(String id,String name,String description,long revision){}
}
