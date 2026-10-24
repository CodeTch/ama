package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import software.amazon.awssdk.core.sync.RequestBody;

import java.util.*;
import java.util.concurrent.*;

class S3ConcurrencyIT {
    @ParameterizedTest
    @ValueSource(strings = {"never", "Enabled", "Suspended"})
    void headGetRacePinsConcreteVersionOrRequiresOpaqueValidator(String mode) throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var pool = Executors.newSingleThreadExecutor()) {
            if (!mode.equals("never")) {
                f.version("Enabled");
                if (mode.equals("Suspended")) f.version(mode);
            }
            for (boolean delete : new boolean[] {false, true}) {
                String name = "observed-" + delete;
                s.put(key(name), body("abc"), 3, hash("abc"));
                var pause =
                        proxy.arm("pause", p -> p.startsWith("HEAD ") && p.contains("/data/v3/"));
                var read =
                        pool.submit(
                                () -> {
                                    try (var in = s.read(key(name), 1, 1)) {
                                        return in.readAllBytes();
                                    }
                                });
                assertTrue(pause.reached.await(15, TimeUnit.SECONDS));
                String physical = new S3Layout(f.config).data(key(name));
                if (delete)
                    f.admin.deleteObject(b -> b.bucket(f.config.dataBucket()).key(physical));
                else
                    f.admin.putObject(
                            b -> b.bucket(f.config.dataBucket()).key(physical),
                            RequestBody.fromBytes(bytes("xyz")));
                pause.release.countDown();
                if (mode.equals("Enabled"))
                    assertArrayEquals(bytes("b"), read.get(20, TimeUnit.SECONDS));
                else {
                    var e =
                            assertThrows(
                                    ExecutionException.class, () -> read.get(20, TimeUnit.SECONDS));
                    assertTrue(e.getCause() instanceof StorageException);
                }
            }
            capture("head-get-" + mode, proxy);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"never", "Enabled", "Suspended"})
    void independentJvmCompleteCompleteAndPutCompleteRaces(String mode) throws Exception {
        try (var f = new S3Fixture("s3");
                var s = f.open()) {
            if (!mode.equals("never")) {
                f.version("Enabled");
                if (mode.equals("Suspended")) f.version(mode);
            }
            for (boolean ordinary : new boolean[] {false, true})
                for (boolean same : new boolean[] {false, true}) {
                    String name = "race-" + ordinary + "-" + same;
                    String second = same ? "abc" : "xyz";
                    var ta = s.beginUpload(new UploadSpec("s3", "meeting", name, 3));
                    var ra = s.writePart(ta, 1, body("abc"), 3, hash("abc"));
                    var a =
                            worker(
                                    f,
                                    "complete",
                                    ta.sessionId(),
                                    hash("abc"),
                                    "1,3," + ra.sha256() + "," + ra.providerToken());
                    Worker b;
                    if (ordinary) b = worker(f, "put", name, second);
                    else {
                        var tb = s.beginUpload(new UploadSpec("s3", "meeting", name, 3));
                        var rb = s.writePart(tb, 1, body(second), 3, hash(second));
                        b =
                                worker(
                                        f,
                                        "complete",
                                        tb.sessionId(),
                                        hash(second),
                                        "1,3," + rb.sha256() + "," + rb.providerToken());
                    }
                    a.go();
                    b.go();
                    String first = a.finish(), last = b.finish();
                    assertEquals(
                            same ? 2 : 1,
                            (first.startsWith("OK") ? 1 : 0) + (last.startsWith("OK") ? 1 : 0));
                    if (!same) assertTrue((first + last).contains("ERROR CONFLICT"));
                    assertTrue(
                            Set.of(hash("abc"), hash("xyz")).contains(s.stat(key(name)).sha256()));
                }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"error409", "error412", "nosuch"})
    void conditionalCompletionErrorsAfterActualCommitReconcile(String fault) throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            byte[] data = new byte[9 * 1024 * 1024];
            Arrays.fill(data, (byte) 11);
            String hash = ControlCodec.hash(data);
            var t = s.beginUpload(new UploadSpec("s3", "meeting", "reconcile", data.length));
            var r = s.writePart(t, 1, new java.io.ByteArrayInputStream(data), data.length, hash);
            proxy.arm(fault, p -> p.startsWith("POST ") && p.contains("uploadId="));
            assertEquals(hash, s.complete(t, List.of(r), hash).sha256());
            capture("classified-" + fault, proxy);
        }
    }
}
