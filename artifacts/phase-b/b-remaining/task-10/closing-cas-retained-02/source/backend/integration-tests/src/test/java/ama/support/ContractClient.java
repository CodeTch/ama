package ama.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Test-only HTTP client; redirects are deliberately never followed across origins. */
public final class ContractClient {
    private final URI base;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

    public ContractClient(URI base) { this.base = base; }

    public ApiReply request(String method, String path, JsonNode body, Map<String, String> headers) throws Exception {
        URI target = base.resolve(path);
        if (!target.getScheme().equals(base.getScheme()) || !java.util.Objects.equals(target.getAuthority(), base.getAuthority())) {
            throw new IllegalArgumentException("Contract request must stay on the configured origin");
        }
        var builder = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(10));
        headers.forEach(builder::header);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
        if (body != null && headers.keySet().stream().noneMatch(key -> key.equalsIgnoreCase("Content-Type"))) builder.header("Content-Type", "application/json");
        var response = client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body().isBlank() ? mapper.nullNode() : mapper.readTree(response.body());
        return new ApiReply(response.statusCode(), json);
    }
}
