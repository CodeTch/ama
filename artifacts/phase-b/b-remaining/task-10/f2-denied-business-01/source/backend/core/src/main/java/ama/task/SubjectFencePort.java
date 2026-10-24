package ama.task;

import ama.contract.TaskContracts.SubjectRef;
import java.util.Optional;

/** All methods require the caller's transaction on the same DataSource. Fence precedes domain locks. */
public interface SubjectFencePort {
    interface LockedSubject {
        SubjectRef subject();
        long businessVersion();
        long cancelEpoch();
        boolean deleted();
    }

    /** Domain creation only, before inserting the root in this transaction. Duplicate identity is rejected. */
    LockedSubject create(SubjectRef subject, long businessVersion, long cancelEpoch);
    Optional<LockedSubject> lock(SubjectRef subject, boolean skipLocked);
    /** Caller has prelocked its domain root; both domain and fence changes must commit together. */
    LockedSubject advance(LockedSubject locked, long businessVersion, long cancelEpoch, boolean deleted);
}
