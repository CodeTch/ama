package ama.identity;
import ama.contract.IdentityContracts.Status;
import java.time.Instant;
/** Server-only authoritative lifecycle input; no client claims or public admin endpoint. */
public interface IdentityLifecyclePort {
    void setStatus(String enterpriseId, Status status, Instant verifiedAt, String traceId);
}
