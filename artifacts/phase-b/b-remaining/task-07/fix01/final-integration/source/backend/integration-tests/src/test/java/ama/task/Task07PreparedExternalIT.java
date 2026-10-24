package ama.task;

import ama.access.MeetingAccess;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import ama.task.ExternalOperationPort.*;
import ama.contract.IdentityContracts.*;
import ama.db.*;
import ama.meeting.JdbcMeetingAccess;
import ama.support.MysqlTestDatabase;
import ama.worker.WorkerLoop;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Real owned MySQL, authentic B03 identity preparation and actual immutable output rows. No resets. */
class Task07PreparedExternalIT {
    final AtomicBoolean failFlush = new AtomicBoolean(), failAudit = new AtomicBoolean(), failEvent = new AtomicBoolean();
    final Task07ProbeSource source=new Task07ProbeSource(Task07DataSource.create());
    {source.before=event->{String sql=event.text();
        if(failAudit.get()&&sql.startsWith("INSERT INTO audit_event("))throw new IllegalStateException("CONTROLLED_AUDIT_FAILURE");
        if(failFlush.get()&&sql.startsWith("INSERT INTO task_outbox("))throw new IllegalStateException("CONTROLLED_FLUSH_FAILURE");
    };}
    final JdbcTemplate jdbc = new JdbcTemplate(source);
    final TransactionRunner tx = new TransactionRunner(source);
    final JdbcSubjectFences fences = new JdbcSubjectFences(source, Set.of("MEETING"));
    final JdbcMeetingAccess access = new JdbcMeetingAccess(source, tx, fences);
    final String owner = "prepared-" + UUID.randomUUID(), meeting = UUID.randomUUID().toString();
    final String input = UUID.randomUUID().toString(), output = UUID.randomUUID().toString();
    final Actor actor = new Actor(owner, Status.ACTIVE, Set.of(Role.USER));
    final TaskSpec spec = new TaskSpec("T07_PREPARED_"+UUID.randomUUID(), "PREPARED_" + UUID.randomUUID(),
            new SubjectRef("MEETING", meeting), Task08InheritedFixture.hash(input + "producer"),
            new TypedRef("TEST_INFO", input), Task08InheritedFixture.hash(input), 0, 0, 0, 3);
    final Set<TaskSpec> coveredSpecs=new HashSet<>(Set.of(spec));
    final PreparedRequest preparedRequest=new PreparedRequest(){};
    final PreparedObservation preparedObservation=new PreparedObservation(){};
    final Map<PreparedObservation,ExternalObservation> receipts=Collections.synchronizedMap(new IdentityHashMap<>());
    PreparedObservation receipt(ExternalObservation value){var receipt=new PreparedObservation(){};receipts.put(receipt,value);return receipt;}
    ExternalObservation remote(PreparedObservation observation){if(observation==preparedObservation)return fact;var value=receipts.get(observation);if(value==null)throw new IllegalArgumentException("Foreign observation");return value;}
    final AtomicBoolean preparationUnavailable=new AtomicBoolean(),deniedFacts=new AtomicBoolean();
    private record DeniedIdentity(String status) {}
    final ExternalRequest wire=new ExternalRequest("T07_CALL",spec.producerKey(),Task08InheritedFixture.hash("logical"+input),Task08InheritedFixture.hash("execution"+input),"task07:"+input);
    ExternalObservation fact=new ExternalObservation(ExternalOutcome.SUCCEEDED,"T07_RESULT:"+input,null,null,null);
    final AtomicInteger markers=new AtomicInteger();
    final AtomicBoolean invalid=new AtomicBoolean(),badOutput=new AtomicBoolean();
    final JdbcTaskPreparations preparations=new JdbcTaskPreparations(source);
    TaskDomainPolicy.PreparedDomain capturedDomain;ExecutionContext capturedContext;
    final AtomicInteger writes = new AtomicInteger(), validations = new AtomicInteger();
    final PreparedExternalPolicy policy = new PreparedExternalPolicy() {

        public Optional<PreparedDomain> prepare(List<TaskSpec> specs, Acquisition purpose, boolean skip) {
            if(preparationUnavailable.get())return Optional.empty();
            if(deniedFacts.get()&&purpose==Acquisition.EXTERNAL_FACT_OBSERVATION){
                var status=jdbc.queryForObject("SELECT status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE",String.class,owner);
                return Optional.of(preparations.issue(specs,purpose,new DeniedIdentity(status),false));
            }
            return access.tryLockIdentities(actor, List.of(), skip)
                    .map(held -> preparations.issue(specs, purpose, held, true));
        }
        public void requirePrepared(List<TaskSpec> specs, Acquisition purpose, PreparedDomain held) { preparations.require(specs, purpose, held); }
        public boolean permitsDomainEffects(PreparedDomain held) { return preparations.permitsEffects(held); }
        public boolean lockRoot(TaskSpec s, boolean skip) { throw new IllegalStateException("Authentic preparation required"); }
        public boolean lockRoot(TaskSpec s, boolean skip, PreparedDomain prepared) {
            if(preparations.value(prepared) instanceof DeniedIdentity denied){
                var root=jdbc.queryForMap("SELECT lifecycle,governance_state FROM meeting WHERE id=? FOR UPDATE",meeting);
                if(denied.status().equals("ACTIVE")&&root.get("lifecycle").equals("ACTIVE")&&root.get("governance_state").equals("ACTIVE"))throw new IllegalArgumentException("Actual denied facts required");
                return !jdbc.queryForList("SELECT id FROM meeting_info_revision WHERE id=? FOR UPDATE",input).isEmpty();
            }
            var identities = (MeetingAccess.LockedIdentities) preparations.value(prepared);
            var held = access.lockPrepared(identities, meeting, skip);
            if (held.isEmpty()) return false;
            access.require(held.orElseThrow(), MeetingAccess.Action.EDIT);
            return !jdbc.queryForList("SELECT id FROM meeting_info_revision WHERE id=? FOR UPDATE" + (skip ? " SKIP LOCKED" : ""), String.class, input).isEmpty();
        }
        public void validateInput(TaskSpec s) {
            if (!coveredSpecs.contains(s) || jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND revision=0 AND name='input'", Integer.class, input, meeting) != 1)
                throw new IllegalArgumentException("Invalid saved input");
        }
        public void validateOutput(TaskSpec s, TaskResult result) {
            validations.incrementAndGet();
            if(result.outcome()==Outcome.FAILED&&result.output()==null&&!"INVALID_MODEL_OUTPUT".equals(result.errorCode()))return;
            if(invalid.get()){if(result.outcome()!=Outcome.FAILED||result.output()!=null||!"INVALID_MODEL_OUTPUT".equals(result.errorCode()))throw new IllegalArgumentException();return;}
            if (result.output() == null || !result.output().equals(new TypedRef("TEST_INFO", output))
                    || jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND revision=1 AND name='output'", Integer.class, output, meeting) != 1)
                throw new IllegalArgumentException("Actual output must exist with matching provenance");
            assertEquals("RECONCILING", state(), "terminal mutation must follow actual output validation");
        }
        public void validateExternalRequest(TaskSpec s, ExternalRequest request) { if(!request.requestManifest().equals("task07:"+input))throw new IllegalArgumentException("Request mismatch"); }
        public ExternalRequest requireRequest(TaskSpec s, PreparedRequest request){if(request!=preparedRequest||!coveredSpecs.contains(s))throw new IllegalArgumentException("Foreign request");return wire;}
        public void writeMarked(PreparedDomain held,ExecutionContext context,PreparedRequest request){preparations.requireExternal(held,context);capturedDomain=held;capturedContext=context;markers.incrementAndGet();}
        public ExternalObservation verifyRemoteFact(TaskSpec s,ExecutionContext context,PreparedObservation observation){if(!coveredSpecs.contains(s))throw new IllegalArgumentException("Foreign spec");return remote(observation);}
        public TaskResult writeCurrent(PreparedDomain held,ExecutionContext context,PreparedObservation observation){preparations.requireExternal(held,context);if(remote(observation).outcome()==ExternalOutcome.FAILED_ZERO_EFFECT)return new TaskResult(Outcome.FAILED,null,remote(observation).errorCode());if(invalid.get())return new TaskResult(Outcome.FAILED,null,"INVALID_MODEL_OUTPUT");var result=writeOutput();return badOutput.get()?new TaskResult(Outcome.SUCCEEDED,new TypedRef("WRONG",output),null):result;}
        public void validateStoredResult(PreparedDomain held,ExecutionContext context,PreparedObservation observation,TaskResult result){preparations.requireExternal(held,context);if(!invalid.get()&&remote(observation).outcome()==ExternalOutcome.SUCCEEDED)assertEquals(output,result.output().id());}
        public boolean verifyReplay(PreparedDomain held,ExecutionContext context,PreparedObservation observation,StoredResult stored){preparations.requireExternal(held,context);if(stored.state().equals("STALE"))return remote(observation).outcome()==ExternalOutcome.SUCCEEDED&&stored.output()==null&&"BUSINESS_FENCED".equals(stored.errorCode())&&jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?",Integer.class,output)==0;if(invalid.get())return observation==preparedObservation&&stored.state().equals("FAILED")&&"INVALID_MODEL_OUTPUT".equals(stored.errorCode())&&stored.output()==null&&jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?",Integer.class,output)==0;return observation==preparedObservation&&stored.state().equals("SUCCEEDED")&&stored.output()!=null&&stored.output().id().equals(output)&&jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND name='output'",Integer.class,output,meeting)==1;}

    };
    final JdbcOutbox outbox = new JdbcOutbox(source, Map.of("TEST_INFO", event -> {
        if (failEvent.get()) throw new IllegalArgumentException("CONTROLLED_EVENT_FAILURE");
        if (!event.subject().equals(spec.subject()) || !event.reference().id().equals(input)) throw new IllegalArgumentException("Invalid event provenance");
    }));
    final JdbcTaskQueue queue = new JdbcTaskQueue(source, fences, outbox, Map.of(spec.type(), policy));
    TaskLease lease;

    @BeforeAll static void migrateOwnedEmptyFixture() {
        var source = Task07DataSource.create();
        assertEquals(15,new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM flyway_schema_history",Integer.class));
        var history = new JdbcTemplate(source).queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
        assertEquals(15, history.size());
        System.out.println("PREPARED completion pre-test immutable history: " + history);
    }
    @BeforeEach void setup() {
        tx.required(() -> {
            jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))", owner, owner);
            fences.create(spec.subject(), 0, 0);
            jdbc.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", meeting, owner);
            jdbc.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,0,'input',?,UTC_TIMESTAMP(6))", input, meeting, owner);
            return null;
        });
        queue.enqueue(spec);
        lease = queue.claim(spec.pool(), "PREPARED_WORKER").orElseThrow();
    }
    TaskResult writeOutput() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        writes.incrementAndGet();
        jdbc.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,1,'output',?,UTC_TIMESTAMP(6))", output, meeting, owner);
        new ama.audit.JdbcAuditAdapter(source).append(new ama.contract.AuditContracts.AuditEvent(
                owner, "PREPARED_OUTPUT", meeting, 1, "SUCCESS", UUID.randomUUID().toString(), java.time.Instant.now()));
        return new TaskResult(Outcome.SUCCEEDED, new TypedRef("TEST_INFO", output), null);
    }
    String state() { return jdbc.queryForObject("SELECT state FROM durable_task WHERE producer_key=?", String.class, spec.producerKey()); }
    int events() { return jdbc.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=?", Integer.class, lease.taskId()); }
    void rolledBack() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?", Integer.class, output));
        assertEquals("RUNNING", state());
        assertNull(jdbc.queryForObject("SELECT output_id FROM durable_task WHERE id=?", String.class, lease.taskId()));
        assertNull(jdbc.queryForObject("SELECT ended_at FROM task_attempt WHERE task_id=? AND attempt=1", java.sql.Timestamp.class, lease.taskId()));
        assertEquals(1, events());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?", Integer.class, meeting));
    }
    boolean complete(Supplier<TaskResult> effects) { return queue.completeWithEffects(lease, effects); }
    @Test void preparedOutputIsCreatedBeforeValidationAndAttemptEndsAtomically(){
        var operations=new JdbcExternalOperations(queue);
        var token=assertDoesNotThrow(()->operations.beginPrepared(lease,preparedRequest)).orElseThrow();
        assertEquals(1,markers.get());assertEquals("RECONCILING",state());
        assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));
        assertEquals(1,writes.get());assertEquals(1,validations.get());assertEquals("SUCCEEDED",state());
        assertEquals(output,jdbc.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,lease.taskId()));
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT outcome FROM task_attempt WHERE task_id=? AND attempt=1",String.class,lease.taskId()));
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,token.operationId()));
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,token.executionId()));
        assertEquals(3,events());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,meeting));
    }
    @Test void terminalDebtRecoversWithoutCallerPermitWhileUnknownCapacityRemainsHeld(){
        var operations=new JdbcExternalOperations(queue);
        var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();
        String model="T07_"+UUID.randomUUID().toString().replace("-", "");
        var pool=new JdbcModelPermitPool(operations,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException();});
        pool.configure();var permit=pool.acquire(token,model,null).orElseThrow();
        assertEquals(0,pool.recoverSettlements(1));
        assertNull(jdbc.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,permit.id()));
        operations.observePrepared(token,preparedObservation);
        assertNotNull(jdbc.queryForObject("SELECT finished_at FROM external_execution WHERE id=?",java.sql.Timestamp.class,token.executionId()));
        int passLimit=2*jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation",Integer.class)+2;boolean settled=false;for(int i=0;i<passLimit&&!settled;i++){pool.recoverSettlements(1);settled=jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,permit.id());}
        assertTrue(settled,"Committed terminal debt must release without the lost in-memory permit");
        assertTrue(pool.settled(permit));
    }

    @Test void samePreparedFactAfterTerminalAcknowledgementLossReplaysWithoutAnotherWriter(){
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();
        assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));
        assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));
        assertEquals(1,writes.get());assertEquals(3,events());
    }
    @Test void registeredPreparedPolicyCannotBypassMarkerWithLegacyRequest(){
        assertThrows(IllegalArgumentException.class,()->new JdbcExternalOperations(queue).beginExternal(lease,wire));
        assertEquals("RUNNING",state());assertEquals(0,markers.get());
    }

    @Test void actualWorkerOwnsMarkerTransportAndCompletionExactlyOnce()throws Exception{
        jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",lease.taskId());
        var operations=new JdbcExternalOperations(queue);String model="T07_"+UUID.randomUUID().toString().replace("-", "");
        var permits=new JdbcModelPermitPool(operations,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException();});permits.configure();
        var sent=new AtomicInteger();var ordinary=new AtomicInteger();var done=new CountDownLatch(1);var error=new AtomicReference<Throwable>();
        TaskQueue observed=new TaskQueue(){
         public String enqueue(TaskSpec s){return queue.enqueue(s);}public Optional<TaskLease> claim(String p,String o){return queue.claim(p,o);}public boolean renew(TaskLease l){return queue.renew(l);}
         public boolean complete(TaskLease l,TaskResult r){ordinary.incrementAndGet();return queue.complete(l,r);}public boolean completeWithEffects(TaskLease l,TaskResult r,Runnable e){throw new AssertionError();}
        };
        WorkerLoop.ExternalHandler handler=e->{try{
         assertTrue(e.begin(preparedRequest));assertEquals("RECONCILING",state());
         assertTrue(e.send(()->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());sent.incrementAndGet();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE execution_id=? AND settled_at IS NULL",Integer.class,e.token().executionId()));return preparedObservation;}));
        }catch(Throwable t){error.set(t);}finally{done.countDown();}};
        try(var worker=assertDoesNotThrow(()->WorkerLoop.external(observed,operations,permits,Map.of(spec.pool(),1),Map.of(spec.type(),handler),Map.of(spec.type(),model),"T07_ACTUAL_WORKER"))){worker.pollOnce(spec.pool());assertTrue(done.await(10,TimeUnit.SECONDS));}
        assertNull(error.get());assertEquals(1,sent.get());assertEquals(0,ordinary.get());assertEquals(1,writes.get());assertEquals("SUCCEEDED",state());
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT outcome FROM task_attempt WHERE task_id=? AND attempt=2",String.class,lease.taskId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation r JOIN external_execution e ON e.id=r.execution_id WHERE e.task_id=? AND r.settled_at IS NOT NULL",Integer.class,lease.taskId()));
    }

    @Test void reconciliationMaintenanceSettlesTerminalDebtWithNoClaimableTasks()throws Exception{
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();
        String model="T07_"+UUID.randomUUID().toString().replace("-", "");
        var permits=new JdbcModelPermitPool(operations,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException();});permits.configure();var permit=permits.acquire(token,model,null).orElseThrow();
        operations.observePrepared(token,preparedObservation);
        try(var loop=assertDoesNotThrow(()->ama.worker.ReconciliationLoop.prepared(operations,permits,1,s->{throw new AssertionError("No claimable Task7 execution");},"T07_MAINTENANCE"))){
         int pollBound=2*((jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation",Integer.class)+7)/8)+2;boolean settled=false;
         // Exercise the real existing cadence entry once per finite slice, without assuming historical rows fit in two wall-clock ticks.
         for(int i=0;i<pollBound&&!settled;i++){Task07ExternalWorkerIT.invoke(loop,"poll");settled=jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,permit.id());}
         assertTrue(settled,"Maintenance must run even when no execution is claimable");
        }
    }

    @Test void invalidLocalOutputPreservesRemoteSuccessAndReleasesOnlyVerifiedTerminalPermit(){
        invalid.set(true);var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();
        assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,token.executionId()));
        assertEquals("FAILED",state());assertEquals("INVALID_MODEL_OUTPUT",jdbc.queryForObject("SELECT last_error FROM durable_task WHERE id=?",String.class,lease.taskId()));assertEquals(0,writes.get());assertNull(jdbc.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,lease.taskId()));
    }
    @Test void foreignPreparedRequestAndObservationCannotInvokeWriters(){
        var operations=new JdbcExternalOperations(queue);assertThrows(IllegalArgumentException.class,()->operations.beginPrepared(lease,new PreparedRequest(){}));assertEquals(0,markers.get());
        var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();assertThrows(IllegalArgumentException.class,()->operations.observePrepared(token,new PreparedObservation(){}));assertEquals(0,writes.get());assertEquals("RECONCILING",state());
    }
    @Test void expiredAndCopiedLeaseCannotMark(){
        var operations=new JdbcExternalOperations(queue);assertTrue(operations.beginPrepared(new TaskLease(lease.taskId(),lease.attempt()+1,lease.leaseOwner(),lease.expiresAt(),spec),preparedRequest).isEmpty());
        jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",lease.taskId());assertTrue(operations.beginPrepared(lease,preparedRequest).isEmpty());assertEquals(0,markers.get());
    }
    @Test void privateExecutionContextExpiresAndCannotBeReconstructedOrCrossSource(){
        new JdbcExternalOperations(queue).beginPrepared(lease,preparedRequest).orElseThrow();
        assertThrows(RuntimeException.class,()->tx.required(()->{preparations.requireExternal(capturedDomain,capturedContext);return null;}));
        assertThrows(IllegalArgumentException.class,()->tx.required(()->{var held=preparations.issue(List.of(spec),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,null,true);preparations.requireExternal(held,new ExecutionContext(){});return null;}));
        var other=Task07DataSource.create();assertThrows(RuntimeException.class,()->new TransactionRunner(other).required(()->tx.required(()->{new JdbcTaskPreparations(other).requireExternal(capturedDomain,capturedContext);return null;})));
    }
    @Test void receiptOnlyRunsNoDomainWriterEvenWithCurrentAuthority(){
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();assertEquals(ObservationResult.RECORDED_FENCED,operations.observePreparedFact(token,preparedObservation));assertEquals(0,writes.get());assertEquals("STALE",state());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,meeting));
    }
    @Test void activeOldEpochRemainsStaleAndCannotWrite(){
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();jdbc.update("UPDATE external_operation SET reconcile_epoch=reconcile_epoch+1 WHERE id=?",token.operationId());
        assertEquals(ObservationResult.STALE_TOKEN,operations.observePrepared(token,preparedObservation));assertEquals(ObservationResult.STALE_TOKEN,operations.observePreparedFact(token,preparedObservation));assertEquals(0,writes.get());assertEquals("RECONCILING",state());
    }
    @Test void outputValidationRollbackPreservesMarkerThenSameReceiptWritesOnceDurably(){
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();badOutput.set(true);assertThrows(IllegalArgumentException.class,()->operations.observePrepared(token,preparedObservation));externalRollback(token);badOutput.set(false);
        assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?",Integer.class,output));
    }
    @Test void flushRollbackPreservesOutputTaskAttemptAndRemoteState(){
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();failFlush.set(true);assertThrows(IllegalStateException.class,()->operations.observePrepared(token,preparedObservation));failFlush.set(false);externalRollback(token);
    }
    @Test void auditRollbackPreservesOutputTaskAttemptAndRemoteState(){
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();failAudit.set(true);assertThrows(IllegalStateException.class,()->operations.observePrepared(token,preparedObservation));failAudit.set(false);externalRollback(token);
    }
    @Test void terminalCommitAcknowledgementLossReplaysWithoutSecondWriter(){
        var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();var once=new AtomicBoolean();source.committed=()->{if(once.compareAndSet(false,true))throw new IllegalStateException("T07_LOST_COMMIT_ACK");};
        assertThrows(IllegalStateException.class,()->operations.observePrepared(token,preparedObservation));source.committed=()->{};assertEquals("SUCCEEDED",state());assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));assertEquals(1,writes.get());assertEquals(3,events());
    }
    @Test void markerCommitAcknowledgementLossNeverReturnsCallAuthority(){
        var operations=new JdbcExternalOperations(queue);source.committed=()->{throw new IllegalStateException("T07_MARKER_ACK_UNKNOWN");};assertThrows(IllegalStateException.class,()->operations.beginPrepared(lease,preparedRequest));source.committed=()->{};assertEquals("RECONCILING",state());assertEquals(1,markers.get());assertTrue(operations.beginPrepared(lease,preparedRequest).isEmpty());
    }
    void externalRollback(OperationToken token){assertEquals("RECONCILING",state());assertEquals("MARKED",jdbc.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,token.operationId()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?",Integer.class,output));assertNull(jdbc.queryForObject("SELECT ended_at FROM task_attempt WHERE task_id=? AND attempt=1",java.sql.Timestamp.class,lease.taskId()));assertEquals(2,events());}

    @Test void invalidTerminalReceiptReplaysWithoutCandidateOrSecondWriter(){invalid.set(true);var operations=new JdbcExternalOperations(queue);var token=operations.beginPrepared(lease,preparedRequest).orElseThrow();assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,preparedObservation));assertEquals(0,writes.get());assertEquals("FAILED",state());}

}
