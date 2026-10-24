package ama.identity;

import ama.contract.IdentityContracts.IssuedSession;
import ama.contract.IdentityContracts.ProviderUnavailableException;
import java.time.Duration;

/** No company protocol has been supplied or qualified. This never signs in a local identity. */
public final class CompanyIdentityAdapter implements SessionIssuer {
    @Override public IssuedSession issueForVerifiedIdentity(String enterpriseId, Duration lifetime) {
        throw new ProviderUnavailableException();
    }
}
