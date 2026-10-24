package ama.worker;
import ama.contract.TaskContracts.TaskLease;
import ama.contract.ExternalContracts.*;
import ama.task.ExternalOperationPort.*;
import java.util.concurrent.Callable;
/** One external owner; transport executes outside every transaction and context monitor. */
public interface ExternalTaskExecution {
 TaskLease lease();
 OperationToken token();
 boolean begin(PreparedRequest request) throws InterruptedException;
 boolean send(Callable<PreparedObservation> transport) throws Exception;
 ObservationResult observe(PreparedObservation observation) throws InterruptedException;
}
