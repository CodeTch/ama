package ama.api;

import java.io.IOException;
import java.net.*;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.core.env.Environment;

/** Server-side CAS 2.0 client. The browser never receives or validates a ticket itself. */
final class CasClient {
    private final URI origin;
    private final URI service;
    private final URI logoutService;
    private final HttpClient http;
    CasClient(Environment env) {
        origin = root(env.getRequiredProperty("cas.origin"));
        service = callback(env.getRequiredProperty("cas.service-url"), "/auth/cas/callback");
        logoutService = callback(env.getProperty("cas.logout-service-url", service.toString()), "/auth/cas/logout/callback");
        // CAS credentials and tickets must go to the configured provider
        // directly. Inherited system proxies have been observed to strip the
        // form body from this short POST, turning a valid login into 400.
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).proxy(DIRECT).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    URI login() { return URI.create(origin + "/cas/login?service=" + encode(service.toString())); }
    URI login(String username, String password) {
        boundedCredential(username);
        boundedCredential(password);
        HttpURLConnection connection = null;
        try {
            String body = "service=" + encode(service.toString()) + "&username=" + encode(username) + "&password=" + encode(password);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(origin + "/cas/login").openConnection(Proxy.NO_PROXY);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setDoOutput(true);
            try (var output = connection.getOutputStream()) { output.write(bytes); }
            if (connection.getResponseCode() == 303) {
                String location = connection.getHeaderField("Location");
                String prefix = service + "?ticket=";
                if (!location.startsWith(prefix)) throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE");
                String ticket = location.substring(prefix.length());
                if (!ticket.matches("ST-[A-Za-z0-9_-]{16,256}")) throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE");
                return URI.create(location);
            }
            if (connection.getResponseCode() == 400 || connection.getResponseCode() == 401) throw new CasProtocol.Failure("INVALID_CREDENTIALS");
            throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE");
        } catch (CasProtocol.Failure failure) { throw failure; }
        catch (IOException | RuntimeException unavailable) { throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE"); }
        finally { if (connection != null) connection.disconnect(); }
    }
    URI logout() { return URI.create(origin + "/cas/logout?service=" + encode(logoutService.toString())); }
    CasProtocol.Assertion validate(String ticket) {
        if (ticket == null || !ticket.matches("ST-[A-Za-z0-9_-]{16,256}")) throw new CasProtocol.Failure("INVALID_TICKET");
        try {
            var request = HttpRequest.newBuilder(URI.create(origin + "/cas/p3/serviceValidate?service=" + encode(service.toString()) + "&ticket=" + encode(ticket)))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/xml").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE");
            try {
                return CasProtocol.parse(response.body());
            } catch (CasProtocol.Failure malformed) {
                // A malformed 200 response is a provider failure, not a bad
                // browser ticket. Do not turn provider corruption into a
                // client retry loop or expose parser details.
                if ("INVALID_RESPONSE".equals(malformed.code())) throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE");
                throw malformed;
            }
        } catch (CasProtocol.Failure failure) { throw failure; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE"); }
        catch (IOException | RuntimeException unavailable) { throw new CasProtocol.Failure("PROVIDER_UNAVAILABLE"); }
    }
    URI service() { return service; }
    private static void boundedCredential(String value) {
        if (value == null || value.isBlank() || value.length() > 1024 || value.codePoints().anyMatch(Character::isISOControl))
            throw new CasProtocol.Failure("INVALID_CREDENTIALS");
    }
    private static URI root(String value) {
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !SetOfEmpty.orRoot(uri.getRawPath())) throw new IllegalArgumentException("Invalid CAS origin");
        return URI.create(uri.getScheme() + "://" + uri.getRawAuthority());
    }
    private static URI callback(String value, String expectedPath) {
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !expectedPath.equals(uri.getRawPath())) throw new IllegalArgumentException("Invalid CAS callback");
        return uri;
    }
    private static String encode(String value) { return URLEncoder.encode(Objects.requireNonNull(value), StandardCharsets.UTF_8); }
    private static final ProxySelector DIRECT = new ProxySelector() {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
    };
    private static final class SetOfEmpty { static boolean orRoot(String path) { return path == null || path.isEmpty() || "/".equals(path); } }
}
