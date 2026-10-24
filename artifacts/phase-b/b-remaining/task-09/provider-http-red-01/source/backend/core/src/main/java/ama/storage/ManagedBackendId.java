package ama.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Frozen v1: field count, then each strict UTF-8 byte length and bytes; all lengths big endian. */
public final class ManagedBackendId {
    private ManagedBackendId() {}

    public sealed interface Definition permits Local, S3 {
        String[] fields();

        String type();

        default String id() {
            return derive(fields());
        }
    }

    public record Local(
            String serviceOrigin, String volumeId, String relativeRoot, String filesystem)
            implements Definition {
        public Local {
            origin(serviceOrigin, true);
            value(volumeId);
            value(filesystem);
            if (!filesystem.matches("[a-z0-9_+-]+")
                    || relativeRoot == null
                    || relativeRoot.startsWith("/")
                    || relativeRoot.contains("\\"))
                throw new IllegalArgumentException("Invalid local binding");
            if (!relativeRoot.equals("."))
                for (String part : relativeRoot.split("/", -1)) {
                    value(part);
                    if (part.equals(".") || part.equals(".."))
                        throw new IllegalArgumentException("Invalid relative root");
                }
        }

        public String[] fields() {
            return new String[] {
                "ama-managed-local-v1",
                serviceOrigin,
                volumeId,
                relativeRoot,
                filesystem,
                "local-layout-v1"
            };
        }

        public String type() {
            return "local";
        }
    }

    public record S3(
            String endpoint,
            String region,
            String dataBucket,
            String controlBucket,
            boolean pathStyle,
            int keyBudget)
            implements Definition {
        public S3 {
            origin(endpoint, false);
            value(region);
            if (dataBucket == null
                    || controlBucket == null
                    || !dataBucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                    || !controlBucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                    || dataBucket.equals(controlBucket)
                    || keyBudget < 650
                    || keyBudget > 1024) throw new IllegalArgumentException("Invalid S3 binding");
        }

        public String[] fields() {
            return new String[] {
                "ama-managed-s3-v1",
                endpoint,
                region,
                dataBucket,
                controlBucket,
                Boolean.toString(pathStyle),
                Integer.toString(keyBudget),
                "layout-v3-199-200-8"
            };
        }

        public String type() {
            return "s3";
        }
    }

    public static String derive(String... fields) {
        Objects.requireNonNull(fields);
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(fields.length);
            for (String field : fields) {
                ama.contract.FileContracts.text(field);
                byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
                out.writeInt(encoded.length);
                out.write(encoded);
            }
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static void require(String id, Definition definition) {
        if (id == null
                || definition == null
                || !id.matches("[0-9a-f]{64}")
                || !id.equals(definition.id()))
            throw new IllegalArgumentException("Managed backend identity mismatch");
    }

    private static void value(String value) {
        ama.contract.FileContracts.text(value);
        if (value.isBlank() || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid binding value");
    }

    /**
     * No DNS resolution or alias normalization: explicit port, lowercase DNS/IPv4 or expanded IPv6.
     */
    public static void origin(String value, boolean httpsOnly) {
        if (value == null
                || !value.matches("https?://(?:[a-z0-9.-]+|\\[[0-9a-f:]+\\]):[1-9][0-9]{0,4}"))
            throw new IllegalArgumentException("Canonical origin required");
        var uri = java.net.URI.create(value);
        if (httpsOnly && !uri.getScheme().equals("https")
                || uri.getPort() > 65535
                || uri.getHost() == null)
            throw new IllegalArgumentException("Canonical origin required");
        String host = uri.getHost();
        if (host.startsWith("[")) {
            if (!host.matches("\\[(?:[0-9a-f]{4}:){7}[0-9a-f]{4}\\]"))
                throw new IllegalArgumentException("Expanded IPv6 required");
        } else if (host.matches("[0-9.]+")) {
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Canonical IPv4 required");
            for (String part : parts)
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255)
                    throw new IllegalArgumentException("Canonical IPv4 required");
        } else {
            for (String label : host.split("\\.", -1))
                if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
                    throw new IllegalArgumentException("Canonical DNS required");
        }
    }
}
