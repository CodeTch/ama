package ama.task;
import ama.contract.TaskContracts.TaskResult;
import java.util.function.Supplier;
/** Data/lifecycle wrapper only. Neither payload nor admission grants database authority. */
public interface PreparedCompletionEnvelope extends Supplier<TaskResult> {
 Supplier<TaskResult> payload();
 void admit();
}
