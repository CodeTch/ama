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
final class Task06NativeFixture implements AutoCloseable {
    final OwnedRoot base;
    final ManagedBackendId.Local binding;
    final URI uri;
    final int port;
    LocalFileStore store;
    InternalFileController server;
    Process process;
    Path configuration;

    Task06NativeFixture(Path root) throws Exception {
        if(Files.exists(root)||Files.isSymbolicLink(root)||!root.toAbsolutePath().normalize().startsWith(Path.of(System.getenv("AMA_TASK05_ROOT"),"native","data")))throw new IllegalStateException("Native case custody");
        if(Files.isSymbolicLink(root.getParent())||!root.getParent().toRealPath().startsWith(Path.of(System.getenv("AMA_TASK05_ROOT")).toRealPath()))throw new IllegalStateException("Native parent custody");
        LocalFixture.pki=Path.of(System.getenv("AMA_TASK05_ROOT"),"pki");
        base = new OwnedRoot(root);
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
                        Task05TransportIT.tls("server"),
                        Map.of("spiffe://ama/writer", InternalFileController.Role.WRITE));
        server.start();
        receipt("started-in-process");
        return client();
    }

    FileStoragePort client() throws Exception {
        return new LocalFileClient(uri, Task05TransportIT.tls("writer"));
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
        Files.createFile(configuration,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        try (var out = Files.newOutputStream(configuration,StandardOpenOption.WRITE)) {
            p.store(out, "owned managed service configuration");
        }
        LocalFixture.privateMode(configuration, false);
        Path log = base.root.resolveSibling(base.root.getFileName() + "-managed-service.log");
        Files.createFile(log,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        process = launch(configuration, log);
        receipt("started");
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
        builder.environment().put("AMA_FILE_KEYSTORE_PASSWORD", Files.readString(Path.of(System.getenv("AMA_TASK05_ROOT"),"pki","keystore-password")));
        builder.environment()
                .put("AMA_FILE_TRUSTSTORE_PASSWORD", Files.readString(Path.of(System.getenv("AMA_TASK05_ROOT"),"pki","keystore-password")));
        return builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    void receipt(String state)throws Exception {
        Path output=Task06Resources.evidencePath().resolve(base.root.getFileName()+"-native-"+state+".json");
        var data=new LinkedHashMap<String,Object>();data.put("state",state);data.put("observedAt",Instant.now().toString());data.put("root",base.root.toString());data.put("role",base.role.toString());data.put("managedId",binding.id());data.put("origin",uri.toString());data.put("port",port);data.put("parentPid",ProcessHandle.current().pid());data.put("mode",process==null?"in-process":"child");
        if(process!=null){data.put("pid",process.pid());data.put("alive",process.isAlive());data.put("startInstant",process.info().startInstant().map(Object::toString).orElse("unavailable-after-exit"));if(!process.isAlive())data.put("exit",process.exitValue());data.put("command",List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx64m","-XX:ActiveProcessorCount=2","-jar",Task06Resources.ROOT.getParent().getParent().resolve("backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar").toString(),configuration.toString()));}
        Files.writeString(output,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data),StandardOpenOption.CREATE_NEW);
    }
    static final class OwnedRoot {
        final Path root,role;final MountedVolumeGuard.Config config;
        OwnedRoot(Path selectedRoot)throws Exception {
            root=selectedRoot;Files.createDirectory(root,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
            role=root.resolveSibling(root.getFileName()+"-role");Files.createFile(role,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));Files.writeString(role,"active-instance",StandardOpenOption.WRITE);
            Path volume=root.resolve(".volume-id");Files.createFile(volume,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));Files.writeString(volume,"fixture-volume",StandardOpenOption.WRITE);
            String[] selected=null;
            for(String line:Files.readAllLines(Path.of("/proc/self/mountinfo"))){String[] fields=line.split(" ");Path mount=Path.of(LocalFixture.unescape(fields[4]));if(root.startsWith(mount)&&(selected==null||fields[4].length()>selected[4].length()))selected=fields;}
            if(selected==null)throw new IllegalStateException("No kernel mount");int separator=Arrays.asList(selected).indexOf("-");
            config=new MountedVolumeGuard.Config(root,Path.of(LocalFixture.unescape(selected[4])),selected[2],selected[separator+1],LocalFixture.unescape(selected[separator+2]),"fixture-volume",role,"active-instance");
        }
    }
    public void close() throws Exception {
        if (process != null) {
            process.destroyForcibly();
            if (!process.waitFor(10, TimeUnit.SECONDS))
                throw new AssertionError("owned managed process did not stop");
        }
        if (server != null) server.close();
        if (store != null) store.close();
        receipt("closed");
    }
}
