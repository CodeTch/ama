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
    private final Map<Object,Boolean> rootsStarted=Collections.synchronizedMap(new WeakHashMap<>());
    record Person(String id,String name,String status) {}
    record Root(String id,String owner,long revision,long accessRevision,long cancelEpoch,String stage,
                String lifecycle,String governance,boolean published,String infoId,String rosterId) {}
    final class Identities {
        final Object transaction; final String actor; final Map<String,Person> people;
        Identities(String actor,Map<String,Person> people){this.transaction=transaction();this.actor=actor;this.people=Map.copyOf(people);}
        JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
    final class Held implements Locked {
        final Identities identities; final SubjectFencePort.LockedSubject subject; final Root root;
        Held(Identities identities,SubjectFencePort.LockedSubject subject,Root root){this.identities=identities;this.subject=subject;this.root=root;}
        JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
    public JdbcMeetingAccess(DataSource source,TransactionRunner tx,SubjectFencePort fences){this.source=source;this.j=new JdbcTemplate(source);this.tx=tx;this.fences=fences;}
    @Override public void require(Actor actor,String id,Action action){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Use the prelocked access capability inside caller transactions");
        tx.required(()->{require(lock(actor,id),action);return null;});
    }
    @Override public Locked lock(Actor actor,String id){return lockMeeting(lockIdentities(actor,List.of()),id);}
    Identities lockIdentities(Actor actor,Collection<String> affected){
        Object currentTransaction=transaction();
        if(rootsStarted.containsKey(currentTransaction))throw new IllegalStateException("Identity prelock cannot follow a meeting root lock");
        Objects.requireNonNull(actor);
        var ids=new TreeSet<String>((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)));
        ids.addAll(affected);ids.add(actor.enterpriseId());var people=new HashMap<String,Person>();
        for(String id:ids){
            var found=j.query("SELECT enterprise_id,display_name,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE",
                    (r,n)->new Person(r.getString(1),r.getString(2),r.getString(3)),id);
            if(!found.isEmpty())people.put(id,found.getFirst());
        }
        var current=people.get(actor.enterpriseId());
        if(current==null || !current.status().equals("ACTIVE"))throw new AccessDeniedException();
        return new Identities(actor.enterpriseId(),people);
    }
    Held lockMeeting(Identities identities,String id){
        check(identities);
        rootsStarted.put(transaction(),true);
        var subject=fences.lock(new SubjectRef("MEETING",id),false).orElseThrow(AccessDeniedException::new);
        Root root=root(id);
        if(subject.deleted())throw new AccessDeniedException();
        if(root.revision()!=subject.businessVersion()||root.cancelEpoch()!=subject.cancelEpoch())throw new IllegalStateException("Meeting fence mismatch");
        return new Held(identities,subject,root);
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
    private Root root(String id){
        return j.query("SELECT * FROM meeting WHERE id=? FOR UPDATE",(r,n)->new Root(r.getString("id"),r.getString("owner_id"),r.getLong("revision"),r.getLong("access_revision"),r.getLong("cancel_epoch"),r.getString("stage"),r.getString("lifecycle"),r.getString("governance_state"),r.getBoolean("ever_published"),r.getString("info_revision_id"),r.getString("roster_revision_id")),id).stream().findFirst().orElseThrow(AccessDeniedException::new);
    }
    private void check(Identities identities){if(identities.issuer()!=this||identities.transaction!=transaction())throw new IllegalArgumentException("Expired or foreign identity capability");}
    private Object transaction(){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Meeting access requires its DataSource transaction");
        return TransactionSynchronizationManager.getResource(source);
    }
}
