package ama.task;

import ama.contract.TaskContracts.*;
import java.util.Optional;

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
}
