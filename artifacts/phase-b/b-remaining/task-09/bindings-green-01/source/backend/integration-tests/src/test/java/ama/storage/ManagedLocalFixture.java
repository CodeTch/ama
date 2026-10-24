package ama.storage;

import ama.file.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Owns new managed-ID roots; reuses reviewed PKI/mount setup without changing infrastructure
 * helpers.
 */
final class ManagedLocalFixture implements AutoCloseable {
    final LocalFixture base;
    final ManagedBackendId.Local binding;
    final URI uri;
    final int port;
    LocalFileStore store;
    InternalFileController server;
    Process process;
    Path configuration;

    ManagedLocalFixture(Path root) throws Exception {
        base = new LocalFixture(root);
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        uri = URI.create("https://localhost:" + port);
        String relative =
                base.config.root().equals(base.config.mountpoint())
                        ? "."
                        : base.config.mountpoint().relativize(base.config.root()).toString();
        binding =
                new ManagedBackendId.Local(
                        uri.toString(), base.config.volumeId(), relative, base.config.filesystem());
    }

    FileStoragePort startServer() throws Exception {
        store =
                new LocalFileStore(
                        base.root,
                        binding.id(),
                        new MountedVolumeGuard(base.config),
                        DurabilityHooks.NONE);
        server =
                new InternalFileController(
                        store,
                        new InetSocketAddress("127.0.0.1", port),
                        LocalFixture.tls("server"),
                        Map.of("spiffe://ama/writer", InternalFileController.Role.WRITE));
        server.start();
        return client();
    }

    FileStoragePort client() throws Exception {
        return new LocalFileClient(uri, LocalFixture.tls("writer"));
    }

    FileStoragePort startProcess() throws Exception {
        LocalFixture.ensurePki();
        Properties p = new Properties();
        p.setProperty("root", base.root.toString());
        p.setProperty("mountpoint", base.config.mountpoint().toString());
        p.setProperty("device", base.config.device());
        p.setProperty("filesystem", base.config.filesystem());
        p.setProperty("source", base.config.source());
        p.setProperty("volumeId", base.config.volumeId());
        p.setProperty("roleFile", base.role.toString());
        p.setProperty("activeRole", base.config.activeRole());
        p.setProperty("backendId", binding.id());
        p.setProperty("serviceOrigin", uri.toString());
        p.setProperty("keyStore", LocalFixture.pki.resolve("server.p12").toString());
        p.setProperty("trustStore", LocalFixture.pki.resolve("trust.p12").toString());
        p.setProperty("writerIdentities", "spiffe://ama/writer");
        p.setProperty("bindAddress", "127.0.0.1");
        p.setProperty("port", Integer.toString(port));
        configuration = base.root.resolveSibling(base.root.getFileName() + "-managed.properties");
        try (var out = Files.newOutputStream(configuration)) {
            p.store(out, "owned managed service configuration");
        }
        LocalFixture.privateMode(configuration, false);
        Path log = base.root.resolveSibling(base.root.getFileName() + "-managed-service.log");
        process = launch(configuration, log);
        Instant end = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(end)) {
            for (String line : Files.readAllLines(log))
                if (line.startsWith("READY ")) return client();
            if (!process.isAlive())
                throw new AssertionError("managed native process failed; log=" + log);
            Thread.sleep(50);
        }
        throw new AssertionError("managed native startup deadline");
    }

    static Process launch(Path properties, Path log) throws Exception {
        Path repo = Path.of("").toAbsolutePath();
        while (!Files.exists(repo.resolve("tools/checks/run.py"))) repo = repo.getParent();
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin/java").toString(),
                        "-Xmx64m",
                        "-XX:ActiveProcessorCount=2",
                        "-jar",
                        repo.resolve(
                                        "backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar")
                                .toString(),
                        properties.toString());
        builder.environment().put("AMA_FILE_KEYSTORE_PASSWORD", new String(LocalFixture.PASSWORD));
        builder.environment()
                .put("AMA_FILE_TRUSTSTORE_PASSWORD", new String(LocalFixture.PASSWORD));
        return builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    public void close() throws Exception {
        if (process != null) {
            process.destroyForcibly();
            if (!process.waitFor(10, TimeUnit.SECONDS))
                throw new AssertionError("owned managed process did not stop");
        }
        if (server != null) server.close();
        if (store != null) store.close();
        base.close();
    }
}
