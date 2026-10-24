package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ManagedBackendIdentityTest {
    @Test
    void fieldCountAndUtf8LengthsMatchIndependentPythonBytes() {
        assertEquals(
                "0867dd2e0c57184ea91aaa08b314b53472c6925f403e4f42823f230295ca2cda",
                ManagedBackendId.derive(
                        "ama-managed-local-v1",
                        "https://files.example.invalid:443",
                        "volume-42",
                        "managed/files",
                        "xfs",
                        "local-layout-v1"));
        assertEquals(
                "2c1f51a65bcb07236ca3625be16df476d8aec7db2aef5ad1c982b023b57e6e71",
                ManagedBackendId.derive(
                        "ama-managed-s3-v1",
                        "https://s3.example.invalid:443",
                        "us-east-1",
                        "managed-data",
                        "managed-control",
                        "true",
                        "1024",
                        "layout-v3-199-200-8"));
    }

    @Test
    void physicalChangesCannotReuseManagedIdentity() {
        var original =
                new ManagedBackendId.S3(
                        "https://s3.example.invalid:443",
                        "us-east-1",
                        "managed-data",
                        "managed-control",
                        true,
                        1024);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManagedBackendId.require(
                                original.id(),
                                new ManagedBackendId.S3(
                                        original.endpoint(),
                                        original.region(),
                                        "other-data",
                                        original.controlBucket(),
                                        true,
                                        1024)));
        var local =
                new ManagedBackendId.Local(
                        "https://files.example.invalid:443", "volume-42", "managed/files", "xfs");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManagedBackendId.require(
                                local.id(),
                                new ManagedBackendId.Local(
                                        local.serviceOrigin(),
                                        local.volumeId(),
                                        "other/root",
                                        "xfs")));
    }

    @Test
    void ambiguousOriginsAndMalformedUnicodeFailClosed() {
        for (String bad :
                new String[] {
                    "https://files.example.invalid",
                    "https://FILES.example.invalid:443",
                    "https://files.example.invalid:0443",
                    "https://files.example.invalid:443/",
                    "https://127.00.0.1:443",
                    "https://user@files.example.invalid:443",
                    "https://files.example.invalid:443?q=1",
                    "https://[::1]:443"
                })
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ManagedBackendId.Local(bad, "v", "data", "xfs"),
                    bad);
        assertThrows(IllegalArgumentException.class, () -> ManagedBackendId.derive("bad\uD800"));
        assertDoesNotThrow(
                () ->
                        ManagedBackendId.origin(
                                "https://[0000:0000:0000:0000:0000:0000:0000:0001]:443", true));
    }

    @Test
    void credentialAndTimeoutRotationDoNotChangePhysicalId() {
        var physical =
                new ManagedBackendId.S3(
                        "https://s3.example.invalid:443",
                        "us-east-1",
                        "managed-data",
                        "managed-control",
                        true,
                        1024);
        for (int i = 1; i <= 2; i++) {
            final int n = i;
            var config =
                    new ama.storage.s3.S3Config(
                            physical.id(),
                            java.net.URI.create(physical.endpoint()),
                            physical.region(),
                            physical.dataBucket(),
                            physical.controlBucket(),
                            () ->
                                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials
                                            .create(
                                                    "controlled-unused-" + n,
                                                    "controlled-unused-secret-" + n),
                            true,
                            1024,
                            1024 * i,
                            5L * 1024 * 1024,
                            5L * 1024 * 1024 * 1024,
                            10000,
                            1024 * 1024,
                            8,
                            java.time.Duration.ofSeconds(i));
            assertEquals(
                    "2c1f51a65bcb07236ca3625be16df476d8aec7db2aef5ad1c982b023b57e6e71",
                    ManagedBackendAdapters.definition(config).id());
        }
    }
}
