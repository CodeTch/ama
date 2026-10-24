package ama.storage.internal;

import ama.contract.StorageContracts.*;

import java.util.List;

/** Internal service wire only. These values must never be reused as browser DTOs. */
public final class LocalFileWire {
    private LocalFileWire() {}

    public record Request(
            StorageKey key,
            UploadSpec spec,
            UploadToken token,
            Integer part,
            Long size,
            String sha256,
            List<PartReceipt> parts,
            String backendId,
            String namespace,
            String prefix,
            String cursor,
            Integer limit,
            Long offset,
            Long length) {}

    public record Failure(String code) {}
}
