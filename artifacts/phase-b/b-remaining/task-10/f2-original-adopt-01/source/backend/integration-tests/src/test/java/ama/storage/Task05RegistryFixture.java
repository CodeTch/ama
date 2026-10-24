package ama.storage;

import ama.storage.s3.*;

import java.nio.file.Path;

final class Task05RegistryFixture implements AutoCloseable {
    final Task05NativeFixture local;
    final S3Fixture s3;
    final ManagedBackendId.Definition binding;
    final FileStoragePort storage;

    Task05RegistryFixture(Path root) throws Exception {
        if ("s3".equals(System.getenv("AMA_STORAGE_CONTRACT"))) {
            local = null;
            s3 = new S3Fixture("infrastructure-fixture");
            var c = s3.config;
            binding =
                    new ManagedBackendId.S3(
                            c.endpoint().toString(),
                            c.region(),
                            c.dataBucket(),
                            c.controlBucket(),
                            c.pathStyle(),
                            c.keyBudget());
            storage =
                    new S3FileStorage(
                            S3Fixture.config(
                                    binding.id(), c.endpoint(), c.dataBucket(), c.controlBucket()));
        } else {
            local = new Task05NativeFixture(root);
            s3 = null;
            binding = local.binding;
            storage = local.startServer();
        }
    }

    FileBackendCatalog.Backend backend(FileStoragePort adapter) {
        return new FileBackendCatalog.Backend(
                binding.id(), binding.type(), 1, "WRITABLE", adapter, binding);
    }

    public void close() throws Exception {
        if (local != null) local.close();
        if (storage instanceof S3FileStorage client) client.close();
        if (s3 != null) s3.close();
    }
}
