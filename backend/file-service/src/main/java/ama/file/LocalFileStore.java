package ama.file;

import ama.contract.StorageContracts.*;
import ama.storage.FileStoragePort;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Single-volume immutable blobs. All filesystem operations use trusted directory descriptors. */
public final class LocalFileStore implements FileStoragePort, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    final LinuxFiles fs;
    final LinuxFiles.Fd root, objects, staging, uploads;
    final ImmutableFiles files;
    final String backend;
    private final Path path;
    private final VolumeGuard volume;
    private final LocalUploadJournal journal;
    private boolean closed;

    public LocalFileStore(Path path, String backend, VolumeGuard volume, DurabilityHooks hooks) {
        new StorageKey(backend, "validation", "validation");
        this.path = path;
        this.backend = backend;
        this.volume = Objects.requireNonNull(volume);
        fs = new LinuxFiles(Objects.requireNonNull(hooks));
        root = fs.root(path);
        LinuxFiles.Fd o = null, s = null, u = null;
        try {
            volume.check(fs, root);
            o = fs.directory(root, "objects", true);
            s = fs.directory(root, "staging", true);
            u = fs.directory(root, "uploads", true);
            objects = o;
            staging = s;
            uploads = u;
            files = new ImmutableFiles(fs, root, staging, this::check);
            journal = new LocalUploadJournal(this);
            check();
        } catch (RuntimeException e) {
            if (o != null) o.close();
            if (s != null) s.close();
            if (u != null) u.close();
            root.close();
            throw e;
        }
    }

    void check() {
        if (closed) throw LinuxFiles.unavailable();
        volume.check(fs, root);
        try (var current = fs.root(path)) {
            same(root, current);
        }
        verifyChild("objects", objects);
        verifyChild("staging", staging);
        verifyChild("uploads", uploads);
    }

    private void verifyChild(String name, LinuxFiles.Fd original) {
        try (var current = fs.directory(root, name, false)) {
            same(original, current);
        } catch (StorageException e) {
            throw LinuxFiles.unavailable();
        }
    }

    void verifyNamed(LinuxFiles.Fd parent, String name, LinuxFiles.Identity expected) {
        try (var current = fs.directory(parent, name, false)) {
            var actual = fs.identity(current);
            if (actual.device() != expected.device() || actual.inode() != expected.inode())
                throw LinuxFiles.unavailable();
        } catch (StorageException e) {
            throw LinuxFiles.unavailable();
        }
    }

    private void same(LinuxFiles.Fd a, LinuxFiles.Fd b) {
        var x = fs.identity(a);
        var y = fs.identity(b);
        if (x.device() != y.device() || x.inode() != y.inode()) throw LinuxFiles.unavailable();
    }

    void backend(String id) {
        if (!backend.equals(id)) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
    }

    static byte[] json(Object value) {
        try {
            return JSON.writeValueAsBytes(value);
        } catch (IOException e) {
            throw LinuxFiles.unavailable();
        }
    }

    static <T> T decode(byte[] bytes, Class<T> type) {
        try {
            return JSON.readValue(bytes, type);
        } catch (IOException | IllegalArgumentException e) {
            throw LinuxFiles.unavailable();
        }
    }

    LinuxFiles.Fd object(StorageKey key, boolean create) {
        backend(key.backendId());
        check();
        String id = ImmutableFiles.hash(json(key));
        LinuxFiles.Fd dir = fs.directory(objects, id, create);
        try {
            if (create) files.bytes(dir, "key", json(key));
            if (!key.equals(decode(files.bytes(dir, "key", 1048576), StorageKey.class)))
                throw LinuxFiles.unavailable();
            return dir;
        } catch (RuntimeException e) {
            dir.close();
            throw e;
        }
    }

    @Override
    public StoredBlob put(StorageKey key, InputStream body, long size, String sha256) {
        Objects.requireNonNull(body);
        try (var dir = object(key, true)) {
            files.put(dir, "data", body, size, sha256);
            fs.sync(objects, true);
            fs.sync(root, true);
            check();
            verifyNamed(objects, ImmutableFiles.hash(json(key)), fs.identity(dir));
            return new StoredBlob(key, size, sha256, null);
        }
    }

    @Override
    public BlobStat stat(StorageKey key) {
        try (var dir = object(key, false);
                var data = fs.file(dir, "data", false)) {
            var digest = files.digest(data);
            check();
            verifyNamed(objects, ImmutableFiles.hash(json(key)), fs.identity(dir));
            return new BlobStat(digest.size(), digest.sha256(), null);
        }
    }

    @Override
    public InputStream read(StorageKey key, long offset, long length) {
        try (var dir = object(key, false)) {
            var data = fs.file(dir, "data", false);
            try {
                long size = fs.identity(data).size();
                if (offset < 0 || length < 0 || offset > size || length > size - offset)
                    throw new StorageException(ErrorCode.INVALID_RANGE);
                fs.seek(data, offset);
                check();
                var directoryIdentity = fs.identity(dir);
                String name = ImmutableFiles.hash(json(key));
                verifyNamed(objects, name, directoryIdentity);
                return fs.input(
                        data,
                        length,
                        () -> {
                            check();
                            verifyNamed(objects, name, directoryIdentity);
                        });
            } catch (RuntimeException e) {
                data.close();
                throw e;
            }
        }
    }

    @Override
    public void delete(StorageKey key) {
        backend(key.backendId());
        check();
        try (var dir = object(key, false)) {
            fs.unlink(dir, "data");
            fs.sync(objects, true);
            fs.sync(root, true);
            check();
            verifyNamed(objects, ImmutableFiles.hash(json(key)), fs.identity(dir));
        } catch (StorageException e) {
            if (e.code() != ErrorCode.NOT_FOUND) throw e;
            fs.sync(objects, true);
            fs.sync(root, true);
            check();
        }
    }

    @Override
    public UploadToken beginUpload(UploadSpec spec) {
        return journal.begin(spec);
    }

    @Override
    public PartReceipt writePart(
            UploadToken token, int part, InputStream body, long size, String hash) {
        return journal.part(token, part, body, size, hash);
    }

    @Override
    public StoredBlob complete(UploadToken token, List<PartReceipt> parts, String hash) {
        return journal.complete(token, parts, hash);
    }

    @Override
    public void abort(UploadToken token) {
        journal.abort(token);
    }

    private record Cursor(String backend, String namespace, String prefix, String last) {}

    @Override
    public BlobPage list(
            String backend, String namespace, String prefix, String cursor, int limit) {
        backend(backend);
        new StorageKey(backend, namespace, "validation");
        if (prefix == null || limit < 1 || limit > 1000)
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        check();
        String after = "";
        if (cursor != null) {
            Cursor c;
            try {
                c = JSON.readValue(Base64.getUrlDecoder().decode(cursor), Cursor.class);
            } catch (Exception e) {
                throw new StorageException(ErrorCode.INVALID_ARGUMENT);
            }
            if (!backend.equals(c.backend)
                    || !namespace.equals(c.namespace)
                    || !prefix.equals(c.prefix)
                    || c.last == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
            after = c.last;
        }
        String last = after;
        TreeMap<String, BlobItem> found = new TreeMap<>();
        fs.visit(
                objects,
                name -> {
                    if (!name.matches("[0-9a-f]{64}")) throw LinuxFiles.unavailable();
                    try (var dir = fs.directory(objects, name, false)) {
                        if (!fs.exists(dir, "key") || !fs.exists(dir, "data")) return;
                        StorageKey key = decode(files.bytes(dir, "key", 1048576), StorageKey.class);
                        if (!ImmutableFiles.hash(json(key)).equals(name))
                            throw LinuxFiles.unavailable();
                        if (key.backendId().equals(backend)
                                && key.namespace().equals(namespace)
                                && key.objectKey().startsWith(prefix)
                                && key.objectKey().compareTo(last) > 0) {
                            // Keep only limit+1 candidates even for a large namespace.
                            if (found.size() <= limit
                                    || key.objectKey().compareTo(found.lastKey()) < 0) {
                                found.put(key.objectKey(), new BlobItem(key, stat(key)));
                                if (found.size() > limit + 1) found.pollLastEntry();
                            }
                        }
                    }
                });
        boolean more = found.size() > limit;
        if (more) found.pollLastEntry();
        String next =
                more
                        ? Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        json(
                                                new Cursor(
                                                        backend,
                                                        namespace,
                                                        prefix,
                                                        found.lastKey())))
                        : null;
        check();
        return new BlobPage(new ArrayList<>(found.values()), next);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            uploads.close();
            staging.close();
            objects.close();
            root.close();
        }
    }
}
