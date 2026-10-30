package ama.identity;

import ama.contract.IdentityContracts.IssuedSession;
import java.net.URI;

/** Application boundary for browser based CAS login and local session issuance. */
public interface CasAuthenticationPort {
    URI loginUri();
    URI logoutUri();
    IssuedSession authenticate(String ticket);
}
