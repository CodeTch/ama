package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

class S3MultipartIT {
    @Test
    void moreThanNativePartCeilingTinyLogicalPartsAreCoalesced() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var writers = Executors.newFixedThreadPool(8)) {
            int count = 10001;
            var token = s.beginUpload(new UploadSpec("s3", "meeting", "many", count));
            var parts = new ArrayList<PartReceipt>();
            List<Future<PartReceipt>> writes = new ArrayList<>();
            for (int n = 1; n <= count; n++) {
                int part = n;
                writes.add(writers.submit(() -> s.writePart(token, part, body("a"), 1, hash("a"))));
            }
            for (var write : writes) parts.add(write.get());
            var result = s.complete(token, parts, hash("a".repeat(count)));
            assertEquals(count, result.size());
            try (var in = s.read(key("many"), 0, count)) {
                assertEquals(
                        "a".repeat(count),
                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            proxy.settle();
            assertEquals(
                    1,
                    proxy.events.stream()
                            .filter(e -> e.method().equals("PUT") && e.path().contains("/data/v3/"))
                            .count());
            assertEquals(
                    0, proxy.events.stream().filter(e -> e.path().contains("partNumber=")).count());
            capture("10001-tiny-logical", proxy);
        }
    }

    @Test
    void emptyLogicalPartsAndMixedBoundariesUseLegalNativeIntervals() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            var empty = s.beginUpload(new UploadSpec("s3", "meeting", "empty-multi", 0));
            var a = s.writePart(empty, 1, body(""), 0, hash(""));
            var b = s.writePart(empty, 2, body(""), 0, hash(""));
            assertEquals(0, s.complete(empty, List.of(a, b), hash("")).size());
            byte[] large = new byte[9 * 1024 * 1024 + 37];
            Arrays.fill(large, (byte) 'q');
            var t =
                    s.beginUpload(
                            new UploadSpec("s3", "meeting", "large-logical", large.length + 6));
            var p2 =
                    s.writePart(
                            t,
                            2,
                            new ByteArrayInputStream(large),
                            large.length,
                            ControlCodec.hash(large));
            var p1 = s.writePart(t, 1, body("abc"), 3, hash("abc"));
            var p3 = s.writePart(t, 3, body("def"), 3, hash("def"));
            var d = ControlCodec.digest();
            d.update(bytes("abc"));
            d.update(large);
            d.update(bytes("def"));
            String expected = ControlCodec.hex(d.digest());
            assertEquals(expected, s.complete(t, List.of(p1, p2, p3), expected).sha256());
            proxy.settle();
            var uploads =
                    proxy.events.stream()
                            .filter(
                                    e ->
                                            e.method().equals("PUT")
                                                    && e.path().contains("partNumber="))
                            .toList();
            assertEquals(2, uploads.size());
            // Native content-length may include SDK aws-chunked framing; decoded-size is signed
            // explicitly.
            assertTrue(uploads.stream().allMatch(e -> e.status() == 200));
            capture("coalesced-native", proxy);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"abort", "complete"})
    void lateSlotCannotAcknowledgeAfterSingleTerminalDecision(String terminal) throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var pool = Executors.newSingleThreadExecutor()) {
            var token = s.beginUpload(new UploadSpec("s3", "meeting", "late", 3));
            var selected = s.writePart(token, 1, body("abc"), 3, hash("abc"));
            var barrier = proxy.arm("pause", p -> p.startsWith("PUT ") && p.endsWith("/parts/2"));
            var late = pool.submit(() -> s.writePart(token, 2, body(""), 0, hash("")));
            assertTrue(barrier.reached.await(20, TimeUnit.SECONDS));
            if (terminal.equals("abort")) s.abort(token);
            else s.complete(token, List.of(selected), hash("abc"));
            barrier.release.countDown();
            var error =
                    assertThrows(ExecutionException.class, () -> late.get(20, TimeUnit.SECONDS));
            assertEquals(
                    ErrorCode.INVALID_UPLOAD_STATE, ((StorageException) error.getCause()).code());
            if (terminal.equals("abort")) error(ErrorCode.NOT_FOUND, () -> s.stat(key("late")));
            else assertEquals(hash("abc"), s.resume(token).sha256());
            capture("late-slot-" + terminal, proxy);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"drop", "error200"})
    void nativeCompleteCommittedResponseLossAndEmbeddedErrorReconcile(String failure)
            throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            byte[] bytes = new byte[9 * 1024 * 1024];
            Arrays.fill(bytes, (byte) 'z');
            String hash = ControlCodec.hash(bytes);
            var t = s.beginUpload(new UploadSpec("s3", "meeting", "native-loss", bytes.length));
            var r = s.writePart(t, 1, new ByteArrayInputStream(bytes), bytes.length, hash);
            proxy.arm(failure, p -> p.startsWith("POST ") && p.contains("uploadId="));
            assertEquals(hash, s.complete(t, List.of(r), hash).sha256());
            error(
                    ErrorCode.CONFLICT,
                    () -> s.put(key("native-loss"), body("different"), 9, hash("different")));
            assertEquals(hash, s.resume(t).sha256());
            capture("native-complete-" + failure, proxy);
        }
    }

    @Test
    void completeAbortRaceHasOneTerminalAndNeverTwoPublicationDecisions() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var pool = Executors.newFixedThreadPool(2)) {
            for (int trial = 0; trial < 4; trial++) {
                var token =
                        s.beginUpload(new UploadSpec("s3", "meeting", "terminal-race-" + trial, 3));
                var r = s.writePart(token, 1, body("abc"), 3, hash("abc"));
                var barrier = new CyclicBarrier(2);
                var complete =
                        pool.submit(
                                () -> {
                                    barrier.await();
                                    try {
                                        return s.complete(token, List.of(r), hash("abc"));
                                    } catch (StorageException e) {
                                        return e.code();
                                    }
                                });
                var abort =
                        pool.submit(
                                () -> {
                                    barrier.await();
                                    s.abort(token);
                                    return true;
                                });
                assertTrue(abort.get(20, TimeUnit.SECONDS));
                Object decision = complete.get(20, TimeUnit.SECONDS);
                if (decision instanceof StoredBlob)
                    assertEquals(hash("abc"), s.resume(token).sha256());
                else {
                    assertEquals(ErrorCode.INVALID_UPLOAD_STATE, decision);
                    error(ErrorCode.INVALID_UPLOAD_STATE, () -> s.resume(token));
                }
            }
            capture("terminal-races", proxy);
        }
    }
}
