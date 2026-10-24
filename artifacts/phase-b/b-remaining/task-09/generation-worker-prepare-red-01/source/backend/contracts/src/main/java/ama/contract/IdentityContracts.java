package ama.contract;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.time.temporal.ChronoUnit;

public final class IdentityContracts {
    private IdentityContracts() {}
    public enum Status { ACTIVE, DISABLED }
    public enum Role { USER, PLATFORM_ADMIN }
    public record Actor(String enterpriseId, Status status, Set<Role> roles) {
        public Actor {
            stableId(enterpriseId);
            if (status == null || roles == null) throw new IllegalArgumentException("Invalid identity");
            roles = Set.copyOf(roles);
        }
    }
    public record Person(String stableId, String displayName, String department, boolean active) {}
    public record PeoplePage(List<Person> items, String nextCursor) {}
    public record ResolvedSession(Actor actor, String csrfToken, Instant expiresAt) {
        public ResolvedSession { expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS); }
        @Override public String toString() { return "ResolvedSession[actor=" + actor + ", csrfToken=REDACTED, expiresAt=" + expiresAt + "]"; }
    }
    /** Secret material for a trusted server callback only; never serialize this as an API response. */
    public record IssuedSession(String token, String csrfToken, Instant expiresAt) {
        public IssuedSession { expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS); }
        @Override public String toString() { return "IssuedSession[token=REDACTED, csrfToken=REDACTED, expiresAt=" + expiresAt + "]"; }
    }
    public static final class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException() { super("Enterprise identity provider is unavailable"); }
    }
    public static void stableId(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 255)
            throw new IllegalArgumentException("Invalid stable identity");
    }
}
