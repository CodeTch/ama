package ama.task;

import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.TaskLease;
import ama.contract.TaskContracts.TaskSpec;
import java.util.Optional;

public interface ExternalOperationPort {
    /** Reject ambient transactions. The marker must ACTUALLY commit before this token can authorize any call. */
    Optional<OperationToken> beginExternal(TaskLease lease, ExternalRequest request);
    /** Independent due-operation takeover; never sends or creates another execution for UNKNOWN. */
    Optional<OperationToken> claimReconciliation(String owner);
    boolean renew(OperationToken token);
    /** Domain rows prelocked before task/operation. Current independent token, not old task lease, authorizes observations. */
    ObservationResult observe(OperationToken token, ExternalObservation observation, Runnable prelockedDomainEffects);
    /** Explicit user retry only: old current execution must be FAILED with complete verified zero-effect proof. */
    String retryAfterZeroEffect(String operationId, long expectedExecutionNo, TaskSpec newTask, ExternalRequest request);
}
