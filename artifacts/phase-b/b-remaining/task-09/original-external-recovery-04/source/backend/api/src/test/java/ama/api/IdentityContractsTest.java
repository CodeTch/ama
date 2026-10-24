package ama.api;

import ama.contract.IdentityContracts.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityContractsTest {
    @Test void actorCannotCarryMutablePrivileges() {
        var roles = EnumSet.of(Role.USER);
        var actor = new Actor("Case ", Status.ACTIVE, roles);
        roles.add(Role.PLATFORM_ADMIN);
        assertEquals(Set.of(Role.USER), actor.roles());
        assertEquals("Case ", actor.enterpriseId(), "stable IDs must retain case and trailing space");
        assertThrows(UnsupportedOperationException.class, () -> actor.roles().add(Role.PLATFORM_ADMIN));
    }
    @Test void invalidActorCannotEnterAuthorizationBoundary() {
        for (String id : new String[]{null, "", " ", "x".repeat(256)}) {
            assertThrows(IllegalArgumentException.class, () -> new Actor(id, Status.ACTIVE, Set.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> new Actor("valid", null, Set.of()));
    }
    @Test void sessionTimestampsTruncateInsteadOfRound() {
        var actor = new Actor("valid", Status.ACTIVE, Set.of(Role.USER));
        var result = new ResolvedSession(actor, "B".repeat(43), Instant.parse("2026-10-22T00:00:00.123456999Z"));
        assertEquals(Instant.parse("2026-10-22T00:00:00.123456Z"), result.expiresAt());
    }
}
