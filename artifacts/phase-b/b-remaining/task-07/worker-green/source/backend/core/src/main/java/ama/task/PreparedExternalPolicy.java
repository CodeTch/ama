package ama.task;

import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import ama.task.ExternalOperationPort.*;

/** Constructor-registered DB-only policy; prepared payloads are data, never caller-selected writers. */
public interface PreparedExternalPolicy extends TaskDomainPolicy {
    record StoredResult(String state, String errorCode, TypedRef output) {}
    ExternalRequest requireRequest(TaskSpec actual, PreparedRequest request);
    void writeMarked(PreparedDomain held, ExecutionContext execution, PreparedRequest request);
    ExternalObservation verifyRemoteFact(TaskSpec actual, ExecutionContext execution, PreparedObservation observation);
    TaskResult writeCurrent(PreparedDomain held, ExecutionContext execution, PreparedObservation observation);
    void validateStoredResult(PreparedDomain held, ExecutionContext execution, PreparedObservation observation, TaskResult actual);
    /** Full immutable receipt/payload and actual stored output equivalence, not result-ID equality alone. */
    default boolean verifyReplay(PreparedDomain held, ExecutionContext execution, PreparedObservation observation, StoredResult stored) { return false; }
}
