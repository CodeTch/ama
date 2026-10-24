package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import software.amazon.awssdk.core.sync.RequestBody;

import java.io.*;
import java.time.Duration;
import java.util.*;

class S3MissingLengthIT {
    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "close", "chunked"})
    void missingLengthsAreTypedAndCannotExhaustRangeAdmission(String framing) throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint())) {
            var c = f.config;
            var bounded =
                    new S3Config(
                            c.backendId(),
                            c.endpoint(),
                            c.region(),
                            c.dataBucket(),
                            c.controlBucket(),
                            c.credentials(),
                            c.pathStyle(),
                            c.keyBudget(),
                            c.chunkBytes(),
                            c.nativeMinimum(),
                            c.nativeMaximum(),
                            c.nativeMaxParts(),
                            c.maximumObject(),
                            2,
                            Duration.ofSeconds(3));
            try (var s = new S3FileStorage(bounded)) {
                byte[] bytes = new byte[4 * 1024 * 1024];
                Arrays.fill(bytes, (byte) 71);
                s.put(
                        key("missing-length"),
                        new ByteArrayInputStream(bytes),
                        bytes.length,
                        ControlCodec.hash(bytes));
                // Establish that the injected framing is accepted by the real SDK, with a
                // genuinely absent length; malformed chunk syntax must not fake this test.
                try (var raw = S3Fixture.admin(proxy.endpoint())) {
                    proxy.arm(
                            framing.equals("chunked") ? "chunked-no-length" : "omit-length",
                            p ->
                                    p.startsWith(framing.equals("HEAD") ? "HEAD " : "GET ")
                                            && p.contains("/data/v3/"));
                    String physical = new S3Layout(bounded).data(key("missing-length"));
                    if (framing.equals("HEAD")) {
                        assertNull(
                                raw.headObject(b -> b.bucket(bounded.dataBucket()).key(physical))
                                        .contentLength());
                    } else
                        try (var actual =
                                raw.getObject(
                                        b ->
                                                b.bucket(bounded.dataBucket())
                                                        .key(physical)
                                                        .range("bytes=0-2"))) {
                            assertNull(actual.response().contentLength());
                            assertArrayEquals(new byte[] {71, 71, 71}, actual.readAllBytes());
                        }
                }
                proxy.settle();
                proxy.reset();
                List<String> untyped = new ArrayList<>();
                for (int attempt = 0; attempt < bounded.admission() + 2; attempt++) {
                    proxy.arm(
                            framing.equals("chunked") ? "chunked-no-length" : "omit-length",
                            p ->
                                    p.startsWith(framing.equals("HEAD") ? "HEAD " : "GET ")
                                            && p.contains("/data/v3/"));
                    var error =
                            assertThrows(
                                    RuntimeException.class,
                                    () -> s.read(key("missing-length"), 0, 3));
                    if (!(error instanceof StorageException e
                            && e.code() == ErrorCode.STORAGE_UNAVAILABLE))
                        untyped.add(error.getClass().getName());
                    proxy.settle();
                    proxy.fault.set(
                            null); // A leaked permit can prevent the armed request reaching HTTP in
                    // RED.
                }
                capture("missing-length-" + framing, proxy);
                // Same adapter, after more malformed exchanges than its entire admission budget.
                try (var in = s.read(key("missing-length"), 0, 1)) {
                    assertArrayEquals(new byte[] {71}, in.readAllBytes());
                }
                assertTrue(untyped.isEmpty(), () -> "untyped missing-length failures=" + untyped);
                proxy.settle();
                var malformed = proxy.snapshot().stream().filter(WireProxy.Event::fault).toList();
                assertEquals(bounded.admission() + 2, malformed.size());
                for (var event : malformed) assertTrue(event.upstreamBytes() < 1024 * 1024);
                if (!framing.equals("HEAD")) {
                    proxy.reset();
                    proxy.arm(
                            framing.equals("chunked") ? "chunked-no-length" : "omit-length",
                            p -> p.startsWith("GET ") && p.contains("/data/v3/"));
                    error(
                            ErrorCode.STORAGE_UNAVAILABLE,
                            () -> s.read(key("missing-length"), 0, bytes.length));
                    capture("missing-length-large-" + framing, proxy);
                    assertTrue(
                            proxy.snapshot().stream()
                                            .filter(WireProxy.Event::fault)
                                            .mapToLong(WireProxy.Event::upstreamBytes)
                                            .sum()
                                    < 1024 * 1024);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"control", "stat", "staging"})
    void allActualByteReadersRejectMissingLengthsWithSanitizedStorageError(String operation)
            throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var client = S3Fixture.admin(proxy.endpoint())) {
            byte[] bytes = new byte[] {1, 2, 3};
            f.admin.putObject(
                    b -> b.bucket(f.config.controlBucket()).key("control/length"),
                    RequestBody.fromBytes(bytes));
            f.admin.putObject(
                    b -> b.bucket(f.config.dataBucket()).key("data/length"),
                    RequestBody.fromBytes(bytes));
            var io = new S3Objects(client, f.config);
            proxy.arm(
                    "omit-length",
                    p ->
                            p.startsWith("GET ")
                                    && (operation.equals("staging")
                                            ? p.contains("/chunk/")
                                            : p.contains("/length")));
            error(
                    ErrorCode.STORAGE_UNAVAILABLE,
                    () -> {
                        switch (operation) {
                            case "control" -> io.get("control/length");
                            case "stat" -> io.stat("data/length");
                            case "staging" ->
                                    new Staging(io)
                                            .stage(
                                                    ControlCodec.id(),
                                                    new ByteArrayInputStream(bytes),
                                                    bytes.length,
                                                    ControlCodec.hash(bytes));
                            default -> throw new AssertionError();
                        }
                    });
            assertArrayEquals(bytes, io.get("control/length"));
            capture("missing-length-reader-" + operation, proxy);
        }
    }
}
