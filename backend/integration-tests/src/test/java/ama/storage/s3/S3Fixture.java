package ama.storage.s3;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Real SDK/HTTP fixture. Each instance owns fresh buckets; no destructive cleanup/history TTL. */
public final class S3Fixture implements AutoCloseable {
    public final S3Config config;
    public final S3Client admin;
    private final List<S3FileStorage> clients = new ArrayList<>();

    public static StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(required("AMA_S3_ACCESS"), required("AMA_S3_SECRET")));
    }

    static String required(String key) {
        String s = System.getenv(key);
        if (s == null) throw new IllegalStateException("owned S3 fixture environment missing");
        return s;
    }

    public S3Fixture(String backend) {
        this(backend, URI.create(required("AMA_S3_ENDPOINT")));
    }

    public S3Fixture(String backend, URI endpoint) {
        String id = UUID.randomUUID().toString();
        config = config(backend, endpoint, "v3-data-" + id, "v3-control-" + id);
        admin = admin(URI.create(required("AMA_S3_ENDPOINT")));
        admin.createBucket(b -> b.bucket(config.dataBucket()));
        admin.createBucket(b -> b.bucket(config.controlBucket()));
    }

    public static S3Config config(String backend, URI endpoint, String data, String control) {
        return new S3Config(
                backend,
                endpoint,
                "us-east-1",
                data,
                control,
                credentials(),
                true,
                1024,
                8 * 1024 * 1024,
                5L * 1024 * 1024,
                5L * 1024 * 1024 * 1024,
                10000,
                50L * 1024 * 1024 * 1024,
                8,
                Duration.ofSeconds(20));
    }

    public static S3Client admin(URI endpoint) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials())
                .forcePathStyle(true)
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder()
                                .connectionTimeout(Duration.ofSeconds(3))
                                .socketTimeout(Duration.ofSeconds(20))
                                .proxyConfiguration(
                                        ProxyConfiguration.builder()
                                                .useSystemPropertyValues(false)
                                                .useEnvironmentVariablesValues(false)
                                                .build()))
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .retryStrategy(
                                        StandardRetryStrategy.builder().maxAttempts(1).build())
                                .build())
                .build();
    }

    public S3FileStorage open() {
        var s = new S3FileStorage(config);
        clients.add(s);
        return s;
    }

    public void version(String mode) {
        admin.putBucketVersioning(
                b ->
                        b.bucket(config.dataBucket())
                                .versioningConfiguration(
                                        v -> v.status(BucketVersioningStatus.fromValue(mode))));
    }

    @Override
    public void close() {
        for (var s : clients) s.close();
        admin.close();
    }
}
