package ama.storage;

import ama.storage.s3.S3Fixture;

import java.nio.file.Path;

/** Runs the same assertions against independently selected real backends. */
final class ContractFixture implements AutoCloseable {
    private final LocalFixture local;
    private final S3Fixture s3;

    ContractFixture(Path root) throws Exception {
        String selected = System.getenv("AMA_STORAGE_CONTRACT");
        if (selected != null && !selected.equals("local") && !selected.equals("s3"))
            throw new IllegalArgumentException("unknown storage contract fixture");
        local = "s3".equals(selected) ? null : new LocalFixture(root);
        s3 = local == null ? new S3Fixture("local") : null;
    }

    FileStoragePort startServer() throws Exception {
        return local == null ? s3.open() : local.startServer();
    }

    FileStoragePort client(String role) throws Exception {
        return local == null ? s3.open() : local.client(role);
    }

    public void close() throws Exception {
        if (local != null) local.close();
        else s3.close();
    }
}
