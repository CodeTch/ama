package ama.file;

import ama.contract.StorageContracts.*;

import com.sun.jna.*;

import java.io.*;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Linux x86_64 libc ABI only. No path-based IO fallback is permitted. */
public final class LinuxFiles {
    interface LibC extends Library {
        int openat(int dir, String name, int flags, int mode);

        int mkdirat(int dir, String name, int mode);

        int unlinkat(int dir, String name, int flags);

        int linkat(int from, String source, int to, String target, int flags);

        int fsync(int fd);

        int close(int fd);

        int geteuid();

        int fstat(int fd, Pointer stat);

        int flock(int fd, int op);

        long read(int fd, byte[] bytes, long length);

        long write(int fd, byte[] bytes, long length);

        long lseek(int fd, long offset, int whence);

        Pointer fdopendir(int fd);

        Pointer readdir(Pointer dir);

        int closedir(Pointer dir);
    }

    private static final int DIRECTORY = 0x10000,
            NOFOLLOW = 0x20000,
            CLOEXEC = 0x80000,
            NONBLOCK = 0x800;
    private static final LibC C = Native.load("c", LibC.class);
    private final DurabilityHooks hooks;

    public LinuxFiles(DurabilityHooks hooks) {
        if (!Platform.isLinux()
                || !System.getProperty("os.arch").equals("amd64")
                || Native.LONG_SIZE != 8) throw unavailable();
        this.hooks = hooks;
    }

    public final class Fd implements AutoCloseable {
        private int value;

        private Fd(int value) {
            this.value = value;
        }

        int value() {
            if (value < 0) throw unavailable();
            return value;
        }

        @Override
        public void close() {
            if (value >= 0) {
                int old = value;
                value = -1;
                C.close(old);
            }
        }
    }

    public record Identity(long device, long inode, int mode, int uid, long size, long links) {}

    public Identity identity(Fd fd) {
        Memory s = new Memory(144);
        if (C.fstat(fd.value(), s) < 0) throw unavailable();
        return new Identity(
                s.getLong(0),
                s.getLong(8),
                s.getInt(24),
                s.getInt(28),
                s.getLong(48),
                s.getLong(16));
    }

    public Fd root(Path path) {
        if (!path.isAbsolute() || !path.normalize().equals(path)) throw unavailable();
        Fd fd = new Fd(check(C.openat(-100, "/", DIRECTORY | NOFOLLOW | CLOEXEC, 0)));
        try {
            for (Path part : path) {
                Fd next = openDirectory(fd, part.toString());
                fd.close();
                fd = next;
            }
            privateNode(fd, true);
            return fd;
        } catch (RuntimeException e) {
            fd.close();
            throw unavailable();
        }
    }

    private Fd openDirectory(Fd parent, String name) {
        component(name);
        hooks.before("OPEN");
        int value = C.openat(parent.value(), name, DIRECTORY | NOFOLLOW | CLOEXEC, 0);
        if (value < 0 && Native.getLastError() == 2)
            throw new StorageException(ErrorCode.NOT_FOUND);
        return new Fd(check(value));
    }

    public Fd directory(Fd parent, String name, boolean create) {
        component(name);
        if (create) {
            hooks.before("MKDIR");
            int result = C.mkdirat(parent.value(), name, 0700);
            if (result < 0 && Native.getLastError() != 17) throw unavailable();
        }
        Fd result = openDirectory(parent, name);
        try {
            privateNode(result, true);
            if (identity(result).device() != identity(parent).device()) throw unavailable();
            // Re-sync existing ancestors too: an earlier failed ACK may have left them visible.
            if (create) {
                sync(result, true);
                sync(parent, true);
            }
            return result;
        } catch (RuntimeException e) {
            result.close();
            throw e;
        }
    }

    private void privateNode(Fd fd, boolean directory) {
        Identity id = identity(fd);
        if (id.uid() != C.geteuid()
                || (id.mode() & 0077) != 0
                || (id.mode() & 0170000) != (directory ? 0040000 : 0100000)) throw unavailable();
    }

    public Fd file(Fd parent, String name, boolean create) {
        component(name);
        hooks.before("OPEN");
        int flags = NOFOLLOW | CLOEXEC | NONBLOCK | (create ? 2 | 0x40 | 0x80 : 0);
        int value = C.openat(parent.value(), name, flags, 0600);
        if (value < 0) {
            if (Native.getLastError() == 2) throw new StorageException(ErrorCode.NOT_FOUND);
            throw unavailable();
        }
        Fd result = new Fd(value);
        try {
            privateNode(result, false);
            if (identity(result).device() != identity(parent).device()) throw unavailable();
            return result;
        } catch (RuntimeException e) {
            result.close();
            throw e;
        }
    }

    public boolean exists(Fd parent, String name) {
        try (Fd ignored = file(parent, name, false)) {
            return true;
        } catch (StorageException e) {
            if (e.code() == ErrorCode.NOT_FOUND) return false;
            throw e;
        }
    }

    public void sync(Fd fd, boolean directory) {
        hooks.before(directory ? "DIR_SYNC" : "FILE_SYNC");
        if (C.fsync(fd.value()) < 0) throw unavailable();
    }

    /** linkat is atomic and refuses an existing destination. It never replaces it. */
    public boolean publish(Fd from, String source, Fd to, String target) {
        component(source);
        component(target);
        hooks.before("LINK");
        if (C.linkat(from.value(), source, to.value(), target, 0) == 0) return true;
        if (Native.getLastError() == 17) return false;
        throw unavailable();
    }

    public void unlink(Fd dir, String name) {
        component(name);
        // Reject existing symlinks/nonregular leaves, while preserving idempotent absence.
        exists(dir, name);
        hooks.before("UNLINK");
        if (C.unlinkat(dir.value(), name, 0) < 0 && Native.getLastError() != 2) throw unavailable();
        sync(dir, true);
    }

    public void write(Fd fd, byte[] bytes, int length) {
        int offset = 0;
        while (offset < length) {
            byte[] slice =
                    offset == 0 && length == bytes.length
                            ? bytes
                            : java.util.Arrays.copyOfRange(bytes, offset, length);
            long n = C.write(fd.value(), slice, slice.length);
            if (n < 0 && Native.getLastError() == 4) continue;
            if (n <= 0) throw unavailable();
            offset += Math.toIntExact(n);
        }
    }

    public int read(Fd fd, byte[] bytes, int length) {
        long n;
        do {
            n = C.read(fd.value(), bytes, length);
        } while (n < 0 && Native.getLastError() == 4);
        if (n < 0) throw unavailable();
        return n == 0 ? -1 : Math.toIntExact(n);
    }

    public void seek(Fd fd, long position) {
        if (position < 0 || C.lseek(fd.value(), position, 0) != position) throw unavailable();
    }

    public void lock(Fd fd) {
        if (C.flock(fd.value(), 2) < 0) throw unavailable();
    }

    public void visit(Fd fd, Consumer<String> consumer) {
        // A fresh open-file description prevents readdir offsets from affecting later/concurrent
        // listings.
        int copy = check(C.openat(fd.value(), ".", DIRECTORY | NOFOLLOW | CLOEXEC, 0));
        Pointer dir = C.fdopendir(copy);
        if (dir == null) {
            C.close(copy);
            throw unavailable();
        }
        try {
            Pointer entry;
            while (true) {
                Native.setLastError(0);
                entry = C.readdir(dir);
                if (entry == null) {
                    if (Native.getLastError() != 0) throw unavailable();
                    break;
                }
                String name = entry.getString(19, "UTF-8");
                if (!name.equals(".") && !name.equals("..")) {
                    component(name);
                    consumer.accept(name);
                }
            }
        } finally {
            C.closedir(dir);
        }
    }

    public InputStream input(Fd fd, long length, Runnable guard) {
        return new InputStream() {
            long remaining = length;
            boolean closed;

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                return read(b, 0, 1) < 0 ? -1 : b[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                java.util.Objects.checkFromIndexSize(off, len, b.length);
                if (closed) throw new IOException("closed");
                if (len == 0) return 0;
                guard.run();
                if (remaining == 0) return -1;
                byte[] buffer = new byte[(int) Math.min(Math.min(len, 65536), remaining)];
                int n = LinuxFiles.this.read(fd, buffer, buffer.length);
                if (n < 0) throw new EOFException("truncated blob");
                System.arraycopy(buffer, 0, b, off, n);
                remaining -= n;
                return n;
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    fd.close();
                }
            }
        };
    }

    public String control(Fd directory, String name, int maximum) {
        try (Fd fd = file(directory, name, false)) {
            return control(fd, maximum);
        }
    }

    public String control(Path path, int maximum) {
        if (!path.isAbsolute() || !path.normalize().equals(path)) throw unavailable();
        Fd parent = new Fd(check(C.openat(-100, "/", DIRECTORY | NOFOLLOW | CLOEXEC, 0)));
        try {
            for (Path part : path.getParent()) {
                Fd next = openDirectory(parent, part.toString());
                parent.close();
                parent = next;
            }
            String name = path.getFileName().toString();
            component(name);
            try (Fd fd =
                    new Fd(
                            check(
                                    C.openat(
                                            parent.value(),
                                            name,
                                            NOFOLLOW | NONBLOCK | CLOEXEC,
                                            0)))) {
                Identity id = identity(fd);
                if ((id.mode() & 0170000) != 0100000
                        || (id.mode() & 0022) != 0
                        || (id.uid() != 0 && id.uid() != C.geteuid())
                        || id.links() != 1) throw unavailable();
                return control(fd, maximum);
            }
        } finally {
            parent.close();
        }
    }

    private String control(Fd fd, int maximum) {
        long size = identity(fd).size();
        if (size < 1 || size > maximum) throw unavailable();
        byte[] bytes = new byte[(int) size];
        try (InputStream in = input(fd, size, () -> {})) {
            if (in.readNBytes(bytes, 0, bytes.length) != bytes.length) throw unavailable();
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw unavailable();
        }
    }

    public static StorageException unavailable() {
        return new StorageException(ErrorCode.STORAGE_UNAVAILABLE);
    }

    private static int check(int value) {
        if (value < 0) throw unavailable();
        return value;
    }

    private static void component(String name) {
        if (name == null
                || name.isEmpty()
                || name.equals(".")
                || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\0') >= 0) throw unavailable();
    }
}
