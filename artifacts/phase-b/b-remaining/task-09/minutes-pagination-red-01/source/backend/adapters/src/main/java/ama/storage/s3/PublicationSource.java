package ama.storage.s3;

import ama.contract.StorageContracts.*;

import java.io.*;
import java.util.*;
import java.util.function.IntFunction;

/**
 * One actual-byte verification pass builds at most 10,000 native interval positions. The immutable
 * accepted roots remain authoritative; the index is rebuilt after every restart. Neither
 * logical-part count nor object length determines retained heap size.
 */
final class PublicationSource {
    private record Position(int part, long offset, Staging.Root root) {}

    private final Staging staging;
    private final int count;
    private final IntFunction<Staging.Root> roots;
    private final List<Position> positions = new ArrayList<>();
    private long step;
    private boolean verified;

    PublicationSource(Staging staging, int count, IntFunction<Staging.Root> roots) {
        this.staging = staging;
        this.count = count;
        this.roots = roots;
    }

    void verify(long step, long expectedSize, String expectedHash, int maximumPositions) {
        this.step = step;
        long required = expectedSize == 0 ? 0 : 1 + (expectedSize - 1) / step;
        if (required > maximumPositions) throw ControlCodec.unavailable();
        positions.clear();
        verified = false;
        long total = 0, next = 0;
        var digest = ControlCodec.digest();
        byte[] buffer = new byte[64 * 1024];
        try {
            for (int i = 0; i < count; i++) {
                Staging.Root root = roots.apply(i);
                if (root.size() > expectedSize - total)
                    throw new StorageException(ErrorCode.SIZE_MISMATCH);
                long end = total + root.size();
                while (positions.size() < required && next < end) {
                    positions.add(new Position(i, next - total, root));
                    next = positions.size() == required ? expectedSize : Math.addExact(next, step);
                }
                long read = 0;
                try (InputStream in = staging.open(root)) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        read = Math.addExact(read, n);
                        digest.update(buffer, 0, n);
                    }
                }
                if (read != root.size()) throw ControlCodec.unavailable();
                total = end;
            }
        } catch (IOException | ArithmeticException e) {
            throw ControlCodec.unavailable();
        }
        if (total != expectedSize) throw new StorageException(ErrorCode.SIZE_MISMATCH);
        if (!ControlCodec.hex(digest.digest()).equals(expectedHash))
            throw new StorageException(ErrorCode.CHECKSUM_MISMATCH);
        verified = true;
    }

    InputStream open(long offset) {
        if (!verified || offset < 0 || offset % step != 0) throw ControlCodec.unavailable();
        if (positions.isEmpty() && offset == 0) return InputStream.nullInputStream();
        long number = offset / step;
        if (number >= positions.size()) throw ControlCodec.unavailable();
        Position position = positions.get((int) number);
        return new InputStream() {
            int index = position.part();
            InputStream current = staging.openVerifiedRange(position.root(), position.offset());
            boolean closed, ended;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                Objects.checkFromIndexSize(off, len, b.length);
                if (closed) throw new IOException("STORAGE_UNAVAILABLE");
                if (len == 0) return 0;
                if (ended) return -1;
                while (true) {
                    int n = current.read(b, off, len);
                    if (n != -1) return n;
                    current.close();
                    if (++index == count) {
                        ended = true;
                        return -1;
                    }
                    current = staging.open(roots.apply(index));
                }
            }

            @Override
            public void close() throws IOException {
                closed = true;
                current.close();
            }
        };
    }
}
