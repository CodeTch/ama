package ama.task;

import ama.contract.TaskContracts.*;
import java.util.Optional;

public interface TaskQueue {
    String enqueue(TaskSpec spec);
    Optional<TaskLease> claim(String pool, String workerId);
    boolean renew(TaskLease lease);
    boolean complete(TaskLease lease, TaskResult result);
    boolean completeWithEffects(TaskLease lease, TaskResult result, Runnable effects);
}
