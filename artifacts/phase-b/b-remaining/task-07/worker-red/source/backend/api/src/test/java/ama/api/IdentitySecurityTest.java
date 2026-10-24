package ama.api;

import ama.identity.CompanyIdentityAdapter;
import ama.contract.IdentityContracts.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.junit.jupiter.api.Assertions.*;

class IdentitySecurityTest {
    @Test void productionIssuerCannotIssueEvenWithValidLocalIdentity() {
        assertThrows(ProviderUnavailableException.class, () -> new CompanyIdentityAdapter().issueForVerifiedIdentity("known", Duration.ofHours(1)));
    }
    @Test void cookieUsesHostBoundaryAndNeverExposesCsrf() {
        var issued = new IssuedSession("A".repeat(43),"B".repeat(43),Instant.parse("2026-10-22T01:00:00Z"));
        String cookie = SessionCookies.issue(issued,Instant.parse("2026-10-22T00:00:00Z"));
        for (String flag : java.util.List.of("__Host-AMA_SESSION=", "Secure", "HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=3600")) assertTrue(cookie.contains(flag));
        assertFalse(cookie.contains("Domain=")); assertFalse(cookie.contains(issued.csrfToken()));
    }
    @Test void testModeConfigurationCannotStartProductionAssembly() {
        assertThrows(RuntimeException.class, () -> {
            try (var app = new SpringApplicationBuilder(ApiApplication.class).run("--server.port=0","--management.server.port=0","--identity.mode=test","--logging.level.root=OFF")) {
                fail("Test-mode production assembly started");
            }
        });
    }
    @Test void sessionDiagnosticsRedactSecretsAndTruncateIssuedExpiry() {
        var issued = new IssuedSession("A".repeat(43), "B".repeat(43), Instant.parse("2026-10-22T01:00:00.123456999Z"));
        assertFalse(issued.toString().contains(issued.token()));
        assertFalse(issued.toString().contains(issued.csrfToken()));
        assertEquals(Instant.parse("2026-10-22T01:00:00.123456Z"), issued.expiresAt());
        var resolved = new ResolvedSession(new Actor("known", Status.ACTIVE, java.util.Set.of(Role.USER)), "B".repeat(43), issued.expiresAt());
        assertFalse(resolved.toString().contains(resolved.csrfToken()));
    }
    @Test void opaqueOrNonOriginConfigurationIsRejected() {
        for (String origin : java.util.List.of("null", "https://good.invalid/", "https://user:pass@good.invalid", "https://good.invalid?next=x", "https://good.invalid#fragment")) {
            assertThrows(RuntimeException.class, () -> {
                try (var app = new SpringApplicationBuilder(ApiApplication.class).run("--server.port=0", "--management.server.port=0", "--logging.level.root=OFF", "--identity.allowed-origin=" + origin)) {
                    fail("Invalid origin configuration started");
                }
            });
        }
    }
    @Test void databaseFailureProducesSafeUnavailableBusinessError() throws Exception {
        try (var app = new SpringApplicationBuilder(ApiApplication.class).run("--server.port=0", "--management.server.port=0", "--logging.level.root=OFF",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1:1/ama?connectTimeout=500&socketTimeout=500")) {
            int port = Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/api/v1/session"))
                    .header("Cookie", "__Host-AMA_SESSION=" + "A".repeat(43)).GET().build();
            var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response.statusCode());
            assertTrue(response.body().contains("IDENTITY_UNAVAILABLE"));
            assertTrue(response.body().contains("traceId"));
            assertFalse(response.body().contains("SQLException"));
            assertFalse(response.body().contains("127.0.0.1"));
            assertFalse(response.body().contains("A".repeat(43)));
        }
    }
}
