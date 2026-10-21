package ama.storage.s3;

import ama.contract.StorageContracts.*;
import ama.storage.FileStoragePort;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.ProxyConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.util.*;

/**
 * Explicit construction only. Production capability qualification and business availability are
 * external.
 */
public final class S3FileStorage implements FileStoragePort, AutoCloseable {
    private final S3Objects io;
    private final S3Layout layout;
    private final Staging staging;
    private final NativePublisher publisher;
    private final SessionJournal sessions;
    private final RangeReader ranges;

    public S3FileStorage(S3Config config) {
        var client =
                S3Client.builder()
                        .endpointOverride(config.endpoint())
                        .region(Region.of(config.region()))
                        .credentialsProvider(config.credentials())
                        .httpClientBuilder(
                                UrlConnectionHttpClient.builder()
                                        .connectionTimeout(config.timeout())
                                        .socketTimeout(config.timeout())
                                        .proxyConfiguration(
                                                ProxyConfiguration.builder()
                                                        .useSystemPropertyValues(false)
                                                        .useEnvironmentVariablesValues(false)
                                                        .build()))
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(config.pathStyle())
                                        .build())
                        .overrideConfiguration(
                                ClientOverrideConfiguration.builder()
                                        .apiCallTimeout(config.timeout())
                                        .apiCallAttemptTimeout(config.timeout())
                                        .retryStrategy(
                                                StandardRetryStrategy.builder()
                                                        .maxAttempts(1)
                                                        .build())
                                        .build())
                        .build();
        io = new S3Objects(client, config);
        layout = new S3Layout(config);
        staging = new Staging(io);
        publisher = new NativePublisher(io, layout);
        sessions = new SessionJournal(io, layout, staging, publisher);
        ranges = new RangeReader(io, layout);
    }

    @Override
    public StoredBlob put(StorageKey key, InputStream body, long size, String sha256) {
        if (key == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        layout.data(key);
        publisher.interval(size);
        String hash = ControlCodec.hashArgument(sha256), owner = ControlCodec.id();
        var candidate = staging.stage(owner, body, size, hash);
        return publisher.publish(
                key, size, hash, owner, new PublicationSource(staging, 1, i -> candidate));
    }

    @Override
    public BlobStat stat(StorageKey key) {
        io.binding(layout, key, false);
        return io.stat(layout.data(key));
    }

    @Override
    public InputStream read(StorageKey key, long offset, long length) {
        return ranges.read(key, offset, length);
    }

    @Override
    public void delete(StorageKey key) {
        String physical = layout.data(key);
        if (!io.scope(layout, key.namespace(), false)) return;
        byte[] binding = io.optional(layout.bindingKey(key));
        if (binding == null) return;
        if (!Arrays.equals(binding, layout.binding(key))) throw ControlCodec.unavailable();
        io.call(
                () ->
                        io.client.deleteObject(
                                DeleteObjectRequest.builder()
                                        .bucket(io.config.dataBucket())
                                        .key(physical)
                                        .build()));
    }

    @Override
    public UploadToken beginUpload(UploadSpec spec) {
        return sessions.begin(spec);
    }

    @Override
    public PartReceipt writePart(UploadToken t, int p, InputStream b, long n, String h) {
        return sessions.write(t, p, b, n, h);
    }

    @Override
    public StoredBlob complete(UploadToken t, List<PartReceipt> p, String h) {
        return sessions.complete(t, p, h);
    }

    /**
     * Recovery of an already frozen decision using only the original token and fixed configuration.
     */
    public StoredBlob resume(UploadToken token) {
        return sessions.resume(token);
    }

    @Override
    public void abort(UploadToken t) {
        sessions.abort(t);
    }

    @Override
    public BlobPage list(
            String backend, String namespace, String prefix, String cursor, int limit) {
        layout.backend(backend);
        new StorageKey(backend, namespace, "validation");
        if (prefix == null || limit < 1 || limit > 1000)
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        String scoped = layout.prefix(namespace, prefix), next = null, last = "";
        if (cursor != null) {
            try {
                String[] c =
                        ControlCodec.unpack(Base64.getUrlDecoder().decode(cursor), "cursor-v3", 8);
                if (!c[1].equals(io.config.identity())
                        || !c[2].equals(backend)
                        || !c[3].equals(namespace)
                        || !c[4].equals(layout.scopeHash(namespace))
                        || !c[5].equals(prefix)
                        || c[6].isEmpty()
                        || (!c[7].isEmpty() && !c[7].startsWith(scoped)))
                    throw ControlCodec.unavailable();
                if (!c[7].isEmpty()) layout.fromData(namespace, c[7]);
                next = c[6];
                last = c[7];
            } catch (RuntimeException e) {
                throw new StorageException(ErrorCode.INVALID_ARGUMENT);
            }
        }
        if (scoped.length() > io.config.keyBudget()
                || prefix.indexOf('\\') >= 0
                || prefix.indexOf('%') >= 0
                || prefix.indexOf(':') >= 0
                || prefix.codePoints().anyMatch(Character::isISOControl))
            return new BlobPage(List.of(), null);
        if (!io.scope(layout, namespace, false)) return new BlobPage(List.of(), null);
        var request =
                ListObjectsV2Request.builder()
                        .bucket(io.config.dataBucket())
                        .prefix(scoped)
                        .maxKeys(limit)
                        .continuationToken(next)
                        .build();
        var page = io.call(() -> io.client.listObjectsV2(request));
        if (page.contents().size() > limit) throw ControlCodec.unavailable();
        List<BlobItem> items = new ArrayList<>();
        String prior = last;
        for (var item : page.contents()) {
            String path = item.key();
            if (path == null || !path.startsWith(scoped) || path.compareTo(prior) <= 0)
                throw ControlCodec.unavailable();
            prior = path;
            StorageKey key;
            try {
                key = layout.fromData(namespace, path);
            } catch (StorageException e) {
                throw ControlCodec.unavailable();
            }
            if (!layout.data(key).equals(path) || !key.objectKey().startsWith(prefix))
                throw ControlCodec.unavailable();
            try {
                items.add(new BlobItem(key, stat(key)));
            } catch (StorageException e) {
                if (e.code() != ErrorCode.NOT_FOUND) throw e;
            }
        }
        String result = null;
        if (Boolean.TRUE.equals(page.isTruncated())) {
            String token = page.nextContinuationToken();
            if (token == null || token.isEmpty() || token.equals(next))
                throw ControlCodec.unavailable();
            result =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    ControlCodec.pack(
                                            "cursor-v3",
                                            io.config.identity(),
                                            backend,
                                            namespace,
                                            layout.scopeHash(namespace),
                                            prefix,
                                            token,
                                            prior));
        }
        return new BlobPage(items, result);
    }

    @Override
    public void close() {
        io.client.close();
    }
}
