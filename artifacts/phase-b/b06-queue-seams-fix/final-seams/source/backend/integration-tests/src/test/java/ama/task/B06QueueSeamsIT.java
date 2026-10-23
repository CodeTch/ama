package ama.task;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.ExternalRequest;
import ama.db.*;
import ama.meeting.JdbcMeetingAccess;
import ama.support.MysqlTestDatabase;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B06 prerequisite composition proof only. The deliberately narrow policy uses the current
 * lockRoot extension and actual B03 access. It is not a production meeting policy or runtime
 * producer: TEST_INFO points to a real info row, never a fabricated transcription manifest.
 */
class B06QueueSeamsIT {
    final DataSource actual=MysqlTestDatabase.dataSource();
    final List<String> trace=new CopyOnWriteArrayList<>();
    final AtomicReference<Thread> completer=new AtomicReference<>();
    volatile CountDownLatch completionAtIdentity;
    final DataSource source=new ProbeSource(actual,sql->{
        if(sql.contains("FOR UPDATE"))trace.add(Thread.currentThread().getName()+" "+sql);
        if(Thread.currentThread()==completer.get() && sql.contains("FROM enterprise_identity")
                && sql.contains("FOR UPDATE") && completionAtIdentity!=null)completionAtIdentity.countDown();
    });
    final JdbcTemplate j=new JdbcTemplate(source);
    final TransactionRunner tx=new TransactionRunner(source);
    final JdbcSubjectFences fences=new JdbcSubjectFences(source,Set.of("MEETING"));
    final JdbcMeetingAccess access=new JdbcMeetingAccess(source,tx,fences);
    final String owner="b06-seam-"+UUID.randomUUID();
    final Actor actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
    final String meeting=UUID.randomUUID().toString(),input=UUID.randomUUID().toString();
    final TaskSpec spec=new TaskSpec("B06_SEAM_TEST","B06_SEAM_"+UUID.randomUUID(),
            new SubjectRef("MEETING",meeting),TaskFixture.hash(input+"producer"),
            new TypedRef("TEST_INFO",input),TaskFixture.hash(input),0,0,0,3);
    final SeamPolicy policy=new SeamPolicy();
    final class SeamPolicy implements TaskDomainPolicy {
        final JdbcTaskPreparations preparations=new JdbcTaskPreparations(source);
        final AtomicInteger prepares=new AtomicInteger(),rootBatches=new AtomicInteger();
        final List<String> affected=new ArrayList<>();
        public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){
            prepares.incrementAndGet();
            if(purpose==Acquisition.EXTERNAL_FACT_OBSERVATION){
                var ids=new TreeSet<String>((a,b)->Arrays.compareUnsigned(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),b.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                ids.add(owner);ids.addAll(affected);boolean allowed=true;
                for(String id:ids){
                    var statuses=j.queryForList("SELECT status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),String.class,id);
                    if(statuses.isEmpty())return Optional.empty();
                    allowed&=statuses.getFirst().equals("ACTIVE");
                }
                return Optional.of(preparations.issue(specs,purpose,"FACT_ONLY",allowed));
            }
            var identities=access.tryLockIdentities(actor,affected,skip);
            return identities.map(value->preparations.issue(specs,purpose,value,true));
        }
        public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){preparations.require(specs,purpose,held);}
        public boolean permitsDomainEffects(PreparedDomain held){return preparations.permitsEffects(held);}
        PreparedDomain adopt(TaskSpec s,MeetingAccess.Locked held){
            access.requirePrepared(held,actor,s.subject().id(),affected,MeetingAccess.Action.EDIT);
            return preparations.issue(List.of(s),Acquisition.TASK_MUTATION,held,true);
        }
        @Override public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain held){
            rootBatches.incrementAndGet();
            if(!TaskDomainPolicy.super.lockRoots(specs,skip,held))return false;
            for(String id:new TreeSet<>(specs.stream().map(x->x.input().id()).toList()))
                if(j.queryForList("SELECT id FROM meeting_info_revision WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),String.class,id).isEmpty())return false;
            return true;
        }
        @Override public boolean lockRoot(TaskSpec s,boolean skip){throw new IllegalStateException("Explicit preparation required");}
        @Override public boolean lockRoot(TaskSpec s,boolean skip,PreparedDomain prepared){
            Object value=preparations.value(prepared);
            if(value instanceof MeetingAccess.Locked held){access.requirePrepared(held,actor,s.subject().id(),affected,MeetingAccess.Action.EDIT);return true;}
            if(value instanceof MeetingAccess.LockedIdentities identities){
                var held=access.lockPrepared(identities,s.subject().id(),skip);
                if(held.isEmpty())return false;
                access.require(held.orElseThrow(),MeetingAccess.Action.EDIT);return true;
            }
            if(!"FACT_ONLY".equals(value))throw new IllegalArgumentException("Unknown proof");
            return !j.queryForList("SELECT id FROM meeting WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),String.class,s.subject().id()).isEmpty();
        }
        @Override public void validateInput(TaskSpec s){
            var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",s.input().id());
            if(!s.subject().equals(spec.subject())||!s.input().equals(spec.input())||s.businessVersion()!=0||s.cancelEpoch()!=0||rows.size()!=1||!rows.getFirst().get("meeting_id").equals(meeting)
                    ||((Number)rows.getFirst().get("revision")).longValue()!=s.businessVersion()
                    ||!s.inputFingerprint().equals(TaskFixture.hash(input)))throw new IllegalArgumentException("Invalid seam input");
        }
        @Override public void validateOutput(TaskSpec s,TaskResult r){
            if(r.output()!=null&&!r.output().equals(s.input()))throw new IllegalArgumentException("Invalid seam output");
        }
        @Override public void validateExternalRequest(TaskSpec s,ExternalRequest r){
            if(!r.operationKind().equals("SEAM_CALL")||!r.requestManifest().equals("seam-v1:"+s.input().id()))throw new IllegalArgumentException("Unknown controlled request");
        }
        @Override public void validateExternalObservation(TaskSpec s,ama.contract.ExternalContracts.ExternalObservation o){
            if(o.outcome()==ama.contract.ExternalContracts.ExternalOutcome.SUCCEEDED&&!o.externalResultId().startsWith("SEAM_RESULT:"))throw new IllegalArgumentException("Unknown controlled observation");
            if(o.outcome()==ama.contract.ExternalContracts.ExternalOutcome.FAILED_ZERO_EFFECT&&!o.noEffectProof().safeEvidenceReference().equals("SEAM_VERIFIED_ZERO"))throw new IllegalArgumentException("Unknown controlled proof");
        }
    };
    final JdbcOutbox outbox=new JdbcOutbox(source,Map.of("TEST_INFO",event->{
        var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",event.reference().id());
        if(rows.size()!=1||!rows.getFirst().get("meeting_id").equals(event.subject().id())
                ||((Number)rows.getFirst().get("revision")).longValue()!=event.subjectRevision())
            throw new IllegalArgumentException("Invalid seam event provenance");
    }));
    final JdbcTaskQueue queue=new JdbcTaskQueue(source,fences,outbox,Map.of("B06_SEAM_TEST",policy));

    @BeforeEach void setup(){
        new SchemaMigrator(source).migrate();
        tx.required(()->{
            j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);
            fences.create(spec.subject(),0,0);
            j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);
            j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,0,'seam',?,UTC_TIMESTAMP(6))",input,meeting,owner);
            return null;
        });
        trace.clear();
    }

    @Test void standaloneClaimMustLockIdentityBeforeSubject(){
        String taskId=queue.enqueue(spec);
        trace.clear();
        var lease=queue.claim(spec.pool(),"SEAM_WORKER");
        assertTrue(lease.isPresent(),"real task must reach a real claim before lock-order assertion");
        assertEquals(taskId,lease.orElseThrow().taskId());
        List<String> sql=List.copyOf(trace);
        System.out.println("B06 standalone claim actual locking SQL="+sql);
        int identity=index(sql,"FROM enterprise_identity"),subject=index(sql,"FROM task_subject"),
                root=index(sql,"SELECT * FROM meeting WHERE"),task=index(sql,"FROM durable_task WHERE id");
        assertTrue(identity>=0&&subject>=0&&root>=0&&task>=0,"all real identity/subject/root/task locks must occur");
        assertTrue(identity<subject&&subject<root&&root<task,
                "canonical identity -> subject -> meeting -> task required; actual indexes="+List.of(identity,subject,root,task));
    }

    @Test void ambientEnqueueMustReuseAuthenticMeetingCapability(){
        Throwable failure=null;
        try{
            tx.required(()->{
                var held=access.lock(actor,meeting);
                access.require(held,MeetingAccess.Action.EDIT);
                queue.enqueue(spec,policy.adopt(spec,held));
                outbox.flush();
                return null;
            });
        }catch(RuntimeException observed){failure=observed;}
        int tasks=j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE producer_key=?",Integer.class,spec.producerKey());
        int events=j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Integer.class,meeting);
        System.out.println("B06 ambient actual="+(failure==null?"success":failure.getClass().getSimpleName()+": "+failure.getMessage())
                +"; persisted tasks="+tasks+"; persisted events="+events+"; locking SQL="+trace);
        assertNull(failure,"authenticated ambient enqueue must reuse the already-held B03 capability without a repeated identity phase");
        assertEquals(1,tasks);
        assertEquals(1,events);
    }

    @Test void disabledIdentityActuallyBlocksCompletionAndPreventsEffects()throws Exception{
        queue.enqueue(spec);
        TaskLease lease=queue.claim(spec.pool(),"SEAM_DISABLE_WORKER").orElseThrow();
        var disabledHeld=new CountDownLatch(1);
        var releaseDisabled=new CountDownLatch(1);
        completionAtIdentity=new CountDownLatch(1);
        var effects=new AtomicInteger();
        trace.clear();
        try(var threads=Executors.newFixedThreadPool(2)){
            var disable=threads.submit(()->tx.required(()->{
                j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
                disabledHeld.countDown();
                await(releaseDisabled);
                return true;
            }));
            try{
                assertTrue(disabledHeld.await(10,TimeUnit.SECONDS),"disable transaction must hold real identity");
                var finish=threads.submit(()->{
                    completer.set(Thread.currentThread());
                    return queue.completeWithEffects(lease,new TaskResult(Outcome.SUCCEEDED,spec.input(),null),()->{
                        effects.incrementAndGet();
                        j.update("UPDATE meeting SET stage='TRANSCRIPT_PENDING' WHERE id=?",meeting);
                    });
                });
                assertTrue(completionAtIdentity.await(10,TimeUnit.SECONDS),"completion must reach actual identity SELECT FOR UPDATE");
                assertFalse(finish.isDone(),"completion must wait while disable transaction owns identity");
                releaseDisabled.countDown();
                assertTrue(disable.get(10,TimeUnit.SECONDS));
                assertInstanceOf(MeetingAccess.AccessDeniedException.class,
                        assertThrows(ExecutionException.class,()->finish.get(10,TimeUnit.SECONDS)).getCause());
            }finally{releaseDisabled.countDown();}
        }
        assertEquals(0,effects.get(),"disabled owner must have no completion effects");
        assertEquals("ENTRY",j.queryForObject("SELECT stage FROM meeting WHERE id=?",String.class,meeting));
        assertEquals("RUNNING",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));
        assertNull(j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,lease.taskId()));
        System.out.println("B06 disable proof: stale Actor.status="+actor.status()+"; current shared identity=DISABLED; effects=0; task=RUNNING; actual locking SQL="+trace);
    }
    @Test void currentThreeArgumentCompletionValidatesOutputBeforeEffects(){
        String task=queue.enqueue(spec);
        TaskLease lease=queue.claim(spec.pool(),"SEAM_OUTPUT_WORKER").orElseThrow();
        String output=UUID.randomUUID().toString();
        var effects=new AtomicInteger();
        var outputPolicy=new TaskDomainPolicy(){
            public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){return policy.prepare(specs,purpose,skip);}
            public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){policy.requirePrepared(specs,purpose,held);}
            public boolean permitsDomainEffects(PreparedDomain held){return policy.permitsDomainEffects(held);}
            public boolean lockRoot(TaskSpec s,boolean skip,PreparedDomain held){return policy.lockRoot(s,skip,held);}
            public boolean lockRoot(TaskSpec s,boolean skip){return policy.lockRoot(s,skip);}
            public void validateInput(TaskSpec s){policy.validateInput(s);}
            public void validateOutput(TaskSpec s,TaskResult r){
                var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",r.output().id());
                if(!r.output().kind().equals("TEST_INFO")||rows.size()!=1
                        ||!rows.getFirst().get("meeting_id").equals(meeting)
                        ||((Number)rows.getFirst().get("revision")).longValue()!=1)
                    throw new IllegalArgumentException("Output row not present before effects");
            }
            public void validateExternalRequest(TaskSpec s,ExternalRequest r){policy.validateExternalRequest(s,r);}
        };
        var outputQueue=new JdbcTaskQueue(source,fences,outbox,Map.of("B06_SEAM_TEST",outputPolicy));
        var failure=assertThrows(IllegalArgumentException.class,()->outputQueue.completeWithEffects(lease,
                new TaskResult(Outcome.SUCCEEDED,new TypedRef("TEST_INFO",output),null),()->{
                    effects.incrementAndGet();
                    j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,1,'output',?,UTC_TIMESTAMP(6))",output,meeting,owner);
                }));
        assertEquals("Output row not present before effects",failure.getMessage());
        assertEquals(0,effects.get());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?",Integer.class,output));
        assertEquals("RUNNING",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
        System.out.println("B06 existing three-argument output ordering control: validation rejected absent real output before effects; effects=0; output rows=0; task=RUNNING. This is existing API behavior, not a production defect.");
    }
    @Test void completeIdentityUnionUsesBinaryOrderBeforeAnySubject(){
        String prefix="b06-union-"+UUID.randomUUID()+"-",bmp=prefix+"\uE000",nonBmp=prefix+"\uD800\uDC00";
        for(String id:List.of(bmp,nonBmp))j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);
        policy.affected.addAll(List.of(nonBmp,bmp));trace.clear();
        queue.enqueue(spec);
        var identities=trace.stream().filter(x->x.contains("FROM enterprise_identity")).toList();
        assertEquals(3,identities.size());
        var expected=new ArrayList<>(List.of(owner,bmp,nonBmp));
        expected.sort((a,b)->Arrays.compareUnsigned(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),b.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(int i=0;i<expected.size();i++)assertTrue(identities.get(i).endsWith("/*identity="+expected.get(i)+"*/"),identities.toString());
        int firstSubject=index(trace,"FROM task_subject");
        for(String sql:identities)assertTrue(trace.indexOf(sql)<firstSubject);
        assertEquals(expected,j.queryForList("SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id IN (?,?,?) ORDER BY enterprise_id COLLATE utf8mb4_0900_bin",String.class,owner,bmp,nonBmp));
    }

    @Test void heldIdentitySkipsBeforeSubjectAndReleasesPartialUnion()throws Exception{
        String first="A-b06-"+UUID.randomUUID();
        j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",first,first);
        policy.affected.add(first);queue.enqueue(spec);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var threads=Executors.newSingleThreadExecutor()){
            var blocker=threads.submit(()->tx.required(()->{
                j.queryForObject("SELECT status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE",String.class,owner);
                held.countDown();await(release);return true;
            }));
            try{
                assertTrue(held.await(10,TimeUnit.SECONDS));trace.clear();
                assertTrue(queue.claim(spec.pool(),"SKIP_IDENTITY").isEmpty());
                assertEquals(-1,index(trace,"FROM task_subject"),trace.toString());
                tx.required(()->{
                    assertEquals(List.of(first),j.queryForList("SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE SKIP LOCKED",String.class,first));
                    return null;
                });
            }finally{release.countDown();}
            assertTrue(blocker.get(10,TimeUnit.SECONDS));
        }
    }

    @Test void disabledCandidateIsSkippedWithoutSubjectOrTaskLocks(){
        queue.enqueue(spec);
        j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);trace.clear();
        assertTrue(assertDoesNotThrow(()->queue.claim(spec.pool(),"DISABLED_SKIP")).isEmpty());
        assertEquals(-1,index(trace,"FROM task_subject"));assertEquals(-1,index(trace,"FROM durable_task"));
    }

    @Test void preparationsRejectForgeryIssuerSpecPurposeAndExpiredTransactions(){
        var captured=new AtomicReference<TaskDomainPolicy.PreparedDomain>();
        tx.required(()->{
            var token=policy.prepare(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,false).orElseThrow();
            captured.set(token);
            assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,new TaskDomainPolicy.PreparedDomain(){}));
            var other=new JdbcTaskPreparations(source);
            var foreign=other.issue(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,null,true);
            assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,foreign));
            assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(List.of(variant("OTHER")),TaskDomainPolicy.Acquisition.TASK_MUTATION,token));
            assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(List.of(spec),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,token));
            assertThrows(IllegalStateException.class,()->new JdbcTaskPreparations(actual).issue(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,null,true));
            return null;
        });
        assertThrows(IllegalStateException.class,()->policy.requirePrepared(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,captured.get()));
        tx.required(()->{assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,captured.get()));return null;});
        assertThrows(IllegalArgumentException.class,()->tx.required(()->queue.enqueue(spec,new TaskDomainPolicy.PreparedDomain(){})));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE producer_key=?",Integer.class,spec.producerKey()));
    }

    @Test void meetingCapabilitiesCheckCoverageIssuerIdentityAndNoHiddenSynchronization(){
        var identities=new AtomicReference<MeetingAccess.LockedIdentities>();
        tx.required(()->{
            identities.set(access.tryLockIdentities(actor,List.of(),false).orElseThrow());
            var gate=access.lockPrepared(identities.get(),meeting,false).orElseThrow();
            assertThrows(IllegalArgumentException.class,()->access.lockPrepared(new MeetingAccess.LockedIdentities(){},meeting,false));
            var other=new JdbcMeetingAccess(source,tx,fences);
            assertThrows(IllegalArgumentException.class,()->other.require(gate,MeetingAccess.Action.EDIT));
            assertThrows(IllegalArgumentException.class,()->access.requirePrepared(new MeetingAccess.Locked(){},actor,meeting,List.of(),MeetingAccess.Action.EDIT));
            assertThrows(IllegalArgumentException.class,()->access.requirePrepared(gate,actor,UUID.randomUUID().toString(),List.of(),MeetingAccess.Action.EDIT));
            assertThrows(IllegalArgumentException.class,()->access.requirePrepared(gate,new Actor("foreign",Status.ACTIVE,Set.of(Role.USER)),meeting,List.of(),MeetingAccess.Action.EDIT));
            assertThrows(IllegalArgumentException.class,()->access.requirePrepared(gate,actor,meeting,List.of("not-prelocked"),MeetingAccess.Action.EDIT));
            assertThrows(IllegalStateException.class,()->access.tryLockIdentities(actor,List.of(),false));
            assertEquals(1,org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().size());
            queue.enqueue(spec,policy.adopt(spec,gate));
            assertEquals(1,org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().size());
            outbox.flush();return null;
        });
        tx.required(()->{assertThrows(IllegalArgumentException.class,()->access.lockPrepared(identities.get(),meeting,false));return null;});
    }

    @Test void disabledExternalSuccessIsRetainedWithoutEffectsOrRenewalOrResend(){
        queue.enqueue(spec);var lease=queue.claim(spec.pool(),"FACT_WORKER").orElseThrow();
        var external=new JdbcExternalOperations(queue);
        var token=external.beginExternal(lease,request(spec)).orElseThrow();
        j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
        var callbacks=new AtomicInteger();trace.clear();
        assertEquals(ama.contract.ExternalContracts.ObservationResult.RECORDED_FENCED,
                external.observe(token,success(spec),callbacks::incrementAndGet));
        assertEquals(0,callbacks.get());
        assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,token.executionId()));
        assertEquals("SEAM_RESULT:"+input,j.queryForObject("SELECT external_result_id FROM external_operation WHERE id=?",String.class,token.operationId()));
        assertEquals("STALE",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));
        assertFalse(external.hasCallAuthority(token));
        assertThrows(MeetingAccess.AccessDeniedException.class,()->queue.renew(lease));
        assertThrows(MeetingAccess.AccessDeniedException.class,()->external.renew(token));
        assertThrows(MeetingAccess.AccessDeniedException.class,()->external.beginExternal(lease,request(spec)));
        assertThrows(MeetingAccess.AccessDeniedException.class,()->external.retryAfterZeroEffect(token.operationId(),1,variant("RETRY_DISABLED"),request(spec)));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE operation_id=?",Integer.class,token.operationId()));
    }

    @Test void zeroEffectRetryReusesOnePreparationAndCompleteRootBatch(){
        queue.enqueue(spec);var lease=queue.claim(spec.pool(),"RETRY_SCOPE").orElseThrow();
        var external=new JdbcExternalOperations(queue);var token=external.beginExternal(lease,request(spec)).orElseThrow();
        external.observe(token,zeroEffect(),()->{});
        policy.prepares.set(0);policy.rootBatches.set(0);trace.clear();
        var next=variant("RETRY_OK");
        String task=external.retryAfterZeroEffect(token.operationId(),1,next,request(next));
        assertEquals(1,policy.prepares.get());assertEquals(1,policy.rootBatches.get());
        assertEquals(1,trace.stream().filter(x->x.contains("FROM enterprise_identity")).count());
        int firstTask=index(trace,"FROM durable_task");
        for(int i=firstTask+1;i<trace.size();i++)assertFalse(trace.get(i).contains("FROM enterprise_identity")||trace.get(i).contains("FROM meeting WHERE")||trace.get(i).contains("FROM meeting_info_revision"),trace.toString());
        policy.prepares.set(0);policy.rootBatches.set(0);
        assertEquals(task,external.retryAfterZeroEffect(token.operationId(),1,next,request(next)));
        assertEquals(1,policy.prepares.get());assertEquals(1,policy.rootBatches.get());
    }

    @Test void crossPolicyObservationIsNonAcknowledgmentAndSameFactCanBeResubmitted(){
        var original=new JdbcTaskQueue(source,fences,outbox,Map.of("B06_SEAM_TEST",policy,"B06_SEAM_OTHER",policy));
        original.enqueue(spec);var lease=original.claim(spec.pool(),"UNION_A").orElseThrow();
        var external=new JdbcExternalOperations(original);var old=external.beginExternal(lease,request(spec)).orElseThrow();
        external.observe(old,zeroEffect(),()->{});
        var candidate=variant("UNION_B");
        var next=new TaskSpec("B06_SEAM_OTHER",candidate.pool(),candidate.subject(),candidate.producerKey(),candidate.input(),candidate.inputFingerprint(),candidate.businessVersion(),candidate.priority(),candidate.cancelEpoch(),candidate.maxAttempts());
        external.retryAfterZeroEffect(old.operationId(),1,next,request(next));
        var nextLease=original.claim(next.pool(),"UNION_B").orElseThrow();
        var current=external.beginExternal(nextLease,request(next)).orElseThrow();
        TaskDomainPolicy foreign=new TaskDomainPolicy(){
            public boolean lockRoot(TaskSpec s,boolean skip){throw new AssertionError("Cross-family root must not run");}
            public void validateInput(TaskSpec s){throw new AssertionError();}
            public void validateOutput(TaskSpec s,TaskResult r){throw new AssertionError();}
            public void validateExternalRequest(TaskSpec s,ExternalRequest r){throw new AssertionError();}
        };
        var mismatched=new JdbcExternalOperations(new JdbcTaskQueue(source,fences,outbox,Map.of("B06_SEAM_TEST",policy,"B06_SEAM_OTHER",foreign)));
        trace.clear();
        assertThrows(DomainPreparationUnavailableException.class,()->mismatched.observe(old,success(spec),()->fail("cross policy callback")));
        assertEquals(-1,index(trace,"FOR UPDATE"),"unknown common issuer must reject before any locks");
        assertEquals("MARKED",j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,current.operationId()));
        assertEquals(ama.contract.ExternalContracts.ObservationResult.QUARANTINED,external.observe(old,success(spec),()->fail("old success effects")));
        assertEquals("MANUAL_REQUIRED",j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,current.operationId()));
        assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE operation_id=?",Integer.class,current.operationId()));
    }

    @Test void registeredIdentityFreeDomainMustExplicitlyOptIn(){
        TaskDomainPolicy unprepared=new TaskDomainPolicy(){
            public boolean lockRoot(TaskSpec s,boolean skip){throw new AssertionError("Unprepared domain root must not run");}
            public void validateInput(TaskSpec s){throw new AssertionError();}
            public void validateOutput(TaskSpec s,TaskResult r){throw new AssertionError();}
            public void validateExternalRequest(TaskSpec s,ExternalRequest r){throw new AssertionError();}
        };
        var unavailable=new JdbcTaskQueue(source,fences,outbox,Map.of(spec.type(),unprepared));
        trace.clear();
        assertThrows(DomainPreparationUnavailableException.class,()->unavailable.enqueue(spec));
        assertEquals(-1,index(trace,"FOR UPDATE"));
    }

    @Test void disabledExpiredCurrentExternalFactMustStillBeRetained(){
        queue.enqueue(spec);var lease=queue.claim(spec.pool(),"EXPIRED_FACT").orElseThrow();
        var external=new JdbcExternalOperations(queue);var token=external.beginExternal(lease,request(spec)).orElseThrow();
        j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
        j.update("UPDATE external_operation SET reconcile_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",token.operationId());
        var callbacks=new AtomicInteger();
        var unverified=new ama.contract.ExternalContracts.ExternalObservation(ama.contract.ExternalContracts.ExternalOutcome.SUCCEEDED,"UNVERIFIED",spec.input(),null,null);
        assertThrows(IllegalArgumentException.class,()->external.observe(token,unverified,callbacks::incrementAndGet));
        assertEquals("MARKED",j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,token.operationId()));
        assertEquals(ama.contract.ExternalContracts.ObservationResult.RECORDED_FENCED,external.observe(token,success(spec),callbacks::incrementAndGet));
        assertEquals(0,callbacks.get());
        assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,token.executionId()));
        assertEquals("SEAM_RESULT:"+input,j.queryForObject("SELECT external_result_id FROM external_operation WHERE id=?",String.class,token.operationId()));
        assertEquals("STALE",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));
        assertFalse(external.hasCallAuthority(token));
    }

    @Test void crossSubjectPreparationRejectsBeforeIdentityOrRootLocks(){
        var foreign=new TaskSpec(spec.type(),spec.pool(),new SubjectRef("MEETING",UUID.randomUUID().toString()),spec.producerKey(),spec.input(),spec.inputFingerprint(),0,0,0,3);
        trace.clear();
        assertThrows(DomainPreparationUnavailableException.class,()->tx.required(()->queue.prepareDomain(List.of(spec,foreign),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,false,null)));
        assertEquals(-1,index(trace,"FOR UPDATE"));
    }

    TaskSpec variant(String marker){return new TaskSpec(spec.type(),spec.pool(),spec.subject(),TaskFixture.hash(marker+spec.producerKey()),spec.input(),spec.inputFingerprint(),spec.businessVersion(),spec.priority(),spec.cancelEpoch(),spec.maxAttempts());}
    ExternalRequest request(TaskSpec s){return new ExternalRequest("SEAM_CALL",spec.producerKey(),TaskFixture.hash("logical"+input),TaskFixture.hash("execution"+input),"seam-v1:"+input);}
    ama.contract.ExternalContracts.ExternalObservation success(TaskSpec s){return new ama.contract.ExternalContracts.ExternalObservation(ama.contract.ExternalContracts.ExternalOutcome.SUCCEEDED,"SEAM_RESULT:"+input,s.input(),null,null);}
    ama.contract.ExternalContracts.ExternalObservation zeroEffect(){return new ama.contract.ExternalContracts.ExternalObservation(ama.contract.ExternalContracts.ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"CONTROLLED_REJECTION",new ama.contract.ExternalContracts.NoEffectProof(ama.contract.ExternalContracts.NoEffectBasis.DEFINITIVE_REJECTION,"SEAM_VERIFIED_ZERO",java.time.Instant.now()));}

    static int index(List<String> sql,String part){for(int i=0;i<sql.size();i++)if(sql.get(i).contains(part))return i;return -1;}
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JDBC gate timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    interface Probe {void before(String sql);}
    static final class ProbeSource extends AbstractDataSource {
        final DataSource delegate;final Probe probe;
        ProbeSource(DataSource delegate,Probe probe){this.delegate=delegate;this.probe=probe;}
        @Override public Connection getConnection()throws SQLException{
            var real=delegate.getConnection();
            real.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                Object value=invoke(real,m,a);
                if(m.getName().equals("prepareStatement")){
                    String sql=(String)a[0];PreparedStatement statement=(PreparedStatement)value;var parameters=new HashMap<Integer,Object>();
                    statement.setQueryTimeout(10);
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                        if(m2.getName().startsWith("set")&&a2!=null&&a2.length>=2&&a2[0] instanceof Integer n)parameters.put(n,a2[1]);
                        if(m2.getName().startsWith("execute"))probe.before(sql+(sql.contains("FROM enterprise_identity")?" /*identity="+parameters.get(1)+"*/":""));
                        return invoke(statement,m2,a2);
                    });
                }
                return value;
            });
        }
        @Override public Connection getConnection(String u,String p)throws SQLException{return getConnection();}
        static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    }
}
