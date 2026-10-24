package ama.task;

import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.TaskLease;
import ama.contract.TaskContracts.TaskSpec;
import java.util.Optional;

public interface ExternalOperationPort {
    /** Reject ambient transactions. The marker must ACTUALLY commit before this token can authorize any call. */
    Optional<OperationToken> beginExternal(TaskLease lease, ExternalRequest request);
    interface PreparedRequest {}
    interface PreparedObservation {}
    interface ExecutionContext {}
    default Optional<OperationToken> beginPrepared(TaskLease lease, PreparedRequest request) {
        throw new IllegalStateException("Prepared external policy not installed");
    }
    default ObservationResult observePrepared(OperationToken token, PreparedObservation observation) {
        throw new IllegalStateException("Prepared external policy not installed");
    }
    default ObservationResult observePreparedFact(OperationToken token, PreparedObservation observation) {
        throw new IllegalStateException("Prepared external policy not installed");
    }
    /** Independent due-operation takeover; never sends or creates another execution for UNKNOWN. */
    Optional<OperationToken> claimReconciliation(String owner);
    boolean renew(OperationToken token);
    /**
     * Independent short transaction; rejects ambient transactions before any effects. Domain policy
     * prelocks roots before tasks/operation. The current independent token authorizes business effects;
     * stale-token authoritative contradictions can only be recorded/quarantined, never regain authority.
     * Generation changes restart the whole owned transaction, bounded to four discovery attempts.
     * @throws ObservationRetryRequiredException retain and resubmit the SAME authoritative fact outside
     * a transaction; this is not an acknowledgment or permission to repeat the external request.
     */
    ObservationResult observe(OperationToken token, ExternalObservation observation, Runnable prelockedDomainEffects);
    /** Explicit user retry only: old current execution must be FAILED with complete verified zero-effect proof. */
    String retryAfterZeroEffect(String operationId, long expectedExecutionNo, TaskSpec newTask, ExternalRequest request);
}
