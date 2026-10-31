package ama.api;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class CasClientTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void exchangesV1TgtForServiceTicketAndReturnsCallback() throws Exception {
        AtomicReference<String> firstBody = new AtomicReference<>();
        AtomicReference<String> secondBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (exchange.getRequestURI().getPath().equals("/v1/tickets/")) {
                firstBody.set(new String(body, StandardCharsets.UTF_8));
                byte[] response = ("<form action=\"http://127.0.0.1:" + server.getAddress().getPort() + "/v1/tickets/TGT-demo1234\" method=\"POST\"></form>").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(201, response.length); exchange.getResponseBody().write(response);
            } else {
                secondBody.set(new String(body, StandardCharsets.UTF_8));
                byte[] response = "ST-demo-ticket12345".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var env = new MockEnvironment().withProperty("cas.origin", origin)
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var callback = new CasClient(env).login("alice", "p@ss");

        assertEquals("http://127.0.0.1:5173/auth/cas/callback?ticket=ST-demo-ticket12345", callback.toString());
        assertTrue(firstBody.get().contains("username=alice"));
        assertTrue(firstBody.get().contains("password=p%40ss"));
        assertTrue(secondBody.get().contains("service=http%3A%2F%2F127.0.0.1%3A5173%2Fauth%2Fcas%2Fcallback"));
    }

    @Test void rejectsMalformedTgtResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            byte[] response = "<html>bad</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var env = new MockEnvironment().withProperty("cas.origin", origin)
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");
        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "secret"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
    }

    @Test void mapsV1CredentialRejectionToInvalidCredentials() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();
        var env = new MockEnvironment().withProperty("cas.origin", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "wrong"));
        assertEquals("INVALID_CREDENTIALS", failure.code());
    }

    @Test void mapsV1ProviderFailureToProviderUnavailable() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        var env = new MockEnvironment().withProperty("cas.origin", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "secret"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
    }

    @Test void rejectsRedirectFromV1TicketExchange() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://attacker.invalid/tgt");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        var env = new MockEnvironment().withProperty("cas.origin", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "secret"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
    }

    @Test void fallsBackToCas20WhenProviderDoesNotExposeV1Tickets() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> { exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.createContext("/cas/login", exchange -> {
            byte[] response = new byte[0];
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:5173/auth/cas/callback?ticket=ST-fallback12345678");
            exchange.sendResponseHeaders(303, response.length); exchange.close();
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var env = new MockEnvironment().withProperty("cas.origin", origin)
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");
        assertEquals("http://127.0.0.1:5173/auth/cas/callback?ticket=ST-fallback12345678", new CasClient(env).login("1", "1").toString());
    }

    @Test void rejectsCas20LocationWithDuplicateTickets() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/cas/login", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:5173/auth/cas/callback?ticket=ST-first-ticket-12345678&ticket=ST-second-ticket-12345678");
            exchange.sendResponseHeaders(303, -1);
            exchange.close();
        });
        server.start();
        var env = new MockEnvironment().withProperty("cas.origin", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "secret"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
    }

    @Test void rejectsTgtActionThatDoesNotNameTheExactTicketResource() throws Exception {
        AtomicReference<Boolean> exchanged = new AtomicReference<>(false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/v1/tickets/")) exchanged.set(true);
            byte[] response = ("<form action=\"http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/tickets/TGT-valid1234?next=unsafe\" method=\"POST\"></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var env = new MockEnvironment().withProperty("cas.origin", origin)
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "secret"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
        assertFalse(exchanged.get());
    }

    @Test void rejectsTgtActionWithAnExtraPathSegment() throws Exception {
        AtomicReference<Boolean> exchanged = new AtomicReference<>(false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/v1/tickets/")) exchanged.set(true);
            byte[] response = ("<form action=\"http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/tickets//TGT-valid1234\" method=\"POST\"></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var env = new MockEnvironment().withProperty("cas.origin", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "secret"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
        assertFalse(exchanged.get());
    }

    @Test void rejectsServiceTicketResponseContainingControlCharacters() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tickets/", exchange -> {
            byte[] response;
            if (exchange.getRequestURI().getPath().equals("/v1/tickets/")) {
                response = ("<form action=\"http://127.0.0.1:" + server.getAddress().getPort()
                        + "/v1/tickets/TGT-valid1234\" method=\"POST\"></form>").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(201, response.length);
            } else {
                response = "ST-valid-service-ticket-1234\n".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
            }
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var env = new MockEnvironment().withProperty("cas.origin", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("cas.service-url", "http://127.0.0.1:5173/auth/cas/callback")
                .withProperty("cas.logout-service-url", "http://127.0.0.1:5173/auth/cas/logout/callback");

        var failure = assertThrows(CasProtocol.Failure.class, () -> new CasClient(env).login("alice", "secret"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
    }

    @Test void rejectsCasServiceOutsideTheConfiguredApplicationOrigin() {
        var env = new MockEnvironment().withProperty("cas.origin", "http://127.0.0.1:8787")
                .withProperty("cas.service-url", "https://attacker.invalid/auth/cas/callback")
                .withProperty("cas.logout-service-url", "https://attacker.invalid/auth/cas/logout/callback")
                .withProperty("identity.allowed-origin", "http://127.0.0.1:5173");

        assertThrows(IllegalArgumentException.class, () -> new CasClient(env));
    }
}
