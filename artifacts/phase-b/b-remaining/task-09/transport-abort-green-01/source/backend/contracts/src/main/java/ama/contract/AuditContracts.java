package ama.contract;

import java.time.Instant;
import java.util.Objects;

public final class AuditContracts {
    private AuditContracts() {}

    /** Metadata only: this contract deliberately has no body, credential or token field. */
    public record AuditEvent(String actorId, String action, String resourceId, long revision,
                             String result, String traceId, Instant time) {
        public AuditEvent {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(traceId, "traceId");
            Objects.requireNonNull(time, "time");
            if (revision < 0) {
                throw new IllegalArgumentException("revision must be nonnegative");
            }
        }
    }
}
