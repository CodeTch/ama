package ama.identity;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CasClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void validatesAgainstP3EndpointWithExactServiceAndTicketEncoding() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cas/p3/serviceValidate", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\"><cas:authenticationSuccess><cas:user>demo-owner</cas:user></cas:authenticationSuccess></cas:serviceResponse>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        URI service = URI.create("http://127.0.0.1:5173/auth/cas/callback");

        CasXml.User user = new CasClient(origin, service, Duration.ofSeconds(2)).validate("ST-a_b123456");

        assertEquals("demo-owner", user.username());
        assertEquals("service=http%3A%2F%2F127.0.0.1%3A5173%2Fauth%2Fcas%2Fcallback&ticket=ST-a_b123456", query.get());
    }

    @Test
    void providerHttpFailureIsUnavailableAndRedirectsAreNotFollowed() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cas/p3/serviceValidate", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://attacker.invalid");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        var error = assertThrows(ama.contract.IdentityContracts.ProviderUnavailableException.class,
                () -> new CasClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                        URI.create("http://127.0.0.1:5173/auth/cas/callback"), Duration.ofSeconds(2)).validate("ST-12345678"));

        assertEquals("Enterprise identity provider is unavailable", error.getMessage());
    }

    @Test
    void boundedResponseReaderStopsBeforeAnUntrustedProviderCanExhaustMemory() {
        final int[] read = {0};
        InputStream endless = new InputStream() {
            @Override public int read() {
                read[0]++;
                return 1;
            }
        };

        assertThrows(ama.contract.IdentityContracts.ProviderUnavailableException.class,
                () -> CasClient.readBounded(endless, 256 * 1024));
        assertTrue(read[0] <= 256 * 1024 + 8192);
    }

    @Test
    void productionConstructorBindsCasServicesToTheConfiguredApplicationOrigin() {
        URI cas = URI.create("http://127.0.0.1:8787");
        URI app = URI.create("http://127.0.0.1:5173");

        assertThrows(IllegalArgumentException.class, () -> new CasClient(cas,
                URI.create("https://attacker.invalid/auth/cas/callback"),
                URI.create("http://127.0.0.1:5173/meetings"), app, Duration.ofSeconds(2)));
    }

    @Test
    void malformedSuccessfulProviderResponseIsUnavailableRatherThanUserInputError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cas/p3/serviceValidate", exchange -> {
            byte[] body = "<not-cas/>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();

        assertThrows(ama.contract.IdentityContracts.ProviderUnavailableException.class,
                () -> new CasClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                        URI.create("http://127.0.0.1:5173/auth/cas/callback"), Duration.ofSeconds(2)).validate("ST-12345678"));
    }
}
