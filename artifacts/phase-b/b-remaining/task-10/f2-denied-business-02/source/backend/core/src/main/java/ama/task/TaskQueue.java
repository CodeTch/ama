package ama.task;

import ama.contract.TaskContracts.*;
import java.util.Optional;
import java.util.function.Supplier;

public interface TaskQueue {
    String enqueue(TaskSpec spec);
    /** Explicit adoption only; implementations without an authentic preparation protocol reject it. */
    default String enqueue(TaskSpec spec, TaskDomainPolicy.PreparedDomain alreadyHeld) {
        throw new DomainPreparationUnavailableException();
    }
    Optional<TaskLease> claim(String pool, String workerId);
    boolean renew(TaskLease lease);
    boolean complete(TaskLease lease, TaskResult result);
    boolean completeWithEffects(TaskLease lease, TaskResult result, Runnable effects);
    /**
     * Atomically write bounded DB-only effects under a freshly validated lease and authentic
     * registered domain policy, then validate the actual returned output before finishing.
     * Preparation, parsing and I/O must already be complete. The callback grants no authority
     * and must use only the prelocked domain; it must not flush or start external work.
     * Implementations without this transaction protocol reject without invoking the callback.
     */
    default boolean completeWithEffects(TaskLease lease, Supplier<TaskResult> effects) {
        throw new UnsupportedOperationException("Atomic prepared completion unavailable");
    }
}
