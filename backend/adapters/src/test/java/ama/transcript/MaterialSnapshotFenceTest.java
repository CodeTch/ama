package ama.transcript;

import ama.contract.TaskContracts.SubjectRef;
import ama.task.SubjectFencePort;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialSnapshotFenceTest {
    @Test
    void advancingMaterialSnapshotAdvancesMeetingFenceWithTheSameRevision() {
        String meeting = "11111111-1111-4111-8111-111111111111";
        var subject = new TestSubject(new SubjectRef("MEETING", meeting), 7, 0, false);
        var fences = new TestFences(subject);
        var projections = new java.util.ArrayList<Object[]>();
        var jdbc = new JdbcTemplate() {
            @Override
            public <T> java.util.List<T> queryForList(String sql, Class<T> type, Object... args) {
                // This fixture exercises an ordinary material change without an active recording.
                assertTrue(java.util.Set.of("SELECT session_id FROM recording_processing WHERE meeting_id=? AND state IN ('WAITING_ARCHIVE','COPYING','TRANSCRIBING','ENHANCING','FAILED') LIMIT 2 FOR UPDATE","SELECT task_id FROM offline_material_processing WHERE meeting_id=? AND stage IN ('TRANSCRIBING','ENHANCING','ADOPTING') LIMIT 2 FOR UPDATE").contains(sql));
                assertEquals(String.class,type);
                assertEquals(java.util.List.of(meeting),java.util.Arrays.asList(args));
                return java.util.List.of();
            }
            @Override
            public int update(String sql, Object... args) {
                if(sql.startsWith("UPDATE meeting_search_projection"))projections.add(args);
                return 1;
            }
        };

        long revision = JdbcMaterials.advanceSnapshot(jdbc, fences, meeting, "snapshot-id", false);

        assertEquals(1, projections.size());
        assertEquals(8L, projections.getFirst()[0]);
        assertEquals(meeting, projections.getFirst()[1]);
        assertEquals(8, revision);
        assertEquals(8, fences.advanced.businessVersion());
        assertEquals(0, fences.advanced.cancelEpoch());
    }

    private record TestSubject(SubjectRef subject, long businessVersion, long cancelEpoch, boolean deleted)
            implements SubjectFencePort.LockedSubject {}

    private static final class TestFences implements SubjectFencePort {
        private final LockedSubject initial;
        private LockedSubject advanced;

        private TestFences(LockedSubject initial) {
            this.initial = initial;
        }

        @Override
        public LockedSubject create(SubjectRef subject, long businessVersion, long cancelEpoch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LockedSubject> lock(SubjectRef subject, boolean skipLocked) {
            return Optional.of(initial);
        }

        @Override
        public LockedSubject advance(LockedSubject locked, long businessVersion, long cancelEpoch, boolean deleted) {
            advanced = new TestSubject(locked.subject(), businessVersion, cancelEpoch, deleted);
            return advanced;
        }
    }
}
