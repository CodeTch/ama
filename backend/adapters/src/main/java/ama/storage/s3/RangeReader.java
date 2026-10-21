package ama.storage.s3;

import ama.contract.StorageContracts.*;

import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.util.Objects;

/** HEAD plus one exact bounded GET. Never enters stat or a full-object digest path. */
final class RangeReader {
    final S3Objects io;
    final S3Layout layout;

    RangeReader(S3Objects io, S3Layout layout) {
        this.io = io;
        this.layout = layout;
    }

    InputStream read(StorageKey key, long offset, long length) {
        if (offset < 0 || length < 0 || offset > Long.MAX_VALUE - length)
            throw new StorageException(ErrorCode.INVALID_RANGE);
        String physical = layout.data(key);
        io.binding(layout, key, false);
        var head =
                io.call(
                        () ->
                                io.client.headObject(
                                        HeadObjectRequest.builder()
                                                .bucket(io.config.dataBucket())
                                                .key(physical)
                                                .build()));
        long size = head.contentLength();
        if (size < 0) throw ControlCodec.unavailable();
        if (offset > size || length > size - offset)
            throw new StorageException(ErrorCode.INVALID_RANGE);
        if (length == 0) return InputStream.nullInputStream();
        String version = head.versionId();
        boolean concrete = version != null && !version.equals("null");
        var request =
                GetObjectRequest.builder()
                        .bucket(io.config.dataBucket())
                        .key(physical)
                        .range("bytes=" + offset + "-" + (offset + length - 1));
        if (concrete) request.versionId(version);
        else {
            if (head.eTag() == null) throw ControlCodec.unavailable();
            request.ifMatch(head.eTag());
        }
        var in = io.raw(request.build());
        var response = in.response();
        if (response.sdkHttpResponse().statusCode() != 206
                || response.contentLength() != length
                || !("bytes " + offset + "-" + (offset + length - 1) + "/" + size)
                        .equals(response.contentRange())
                || (concrete && !version.equals(response.versionId()))
                || !Objects.equals(head.eTag(), response.eTag())) {
            io.release(in);
            throw ControlCodec.unavailable();
        }
        return new InputStream() {
            long left = length;
            boolean closed;

            private IOException fail() {
                close();
                return new IOException("STORAGE_UNAVAILABLE");
            }

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                Objects.checkFromIndexSize(off, len, b.length);
                if (len == 0) return 0;
                if (closed) {
                    if (left == 0) return -1;
                    throw new IOException("STORAGE_UNAVAILABLE");
                }
                try {
                    if (left == 0) {
                        if (in.read() != -1) throw fail();
                        close();
                        return -1;
                    }
                    int n = in.read(b, off, (int) Math.min(len, left));
                    if (n < 0) throw fail();
                    left -= n;
                    return n;
                } catch (IOException | RuntimeException e) {
                    throw fail();
                }
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    io.release(in);
                }
            }
        };
    }
}
