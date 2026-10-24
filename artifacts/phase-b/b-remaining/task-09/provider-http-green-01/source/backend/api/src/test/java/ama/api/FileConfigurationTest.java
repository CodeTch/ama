package ama.api;

import static org.junit.jupiter.api.Assertions.*;

import ama.storage.ManagedBackendId;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;

import java.util.*;

class FileConfigurationTest {
    @Test
    void productionAssemblyRejectsHttpEvenWithValidManagedIdentity() throws Exception {
        var definition =
                new ManagedBackendId.S3(
                        "http://127.0.0.1:47779",
                        "us-east-1",
                        "controlled-data",
                        "controlled-control",
                        true,
                        1024);
        var values = new HashMap<String, Object>();
        values.put("files.backends", "owned");
        values.put("files.default", "owned");
        values.put("files.owned.type", "s3");
        values.put("files.owned.id", definition.id());
        values.put("files.owned.revision", "1");
        values.put("files.owned.endpoint", definition.endpoint());
        values.put("files.owned.region", definition.region());
        values.put("files.owned.data-bucket", definition.dataBucket());
        values.put("files.owned.control-bucket", definition.controlBucket());
        values.put("files.owned.maximum-object-bytes", "1024");
        values.put("files.owned.access-key", "unused-controlled-key");
        values.put("files.owned.secret-key", "unused-controlled-secret");
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("controlled", values));
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            try (var ignored =
                                    new FileConfiguration().fileDefinitions(environment)) {}
                            ;
                        });
        assertEquals("Production S3 requires HTTPS", failure.getMessage());
    }
}
