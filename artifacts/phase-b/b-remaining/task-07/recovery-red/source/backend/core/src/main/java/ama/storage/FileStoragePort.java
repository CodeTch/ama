package ama.storage;

import ama.contract.StorageContracts.*;
import java.io.InputStream;
import java.util.List;

/**
 * Internal immutable-blob port. Authorization and registry commits belong to the caller.
 * Implementations verify actual size/SHA-256 before reporting success. Logical keys may
 * never be overwritten. put/complete replay of the same key and actual content succeeds;
 * differing content for an existing key fails with CONFLICT. providerVersionId is provenance, not mutable-version selection.
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

    /**
     * Completes exactly the supplied trusted immutable receipt manifest. It must be nonempty,
     * unique, ascending and contiguous from 1, and belong to this upload session. Actual selected
     * bytes must match their receipts, total the upload's expected size, and match the whole SHA-256.
     * Other physically stored parts are unselected technical artifacts: they neither enter the
     * result nor block a valid selection. Callers with a business receipt registry must still match
     * client requests to all committed receipts before freezing this manifest.
     *
     * <p>A valid completion freezes this exact manifest and hash. The same request replays; a
     * different valid manifest conflicts even if it produces identical bytes. A bad whole hash while
     * open remains retryable. After the decision, writes fail and abort preserves completion.
     */
    StoredBlob complete(UploadToken token, List<PartReceipt> parts, String sha256);
    void abort(UploadToken token);
    BlobPage list(String backendId, String namespace, String prefix, String cursor, int limit);
}
