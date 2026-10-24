package ama.identity;
import ama.contract.IdentityContracts.IssuedSession;
import java.time.Duration;
/** Production implementation must remain unavailable until the company protocol is qualified. */
public interface SessionIssuer {
    IssuedSession issueForVerifiedIdentity(String enterpriseId, Duration lifetime);
}
