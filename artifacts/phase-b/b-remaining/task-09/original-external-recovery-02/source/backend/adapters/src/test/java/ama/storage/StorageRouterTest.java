package ama.storage;

import ama.contract.StorageContracts.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageRouterTest {
    private static final String HASH = "a".repeat(64);
    // Sentinels qualify routing only; they implement no storage semantics.
    private FileStoragePort backend(String id) {
        return (FileStoragePort) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{FileStoragePort.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "stat" -> new BlobStat(id.equals("old") ? 7 : 9, HASH, null);
                    case "beginUpload" -> new UploadToken(id, "session-1");
                    case "complete" -> new StoredBlob(new StorageKey(id, "ns", "object"), 7, HASH, null);
                    default -> throw new AssertionError("Unexpected provider operation");
                };
            });
    }

    @Test void defaultChangePreservesHistoricalKeysAndActiveUploads() {
        var original = new StorageRouter(Map.of("old", backend("old"), "new", backend("new")), "old");
        var token = original.beginUpload(new UploadSpec(original.defaultWriteBackendId(), "ns", "object", 7));
        var changed = original.withDefaultWriteBackendId("new");
        assertEquals("old", token.backendId());
        assertEquals(7, changed.stat(new StorageKey("old", "ns", "object")).size());
        assertEquals("old", changed.complete(token, List.of(), HASH).key().backendId());
        assertEquals("new", changed.beginUpload(new UploadSpec(changed.defaultWriteBackendId(), "ns", "next", 9)).backendId());
        assertEquals("old", original.defaultWriteBackendId());
    }

    @Test void missingHistoricalBackendFailsClosedWithoutFallback() {
        var router = new StorageRouter(Map.of("new", backend("new")), "new");
        assertEquals(ErrorCode.STORAGE_UNAVAILABLE, assertThrows(StorageException.class,
            () -> router.stat(new StorageKey("missing", "ns", "object"))).code());
        assertEquals(ErrorCode.STORAGE_UNAVAILABLE, assertThrows(StorageException.class,
            () -> router.complete(new UploadToken("missing", "session"), List.of(), HASH)).code());
        assertEquals(ErrorCode.STORAGE_UNAVAILABLE, assertThrows(StorageException.class,
            () -> router.withDefaultWriteBackendId("missing")).code());
    }

    @Test void callerCannotReplaceBackendDefinitionAfterConstruction() {
        var definitions = new HashMap<String, FileStoragePort>();
        definitions.put("old", backend("old"));
        var router = new StorageRouter(definitions, "old");
        definitions.put("old", backend("new"));
        definitions.clear();
        assertEquals(7, router.stat(new StorageKey("old", "ns", "object")).size());
    }

    @Test void unsafeLogicalKeysAreRejectedBeforeRouting() {
        for (String key : new String[]{"", "/absolute", "../escape", "a/../b", "a/./b",
                "a//b", "a/", "C:/escape", "a\\b", "a%2fb", "a\u0000b"}) {
            assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(StorageException.class,
                () -> new StorageKey("old", "ns", key), key).code());
        }
        for (String id : new String[]{"", " ", "../backend", "a/b", "a%b"}) {
            assertThrows(StorageException.class, () -> new StorageKey(id, "ns", "object"));
            assertThrows(StorageException.class, () -> new StorageKey("old", id, "object"));
        }
        assertThrows(StorageException.class, () -> new StorageKey(null, "ns", "object"));
        assertDoesNotThrow(() -> new StorageKey("old-1", "meeting_1", "objects/2026/report.pdf"));
    }

    @Test void metadataRejectsFalseHashesAndNegativeSizes() {
        for (String hash : new String[]{"etag", "a".repeat(63), "g".repeat(64), ""}) {
            assertThrows(StorageException.class, () -> new BlobStat(0, hash, null));
        }
        assertEquals(HASH, new BlobStat(0, "A".repeat(64), null).sha256());
        assertThrows(StorageException.class, () -> new BlobStat(-1, HASH, null));
        assertThrows(StorageException.class, () -> new UploadSpec("old", "ns", "object", -1));
        assertThrows(StorageException.class, () -> new PartReceipt(0, 1, HASH, "receipt"));
    }

    @Test void blobPageCannotBeChangedByCaller() {
        var items = new ArrayList<BlobItem>();
        items.add(new BlobItem(new StorageKey("old", "ns", "object"), new BlobStat(7, HASH, null)));
        var page = new BlobPage(items, null);
        items.clear();
        assertEquals(1, page.items().size());
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
    }

    @Test void everyOperationUsesExplicitBackendAndPreservesArguments() {
        var key = new StorageKey("old", "ns", "object");
        var token = new UploadToken("old", "session");
        var body = new java.io.ByteArrayInputStream(new byte[]{1, 2});
        var receipt = new PartReceipt(1, 2, HASH, "opaque");
        var stored = new StoredBlob(key, 2, HASH, "version");
        var page = new BlobPage(List.of(new BlobItem(key, new BlobStat(2, HASH, "version"))), "next");
        var remaining = new HashMap<String, Object[]>();
        remaining.put("put", new Object[]{key, body, 2L, HASH});
        remaining.put("read", new Object[]{key, 1L, 1L});
        remaining.put("delete", new Object[]{key});
        remaining.put("writePart", new Object[]{token, 1, body, 2L, HASH});
        remaining.put("abort", new Object[]{token});
        remaining.put("list", new Object[]{"old", "ns", "objects/", "cursor", 10});
        FileStoragePort selected = (FileStoragePort) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{FileStoragePort.class}, (proxy, method, args) -> {
                assertArrayEquals(remaining.remove(method.getName()), args);
                return switch (method.getName()) {
                    case "put" -> stored;
                    case "read" -> body;
                    case "writePart" -> receipt;
                    case "list" -> page;
                    default -> null;
                };
            });
        var router = new StorageRouter(Map.of("old", selected, "new", backend("new")), "new");
        assertSame(stored, router.put(key, body, 2, HASH));
        assertSame(body, router.read(key, 1, 1));
        router.delete(key);
        assertSame(receipt, router.writePart(token, 1, body, 2, HASH));
        router.abort(token);
        assertSame(page, router.list("old", "ns", "objects/", "cursor", 10));
        assertTrue(remaining.isEmpty());
    }

    @Test void uploadAndMetadataCannotCarryMissingIdentityOrReceipt() {
        assertThrows(StorageException.class, () -> new UploadToken("old", "../session"));
        assertThrows(StorageException.class, () -> new StoredBlob(null, 0, HASH, null));
        assertThrows(StorageException.class, () -> new PartReceipt(1, 0, HASH, null));
        assertThrows(StorageException.class, () -> new BlobItem(null, new BlobStat(0, HASH, null)));
        assertThrows(StorageException.class, () -> new BlobPage(null, null));
    }
}
