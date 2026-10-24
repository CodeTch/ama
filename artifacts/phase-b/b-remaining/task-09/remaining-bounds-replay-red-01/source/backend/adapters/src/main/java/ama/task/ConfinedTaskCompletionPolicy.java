package ama.task;
import ama.contract.TaskContracts.TaskResult;
import java.util.function.Supplier;
interface ConfinedTaskCompletionPolicy extends TaskDomainPolicy {
 CompletionInvocation bindCompletion(JdbcTaskQueue.AfterTask scope,Supplier<TaskResult> carrier);
}
interface CompletionInvocation {
 TaskResult write();
 void validateActual(TaskResult returned);
}
enum CompletionPhase { BINDING,BOUND,WRITING,WRITTEN,VALIDATING,VALIDATED,SETTLED,CLOSED }
