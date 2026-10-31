package ama.contract;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Internal storage values; never browser response DTOs. */
public final class StorageContracts {
    private StorageContracts() {}

    /** Bounded ordered immutable-object reads. These values grant no registry/business authority. */
    public record ReadChunk(StorageKey key,long size,String sha256) {
        public ReadChunk {
            if(key==null||key.objectKey().length()>512||size<1||size>64000||sha256==null||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Bounded immutable chunk required");
        }
    }
    public record ReadSequence(List<ReadChunk> chunks) {
        public static final int MAX_WIRE_BYTES=2*1024*1024;
        public ReadSequence {
            chunks=List.copyOf(chunks);
            if(chunks.isEmpty()||chunks.size()>4096)throw new IllegalArgumentException("Finite chunk sequence required");
            var first=chunks.getFirst().key();var keys=new java.util.HashSet<StorageKey>();long size=0;
            for(var chunk:chunks){var key=chunk.key();if(!key.backendId().equals(first.backendId())||!key.namespace().equals(first.namespace())||!keys.add(key))throw new IllegalArgumentException("One backend/namespace and unique ordered keys required");size=Math.addExact(size,chunk.size());}
            if(size>230400000L)throw new IllegalArgumentException("Chunk sequence exceeds two-hour PCM bound");
        }
        public long size(){return chunks.stream().mapToLong(ReadChunk::size).sum();}
    }

    public enum ErrorCode {
        INVALID_ARGUMENT, SIZE_MISMATCH, CHECKSUM_MISMATCH, NOT_FOUND, CONFLICT,
        INVALID_RANGE, INVALID_UPLOAD_STATE, STORAGE_UNAVAILABLE
    }

    /** Fixed safe message; provider messages and locators must not enter public errors. */
    public static final class StorageException extends RuntimeException {
        private final ErrorCode code;
        public StorageException(ErrorCode code) {
            super(Objects.requireNonNull(code).name());
            this.code = code;
        }
        public ErrorCode code() { return code; }
    }

    /** Logical immutable identity, never a physical path. */
    public record StorageKey(String backendId, String namespace, String objectKey) {
        public StorageKey {
            component(backendId);
            component(namespace);
            require(objectKey != null && !objectKey.isBlank());
            require(!unsafe(objectKey));
            for (String segment : objectKey.split("/", -1)) {
                require(!segment.isEmpty() && !segment.equals(".") && !segment.equals(".."));
            }
        }
    }

    /** Version ID is nullable provenance; the logical key still requires immutability. */
    public record StoredBlob(StorageKey key, long size, String sha256, String providerVersionId) {
        public StoredBlob {
            require(key != null);
            require(size >= 0);
            sha256 = hash(sha256);
        }
    }

    public record BlobStat(long size, String sha256, String providerVersionId) {
        public BlobStat {
            require(size >= 0);
            sha256 = hash(sha256);
        }
    }

    public record BlobItem(StorageKey key, BlobStat stat) {
        public BlobItem { require(key != null && stat != null); }
    }

    public record BlobPage(List<BlobItem> items, String nextCursor) {
        public BlobPage {
            require(items != null);
            require(items.stream().allMatch(Objects::nonNull));
            items = List.copyOf(items);
        }
    }

    public record UploadSpec(String backendId, String namespace, String objectKey, long expectedSize) {
        public UploadSpec {
            new StorageKey(backendId, namespace, objectKey);
            require(expectedSize >= 0);
        }
    }

    /** Server session identity only; never a provider upload ID or credential. */
    public record UploadToken(String backendId, String sessionId) {
        public UploadToken { component(backendId); component(sessionId); }
    }

    /** Server-owned receipt; SHA-256 must never contain an ETag. */
    public record PartReceipt(int part, long size, String sha256, String providerToken) {
        public PartReceipt {
            require(part > 0 && size >= 0);
            sha256 = hash(sha256);
            require(providerToken != null && !providerToken.isBlank());
        }
    }

    private static String hash(String value) {
        require(value != null && value.matches("[0-9a-fA-F]{64}"));
        return value.toLowerCase(Locale.ROOT);
    }

    private static void component(String value) {
        require(value != null && !value.isBlank());
        require(!unsafe(value) && value.indexOf('/') < 0);
        require(!value.equals(".") && !value.equals(".."));
        require(value.codePoints().noneMatch(Character::isWhitespace));
    }

    private static boolean unsafe(String value) {
        return value.indexOf('\\') >= 0 || value.indexOf('%') >= 0 || value.indexOf(':') >= 0
            || value.codePoints().anyMatch(Character::isISOControl);
    }

    private static void require(boolean valid) {
        if (!valid) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
    }
}
