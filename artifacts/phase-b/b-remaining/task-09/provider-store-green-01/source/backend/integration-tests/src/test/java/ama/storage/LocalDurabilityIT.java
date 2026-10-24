package ama.storage;

import static ama.storage.FileStorageContractIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;
import ama.file.*;
import ama.storage.internal.LocalFileWire.Request;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class LocalDurabilityIT {
    @TempDir Path temporary;
    static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    static final StorageKey KEY = key("notes/a");

    @Test
    void durablePutIsReadableAfterStoreRestart() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            try (var s = fixture.open(DurabilityHooks.NONE)) {
                assertEquals(3, s.put(KEY, body("abc"), 3, ABC).size());
            }
            try (var s = fixture.open(DurabilityHooks.NONE);
                    var in = s.read(KEY, 1, 2)) {
                assertArrayEquals(bytes("bc"), in.readAllBytes());
                assertEquals(ABC, s.stat(KEY).sha256());
            }
        }
    }

    @ParameterizedTest(name = "failure at every {0} before ACK")
    @ValueSource(strings = {"FILE_SYNC", "DIR_SYNC", "LINK", "UNLINK", "MKDIR"})
    void injectedNativeOperationFailureCannotAcknowledge(String operation) throws Exception {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger observed = new AtomicInteger();
        try (var baseline = new LocalFixture(temporary.resolve("baseline"));
                var s =
                        baseline.open(
                                op -> {
                                    if (armed.get() && op.equals(operation))
                                        observed.incrementAndGet();
                                })) {
            armed.set(true);
            s.put(KEY, body("abc"), 3, ABC);
        }
        int count = observed.get();
        assertTrue(count > 0);
        for (int failAt = 1; failAt <= count; failAt++) {
            int index = failAt;
            AtomicInteger calls = new AtomicInteger();
            AtomicBoolean active = new AtomicBoolean();
            try (var fixture = new LocalFixture(temporary.resolve("failure-" + index));
                    var s =
                            fixture.open(
                                    op -> {
                                        if (active.get()
                                                && op.equals(operation)
                                                && calls.incrementAndGet() == index)
                                            throw new StorageException(
                                                    ErrorCode.STORAGE_UNAVAILABLE);
                                    })) {
                active.set(true);
                error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, body("abc"), 3, ABC));
                active.set(false);
                // A visible but unacknowledged publication is retried through the durability path.
                assertEquals(ABC, s.put(KEY, body("abc"), 3, ABC).sha256());
                assertArrayEquals(bytes("abc"), read(s, KEY, 0, 3));
            }
        }
    }

    @Test
    void replayMustReestablishFileAndDirectoryDurability() throws Exception {
        AtomicReference<String> fault = new AtomicReference<>("");
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s =
                        fixture.open(
                                op -> {
                                    if (op.equals(fault.get()))
                                        throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);
                                })) {
            s.put(KEY, body("abc"), 3, ABC);
            for (String operation : List.of("FILE_SYNC", "DIR_SYNC")) {
                fault.set(operation);
                error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, body("abc"), 3, ABC));
            }
            fault.set("");
            assertEquals(ABC, s.put(KEY, body("abc"), 3, ABC).sha256());
        }
    }

    @Test
    void deleteDirectorySyncFailureIsNotSuccessAndRetryIsDurable() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s =
                        fixture.open(
                                op -> {
                                    if (fail.get() && op.equals("DIR_SYNC"))
                                        throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);
                                })) {
            s.put(KEY, body("abc"), 3, ABC);
            fail.set(true);
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.delete(KEY));
            fail.set(false);
            s.delete(KEY);
            error(ErrorCode.NOT_FOUND, () -> s.stat(KEY));
        }
    }

    @ParameterizedTest(name = "selected completion survives process kill with extra [{0}]")
    @ValueSource(strings = {"", "xyz"})
    void multipartSelectedPartsAndCompletedResultSurviveActualProcessKill(String unselected)
            throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var s = fixture.startProcess();
            s.put(key("ordinary"), body("abc"), 3, ABC);
            var token = s.beginUpload(new UploadSpec("local", "meeting", "notes/a", 3));
            var receipt = s.writePart(token, 1, body("abc"), 3, ABC);
            var extra =
                    s.writePart(
                            token,
                            2,
                            body(unselected),
                            bytes(unselected).length,
                            hash(bytes(unselected)));
            fixture.killProcess();
            s = fixture.startProcess();
            assertArrayEquals(bytes("abc"), read(s, key("ordinary"), 0, 3));
            var result = s.complete(token, List.of(receipt), ABC);
            fixture.killProcess();
            s = fixture.startProcess();
            assertEquals(result, s.complete(token, List.of(receipt), ABC));
            assertArrayEquals(bytes("abc"), read(s, KEY, 0, 3));
            var restarted = s;
            error(
                    unselected.isEmpty() ? ErrorCode.CONFLICT : ErrorCode.SIZE_MISMATCH,
                    () -> restarted.complete(token, List.of(receipt, extra), ABC));
            error(
                    ErrorCode.INVALID_UPLOAD_STATE,
                    () -> restarted.writePart(token, 3, body(""), 0, hash(bytes(""))));
            restarted.abort(token);
            assertEquals(result, restarted.complete(token, List.of(receipt), ABC));
            assertArrayEquals(
                    bytes(unselected),
                    Files.readAllBytes(
                            fixture.root.resolve("uploads").resolve(token.sessionId())
                                    .resolve("part-2")));
        }
    }

    @Test
    void fullHashMismatchDoesNotPoisonCompletionRetry() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            var token = s.beginUpload(new UploadSpec("local", "meeting", "notes/a", 3));
            var receipt = s.writePart(token, 1, body("abc"), 3, ABC);
            s.writePart(token, 2, body(""), 0, hash(bytes("")));
            error(
                    ErrorCode.CHECKSUM_MISMATCH,
                    () -> s.complete(token, List.of(receipt), hash(bytes("xyz"))));
            assertEquals(ABC, s.complete(token, List.of(receipt), ABC).sha256());
        }
    }

    @Test
    void completeRejectsMissingSelectedPart() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            var other = s.beginUpload(new UploadSpec("local", "meeting", "other", 3));
            error(
                    ErrorCode.NOT_FOUND,
                    () ->
                            s.complete(
                                    other,
                                    List.of(
                                            new PartReceipt(
                                                    1, 3, ABC, other.sessionId() + ":1:" + ABC)),
                                    ABC));
        }
    }

    @Test
    void missingSentinelAndDetachedRootAreStorageOutages() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            s.put(KEY, body("abc"), 3, ABC);
            Files.delete(fixture.root.resolve(".volume-id"));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.stat(KEY));
            Files.writeString(fixture.root.resolve(".volume-id"), "fixture-volume");
            LocalFixture.privateMode(fixture.root.resolve(".volume-id"), false);
            Files.move(fixture.root, temporary.resolve("detached"));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.read(KEY, 0, 3));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mount", "role"})
    void lossAfterPublicationBeforeAckIsRejected(String loss) throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            String kernel = Files.readString(Path.of("/proc/self/mountinfo"));
            AtomicBoolean missingMount = new AtomicBoolean();
            AtomicBoolean armed = new AtomicBoolean();
            AtomicInteger unlinks = new AtomicInteger();
            var guard =
                    new MountedVolumeGuard(fixture.config, () -> missingMount.get() ? "" : kernel);
            try (var s =
                    new LocalFileStore(
                            fixture.root,
                            "local",
                            guard,
                            op -> {
                                if (armed.get()
                                        && op.equals("UNLINK")
                                        && unlinks.incrementAndGet() == 2) {
                                    if (loss.equals("mount")) missingMount.set(true);
                                    else
                                        try {
                                            Files.writeString(fixture.role, "standby");
                                        } catch (IOException e) {
                                            throw new UncheckedIOException(e);
                                        }
                                }
                            })) {
                armed.set(true);
                error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, body("abc"), 3, ABC));
                missingMount.set(false);
                Files.writeString(fixture.role, "active-instance");
                armed.set(false);
                assertEquals(ABC, s.put(KEY, body("abc"), 3, ABC).sha256());
            }
        }
    }

    private static class ReadObserver implements DurabilityHooks {
        final AtomicLong payloadBytes = new AtomicLong();

        @Override
        public void before(String operation) {}

        @Override
        public void afterRead(int descriptor, int count) {
            if (descriptorPath(descriptor).endsWith("/data")) payloadBytes.addAndGet(count);
        }
    }

    private static String descriptorPath(int descriptor) {
        try {
            return Files.readSymbolicLink(Path.of("/proc/self/fd/" + descriptor)).toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String readMetadata(StorageKey key) throws Exception {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        new ObjectMapper()
                                .writeValueAsBytes(
                                        new Request(
                                                key, null, null, null, null, null, null, null, null,
                                                null, null, null, null, null)));
    }

    @Test
    void httpRangeReadsOnlyRequestedPayloadAndInvalidOrEmptyReadsNone() throws Exception {
        var observer = new ReadObserver();
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var client = fixture.startServer(observer);
            byte[] value = new byte[2 * 1024 * 1024];
            Arrays.fill(value, (byte) 'z');
            fixture.store.put(KEY, new ByteArrayInputStream(value), value.length, hash(value));
            long baseline = LocalFixture.volumeDescriptors(fixture.root);
            try (var http =
                    HttpClient.newBuilder().sslContext(LocalFixture.tls("writer")).build()) {
                for (String range :
                        List.of(
                                "bytes=2097150-2097151",
                                "bytes=-2",
                                "bytes=2097152-",
                                "bytes=0-1,3-4")) {
                    observer.payloadBytes.set(0);
                    var response =
                            http.send(
                                    HttpRequest.newBuilder(fixture.uri.resolve("/internal/v1/read"))
                                            .POST(HttpRequest.BodyPublishers.noBody())
                                            .header("X-Ama-Request", readMetadata(KEY))
                                            .header("Range", range)
                                            .build(),
                                    HttpResponse.BodyHandlers.ofByteArray());
                    boolean valid =
                            range.equals("bytes=-2") || range.equals("bytes=2097150-2097151");
                    assertEquals(valid ? 206 : 416, response.statusCode());
                    assertEquals(valid ? 2 : 0, observer.payloadBytes.get(), range);
                    if (valid) assertArrayEquals(bytes("zz"), response.body());
                }
            }
            observer.payloadBytes.set(0);
            assertArrayEquals(new byte[0], read(client, KEY, value.length, 0));
            assertEquals(0, observer.payloadBytes.get());
            observer.payloadBytes.set(0);
            assertEquals(hash(value), client.stat(KEY).sha256());
            assertEquals(
                    value.length, observer.payloadBytes.get(), "stat still verifies all bytes");
            awaitDescriptors(fixture, baseline);
        }
    }

    @Test
    void httpSizeAndBodyStayOnTheOpenedInodeDuringLeafReplacement() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var armed = new AtomicBoolean();
        var observer =
                new ReadObserver() {
                    @Override
                    public void afterRead(int descriptor, int count) {
                        super.afterRead(descriptor, count);
                        if (count > 0
                                && descriptorPath(descriptor).endsWith("/data")
                                && armed.compareAndSet(true, false)) {
                            entered.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new AssertionError("release timed out");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                        }
                    }
                };
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            fixture.startServer(observer);
            byte[] value = new byte[256 * 1024];
            Arrays.fill(value, (byte) 'z');
            fixture.store.put(KEY, new ByteArrayInputStream(value), value.length, hash(value));
            long baseline = LocalFixture.volumeDescriptors(fixture.root);
            try (var http =
                    HttpClient.newBuilder().sslContext(LocalFixture.tls("writer")).build()) {
                armed.set(true);
                var response =
                        http.sendAsync(
                                HttpRequest.newBuilder(fixture.uri.resolve("/internal/v1/read"))
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .header("X-Ama-Request", readMetadata(KEY))
                                        .build(),
                                HttpResponse.BodyHandlers.ofByteArray());
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    fixture.store.delete(KEY);
                    fixture.store.put(KEY, body("abc"), 3, ABC);
                } finally {
                    release.countDown();
                }
                var result = response.get(5, TimeUnit.SECONDS);
                assertEquals(200, result.statusCode());
                assertEquals(
                        Long.toString(value.length),
                        result.headers().firstValue("Content-Length").orElseThrow());
                assertArrayEquals(value, result.body());
            }
            awaitDescriptors(fixture, baseline);
            assertArrayEquals(bytes("abc"), read(fixture.store, KEY, 0, 3));
        }
    }

    @Test
    void listingHashesOnlyTheFinalPageNotLookaheadOrEvictedCandidates() throws Exception {
        var observer = new ReadObserver();
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var store = fixture.open(observer)) {
            byte[] value = new byte[256 * 1024];
            for (int i = 9; i >= 0; i--)
                store.put(
                        key("page/" + i),
                        new ByteArrayInputStream(value),
                        value.length,
                        hash(value));
            observer.payloadBytes.set(0);
            var page = store.list("local", KEY.namespace(), "page/", null, 1);
            assertEquals(List.of(key("page/0")), page.items().stream().map(BlobItem::key).toList());
            assertNotNull(page.nextCursor());
            assertEquals(value.length, observer.payloadBytes.get());
        }
    }

    private static void awaitDescriptors(LocalFixture fixture, long baseline) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (LocalFixture.volumeDescriptors(fixture.root) != baseline
                && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals(baseline, LocalFixture.volumeDescriptors(fixture.root));
    }

    @Test
    void peerResetBeforeHeadersBoundsPayloadWorkAndReleasesDescriptors() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var resumed = new CountDownLatch(1);
        var armed = new AtomicBoolean();
        var observer =
                new ReadObserver() {
                    @Override
                    public void afterRead(int descriptor, int count) {
                        super.afterRead(descriptor, count);
                        if (count > 0
                                && descriptorPath(descriptor).endsWith("/key")
                                && armed.compareAndSet(true, false)) {
                            entered.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new AssertionError("release timed out");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                            resumed.countDown();
                        }
                    }
                };
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            fixture.startServer(observer);
            byte[] value = new byte[8 * 1024 * 1024];
            fixture.store.put(KEY, new ByteArrayInputStream(value), value.length, hash(value));
            observer.payloadBytes.set(0);
            long baseline = LocalFixture.volumeDescriptors(fixture.root);
            try (var socket =
                    LocalFixture.tls("writer")
                            .getSocketFactory()
                            .createSocket("localhost", fixture.uri.getPort())) {
                socket.setSoLinger(true, 0);
                armed.set(true);
                String request =
                        "POST /internal/v1/read HTTP/1.1\r\nHost: localhost\r\nX-Ama-Request: "
                                + readMetadata(KEY)
                                + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                socket.getOutputStream()
                        .write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertTrue(resumed.await(5, TimeUnit.SECONDS));
            awaitDescriptors(fixture, baseline);
            assertTrue(
                    observer.payloadBytes.get() <= 65536,
                    "reset is detected on response writes; no pre-header payload scan");
        }
    }

    @Test
    void cancelledHttpReadReleasesTheServerFileDescriptor() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var client = fixture.startServer();
            byte[] value = new byte[4 * 1024 * 1024];
            Arrays.fill(value, (byte) 'z');
            fixture.store.put(KEY, new ByteArrayInputStream(value), value.length, hash(value));
            long baseline = LocalFixture.volumeDescriptors(fixture.root);
            try (var in = client.read(KEY, 0, value.length)) {
                assertEquals('z', in.read());
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (LocalFixture.volumeDescriptors(fixture.root) != baseline
                    && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(baseline, LocalFixture.volumeDescriptors(fixture.root));
            assertEquals(value.length, client.stat(KEY).size());
        }
    }

    @Test
    void closedReadStreamsReleaseNativeDescriptors() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            s.put(KEY, body("abc"), 3, ABC);
            long before = LocalFixture.volumeDescriptors(fixture.root);
            for (int i = 0; i < 100; i++) {
                try (var in = s.read(KEY, 0, 3)) {
                    assertEquals('a', in.read());
                }
            }
            assertEquals(before, LocalFixture.volumeDescriptors(fixture.root));
        }
    }

    @Test
    void activeRoleLossDuringStreamingRejectsAckAndAllAccess() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            InputStream changed =
                    new ByteArrayInputStream(bytes("abc")) {
                        @Override
                        public synchronized int read(byte[] b, int o, int l) {
                            int n = super.read(b, o, l);
                            try {
                                Files.writeString(fixture.role, "standby");
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            return n;
                        }
                    };
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, changed, 3, ABC));
            // Once admission is lost, failure cleanup must not issue further volume mutations.
            try (var staged = Files.list(fixture.root.resolve("staging"))) {
                assertEquals(1, staged.count());
            }
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.stat(KEY));
            Files.writeString(fixture.role, "active-instance");
            assertEquals(ABC, s.put(KEY, body("abc"), 3, ABC).sha256());
        }
    }

    @Test
    void kernelMountIdentityLossCannotFallThroughToUnderlyingDirectory() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            AtomicBoolean missing = new AtomicBoolean();
            String mountinfo = Files.readString(Path.of("/proc/self/mountinfo"));
            var guard =
                    new MountedVolumeGuard(fixture.config, () -> missing.get() ? "" : mountinfo);
            try (var s = new LocalFileStore(fixture.root, "local", guard, DurabilityHooks.NONE)) {
                s.put(KEY, body("abc"), 3, ABC);
                missing.set(true);
                error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(key("new"), body("abc"), 3, ABC));
                error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.read(KEY, 0, 3));
                // Simulate pathname replacement while the old mounted descriptor remains alive.
                missing.set(false);
                Path detached = temporary.resolve("detached");
                Files.move(fixture.root, detached);
                Files.createDirectory(fixture.root);
                LocalFixture.privateMode(fixture.root, true);
                error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(key("new"), body("abc"), 3, ABC));
                assertEquals(0, Files.list(fixture.root).count());
            }
        }
    }

    @Test
    void absentRootWrongSentinelWrongDeviceAndUnsafePermissionsFailClosed() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            Files.writeString(fixture.root.resolve(".volume-id"), "wrong");
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> fixture.open(DurabilityHooks.NONE));
            Files.writeString(fixture.root.resolve(".volume-id"), "fixture-volume");
            var c = fixture.config;
            var wrong =
                    new MountedVolumeGuard(
                            new MountedVolumeGuard.Config(
                                    c.root(),
                                    c.mountpoint(),
                                    "999:999",
                                    c.filesystem(),
                                    c.source(),
                                    c.volumeId(),
                                    c.roleFile(),
                                    c.activeRole()));
            error(
                    ErrorCode.STORAGE_UNAVAILABLE,
                    () -> new LocalFileStore(fixture.root, "local", wrong, DurabilityHooks.NONE));
            Files.setPosixFilePermissions(
                    fixture.root,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> fixture.open(DurabilityHooks.NONE));
            error(
                    ErrorCode.STORAGE_UNAVAILABLE,
                    () ->
                            new LocalFileStore(
                                    temporary.resolve("absent"),
                                    "local",
                                    new MountedVolumeGuard(c),
                                    DurabilityHooks.NONE));
            assertFalse(Files.exists(temporary.resolve("absent")));
        }
    }

    @Test
    void abortDoesNotSwallowLostTombstonePublication() throws Exception {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicReference<Path> tombstone = new AtomicReference<>();
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s =
                        fixture.open(
                                op -> {
                                    if (armed.get() && op.equals("UNLINK")) {
                                        try {
                                            Files.deleteIfExists(tombstone.get());
                                        } catch (IOException e) {
                                            throw new UncheckedIOException(e);
                                        }
                                    }
                                })) {
            var token = s.beginUpload(new UploadSpec("local", "meeting", "notes/a", 3));
            tombstone.set(
                    fixture.root.resolve("uploads").resolve(token.sessionId()).resolve("aborted"));
            armed.set(true);
            assertThrows(StorageException.class, () -> s.abort(token));
            armed.set(false);
            s.abort(token);
            error(ErrorCode.INVALID_UPLOAD_STATE, () -> s.writePart(token, 1, body("abc"), 3, ABC));
        }
    }

    @Test
    void deleteRejectsAnExistingSymlinkLeaf() throws Exception {
        Path outside = temporary.resolve("outside");
        Files.writeString(outside, "outside");
        LocalFixture.privateMode(outside, false);
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            s.put(KEY, body("abc"), 3, ABC);
            try (var directories = Files.list(fixture.root.resolve("objects"))) {
                Path data = directories.findFirst().orElseThrow().resolve("data");
                Files.delete(data);
                Files.createSymbolicLink(data, outside);
                error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.delete(KEY));
                assertTrue(Files.isSymbolicLink(data));
            }
            assertEquals("outside", Files.readString(outside));
        }
    }

    @Test
    void symlinkLeafAndParentCannotReadOrWriteOutsideVolume() throws Exception {
        Path outside = temporary.resolve("outside");
        Files.writeString(outside, "outside-secret");
        LocalFixture.privateMode(outside, false);
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            s.put(KEY, body("abc"), 3, ABC);
            Path dir;
            try (var dirs = Files.list(fixture.root.resolve("objects"))) {
                dir = dirs.findFirst().orElseThrow();
            }
            Files.delete(dir.resolve("data"));
            Files.createSymbolicLink(dir.resolve("data"), outside);
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.read(KEY, 0, 3));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, body("abc"), 3, ABC));
            Files.delete(dir.resolve("data"));
            Files.move(fixture.root.resolve("objects"), fixture.root.resolve("objects-original"));
            Files.createSymbolicLink(fixture.root.resolve("objects"), temporary);
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(key("escape"), body("abc"), 3, ABC));
            assertEquals("outside-secret", Files.readString(outside));
            assertFalse(Files.exists(temporary.resolve("data")));
        }
    }

    @Test
    void replacementBetweenStreamingAndPublishCannotEscapeAnchors() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s = fixture.open(DurabilityHooks.NONE)) {
            InputStream race =
                    new ByteArrayInputStream(bytes("abc")) {
                        boolean done;

                        @Override
                        public synchronized int read(byte[] b, int o, int l) {
                            if (!done) {
                                done = true;
                                try {
                                    Files.move(
                                            fixture.root.resolve("objects"),
                                            fixture.root.resolve("saved"));
                                    Files.createSymbolicLink(
                                            fixture.root.resolve("objects"), temporary);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                            return super.read(b, o, l);
                        }
                    };
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, race, 3, ABC));
            assertFalse(Files.exists(temporary.resolve("data")));
        }
    }

    @Test
    void replacingStagedNameAtLinkCannotPublishAnAcknowledgedSymlink() throws Exception {
        Path outside = temporary.resolve("outside");
        Files.writeString(outside, "outside");
        LocalFixture.privateMode(outside, false);
        AtomicInteger links = new AtomicInteger();
        AtomicBoolean armed = new AtomicBoolean();
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s =
                        fixture.open(
                                op -> {
                                    if (armed.get()
                                            && op.equals("LINK")
                                            && links.incrementAndGet() == 2) {
                                        try (var staged =
                                                Files.list(fixture.root.resolve("staging"))) {
                                            Path temp = staged.findFirst().orElseThrow();
                                            Files.delete(temp);
                                            Files.createSymbolicLink(temp, outside);
                                        } catch (IOException e) {
                                            throw new UncheckedIOException(e);
                                        }
                                    }
                                })) {
            armed.set(true);
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, body("abc"), 3, ABC));
            assertEquals("outside", Files.readString(outside));
        }
    }

    @Test
    void replacingObjectDirectoryAtPublicationCannotReceiveSuccess() throws Exception {
        AtomicInteger links = new AtomicInteger();
        AtomicBoolean armed = new AtomicBoolean();
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var s =
                        fixture.open(
                                op -> {
                                    if (armed.get()
                                            && op.equals("LINK")
                                            && links.incrementAndGet() == 2) {
                                        try (var dirs =
                                                Files.list(fixture.root.resolve("objects"))) {
                                            Path dir = dirs.findFirst().orElseThrow();
                                            Files.move(
                                                    dir, fixture.root.resolve("detached-object"));
                                            Files.createSymbolicLink(dir, temporary);
                                        } catch (IOException e) {
                                            throw new UncheckedIOException(e);
                                        }
                                    }
                                })) {
            armed.set(true);
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(KEY, body("abc"), 3, ABC));
            assertFalse(Files.exists(temporary.resolve("data")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "rogue", "expired", "outsider"})
    void mtlsRejectsMissingUntrustedExpiredAndUnassignedClient(String identity) throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            fixture.startServer();
            LocalFileClient denied =
                    identity.equals("missing")
                            ? new LocalFileClient(fixture.uri, LocalFixture.noCertificate())
                            : fixture.client(identity);
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> denied.put(KEY, body("abc"), 3, ABC));
            assertEquals(0, fixture.store.list("local", "meeting", "", null, 10).items().size());
        }
    }

    @Test
    void mtlsReaderCanReadButCannotMutateAndClientChecksServerHostname() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var writer = fixture.startServer();
            writer.put(KEY, body("abc"), 3, ABC);
            var reader = fixture.client("reader");
            assertArrayEquals(bytes("abc"), read(reader, KEY, 0, 3));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> reader.delete(KEY));
            var wrongHost =
                    new LocalFileClient(
                            URI.create("https://127.0.0.1:" + fixture.uri.getPort()),
                            LocalFixture.tls("writer"));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> wrongHost.stat(KEY));
            assertEquals(ABC, writer.stat(KEY).sha256());
        }
    }

    @Test
    void mtlsClientRejectsUntrustedServerAndMissingPkiConfiguration() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            fixture.startServer();
            var ssl =
                    ama.storage.internal.ServiceTls.context(
                            LocalFixture.pki.resolve("writer.p12"),
                            LocalFixture.PASSWORD,
                            LocalFixture.pki.resolve("rogue-trust.p12"),
                            LocalFixture.PASSWORD);
            error(
                    ErrorCode.STORAGE_UNAVAILABLE,
                    () -> new LocalFileClient(fixture.uri, ssl).stat(KEY));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ama.storage.internal.ServiceTls.context(null, null, null, null));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LocalFileClient(URI.create("http://localhost"), ssl));
        }
    }

    @Test
    void httpSingleRangeSuffixAndUnsatisfiableResponseAreExplicit() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var client = fixture.startServer();
            client.put(KEY, body("abc"), 3, ABC);
            var http = HttpClient.newBuilder().sslContext(LocalFixture.tls("writer")).build();
            Request request =
                    new Request(
                            KEY, null, null, null, null, null, null, null, null, null, null, null,
                            null, null);
            String metadata =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(new ObjectMapper().writeValueAsBytes(request));
            for (String range : List.of("bytes=1-2", "bytes=1-", "bytes=-2")) {
                var response =
                        http.send(
                                HttpRequest.newBuilder(fixture.uri.resolve("/internal/v1/read"))
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .header("X-Ama-Request", metadata)
                                        .header("Range", range)
                                        .build(),
                                HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(206, response.statusCode());
                assertArrayEquals(bytes("bc"), response.body());
                assertEquals(
                        "bytes 1-2/3",
                        response.headers().firstValue("Content-Range").orElseThrow());
            }
            for (String range :
                    List.of(
                            "bytes=3-",
                            "bytes=0-3",
                            "bytes=2-1",
                            "bytes=0-1,2-2",
                            "bytes=-0",
                            "bytes=999999999999999999999-")) {
                var response =
                        http.send(
                                HttpRequest.newBuilder(fixture.uri.resolve("/internal/v1/read"))
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .header("X-Ama-Request", metadata)
                                        .header("Range", range)
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(416, response.statusCode());
                assertEquals(
                        "bytes */3", response.headers().firstValue("Content-Range").orElseThrow());
                assertTrue(response.body().contains("INVALID_RANGE"));
            }
            var publicRoute =
                    http.send(
                            HttpRequest.newBuilder(fixture.uri.resolve("/api/v1/files/x"))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());
            assertEquals(404, publicRoute.statusCode());
        }
    }

    @Test
    void streamingTransferExceedsFileProcessHeapWithoutBufferingWholeBlob() throws Exception {
        final long size = 96L * 1024 * 1024;
        byte[] block = new byte[65536];
        Arrays.fill(block, (byte) 'z');
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (long n = 0; n < size; n += block.length) digest.update(block);
        String expected = HexFormat.of().formatHex(digest.digest());
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var client = fixture.startProcess();
            InputStream generated =
                    new InputStream() {
                        long remaining = size;

                        public int read() {
                            if (remaining-- <= 0) return -1;
                            return 'z';
                        }

                        public int read(byte[] b, int off, int len) {
                            if (remaining == 0) return -1;
                            int count = (int) Math.min(len, remaining);
                            Arrays.fill(b, off, off + count, (byte) 'z');
                            remaining -= count;
                            return count;
                        }
                    };
            assertEquals(expected, client.put(KEY, generated, size, expected).sha256());
            try (var in = client.read(KEY, size - 3, 3)) {
                assertArrayEquals(bytes("zzz"), in.readAllBytes());
            }
            assertEquals(size, client.stat(KEY).size());
        }
    }

    @Test
    void multipartCompleteAbortRacePreservesTerminalWinner() throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var pool = Executors.newFixedThreadPool(2)) {
            var client = fixture.startServer();
            var other = fixture.client("writer");
            var token = client.beginUpload(new UploadSpec("local", "meeting", "notes/a", 3));
            var receipt = client.writePart(token, 1, body("abc"), 3, ABC);
            var latch = new CountDownLatch(1);
            var complete =
                    pool.submit(
                            () -> {
                                latch.await();
                                try {
                                    return client.complete(token, List.of(receipt), ABC);
                                } catch (StorageException e) {
                                    return e.code();
                                }
                            });
            var abort =
                    pool.submit(
                            () -> {
                                latch.await();
                                other.abort(token);
                                return true;
                            });
            latch.countDown();
            Object result = complete.get(15, TimeUnit.SECONDS);
            assertTrue(abort.get(15, TimeUnit.SECONDS));
            if (result instanceof StoredBlob) {
                assertArrayEquals(bytes("abc"), read(client, KEY, 0, 3));
                assertEquals(result, client.complete(token, List.of(receipt), ABC));
            } else {
                assertEquals(ErrorCode.INVALID_UPLOAD_STATE, result);
                error(ErrorCode.NOT_FOUND, () -> client.stat(KEY));
            }
        }
    }
}
