package ama.identity;
import ama.contract.IdentityContracts.ResolvedSession;
import java.util.Optional;
/** Shared resolver/revocation boundary. No production sign-in endpoint exists. */
public interface SessionPort extends IdentityPort {
    Optional<ResolvedSession> resolve(String token);
    void revoke(String token, String traceId);
    void revokeAll(String enterpriseId, String traceId);
}
