package ama.storage.s3;

import ama.contract.StorageContracts.*;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Narrow transport boundary. Admission follows the lifetime of returned streams. */
final class S3Objects {
    final S3Client client;
    final S3Config config;
    private final Semaphore requests;
    private final Semaphore streamingRequests;

    S3Objects(S3Client client, S3Config config) {
        this.client = client;
        this.config = config;
        requests = new Semaphore(config.admission());
        streamingRequests = new Semaphore(config.admission() - 1);
    }

    private void acquire() {
        acquire(requests);
    }

    private void acquire(Semaphore permits) {
        try {
            if (!permits.tryAcquire(config.timeout().toMillis(), TimeUnit.MILLISECONDS))
                throw ControlCodec.unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ControlCodec.unavailable();
        }
    }

    /** Native request bodies fetch staging chunks; leave one shared slot for that nested I/O. */
    <T> T streaming(Supplier<T> action) {
        acquire(streamingRequests);
        try {
            return call(action);
        } finally {
            streamingRequests.release();
        }
    }

    <T> T call(Supplier<T> action) {
        acquire();
        try {
            return action.get();
        } catch (StorageException e) {
            throw e;
        } catch (S3Exception e) {
            throw mapped(e);
        } catch (RuntimeException e) {
            throw ControlCodec.unavailable();
        } finally {
            requests.release();
        }
    }

    static StorageException mapped(S3Exception e) {
        String code = e.awsErrorDetails() == null ? "" : e.awsErrorDetails().errorCode();
        return new StorageException(
                e.statusCode() == 404 && ("NoSuchKey".equals(code) || "NotFound".equals(code))
                        ? ErrorCode.NOT_FOUND
                        : ErrorCode.STORAGE_UNAVAILABLE);
    }

    ResponseInputStream<GetObjectResponse> raw(GetObjectRequest request) {
        S3Layout.physical(request.key(), config.keyBudget());
        acquire();
        try {
            var response = client.getObject(request);
            // ResponseInputStream cannot be wrapped without losing abort; callers release
            // explicitly.
            return response;
        } catch (S3Exception e) {
            requests.release();
            throw mapped(e);
        } catch (RuntimeException e) {
            requests.release();
            throw ControlCodec.unavailable();
        }
    }

    void release(ResponseInputStream<?> in) {
        try {
            in.abort();
        } finally {
            requests.release();
        }
    }

    static long contentLength(Long value) {
        if (value == null || value < 0) throw ControlCodec.unavailable();
        return value;
    }

    byte[] get(String key) {
        var in = raw(GetObjectRequest.builder().bucket(config.controlBucket()).key(key).build());
        try {
            long n = contentLength(in.response().contentLength());
            if (n > ControlCodec.MAX) throw ControlCodec.unavailable();
            byte[] bytes = in.readNBytes((int) n + 1);
            if (bytes.length != n) throw ControlCodec.unavailable();
            return bytes;
        } catch (IOException e) {
            throw ControlCodec.unavailable();
        } finally {
            release(in);
        }
    }

    byte[] optional(String key) {
        try {
            return get(key);
        } catch (StorageException e) {
            if (e.code() == ErrorCode.NOT_FOUND) return null;
            throw e;
        }
    }

    /** Unknown response is reconciled at the exact fixed key. Absence is never no-effect. */
    byte[] create(String key, byte[] bytes) {
        S3Layout.physical(key, config.keyBudget());
        try {
            call(
                    () ->
                            client.putObject(
                                    PutObjectRequest.builder()
                                            .bucket(config.controlBucket())
                                            .key(key)
                                            .ifNoneMatch("*")
                                            .build(),
                                    RequestBody.fromBytes(bytes)));
        } catch (StorageException ignored) {
            byte[] winner = optional(key);
            if (winner == null) throw ControlCodec.unavailable();
            return winner;
        }
        return get(key);
    }

    void exact(String key, byte[] bytes) {
        if (!Arrays.equals(bytes, create(key, bytes))) throw ControlCodec.unavailable();
    }

    void binding(S3Layout layout, StorageKey key, boolean create) {
        layout.data(key);
        if (!scope(layout, key.namespace(), create))
            throw new StorageException(ErrorCode.NOT_FOUND);
        byte[] expected = layout.binding(key);
        byte[] actual =
                create ? create(layout.bindingKey(key), expected) : get(layout.bindingKey(key));
        if (!Arrays.equals(expected, actual)) throw ControlCodec.unavailable();
    }

    boolean scope(S3Layout layout, String namespace, boolean create) {
        byte[] expected = layout.scopeBinding(namespace);
        byte[] actual =
                create
                        ? create(layout.scopeKey(namespace), expected)
                        : optional(layout.scopeKey(namespace));
        if (actual == null) return false;
        if (!Arrays.equals(expected, actual)) throw ControlCodec.unavailable();
        return true;
    }

    BlobStat stat(String physical) {
        var in = raw(GetObjectRequest.builder().bucket(config.dataBucket()).key(physical).build());
        try {
            long expected = contentLength(in.response().contentLength());
            var digest = ControlCodec.digest();
            long n = 0;
            byte[] buf = new byte[64 * 1024];
            int k;
            while ((k = in.read(buf)) != -1) {
                n = Math.addExact(n, k);
                digest.update(buf, 0, k);
            }
            if (n != expected) throw ControlCodec.unavailable();
            return new BlobStat(n, ControlCodec.hex(digest.digest()), in.response().versionId());
        } catch (IOException | ArithmeticException e) {
            throw ControlCodec.unavailable();
        } finally {
            release(in);
        }
    }
}
