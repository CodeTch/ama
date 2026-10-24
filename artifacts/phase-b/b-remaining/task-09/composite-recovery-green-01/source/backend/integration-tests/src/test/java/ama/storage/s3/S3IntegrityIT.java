package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.sync.RequestBody;

import java.util.*;
import java.util.concurrent.*;

class S3IntegrityIT {
    @Test
    void wrongConcreteRangeVersionFailsClosed() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            f.version("Enabled");
            s.put(key("version"), body("abc"), 3, hash("abc"));
            proxy.arm("wrong-version", p -> p.startsWith("GET ") && p.contains("/data/v3/"));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.read(key("version"), 0, 1));
            capture("v3-wrong-range-version", proxy);
        }
    }

    @Test
    void concurrentDeletionReturnsAnEmptyAdvancingPageWithoutNamespaceScan() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var pool = Executors.newSingleThreadExecutor()) {
            s.put(key("p/a"), body("a"), 1, hash("a"));
            s.put(key("p/b"), body("b"), 1, hash("b"));
            proxy.settle();
            proxy.reset();
            var pause = proxy.arm("pause", p -> p.startsWith("GET ") && p.contains("list-type="));
            var pending = pool.submit(() -> s.list("s3", "meeting", "p/", null, 1));
            assertTrue(pause.reached.await(15, TimeUnit.SECONDS));
            try {
                f.admin.deleteObject(
                        b ->
                                b.bucket(f.config.dataBucket())
                                        .key(new S3Layout(f.config).data(key("p/a"))));
            } finally {
                pause.release.countDown();
            }
            var first = pending.get(20, TimeUnit.SECONDS);
            assertTrue(first.items().isEmpty());
            assertNotNull(first.nextCursor());
            var second = s.list("s3", "meeting", "p/", first.nextCursor(), 1);
            assertEquals(List.of(key("p/b")), second.items().stream().map(BlobItem::key).toList());
            assertNull(second.nextCursor());
            proxy.settle();
            assertEquals(
                    2, proxy.snapshot().stream().filter(e -> e.path().contains("list-type=")).count());
            capture("v3-delete-short-page", proxy);
        }
    }

    @Test
    void malformedPhysicalListEntryAndFrozenManifestNeverReceiveRepairWrites() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var pool = Executors.newSingleThreadExecutor()) {
            s.put(key("bound"), body("x"), 1, hash("x"));
            String malformed = new S3Layout(f.config).scope("meeting") + "xa/xb";
            f.admin.putObject(
                    b -> b.bucket(f.config.dataBucket()).key(malformed),
                    RequestBody.fromBytes(bytes("x")));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.list("s3", "meeting", "", null, 1));
            var token = s.beginUpload(new UploadSpec("s3", "meeting", "frozen", 3));
            var receipt = s.writePart(token, 1, body("abc"), 3, hash("abc"));
            String prefix = "sessions/v1/" + token.sessionId().split("\\.")[0] + "/";
            var pause = proxy.arm("pause", p -> p.startsWith("PUT ") && p.contains("/terminal"));
            var pending = pool.submit(() -> s.complete(token, List.of(receipt), hash("abc")));
            assertTrue(pause.reached.await(15, TimeUnit.SECONDS));
            String page =
                    f
                            .admin
                            .listObjectsV2(
                                    b ->
                                            b.bucket(f.config.controlBucket())
                                                    .prefix(prefix + "completions/"))
                            .contents()
                            .stream()
                            .map(o -> o.key())
                            .filter(k -> k.endsWith("/page/0"))
                            .findFirst()
                            .orElseThrow();
            try {
                f.admin.putObject(
                        b -> b.bucket(f.config.controlBucket()).key(page),
                        RequestBody.fromBytes(bytes("malformed")));
            } finally {
                pause.release.countDown();
            }
            assertTrue(
                    assertThrows(ExecutionException.class, () -> pending.get(20, TimeUnit.SECONDS))
                                    .getCause()
                            instanceof StorageException);
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.resume(token));
            error(
                    ErrorCode.INVALID_UPLOAD_STATE,
                    () -> s.writePart(token, 2, body(""), 0, hash("")));
            assertArrayEquals(
                    bytes("malformed"),
                    f.admin
                            .getObjectAsBytes(b -> b.bucket(f.config.controlBucket()).key(page))
                            .asByteArray());
            error(ErrorCode.NOT_FOUND, () -> s.stat(key("frozen")));
            capture("v3-malformed-manifest-list", proxy);
        }
    }

    @Test
    void copiedTokenSpecCannotSelectAnotherPhysicalConfiguration() throws Exception {
        try (var a = new S3Fixture("s3");
                var b = new S3Fixture("s3");
                var first = a.open();
                var second = b.open()) {
            var token = first.beginUpload(new UploadSpec("s3", "meeting", "scope", 0));
            String path = "sessions/v1/" + token.sessionId().split("\\.")[0] + "/spec";
            byte[] original =
                    a.admin
                            .getObjectAsBytes(r -> r.bucket(a.config.controlBucket()).key(path))
                            .asByteArray();
            b.admin.putObject(
                    r -> r.bucket(b.config.controlBucket()).key(path),
                    RequestBody.fromBytes(original));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> second.resume(token));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> second.abort(token));
            assertTrue(
                    b.admin
                            .listObjectsV2(r -> r.bucket(b.config.dataBucket()))
                            .contents()
                            .isEmpty());
        }
    }
}
