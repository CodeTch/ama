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
    /** Checks the selected backend's current availability, without claiming object integrity.
     * Implementations may provide a bounded topology probe; older adapters retain their list check. */
    default void probe(String backendId) {list(backendId,"files","",null,1);}
    StoredBlob put(StorageKey key, InputStream body, long size, String sha256);
    BlobStat stat(StorageKey key);
    InputStream read(StorageKey key, long offset, long length);
    /** One bounded ordered stream; verifies every selected immutable chunk before complete EOF. */
    default InputStream readSequence(ReadSequence sequence) {return ChunkSequenceStream.open(this,sequence);}
    void delete(StorageKey key);
    /** Permanently fences an exclusively owned logical key before removing its current bytes.
     * A successful return survives process restart and rejects every later put/complete for
     * this key, including when it did not exist. Callers first commit their no-attachment
     * tombstone and retain the exact managed key as retry inventory. Upload parts require
     * discardUpload as well. This does not claim erasure of backups or remote versions. */
    default void retireKey(StorageKey key) {throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);}
    /** Permanently forbids every future object PUT/complete under recordings/<session UUID>/,
     * including keys absent from SQL after a rolled-back append. The key argument is exactly
     * (original backend, recordings, session UUID). This fences writes only: callers still
     * enumerate and retire the actual stored keys before claiming physical erasure. */
    default void fenceRecordingSession(StorageKey scope) {throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);}
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
    /** Permanently retires an unreferenced, exclusively owned session/key. Unlike abort,
     * this fences completion/replay at storage and removes its current data from use.
     * S3 retains a permanent entity sentinel; historical versions, staging and control
     * evidence are not claimed erased. The key is never reusable.
     * Callers must commit their no-attachment tombstone before invoking it. */
    default void discardUpload(UploadToken token, StorageKey key) {throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);}

    BlobPage list(String backendId, String namespace, String prefix, String cursor, int limit);
}
