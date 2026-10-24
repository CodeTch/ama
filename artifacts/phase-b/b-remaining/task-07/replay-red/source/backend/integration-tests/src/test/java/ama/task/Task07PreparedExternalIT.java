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
    final DataSource source = new Task08InheritedB06QueueSeamsIT.ProbeSource(Task07DataSource.create(), sql -> {
        if (failAudit.get() && sql.startsWith("INSERT INTO audit_event(")) throw new IllegalStateException("CONTROLLED_AUDIT_FAILURE");
        if (failFlush.get() && sql.startsWith("INSERT INTO task_outbox(")) throw new IllegalStateException("CONTROLLED_FLUSH_FAILURE");
    });
    final JdbcTemplate jdbc = new JdbcTemplate(source);
    final TransactionRunner tx = new TransactionRunner(source);
    final JdbcSubjectFences fences = new JdbcSubjectFences(source, Set.of("MEETING"));
    final JdbcMeetingAccess access = new JdbcMeetingAccess(source, tx, fences);
    final String owner = "prepared-" + UUID.randomUUID(), meeting = UUID.randomUUID().toString();
    final String input = UUID.randomUUID().toString(), output = UUID.randomUUID().toString();
    final Actor actor = new Actor(owner, Status.ACTIVE, Set.of(Role.USER));
    final TaskSpec spec = new TaskSpec("PREPARED_TEST", "PREPARED_" + UUID.randomUUID(),
            new SubjectRef("MEETING", meeting), Task08InheritedFixture.hash(input + "producer"),
            new TypedRef("TEST_INFO", input), Task08InheritedFixture.hash(input), 0, 0, 0, 3);
    final PreparedRequest preparedRequest=new PreparedRequest(){};
    final PreparedObservation preparedObservation=new PreparedObservation(){};
    final ExternalRequest wire=new ExternalRequest("T07_CALL",spec.producerKey(),Task08InheritedFixture.hash("logical"+input),Task08InheritedFixture.hash("execution"+input),"task07:"+input);
    final ExternalObservation fact=new ExternalObservation(ExternalOutcome.SUCCEEDED,"T07_RESULT:"+input,null,null,null);
    final AtomicInteger markers=new AtomicInteger();
    final AtomicInteger writes = new AtomicInteger(), validations = new AtomicInteger();
    final PreparedExternalPolicy policy = new PreparedExternalPolicy() {
        final JdbcTaskPreparations preparations = new JdbcTaskPreparations(source);
        public Optional<PreparedDomain> prepare(List<TaskSpec> specs, Acquisition purpose, boolean skip) {
            return access.tryLockIdentities(actor, List.of(), skip)
                    .map(held -> preparations.issue(specs, purpose, held, true));
        }
        public void requirePrepared(List<TaskSpec> specs, Acquisition purpose, PreparedDomain held) { preparations.require(specs, purpose, held); }
        public boolean permitsDomainEffects(PreparedDomain held) { return preparations.permitsEffects(held); }
        public boolean lockRoot(TaskSpec s, boolean skip) { throw new IllegalStateException("Authentic preparation required"); }
        public boolean lockRoot(TaskSpec s, boolean skip, PreparedDomain prepared) {
            var identities = (MeetingAccess.LockedIdentities) preparations.value(prepared);
            var held = access.lockPrepared(identities, meeting, skip);
            if (held.isEmpty()) return false;
            access.require(held.orElseThrow(), MeetingAccess.Action.EDIT);
            return !jdbc.queryForList("SELECT id FROM meeting_info_revision WHERE id=? FOR UPDATE" + (skip ? " SKIP LOCKED" : ""), String.class, input).isEmpty();
        }
        public void validateInput(TaskSpec s) {
            if (!s.equals(spec) || jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND revision=0 AND name='input'", Integer.class, input, meeting) != 1)
                throw new IllegalArgumentException("Invalid saved input");
        }
        public void validateOutput(TaskSpec s, TaskResult result) {
            validations.incrementAndGet();
            if (result.output() == null || !result.output().equals(new TypedRef("TEST_INFO", output))
                    || jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND revision=1 AND name='output'", Integer.class, output, meeting) != 1)
                throw new IllegalArgumentException("Actual output must exist with matching provenance");
            assertEquals("RECONCILING", state(), "terminal mutation must follow actual output validation");
        }
        public void validateExternalRequest(TaskSpec s, ExternalRequest request) { if(!request.requestManifest().equals("task07:"+input))throw new IllegalArgumentException("Request mismatch"); }
        public ExternalRequest requireRequest(TaskSpec s, PreparedRequest request){if(request!=preparedRequest||!s.equals(spec))throw new IllegalArgumentException("Foreign request");return wire;}
        public void writeMarked(PreparedDomain held,ExecutionContext context,PreparedRequest request){preparations.requireExternal(held,context);markers.incrementAndGet();}
        public ExternalObservation verifyRemoteFact(TaskSpec s,ExecutionContext context,PreparedObservation observation){if(observation!=preparedObservation||!s.equals(spec))throw new IllegalArgumentException("Foreign observation");return fact;}
        public TaskResult writeCurrent(PreparedDomain held,ExecutionContext context,PreparedObservation observation){preparations.requireExternal(held,context);return writeOutput();}
        public void validateStoredResult(PreparedDomain held,ExecutionContext context,PreparedObservation observation,TaskResult result){preparations.requireExternal(held,context);assertEquals(output,result.output().id());}
        public boolean verifyReplay(PreparedDomain held,ExecutionContext context,PreparedObservation observation,TaskResult stored){preparations.requireExternal(held,context);return observation==preparedObservation&&stored.output()!=null&&stored.output().id().equals(output)&&jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND name='output'",Integer.class,output,meeting)==1;}
    
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
        boolean settled=false;for(int i=0;i<32&&!settled;i++){pool.recoverSettlements(1);settled=jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,permit.id());}
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

}
