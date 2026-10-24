package ama.storage.s3;

import ama.contract.StorageContracts.*;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Explicit immutable backend definition. No auto-configuration or production readiness claim. */
public record S3Config(
        String backendId,
        URI endpoint,
        String region,
        String dataBucket,
        String controlBucket,
        AwsCredentialsProvider credentials,
        boolean pathStyle,
        int keyBudget,
        int chunkBytes,
        long nativeMinimum,
        long nativeMaximum,
        int nativeMaxParts,
        long maximumObject,
        int admission,
        Duration timeout) {
    public S3Config {
        new StorageKey(backendId, "validation", "validation");
        Objects.requireNonNull(credentials);
        if (endpoint == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null
                || endpoint.getHost() == null
                || !java.util.Set.of("http", "https").contains(endpoint.getScheme())
                || (endpoint.getPath() != null
                        && !endpoint.getPath().isEmpty()
                        && !endpoint.getPath().equals("/"))
                || region == null
                || region.isBlank()
                || dataBucket == null
                || controlBucket == null
                || !dataBucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                || !controlBucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                || dataBucket.equals(controlBucket)
                || keyBudget < 650
                || keyBudget > 1024
                || chunkBytes < 1
                || chunkBytes > 8 * 1024 * 1024
                || nativeMinimum < 5L * 1024 * 1024
                || nativeMaximum < nativeMinimum
                || nativeMaxParts < 1
                || nativeMaxParts > 10000
                || maximumObject < 0
                || admission < 2
                || admission > 256
                || timeout == null
                || timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(5)) > 0)
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
    }

    String identity() {
        return ControlCodec.hash(physicalBinding());
    }

    /** Static physical routing only; credentials and operational settings never select history. */
    byte[] physicalBinding() {
        return ControlCodec.pack(
                "physical-config-v3",
                backendId,
                endpoint.toString(),
                region,
                dataBucket,
                controlBucket,
                Boolean.toString(pathStyle),
                Integer.toString(keyBudget),
                "layout-v3-199-200-8");
    }

    @Override
    public String toString() {
        return "S3Config[redacted]";
    }
}
