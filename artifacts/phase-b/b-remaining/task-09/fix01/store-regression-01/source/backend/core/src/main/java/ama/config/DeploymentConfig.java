package ama.config;
import java.util.Map;
import java.net.URI;
import java.util.Set;

/** Database source and file backend are deliberately independent deployment choices. */
public record DeploymentConfig(String databaseMode, String storageType, String defaultBackendId) {
    public static DeploymentConfig parse(Map<String, String> values) {
        String database = values.getOrDefault("database.mode", "private-ha").trim();
        String storage = values.getOrDefault("storage.type", "local").trim();
        String backend = values.getOrDefault("storage.default-backend-id", "local-primary").trim();
        if (!Set.of("private-ha", "enterprise-platform").contains(database)) throw invalid("database.mode");
        if (!Set.of("local", "s3").contains(storage)) throw invalid("storage.type");
        if (backend.isBlank()) throw invalid("storage.default-backend-id");
        if (storage.equals("s3")) {
            String endpoint = required(values, "storage.s3.endpoint");
            try {
                URI uri = URI.create(endpoint);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw invalid("storage.s3.endpoint");
            } catch (IllegalArgumentException error) { throw invalid("storage.s3.endpoint"); }
            for (String key : new String[]{"bucket", "region", "access-key", "secret-key"}) required(values, "storage.s3." + key);
        }
        return new DeploymentConfig(database, storage, backend);
    }
    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw invalid(key);
        return value.trim();
    }
    private static IllegalArgumentException invalid(String key) { return new IllegalArgumentException("Missing or invalid configuration: " + key); }
}
