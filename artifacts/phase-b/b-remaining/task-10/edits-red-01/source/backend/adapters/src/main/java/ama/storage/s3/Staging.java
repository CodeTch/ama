package ama.storage.s3;

import ama.contract.StorageContracts.*;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded chunks and a sequential authenticated page chain; no local recovery files. */
final class Staging {
    record Root(
            String owner, String candidate, long size, String hash, long chunks, String pagesHash) {
        byte[] bytes() {
            return ControlCodec.pack(
                    "root-v1",
                    owner,
                    candidate,
                    Long.toString(size),
                    hash,
                    Long.toString(chunks),
                    pagesHash);
        }

        String prefix() {
            return "staging/v1/" + owner + "/" + candidate + "/";
        }
    }

    private final S3Objects io;

    Staging(S3Objects io) {
        this.io = io;
    }

    Root parse(byte[] data, String owner) {
        String[] p = ControlCodec.unpack(data, "root-v1", 7);
        if (!p[1].equals(owner)) throw ControlCodec.unavailable();
        ControlCodec.uuid(p[1]);
        ControlCodec.uuid(p[2]);
        if (!p[4].matches("[0-9a-f]{64}") || !p[6].matches("[0-9a-f]{64}"))
            throw ControlCodec.unavailable();
        long size = ControlCodec.number(p[3]), count = ControlCodec.number(p[5]);
        if (count != (size == 0 ? 0 : 1 + (size - 1) / io.config.chunkBytes()))
            throw ControlCodec.unavailable();
        return new Root(owner, p[2], size, p[4], count, p[6]);
    }

    Root stage(String owner, InputStream body, long expected, String hash) {
        if (body == null || expected < 0) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        String candidate = ControlCodec.id(),
                prefix = "staging/v1/" + owner + "/" + candidate + "/";
        var full = ControlCodec.digest();
        var pages = ControlCodec.digest();
        long size = 0, count = 0;
        try {
            byte[] buffer = new byte[io.config.chunkBytes()];
            while (true) {
                int n = 0;
                while (n < buffer.length) {
                    int k = body.read(buffer, n, buffer.length - n);
                    if (k == -1) break;
                    if (k == 0) {
                        int b = body.read();
                        if (b == -1) break;
                        buffer[n++] = (byte) b;
                    } else n += k;
                    if (n > expected - size) throw new StorageException(ErrorCode.SIZE_MISMATCH);
                }
                if (n == 0) break;
                size = Math.addExact(size, n);
                full.update(buffer, 0, n);
                byte[] chunk = n == buffer.length ? buffer : Arrays.copyOf(buffer, n);
                String h = ControlCodec.hash(chunk), path = prefix + "chunk/" + count;
                S3Layout.physical(path, io.config.keyBudget());
                // Chunks have a larger bound than control records and are verified from actual GET
                // bytes.
                try {
                    io.call(
                            () ->
                                    io.client.putObject(
                                            PutObjectRequest.builder()
                                                    .bucket(io.config.controlBucket())
                                                    .key(path)
                                                    .ifNoneMatch("*")
                                                    .build(),
                                            RequestBody.fromBytes(chunk)));
                } catch (StorageException uncertain) {
                    /* exact-key verification below; absence remains unavailable */
                }
                verifyChunk(path, n, h);
                byte[] page =
                        ControlCodec.pack("chunk-v1", Long.toString(count), Integer.toString(n), h);
                io.exact(prefix + "manifest-page/" + count, page);
                pages.update(page);
                count++;
                if (n < buffer.length) break;
            }
        } catch (IOException | ArithmeticException e) {
            throw ControlCodec.unavailable();
        }
        if (size != expected) throw new StorageException(ErrorCode.SIZE_MISMATCH);
        String actual = ControlCodec.hex(full.digest());
        if (!actual.equals(hash)) throw new StorageException(ErrorCode.CHECKSUM_MISMATCH);
        Root root =
                new Root(owner, candidate, size, actual, count, ControlCodec.hex(pages.digest()));
        io.exact(prefix + "root", root.bytes());
        return root;
    }

    private byte[] verifyChunk(String key, long size, String hash) {
        var stream =
                io.raw(
                        GetObjectRequest.builder()
                                .bucket(io.config.controlBucket())
                                .key(key)
                                .build());
        try {
            if (size < 1
                    || size > io.config.chunkBytes()
                    || S3Objects.contentLength(stream.response().contentLength()) != size)
                throw ControlCodec.unavailable();
            byte[] bytes = stream.readNBytes((int) size + 1);
            if (bytes.length != size || !ControlCodec.hash(bytes).equals(hash))
                throw ControlCodec.unavailable();
            return bytes;
        } catch (IOException e) {
            throw ControlCodec.unavailable();
        } finally {
            io.release(stream);
        }
    }

    /** Only used after PublicationSource has verified every page and every actual byte. */
    InputStream openVerifiedRange(Root root, long offset) {
        if (offset == 0) return open(root);
        if (offset < 0 || offset > root.size()) throw ControlCodec.unavailable();
        if (!Arrays.equals(io.get(root.prefix() + "root"), root.bytes()))
            throw ControlCodec.unavailable();
        return new InputStream() {
            long index = offset / io.config.chunkBytes();
            int skip = (int) (offset % io.config.chunkBytes());
            ByteArrayInputStream current = new ByteArrayInputStream(new byte[0]);
            boolean closed;

            @Override
            public int read() {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                Objects.checkFromIndexSize(off, len, b.length);
                if (closed) throw ControlCodec.unavailable();
                if (len == 0) return 0;
                while (current.available() == 0) {
                    if (index == root.chunks()) return -1;
                    String[] page =
                            ControlCodec.unpack(
                                    io.get(root.prefix() + "manifest-page/" + index),
                                    "chunk-v1",
                                    4);
                    long n = ControlCodec.number(page[2]);
                    if (ControlCodec.number(page[1]) != index
                            || n
                                    != Math.min(
                                            io.config.chunkBytes(),
                                            root.size() - index * io.config.chunkBytes()))
                        throw ControlCodec.unavailable();
                    byte[] actual = verifyChunk(root.prefix() + "chunk/" + index, n, page[3]);
                    current = new ByteArrayInputStream(actual, skip, actual.length - skip);
                    skip = 0;
                    index++;
                }
                return current.read(b, off, len);
            }

            @Override
            public void close() {
                closed = true;
                current = new ByteArrayInputStream(new byte[0]);
            }
        };
    }

    InputStream open(Root root) {
        if (!Arrays.equals(io.get(root.prefix() + "root"), root.bytes()))
            throw ControlCodec.unavailable();
        return new InputStream() {
            long index, total;
            ByteArrayInputStream current = new ByteArrayInputStream(new byte[0]);
            final MessageDigest pages = ControlCodec.digest(), full = ControlCodec.digest();
            boolean finished, closed;

            private boolean next() {
                if (closed) throw ControlCodec.unavailable();
                if (index == root.chunks()) {
                    if (!finished) {
                        finished = true;
                        if (total != root.size()
                                || !ControlCodec.hex(pages.digest()).equals(root.pagesHash())
                                || !ControlCodec.hex(full.digest()).equals(root.hash()))
                            throw ControlCodec.unavailable();
                    }
                    return false;
                }
                byte[] page = io.get(root.prefix() + "manifest-page/" + index);
                String[] p = ControlCodec.unpack(page, "chunk-v1", 4);
                if (ControlCodec.number(p[1]) != index) throw ControlCodec.unavailable();
                long n = ControlCodec.number(p[2]);
                if (n != Math.min(io.config.chunkBytes(), root.size() - total))
                    throw ControlCodec.unavailable();
                byte[] data = verifyChunk(root.prefix() + "chunk/" + index, n, p[3]);
                pages.update(page);
                full.update(data);
                total += n;
                index++;
                current = new ByteArrayInputStream(data);
                return true;
            }

            @Override
            public int read() {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                Objects.checkFromIndexSize(off, len, b.length);
                if (len == 0) return 0;
                while (current.available() == 0) if (!next()) return -1;
                return current.read(b, off, len);
            }

            @Override
            public void close() {
                closed = true;
                current = new ByteArrayInputStream(new byte[0]);
            }
        };
    }
}
