package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit local group only. S3 and business registry authorization are separate future gates. */
class FileStorageContractIT {
    @TempDir Path temporary;

    static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static ByteArrayInputStream body(String s) {
        return new ByteArrayInputStream(bytes(s));
    }

    static StorageKey key(String name) {
        return new StorageKey("local", "meeting", name);
    }

    static byte[] read(FileStoragePort store, StorageKey key, long offset, long length)
            throws IOException {
        try (var in = store.read(key, offset, length)) {
            return in.readAllBytes();
        }
    }

    static void error(ErrorCode code, org.junit.jupiter.api.function.Executable call) {
        assertEquals(code, assertThrows(StorageException.class, call).code());
    }

    @ParameterizedTest(name = "{0}: immutable bytes, exact ranges and content conflicts")
    @ValueSource(strings = {"local"})
    void localImmutableContract(String backend) throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var s = fixture.startServer();
            byte[] value = bytes("0123456789");
            String hash = hash(value);
            assertEquals(
                    10, s.put(key("path/a"), new ByteArrayInputStream(value), 10, hash).size());
            assertEquals(hash, s.stat(key("path/a")).sha256());
            assertEquals(
                    hash, s.put(key("path/a"), new ByteArrayInputStream(value), 10, hash).sha256());
            error(
                    ErrorCode.CONFLICT,
                    () -> s.put(key("path/a"), body("different"), 9, hash(bytes("different"))));
            for (var range :
                    List.of(
                            new Object[] {0L, 10L, "0123456789"},
                            new Object[] {0L, 1L, "0"},
                            new Object[] {3L, 4L, "3456"},
                            new Object[] {9L, 1L, "9"},
                            new Object[] {10L, 0L, ""})) {
                try (var in = s.read(key("path/a"), (long) range[0], (long) range[1])) {
                    assertArrayEquals(bytes((String) range[2]), in.readAllBytes());
                }
            }
            error(ErrorCode.INVALID_RANGE, () -> s.read(key("path/a"), -1, 1));
            error(ErrorCode.INVALID_RANGE, () -> s.read(key("path/a"), 9, 2));
            error(ErrorCode.INVALID_RANGE, () -> s.read(key("path/a"), Long.MAX_VALUE, 2));
            error(ErrorCode.INVALID_RANGE, () -> s.read(key("path/a"), 11, 0));
            s.delete(key("path/a"));
            s.delete(key("path/a"));
            error(ErrorCode.NOT_FOUND, () -> s.stat(key("path/a")));
            assertEquals(0, s.put(key("empty"), body(""), 0, hash(bytes(""))).size());
            assertArrayEquals(new byte[0], read(s, key("empty"), 0, 0));
        }
    }

    @ParameterizedTest(name = "{0}: size, checksum, interrupted stream")
    @ValueSource(strings = {"local"})
    void localRejectsUnverifiedBytes(String backend) throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var s = fixture.startServer();
            String abc = hash(bytes("abc"));
            error(ErrorCode.SIZE_MISMATCH, () -> s.put(key("short"), body("ab"), 3, abc));
            error(ErrorCode.SIZE_MISMATCH, () -> s.put(key("long"), body("abcd"), 3, abc));
            error(ErrorCode.CHECKSUM_MISMATCH, () -> s.put(key("wrong"), body("xyz"), 3, abc));
            InputStream broken =
                    new InputStream() {
                        int n;

                        public int read() throws IOException {
                            if (n++ == 1) throw new IOException("injected stream loss");
                            return 'a';
                        }
                    };
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.put(key("interrupted"), broken, 3, abc));
            for (String key : List.of("short", "long", "wrong", "interrupted"))
                error(ErrorCode.NOT_FOUND, () -> s.stat(key(key)));
        }
    }

    @ParameterizedTest(name = "{0}: multipart replay, ordered receipts, terminal states")
    @ValueSource(strings = {"local"})
    void localMultipartContract(String backend) throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var s = fixture.startServer();
            var token = s.beginUpload(new UploadSpec("local", "meeting", "multi", 6));
            assertEquals("local", token.backendId());
            var b = s.writePart(token, 2, body("def"), 3, hash(bytes("def")));
            var a = s.writePart(token, 1, body("abc"), 3, hash(bytes("abc")));
            assertEquals(a, s.writePart(token, 1, body("abc"), 3, hash(bytes("abc"))));
            error(
                    ErrorCode.CONFLICT,
                    () -> s.writePart(token, 1, body("xyz"), 3, hash(bytes("xyz"))));
            error(
                    ErrorCode.INVALID_ARGUMENT,
                    () -> s.complete(token, List.of(b, a), hash(bytes("abcdef"))));
            error(
                    ErrorCode.INVALID_ARGUMENT,
                    () -> s.complete(token, List.of(a, a), hash(bytes("abcdef"))));
            error(ErrorCode.SIZE_MISMATCH, () -> s.complete(token, List.of(a), hash(bytes("abc"))));
            var forged = new PartReceipt(1, 3, a.sha256(), "another-session");
            error(
                    ErrorCode.INVALID_ARGUMENT,
                    () -> s.complete(token, List.of(forged, b), hash(bytes("abcdef"))));
            var result = s.complete(token, List.of(a, b), hash(bytes("abcdef")));
            assertEquals(result, s.complete(token, List.of(a, b), hash(bytes("abcdef"))));
            error(
                    ErrorCode.CONFLICT,
                    () -> s.complete(token, List.of(a, b), hash(bytes("ABCDEF"))));
            s.abort(token);
            assertArrayEquals(bytes("abcdef"), read(s, key("multi"), 0, 6));
            error(
                    ErrorCode.INVALID_UPLOAD_STATE,
                    () -> s.writePart(token, 3, body("g"), 1, hash(bytes("g"))));
            var aborted = s.beginUpload(new UploadSpec("local", "meeting", "aborted", 3));
            var receipt = s.writePart(aborted, 1, body("abc"), 3, hash(bytes("abc")));
            s.abort(aborted);
            s.abort(aborted);
            error(
                    ErrorCode.INVALID_UPLOAD_STATE,
                    () -> s.complete(aborted, List.of(receipt), hash(bytes("abc"))));
            error(
                    ErrorCode.INVALID_UPLOAD_STATE,
                    () -> s.writePart(aborted, 1, body("abc"), 3, hash(bytes("abc"))));
        }
    }

    @ParameterizedTest(name = "{0}: concurrent clients have one immutable winner")
    @ValueSource(strings = {"local"})
    void localConcurrentWinner(String backend) throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"));
                var pool = Executors.newFixedThreadPool(2)) {
            var first = fixture.startServer();
            var second = fixture.client("writer");
            var start = new CountDownLatch(1);
            List<Future<Object>> writes = new ArrayList<>();
            for (var pair : List.of(Map.entry(first, "abc"), Map.entry(second, "xyz")))
                writes.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        return pair.getKey()
                                                .put(
                                                        key("race"),
                                                        body(pair.getValue()),
                                                        3,
                                                        hash(bytes(pair.getValue())));
                                    } catch (StorageException e) {
                                        return e.code();
                                    }
                                }));
            start.countDown();
            List<Object> results =
                    List.of(
                            writes.get(0).get(15, TimeUnit.SECONDS),
                            writes.get(1).get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(StoredBlob.class::isInstance).count());
            assertEquals(1, results.stream().filter(ErrorCode.CONFLICT::equals).count());
            StoredBlob winner =
                    (StoredBlob)
                            results.stream()
                                    .filter(StoredBlob.class::isInstance)
                                    .findFirst()
                                    .orElseThrow();
            assertEquals(winner.sha256(), hash(read(first, key("race"), 0, 3)));
        }
    }

    @ParameterizedTest(name = "{0}: bounded listing and cursor isolation")
    @ValueSource(strings = {"local"})
    void localListingContract(String backend) throws Exception {
        try (var fixture = new LocalFixture(temporary.resolve("volume"))) {
            var s = fixture.startServer();
            String hash = hash(bytes("a"));
            for (String name : List.of("prefix/c", "prefix/a", "prefix/b", "other"))
                s.put(key(name), body("a"), 1, hash);
            s.put(new StorageKey("local", "another", "prefix/a"), body("a"), 1, hash);
            var first = s.list("local", "meeting", "prefix/", null, 2);
            assertEquals(
                    List.of("prefix/a", "prefix/b"),
                    first.items().stream().map(i -> i.key().objectKey()).toList());
            assertNotNull(first.nextCursor());
            var second = s.list("local", "meeting", "prefix/", first.nextCursor(), 2);
            assertEquals(
                    List.of("prefix/c"),
                    second.items().stream().map(i -> i.key().objectKey()).toList());
            assertNull(second.nextCursor());
            error(
                    ErrorCode.INVALID_ARGUMENT,
                    () -> s.list("local", "another", "prefix/", first.nextCursor(), 2));
            error(
                    ErrorCode.INVALID_ARGUMENT,
                    () -> s.list("local", "meeting", "other", first.nextCursor(), 2));
            error(ErrorCode.INVALID_ARGUMENT, () -> s.list("local", "meeting", "", null, 1001));
        }
    }
}
