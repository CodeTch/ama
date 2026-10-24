package ama.task;

import ama.access.MeetingAccess;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.ExternalRequest;
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
class Task06InheritedPreparedCompletionIT {
    final AtomicBoolean failFlush = new AtomicBoolean(), failAudit = new AtomicBoolean(), failEvent = new AtomicBoolean();
    final DataSource source = new B06QueueSeamsIT.ProbeSource(MysqlTestDatabase.dataSource(), sql -> {
        if (failAudit.get() && sql.startsWith("INSERT INTO audit_event(")) throw new IllegalStateException("CONTROLLED_AUDIT_FAILURE");
        if (failFlush.get() && sql.startsWith("INSERT INTO task_outbox(")) throw new IllegalStateException("CONTROLLED_FLUSH_FAILURE");
    });
    final JdbcTemplate jdbc = new JdbcTemplate(source);
    final TransactionRunner tx = new TransactionRunner(source);
    final JdbcSubjectFences fences = new JdbcSubjectFences(source, Set.of("MEETING"));
    final JdbcMeetingAccess access = new JdbcMeetingAccess(source, tx, fences);
    final String owner = "task06-fix-prepared-" + UUID.randomUUID(), meeting = UUID.randomUUID().toString();
    final String input = UUID.randomUUID().toString(), output = UUID.randomUUID().toString();
    final Actor actor = new Actor(owner, Status.ACTIVE, Set.of(Role.USER));
    final TaskSpec spec = new TaskSpec("PREPARED_TEST", "TASK06_FIX_PREPARED_" + UUID.randomUUID(),
            new SubjectRef("MEETING", meeting), TaskFixture.hash(input + "producer"),
            new TypedRef("TEST_INFO", input), TaskFixture.hash(input), 0, 0, 0, 3);
    final AtomicInteger writes = new AtomicInteger(), validations = new AtomicInteger();
    final TaskDomainPolicy policy = new TaskDomainPolicy() {
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
            assertEquals("RUNNING", state(), "terminal mutation must follow actual output validation");
        }
        public void validateExternalRequest(TaskSpec s, ExternalRequest request) { throw new IllegalArgumentException("No external authority"); }
    };
    final JdbcOutbox outbox = new JdbcOutbox(source, Map.of("TEST_INFO", event -> {
        if (failEvent.get()) throw new IllegalArgumentException("CONTROLLED_EVENT_FAILURE");
        if (!event.subject().equals(spec.subject()) || !event.reference().id().equals(input)) throw new IllegalArgumentException("Invalid event provenance");
    }));
    final JdbcTaskQueue queue = new JdbcTaskQueue(source, fences, outbox, Map.of(spec.type(), policy));
    TaskLease lease;

    @BeforeAll static void migrateOwnedEmptyFixture() {
        var source = MysqlTestDatabase.dataSource();
        // Forward schema guard only; no migration invocation.
        var history = new JdbcTemplate(source).queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
        assertEquals(14, history.size());
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
    @Test void newOutputIsWrittenBeforeValidationAndCommitsWithTaskAndAttempt() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?", Integer.class, output));
        assertTrue(assertDoesNotThrow(() -> complete(this::writeOutput)));
        assertEquals(1, writes.get()); assertEquals(1, validations.get());
        assertEquals("SUCCEEDED", state());
        assertEquals(output, jdbc.queryForObject("SELECT output_id FROM durable_task WHERE id=?", String.class, lease.taskId()));
        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT outcome FROM task_attempt WHERE task_id=? AND attempt=1", String.class, lease.taskId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?", Integer.class, output));
        assertEquals(2, events());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?", Integer.class, meeting));
        assertFalse(complete(() -> { fail("duplicate completion wrote output"); return null; }));
    }
    @Test void invalidOutputRollsBackItsRowAndTaskAttemptAndOutbox() {
        assertThrows(IllegalArgumentException.class, () -> complete(() -> { writeOutput(); return new TaskResult(Outcome.SUCCEEDED, new TypedRef("OTHER_KIND", output), null); }));
        assertEquals(1, writes.get()); rolledBack();
    }
    @Test void callbackThrowRollsBackItsRowAndTaskAttemptAndOutbox() {
        assertThrows(IllegalStateException.class, () -> complete(() -> { writeOutput(); throw new IllegalStateException("CONTROLLED_CALLBACK_FAILURE"); }));
        assertEquals(1, writes.get()); rolledBack();
    }
    @Test void nullResultRollsBackItsRowAndTaskAttemptAndOutbox() {
        assertThrows(NullPointerException.class, () -> complete(() -> { writeOutput(); return null; }));
        assertEquals(1, writes.get()); rolledBack();
    }
    @Test void flushFailureRollsBackOutputTaskAttemptAndOutbox() {
        long cursor = jdbc.queryForObject("SELECT next_id FROM task_outbox_sequence WHERE id=1", Long.class);
        failFlush.set(true);
        assertThrows(IllegalStateException.class, () -> complete(this::writeOutput));
        assertEquals(1, writes.get()); rolledBack();
        assertEquals(cursor, jdbc.queryForObject("SELECT next_id FROM task_outbox_sequence WHERE id=1", Long.class));
    }
    @Test void expiredLeaseSuppressesCallback() {
        jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", lease.taskId());
        assertFalse(complete(this::writeOutput)); assertEquals(0, writes.get()); rolledBack();
    }
    @Test void staleVersionSuppressesCallback() {
        tx.required(() -> { var held = fences.lock(spec.subject(), false).orElseThrow(); jdbc.update("UPDATE meeting SET revision=1 WHERE id=?", meeting); fences.advance(held, 1, 0, false); return null; });
        assertFalse(complete(this::writeOutput)); assertEquals(0, writes.get()); rolledBack();
    }
    @Test void revokedOwnerSuppressesCallback() {
        jdbc.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?", owner);
        assertThrows(MeetingAccess.AccessDeniedException.class, () -> complete(this::writeOutput));
        assertEquals(0, writes.get()); rolledBack();
    }
    @Test void invalidActualInputSuppressesCallback() {
        jdbc.update("UPDATE meeting_info_revision SET name='changed' WHERE id=?", input);
        assertThrows(IllegalArgumentException.class, () -> complete(this::writeOutput));
        assertEquals(0, writes.get()); rolledBack();
    }
    @Test void oldRunnableContractStillValidatesBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> queue.completeWithEffects(lease, new TaskResult(Outcome.SUCCEEDED, new TypedRef("TEST_INFO", output), null), this::writeOutput));
        assertEquals(0, writes.get()); rolledBack();
    }
    @Test void auditFailureRollsBackOutputTaskAttemptAndOutbox() {
        failAudit.set(true);
        assertThrows(IllegalStateException.class, () -> complete(this::writeOutput));
        assertEquals(1, writes.get()); rolledBack();
    }
    @Test void eventValidationFailureRollsBackOutputAuditTaskAndAttempt() {
        failEvent.set(true);
        assertThrows(IllegalArgumentException.class, () -> complete(this::writeOutput));
        assertEquals(1, writes.get()); rolledBack();
    }
    @Test void cancelledEpochSuppressesCallback() {
        tx.required(() -> { var held = fences.lock(spec.subject(), false).orElseThrow(); jdbc.update("UPDATE meeting SET cancel_epoch=1 WHERE id=?", meeting); fences.advance(held, 0, 1, false); return null; });
        assertFalse(complete(this::writeOutput)); assertEquals(0, writes.get()); rolledBack();
    }
    @Test void forgedOwnerAndAttemptSuppressCallback() {
        var wrongOwner = new TaskLease(lease.taskId(), lease.attempt(), "FOREIGN_WORKER", lease.expiresAt(), spec);
        var wrongAttempt = new TaskLease(lease.taskId(), lease.attempt() + 1, lease.leaseOwner(), lease.expiresAt(), spec);
        assertFalse(queue.completeWithEffects(wrongOwner, this::writeOutput));
        assertFalse(queue.completeWithEffects(wrongAttempt, this::writeOutput));
        assertEquals(0, writes.get()); rolledBack();
    }
    @Test void nullCallbackIsRejectedWithoutAnyMutation() {
        assertThrows(NullPointerException.class, () -> queue.completeWithEffects(lease, (Supplier<TaskResult>) null));
        assertThrows(NullPointerException.class, () -> queue.completeWithEffects(null, this::writeOutput));
        assertEquals(0, writes.get()); rolledBack();
    }
    @Test void callbackMayAdvanceItsPrelockedDomainBeforeTerminalMutation() {
        assertTrue(complete(() -> {
            var result = writeOutput();
            jdbc.update("UPDATE meeting SET revision=1 WHERE id=?", meeting);
            jdbc.update("UPDATE task_subject SET business_version=1 WHERE subject_kind='MEETING' AND subject_id=?", meeting);
            return result;
        }));
        assertEquals("SUCCEEDED", state());
        assertEquals(1L, jdbc.queryForObject("SELECT business_version FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?", Long.class, meeting));
    }
    @Test void ambientTransactionOwnsFinalFlushAndRollback() {
        assertThrows(IllegalStateException.class, () -> tx.required(() -> {
            assertTrue(complete(this::writeOutput));
            assertEquals(1, events(), "queue must not flush the caller's transaction early");
            outbox.flush();
            assertEquals(2, events());
            throw new IllegalStateException("CALLER_ABORT");
        }));
        assertEquals(1, writes.get()); rolledBack();
    }
    @Test void actualWorkerPreparesOutsideTransactionAndCommitsExactlyOnce() throws Exception {
        jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", lease.taskId());
        var done = new CountDownLatch(1); var preparedCalls = new AtomicInteger(); var ordinaryCalls = new AtomicInteger();
        TaskQueue observed = new TaskQueue() {
            public String enqueue(TaskSpec s) { return queue.enqueue(s); }
            public Optional<TaskLease> claim(String pool, String owner) { return queue.claim(pool, owner); }
            public boolean renew(TaskLease l) { return queue.renew(l); }
            public boolean complete(TaskLease l, TaskResult result) { ordinaryCalls.incrementAndGet(); try { return queue.complete(l, result); } finally { done.countDown(); } }
            public boolean completeWithEffects(TaskLease l, TaskResult result, Runnable effects) { throw new AssertionError("Wrong API"); }
            public boolean completeWithEffects(TaskLease l, Supplier<TaskResult> effects) {
                preparedCalls.incrementAndGet(); try { return queue.completeWithEffects(l, effects); } finally { done.countDown(); }
            }
        };
        var preparationOutside = new AtomicBoolean();
        WorkerLoop.PreparedHandler handler = l -> {
            preparationOutside.set(!TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0, writes.get());
            return this::writeOutput;
        };
        try (var worker = WorkerLoop.prepared(observed, Map.of(spec.pool(), 1), Map.of(spec.type(), handler), "ACTUAL_WORKER")) {
            worker.pollOnce(spec.pool());
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
        assertTrue(preparationOutside.get()); assertEquals(1, preparedCalls.get()); assertEquals(0, ordinaryCalls.get());
        assertEquals(1, writes.get()); assertEquals("SUCCEEDED", state()); assertEquals(2, events());
        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT outcome FROM task_attempt WHERE task_id=? AND attempt=2", String.class, lease.taskId()));
    }

 @AfterEach void closeOwnedFixture()throws Exception {failAudit.set(false);failFlush.set(false);failEvent.set(false);tx.required(()->{var ids=jdbc.queryForList("SELECT id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? AND pool=? FOR UPDATE",String.class,meeting,spec.pool());for(String id:ids){jdbc.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='FAILED',error_code='TASK06_FIXTURE_CLOSED' WHERE task_id=? AND ended_at IS NULL",id);jdbc.update("UPDATE durable_task SET state='FAILED',lease_owner=NULL,expires_at=NULL WHERE id=? AND state IN ('READY','RUNNING')",id);}return null;});var root=java.nio.file.Path.of(System.getenv("AMA_TASK05_ROOT")).getParent().getParent().resolve("artifacts/phase-b/b-remaining/task-06/fix-01/inherited-queue");java.nio.file.Files.createDirectories(root);java.nio.file.Files.writeString(root.resolve(meeting+".json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("owner",owner,"meeting",meeting,"pool",spec.pool(),"tasks",jdbc.queryForList("SELECT id,state FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? AND pool=?",meeting,spec.pool()),"scope","controlled forward inherited queue policy")));}
}
