package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.yaml.snakeyaml.Yaml;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;

/** Checks actual HTTP errors against the source OpenAPI response, including external references. */
final class RegistryDeclaredError {
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Located(Path file, JsonNode value) {
        Located get(String name) {
            return new Located(file, value.path(name));
        }

        Located resolve() throws Exception {
            Located current = this;
            var visited = new HashSet<String>();
            while (current.value.has("$ref")) {
                String ref = current.value.get("$ref").asText();
                assertTrue(visited.add(current.file + ":" + ref), "Cyclic OpenAPI reference");
                String[] parts = ref.split("#", 2);
                Path target =
                        parts[0].isEmpty()
                                ? current.file
                                : current.file.getParent().resolve(parts[0]).normalize();
                try (var input = Files.newInputStream(target)) {
                    JsonNode document = JSON.valueToTree(new Yaml().load(input));
                    current =
                            new Located(
                                    target, parts.length == 2 ? document.at(parts[1]) : document);
                    assertFalse(
                            current.value.isMissingNode(), "Unresolved OpenAPI reference: " + ref);
                }
            }
            return current;
        }
    }

    static void assertMatches(String path, String method, HttpResponse<String> response)
            throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("contracts/openapi/api.yaml")))
            root = root.getParent();
        assertNotNull(root, "OpenAPI source root must exist");
        Path file = root.resolve("contracts/openapi/api.yaml");
        Located document;
        try (var input = Files.newInputStream(file)) {
            document = new Located(file, JSON.valueToTree(new Yaml().load(input)));
        }
        Located declared =
                document.get("paths")
                        .get(path)
                        .resolve()
                        .get(method)
                        .get("responses")
                        .get(Integer.toString(response.statusCode()))
                        .resolve()
                        .get("content")
                        .get("application/json")
                        .get("schema")
                        .resolve();
        assertFalse(
                declared.value.isMissingNode(),
                "No declared response schema for " + path + " " + response.statusCode());
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .startsWith("application/json"));
        System.out.println(
                "OpenAPI response contract: "
                        + method
                        + " "
                        + path
                        + " "
                        + response.statusCode()
                        + " "
                        + response.body());
        validate(
                declared, JSON.readTree(response.body()), "$ [HTTP " + response.statusCode() + "]");
    }

    // The closed error envelope currently uses only object/string, required, enum and UUID format.
    // Reject unsupported types instead of silently accepting an unvalidated response shape.
    private static void validate(Located located, JsonNode actual, String at) throws Exception {
        Located resolved = located.resolve();
        JsonNode schema = resolved.value;
        switch (schema.path("type").asText()) {
            case "object" -> {
                assertTrue(actual.isObject(), at + " must be an object");
                for (JsonNode key : schema.path("required"))
                    assertTrue(actual.has(key.asText()), at + " missing " + key.asText());
                var fields = actual.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    Located property = resolved.get("properties").get(field.getKey());
                    if (!property.value.isMissingNode())
                        validate(property, field.getValue(), at + "." + field.getKey());
                    else
                        assertFalse(
                                schema.has("additionalProperties")
                                        && !schema.get("additionalProperties").asBoolean(),
                                at + " undeclared property " + field.getKey());
                }
            }
            case "string" -> {
                assertTrue(actual.isTextual(), at + " must be a string");
                if (schema.has("enum")) {
                    boolean allowed = false;
                    for (JsonNode value : schema.get("enum")) allowed |= value.equals(actual);
                    assertTrue(
                            allowed,
                            at
                                    + " value "
                                    + actual
                                    + " excluded by declared enum "
                                    + schema.get("enum"));
                }
                if (schema.path("format").asText().equals("uuid"))
                    assertEquals(
                            actual.asText(),
                            UUID.fromString(actual.asText()).toString(),
                            at + " must be a canonical UUID");
            }
            default -> fail(at + " unsupported error-schema type " + schema.path("type"));
        }
    }
}
