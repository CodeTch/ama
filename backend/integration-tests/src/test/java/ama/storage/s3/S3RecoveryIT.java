package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.sync.RequestBody;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

class S3RecoveryIT {
    @Test
    void largerObjectReadCancellationHasSameFixedTransportBound() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            byte[] data = new byte[64 * 1024 * 1024];
            Arrays.fill(data, (byte) 'q');
            s.put(
                    key("larger-range"),
                    new ByteArrayInputStream(data),
                    data.length,
                    ControlCodec.hash(data));
            proxy.settle();
            proxy.reset();
            try (var in = s.read(key("larger-range"), data.length / 2, 1)) {
                assertArrayEquals(bytes("q"), in.readAllBytes());
            }
            proxy.settle();
            assertEquals(
                    1,
                    proxy.snapshot().stream()
                            .filter(e -> e.method().equals("GET") && e.path().contains("/data/v3/"))
                            .count());
            assertEquals(
                    1,
                    proxy.snapshot().stream()
                            .filter(e -> e.path().contains("/data/v3/"))
                            .mapToLong(WireProxy.Event::upstreamBytes)
                            .sum());
            capture("range-64MiB-one", proxy);
            proxy.reset();
            try (var in = s.read(key("larger-range"), 0, data.length)) {
                assertEquals('q', in.read());
            }
            proxy.settle();
            long bytes =
                    proxy.snapshot().stream()
                            .filter(e -> e.path().contains("/data/v3/"))
                            .mapToLong(WireProxy.Event::upstreamBytes)
                            .sum();
            assertTrue(
                    bytes > 0 && bytes < 1024 * 1024,
                    "cancel payload must be bounded independently of 64MiB object: " + bytes);
            capture("range-64MiB-cancel", proxy);
        }
    }

    @Test
    void overlappingHelpersUseDistinctNativeIdsAndPreserveCompleteDecision() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var pool = Executors.newSingleThreadExecutor()) {
            byte[] data = new byte[9 * 1024 * 1024];
            Arrays.fill(data, (byte) 4);
            String hash = ControlCodec.hash(data);
            var t = s.beginUpload(new UploadSpec("s3", "meeting", "helpers", data.length));
            var r = s.writePart(t, 1, new ByteArrayInputStream(data), data.length, hash);
            var barrier = proxy.arm("pause", p -> p.startsWith("POST ") && p.contains("uploads"));
            var first = pool.submit(() -> s.complete(t, List.of(r), hash));
            assertTrue(barrier.reached.await(20, TimeUnit.SECONDS));
            s.abort(t);
            assertEquals(hash, s.resume(t).sha256());
            barrier.release.countDown();
            assertEquals(hash, first.get(30, TimeUnit.SECONDS).sha256());
            String prefix = "sessions/v1/" + t.sessionId().split("\\.")[0] + "/attempts/";
            var records =
                    f.admin.listObjectsV2(b -> b.bucket(f.config.controlBucket()).prefix(prefix));
            var ids = new HashSet<String>();
            for (var record : records.contents())
                if (record.key().endsWith("/known-upload")) {
                    byte[] value =
                            f.admin
                                    .getObjectAsBytes(
                                            b ->
                                                    b.bucket(f.config.controlBucket())
                                                            .key(record.key()))
                                    .asByteArray();
                    ids.add(ControlCodec.unpack(value, "native-v1", 3)[2]);
                }
            assertEquals(2, ids.size());
            capture("overlapping-helper-native-ids", proxy);
        }
    }

    @Test
    void lostNativePartResponseKeepsFrozenManifestAndCanRebuild() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            byte[] data = new byte[9 * 1024 * 1024];
            String hash = ControlCodec.hash(data);
            var t = s.beginUpload(new UploadSpec("s3", "meeting", "lost-part", data.length));
            var r = s.writePart(t, 1, new ByteArrayInputStream(data), data.length, hash);
            proxy.arm("drop", p -> p.startsWith("PUT ") && p.contains("partNumber=1"));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.complete(t, List.of(r), hash));
            error(ErrorCode.INVALID_UPLOAD_STATE, () -> s.writePart(t, 2, body(""), 0, hash("")));
            assertEquals(hash, s.resume(t).sha256());
            assertFalse(
                    f.admin
                            .listMultipartUploads(b -> b.bucket(f.config.dataBucket()))
                            .uploads()
                            .isEmpty());
            capture("lost-native-part", proxy);
        }
    }

    @Test
    void corruptedSpecOrResultCannotBeTrustedAndFalseMetadataIsNotAContentDigest()
            throws Exception {
        try (var f = new S3Fixture("s3");
                var s = f.open()) {
            var t = s.beginUpload(new UploadSpec("s3", "meeting", "spec-corrupt", 0));
            String session = t.sessionId().split("\\.")[0];
            f.admin.putObject(
                    b -> b.bucket(f.config.controlBucket()).key("sessions/v1/" + session + "/spec"),
                    RequestBody.fromBytes(bytes("corrupt")));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.abort(t));
            var completed = s.beginUpload(new UploadSpec("s3", "meeting", "result-corrupt", 3));
            var r = s.writePart(completed, 1, body("abc"), 3, hash("abc"));
            s.complete(completed, List.of(r), hash("abc"));
            String id = completed.sessionId().split("\\.")[0];
            f.admin.putObject(
                    b -> b.bucket(f.config.controlBucket()).key("sessions/v1/" + id + "/result"),
                    RequestBody.fromBytes(bytes("corrupt")));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.resume(completed));
            s.put(key("false-metadata"), body("abc"), 3, hash("abc"));
            f.admin.putObject(
                    b ->
                            b.bucket(f.config.dataBucket())
                                    .key(new S3Layout(f.config).data(key("false-metadata")))
                                    .metadata(Map.of("sha256", hash("xyz"), "size", "10000")),
                    RequestBody.fromBytes(bytes("abc")));
            assertEquals(new BlobStat(3, hash("abc"), null), s.stat(key("false-metadata")));
            try (var in = s.read(key("false-metadata"), 1, 1)) {
                assertArrayEquals(bytes("b"), in.readAllBytes());
            }
        }
    }
}
