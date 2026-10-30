package ama.transcript;

import ama.contract.TaskContracts.SubjectRef;
import ama.task.SubjectFencePort;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialSnapshotFenceTest {
    @Test
    void advancingMaterialSnapshotAdvancesMeetingFenceWithTheSameRevision() {
        String meeting = "11111111-1111-4111-8111-111111111111";
        var subject = new TestSubject(new SubjectRef("MEETING", meeting), 7, 0, false);
        var fences = new TestFences(subject);
        var jdbc = new JdbcTemplate() {
            @Override
            public int update(String sql, Object... args) {
                return 1;
            }
        };

        long revision = JdbcMaterials.advanceSnapshot(jdbc, fences, meeting, "snapshot-id", false);

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
