package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import ama.api.ApiApplication;
import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Real authenticated HTTP and MySQL. Removing upload acceptance must fail this test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileRegistryHttpIT {
    ConfigurableApplicationContext app;
    JdbcTemplate j;
    final ObjectMapper json = new ObjectMapper();
    final String csrf = "R".repeat(43), origin = "https://registry.example.invalid";
    String token;

    @BeforeAll
    void start() throws Exception {
        var source = MysqlTestDatabase.dataSource();
        new SchemaMigrator(source).migrate();
        j = new JdbcTemplate(source);
        app =
                new SpringApplicationBuilder(ApiApplication.class)
                        .run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--management.server.port=0",
                                "--logging.level.root=ERROR",
                                "--spring.datasource.url=" + System.getenv("AMA_TEST_DATABASE_URL"),
                                "--spring.datasource.username="
                                        + System.getenv("AMA_TEST_DATABASE_USERNAME"),
                                "--spring.datasource.password="
                                        + System.getenv("AMA_TEST_DATABASE_PASSWORD"),
                                "--identity.allowed-origin=" + origin);
        String actor = "registry-owner-" + UUID.randomUUID();
        byte[] secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        j.update(
                "INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at)"
                    + " VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",
                actor,
                actor);
        j.update(
                "INSERT INTO"
                    + " identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at)"
                    + " VALUES (?,?,0,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",
                sha(token),
                actor,
                csrf);
    }

    @AfterAll
    void stop() {
        if (app != null) app.close();
    }

    static String sha(String text) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    HttpResponse<String> call(String method, String path, String body, String key)
            throws Exception {
        var r =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + app.getEnvironment()
                                                        .getProperty("local.server.port")
                                                + "/api/v1"
                                                + path))
                        .header("Cookie", "__Host-AMA_SESSION=" + token)
                        .header("X-CSRF-Token", csrf)
                        .header("Origin", origin)
                        .header("Content-Type", "application/json");
        if (key != null) r.header("Idempotency-Key", key);
        return HttpClient.newHttpClient()
                .send(
                        r.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void unconfiguredPurposeFailsClosedThroughDeclaredUploadRoute() throws Exception {
        var created = call("POST", "/meetings", "{}", UUID.randomUUID().toString());
        assertEquals(201, created.statusCode(), created.body());
        String id = json.readTree(created.body()).get("id").asText();
        var response =
                call(
                        "POST",
                        "/meetings/" + id + "/uploads",
                        json.writeValueAsString(
                                Map.of(
                                        "purpose",
                                        "NOT_CONFIGURED",
                                        "name",
                                        "文件",
                                        "size",
                                        3,
                                        "sha256",
                                        sha("abc"))),
                        "first");
        assertEquals(503, response.statusCode(), response.body());
        assertEquals(
                "FILE_PURPOSE_UNAVAILABLE",
                json.readTree(response.body()).path("error").path("code").asText());
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",
                        Integer.class,
                        id));
    }

    @Test
    void emptyBackendConfigurationIsNotReady() throws Exception {
        var request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + app.getEnvironment()
                                                        .getProperty("local.management.port")
                                                + "/internal/health/ready"))
                        .GET()
                        .build();
        assertEquals(
                503,
                HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString())
                        .statusCode());
    }

    @Test
    void configuredPurposeWithoutBackendReturnsDeclaredUnavailable() throws Exception {
        var original = app;
        try (var controlled =
                new SpringApplicationBuilder(ApiApplication.class)
                        .initializers(
                                context ->
                                        ((org.springframework.context.support
                                                                .GenericApplicationContext)
                                                        context)
                                                .registerBean(
                                                        "controlledPurpose",
                                                        FilePurposePolicy.class,
                                                        () -> (actor, meeting, purpose) -> {}))
                        .run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--management.server.port=0",
                                "--logging.level.root=ERROR",
                                "--spring.datasource.url=" + System.getenv("AMA_TEST_DATABASE_URL"),
                                "--spring.datasource.username="
                                        + System.getenv("AMA_TEST_DATABASE_USERNAME"),
                                "--spring.datasource.password="
                                        + System.getenv("AMA_TEST_DATABASE_PASSWORD"),
                                "--identity.allowed-origin=" + origin)) {
            app = controlled;
            var created = call("POST", "/meetings", "{}", UUID.randomUUID().toString());
            assertEquals(201, created.statusCode(), created.body());
            String meeting = json.readTree(created.body()).get("id").asText();
            var response =
                    call(
                            "POST",
                            "/meetings/" + meeting + "/uploads",
                            json.writeValueAsString(
                                    Map.of("purpose", "CONTROLLED_INPUT", "name", "x", "size", 0)),
                            "no-backend");
            assertEquals(503, response.statusCode(), response.body());
            RegistryDeclaredError.assertMatches("/api/v1/meetings/{id}/uploads", "post", response);
            assertEquals(
                    0,
                    j.queryForObject(
                            "SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",
                            Integer.class,
                            meeting));
        } finally {
            app = original;
        }
    }
}
