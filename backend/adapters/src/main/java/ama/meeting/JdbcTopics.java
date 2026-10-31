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
    private final JdbcTemplate j; private final TransactionRunner tx; private final JdbcMeetingAccess access; private final JdbcAuditAdapter audit;private final TopicCommandReceipts receipts;
    public JdbcTopics(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=tx;this.access=access;audit=new JdbcAuditAdapter(source);receipts=new TopicCommandReceipts(j);}
    public List<Map<String,Object>> list(Actor actor){return tx.required(()->{active(actor);var rows=j.queryForList("SELECT id,name,description,revision,CAST(revision AS CHAR) revisionToken FROM topic WHERE owner_id=? ORDER BY updated_at DESC,id DESC LIMIT 1001",actor.enterpriseId());if(rows.size()>1000)throw new IllegalStateException("Topic list exceeds budget");rows.forEach(JdbcTopics::normalizeRevision);return rows;});}
    public Map<String,Object> create(Actor actor,String key,String name,String description){
        input(name,description);return tx.required(()->{var claim=receipts.claim(actor,TopicCommandReceipts.WORKSPACE,"CREATE",key,MeetingDigests.fields("topic-create-v1",name,description));active(actor);if(claim.replay())return replay(claim,201);
            if(j.queryForObject("SELECT COUNT(*) FROM topic WHERE owner_id=?",Long.class,actor.enterpriseId())>=1000)throw new IllegalArgumentException("Topic limit reached");uniqueName(actor,null,name);String id=UUID.randomUUID().toString();
            j.update("INSERT INTO topic(id,owner_id,name,description,revision,created_at,updated_at) VALUES (?,?,?,?,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,actor.enterpriseId(),name,description);record(actor,"TOPIC_CREATED",id,0);var result=row(id);receipts.accept(claim,201,result);return result;});
    }
    public Map<String,Object> update(Actor actor,String id,String key,long expected,String name,String description){
        ama.contract.MinutesContracts.uuid(id);input(name,description);revision(expected);return tx.required(()->{var claim=receipts.claim(actor,id,"UPDATE",key,MeetingDigests.fields("topic-update-v1",id,Long.toString(expected),name,description));active(actor);if(claim.replay())return replay(claim,200);
            var old=owned(actor,id);if(old.revision()!=expected)throw new Conflict();uniqueName(actor,id,name);j.update("UPDATE topic SET name=?,description=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",name,description,id);record(actor,"TOPIC_UPDATED",id,expected+1);var result=row(id);receipts.accept(claim,200,result);return result;});
    }
    public Map<String,Object> delete(Actor actor,String id,String key,long expected){
        ama.contract.MinutesContracts.uuid(id);revision(expected);return tx.required(()->{var claim=receipts.claim(actor,id,"DELETE",key,MeetingDigests.fields("topic-delete-v1",id,Long.toString(expected)));active(actor);if(claim.replay())return replay(claim,200);
            var old=owned(actor,id);if(old.revision()!=expected||j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE topic_id=?",Long.class,id)>0)throw new Conflict();j.update("DELETE FROM topic WHERE id=?",id);var result=new LinkedHashMap<String,Object>();result.put("id",id);result.put("name",old.name());result.put("description",old.description());result.put("revision",expected+1);result.put("revisionToken",Long.toString(expected+1));record(actor,"TOPIC_DELETED",id,expected+1);receipts.accept(claim,200,result);return result;});
    }
    public record MembershipTarget(String meetingId,long expectedRevision){public MembershipTarget{ama.contract.MinutesContracts.uuid(meetingId);if(expectedRevision<0||expectedRevision==Long.MAX_VALUE)throw new IllegalArgumentException("Exact meeting revision required");}}
    public void assign(Actor actor,String meeting,String topic,String key,long expected){
        ama.contract.MinutesContracts.uuid(topic);var target=new MembershipTarget(meeting,expected);
        membershipCommand(actor,key,"ASSIGN",meeting,topic,List.of(target),false);
    }
    public void unassign(Actor actor,String meeting,String topic,String key,long expected){
        ama.contract.MinutesContracts.uuid(topic);var target=new MembershipTarget(meeting,expected);
        membershipCommand(actor,key,"UNASSIGN",meeting,topic,List.of(target),true);
    }
    public void assignMany(Actor actor,String key,String topic,List<MembershipTarget> targets){
        if(topic!=null)ama.contract.MinutesContracts.uuid(topic);targets=targets(targets);
        membershipCommand(actor,key,"BATCH_ASSIGN",TopicCommandReceipts.WORKSPACE,topic,targets,false);
    }
    private static List<MembershipTarget> targets(List<MembershipTarget> values){
        if(values==null||values.isEmpty()||values.size()>50||values.stream().anyMatch(Objects::isNull)||values.stream().map(MembershipTarget::meetingId).distinct().count()!=values.size())throw new IllegalArgumentException("One to fifty unique meetings required");return List.copyOf(values);
    }
    private void membershipCommand(Actor actor,String key,String operation,String resource,String topic,List<MembershipTarget> targets,boolean remove){
        var fields=new ArrayList<String>();Collections.addAll(fields,"topic-membership-command-v1",operation,topic,Integer.toString(targets.size()));for(var target:targets){fields.add(target.meetingId());fields.add(Long.toString(target.expectedRevision()));}String request=MeetingDigests.fields(fields.toArray(String[]::new));
        tx.required(()->{
            var claim=receipts.claim(actor,resource,operation,key,request);var roots=lockMeetings(actor,targets);
            if(claim.replay()){if(claim.status()!=204||claim.result()!=null)throw new IllegalStateException("Topic receipt unavailable");return null;}
            for(var target:targets)if(!roots.get(target.meetingId()).revisionToken().equals(Long.toString(target.expectedRevision())))throw new Conflict();
            if(topic!=null)owned(actor,topic);
            // Validate every existing edge before touching any meeting in the batch.
            var edges=new HashMap<String,List<String>>();for(var target:targets){var current=currentTopics(actor,target.meetingId());if(remove&&!current.isEmpty()&&!current.equals(List.of(topic)))throw new Conflict();edges.put(target.meetingId(),current);}
            for(var target:targets){
                j.update("DELETE FROM meeting_topic WHERE meeting_id=?",target.meetingId());
                if(!remove&&topic!=null)j.update("INSERT INTO meeting_topic(topic_id,meeting_id,added_at) VALUES (?,?,UTC_TIMESTAMP(6))",topic,target.meetingId());
                // Personal grouping changes list order, not content/task/ACL revisions.
                j.update("UPDATE meeting SET updated_at=UTC_TIMESTAMP(6) WHERE id=?",target.meetingId());
                record(actor,remove||topic==null?"TOPIC_UNASSIGNED":"TOPIC_ASSIGNED",target.meetingId(),target.expectedRevision());
            }
            receipts.accept(claim,204,null);return null;
        });
    }
    private Map<String,JdbcMeetingAccess.WorkspaceStatus> lockMeetings(Actor actor,List<MembershipTarget> targets){
        var identities=access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);var roots=new LinkedHashMap<String,JdbcMeetingAccess.WorkspaceStatus>();
        for(var target:targets.stream().sorted(Comparator.comparing(MembershipTarget::meetingId)).toList()){
            var gate=access.lockPrepared(identities,target.meetingId(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);access.require(gate,MeetingAccess.Action.EDIT);var root=access.workspaceStatus(gate,actor,target.meetingId());if(!root.owner())throw new MeetingAccess.AccessDeniedException();roots.put(target.meetingId(),root);
        }return roots;
    }
    public List<Map<String,Object>> memberships(Actor actor,List<String> meetings){
        if(meetings==null)throw new IllegalArgumentException("Meetings required");var targets=targets(meetings.stream().map(id->new MembershipTarget(id,0)).toList());
        return tx.required(()->{lockMeetings(actor,targets);var result=new ArrayList<Map<String,Object>>();for(String meeting:meetings){var current=currentTopics(actor,meeting);if(current.size()>1)throw new IllegalStateException("Meeting has multiple legacy topics");var row=new LinkedHashMap<String,Object>();row.put("meetingId",meeting);row.put("topicId",current.isEmpty()?null:current.getFirst());result.add(row);}return List.copyOf(result);});
    }
    /** Full current count, not the number of rows on the owner's current meeting page. */
    public Map<String,Object> summary(Actor actor,String id){
        ama.contract.MinutesContracts.uuid(id);return tx.required(()->{
            active(actor);var selected=owned(actor,id);
            var counts=j.queryForMap("SELECT COUNT(*) amount,COALESCE(SUM(CASE WHEN m.owner_id<>? THEN 1 ELSE 0 END),0) unowned_count FROM meeting_topic mt JOIN meeting m ON m.id=mt.meeting_id WHERE mt.topic_id=?",actor.enterpriseId(),id);
            if(((Number)counts.get("unowned_count")).longValue()!=0)throw new MeetingAccess.AccessDeniedException();long amount=((Number)counts.get("amount")).longValue();if(amount<0||amount>1000000000L)throw new IllegalStateException("Topic membership count unavailable");
            return Map.of("id",id,"revisionToken",Long.toString(selected.revision()),"meetingCount",amount);
        });
    }
    private List<String> currentTopics(Actor actor,String meeting){
        var rows=j.queryForList("SELECT m.topic_id,t.owner_id FROM meeting_topic m LEFT JOIN topic t ON t.id=m.topic_id WHERE m.meeting_id=? ORDER BY m.topic_id LIMIT 1001",meeting);
        if(rows.size()>1000||rows.stream().anyMatch(row->!actor.enterpriseId().equals(row.get("owner_id"))))throw new IllegalStateException("Topic membership provenance unavailable");return rows.stream().map(row->(String)row.get("topic_id")).toList();
    }
    private void active(Actor actor){access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);}
    private Topic owned(Actor actor,String id){var rows=j.query("SELECT id,name,description,revision FROM topic WHERE id=? AND owner_id=? FOR UPDATE",(r,n)->new Topic(r.getString(1),r.getString(2),r.getString(3),r.getLong(4)),id,actor.enterpriseId());if(rows.size()!=1)throw new MeetingAccess.AccessDeniedException();return rows.getFirst();}
    private Map<String,Object> row(String id){var result=j.queryForMap("SELECT id,name,description,revision,CAST(revision AS CHAR) revisionToken FROM topic WHERE id=?",id);normalizeRevision(result);return result;}
    private static void normalizeRevision(Map<String,Object> row){long revision=((Number)row.get("revision")).longValue();if(revision<0||revision==Long.MAX_VALUE||!Long.toString(revision).equals(row.get("revisionToken")))throw new IllegalStateException("Topic revision unavailable");row.put("revision",revision);}
    private static void requireName(String name){if(name==null||name.isBlank()||name.length()>255)throw new IllegalArgumentException("Topic name required");}
    private static void input(String name,String description){requireName(name);if(description!=null&&description.length()>4096)throw new IllegalArgumentException("Bounded topic description required");MeetingDigests.fields(name,description);}
    private static void revision(long value){if(value<0||value>=Long.MAX_VALUE-1)throw new IllegalArgumentException("Topic revision exhausted");}
    private void uniqueName(Actor actor,String id,String name){if(j.queryForObject("SELECT COUNT(*) FROM topic WHERE owner_id=? AND name=? AND (? IS NULL OR id<>?)",Long.class,actor.enterpriseId(),name,id,id)>0)throw new Conflict();}
    private Map<String,Object> replay(TopicCommandReceipts.Claim claim,int status){if(claim.status()!=status||claim.result()==null)throw new IllegalStateException("Topic receipt unavailable");return claim.result();}
    private void record(Actor actor,String action,String id,long revision){audit.append(new AuditEvent(actor.enterpriseId(),action,id,revision,"SUCCEEDED",UUID.randomUUID().toString(),Instant.now()));}
    private record Topic(String id,String name,String description,long revision){}
}
