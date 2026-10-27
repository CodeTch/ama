package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TaskContracts.SubjectRef;
import ama.db.TransactionRunner;
import ama.task.SubjectFencePort;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Identity -> subject -> meeting. Never reads legacy meeting_acl or treats roles as object access. */
public final class JdbcMeetingAccess implements MeetingAccess {
    private final DataSource source;
    private final JdbcTemplate j;
    private final TransactionRunner tx;
    private final SubjectFencePort fences;
    // No transaction synchronization: B05 owns the sole final-flush synchronization.
    // Weak keys track actual Spring transaction holders without retaining completed transactions.
    private final Map<Object,Boolean> identitiesStarted=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object,Boolean> rootsStarted=Collections.synchronizedMap(new WeakHashMap<>());
    record Person(String id,String name,String status) {}
    record Root(String id,String owner,long revision,long accessRevision,long cancelEpoch,String stage,
                String lifecycle,String governance,boolean published,String infoId,String rosterId) {}
    record IdentityStatus(String id,String status) {}
    abstract class IdentityCapture implements LockedIdentities {
        final Object transaction; final String actor; final Map<String,IdentityStatus> statuses;
        private IdentityCapture(String actor,Map<String,IdentityStatus> statuses){this.transaction=transaction();this.actor=actor;this.statuses=Map.copyOf(statuses);}
        final Set<String> ids(){return statuses.keySet();}
        final JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
    private final class AuthorizationIdentities extends IdentityCapture {
        private AuthorizationIdentities(String actor,Map<String,IdentityStatus> statuses){super(actor,statuses);}
    }
    final class Identities extends IdentityCapture {
        final Map<String,Person> people;
        private Identities(String actor,Map<String,Person> people,Map<String,IdentityStatus> statuses){super(actor,statuses);this.people=Map.copyOf(people);}
    }
    final class Held implements Locked {
        final IdentityCapture identities; final SubjectFencePort.LockedSubject subject; final Root root;
        private Held(IdentityCapture identities,SubjectFencePort.LockedSubject subject,Root root){this.identities=identities;this.subject=subject;this.root=root;}
        JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
    public JdbcMeetingAccess(DataSource source,TransactionRunner tx,SubjectFencePort fences){this.source=source;this.j=new JdbcTemplate(source);this.tx=tx;this.fences=fences;}
    @Override public void require(Actor actor,String id,Action action){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Use the prelocked access capability inside caller transactions");
        tx.required(()->{require(lock(actor,id),action);return null;});
    }
    /** Package-owned DB projection must transfer from the exact DataSource that issued its gate. */
    public void requireProjectionSource(DataSource expected){if(source!=expected)throw new IllegalArgumentException("Foreign projection DataSource");}
    /** Detached publication preparation may not parse or hash after this holder acquired a root. */
    public void requireBeforeRootAcquisition(){
        Object holder=org.springframework.transaction.support.TransactionSynchronizationManager.getResource(source);
        if(holder!=null&&rootsStarted.containsKey(holder))throw new IllegalStateException("Publication preparation cannot follow a root");
    }
    /** A separate source capture must never suspend a holder retaining identity or root locks. */
    public void requireBeforeIdentityAcquisition(){
        requireBeforeRootAcquisition();Object holder=TransactionSynchronizationManager.getResource(source);
        if(holder!=null&&identitiesStarted.containsKey(holder))throw new IllegalStateException("Publication preparation cannot follow identity locks");
    }
    @Override public Locked lock(Actor actor,String id){return lockPrepared(captureIdentities(actor,List.of(),false,false,false).orElseThrow(AccessDeniedException::new),id,false).orElseThrow(AccessDeniedException::new);}
    Identities lockIdentities(Actor actor,Collection<String> affected){
        return (Identities)captureIdentities(actor,affected,false,false,true).orElseThrow(AccessDeniedException::new);
    }
    @Override public Optional<LockedIdentities> tryLockIdentities(Actor actor,Collection<String> affected,boolean skip){
        return captureIdentities(actor,affected,skip,true,false);
    }
    /** Status-only complete historical identity union; only the current actor must remain ACTIVE. */
    public Optional<LockedIdentities> tryLockHistoricalIdentities(Actor actor,Collection<String> historical,boolean skip){
        var captured=captureIdentities(actor,historical,skip,false,false);
        if(captured.isPresent()&&!((IdentityCapture)captured.orElseThrow()).ids().containsAll(historical))throw new IllegalArgumentException("Historical identity no longer exists");
        return captured;
    }
    /** Internal operation facts only. These opaque classes implement neither Locked nor LockedIdentities. */
    public final class FactIdentities {
        private final Object holder;private final String actor;private final Map<String,IdentityStatus> statuses;
        private FactIdentities(String actor,Map<String,IdentityStatus> statuses){holder=transaction();this.actor=actor;this.statuses=Map.copyOf(statuses);}
        private JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
    public final class FactRoot {
        private final FactIdentities identities;private final SubjectFencePort.LockedSubject subject;private final Root root;
        private FactRoot(FactIdentities identities,SubjectFencePort.LockedSubject subject,Root root){this.identities=identities;this.subject=subject;this.root=root;}
        private JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
    public Optional<FactIdentities> tryLockFactIdentities(Actor actor,Collection<String> historical,boolean skip){
        Object holder=transaction();identitiesStarted.put(holder,true);if(rootsStarted.containsKey(holder))throw new IllegalStateException("Fact identities cannot follow a root");Objects.requireNonNull(actor);Objects.requireNonNull(historical);
        var ids=new TreeSet<String>((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)));ids.add(actor.enterpriseId());ids.addAll(historical);var statuses=new HashMap<String,IdentityStatus>();
        for(String id:ids){var rows=j.query("SELECT enterprise_id,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),(row,n)->new IdentityStatus(row.getString(1),row.getString(2)),id);if(rows.isEmpty()){if(skip)return Optional.empty();throw new AccessDeniedException();}statuses.put(id,rows.getFirst());}
        return Optional.of(new FactIdentities(actor.enterpriseId(),statuses));
    }
    public boolean factActorActive(FactIdentities identities){checkFact(identities);return identities.statuses.get(identities.actor).status().equals("ACTIVE");}
    public Optional<FactRoot> lockFactRoot(FactIdentities identities,String meeting,boolean skip){
        checkFact(identities);rootsStarted.put(transaction(),true);var subject=fences.lock(new SubjectRef("MEETING",meeting),skip);if(subject.isEmpty())return Optional.empty();var row=roots(meeting,skip);if(row.isEmpty())return Optional.empty();var root=row.orElseThrow();var fence=subject.orElseThrow();if(root.revision()!=fence.businessVersion()||root.cancelEpoch()!=fence.cancelEpoch())throw new IllegalStateException("Meeting fact fence mismatch");return Optional.of(new FactRoot(identities,fence,root));
    }
    public boolean factAllowsBusiness(FactRoot value,long expectedEpoch,long originalVersion){checkFact(value);var root=value.root;return factActorActive(value.identities)&&root.owner().equals(value.identities.actor)&&root.lifecycle().equals("ACTIVE")&&root.governance().equals("ACTIVE")&&!root.published()&&!value.subject.deleted()&&root.cancelEpoch()==expectedEpoch&&root.revision()>=originalVersion;}
    /** Converts only an actually authorized current root; the denied fact capability is never a source grant. */
    public Locked authorizeFactRoot(FactRoot value,long expectedEpoch,long originalVersion){checkFact(value);if(!factAllowsBusiness(value,expectedEpoch,originalVersion))throw new AccessDeniedException();var ids=new AuthorizationIdentities(value.identities.actor,value.identities.statuses);var held=new Held(ids,value.subject,value.root);require(held,Action.EDIT);return held;}
    private void checkFact(FactIdentities value){if(value==null||value.issuer()!=this||value.holder!=transaction())throw new IllegalArgumentException("Foreign or expired fact identities");}
    private void checkFact(FactRoot value){if(value==null||value.issuer()!=this)throw new IllegalArgumentException("Foreign fact root");checkFact(value.identities);}
    private Optional<LockedIdentities> captureIdentities(Actor actor,Collection<String> affected,boolean skip,boolean allActive,boolean names){
        Object currentTransaction=transaction();
        identitiesStarted.put(currentTransaction,true);
        if(rootsStarted.containsKey(currentTransaction))throw new IllegalStateException("Identity prelock cannot follow a meeting root lock");
        Objects.requireNonNull(actor);Objects.requireNonNull(affected);
        var ids=new TreeSet<String>((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)));
        ids.addAll(affected);ids.add(actor.enterpriseId());var people=new HashMap<String,Person>();var statuses=new HashMap<String,IdentityStatus>();
        for(String id:ids){
            if(names){
                var found=j.query("SELECT enterprise_id,display_name,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),
                        (r,n)->new Person(r.getString(1),r.getString(2),r.getString(3)),id);
                if(found.isEmpty()){if(skip)return Optional.empty();continue;}
                var person=found.getFirst();people.put(id,person);statuses.put(id,new IdentityStatus(person.id(),person.status()));
            } else {
                var found=j.query("SELECT enterprise_id,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),
                        (r,n)->new IdentityStatus(r.getString(1),r.getString(2)),id);
                if(found.isEmpty()){if(skip)return Optional.empty();continue;}statuses.put(id,found.getFirst());
            }
        }
        var current=statuses.get(actor.enterpriseId());
        if(current==null||!current.status().equals("ACTIVE")
                ||allActive&&(statuses.size()!=ids.size()||statuses.values().stream().anyMatch(p->!p.status().equals("ACTIVE")))){
            if(skip)return Optional.empty();throw new AccessDeniedException();
        }
        return Optional.of(names?new Identities(actor.enterpriseId(),people,statuses):new AuthorizationIdentities(actor.enterpriseId(),statuses));
    }
    Held lockMeeting(Identities identities,String id){
        return (Held)lockPrepared(identities,id,false).orElseThrow(AccessDeniedException::new);
    }
    @Override public Optional<Locked> lockPrepared(LockedIdentities prepared,String id,boolean skip){
        if(!(prepared instanceof JdbcMeetingAccess.IdentityCapture identities)||identities.issuer()!=this)
            throw new IllegalArgumentException("Foreign identity capability");
        check(identities);
        rootsStarted.put(transaction(),true);
        var subject=fences.lock(new SubjectRef("MEETING",id),skip);
        if(subject.isEmpty()){if(skip)return Optional.empty();throw new AccessDeniedException();}
        var roots=roots(id,skip);
        if(roots.isEmpty()){if(skip)return Optional.empty();throw new AccessDeniedException();}
        Root root=roots.orElseThrow();var fence=subject.orElseThrow();
        if(fence.deleted())throw new AccessDeniedException();
        if(root.revision()!=fence.businessVersion()||root.cancelEpoch()!=fence.cancelEpoch())throw new IllegalStateException("Meeting fence mismatch");
        return Optional.of(new Held(identities,fence,root));
    }
    @Override public void requirePrepared(Locked locked,Actor actor,String meetingId,Collection<String> affected,Action action){
        if(!(locked instanceof JdbcMeetingAccess.Held held)||held.issuer()!=this)throw new IllegalArgumentException("Foreign meeting capability");
        check(held.identities);
        if(!held.root.id().equals(meetingId)||!held.identities.actor.equals(actor.enterpriseId())
                ||!held.identities.ids().containsAll(affected))throw new IllegalArgumentException("Meeting capability coverage mismatch");
        require(held,action);
    }
    @Override public void require(Locked locked,Action action){
        if(!(locked instanceof JdbcMeetingAccess.Held held)||held.issuer()!=this)throw new IllegalArgumentException("Foreign meeting capability");
        check(held.identities);Objects.requireNonNull(action);
        // Re-read the already-held root so mutations by the same caller transaction cannot
        // reuse a cached governance/pointer decision. No new root or identity lock is discovered.
        var root=root(held.root.id());
        if(!"ACTIVE".equals(j.queryForObject("SELECT status FROM enterprise_identity WHERE enterprise_id=?",String.class,held.identities.actor)))throw new AccessDeniedException();
        if(!root.lifecycle().equals("ACTIVE") || !root.governance().equals("ACTIVE"))throw new AccessDeniedException();
        if(root.owner().equals(held.identities.actor))return;
        if(action!=Action.READ || root.rosterId()==null)throw new AccessDeniedException();
        // Validate every provenance edge independently: pointers/FKs alone do not establish membership.
        var grants=j.queryForList("SELECT roster_id FROM meeting_access_grant WHERE meeting_id=? AND enterprise_id=? AND action='READ' FOR UPDATE",String.class,root.id(),held.identities.actor);
        if(grants.size()!=1||!root.rosterId().equals(grants.getFirst()))throw new AccessDeniedException();
        var rosters=j.queryForList("SELECT id FROM roster_revision WHERE id=? AND meeting_id=? AND state='CONFIRMED' FOR UPDATE",String.class,root.rosterId(),root.id());
        if(rosters.size()!=1)throw new AccessDeniedException();
        var members=j.queryForList("SELECT member_id FROM roster_member WHERE roster_id=? AND enterprise_id=? AND member_kind='ENTERPRISE' FOR UPDATE",String.class,root.rosterId(),held.identities.actor);
        if(members.size()!=1)throw new AccessDeniedException();
    }
    /** Detached metadata only; possession of this record is never an authorization capability. */
    public record WorkspaceStatus(String id,String revisionToken,String accessRevisionToken,String stage,String lifecycle,String governance,boolean owner,boolean published,String infoId,String rosterId) {}
    public WorkspaceStatus workspaceStatus(Locked locked,Actor actor,String meeting) {
        requirePrepared(locked,actor,meeting,List.of(actor.enterpriseId()),Action.READ);
        var value=((Held)locked).root;
        return new WorkspaceStatus(value.id(),Long.toString(value.revision()),Long.toString(value.accessRevision()),value.stage(),value.lifecycle(),value.governance(),value.owner().equals(actor.enterpriseId()),value.published(),value.infoId(),value.rosterId());
    }
    /** Joined lists require confirmed enterprise participation even when actor is also owner. */
    boolean confirmedParticipant(Locked locked,Actor actor,String meetingId) {
        requirePrepared(locked,actor,meetingId,List.of(),Action.READ);
        var held=(Held)locked;var root=held.root;if(root.rosterId()==null)return false;
        var grants=j.queryForList("SELECT roster_id FROM meeting_access_grant WHERE meeting_id=? AND enterprise_id=? AND action='READ' FOR UPDATE",String.class,meetingId,actor.enterpriseId());
        if(grants.size()!=1||!root.rosterId().equals(grants.getFirst()))return false;
        var parents=j.queryForList("SELECT id FROM roster_revision WHERE id=? AND meeting_id=? AND state='CONFIRMED' FOR UPDATE",String.class,root.rosterId(),meetingId);
        if(parents.size()!=1)return false;
        return j.queryForList("SELECT member_id FROM roster_member WHERE roster_id=? AND enterprise_id=? AND member_kind='ENTERPRISE' FOR UPDATE",String.class,root.rosterId(),actor.enterpriseId()).size()==1;
    }
    private Root root(String id){
        return roots(id,false).orElseThrow(AccessDeniedException::new);
    }
    private Optional<Root> roots(String id,boolean skip){
        return j.query("SELECT id,owner_id,revision,access_revision,cancel_epoch,stage,lifecycle,governance_state,ever_published,info_revision_id,roster_revision_id FROM meeting WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),(r,n)->new Root(r.getString("id"),r.getString("owner_id"),r.getLong("revision"),r.getLong("access_revision"),r.getLong("cancel_epoch"),r.getString("stage"),r.getString("lifecycle"),r.getString("governance_state"),r.getBoolean("ever_published"),r.getString("info_revision_id"),r.getString("roster_revision_id")),id).stream().findFirst();
    }
    private void check(IdentityCapture identities){if(identities.issuer()!=this||identities.transaction!=transaction())throw new IllegalArgumentException("Expired or foreign identity capability");}
    private Object transaction(){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Meeting access requires its DataSource transaction");
        return TransactionSynchronizationManager.getResource(source);
    }
}
