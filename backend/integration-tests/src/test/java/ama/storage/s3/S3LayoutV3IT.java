package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.sync.RequestBody;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class S3LayoutV3IT {
    @Test
    void deleteNeverRemovesAnUnboundPhysicalObject() throws Exception {
        try (var f = new S3Fixture("s3");
                var s = f.open()) {
            s.put(key("bound"), body("x"), 1, hash("x"));
            String path = new S3Layout(f.config).data(key("unbound"));
            f.admin.putObject(
                    b -> b.bucket(f.config.dataBucket()).key(path),
                    RequestBody.fromBytes(bytes("foreign")));
            s.delete(key("unbound"));
            assertArrayEquals(
                    bytes("foreign"),
                    f.admin
                            .getObjectAsBytes(b -> b.bucket(f.config.dataBucket()).key(path))
                            .asByteArray());
        }
    }

    static String query(String path, String key) {
        return Arrays.stream(path.substring(path.indexOf('?') + 1).split("&"))
                .map(p -> p.split("=", 2))
                .filter(p -> p[0].equals(key))
                .map(p -> java.net.URLDecoder.decode(p[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }

    @Test
    void real512And1024BoundaryKeysUseShallowSignedRequestsAndNoOverbudgetIO() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("b".repeat(64), proxy.endpoint());
                var s = f.open()) {
            var layout = new S3Layout(f.config);
            String ns = "n".repeat(64);
            for (String name :
                    List.of(
                            "a".repeat(512),
                            "a".repeat(942),
                            String.join("/", Collections.nCopies(256, "a")))) {
                StorageKey k = new StorageKey(f.config.backendId(), ns, name);
                String path = layout.data(k);
                s.put(k, body("abc"), 3, hash("abc"));
                assertEquals(hash("abc"), s.stat(k).sha256());
                try (var in = s.read(k, 1, 1)) {
                    assertEquals("b", new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                assertArrayEquals(
                        bytes("abc"),
                        f.admin
                                .getObjectAsBytes(b -> b.bucket(f.config.dataBucket()).key(path))
                                .asByteArray());
                assertEquals(
                        List.of(k),
                        s.list(k.backendId(), ns, name, null, 2).items().stream()
                                .map(BlobItem::key)
                                .toList());
            }
            proxy.settle();
            int count = proxy.events.size();
            error(
                    ErrorCode.INVALID_ARGUMENT,
                    () ->
                            s.put(
                                    new StorageKey(f.config.backendId(), ns, "a".repeat(943)),
                                    body(""),
                                    0,
                                    hash("")));
            error(
                    ErrorCode.INVALID_ARGUMENT,
                    () ->
                            s.beginUpload(
                                    new UploadSpec(f.config.backendId(), ns, ".".repeat(512), 0)));
            proxy.settle();
            assertEquals(count, proxy.events.size());
            var removed = new StorageKey(f.config.backendId(), ns, "a".repeat(512));
            s.delete(removed);
            error(ErrorCode.NOT_FOUND, () -> s.stat(removed));
            capture("v3-key-budget", proxy);
        }
    }

    @Test
    void listExact199AndExtensionsCoexistWithCanonicalNarrowPrefixesAndCursorIsolation()
            throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            var layout = new S3Layout(f.config);
            String p = "a".repeat(199);
            List<String> names =
                    new ArrayList<>(
                            List.of(
                                    p,
                                    p + "a",
                                    p + "b",
                                    "a".repeat(198) + "/b",
                                    "a".repeat(198) + "~",
                                    "outside"));
            for (String name : names)
                s.put(
                        key(name),
                        body(name.length() + ""),
                        (name.length() + "").length(),
                        hash(name.length() + ""));
            for (String prefix : List.of("", p, "a".repeat(198) + "/", "a".repeat(198) + "~")) {
                proxy.settle();
                proxy.events.clear();
                List<String> found = new ArrayList<>();
                String cursor = null;
                do {
                    var page = s.list("s3", "meeting", prefix, cursor, 1);
                    found.addAll(page.items().stream().map(i -> i.key().objectKey()).toList());
                    cursor = page.nextCursor();
                } while (cursor != null);
                assertEquals(
                        names.stream().filter(n -> n.startsWith(prefix)).sorted().toList(), found);
                proxy.settle();
                String query = layout.prefix("meeting", prefix);
                capture("v3-boundary-list", proxy);
                assertTrue(
                        proxy.events.stream()
                                .filter(e -> e.path().contains("list-type="))
                                .allMatch(
                                        e ->
                                                query.equals(query(e.path(), "prefix"))
                                                        && "1"
                                                                .equals(
                                                                        query(
                                                                                e.path(),
                                                                                "max-keys"))));
            }
            String old =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    ControlCodec.pack(
                                            "cursor-v2",
                                            f.config.identity(),
                                            "meeting",
                                            "",
                                            "provider",
                                            "last"));
            error(ErrorCode.INVALID_ARGUMENT, () -> s.list("s3", "meeting", "", old, 1));
            String cursor = s.list("s3", "meeting", "", null, 1).nextCursor();
            error(ErrorCode.INVALID_ARGUMENT, () -> s.list("s3", "other", "", cursor, 1));
        }
    }

    @Test
    void forcedScopeBindingCollisionFailsClosedAndAbsentScopeListsEmpty() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            var layout = new S3Layout(f.config);
            assertTrue(s.list("s3", "absent", "", null, 2).items().isEmpty());
            byte[] wrong = layout.scopeBinding("other");
            f.admin.putObject(
                    b -> b.bucket(f.config.controlBucket()).key(layout.scopeKey("meeting")),
                    RequestBody.fromBytes(wrong));
            error(
                    ErrorCode.STORAGE_UNAVAILABLE,
                    () -> s.put(key("collision"), body("x"), 1, hash("x")));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.list("s3", "meeting", "", null, 2));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.stat(key("collision")));
            error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.delete(key("collision")));
            assertArrayEquals(
                    wrong,
                    f.admin
                            .getObjectAsBytes(
                                    b ->
                                            b.bucket(f.config.controlBucket())
                                                    .key(layout.scopeKey("meeting")))
                            .asByteArray());
            assertTrue(
                    f.admin
                            .listObjectsV2(b -> b.bucket(f.config.dataBucket()))
                            .contents()
                            .isEmpty());
            capture("v3-scope-collision", proxy);
        }
    }
}
