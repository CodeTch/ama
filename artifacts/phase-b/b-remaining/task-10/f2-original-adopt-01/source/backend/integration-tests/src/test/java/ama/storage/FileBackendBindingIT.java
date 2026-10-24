package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Production assembly in fresh processes, with retained logs and only owned local roots. */
class FileBackendBindingIT {
    @Test
    void productionApiRejectsSameIdWithDifferentS3BucketInFreshJvm() throws Exception {
        var original =
                new ManagedBackendId.S3(
                        "https://s3.example.invalid:443",
                        "us-east-1",
                        "binding-original-data",
                        "binding-control-data",
                        true,
                        1024);
        var command =
                new ArrayList<String>(
                        List.of(
                                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                                "-Xmx192m",
                                "-XX:ActiveProcessorCount=2",
                                "-cp",
                                System.getProperty("java.class.path"),
                                RegistryProductionConfigProcess.class.getName(),
                                "--files.backends=primary",
                                "--files.default=primary",
                                "--files.primary.id=" + original.id(),
                                "--files.primary.type=s3",
                                "--files.primary.revision=1",
                                "--files.primary.endpoint=" + original.endpoint(),
                                "--files.primary.region=" + original.region(),
                                "--files.primary.data-bucket=binding-replacement-data",
                                "--files.primary.control-bucket=" + original.controlBucket()));
        Path root = Path.of("target/registry-binding");
        Files.createDirectories(root);
        Path log = Files.createTempFile(root, "changed-bucket-", ".log");
        var process =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            assertTrue(process.waitFor(40, TimeUnit.SECONDS), "startup deadline; log=" + log);
            assertNotEquals(0, process.exitValue());
            String output = Files.readString(log);
            assertTrue(output.contains("Managed backend identity mismatch"), output);
            assertFalse(output.contains("UNEXPECTED_CONFIG_ACCEPTED"));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    void nativeServiceAcceptsMatchingDefinitionAndRejectsSameIdChangedRootInFreshJvm()
            throws Exception {
        Path root = Path.of("target/registry-binding").toAbsolutePath();
        Files.createDirectories(root);
        try (var fixture = new ManagedLocalFixture(Files.createTempDirectory(root, "service-"))) {
            var client = fixture.startProcess();
            assertNotNull(client.list(fixture.binding.id(), "files", "", null, 1));
            var properties = new Properties();
            try (var in = Files.newInputStream(fixture.configuration)) {
                properties.load(in);
            }
            properties.setProperty(
                    "root", fixture.base.root.resolve("different-physical-root").toString());
            Path changed =
                    fixture.configuration.resolveSibling(
                            UUID.randomUUID() + "-changed-root.properties");
            try (var out = Files.newOutputStream(changed)) {
                properties.store(out, "owned negative binding configuration");
            }
            LocalFixture.privateMode(changed, false);
            Path log = changed.resolveSibling(UUID.randomUUID() + "-changed-root.log");
            var process = ManagedLocalFixture.launch(changed, log);
            try {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS), "startup deadline; log=" + log);
                assertNotEquals(0, process.exitValue());
                String output = Files.readString(log);
                assertTrue(output.contains("Managed backend identity mismatch"), output);
                assertFalse(output.contains("READY "));
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        }
    }
}
