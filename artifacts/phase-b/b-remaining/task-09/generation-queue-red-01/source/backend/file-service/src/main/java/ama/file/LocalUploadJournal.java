package ama.file;

import ama.contract.StorageContracts.*;

import java.io.*;
import java.util.*;

/** Immutable session journal, serialized across threads/processes with flock on its directory. */
final class LocalUploadJournal {
    private final LocalFileStore store;
    private final LinuxFiles fs;
    private final ImmutableFiles files;

    LocalUploadJournal(LocalFileStore store) {
        this.store = store;
        fs = store.fs;
        files = store.files;
    }

    record Completion(List<PartReceipt> parts, String sha256) {}

    UploadToken begin(UploadSpec spec) {
        store.backend(spec.backendId());
        store.check();
        String id = UUID.randomUUID().toString();
        try (var dir = fs.directory(store.uploads, id, true)) {
            files.bytes(dir, "spec", LocalFileStore.json(spec));
            ack(id, dir);
            return new UploadToken(spec.backendId(), id);
        }
    }

    private LinuxFiles.Fd session(UploadToken token) {
        store.backend(token.backendId());
        store.check();
        if (!token.sessionId()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        var dir = fs.directory(store.uploads, token.sessionId(), false);
        try {
            fs.lock(dir);
            return dir;
        } catch (RuntimeException e) {
            dir.close();
            throw e;
        }
    }

    private void ack(String id, LinuxFiles.Fd dir) {
        fs.sync(dir, true);
        fs.sync(store.uploads, true);
        fs.sync(store.root, true);
        store.check();
        store.verifyNamed(store.uploads, id, fs.identity(dir));
    }

    private UploadSpec spec(LinuxFiles.Fd dir) {
        return LocalFileStore.decode(files.bytes(dir, "spec", 1048576), UploadSpec.class);
    }

    private void open(LinuxFiles.Fd dir) {
        if (fs.exists(dir, "aborted") || fs.exists(dir, "decision"))
            throw new StorageException(ErrorCode.INVALID_UPLOAD_STATE);
    }

    PartReceipt part(UploadToken token, int part, InputStream body, long size, String hash) {
        if (part < 1) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        try (var dir = session(token)) {
            open(dir);
            UploadSpec spec = spec(dir);
            if (size < 0 || size > spec.expectedSize())
                throw new StorageException(ErrorCode.SIZE_MISMATCH);
            // The immutable part's actual bytes are the receipt authority, including after a
            // restart.
            files.put(dir, "part-" + part, body, size, hash);
            ack(token.sessionId(), dir);
            return new PartReceipt(
                    part,
                    size,
                    hash,
                    token.sessionId() + ":" + part + ":" + hash.toLowerCase(Locale.ROOT));
        }
    }

    StoredBlob complete(UploadToken token, List<PartReceipt> parts, String hash) {
        if (parts == null || parts.isEmpty() || hash == null || !hash.matches("[0-9a-fA-F]{64}"))
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        Completion plan = new Completion(List.copyOf(parts), hash.toLowerCase(Locale.ROOT));
        try (var dir = session(token)) {
            if (fs.exists(dir, "aborted"))
                throw new StorageException(ErrorCode.INVALID_UPLOAD_STATE);
            UploadSpec spec = spec(dir);
            long total = 0;
            int expected = 1;
            for (PartReceipt part : parts) {
                if (part == null
                        || part.part() != expected++
                        || !part.providerToken()
                                .equals(
                                        token.sessionId()
                                                + ":"
                                                + part.part()
                                                + ":"
                                                + part.sha256()))
                    throw new StorageException(ErrorCode.INVALID_ARGUMENT);
                try (var fd = fs.file(dir, "part-" + part.part(), false)) {
                    var actual = files.digest(fd);
                    if (actual.size() != part.size() || !actual.sha256().equals(part.sha256()))
                        throw new StorageException(ErrorCode.CONFLICT);
                    fs.sync(fd, false);
                }
                if (part.size() > spec.expectedSize() - total)
                    throw new StorageException(ErrorCode.SIZE_MISMATCH);
                total += part.size();
            }
            if (total != spec.expectedSize()) throw new StorageException(ErrorCode.SIZE_MISMATCH);
            byte[] decision = LocalFileStore.json(plan);
            if (fs.exists(dir, "decision")
                    && !Arrays.equals(files.bytes(dir, "decision", 1048576), decision))
                throw new StorageException(ErrorCode.CONFLICT);
            // Verify the whole stream before freezing the decision; an invalid hash must be
            // retryable.
            try (InputStream in = concatenate(dir, parts)) {
                var full = ImmutableFiles.digest();
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) >= 0) full.update(buffer, 0, n);
                if (!HexFormat.of().formatHex(full.digest()).equals(plan.sha256()))
                    throw new StorageException(ErrorCode.CHECKSUM_MISMATCH);
            } catch (IOException e) {
                throw LinuxFiles.unavailable();
            }
            // Freeze completion before publication; abort can never remove a winning complete.
            files.bytes(dir, "decision", decision);
            StorageKey key = new StorageKey(spec.backendId(), spec.namespace(), spec.objectKey());
            try (InputStream stream = concatenate(dir, parts)) {
                StoredBlob result = store.put(key, stream, total, plan.sha256());
                files.bytes(dir, "result", LocalFileStore.json(result));
                ack(token.sessionId(), dir);
                return result;
            } catch (IOException e) {
                throw LinuxFiles.unavailable();
            }
        }
    }

    private InputStream concatenate(LinuxFiles.Fd dir, List<PartReceipt> parts) {
        return new InputStream() {
            int index;
            InputStream current;

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                return read(b, 0, 1) < 0 ? -1 : b[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;
                while (true) {
                    if (current == null) {
                        if (index == parts.size()) return -1;
                        var part = parts.get(index++);
                        current =
                                fs.input(
                                        fs.file(dir, "part-" + part.part(), false),
                                        part.size(),
                                        store::check);
                    }
                    int n = current.read(b, off, len);
                    if (n >= 0) return n;
                    current.close();
                    current = null;
                }
            }

            @Override
            public void close() throws IOException {
                if (current != null) current.close();
            }
        };
    }

    void abort(UploadToken token) {
        LinuxFiles.Fd directory;
        try {
            directory = session(token);
        } catch (StorageException e) {
            if (e.code() != ErrorCode.NOT_FOUND) throw e;
            // Only a missing session is an idempotent abort. Publication failures are not.
            fs.sync(store.uploads, true);
            fs.sync(store.root, true);
            store.check();
            return;
        }
        try (var dir = directory) {
            if (fs.exists(dir, "decision")) {
                try (var decision = fs.file(dir, "decision", false)) {
                    fs.sync(decision, false);
                }
                ack(token.sessionId(), dir);
                return;
            }
            files.bytes(dir, "aborted", new byte[0]);
            // Keep the terminal tombstone and spec; remove accepted parts only after abort is
            // durable.
            List<String> names = new ArrayList<>();
            fs.visit(
                    dir,
                    n -> {
                        if (n.startsWith("part-")) names.add(n);
                    });
            for (String name : names) fs.unlink(dir, name);
            ack(token.sessionId(), dir);
        }
    }
}
