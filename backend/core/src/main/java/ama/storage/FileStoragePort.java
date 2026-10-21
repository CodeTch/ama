package ama.storage;

import ama.contract.StorageContracts.*;
import java.io.InputStream;
import java.util.List;

/**
 * Internal immutable-blob port. Authorization and registry commits belong to the caller.
 * Implementations verify actual size/SHA-256 before reporting success. Logical keys may
 * never be overwritten; providerVersionId is provenance, not mutable-version selection.
 * Reads return exactly [offset, offset + length), reject invalid/overflow/out-of-bounds
 * ranges with INVALID_RANGE, and allow a zero-length read at EOF. Callers close streams.
 * Upload tokens pin a backend for the session. Complete and abort are idempotent;
 * receipts and provider tokens are server-owned. List cursors must remain scoped to
 * backend, namespace and prefix. No implementation may fall back to another backend.
 */
public interface FileStoragePort {
    StoredBlob put(StorageKey key, InputStream body, long size, String sha256);
    BlobStat stat(StorageKey key);
    InputStream read(StorageKey key, long offset, long length);
    void delete(StorageKey key);
    UploadToken beginUpload(UploadSpec spec);
    PartReceipt writePart(UploadToken token, int part, InputStream body, long size, String sha256);
    StoredBlob complete(UploadToken token, List<PartReceipt> parts, String sha256);
    void abort(UploadToken token);
    BlobPage list(String backendId, String namespace, String prefix, String cursor, int limit);
}
