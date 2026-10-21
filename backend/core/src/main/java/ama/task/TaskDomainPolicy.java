package ama.task;

import ama.contract.TaskContracts.TaskSpec;
import ama.contract.TaskContracts.TaskResult;
import ama.contract.ExternalContracts.ExternalRequest;

/** A registered task type has a domain-owned policy. Unknown type/kind is rejected without a fallback. */
public interface TaskDomainPolicy {
    /** Called after subject lock and BEFORE task lock. Prelock every relevant domain row in canonical order. */
    boolean lockRoot(TaskSpec spec, boolean skipLocked);
    /** Verify immutable typed input exists and belongs to exact subject/version/fingerprint; no bodies in queue. */
    void validateInput(TaskSpec spec);
    /** Verify typed output and its fixed input provenance under the already-held root. */
    void validateOutput(TaskSpec spec, TaskResult result);
    /** Local authoritative adapter validates observations/proofs; never performs network I/O under locks. */
    default void validateExternalObservation(TaskSpec spec, ama.contract.ExternalContracts.ExternalObservation observation) {
        throw new IllegalStateException("No authoritative observation verifier registered");
    }
    /** Validate the versioned content-free execution manifest and logical business key for this operation. */
    void validateExternalRequest(TaskSpec spec, ExternalRequest request);
}
