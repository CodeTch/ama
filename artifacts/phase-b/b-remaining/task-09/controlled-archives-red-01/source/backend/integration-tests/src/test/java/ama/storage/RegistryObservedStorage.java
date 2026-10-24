package ama.storage;

import ama.contract.StorageContracts.*;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only interleavings surround actual durable backend operations; never replace receipts. */
final class RegistryObservedStorage implements FileStoragePort {
    final FileStoragePort actual;
    final AtomicReference<Runnable> afterBegin = new AtomicReference<>(),
            afterPart = new AtomicReference<>(),
            beforeComplete = new AtomicReference<>(),
            afterComplete = new AtomicReference<>();
    final AtomicReference<Runnable> afterReadClose = new AtomicReference<>();

    RegistryObservedStorage(FileStoragePort actual) {
        this.actual = actual;
    }

    static void run(AtomicReference<Runnable> hook) {
        var action = hook.getAndSet(null);
        if (action != null) action.run();
    }

    public StoredBlob put(StorageKey k, InputStream b, long n, String h) {
        return actual.put(k, b, n, h);
    }

    public BlobStat stat(StorageKey k) {
        return actual.stat(k);
    }

    public InputStream read(StorageKey k, long o, long n) {
        var body = actual.read(k, o, n);
        var closed = afterReadClose.getAndSet(null);
        if (closed == null) return body;
        return new java.io.FilterInputStream(body) {
            @Override
            public void close() throws java.io.IOException {
                super.close();
                closed.run();
            }
        };
    }

    public void delete(StorageKey k) {
        actual.delete(k);
    }

    public UploadToken beginUpload(UploadSpec s) {
        var result = actual.beginUpload(s);
        run(afterBegin);
        return result;
    }

    public PartReceipt writePart(UploadToken t, int p, InputStream b, long n, String h) {
        var result = actual.writePart(t, p, b, n, h);
        run(afterPart);
        return result;
    }

    public StoredBlob complete(UploadToken t, List<PartReceipt> p, String h) {
        run(beforeComplete);
        var result = actual.complete(t, p, h);
        run(afterComplete);
        return result;
    }

    public void abort(UploadToken t) {
        actual.abort(t);
    }

    public BlobPage list(String b, String n, String p, String c, int l) {
        return actual.list(b, n, p, c, l);
    }
}
