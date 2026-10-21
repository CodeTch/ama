package ama.api;

import ama.config.DeploymentConfig;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentConfigTest {
    @Test void localStartsWithoutS3Credentials() {
        var config = DeploymentConfig.parse(Map.of("database.mode", "enterprise-platform", "storage.type", "local", "storage.default-backend-id", "files-1"));
        assertEquals("local", config.storageType());
        assertEquals("enterprise-platform", config.databaseMode());
        assertEquals("files-1", config.defaultBackendId());
    }
    @Test void s3MissingEndpointRejected() {
        assertThrows(IllegalArgumentException.class, () -> DeploymentConfig.parse(Map.of("storage.type", "s3", "storage.s3.bucket", "meetings", "storage.s3.region", "internal", "storage.s3.access-key", "test", "storage.s3.secret-key", "test")));
    }
    @Test void allFourDatabaseStorageCombinationsAreIndependent() {
        for (String db : new String[]{"private-ha", "enterprise-platform"}) {
            for (String storage : new String[]{"local", "s3"}) {
                var config = DeploymentConfig.parse(Map.of("database.mode", db, "storage.type", storage, "storage.s3.endpoint", "https://object.invalid", "storage.s3.bucket", "meetings", "storage.s3.region", "internal", "storage.s3.access-key", "test", "storage.s3.secret-key", "test"));
                assertEquals(db, config.databaseMode());
                assertEquals(storage, config.storageType());
            }
        }
    }
    @Test void invalidConfigurationRejectedWithoutEchoingSecrets() {
        for (var config : java.util.List.of(Map.of("database.mode", "other"), Map.of("storage.type", "other"), Map.of("storage.default-backend-id", " "), Map.of("storage.type", "s3", "storage.s3.endpoint", "file:///secret"))) {
            var error = assertThrows(IllegalArgumentException.class, () -> DeploymentConfig.parse(config));
            assertFalse(error.getMessage().contains("secret"));
        }
    }
    @Test void readinessFailsWhenDatabaseUnavailable() throws Exception {
        try (var app = start("--spring.datasource.url=jdbc:mysql://127.0.0.1:1/ama?connectTimeout=500&socketTimeout=500")) {
            var reply = get(app, "ready");
            assertEquals(503, reply.statusCode());
            assertEquals("{\"status\":\"DOWN\"}", reply.body());
            assertEquals(200, get(app, "live").statusCode());
            assertTrue(app.isActive());
        }
    }
    @Test void readinessRecoversFromRealDatabaseOutage() throws Exception {
        var database = org.h2.tools.Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
        int port = database.getPort();
        try (var app = start("--spring.datasource.url=jdbc:h2:tcp://127.0.0.1:" + port + "/mem:health;DB_CLOSE_DELAY=-1", "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.datasource.driver-class-name=org.h2.Driver")) {
            assertEquals(200, get(app, "ready").statusCode());
            database.stop();
            assertEquals(503, get(app, "ready").statusCode());
            assertEquals(200, get(app, "live").statusCode());
            database = org.h2.tools.Server.createTcpServer("-tcpPort", Integer.toString(port), "-ifNotExists").start();
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            int status;
            do {
                status = get(app, "ready").statusCode();
                if (status == 200) break;
                // Allow Hikari's 500 ms recent-connection validation bypass to expire.
                Thread.sleep(600);
            } while (System.nanoTime() < deadline);
            assertEquals(200, status);
        } finally { database.stop(); }
    }
    @Test void healthIsNotExposedOnPublicListener() throws Exception {
        try (var app = start()) {
            int port = Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
            var reply = request(port, "/internal/health/live");
            assertEquals(403, reply.statusCode());
            assertFalse(reply.body().contains("127.0.0.1"));
        }
    }
    @Test void untrustedNetworkCannotReadHealthAndForwardedHeadersAreIgnored() throws Exception {
        try (var app = start("--internal.health.allowed-networks=192.0.2.0/24")) {
            int port = Integer.parseInt(app.getEnvironment().getProperty("local.management.port"));
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/health/live")).header("X-Forwarded-For", "192.0.2.10").GET().build();
            var reply = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, reply.statusCode());
            assertEquals("{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}}", reply.body());
        }
    }
    @Test void s3RejectsEachMissingRequiredFieldAndMalformedEndpoint() {
        var valid = Map.of("storage.type", "s3", "storage.s3.endpoint", "https://objects.invalid", "storage.s3.bucket", "meeting", "storage.s3.region", "internal", "storage.s3.access-key", "test", "storage.s3.secret-key", "test");
        for (String key : new String[]{"endpoint", "bucket", "region", "access-key", "secret-key"}) {
            var values = new java.util.HashMap<>(valid);
            values.remove("storage.s3." + key);
            assertThrows(IllegalArgumentException.class, () -> DeploymentConfig.parse(values));
        }
        for (String endpoint : new String[]{"/relative", "https://user:password@host.invalid", "file:///private", "https://objects.invalid/#fragment"}) {
            var values = new java.util.HashMap<>(valid);
            values.put("storage.s3.endpoint", endpoint);
            assertThrows(IllegalArgumentException.class, () -> DeploymentConfig.parse(values));
        }
    }
    private ConfigurableApplicationContext start(String... extra) {
        // These historical tests isolate DB liveness; managed file readiness has its own real HTTP IT.
        var args = new java.util.ArrayList<>(java.util.List.of("--server.port=0", "--management.server.port=0", "--logging.level.root=ERROR", "--management.endpoint.health.group.ready.include=readinessState,db"));
        args.addAll(java.util.List.of(extra));
        return new SpringApplicationBuilder(ApiApplication.class).run(args.toArray(String[]::new));
    }
    private HttpResponse<String> get(ConfigurableApplicationContext app, String probe) throws Exception {
        return request(Integer.parseInt(app.getEnvironment().getProperty("local.management.port")), "/internal/health/" + probe);
    }
    private HttpResponse<String> request(int port, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
