package ama.recording;

import ama.contract.RecordingContracts;
import ama.db.MysqlDataSources;
import ama.db.TransactionRunner;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real local MySQL proof in a new owned schema; never resets shared or historical fixtures. */
class RecordingProtocolIT {
    static DataSource source;
    static JdbcTemplate jdbc;
    @BeforeAll static void schema() {
        String url = System.getenv("AMA_C_RECORDING_DATABASE_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))
            throw new IllegalArgumentException("Fresh owned local recording schema required");
        source = MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }
    RecordingService service() { return new RecordingService(new JdbcRecordingPersistence(source)); }
    @Test void concurrentAdmissionReplaysTheOnlyActiveSessionForTheMeeting()throws Exception {
        String meeting=java.util.UUID.randomUUID().toString();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{start.await();return service().open(meeting,"local");});
            var second=pool.submit(()->{start.await();return service().open(meeting,"local");});
            start.countDown();var session=first.get(15,java.util.concurrent.TimeUnit.SECONDS);assertEquals(session,second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(session,service().open(meeting,"another-default"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recording_session WHERE meeting_id=?",Integer.class,meeting));
            service().accept(session.sessionId(),1,chunk(session,1));service().finish(session.sessionId(),1,Map.of(session.partId(),1L));
            assertNotEquals(session.sessionId(),service().open(meeting,"local").sessionId());
        }
    }
    RecordingContracts.Chunk chunk(RecordingContracts.Session s, long seq) {
        return new RecordingContracts.Chunk(s.partId(), s.writerEpoch(), seq, (seq-1)*2, 2, new byte[]{1,2,3,(byte)seq});
    }
    @Test void acknowledgedPcmSurvivesNewServiceAndExactDuplicate() {
        var original = service(); var s = original.open(java.util.UUID.randomUUID().toString(), "local");
        assertEquals(1, original.accept(s.sessionId(), 1, chunk(s,1)).highestContiguousSeq());
        assertArrayEquals(chunk(s,1).pcm(), jdbc.queryForObject("SELECT pcm FROM recording_chunk WHERE session_id=?", byte[].class, s.sessionId()));
        var recovered = service(); assertEquals(s, recovered.session(s.sessionId()));
        assertEquals(1, recovered.ack(s.sessionId(), s.partId()).highestContiguousSeq());
        assertEquals(1, recovered.accept(s.sessionId(), 1, chunk(s,1)).highestContiguousSeq());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?", Integer.class, s.sessionId()));
    }
    @Test void pauseFinishAndGapSurviveRecovery() {
        var original = service(); var s = original.open(java.util.UUID.randomUUID().toString(), "local");
        original.accept(s.sessionId(), 1, chunk(s,2)); original.pause(s.sessionId(),1);
        var recovered = service(); assertThrows(RecordingService.Paused.class, () -> recovered.accept(s.sessionId(),1,chunk(s,1)));
        recovered.resume(s.sessionId(),1);
        assertThrows(RecordingService.Gap.class, () -> recovered.finish(s.sessionId(),1,Map.of(s.partId(),2L)));
        assertEquals(2, recovered.accept(s.sessionId(),1,chunk(s,1)).highestContiguousSeq());
        recovered.finish(s.sessionId(),1,Map.of(s.partId(),2L));
        assertThrows(IllegalStateException.class, () -> service().accept(s.sessionId(),1,chunk(s,3)));
        assertEquals("FINISHED", jdbc.queryForObject("SELECT state FROM recording_session WHERE session_id=?",String.class,s.sessionId()));
    }
    @Test void callerRollbackCannotCreateAPhantomAck() {
        var original = service(); var s = original.open(java.util.UUID.randomUUID().toString(), "local");
        assertThrows(IllegalStateException.class, () -> new TransactionRunner(source).required(() -> {
            original.accept(s.sessionId(),1,chunk(s,1)); throw new IllegalStateException("controlled caller rollback");
        }));
        assertEquals(0, original.ack(s.sessionId(),s.partId()).highestContiguousSeq());
        assertEquals(0, service().ack(s.sessionId(),s.partId()).highestContiguousSeq());
        assertEquals(1, original.accept(s.sessionId(),1,chunk(s,1)).highestContiguousSeq());
    }
    @Test void legacyMetadataWithoutPcmIsNeverAcknowledged() {
        var original = service(); var s = original.open(java.util.UUID.randomUUID().toString(), "local");
        jdbc.update("INSERT INTO recording_chunk(session_id,part_id,seq,sample_start,sample_count,sha256,bytes,persisted_at) VALUES (?,?,1,0,2,?,4,UTC_TIMESTAMP(6))",s.sessionId(),s.partId(),chunk(s,1).sha256());
        assertThrows(IllegalStateException.class, () -> original.ack(s.sessionId(),s.partId()));
    }
    @Test void twoIndependentServicesSerializeExactDuplicateAndConflict() throws Exception {
        var original=service();var s=original.open(java.util.UUID.randomUUID().toString(),"local");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(() -> {start.await();return service().accept(s.sessionId(),1,chunk(s,1));});
            var second=pool.submit(() -> {start.await();return service().accept(s.sessionId(),1,chunk(s,1));});
            start.countDown();assertEquals(1,first.get(15,java.util.concurrent.TimeUnit.SECONDS).highestContiguousSeq());
            assertEquals(1,second.get(15,java.util.concurrent.TimeUnit.SECONDS).highestContiguousSeq());
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,s.sessionId()));
        var conflicting=new RecordingContracts.Chunk(s.partId(),1,1,0,2,new byte[]{9,9,9,9});
        assertThrows(RecordingService.HashConflict.class,() -> service().accept(s.sessionId(),1,conflicting));
        assertArrayEquals(chunk(s,1).pcm(),jdbc.queryForObject("SELECT pcm FROM recording_chunk WHERE session_id=?",byte[].class,s.sessionId()));
    }
    @Test void multiplePartsAndNewEpochSurviveRestartAndCannotBeOmitted() {
        var original=service();var first=original.open(java.util.UUID.randomUUID().toString(),"local");
        original.accept(first.sessionId(),1,chunk(first,1));original.pause(first.sessionId(),1);
        var second=service().nextPart(first.sessionId(),1);
        assertNotEquals(first.partId(),second.partId());assertEquals(2,second.writerEpoch());
        assertThrows(RecordingService.StaleWriter.class,() -> service().accept(first.sessionId(),1,chunk(first,2)));
        service().accept(second.sessionId(),2,chunk(second,1));
        assertEquals(1,service().ack(first.sessionId(),first.partId()).highestContiguousSeq());
        assertThrows(RecordingService.Gap.class,() -> service().finish(first.sessionId(),2,Map.of(second.partId(),1L)));
        service().finish(first.sessionId(),2,Map.of(first.partId(),1L,second.partId(),1L));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM recording_part WHERE session_id=?",Integer.class,first.sessionId()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,first.sessionId()));
    }
}
