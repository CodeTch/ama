package ama.task;

import ama.contract.OutboxContracts.*;
import ama.contract.TaskContracts.SubjectRef;
import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Prepared for the centrally frozen V010. Never migrates, resets, or repairs an existing fixture. */
class Task08InheritedOutboxFenceIT {
    DataSource source;
    JdbcTemplate jdbc;
    TransactionRunner tx;
    JdbcSubjectFences fences;
    JdbcOutbox outbox;
    SubjectRef first;
    SubjectRef second;

    @BeforeEach void prepareDataAfterApprovedMigration() {
        source = Task08InheritedResources.source();
        jdbc = new JdbcTemplate(source);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='010' AND success=1", Integer.class),
                "Requires a new owned fixture with centrally frozen, ordered V010 already applied");
        tx = new TransactionRunner(source);
        fences = new JdbcSubjectFences(source, Set.of("MEETING"));
        outbox = new JdbcOutbox(source, Map.of("MEETING", event -> {
            if (!event.subject().kind().equals("MEETING") || !event.subject().id().equals(event.reference().id())) {
                throw new IllegalArgumentException("Cross-subject reference");
            }
            Long version = jdbc.queryForObject("SELECT revision FROM meeting WHERE id=?", Long.class, event.reference().id());
            if (version != event.subjectRevision()) throw new IllegalArgumentException("Wrong event version");
        }));
        first = createMeeting(); second = createMeeting();
    }

    SubjectRef createMeeting() {
        var subject = new SubjectRef("MEETING", UUID.randomUUID().toString());
        String owner = "t08-inherited-test-" + UUID.randomUUID();
        tx.required(() -> {
            jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,session_generation,verified_at) VALUES (?,?,'ACTIVE',0,UTC_TIMESTAMP(6))", owner, owner);
            fences.create(subject, 0, 0);
            jdbc.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", subject.id(), owner);
            return null;
        });
        return subject;
    }

    EventDraft event(SubjectRef subject) {
        return new EventDraft("MEETING_CHANGED", null, subject, 0, new Reference("MEETING", subject.id()));
    }

    long cursor() { return jdbc.queryForObject("SELECT next_id-1 FROM task_outbox_sequence WHERE id=1", Long.class); }

    @Test void delayedLowerCommitCannotBeSkippedByCursorReader() throws Exception {
        long baseline = cursor();
        var firstFlushed = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondReady = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> tx.required(() -> {
                fences.lock(first, false).orElseThrow();
                jdbc.queryForObject("SELECT revision FROM meeting WHERE id=? FOR UPDATE", Long.class, first.id());
                outbox.append(event(first)); outbox.flush(); firstFlushed.countDown();
                if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
                return null;
            }));
            assertTrue(firstFlushed.await(5, TimeUnit.SECONDS));
            var b = workers.submit(() -> tx.required(() -> {
                fences.lock(second, false).orElseThrow();
                jdbc.queryForObject("SELECT revision FROM meeting WHERE id=? FOR UPDATE", Long.class, second.id());
                outbox.append(event(second)); secondReady.countDown(); outbox.flush();
                return null;
            }));
            try {
                assertTrue(secondReady.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> b.get(250, TimeUnit.MILLISECONDS));
                assertTrue(outbox.eventsAfter(baseline, 10).isEmpty());
            } finally { releaseFirst.countDown(); }
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        var delivered = outbox.eventsAfter(baseline, 10);
        assertEquals(2, delivered.size());
        assertEquals(baseline + 1, delivered.getFirst().sequence());
        assertEquals(first, delivered.getFirst().event().subject());
        assertEquals(baseline + 2, delivered.getLast().sequence());
        assertEquals(second, delivered.getLast().event().subject());
    }

    @Test void rollbackReturnsSequenceAndLeavesNoEventOrDomainEffect() {
        long baseline = cursor();
        assertThrows(IllegalStateException.class, () -> tx.required(() -> {
            var held = fences.lock(first, false).orElseThrow();
            jdbc.queryForObject("SELECT revision FROM meeting WHERE id=? FOR UPDATE", Long.class, first.id());
            fences.advance(held, 1, 0, false);
            jdbc.update("UPDATE meeting SET revision=1 WHERE id=?", first.id());
            outbox.append(new EventDraft("MEETING_CHANGED", null, first, 1, new Reference("MEETING", first.id())));
            outbox.flush();
            throw new IllegalStateException("Rollback after the final flush");
        }));
        assertEquals(baseline, cursor());
        assertTrue(outbox.eventsAfter(baseline, 10).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT revision FROM meeting WHERE id=?", Long.class, first.id()));
        tx.required(() -> { outbox.append(event(first)); outbox.flush(); return null; });
        assertEquals(baseline + 1, outbox.eventsAfter(baseline, 1).getFirst().sequence());
    }

    @Test void missingFlushAndForeignCommitCallbacksAreRejectedWithoutEffects() {
        long baseline = cursor();
        assertThrows(IllegalStateException.class, () -> tx.required(() -> { outbox.append(event(first)); return null; }));
        assertThrows(IllegalStateException.class, () -> tx.required(() -> {
            outbox.append(event(first));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {});
            outbox.flush(); return null;
        }));
        assertThrows(IllegalStateException.class, () -> tx.required(() -> {
            outbox.append(event(first)); outbox.flush();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    jdbc.update("UPDATE meeting SET revision=99 WHERE id=?", first.id());
                }
            });
            return null;
        }));
        assertEquals(baseline, cursor());
        assertEquals(0, jdbc.queryForObject("SELECT revision FROM meeting WHERE id=?", Long.class, first.id()));
        tx.required(() -> { outbox.append(event(first)); outbox.flush(); return null; });
        assertEquals(1, outbox.eventsAfter(baseline, 10).size(), "Rollback must release transaction-scoped buffer");
    }

    @Test void tombstoneCannotBeReinitializedOrRevived() {
        tx.required(() -> {
            var held = fences.lock(first, false).orElseThrow();
            jdbc.queryForObject("SELECT revision FROM meeting WHERE id=? FOR UPDATE", Long.class, first.id());
            fences.advance(held, 1, 1, true);
            jdbc.update("UPDATE meeting SET revision=1,cancel_epoch=1,lifecycle='DELETED' WHERE id=?", first.id());
            return null;
        });
        assertThrows(RuntimeException.class, () -> tx.required(() -> { fences.create(first, 0, 0); return null; }));
        assertThrows(IllegalArgumentException.class, () -> tx.required(() -> {
            fences.advance(fences.lock(first, false).orElseThrow(), 2, 2, false); return null;
        }));
        tx.required(() -> { assertTrue(fences.lock(first, false).orElseThrow().deleted()); return null; });
    }

    @Test void missingSubjectAndUnregisteredReferencesNeverCreateRows() {
        var missing = new SubjectRef("MEETING", UUID.randomUUID().toString());
        tx.required(() -> {
            assertTrue(fences.lock(missing, true).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> outbox.append(new EventDraft("MEETING_CHANGED", null, first, 0,
                    new Reference("UNREGISTERED", first.id()))));
            return null;
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM task_subject WHERE subject_id=?", Integer.class, missing.id()));
    }

    @Test void domainLocksAndNewEventsAreRejectedAfterFinalFlush() {
        long baseline = cursor();
        assertThrows(IllegalStateException.class, () -> tx.required(() -> {
            outbox.append(event(first)); outbox.flush(); fences.lock(second, false); return null;
        }));
        assertThrows(IllegalStateException.class, () -> tx.required(() -> {
            outbox.append(event(first)); outbox.flush(); outbox.append(event(second)); return null;
        }));
        assertEquals(baseline, cursor());
    }

    @Test void concurrentGlobalProducersCommitOneContinuousPrefix() throws Exception {
        var subjects = new java.util.ArrayList<SubjectRef>();
        for (int n=0;n<20;n++) subjects.add(createMeeting());
        long baseline=cursor();
        try(var workers=Executors.newFixedThreadPool(8)) {
            var futures=new java.util.ArrayList<Future<?>>();
            for(var subject:subjects) futures.add(workers.submit(()->tx.required(()->{
                fences.lock(subject,false).orElseThrow();
                jdbc.queryForObject("SELECT id FROM meeting WHERE id=? FOR UPDATE",String.class,subject.id());
                outbox.append(event(subject));outbox.flush();return null;
            })));
            for(var future:futures) future.get(15,TimeUnit.SECONDS);
        }
        var events=outbox.eventsAfter(baseline,30);assertEquals(20,events.size());
        for(int n=0;n<20;n++)assertEquals(baseline+n+1,events.get(n).sequence());
        assertEquals(20,events.stream().map(e->e.event().subject()).distinct().count());
    }
}
