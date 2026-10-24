package ama.task;

import ama.contract.TaskContracts.Permit;
import ama.contract.ExternalContracts.OperationToken;
import java.time.Instant;
import java.util.Optional;

public interface ModelPermitPool {
    /** An unbound call cannot authorize external execution. Retained planned signature fails closed. */
    default Optional<Permit> acquire(String model, Instant safeDeadline) {
        throw new IllegalStateException("A current committed execution token is required");
    }
    /** Independent short transaction; NULL deadline retains capacity until definitive execution settlement. */
    Optional<Permit> acquire(OperationToken token, String model, Instant safeDeadline);
    /** Verify this exact execution is terminal in the committed DB; never trust a caller's timeout declaration. */
    boolean settled(Permit permit);
    /** Bounded durable maintenance; discovery does not itself authorize release. */
    default int recoverSettlements(int scanLimit) { return 0; }
}
