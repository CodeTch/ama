package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import ama.db.SchemaMigrator;
import ama.storage.s3.S3Fixture;
import ama.support.MysqlTestDatabase;

import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Actual API JVM deaths, same durable local/S3 provider, and persistent MySQL decisions. */
class FileRegistryRestartIT {
    Process process;
    ManagedLocalFixture local;
    S3Fixture s3;
    Path logs;
    int port;
    String token;
    final String csrf = "J".repeat(43);
    final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void stop() throws Exception {
        kill();
        if (local != null) local.close();
        if (s3 != null) s3.close();
    }

    void kill() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        }
        process = null;
    }

    void start(String mode) throws Exception {
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin/java").toString(),
                        "-Xmx192m",
                        "-XX:ActiveProcessorCount=2",
                        "-cp",
                        System.getProperty("java.class.path"),
                        RegistryApiProcess.class.getName(),
                        mode);
        if (local != null) {
            builder.environment().put("AMA_REGISTRY_LOCAL_URI", local.uri.toString());
            builder.environment().put("AMA_REGISTRY_PKI", LocalFixture.pki.toString());
            builder.environment().put("AMA_REGISTRY_VOLUME", local.binding.volumeId());
            builder.environment().put("AMA_REGISTRY_RELATIVE_ROOT", local.binding.relativeRoot());
            builder.environment().put("AMA_REGISTRY_FILESYSTEM", local.binding.filesystem());
        } else {
            builder.environment().put("AMA_REGISTRY_DATA_BUCKET", s3.config.dataBucket());
            builder.environment().put("AMA_REGISTRY_CONTROL_BUCKET", s3.config.controlBucket());
        }
        Path log = logs.resolve(UUID.randomUUID() + "-api.log");
        process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        while (System.nanoTime() < end) {
            for (String line : Files.readAllLines(log))
                if (line.startsWith("REGISTRY_READY ")) {
                    port = Integer.parseInt(line.substring(15));
                    return;
                }
            if (!process.isAlive())
                throw new AssertionError("owned API failed; retained log " + log);
            Thread.sleep(100);
        }
        throw new AssertionError("owned API startup timed out; retained log " + log);
    }

    HttpResponse<String> call(String method, String path, String body, String key, String hash)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                        .header("Cookie", "__Host-AMA_SESSION=" + token)
                        .header("Origin", "https://registry-restart.example.invalid")
                        .header("X-CSRF-Token", csrf)
                        .header(
                                "Content-Type",
                                hash == null ? "application/json" : "application/octet-stream");
        if (key != null) request.header("Idempotency-Key", key);
        if (hash != null) request.header("X-Checksum-SHA256", hash);
        return HttpClient.newHttpClient()
                .send(
                        request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void frozenManifestNamePurposeAndOpaqueTokenRecoverAfterActualJvmDeath() throws Exception {
        var source = MysqlTestDatabase.dataSource();
        new SchemaMigrator(source).migrate();
        var j = new JdbcTemplate(source);
        Path root = Path.of("target/registry-restart").toAbsolutePath();
        Files.createDirectories(root);
        logs = Files.createTempDirectory(root, "processes-");
        if ("s3".equals(System.getenv("AMA_STORAGE_CONTRACT"))) s3 = new S3Fixture("local");
        else {
            local = new ManagedLocalFixture(logs.resolve("native"));
            local.startProcess();
        }
        byte[] secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String actor = "restart-" + UUID.randomUUID();
        j.update(
                "INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at)"
                    + " VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",
                actor,
                actor);
        j.update(
                "INSERT INTO"
                    + " identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at)"
                    + " VALUES (?,?,0,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",
                FileRegistryUploadIT.sha(token),
                actor,
                csrf);
        start("crash-after-complete");
        var created = call("POST", "/meetings", "{}", "restart-create", null);
        assertEquals(201, created.statusCode(), created.body());
        String meeting = json.readTree(created.body()).get("id").asText();
        String name = "恢复😀/\\\"\n".repeat(5000), hash = FileRegistryUploadIT.sha("abc");
        var begin =
                call(
                        "POST",
                        "/meetings/" + meeting + "/uploads",
                        json.writeValueAsString(
                                Map.of("purpose", "CONTROLLED_INPUT", "name", name, "size", 3)),
                        "restart-begin",
                        null);
        assertEquals(201, begin.statusCode(), begin.body());
        String upload = json.readTree(begin.body()).get("uploadId").asText();
        String storageToken =
                j.queryForObject(
                        "SELECT provider_upload_token FROM upload_session WHERE id=?",
                        String.class,
                        upload);
        assertNotEquals(upload, storageToken);
        assertEquals(
                204,
                call("PUT", "/uploads/" + upload + "/parts/1", "abc", null, hash).statusCode());
        String manifest =
                json.writeValueAsString(
                        Map.of(
                                "parts",
                                List.of(Map.of("part", 1, "sha256", hash)),
                                "sha256",
                                hash));
        assertThrows(
                java.io.IOException.class,
                () -> call("POST", "/uploads/" + upload + "/completion", manifest, null, null));
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(73, process.exitValue());
        assertEquals(
                "FINALIZING",
                j.queryForObject(
                        "SELECT state FROM upload_session WHERE id=?", String.class, upload));
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_object WHERE meeting_id=?",
                        Integer.class,
                        meeting));
        start("normal");
        var done = call("POST", "/uploads/" + upload + "/completion", manifest, null, null);
        assertEquals(200, done.statusCode(), done.body());
        String file = json.readTree(done.body()).get("fileId").asText();
        assertEquals(
                name,
                j.queryForObject(
                        "SELECT display_name FROM file_object WHERE id=?", String.class, file));
        assertEquals(
                storageToken,
                j.queryForObject(
                        "SELECT provider_upload_token FROM upload_session WHERE id=?",
                        String.class,
                        upload));
        kill();
        start("normal");
        assertEquals(
                file,
                json.readTree(
                                call(
                                                "POST",
                                                "/uploads/" + upload + "/completion",
                                                manifest,
                                                null,
                                                null)
                                        .body())
                        .get("fileId")
                        .asText());
        kill();
        start("purpose-disabled");
        assertEquals(
                503,
                call("POST", "/uploads/" + upload + "/completion", manifest, null, null)
                        .statusCode());
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_object WHERE meeting_id=?",
                        Integer.class,
                        meeting));
    }
}
