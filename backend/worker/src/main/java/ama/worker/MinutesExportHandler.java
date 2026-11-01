package ama.worker;
import ama.contract.TaskContracts.*;import ama.export.MinutesExportProcess;import ama.task.*;import java.util.function.Supplier;
final class MinutesExportHandler implements WorkerLoop.ContextualPreparedHandler {
 private final JdbcTaskQueue queue;private final MinutesExportProcess converter;
 MinutesExportHandler(JdbcTaskQueue queue,MinutesExportProcess converter){this.queue=java.util.Objects.requireNonNull(queue);this.converter=java.util.Objects.requireNonNull(converter);}
 public Supplier<TaskResult> prepare(TaskLease lease,PreparationCancellation cancellation){return MinutesExportRuntime.prepare(queue,lease,converter,cancellation);}
}
