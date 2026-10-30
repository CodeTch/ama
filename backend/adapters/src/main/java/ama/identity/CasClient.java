package ama.identity;

import ama.contract.IdentityContracts.ProviderUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Minimal CAS 2.0 client. It never follows provider redirects or sends credentials. */
final class CasClient {
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final URI origin;
    private final URI service;
    private final URI logoutService;
    private final Duration timeout;
    private final HttpClient http;

    CasClient(URI origin, URI service, Duration timeout) {
        this(origin, service, service, timeout);
    }

    CasClient(URI origin, URI service, URI logoutService, Duration timeout) {
        this.origin = validOrigin(origin);
        this.service = validService(service);
        this.logoutService = validService(logoutService);
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Invalid CAS timeout");
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    CasClient(URI origin, URI service, URI logoutService, URI applicationOrigin, Duration timeout) {
        this.origin = validOrigin(origin);
        URI expected = validOrigin(applicationOrigin);
        this.service = validService(service, expected, "/auth/cas/callback");
        this.logoutService = validService(logoutService, expected, null);
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Invalid CAS timeout");
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    URI loginUri() {
        return URI.create(endpoint("/cas/login") + "?service=" + encode(service.toString()));
    }

    URI logoutUri() {
        return URI.create(endpoint("/cas/logout") + "?service=" + encode(logoutService.toString()));
    }

    CasXml.User validate(String ticket) {
        if (ticket == null || !ticket.matches("ST-[A-Za-z0-9_-]{8,512}"))
            throw new IllegalArgumentException("Invalid CAS ticket");
        URI uri = URI.create(endpoint("/cas/p3/serviceValidate") + "?service=" + encode(service.toString()) + "&ticket=" + encode(ticket));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/xml").GET().build();
        final HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException failure) {
            throw new ProviderUnavailableException();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException();
        }
        if (response.statusCode() / 100 != 2) {
            try { response.body().close(); } catch (IOException ignored) {}
            throw new ProviderUnavailableException();
        }
        byte[] body = readBounded(response.body(), MAX_RESPONSE_BYTES);
        try {
            return CasXml.parse(new String(body, StandardCharsets.UTF_8));
        } catch (CasXml.Malformed malformed) {
            throw new ProviderUnavailableException();
        }
    }

    static byte[] readBounded(InputStream body, int maximum) {
        if (body == null || maximum < 1) throw new ProviderUnavailableException();
        try (InputStream input = body; var output = new ByteArrayOutputStream(Math.min(maximum, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            while (true) {
                int count = input.read(buffer);
                if (count < 0) return output.toByteArray();
                if (count > maximum - total) throw new ProviderUnavailableException();
                output.write(buffer, 0, count);
                total += count;
            }
        } catch (IOException failure) {
            throw new ProviderUnavailableException();
        }
    }

    private String endpoint(String path) {
        return origin.toString().replaceFirst("/$", "") + path;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static URI validOrigin(URI value) {
        if (value == null || !("http".equals(value.getScheme()) || "https".equals(value.getScheme()))
                || value.getHost() == null || value.getRawUserInfo() != null || value.getRawQuery() != null
                || value.getRawFragment() != null || (!value.getRawPath().isEmpty() && !"/".equals(value.getRawPath())))
            throw new IllegalArgumentException("Invalid CAS origin");
        return value;
    }

    private static URI validService(URI value) {
        if (value == null || !("http".equals(value.getScheme()) || "https".equals(value.getScheme()))
                || value.getHost() == null || value.getRawUserInfo() != null || value.getRawQuery() != null
                || value.getRawFragment() != null || value.getRawPath().isEmpty())
            throw new IllegalArgumentException("Invalid CAS service URL");
        return value;
    }

    private static URI validService(URI value, URI expectedOrigin, String expectedPath) {
        URI service = validService(value);
        if (!sameOrigin(service, expectedOrigin) || (expectedPath != null && !expectedPath.equals(service.getRawPath())))
            throw new IllegalArgumentException("CAS service URL must belong to the application origin");
        return service;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && left.getPort() == right.getPort();
    }
}
