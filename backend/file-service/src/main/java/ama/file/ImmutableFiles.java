package ama.file;

import ama.contract.StorageContracts.*;

import java.io.*;
import java.security.*;
import java.util.*;

/** Immutable private files: stage, verify, force, link without replacement, force directories. */
final class ImmutableFiles {
    final LinuxFiles fs;
    final LinuxFiles.Fd root, staging;
    final Runnable guard;

    ImmutableFiles(LinuxFiles fs, LinuxFiles.Fd root, LinuxFiles.Fd staging, Runnable guard) {
        this.fs = fs;
        this.root = root;
        this.staging = staging;
        this.guard = guard;
    }

    record Digest(long size, String sha256) {}

    static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    Digest digest(LinuxFiles.Fd file) {
        fs.seek(file, 0);
        MessageDigest hash = digest();
        long size = 0;
        byte[] buffer = new byte[65536];
        int n;
        while ((n = fs.read(file, buffer, buffer.length)) >= 0) {
            guard.run();
            hash.update(buffer, 0, n);
            size = Math.addExact(size, n);
        }
        return new Digest(size, HexFormat.of().formatHex(hash.digest()));
    }

    void put(LinuxFiles.Fd dir, String name, InputStream body, long size, String sha256) {
        if (size < 0 || sha256 == null || !sha256.matches("[0-9a-fA-F]{64}"))
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        guard.run();
        if (replay(dir, name, body, size, sha256)) return;
        String temp = UUID.randomUUID().toString();
        boolean created = false;
        LinuxFiles.Identity accepted = null;
        try {
            try (var out = fs.file(staging, temp, true)) {
                created = true;
                MessageDigest hash = digest();
                byte[] buffer = new byte[65536];
                long actual = 0;
                int n;
                while ((n = body.read(buffer)) >= 0) {
                    guard.run();
                    if (n == 0) continue;
                    if (n > size - actual) throw new StorageException(ErrorCode.SIZE_MISMATCH);
                    fs.write(out, buffer, n);
                    hash.update(buffer, 0, n);
                    actual += n;
                }
                if (actual != size) throw new StorageException(ErrorCode.SIZE_MISMATCH);
                if (!HexFormat.of().formatHex(hash.digest()).equalsIgnoreCase(sha256))
                    throw new StorageException(ErrorCode.CHECKSUM_MISMATCH);
                fs.sync(out, false);
                guard.run();
                accepted = fs.identity(out);
                if (!fs.publish(staging, temp, dir, name)) {
                    try (var existing = fs.file(dir, name, false)) {
                        Digest got = digest(existing);
                        if (got.size() != size || !got.sha256().equalsIgnoreCase(sha256))
                            throw new StorageException(ErrorCode.CONFLICT);
                        fs.sync(existing, false);
                        accepted = fs.identity(existing);
                    }
                }
                fs.sync(dir, true);
                fs.sync(root, true);
            }
            fs.unlink(staging, temp);
            created = false;
            // The staging pathname may have been replaced while the original FD stayed open.
            // Verify the linked inode, and flush metadata changed by link/unlink before ACK.
            try (var published = fs.file(dir, name, false)) {
                var identity = fs.identity(published);
                if (accepted == null
                        || identity.device() != accepted.device()
                        || identity.inode() != accepted.inode()) throw LinuxFiles.unavailable();
                fs.sync(published, false);
            }
            fs.sync(dir, true);
            fs.sync(root, true);
            guard.run();
        } catch (IOException | ArithmeticException e) {
            throw LinuxFiles.unavailable();
        } finally {
            if (created) {
                try {
                    // Admission loss forbids even best-effort cleanup mutations on this volume.
                    guard.run();
                    fs.unlink(staging, temp);
                } catch (RuntimeException ignored) {
                    /* Failed operation remains failed; unacknowledged staging is recoverable garbage. */
                }
            }
        }
    }

    /** A prior visible result still needs complete input/inode checks and fresh durability.
     * No link count changes are made, so another staged copy and unlink are unnecessary. */
    private boolean replay(LinuxFiles.Fd dir, String name, InputStream body, long size, String sha256) {
        LinuxFiles.Fd existing;
        try { existing = fs.file(dir, name, false); }
        catch (StorageException absent) {
            if (absent.code() == ErrorCode.NOT_FOUND) return false;
            throw absent;
        }
        try (existing) {
            MessageDigest inputHash = digest();
            byte[] buffer = new byte[65536];
            long actual = 0;
            for (int n; (n = body.read(buffer)) >= 0;) {
                guard.run();
                if (n > size - actual) throw new StorageException(ErrorCode.SIZE_MISMATCH);
                inputHash.update(buffer, 0, n);
                actual += n;
            }
            if (actual != size) throw new StorageException(ErrorCode.SIZE_MISMATCH);
            if (!HexFormat.of().formatHex(inputHash.digest()).equalsIgnoreCase(sha256))
                throw new StorageException(ErrorCode.CHECKSUM_MISMATCH);
            Digest got = digest(existing);
            if (got.size() != size || !got.sha256().equalsIgnoreCase(sha256))
                throw new StorageException(ErrorCode.CONFLICT);
            var accepted = fs.identity(existing);
            fs.sync(existing, false);
            fs.sync(dir, true);
            fs.sync(root, true);
            try (var published = fs.file(dir, name, false)) {
                var identity = fs.identity(published);
                if (identity.device() != accepted.device() || identity.inode() != accepted.inode())
                    throw LinuxFiles.unavailable();
            }
            guard.run();
            return true;
        } catch (IOException | ArithmeticException failure) {
            throw LinuxFiles.unavailable();
        }
    }

    void bytes(LinuxFiles.Fd dir, String name, byte[] bytes) {
        if (bytes.length > 1048576) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        put(dir, name, new ByteArrayInputStream(bytes), bytes.length, hash(bytes));
    }

    byte[] bytes(LinuxFiles.Fd dir, String name, int max) {
        try (var fd = fs.file(dir, name, false)) {
            long size = fs.identity(fd).size();
            if (size < 0 || size > max) throw LinuxFiles.unavailable();
            try (var in = fs.input(fd, size, guard)) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw LinuxFiles.unavailable();
            }
        }
    }
}
