package ama.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractClientTest {
    @Test void preservesStatusAndJsonBodyAcrossRealHttp() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (!"POST".equals(exchange.getRequestMethod()) || !"test-only".equals(exchange.getRequestHeaders().getFirst("X-Request-Token"))) {
                exchange.sendResponseHeaders(400, -1);
            } else {
                exchange.getResponseHeaders().set("X-Fixture", "echo");
                exchange.sendResponseHeaders(202, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            var client = new ContractClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            ApiReply reply = client.request("POST", "/echo", new ObjectMapper().readTree("{\"value\":42}"), Map.of("X-Request-Token", "test-only"));
            assertEquals(202, reply.status());
            assertEquals(42, reply.body().path("value").asInt());
        } finally { server.stop(0); }
    }
    @Test void rejectsCrossOriginRequestsBeforeSendingHeaders() {
        var client = new ContractClient(URI.create("http://127.0.0.1:1"));
        assertThrows(IllegalArgumentException.class, () -> client.request("GET", "http://elsewhere.invalid/", null, Map.of("Authorization", "test-secret")));
    }
}
