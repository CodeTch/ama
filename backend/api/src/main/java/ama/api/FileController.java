package ama.api;

import ama.contract.FileContracts.*;
import ama.contract.IdentityContracts.*;
import ama.storage.FileRegistry;
import ama.storage.FileUploads;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.*;

@RestController
class FileController {
    private final FileUploads uploads;
    private final FileRegistry registry;
    private final long budget;
    private final ObjectMapper json =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxStringLength(Integer.MAX_VALUE)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    FileController(FileUploads uploads, FileRegistry registry, Environment env) {
        this.uploads = uploads;
        this.registry = registry;
        budget = env.getProperty("files.http.max-request-bytes", Long.class, 8L * 1024 * 1024);
        if (budget < 1) throw new IllegalArgumentException("Invalid file request budget");
    }

    @PostMapping("/api/v1/meetings/{id}/uploads")
    ResponseEntity<Accepted> begin(@PathVariable String id, HttpServletRequest request)
            throws IOException {
        var body = parse(request, Set.of("purpose", "name", "size", "sha256"));
        return ResponseEntity.status(201)
                .body(
                        uploads.begin(
                                actor(request),
                                id,
                                header(request, "Idempotency-Key"),
                                new Begin(
                                        text(body, "purpose", true),
                                        text(body, "name", true),
                                        number(body, "size"),
                                        text(body, "sha256", false)),
                                trace()));
    }

    @PutMapping("/api/v1/uploads/{id}/parts/{part}")
    ResponseEntity<Void> part(
            @PathVariable String id, @PathVariable int part, HttpServletRequest request)
            throws IOException {
        long size = request.getContentLengthLong();
        if (size < 0) throw new IllegalArgumentException("Content-Length required");
        if (size > budget) throw new RequestTooLarge();
        try (var stream = request.getInputStream()) {
            uploads.part(
                    actor(request),
                    id,
                    part,
                    stream,
                    size,
                    header(request, "X-Checksum-SHA256"),
                    trace());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/uploads/{id}/completion")
    Completed complete(@PathVariable String id, HttpServletRequest request) throws IOException {
        var body = parse(request, Set.of("parts", "sha256"));
        var parts = body.get("parts");
        if (parts == null || !parts.isArray()) throw new IllegalArgumentException("Parts required");
        var values = new ArrayList<ClientPart>();
        for (var part : parts) {
            fields(part, Set.of("part", "sha256"));
            values.add(
                    new ClientPart(
                            Math.toIntExact(number(part, "part")), text(part, "sha256", true)));
        }
        return uploads.complete(
                actor(request), id, new Completion(values, text(body, "sha256", true)), trace());
    }

    @DeleteMapping("/api/v1/uploads/{id}")
    ResponseEntity<Void> abort(@PathVariable String id, HttpServletRequest request) {
        uploads.abort(actor(request), id, trace());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/files/{id}/content")
    void content(@PathVariable String id, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        var ranges = Collections.list(request.getHeaders("Range"));
        if (ranges.size() > 1) throw new IllegalArgumentException("One range header required");
        String range = ranges.isEmpty() ? null : ranges.getFirst();
        try (var download = registry.download(actor(request), id, range)) {
            response.setStatus(range == null ? 200 : 206);
            response.setContentType("application/octet-stream");
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLengthLong(download.length());
            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=\"download\"; filename*=UTF-8''"
                            + encoded(download.file().displayName()));
            if (range != null)
                response.setHeader(
                        "Content-Range",
                        "bytes "
                                + download.offset()
                                + "-"
                                + (download.offset() + download.length() - 1)
                                + "/"
                                + download.file().size());
            download.body().transferTo(response.getOutputStream());
        }
    }

    private static String encoded(String value) {
        StringBuilder result = new StringBuilder();
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 255;
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '.'
                    || c == '_') result.append((char) c);
            else
                result.append('%')
                        .append("0123456789ABCDEF".charAt(c >> 4))
                        .append("0123456789ABCDEF".charAt(c & 15));
        }
        return result.toString();
    }

    private JsonNode parse(HttpServletRequest request, Set<String> allowed) throws IOException {
        if (request.getContentLengthLong() > budget) throw new RequestTooLarge();
        var bytes = new ByteArrayOutputStream();
        try (var stream = request.getInputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int n; (n = stream.read(buffer)) != -1; ) {
                total += n;
                if (total > budget) throw new RequestTooLarge();
                bytes.write(buffer, 0, n);
            }
        }
        try {
            var node = json.readTree(bytes.toByteArray());
            fields(node, allowed);
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON");
        }
    }

    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Object required");
        node.fieldNames()
                .forEachRemaining(
                        name -> {
                            if (!allowed.contains(name))
                                throw new IllegalArgumentException("Unknown field");
                        });
    }

    private static String text(JsonNode node, String name, boolean required) {
        var value = node.get(name);
        if (value == null || value.isNull()) {
            if (required) throw new IllegalArgumentException("Missing text");
            return null;
        }
        if (!value.isTextual()) throw new IllegalArgumentException("Invalid text");
        return value.textValue();
    }

    private static long number(JsonNode node, String name) {
        var value = node.get(name);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.longValue() < 0) throw new IllegalArgumentException("Invalid number");
        return value.longValue();
    }

    private static String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() != 1 || values.getFirst().isBlank())
            throw new IllegalArgumentException("Exactly one header required");
        return values.getFirst();
    }

    private static Actor actor(HttpServletRequest request) {
        return ((ResolvedSession) request.getAttribute(SessionSecurityFilter.SESSION)).actor();
    }

    private static String trace() {
        return UUID.randomUUID().toString();
    }

    static final class RequestTooLarge extends RuntimeException {}
}
