package ama.worker;
import ama.contract.TaskContracts.*;
import ama.task.*;
import java.util.Objects;
import java.util.function.Supplier;
/** Bounded local archive pool; the durable queue owns registration and final completion. */
public final class RecordingArchiveHandler implements WorkerLoop.ContextualPreparedHandler {
 private final JdbcTaskQueue queue;
 public RecordingArchiveHandler(JdbcTaskQueue queue){this.queue=Objects.requireNonNull(queue);}
 @Override public Supplier<TaskResult> prepare(TaskLease lease,PreparationCancellation cancellation){return RecordingArchiveRuntime.prepare(queue,lease,cancellation);}
}
