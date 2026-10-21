package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import ama.api.ApiApplication;
import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;

import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Controlled policy only; every blob goes through the real native local or Java S3 HTTP adapter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileRegistryUploadIT {
    ConfigurableApplicationContext app;
    JdbcTemplate j;
    ManagedRegistryFixture fixture;
    FileStoragePort storage;
    RegistryObservedStorage observed;
    RegistryProbeSource probe;
    FileBackendDefinitions composition;
    final ObjectMapper json = new ObjectMapper();
    final String csrf = "U".repeat(43), origin = "https://uploads.example.invalid";
    String owner, token, meeting;
    volatile boolean purposeEnabled = true;

    @BeforeAll
    void start() throws Exception {
        var source = MysqlTestDatabase.dataSource();
        new SchemaMigrator(source).migrate();
        j = new JdbcTemplate(source);
        Path directory = Path.of("target/registry-fixtures").toAbsolutePath();
        Files.createDirectories(directory);
        fixture = new ManagedRegistryFixture(Files.createTempDirectory(directory, "native-"));
        observed = new RegistryObservedStorage(fixture.storage);
        storage = observed;
        probe = new RegistryProbeSource(source);
        composition =
                new FileBackendDefinitions(List.of(fixture.backend(storage)), fixture.binding.id());
        startApp();
    }

    void startApp() {
        app =
                new SpringApplicationBuilder(ApiApplication.class)
                        .initializers(
                                context -> {
                                    var c = (GenericApplicationContext) context;
                                    c.registerBean(
                                            "registryDataSource",
                                            javax.sql.DataSource.class,
                                            () -> probe);
                                    c.registerBean(
                                            "controlledFilePurpose",
                                            FilePurposePolicy.class,
                                            () ->
                                                    (actor, id, purpose) -> {
                                                        if (!purposeEnabled
                                                                || !purpose.equals(
                                                                        "CONTROLLED_INPUT"))
                                                            throw new FilePurposePolicy
                                                                    .Unavailable();
                                                    });
                                    c.registerBean(
                                            "controlledBackends",
                                            FileBackendDefinitions.class,
                                            () -> composition);
                                    c.registerBean(
                                            "INPUT_BATCH_FILE",
                                            FileReferencePolicy.class,
                                            () ->
                                                    new ControlledBatchReference(
                                                            new JdbcTemplate(probe)));
                                })
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
                                "--identity.allowed-origin=" + origin,
                                "--files.backend.id=local",
                                "--files.backend.type="
                                        + ("s3".equals(System.getenv("AMA_STORAGE_CONTRACT"))
                                                ? "s3"
                                                : "local"));
    }

    @AfterAll
    void stop() throws Exception {
        if (app != null) app.close();
        if (fixture != null) fixture.close();
    }

    @BeforeEach
    void identity() throws Exception {
        probe.rollbackOn.set(null);
        probe.lostAckOn.set(null);
        observed.afterBegin.set(null);
        observed.afterPart.set(null);
        observed.beforeComplete.set(null);
        observed.afterComplete.set(null);
        purposeEnabled = true;
        owner = "upload-owner-" + UUID.randomUUID();
        token = identity(owner);
        var created = call("POST", "/meetings", "{}", "create", null);
        assertEquals(201, created.statusCode(), created.body());
        meeting = body(created).get("id").asText();
    }

    String identity(String id) throws Exception {
        byte[] secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        j.update(
                "INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at)"
                    + " VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",
                id,
                id);
        j.update(
                "INSERT INTO"
                    + " identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at)"
                    + " VALUES (?,?,0,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",
                sha(value),
                id,
                csrf);
        return value;
    }

    static String sha(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    JsonNode body(HttpResponse<String> r) throws Exception {
        return json.readTree(r.body());
    }

    HttpResponse<String> call(String method, String path, String payload, String key, String hash)
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
                        .header(
                                "Content-Type",
                                hash == null ? "application/json" : "application/octet-stream");
        if (key != null) r.header("Idempotency-Key", key);
        if (hash != null) r.header("X-Checksum-SHA256", hash);
        var response =
                HttpClient.newHttpClient()
                        .send(
                                r.method(method, HttpRequest.BodyPublishers.ofString(payload))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401)
            System.out.println(
                    "Controlled identity diagnostics: "
                            + j.queryForList(
                                    "SELECT i.status,i.session_generation AS"
                                        + " identity_generation,s.session_generation AS"
                                        + " session_generation,s.revoked_at IS NULL AS"
                                        + " unrevoked,TIMESTAMPDIFF(MICROSECOND,s.issued_at,UTC_TIMESTAMP(6))"
                                        + " AS issued_age_us,TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(6),s.expires_at)"
                                        + " AS seconds_remaining FROM enterprise_identity i JOIN"
                                        + " identity_session s ON i.enterprise_id=s.enterprise_id"
                                        + " WHERE i.enterprise_id=?",
                                    owner));
        return response;
    }

    String request(String name, String value) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "purpose",
                        "CONTROLLED_INPUT",
                        "name",
                        name,
                        "size",
                        value.getBytes(StandardCharsets.UTF_8).length,
                        "sha256",
                        sha(value)));
    }

    String begin(String value) throws Exception {
        var r =
                call(
                        "POST",
                        "/meetings/" + meeting + "/uploads",
                        request("original", value),
                        UUID.randomUUID().toString(),
                        null);
        assertEquals(201, r.statusCode(), r.body());
        return body(r).get("uploadId").asText();
    }

    HttpResponse<String> part(String id, int number, String value) throws Exception {
        return call("PUT", "/uploads/" + id + "/parts/" + number, value, null, sha(value));
    }

    HttpResponse<String> complete(String id, String whole, String... parts) throws Exception {
        var list = new ArrayList<Map<String, Object>>();
        for (int n = 0; n < parts.length; n++)
            list.add(Map.of("part", n + 1, "sha256", sha(parts[n])));
        return call(
                "POST",
                "/uploads/" + id + "/completion",
                json.writeValueAsString(Map.of("parts", list, "sha256", sha(whole))),
                null,
                null);
    }

    @Test
    void uploadResultHasNoBusinessDownloadGrant() throws Exception {
        String id = begin("abc");
        assertEquals(204, part(id, 1, "abc").statusCode());
        var done = complete(id, "abc", "abc");
        assertEquals(200, done.statusCode(), done.body());
        String file = body(done).get("fileId").asText();
        assertEquals(404, call("GET", "/files/" + file + "/content", "", null, null).statusCode());
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_reference WHERE file_id=? AND"
                            + " owner_kind='UPLOAD_SESSION' AND owner_id=?",
                        Integer.class,
                        file,
                        id));
        assertEquals(
                "COMPLETED",
                j.queryForObject("SELECT state FROM upload_session WHERE id=?", String.class, id));
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_part WHERE upload_id=?", Integer.class, id));
    }

    @Test
    void sameInitKeyReplaysAndDifferentBodyConflicts() throws Exception {
        String path = "/meetings/" + meeting + "/uploads", request = request("same", "abc");
        var first = call("POST", path, request, "same", null);
        assertEquals(201, first.statusCode());
        assertEquals(
                body(first).get("uploadId"),
                body(call("POST", path, request, "same", null)).get("uploadId"));
        assertEquals(
                409, call("POST", path, request("different", "abc"), "same", null).statusCode());
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",
                        Integer.class,
                        meeting));
    }

    @Test
    void samePartReplayRetainsReceiptAndDifferentContentConflicts() throws Exception {
        String id = begin("abc");
        assertEquals(204, part(id, 1, "abc").statusCode());
        String receipt =
                j.queryForObject(
                        "SELECT provider_token FROM upload_part WHERE upload_id=?",
                        String.class,
                        id);
        assertEquals(204, part(id, 1, "abc").statusCode());
        assertEquals(409, part(id, 1, "def").statusCode());
        assertEquals(
                receipt,
                j.queryForObject(
                        "SELECT provider_token FROM upload_part WHERE upload_id=?",
                        String.class,
                        id));
    }

    @Test
    void completionMustMatchAllCommittedReceipts() throws Exception {
        String id = begin("abcd");
        assertEquals(204, part(id, 1, "ab").statusCode());
        assertEquals(204, part(id, 2, "cd").statusCode());
        assertEquals(409, complete(id, "abcd", "ab").statusCode());
        assertEquals(
                "OPEN",
                j.queryForObject("SELECT state FROM upload_session WHERE id=?", String.class, id));
        assertEquals(409, complete(id, "abcd", "ab", "cd", "x").statusCode());
        assertEquals(200, complete(id, "abcd", "ab", "cd").statusCode());
    }

    @Test
    void completedReplayIsSameFileAndRejectsChangedManifest() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        var first = complete(id, "abc", "abc");
        assertEquals(200, first.statusCode());
        assertEquals(body(first).get("fileId"), body(complete(id, "abc", "abc")).get("fileId"));
        assertEquals(409, complete(id, "abc", "ab").statusCode());
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_object WHERE meeting_id=?",
                        Integer.class,
                        meeting));
        assertEquals(204, call("DELETE", "/uploads/" + id, "", null, null).statusCode());
        assertEquals(200, complete(id, "abc", "abc").statusCode());
    }

    @Test
    void abortedSessionCannotResurrect() throws Exception {
        String id = begin("abc");
        assertEquals(204, call("DELETE", "/uploads/" + id, "", null, null).statusCode());
        assertEquals(204, call("DELETE", "/uploads/" + id, "", null, null).statusCode());
        assertEquals(409, part(id, 1, "abc").statusCode());
        assertEquals(409, complete(id, "abc", "abc").statusCode());
    }

    @Test
    void purposeDisableBlocksExistingMutation() throws Exception {
        String id = begin("abc");
        purposeEnabled = false;
        assertEquals(503, part(id, 1, "abc").statusCode());
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_part WHERE upload_id=?", Integer.class, id));
    }

    @Test
    void publishedMeetingCannotAcceptNewBytes() throws Exception {
        String id = begin("abc");
        j.update("UPDATE meeting SET ever_published=1 WHERE id=?", meeting);
        assertEquals(409, part(id, 1, "abc").statusCode());
        assertEquals(
                409,
                call(
                                "POST",
                                "/meetings/" + meeting + "/uploads",
                                request("new", "abc"),
                                "after-publish",
                                null)
                        .statusCode());
    }

    @Test
    void epochMismatchBlocksExistingSession() throws Exception {
        String id = begin("abc");
        j.update("UPDATE meeting SET cancel_epoch=1 WHERE id=?", meeting);
        j.update("UPDATE task_subject SET cancel_epoch=1 WHERE subject_id=?", meeting);
        assertEquals(409, part(id, 1, "abc").statusCode());
    }

    @Test
    void crossMeetingOwnerCannotWriteUpload() throws Exception {
        String id = begin("abc");
        token = identity("outsider-" + UUID.randomUUID());
        assertEquals(403, part(id, 1, "abc").statusCode());
    }

    @Test
    void fullNameAndOpaqueTokenSurviveApplicationRestart() throws Exception {
        String name = "长名😀/\\\"\n".repeat(5000), path = "/meetings/" + meeting + "/uploads";
        var init = call("POST", path, request(name, "abc"), "restart", null);
        assertEquals(201, init.statusCode());
        String id = body(init).get("uploadId").asText();
        String token =
                j.queryForObject(
                        "SELECT provider_upload_token FROM upload_session WHERE id=?",
                        String.class,
                        id);
        assertNotEquals(id, token);
        part(id, 1, "abc");
        app.close();
        startApp();
        var result = complete(id, "abc", "abc");
        assertEquals(200, result.statusCode());
        assertEquals(
                name,
                j.queryForObject(
                        "SELECT display_name FROM file_object WHERE id=?",
                        String.class,
                        body(result).get("fileId").asText()));
        assertEquals(
                token,
                j.queryForObject(
                        "SELECT provider_upload_token FROM upload_session WHERE id=?",
                        String.class,
                        id));
    }

    /** Deliberately test-only B06 composition over actual deployed V011 rows and current root. */
    static final class ControlledBatchReference implements FileReferencePolicy {
        private final JdbcTemplate j;

        record Held(String revision, String batch, String file, String hash) {}

        ControlledBatchReference(JdbcTemplate j) {
            this.j = j;
        }

        public Object prelock(
                FileRegistry.LockedMeeting held,
                String meeting,
                FileRegistry.Reference ref,
                String file) {
            var batch =
                    j.queryForList(
                            "SELECT id FROM input_batch WHERE id=? AND meeting_id=? FOR UPDATE",
                            String.class,
                            ref.ownerId(),
                            meeting);
            if (batch.size() != 1) throw new ama.contract.FileContracts.NotFound();
            var row =
                    j.queryForList(
                            "SELECT r.id FROM transcript_revision r JOIN meeting m ON"
                                + " m.transcript_revision_id=r.id WHERE m.id=? AND r.meeting_id=?"
                                + " AND r.batch_id=?",
                            String.class,
                            meeting,
                            meeting,
                            ref.ownerId());
            if (row.size() != 1) throw new ama.contract.FileContracts.NotFound();
            j.queryForList(
                    "SELECT id FROM transcript_revision WHERE id=? FOR UPDATE",
                    String.class,
                    row.getFirst());
            String hash =
                    j.queryForObject(
                            "SELECT sha256 FROM input_batch_file WHERE batch_id=? AND file_id=? FOR"
                                + " UPDATE",
                            String.class,
                            ref.ownerId(),
                            file);
            return new Held(row.getFirst(), ref.ownerId(), file, hash);
        }

        public void validate(
                Object proof,
                String meeting,
                FileRegistry.Reference ref,
                FileRegistry.FileRecord file,
                String purpose) {
            if (!(proof instanceof Held h)
                    || !h.batch().equals(ref.ownerId())
                    || !h.file().equals(file.fileId())
                    || !h.hash().equals(file.sha256())
                    || !file.meetingId().equals(meeting)
                    || !purpose.equals("CONTROLLED_INPUT")
                    || !ref.purpose().equals(purpose)
                    || j.queryForObject(
                                    "SELECT COUNT(*) FROM meeting WHERE id=? AND"
                                        + " transcript_revision_id=?",
                                    Integer.class,
                                    meeting,
                                    h.revision())
                            != 1) throw new ama.contract.FileContracts.NotFound();
        }
    }

    String actualBatch(String file) throws Exception {
        String batch = UUID.randomUUID().toString(),
                input = UUID.randomUUID().toString(),
                revision = UUID.randomUUID().toString(),
                hash = sha("abcdef");
        j.update(
                "INSERT INTO"
                    + " input_batch(id,meeting_id,kind,input_fingerprint,base_revision,created_at)"
                    + " VALUES (?,?,'TRANSCRIPT',?,0,UTC_TIMESTAMP(6))",
                batch,
                meeting,
                hash);
        j.update(
                "INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,1,?,?)",
                batch,
                file,
                hash);
        j.update(
                "INSERT INTO"
                    + " transcription_input(id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at)"
                    + " VALUES"
                    + " (?,?,?,'IMPORT','EMPTY','controlled-v1','controlled-parser',1,'{}',?,0,0,UTC_TIMESTAMP(6))",
                input,
                meeting,
                batch,
                hash);
        j.update(
                "INSERT INTO"
                    + " transcript_revision(id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id)"
                    + " VALUES (?,?,1,'RAW',?,?,?,UTC_TIMESTAMP(6),?)",
                revision,
                meeting,
                batch,
                hash,
                owner,
                input);
        j.update("UPDATE meeting SET transcript_revision_id=? WHERE id=?", revision, meeting);
        return batch;
    }

    @Test
    void actualRegisteredBusinessReferencePermitsOnlyCurrentMeetingFile() throws Exception {
        String id = begin("abcdef");
        assertEquals(204, part(id, 1, "abcdef").statusCode());
        var done = complete(id, "abcdef", "abcdef");
        assertEquals(200, done.statusCode());
        String file = body(done).get("fileId").asText();
        String batch = actualBatch(file);
        j.update(
                "INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES"
                    + " (?,'INPUT_BATCH_FILE',?,'CONTROLLED_INPUT')",
                file,
                batch);
        var download = call("GET", "/files/" + file + "/content", "", null, null);
        assertEquals(200, download.statusCode(), download.body());
        assertEquals("abcdef", download.body());
        j.update("UPDATE meeting SET transcript_revision_id=NULL WHERE id=?", meeting);
        assertEquals(404, call("GET", "/files/" + file + "/content", "", null, null).statusCode());
    }

    @Test
    void initReplayMustRejectChangedCancellationEpoch() throws Exception {
        String request = request("fixed", "abc"), path = "/meetings/" + meeting + "/uploads";
        assertEquals(201, call("POST", path, request, "epoch-replay", null).statusCode());
        j.update("UPDATE meeting SET cancel_epoch=1 WHERE id=?", meeting);
        j.update("UPDATE task_subject SET cancel_epoch=1 WHERE subject_id=?", meeting);
        assertEquals(409, call("POST", path, request, "epoch-replay", null).statusCode());
    }

    @Test
    void orphanIsNotReadableAsBusinessFile() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        probe.rollbackOn.set("INSERT INTO file_object");
        var failed = complete(id, "abc", "abc");
        assertEquals(503, failed.statusCode(), failed.body());
        RegistryDeclaredError.assertMatches("/api/v1/uploads/{id}/completion", "post", failed);
        assertEquals(
                "FINALIZING",
                j.queryForObject("SELECT state FROM upload_session WHERE id=?", String.class, id));
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_object WHERE meeting_id=?",
                        Integer.class,
                        meeting));
        assertNotNull(probe.insertedFile);
        assertEquals(
                404,
                call("GET", "/files/" + probe.insertedFile + "/content", "", null, null)
                        .statusCode());
        assertEquals(200, complete(id, "abc", "abc").statusCode());
    }

    @Test
    void lostStorageAcknowledgmentRetainsFrozenDecision() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        observed.afterComplete.set(
                () -> {
                    throw new ama.contract.StorageContracts.StorageException(
                            ama.contract.StorageContracts.ErrorCode.STORAGE_UNAVAILABLE);
                });
        assertEquals(503, complete(id, "abc", "abc").statusCode());
        assertEquals(
                "FINALIZING",
                j.queryForObject("SELECT state FROM upload_session WHERE id=?", String.class, id));
        assertEquals(409, call("DELETE", "/uploads/" + id, "", null, null).statusCode());
        assertEquals(409, part(id, 1, "abc").statusCode());
        assertEquals(200, complete(id, "abc", "abc").statusCode());
    }

    @Test
    void lostDatabaseAcknowledgmentReturnsSameCommittedFileOnRetry() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        probe.lostAckOn.set("INSERT INTO file_object");
        var lost = complete(id, "abc", "abc");
        assertEquals(503, lost.statusCode(), lost.body());
        String file =
                j.queryForObject(
                        "SELECT completed_file_id FROM upload_session WHERE id=?",
                        String.class,
                        id);
        assertNotNull(file);
        assertEquals(file, body(complete(id, "abc", "abc")).get("fileId").asText());
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_object WHERE meeting_id=?",
                        Integer.class,
                        meeting));
    }

    @Test
    void beginAndPartAcksFollowTheirDatabaseCommit() throws Exception {
        probe.rollbackOn.set("INSERT INTO upload_session");
        var failed =
                call(
                        "POST",
                        "/meetings/" + meeting + "/uploads",
                        request("fixed", "abc"),
                        "rollback-begin",
                        null);
        assertEquals(503, failed.statusCode(), failed.body());
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",
                        Integer.class,
                        meeting));
        String id = begin("abc");
        probe.rollbackOn.set("INSERT INTO upload_part");
        assertEquals(503, part(id, 1, "abc").statusCode());
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_part WHERE upload_id=?", Integer.class, id));
        assertEquals(204, part(id, 1, "abc").statusCode());
    }

    @Test
    void identityRevocationBetweenStorageAndReceiptCommitIsDenied() throws Exception {
        String id = begin("abc");
        observed.afterPart.set(
                () ->
                        j.update(
                                "UPDATE enterprise_identity SET status='DISABLED' WHERE"
                                    + " enterprise_id=?",
                                owner));
        assertEquals(403, part(id, 1, "abc").statusCode());
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_part WHERE upload_id=?", Integer.class, id));
    }

    @Test
    void purposeDisableBetweenStorageAndRegistrationPreservesFinalizing() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        observed.afterComplete.set(() -> purposeEnabled = false);
        assertEquals(503, complete(id, "abc", "abc").statusCode());
        assertEquals(
                "FINALIZING",
                j.queryForObject("SELECT state FROM upload_session WHERE id=?", String.class, id));
        purposeEnabled = true;
        assertEquals(200, complete(id, "abc", "abc").statusCode());
    }

    @Test
    void latePartCannotBeAcknowledgedAfterCompletion() throws Exception {
        String id = begin("a");
        part(id, 1, "a");
        var durable = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        observed.afterPart.set(
                () -> {
                    durable.countDown();
                    try {
                        if (!release.await(20, java.util.concurrent.TimeUnit.SECONDS))
                            throw new AssertionError("late part gate timeout");
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                });
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var late = executor.submit(() -> part(id, 2, "b"));
            try {
                assertTrue(durable.await(10, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(200, complete(id, "a", "a").statusCode());
            } finally {
                release.countDown();
            }
            assertEquals(409, late.get(10, java.util.concurrent.TimeUnit.SECONDS).statusCode());
        }
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_part WHERE upload_id=?", Integer.class, id));
    }

    ama.contract.IdentityContracts.Actor actor() {
        return new ama.contract.IdentityContracts.Actor(
                owner,
                ama.contract.IdentityContracts.Status.ACTIVE,
                Set.of(ama.contract.IdentityContracts.Role.USER));
    }

    String uploaded(String content) throws Exception {
        String id = begin(content);
        part(id, 1, content);
        var done = complete(id, content, content);
        assertEquals(200, done.statusCode());
        return body(done).get("fileId").asText();
    }

    @Test
    void multiFileBindingLocksAllSessionsBeforeFilesAndAtomicallyHandsOffProtection()
            throws Exception {
        String first = uploaded("abcdef"), second = uploaded("abcdef"), batch = actualBatch(first);
        j.update(
                "INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,2,?,?)",
                batch,
                second,
                sha("abcdef"));
        String
                firstUpload =
                        j.queryForObject(
                                "SELECT id FROM upload_session WHERE completed_file_id=?",
                                String.class,
                                first),
                secondUpload =
                        j.queryForObject(
                                "SELECT id FROM upload_session WHERE completed_file_id=?",
                                String.class,
                                second);
        var registry = app.getBean(FileRegistry.class);
        var tx = app.getBean(ama.db.TransactionRunner.class);
        probe.statements.clear();
        tx.required(
                () -> {
                    var gate =
                            registry.lock(actor(), meeting, ama.access.MeetingAccess.Action.EDIT);
                    app.getBean(ama.access.MeetingAccess.class)
                            .require(registry.access(gate), ama.access.MeetingAccess.Action.EDIT);
                    var one =
                            registry.prelock(
                                    gate,
                                    new FileRegistry.Reference(
                                            "INPUT_BATCH_FILE", batch, "CONTROLLED_INPUT"),
                                    first);
                    var two =
                            registry.prelock(
                                    gate,
                                    new FileRegistry.Reference(
                                            "INPUT_BATCH_FILE", batch, "CONTROLLED_INPUT"),
                                    second);
                    registry.bindAll(
                            List.of(
                                    new FileRegistry.Binding(one, 0, firstUpload),
                                    new FileRegistry.Binding(two, 0, secondUpload)));
                    app.getBean(ama.task.JdbcOutbox.class).flush();
                    return null;
                });
        int lastSession = -1, firstFile = Integer.MAX_VALUE;
        for (int i = 0; i < probe.statements.size(); i++) {
            String sql = probe.statements.get(i);
            if (sql.contains("FROM upload_session") && sql.endsWith("FOR UPDATE")) lastSession = i;
            if (sql.contains("FROM file_object") && sql.endsWith("FOR UPDATE"))
                firstFile = Math.min(firstFile, i);
        }
        assertTrue(
                lastSession < firstFile,
                "all lower-order session locks must precede any file lock");
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_reference WHERE file_id IN (?,?) AND"
                            + " owner_kind='UPLOAD_SESSION'",
                        Integer.class,
                        first,
                        second));
        assertEquals(
                2,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_reference WHERE file_id IN (?,?) AND"
                            + " owner_kind='INPUT_BATCH_FILE'",
                        Integer.class,
                        first,
                        second));
    }

    @Test
    void registryRejectsForeignExpiredOrLatePrelockCapabilities() throws Exception {
        String file = uploaded("abcdef"), batch = actualBatch(file);
        var registry = app.getBean(FileRegistry.class);
        var tx = app.getBean(ama.db.TransactionRunner.class);
        FileRegistry.LockedMeeting old =
                tx.required(
                        () ->
                                registry.lock(
                                        actor(), meeting, ama.access.MeetingAccess.Action.EDIT));
        assertThrows(IllegalArgumentException.class, () -> tx.required(() -> registry.access(old)));
        assertThrows(
                IllegalArgumentException.class,
                () -> tx.required(() -> registry.access(new FileRegistry.LockedMeeting() {})));
        tx.required(
                () -> {
                    var gate =
                            registry.lock(actor(), meeting, ama.access.MeetingAccess.Action.EDIT);
                    var proof =
                            registry.prelock(
                                    gate,
                                    new FileRegistry.Reference(
                                            "INPUT_BATCH_FILE", batch, "CONTROLLED_INPUT"),
                                    file);
                    registry.bind(proof, 0, null);
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    registry.prelock(
                                            gate,
                                            new FileRegistry.Reference(
                                                    "INPUT_BATCH_FILE", batch, "CONTROLLED_INPUT"),
                                            file));
                    app.getBean(ama.task.JdbcOutbox.class).flush();
                    return null;
                });
    }

    @Test
    void fileStateAndLocationFenceRejectReferenceBinding() throws Exception {
        String file = uploaded("abcdef"), batch = actualBatch(file);
        var registry = app.getBean(FileRegistry.class);
        var tx = app.getBean(ama.db.TransactionRunner.class);
        assertThrows(
                ama.contract.FileContracts.Conflict.class,
                () ->
                        tx.required(
                                () -> {
                                    var gate =
                                            registry.lock(
                                                    actor(),
                                                    meeting,
                                                    ama.access.MeetingAccess.Action.EDIT);
                                    var proof =
                                            registry.prelock(
                                                    gate,
                                                    new FileRegistry.Reference(
                                                            "INPUT_BATCH_FILE",
                                                            batch,
                                                            "CONTROLLED_INPUT"),
                                                    file);
                                    registry.bind(proof, 1, null);
                                    return null;
                                }));
        j.update("UPDATE file_object SET state='DELETE_PENDING' WHERE id=?", file);
        assertThrows(
                ama.contract.FileContracts.NotFound.class,
                () ->
                        tx.required(
                                () -> {
                                    var gate =
                                            registry.lock(
                                                    actor(),
                                                    meeting,
                                                    ama.access.MeetingAccess.Action.EDIT);
                                    var proof =
                                            registry.prelock(
                                                    gate,
                                                    new FileRegistry.Reference(
                                                            "INPUT_BATCH_FILE",
                                                            batch,
                                                            "CONTROLLED_INPUT"),
                                                    file);
                                    registry.bind(proof, 0, null);
                                    return null;
                                }));
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_reference WHERE file_id=? AND"
                            + " owner_kind='INPUT_BATCH_FILE'",
                        Integer.class,
                        file));
    }

    @Test
    void businessDownloadRangesAndDispositionAreExact() throws Exception {
        String file = uploaded("abcdef"), batch = actualBatch(file);
        j.update(
                "INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES"
                    + " (?,'INPUT_BATCH_FILE',?,'CONTROLLED_INPUT')",
                file,
                batch);
        var request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + app.getEnvironment()
                                                        .getProperty("local.server.port")
                                                + "/api/v1/files/"
                                                + file
                                                + "/content"))
                        .header("Cookie", "__Host-AMA_SESSION=" + token)
                        .header("Range", "bytes=1-3")
                        .GET()
                        .build();
        var response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(206, response.statusCode(), response.body());
        assertEquals("bcd", response.body());
        assertEquals("bytes 1-3/6", response.headers().firstValue("Content-Range").orElseThrow());
        assertEquals("3", response.headers().firstValue("Content-Length").orElseThrow());
        var invalid =
                HttpRequest.newBuilder(request.uri())
                        .header("Cookie", "__Host-AMA_SESSION=" + token)
                        .header("Range", "bytes=6-9")
                        .GET()
                        .build();
        var rejected =
                HttpClient.newHttpClient().send(invalid, HttpResponse.BodyHandlers.ofString());
        assertEquals(416, rejected.statusCode());
        assertEquals("bytes */6", rejected.headers().firstValue("Content-Range").orElseThrow());
        RegistryDeclaredError.assertMatches("/api/v1/files/{id}/content", "get", rejected);
    }

    @Test
    void actualFileErrorsConformToDeclaredSharedResponseUnion() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        var conflict = part(id, 1, "def");
        assertEquals(409, conflict.statusCode());
        RegistryDeclaredError.assertMatches("/api/v1/uploads/{id}/parts/{part}", "put", conflict);
        purposeEnabled = false;
        var unavailable = part(id, 1, "abc");
        assertEquals(503, unavailable.statusCode());
        RegistryDeclaredError.assertMatches(
                "/api/v1/uploads/{id}/parts/{part}", "put", unavailable);
        var missing = call("GET", "/files/" + UUID.randomUUID() + "/content", "", null, null);
        assertEquals(404, missing.statusCode());
        RegistryDeclaredError.assertMatches("/api/v1/files/{id}/content", "get", missing);
    }

    @Test
    void configuredActualAdapterMakesReadinessAvailable() throws Exception {
        var request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + app.getEnvironment()
                                                        .getProperty("local.management.port")
                                                + "/internal/health/ready"))
                        .GET()
                        .build();
        var response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    @Test
    void defaultSwitchPinsExistingUploadAndBusinessDownloadToOriginalBackend() throws Exception {
        var originalComposition = composition;
        try (var second =
                new ManagedRegistryFixture(
                        Files.createTempDirectory(
                                Path.of("target/registry-fixtures").toAbsolutePath(), "second-"))) {
            String old = begin("abcdef");
            app.close();
            composition =
                    new FileBackendDefinitions(
                            List.of(fixture.backend(storage), second.backend(second.storage)),
                            second.binding.id());
            startApp();
            assertEquals(204, part(old, 1, "abcdef").statusCode());
            var done = complete(old, "abcdef", "abcdef");
            assertEquals(200, done.statusCode());
            String file = body(done).get("fileId").asText();
            assertEquals(
                    fixture.binding.id(),
                    j.queryForObject(
                            "SELECT backend_id FROM file_object WHERE id=?", String.class, file));
            String fresh = begin("new");
            assertEquals(
                    second.binding.id(),
                    j.queryForObject(
                            "SELECT backend_id FROM upload_session WHERE id=?",
                            String.class,
                            fresh));
            String batch = actualBatch(file);
            j.update(
                    "INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES"
                        + " (?,'INPUT_BATCH_FILE',?,'CONTROLLED_INPUT')",
                    file,
                    batch);
            var downloaded = call("GET", "/files/" + file + "/content", "", null, null);
            assertEquals(200, downloaded.statusCode());
            assertEquals("abcdef", downloaded.body());
            app.close();
            composition = originalComposition;
            startApp();
        } finally {
            if (composition != originalComposition) {
                app.close();
                composition = originalComposition;
                startApp();
            }
        }
    }

    @Test
    void readOnlyBackendRecoversFrozenResultButRejectsNewPartWrites() throws Exception {
        var originalComposition = composition;
        try (var second =
                new ManagedRegistryFixture(
                        Files.createTempDirectory(
                                Path.of("target/registry-fixtures").toAbsolutePath(),
                                "readonly-"))) {
            var watched = new RegistryObservedStorage(second.storage);
            app.close();
            composition =
                    new FileBackendDefinitions(
                            List.of(fixture.backend(storage), second.backend(watched)),
                            second.binding.id());
            startApp();
            String frozen = begin("abc"), open = begin("xyz");
            part(frozen, 1, "abc");
            watched.afterComplete.set(
                    () -> {
                        throw new ama.contract.StorageContracts.StorageException(
                                ama.contract.StorageContracts.ErrorCode.STORAGE_UNAVAILABLE);
                    });
            assertEquals(503, complete(frozen, "abc", "abc").statusCode());
            app.close();
            var readonly =
                    new FileBackendCatalog.Backend(
                            second.binding.id(),
                            second.binding.type(),
                            2,
                            "READ_ONLY",
                            watched,
                            second.binding);
            composition =
                    new FileBackendDefinitions(
                            List.of(fixture.backend(storage), readonly), fixture.binding.id());
            startApp();
            assertEquals(200, complete(frozen, "abc", "abc").statusCode());
            assertEquals(503, part(open, 1, "xyz").statusCode());
            app.close();
            composition = originalComposition;
            startApp();
        } finally {
            if (composition != originalComposition) {
                app.close();
                composition = originalComposition;
                startApp();
            }
        }
    }

    @Test
    void concurrentCompletionsReturnExactlyOneRegisteredFile() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var go = new java.util.concurrent.CountDownLatch(1);
            var one =
                    pool.submit(
                            () -> {
                                go.await();
                                return complete(id, "abc", "abc");
                            });
            var two =
                    pool.submit(
                            () -> {
                                go.await();
                                return complete(id, "abc", "abc");
                            });
            go.countDown();
            var a = one.get(30, java.util.concurrent.TimeUnit.SECONDS);
            var b = two.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(200, a.statusCode(), a.body());
            assertEquals(200, b.statusCode(), b.body());
            assertEquals(body(a).get("fileId"), body(b).get("fileId"));
        }
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_object WHERE meeting_id=?",
                        Integer.class,
                        meeting));
    }

    @Test
    void concurrentInitReplayAcceptsOnlyOneBusinessSession() throws Exception {
        String path = "/meetings/" + meeting + "/uploads", request = request("same", "abc");
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var go = new java.util.concurrent.CountDownLatch(1);
            var one =
                    pool.submit(
                            () -> {
                                go.await();
                                return call("POST", path, request, "concurrent", null);
                            });
            var two =
                    pool.submit(
                            () -> {
                                go.await();
                                return call("POST", path, request, "concurrent", null);
                            });
            go.countDown();
            var a = one.get(30, java.util.concurrent.TimeUnit.SECONDS);
            var b = two.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(201, a.statusCode(), a.body());
            assertEquals(201, b.statusCode(), b.body());
            assertEquals(body(a).get("uploadId"), body(b).get("uploadId"));
        }
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",
                        Integer.class,
                        meeting));
    }

    @Test
    void beginReauthorizesPurposeAfterStorageAcceptance() throws Exception {
        observed.afterBegin.set(() -> purposeEnabled = false);
        var failed =
                call(
                        "POST",
                        "/meetings/" + meeting + "/uploads",
                        request("fixed", "abc"),
                        "purpose-race",
                        null);
        assertEquals(503, failed.statusCode());
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",
                        Integer.class,
                        meeting));
    }

    @Test
    void cancellationAfterDurablePublicationPreventsRegistration() throws Exception {
        String id = begin("abc");
        part(id, 1, "abc");
        observed.afterComplete.set(
                () -> {
                    j.update("UPDATE meeting SET cancel_epoch=cancel_epoch+1 WHERE id=?", meeting);
                    j.update(
                            "UPDATE task_subject SET cancel_epoch=cancel_epoch+1 WHERE"
                                + " subject_id=?",
                            meeting);
                });
        assertEquals(409, complete(id, "abc", "abc").statusCode());
        assertEquals(
                "FINALIZING",
                j.queryForObject("SELECT state FROM upload_session WHERE id=?", String.class, id));
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM file_object WHERE meeting_id=?",
                        Integer.class,
                        meeting));
    }

    @Test
    void publishedCompletionReplayReturnsUnchangedResultButCannotBindNewReference()
            throws Exception {
        String id = begin("abcdef");
        part(id, 1, "abcdef");
        String file = body(complete(id, "abcdef", "abcdef")).get("fileId").asText(),
                batch = actualBatch(file);
        j.update("UPDATE meeting SET ever_published=1,stage='PUBLISHED' WHERE id=?", meeting);
        assertEquals(file, body(complete(id, "abcdef", "abcdef")).get("fileId").asText());
        assertEquals(204, call("DELETE", "/uploads/" + id, "", null, null).statusCode());
        var registry = app.getBean(FileRegistry.class);
        assertThrows(
                ama.contract.FileContracts.Conflict.class,
                () ->
                        app.getBean(ama.db.TransactionRunner.class)
                                .required(
                                        () -> {
                                            var gate =
                                                    registry.lock(
                                                            actor(),
                                                            meeting,
                                                            ama.access.MeetingAccess.Action.EDIT);
                                            registry.bind(
                                                    registry.prelock(
                                                            gate,
                                                            new FileRegistry.Reference(
                                                                    "INPUT_BATCH_FILE",
                                                                    batch,
                                                                    "CONTROLLED_INPUT"),
                                                            file),
                                                    0,
                                                    null);
                                            return null;
                                        }));
    }

    @Test
    void malformedOrForgedReceiptsNeverBecomeCommittedParts() throws Exception {
        String id = begin("abc");
        assertEquals(
                400,
                call("PUT", "/uploads/" + id + "/parts/1", "xyz", null, sha("abc")).statusCode());
        assertEquals(
                0,
                j.queryForObject(
                        "SELECT COUNT(*) FROM upload_part WHERE upload_id=?", Integer.class, id));
        assertEquals(204, part(id, 2, "abc").statusCode());
        assertEquals(409, complete(id, "abc", "abc").statusCode());
        var forged =
                Map.of(
                        "parts",
                        List.of(
                                Map.of(
                                        "part",
                                        2,
                                        "sha256",
                                        sha("abc"),
                                        "providerToken",
                                        "browser-forged")),
                        "sha256",
                        sha("abc"));
        assertEquals(
                400,
                call(
                                "POST",
                                "/uploads/" + id + "/completion",
                                json.writeValueAsString(forged),
                                null,
                                null)
                        .statusCode());
        assertEquals(
                "OPEN",
                j.queryForObject("SELECT state FROM upload_session WHERE id=?", String.class, id));
    }

    @Test
    void effectiveMembersReadOnlyWhileDraftsAndAdminsDoNotGrantContent() throws Exception {
        String file = uploaded("abcdef"), batch = actualBatch(file);
        j.update(
                "INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES"
                    + " (?,'INPUT_BATCH_FILE',?,'CONTROLLED_INPUT')",
                file,
                batch);
        String ownerToken = token,
                member = "member-" + UUID.randomUUID(),
                memberToken = identity(member),
                admin = "admin-" + UUID.randomUUID(),
                adminToken = identity(admin);
        j.update("INSERT INTO role_assignment VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))", admin);
        assertEquals(
                200,
                call(
                                "PUT",
                                "/meetings/" + meeting + "/info",
                                json.writeValueAsString(
                                        Map.of(
                                                "expectedRevision",
                                                0,
                                                "rosterDraftIds",
                                                List.of(member))),
                                "draft",
                                null)
                        .statusCode());
        token = memberToken;
        assertEquals(403, call("GET", "/files/" + file + "/content", "", null, null).statusCode());
        token = ownerToken;
        assertEquals(
                200,
                call(
                                "POST",
                                "/meetings/" + meeting + "/roster-confirmations",
                                "{\"expectedRevision\":1}",
                                "confirm",
                                null)
                        .statusCode());
        token = memberToken;
        assertEquals(200, call("GET", "/files/" + file + "/content", "", null, null).statusCode());
        String upload =
                j.queryForObject(
                        "SELECT id FROM upload_session WHERE completed_file_id=?",
                        String.class,
                        file);
        assertEquals(403, part(upload, 1, "abcdef").statusCode());
        token = adminToken;
        assertEquals(403, call("GET", "/files/" + file + "/content", "", null, null).statusCode());
        token = ownerToken;
        j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?", meeting);
        assertEquals(403, call("GET", "/files/" + file + "/content", "", null, null).statusCode());
    }
}
