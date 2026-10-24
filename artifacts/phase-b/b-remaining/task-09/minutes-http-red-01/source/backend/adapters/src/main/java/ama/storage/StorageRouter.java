package ama.storage;

import ama.contract.StorageContracts.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable routing snapshot. A changed write default shares the same pinned backend
 * definitions; existing keys/tokens are always routed by their explicit backend ID.
 * Registered adapters must already have passed their own readiness/capability checks.
 */
public final class StorageRouter implements FileStoragePort {
    private final Map<String, FileStoragePort> backends;
    private final String defaultWriteBackendId;

    public StorageRouter(Map<String, FileStoragePort> backends, String defaultWriteBackendId) {
        if (backends == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        var copy = new HashMap<String, FileStoragePort>();
        backends.forEach((id, adapter) -> {
            new UploadToken(id, "validation");
            if (adapter == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
            copy.put(id, adapter);
        });
        this.backends = Map.copyOf(copy);
        backend(defaultWriteBackendId);
        this.defaultWriteBackendId = defaultWriteBackendId;
    }

    public String defaultWriteBackendId() { return defaultWriteBackendId; }

    public StorageRouter withDefaultWriteBackendId(String id) {
        return new StorageRouter(backends, id);
    }

    private FileStoragePort backend(String id) {
        new UploadToken(id, "validation");
        var adapter = backends.get(id);
        if (adapter == null) throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);
        return adapter;
    }

    public StoredBlob put(StorageKey key, InputStream body, long size, String sha256) {
        return backend(key.backendId()).put(key, body, size, sha256);
    }
    public BlobStat stat(StorageKey key) {
        return backend(key.backendId()).stat(key);
    }
    public InputStream read(StorageKey key, long offset, long length) {
        return backend(key.backendId()).read(key, offset, length);
    }
    public void delete(StorageKey key) {
        backend(key.backendId()).delete(key);
    }
    public UploadToken beginUpload(UploadSpec spec) {
        return backend(spec.backendId()).beginUpload(spec);
    }
    public PartReceipt writePart(UploadToken token, int part, InputStream body, long size, String sha256) {
        return backend(token.backendId()).writePart(token, part, body, size, sha256);
    }
    public StoredBlob complete(UploadToken token, List<PartReceipt> parts, String sha256) {
        return backend(token.backendId()).complete(token, parts, sha256);
    }
    public void abort(UploadToken token) {
        backend(token.backendId()).abort(token);
    }
    public BlobPage list(String backendId, String namespace, String prefix, String cursor, int limit) {
        return backend(backendId).list(backendId, namespace, prefix, cursor, limit);
    }
}
