package ama.task;

/**
 * No observation was acknowledged. Retain and resubmit the SAME authoritative fact outside any
 * transaction. This does not authorize repeating the external request or creating another effect.
 */
public final class ObservationRetryRequiredException extends RuntimeException {
    public ObservationRetryRequiredException() {
        super("Observation generation kept changing; resubmit the same authoritative fact outside a transaction, never repeat the external request");
    }
}
