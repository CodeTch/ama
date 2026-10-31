package ama.identity;

/** Provisions the AMA identity projection after a trusted CAS assertion. */
public interface IdentityProvisioner {
    void ensureCasIdentity(String enterpriseId, String displayName, String department);
}
