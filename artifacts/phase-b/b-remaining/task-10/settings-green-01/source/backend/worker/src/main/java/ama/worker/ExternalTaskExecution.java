package ama.worker;
import ama.contract.TaskContracts.TaskLease;
import ama.contract.ExternalContracts.*;
import ama.task.ExternalOperationPort.*;
import java.util.concurrent.Callable;
/** One external owner; transport executes outside every transaction and context monitor. */
public interface ExternalTaskExecution {
 /** Read-only physical-I/O cancellation; an implementation is not send authority. */
 default ama.task.PreparationCancellation cancellation(){throw new IllegalStateException("External cancellation not installed");}
 TaskLease lease();
 OperationToken token();
 boolean begin(PreparedRequest request) throws InterruptedException;
 boolean send(Callable<PreparedObservation> transport) throws Exception;
 ObservationResult observe(PreparedObservation observation) throws InterruptedException;
}
