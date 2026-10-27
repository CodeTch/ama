package ama.storage.s3;

import ama.contract.StorageContracts.*;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.util.*;

/** Each helper owns a fresh native ID. All final publications are conditional on the wire. */
final class NativePublisher {
    final S3Objects io;
    final S3Layout layout;

    NativePublisher(S3Objects io, S3Layout layout) {
        this.io = io;
        this.layout = layout;
    }

    long interval(long size) {
        if (size < 0 || size > io.config.maximumObject())
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        long needed = size == 0 ? 0 : 1 + (size - 1) / io.config.nativeMaxParts();
        long result = Math.max(io.config.nativeMinimum(), needed);
        if (result > io.config.nativeMaximum())
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        return result;
    }

    StoredBlob existing(StorageKey key, long size, String hash) {
        RetiredUploads.live(io,layout,key);
        BlobStat actual;
        try {
            actual = io.stat(layout.data(key));
        } catch (StorageException e) {
            if (e.code() == ErrorCode.NOT_FOUND) return null;
            throw e;
        }
        RetiredUploads.live(io,layout,key);
        if (actual.size() != size || !actual.sha256().equals(hash))
            throw new StorageException(ErrorCode.CONFLICT);
        return new StoredBlob(key, size, hash, actual.providerVersionId());
    }

    StoredBlob publish(
            StorageKey key, long size, String hash, String owner, PublicationSource source) {
        RetiredUploads.live(io,layout,key);
        long step = interval(size);
        source.verify(step, size, hash, io.config.nativeMaxParts());
        io.binding(layout, key, true);
        StoredBlob found = existing(key, size, hash);
        if (found != null) return found;
        String physical = layout.data(key);
        try {
            if (size <= io.config.chunkBytes()) {
                byte[] bytes;
                try (var in = source.open(0)) {
                    bytes = in.readNBytes((int) size + 1);
                }
                if (bytes.length != size || !ControlCodec.hash(bytes).equals(hash))
                    throw ControlCodec.unavailable();
                io.call(
                        () ->
                                io.client.putObject(
                                        PutObjectRequest.builder()
                                                .bucket(io.config.dataBucket())
                                                .key(physical)
                                                .ifNoneMatch("*")
                                                .build(),
                                        RequestBody.fromBytes(bytes)));
            } else {
                String attempt = "sessions/v1/" + owner + "/attempts/" + ControlCodec.id() + "/";
                io.exact(
                        attempt + "intent",
                        ControlCodec.pack(
                                "intent-v1",
                                io.config.identity(),
                                physical,
                                Long.toString(size),
                                hash));
                // No list-diff guessing after unknown Create outcome. An intent without
                // known-upload is retained.
                String upload =
                        io.call(
                                        () ->
                                                io.client.createMultipartUpload(
                                                        CreateMultipartUploadRequest.builder()
                                                                .bucket(io.config.dataBucket())
                                                                .key(physical)
                                                                .build()))
                                .uploadId();
                if (upload == null || upload.isBlank()) throw ControlCodec.unavailable();
                io.exact(
                        attempt + "known-upload", ControlCodec.pack("native-v1", physical, upload));
                List<CompletedPart> parts = new ArrayList<>();
                for (long start = 0; start < size; ) {
                    long offset = start, length = Math.min(step, size - start);
                    int part = parts.size() + 1;
                    RequestBody body =
                            RequestBody.fromContentProvider(
                                    () -> slice(source, offset, length),
                                    length,
                                    "application/octet-stream");
                    var response =
                            io.streaming(
                                    () ->
                                            io.client.uploadPart(
                                                    UploadPartRequest.builder()
                                                            .bucket(io.config.dataBucket())
                                                            .key(physical)
                                                            .uploadId(upload)
                                                            .partNumber(part)
                                                            .contentLength(length)
                                                            .build(),
                                                    body));
                    if (response.eTag() == null) throw ControlCodec.unavailable();
                    parts.add(
                            CompletedPart.builder().partNumber(part).eTag(response.eTag()).build());
                    start += length;
                }
                io.call(
                        () ->
                                io.client.completeMultipartUpload(
                                        CompleteMultipartUploadRequest.builder()
                                                .bucket(io.config.dataBucket())
                                                .key(physical)
                                                .uploadId(upload)
                                                .ifNoneMatch("*")
                                                .multipartUpload(
                                                        CompletedMultipartUpload.builder()
                                                                .parts(parts)
                                                                .build())
                                                .build()));
            }
        } catch (IOException | StorageException uncertain) {
            // Includes parsed embedded 200 errors, 409, 412, NoSuchUpload and transport loss.
            found = existing(key, size, hash);
            if (found != null) return found;
            throw ControlCodec.unavailable();
        }
        found = existing(key, size, hash);
        if (found == null) throw ControlCodec.unavailable();
        return found;
    }

    private InputStream slice(PublicationSource source, long offset, long length) {
        InputStream in = source.open(offset);
        return new FilterInputStream(in) {
            long left = length;

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                return read(b, 0, 1) == -1 ? -1 : b[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                Objects.checkFromIndexSize(off, len, b.length);
                if (len == 0) return 0;
                if (left == 0) return -1;
                int n = in.read(b, off, (int) Math.min(len, left));
                if (n < 0) throw new IOException("STORAGE_UNAVAILABLE");
                left -= n;
                return n;
            }
        };
    }
}
