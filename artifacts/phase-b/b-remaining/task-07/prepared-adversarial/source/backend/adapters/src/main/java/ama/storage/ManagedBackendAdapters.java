package ama.storage;

import ama.storage.s3.*;

import java.net.URI;

import javax.net.ssl.SSLContext;

/** Production assembly derives identity from the exact physical values handed to the adapter. */
public final class ManagedBackendAdapters {
    private ManagedBackendAdapters() {}

    public static ManagedBackendId.S3 definition(S3Config config) {
        return new ManagedBackendId.S3(
                config.endpoint().toString(),
                config.region(),
                config.dataBucket(),
                config.controlBucket(),
                config.pathStyle(),
                config.keyBudget());
    }

    public static FileBackendCatalog.Backend s3(S3Config config, long revision, String status) {
        var binding = definition(config);
        ManagedBackendId.require(config.backendId(), binding);
        return new FileBackendCatalog.Backend(
                config.backendId(), "s3", revision, status, new S3FileStorage(config), binding);
    }

    public static FileBackendCatalog.Backend local(
            String id,
            ManagedBackendId.Local binding,
            SSLContext tls,
            long revision,
            String status) {
        ManagedBackendId.require(id, binding);
        return new FileBackendCatalog.Backend(
                id,
                "local",
                revision,
                status,
                new LocalFileClient(URI.create(binding.serviceOrigin()), tls),
                binding);
    }
}
