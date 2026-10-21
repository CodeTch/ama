package ama.storage;

import ama.file.*;
import ama.storage.internal.ServiceTls;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import javax.net.ssl.*;

/** Owned loopback/process/real-filesystem fixture; generated PKI stays under ignored target/. */
final class LocalFixture implements AutoCloseable {
    static final char[] PASSWORD = "test-fixture-only".toCharArray();
    static Path pki;
    final Path root, role;
    final MountedVolumeGuard.Config config;
    LocalFileStore store;
    InternalFileController server;
    Process process;
    URI uri;

    LocalFixture(Path root) throws Exception {
        this.root = root;
        Files.createDirectories(root);
        privateMode(root, true);
        role = root.resolveSibling(root.getFileName() + "-role");
        Files.writeString(role, "active-instance");
        privateMode(role, false);
        Files.writeString(root.resolve(".volume-id"), "fixture-volume");
        privateMode(root.resolve(".volume-id"), false);
        String[] selected = null;
        for (String line : Files.readAllLines(Path.of("/proc/self/mountinfo"))) {
            String[] f = line.split(" ");
            Path mount = Path.of(unescape(f[4]));
            if (root.startsWith(mount)
                    && (selected == null || f[4].length() > selected[4].length())) selected = f;
        }
        if (selected == null) throw new IllegalStateException("no kernel mount");
        int sep = Arrays.asList(selected).indexOf("-");
        config =
                new MountedVolumeGuard.Config(
                        root,
                        Path.of(unescape(selected[4])),
                        selected[2],
                        selected[sep + 1],
                        unescape(selected[sep + 2]),
                        "fixture-volume",
                        role,
                        "active-instance");
    }

    static String unescape(String s) {
        return s.replace("\\040", " ").replace("\\134", "\\");
    }

    static void privateMode(Path p, boolean dir) throws IOException {
        Files.setPosixFilePermissions(
                p, PosixFilePermissions.fromString(dir ? "rwx------" : "rw-------"));
    }

    LocalFileStore open(DurabilityHooks hooks) {
        return new LocalFileStore(root, "local", new MountedVolumeGuard(config), hooks);
    }

    LocalFileClient startServer() throws Exception {
        return startServer(DurabilityHooks.NONE);
    }

    LocalFileClient startServer(DurabilityHooks hooks) throws Exception {
        ensurePki();
        store = open(hooks);
        server =
                new InternalFileController(
                        store,
                        new InetSocketAddress("127.0.0.1", 0),
                        tls("server"),
                        Map.of(
                                "spiffe://ama/writer",
                                InternalFileController.Role.WRITE,
                                "spiffe://ama/reader",
                                InternalFileController.Role.READ));
        server.start();
        uri = URI.create("https://localhost:" + server.port());
        return client("writer");
    }

    LocalFileClient client(String identity) throws Exception {
        return new LocalFileClient(uri, tls(identity));
    }

    LocalFileClient startProcess() throws Exception {
        ensurePki();
        Properties p = new Properties();
        p.setProperty("root", root.toString());
        p.setProperty("mountpoint", config.mountpoint().toString());
        p.setProperty("device", config.device());
        p.setProperty("filesystem", config.filesystem());
        p.setProperty("source", config.source());
        p.setProperty("volumeId", config.volumeId());
        p.setProperty("roleFile", role.toString());
        p.setProperty("activeRole", config.activeRole());
        p.setProperty("backendId", "local");
        p.setProperty("keyStore", pki.resolve("server.p12").toString());
        p.setProperty("trustStore", pki.resolve("trust.p12").toString());
        p.setProperty("writerIdentities", "spiffe://ama/writer");
        p.setProperty("bindAddress", "127.0.0.1");
        p.setProperty("port", "0");
        Path props = root.resolveSibling(root.getFileName() + "-service.properties");
        try (var out = Files.newOutputStream(props)) {
            p.store(out, "owned loopback test");
        }
        privateMode(props, false);
        Path repo = Path.of("").toAbsolutePath();
        while (!Files.exists(repo.resolve("tools/checks/run.py"))) repo = repo.getParent();
        Path log = root.resolveSibling(root.getFileName() + "-process.log");
        var builder =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                                "-Xmx64m",
                                "-jar",
                                repo.resolve(
                                                "backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar")
                                        .toString(),
                                props.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile());
        builder.environment().put("AMA_FILE_KEYSTORE_PASSWORD", new String(PASSWORD));
        builder.environment().put("AMA_FILE_TRUSTSTORE_PASSWORD", new String(PASSWORD));
        process = builder.start();
        Instant deadline = Instant.now().plusSeconds(25);
        while (Instant.now().isBefore(deadline)) {
            for (String line : Files.exists(log) ? Files.readAllLines(log) : List.<String>of())
                if (line.startsWith("READY ")) {
                    uri = URI.create("https://localhost:" + line.substring(6));
                    return client("writer");
                }
            if (!process.isAlive())
                throw new AssertionError("file process failed: " + Files.readString(log));
            Thread.sleep(50);
        }
        throw new AssertionError("file process startup deadline");
    }

    void killProcess() throws Exception {
        if (process != null) {
            process.destroyForcibly();
            if (!process.waitFor(10, TimeUnit.SECONDS))
                throw new AssertionError("owned process did not stop");
            process = null;
        }
    }

    static synchronized void ensurePki() throws Exception {
        if (pki != null) return;
        Path target = Path.of("target/local-fixtures").toAbsolutePath();
        Files.createDirectories(target);
        pki = Files.createTempDirectory(target, "pki-");
        privateMode(pki, true);
        run(
                "openssl",
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-noenc",
                "-keyout",
                "ca.key",
                "-out",
                "ca.crt",
                "-days",
                "2",
                "-subj",
                "/CN=AMA local test CA");
        run(
                "openssl",
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-noenc",
                "-keyout",
                "rogue-ca.key",
                "-out",
                "rogue-ca.crt",
                "-days",
                "2",
                "-subj",
                "/CN=Untrusted local test CA");
        for (String name : List.of("server", "writer", "reader", "outsider", "rogue", "expired")) {
            run(
                    "openssl",
                    "req",
                    "-newkey",
                    "rsa:2048",
                    "-noenc",
                    "-keyout",
                    name + ".key",
                    "-out",
                    name + ".csr",
                    "-subj",
                    "/CN=" + name);
            String san =
                    name.equals("server")
                            ? "DNS:localhost"
                            : "URI:spiffe://ama/" + (name.equals("expired") ? "writer" : name);
            Files.writeString(
                    pki.resolve(name + ".ext"),
                    "subjectAltName="
                            + san
                            + "\nextendedKeyUsage="
                            + (name.equals("server") ? "serverAuth" : "clientAuth")
                            + "\n"
                            + "basicConstraints=CA:FALSE\n"
                            + "keyUsage=digitalSignature,keyEncipherment\n");
            String ca = name.equals("rogue") ? "rogue-ca" : "ca";
            run(
                    "openssl",
                    "x509",
                    "-req",
                    "-in",
                    name + ".csr",
                    "-CA",
                    ca + ".crt",
                    "-CAkey",
                    ca + ".key",
                    "-CAcreateserial",
                    "-out",
                    name + ".crt",
                    "-days",
                    name.equals("expired") ? "-1" : "2",
                    "-extfile",
                    name + ".ext");
            run(
                    "openssl",
                    "pkcs12",
                    "-export",
                    "-inkey",
                    name + ".key",
                    "-in",
                    name + ".crt",
                    "-certfile",
                    ca + ".crt",
                    "-out",
                    name + ".p12",
                    "-passout",
                    "pass:" + new String(PASSWORD));
        }
        run(
                Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
                "-importcert",
                "-noprompt",
                "-alias",
                "ca",
                "-file",
                "ca.crt",
                "-keystore",
                "trust.p12",
                "-storetype",
                "PKCS12",
                "-storepass",
                new String(PASSWORD));
        run(
                Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
                "-importcert",
                "-noprompt",
                "-alias",
                "rogue",
                "-file",
                "rogue-ca.crt",
                "-keystore",
                "rogue-trust.p12",
                "-storetype",
                "PKCS12",
                "-storepass",
                new String(PASSWORD));
    }

    static void run(String... command) throws Exception {
        var p =
                new ProcessBuilder(command)
                        .directory(pki.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(pki.resolve("generation.log").toFile())
                        .start();
        if (!p.waitFor(20, TimeUnit.SECONDS) || p.exitValue() != 0)
            throw new AssertionError("test PKI command failed: " + command[0]);
    }

    static SSLContext tls(String name) throws Exception {
        ensurePki();
        return ServiceTls.context(
                pki.resolve(name + ".p12"), PASSWORD, pki.resolve("trust.p12"), PASSWORD);
    }

    static SSLContext noCertificate() throws Exception {
        ensurePki();
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(pki.resolve("trust.p12"))) {
            trust.load(in, PASSWORD);
        }
        var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(trust);
        var ssl = SSLContext.getInstance("TLS");
        ssl.init(new KeyManager[0], tm.getTrustManagers(), null);
        return ssl;
    }

    static long volumeDescriptors(Path root) throws IOException {
        try (var descriptors = Files.list(Path.of("/proc/self/fd"))) {
            return descriptors
                    .filter(
                            fd -> {
                                try {
                                    return Files.readSymbolicLink(fd).startsWith(root);
                                } catch (IOException concurrentClose) {
                                    return false;
                                }
                            })
                    .count();
        }
    }

    @Override
    public void close() throws Exception {
        killProcess();
        if (server != null) server.close();
        if (store != null) store.close();
    }
}
