package ama.file;

import ama.storage.ManagedBackendId;
import ama.storage.internal.ServiceTls;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * Standalone ama-file process; deployment supplies mounted-volume identity and controlled service
 * PKI.
 */
public final class FileServiceMain {
    private FileServiceMain() {}

    static int workerThreads(Properties properties){
        String value=properties.getProperty("workerThreads","16");
        if(!value.matches("[1-9][0-9]?")||Integer.parseInt(value)>32)throw new IllegalArgumentException("File worker count must be in 1..32");
        return Integer.parseInt(value);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("one protected configuration file required");
        Properties p = new Properties();
        try (var in = Files.newInputStream(Path.of(args[0]))) {
            p.load(in);
        }
        String backendId = required(p, "backendId");
        if (backendId.matches("[0-9a-f]{64}")) {
            Path root = Path.of(required(p, "root")), mount = Path.of(required(p, "mountpoint"));
            if (!root.isAbsolute()
                    || !mount.isAbsolute()
                    || !root.normalize().equals(root)
                    || !mount.normalize().equals(mount)
                    || !root.startsWith(mount))
                throw new IllegalArgumentException("Invalid managed root");
            String relative = root.equals(mount) ? "." : mount.relativize(root).toString();
            ManagedBackendId.require(
                    backendId,
                    new ManagedBackendId.Local(
                            required(p, "serviceOrigin"),
                            required(p, "volumeId"),
                            relative,
                            required(p, "filesystem")));
        }
        var guard =
                new MountedVolumeGuard(
                        new MountedVolumeGuard.Config(
                                Path.of(required(p, "root")),
                                Path.of(required(p, "mountpoint")),
                                required(p, "device"),
                                required(p, "filesystem"),
                                required(p, "source"),
                                required(p, "volumeId"),
                                Path.of(required(p, "roleFile")),
                                required(p, "activeRole")));
        var tls =
                ServiceTls.context(
                        Path.of(required(p, "keyStore")),
                        password("AMA_FILE_KEYSTORE_PASSWORD"),
                        Path.of(required(p, "trustStore")),
                        password("AMA_FILE_TRUSTSTORE_PASSWORD"));
        Map<String, InternalFileController.Role> identities = new HashMap<>();
        for (String identity : required(p, "writerIdentities").split(",")) {
            if (identity.isBlank())
                throw new IllegalArgumentException("writer identities required");
            identities.put(identity.strip(), InternalFileController.Role.WRITE);
        }
        for (String identity : p.getProperty("readerIdentities", "").split(","))
            if (!identity.isBlank())
                identities.putIfAbsent(identity.strip(), InternalFileController.Role.READ);
        // Bound exchange lifetime even if a trusted peer stalls a binary stream.
        System.setProperty("sun.net.httpserver.maxReqTime", "300");
        System.setProperty("sun.net.httpserver.maxRspTime", "300");
        var store =
                new LocalFileStore(
                        Path.of(required(p, "root")),
                        required(p, "backendId"),
                        guard,
                        DurabilityHooks.NONE);
        var server =
                new InternalFileController(
                        store,
                        new InetSocketAddress(
                                required(p, "bindAddress"), Integer.parseInt(required(p, "port"))),
                        tls,
                        identities,workerThreads(p));
        System.getLogger(FileServiceMain.class.getName()).log(System.Logger.Level.INFO,"File worker threads="+workerThreads(p)+" queueCapacity=128");
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    server.close();
                                    store.close();
                                }));
        server.start();
        System.out.println("READY " + server.port());
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("missing service setting: " + key);
        return value;
    }

    private static char[] password(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("service PKI password required");
        return value.toCharArray();
    }
}
