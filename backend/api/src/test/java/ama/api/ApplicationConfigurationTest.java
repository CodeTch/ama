package ama.api;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigurationTest {
    @Test
    void applicationConfigurationHasNoDuplicateYamlKeys() {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Map<?, ?> document;
        try (InputStream input = ApplicationConfigurationTest.class.getResourceAsStream("/application.yml")) {
            assertNotNull(input, "application.yml must be on the test classpath");
            document = new Yaml(options).load(input);
        } catch (Exception failure) {
            throw new AssertionError("application.yml must load without duplicate keys", failure);
        }

        assertTrue(document.containsKey("cas"), "CAS configuration must be declared");
        assertNotNull(document.get("cas"));
        Map<?, ?> cas = (Map<?, ?>) document.get("cas");
        assertTrue(cas.keySet().containsAll(java.util.Set.of(
                "enabled", "origin", "service-url", "logout-service-url",
                "timeout-ms", "identity-attribute", "session-lifetime-seconds")));
    }

    @Test
    void legacyCasControllerIsDisabledWhenTheConfiguredCasControllerIsEnabled() {
        var condition = CasController.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition, "legacy CAS controller must be conditional");
        assertTrue(condition.havingValue().equals("false"));
        assertTrue(condition.matchIfMissing());
    }
}
