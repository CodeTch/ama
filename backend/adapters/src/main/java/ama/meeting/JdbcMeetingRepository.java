package ama.meeting;

import ama.access.MeetingAccess.Action;
import ama.access.MeetingAccess.AccessDeniedException;
import ama.audit.AuditPort;
import ama.contract.AuditContracts.AuditEvent;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.*;
import ama.contract.OutboxContracts.*;
import ama.contract.TaskContracts.SubjectRef;
import ama.db.TransactionRunner;
import ama.task.*;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One owner for aggregate writes. All SQL uses the same DataSource as identity/audit/fences/outbox. */
public final class JdbcMeetingRepository implements MeetingRepository {
    private final JdbcTemplate j;
    private final DataSource source;
    private final WorkspaceReadBudget.Limits readLimits;
    private final TransactionRunner tx;
    private final JdbcMeetingAccess access;
    private final SubjectFencePort fences;
    private final OutboxPort outbox;
    private final AuditPort audit;
    private final CategoryDictionary categories;
    private final JdbcMeetingReadProjection readProjection;
    private final ama.transcript.JdbcTranscriptAuthority minutes;
    private record Replay(String scope,String result,int status,boolean matches) {}
    private record Roster(String id,long revision,String state) {}
    public JdbcMeetingRepository(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,SubjectFencePort fences,
                                 OutboxPort outbox,AuditPort audit,CategoryDictionary categories){
        this(source,tx,access,fences,outbox,audit,categories,null);
    }
    public JdbcMeetingRepository(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,SubjectFencePort fences,OutboxPort outbox,AuditPort audit,CategoryDictionary categories,ama.transcript.JdbcTranscriptAuthority minutes){this(source,tx,access,fences,outbox,audit,categories,minutes,WorkspaceReadBudget.DEFAULT);}
    public JdbcMeetingRepository(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,SubjectFencePort fences,OutboxPort outbox,AuditPort audit,CategoryDictionary categories,ama.transcript.JdbcTranscriptAuthority minutes,WorkspaceReadBudget.Limits limits){
        this.source=Objects.requireNonNull(source);this.readLimits=Objects.requireNonNull(limits);j=new JdbcTemplate(source);j.setQueryTimeout(5);readProjection=new JdbcMeetingReadProjection(source,tx,access,limits);this.tx=tx;this.access=access;this.fences=fences;this.outbox=outbox;this.audit=audit;this.categories=categories;this.minutes=minutes;
    }
    @Override public Created create(Actor actor,String key,String trace){
        independent();
        return tx.required(()->{
            var replay=claim(actor,"meetings","CREATE",key,MeetingDigests.fields("meeting-create-v1"));
            // Creation only needs the current actor status; it never snapshots a directory name.
            var identities=access.tryLockHistoricalIdentities(actor,List.of(),false).orElseThrow(AccessDeniedException::new);
            if(replay.result()!=null){
                var result=restore(replay,"meeting_info_revision");
                access.require(access.lockPrepared(identities,result.id(),false).orElseThrow(AccessDeniedException::new),Action.EDIT);
                checkReplay(replay);
                return new Created(result.id(),result.revision(),"ENTRY");
            }
            checkReplay(replay);
            String id=uuid(),info=uuid();var subject=new SubjectRef("MEETING",id);
            fences.create(subject,0,0);
            j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,actor.enterpriseId());
            // Only genuine creation proves untouched state; replay and legacy reads never initialize it.
            // The required schema insert shares every creation write, audit and final outbox flush.
            j.update("INSERT INTO meeting_minutes_editor_guard(meeting_id,intent_epoch) VALUES (?,0)",id);
            insertInfo(info,id,0,null,null,null,null,actor.enterpriseId());
            insertRoster(id,0,"DRAFT",List.of(),null);
            j.update("UPDATE meeting SET info_revision_id=? WHERE id=?",info,id);
            j.update("INSERT INTO meeting_search_projection(meeting_id,info_revision_id,number_search,meeting_revision) VALUES (?,?,?,0)",id,info,id);
            complete(replay,info,201);
            finish(actor,id,0,"MEETING_CREATED","MEETING_INFO",info,trace);
            return new Created(id,0,"ENTRY");
        });
    }
    /** Returning a stored ACK still requires current authority, but never constructs a new roster snapshot. */
    private void requireReplayAccess(Actor actor,String meeting,Action action){
        var identities=access.tryLockHistoricalIdentities(actor,List.of(),false).orElseThrow(AccessDeniedException::new);
        var held=access.lockPrepared(identities,meeting,false).orElseThrow(AccessDeniedException::new);
        access.require(held,action);
    }
    @Override public Mutation save(Actor actor,String id,String key,SaveInfo request,String trace){
        independent();
        // Discovery owns no locks; CAS under root rejects any intervening aggregate change.
        var affected=affected(id);affected.addAll(request.rosterDraftIds());
        var prepared=prepareMinutes(actor,id);
        return tx.required(()->{
            var replay=claim(actor,id,"SAVE_INFO",key,MeetingDigests.save(request));
            if(replay.result()!=null){requireReplayAccess(actor,id,Action.EDIT);checkReplay(replay);return restore(replay,"meeting_info_revision");}
            var budget=new WorkspaceReadBudget(readLimits,()->Thread.currentThread().isInterrupted());
            var identities=access.lockIdentities(actor,affected,budget);
            var held=access.lockMeeting(identities,id);access.require(held,Action.EDIT);
            checkReplay(replay);
            writable(held,request.expectedRevision());
            requireDiscoveredIdentities(held,budget);
            if(request.category()!=null)categories.requireAvailableCategory(request.category());
            var desired=new ArrayList<Member>();
            for(String personId:request.rosterDraftIds()){
                var person=identities.people.get(personId);
                if(person==null)throw new Conflict();
                desired.add(new Member(uuid(),personId,person.name(),"ENTERPRISE"));
            }
            for(String name:request.externalRosterNames())desired.add(new Member(uuid(),null,name,"EXTERNAL"));
            if(minutes==null)throw new ama.contract.TranscriptContracts.Failure(503,"MINUTES_TRANSITION_UNAVAILABLE");
            if(prepared.failure()!=null)throw prepared.failure();
            var transition=minutes.lockMinutesTransition(prepared.value(),actor,id,held);
            long version=held.root.revision()+1;String info=uuid();
            insertInfo(info,id,version,request.name(),request.category(),request.summary(),request.occurredOn(),actor.enterpriseId());
            var latest=latest(id);
            if(latest==null || !sameRoster(members(held,actor,latest.id(),budget),desired))insertRoster(id,version,"DRAFT",desired,null);
            j.update("UPDATE meeting SET revision=?,info_revision_id=?,actual_meeting_date=?,final_confirmation_id=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=?",version,info,request.occurredOn(),id);
            j.update("UPDATE meeting_search_projection SET info_revision_id=?,name_search=?,category_id=?,meeting_revision=? WHERE meeting_id=?",info,request.name(),request.category(),version,id);
            fences.advance(held.subject,version,held.root.cancelEpoch(),false);
            minutes.finishMinutesTransition(transition,version);
            complete(replay,info,200);
            finish(actor,id,version,"MEETING_INFO_SAVED","MEETING_INFO",info,trace);
            return new Mutation(id,version);
        });
    }
    @Override public Mutation confirm(Actor actor,String id,String key,ConfirmRoster request,String trace){
        independent();var affected=affected(id);
        var prepared=prepareMinutes(actor,id);
        return tx.required(()->{
            var replay=claim(actor,id,"CONFIRM_ROSTER",key,MeetingDigests.fields("meeting-roster-confirm-v1",request.expectedRevision().toString()));
            if(replay.result()!=null){requireReplayAccess(actor,id,Action.MANAGE);checkReplay(replay);return restore(replay,"roster_revision");}
            var budget=new WorkspaceReadBudget(readLimits,()->Thread.currentThread().isInterrupted());
            var identities=access.lockIdentities(actor,affected,budget);
            var held=access.lockMeeting(identities,id);access.require(held,Action.MANAGE);
            checkReplay(replay);
            writable(held,request.expectedRevision());
            if(held.root.accessRevision()==Long.MAX_VALUE-1)throw new RevisionExhausted();
            requireDiscoveredIdentities(held,budget);
            var draft=latest(id);if(draft==null || !draft.state().equals("DRAFT"))throw new Conflict();
            var people=members(held,actor,draft.id(),budget);
            for(var member:people)if(member.enterpriseId()!=null){
                var current=identities.people.get(member.enterpriseId());
                // A discovery race cannot acquire a new lower-order identity lock here.
                if(current==null||!current.status().equals("ACTIVE"))throw new Conflict();
            }
            if(minutes==null)throw new ama.contract.TranscriptContracts.Failure(503,"MINUTES_TRANSITION_UNAVAILABLE");
            if(prepared.failure()!=null)throw prepared.failure();
            var transition=minutes.lockMinutesTransition(prepared.value(),actor,id,held);
            long version=held.root.revision()+1;
            String confirmed=insertRoster(id,version,"CONFIRMED",people,actor.enterpriseId());
            // Full replacement is an atomic diff under the single root; removed grants cannot survive.
            j.update("DELETE FROM meeting_access_grant WHERE meeting_id=?",id);
            for(var person:people)if(person.enterpriseId()!=null)j.update("INSERT INTO meeting_access_grant(meeting_id,enterprise_id,action,roster_id,confirmed_at) VALUES (?,?,'READ',?,UTC_TIMESTAMP(6))",id,person.enterpriseId(),confirmed);
            j.update("UPDATE meeting SET revision=?,access_revision=access_revision+1,roster_revision_id=?,final_confirmation_id=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=?",version,confirmed,id);
            j.update("UPDATE meeting_search_projection SET roster_revision_id=?,meeting_revision=? WHERE meeting_id=?",confirmed,version,id);
            j.update("DELETE FROM meeting_person_search WHERE meeting_id=?",id);
            j.update("INSERT INTO meeting_person_search(meeting_id,roster_id,member_id,name_search) SELECT ?,roster_id,member_id,display_name FROM roster_member WHERE roster_id=?",id,confirmed);
            fences.advance(held.subject,version,held.root.cancelEpoch(),false);
            minutes.finishMinutesTransition(transition,version);
            complete(replay,confirmed,200);
            finish(actor,id,version,"MEETING_ROSTER_CONFIRMED","MEETING_ROSTER",confirmed,trace);
            return new Mutation(id,version);
        });
    }
    @Override public Saved read(Actor actor,String id){return readProjection.saved(actor,id);}
    private record Pending(ama.transcript.JdbcTranscriptAuthority.PreparedMinutesTransition value,RuntimeException failure) {}
    private Pending prepareMinutes(Actor actor,String id){try{return new Pending(minutes==null?null:minutes.prepareMinutesTransition(actor,id),null);}catch(RuntimeException unavailable){return new Pending(null,unavailable);}}
    private Replay claim(Actor actor,String resource,String operation,String key,String requestHash){
        String scope=MeetingDigests.fields(actor.enterpriseId(),resource,operation,key),keyHash=MeetingDigests.fields(key);
        j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,?,?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",scope,actor.enterpriseId(),resource,operation,keyHash,requestHash);
        var row=j.queryForMap("SELECT * FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);
        boolean matches=actor.enterpriseId().equals(row.get("actor_id"))&&resource.equals(row.get("resource_id"))&&operation.equals(row.get("operation"))&&keyHash.equals(row.get("key_hash"))&&requestHash.equals(row.get("request_hash"));
        return new Replay(scope,(String)row.get("result_id"),((Number)row.get("http_status")).intValue(),matches);
    }
    private void checkReplay(Replay replay){if(!replay.matches())throw new Conflict();}
    private Mutation restore(Replay replay,String table){
        var rows=j.query("SELECT meeting_id,revision FROM "+table+" WHERE id=?",(r,n)->new Mutation(r.getString(1),r.getLong(2)),replay.result());
        if(rows.size()!=1 || (replay.status()!=200&&replay.status()!=201))throw new IllegalStateException("Invalid idempotency result");return rows.getFirst();
    }
    private void complete(Replay replay,String reference,int status){j.update("UPDATE idempotency_record SET result_id=?,http_status=? WHERE scope_hash=?",reference,status,replay.scope());}
    private void finish(Actor actor,String id,long revision,String type,String kind,String reference,String trace){
        audit.append(new AuditEvent(actor.enterpriseId(),type,id,revision,"SUCCEEDED",trace,Instant.now()));
        outbox.append(new EventDraft(type,null,new SubjectRef("MEETING",id),revision,new Reference(kind,reference)));
        outbox.flush(); // Final operation: nothing may lock/write after this line.
    }
    private void writable(JdbcMeetingAccess.Held held,long expected){
        if(held.root.revision()!=expected || held.root.published() || held.root.stage().equals("PUBLISHED"))throw new Conflict();
        if(expected==Long.MAX_VALUE-1)throw new RevisionExhausted();
    }
    private Set<String> affected(String id){
        var ids=new HashSet<>(j.queryForList("SELECT enterprise_id FROM meeting_access_grant WHERE meeting_id=?",String.class,id));
        ids.addAll(j.queryForList("SELECT m.enterprise_id FROM roster_member m WHERE m.roster_id=(SELECT r.id FROM roster_revision r WHERE r.meeting_id=? ORDER BY r.revision DESC LIMIT 1) AND m.enterprise_id IS NOT NULL",String.class,id));
        return ids;
    }
    private void requireDiscoveredIdentities(JdbcMeetingAccess.Held held,WorkspaceReadBudget budget){
        var ids=new HashSet<>(j.queryForList("SELECT enterprise_id FROM meeting_access_grant WHERE meeting_id=? ORDER BY enterprise_id FOR UPDATE",String.class,held.root.id()));
        var latest=latest(held.root.id());
        if(latest!=null){
            // This prelock validation only needs fixed identity IDs; never transfer roster LONGTEXT here.
            budget.reserve(1,1,64,64,64,4,false);
            long count=j.queryForObject("SELECT COUNT(*) FROM roster_member WHERE roster_id=? AND enterprise_id IS NOT NULL",Long.class,latest.id());
            if(count<0||count>readLimits.rows())throw new WorkspaceReadBudget.Unavailable();
            reserveRosterIdentityIds(budget,count);
            var found=j.queryForList("SELECT enterprise_id FROM roster_member WHERE roster_id=? AND enterprise_id IS NOT NULL ORDER BY member_id FOR UPDATE",String.class,latest.id());
            if(found.size()!=count)throw new WorkspaceReadBudget.Unavailable();ids.addAll(found);
        }
        // Even a caller guessing a future CAS cannot make discovery omit a newly removed identity.
        // Refuse and roll back; never discover and lock a lower-order identity after the root.
        if(!held.identities.ids().containsAll(ids))throw new Conflict();
    }
    static void reserveRosterIdentityIds(WorkspaceReadBudget budget,long count){
        // VARCHAR(255) admits 255 Unicode scalars: up to four UTF-8 bytes and two UTF-16 units each.
        budget.reserve(count,count,Math.multiplyExact(count,1020L),Math.multiplyExact(count,255L),Math.multiplyExact(count,510L),4,false);
    }
    private Roster latest(String id){return j.query("SELECT id,revision,state FROM roster_revision WHERE meeting_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",(r,n)->new Roster(r.getString(1),r.getLong(2),r.getString(3)),id).stream().findFirst().orElse(null);}
    private List<Member> members(JdbcMeetingAccess.Held held,Actor actor,String roster,WorkspaceReadBudget budget){
        var read=new BoundedMeetingMetadata(source,access,actor,held.root.id(),held,budget);
        if(read.capture("roster_revision","id","id=? AND meeting_id=?",1,roster,held.root.id()).size()!=1)throw new WorkspaceReadBudget.Unavailable();
        var selected=read.capture("roster_member","member_id enterprise_id display_name member_kind","roster_id=?",128,roster);
        return read.read(selected).stream().map(row->new Member((String)row.get("member_id"),(String)row.get("enterprise_id"),(String)row.get("display_name"),(String)row.get("member_kind"))).toList();
    }
    private boolean sameRoster(List<Member> before,List<Member> after){
        return signatures(before).equals(signatures(after));
    }
    private Map<List<String>,Long> signatures(List<Member> members){return members.stream().collect(java.util.stream.Collectors.groupingBy(m->Arrays.asList(m.enterpriseId(),m.displayName(),m.memberKind()),java.util.stream.Collectors.counting()));}
    private String insertRoster(String meeting,long version,String state,List<Member> people,String actor){
        String id=uuid();
        j.update("INSERT INTO roster_revision(id,meeting_id,revision,state,confirmed_at,confirmed_by) VALUES (?,?,?,?,IF(? IS NULL,NULL,UTC_TIMESTAMP(6)),?)",id,meeting,version,state,actor,actor);
        for(var person:people)j.update("INSERT INTO roster_member(roster_id,member_id,enterprise_id,display_name,member_kind) VALUES (?,?,?,?,?)",id,uuid(),person.enterpriseId(),person.displayName(),person.memberKind());
        return id;
    }
    private void insertInfo(String info,String id,long version,String name,String category,String summary,LocalDate date,String actor){
        j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,category_id,summary,actual_meeting_date,created_by,created_at) VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",info,id,version,name,category,summary,date,actor);
    }
    private static String uuid(){return UUID.randomUUID().toString();}
    private static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Meeting commands own their transaction; compose downstream operations with prelocked access instead");}
}
