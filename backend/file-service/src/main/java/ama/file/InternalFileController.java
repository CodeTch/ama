package ama.file;

import ama.contract.StorageContracts.*;
import ama.storage.internal.LocalFileWire.*;
import ama.storage.internal.LocalFileWire.Request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;

import javax.net.ssl.*;

/** Private mTLS service; deliberately registers no browser/business routes. */
public final class InternalFileController implements AutoCloseable {
    public enum Role {
        READ,
        WRITE
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final LocalFileStore store;
    private final Map<String, Role> roles;
    private final HttpsServer server;
    private final ExecutorService executor;

    public InternalFileController(
            LocalFileStore store,
            InetSocketAddress address,
            SSLContext tls,
            Map<String, Role> roles)
            throws IOException {
        if (roles == null
                || roles.isEmpty()
                || roles.entrySet().stream()
                        .anyMatch(
                                e ->
                                        e.getKey() == null
                                                || !e.getKey().startsWith("spiffe://")
                                                || e.getValue() == null))
            throw new IllegalArgumentException("exact service URI identities and roles required");
        this.store = Objects.requireNonNull(store);
        this.roles = Map.copyOf(roles);
        Objects.requireNonNull(tls);
        store.check();
        server = HttpsServer.create(address, 64);
        server.setHttpsConfigurator(
                new HttpsConfigurator(tls) {
                    @Override
                    public void configure(HttpsParameters p) {
                        SSLParameters parameters = tls.getDefaultSSLParameters();
                        parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
                        parameters.setNeedClientAuth(true);
                        p.setSSLParameters(parameters);
                    }
                });
        executor =
                new ThreadPoolExecutor(
                        4,
                        16,
                        30,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(128),
                        new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/internal/v1/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private Role authenticate(HttpsExchange exchange) {
        try {
            var cert = (X509Certificate) exchange.getSSLSession().getPeerCertificates()[0];
            cert.checkValidity();
            var names = cert.getSubjectAlternativeNames();
            if (names == null) return null;
            Role allowed = null;
            for (var name : names)
                if (name.size() == 2 && Integer.valueOf(6).equals(name.get(0))) {
                    Role role = roles.get(String.valueOf(name.get(1)));
                    if (role == Role.WRITE) return role;
                    if (role == Role.READ) allowed = role;
                }
            return allowed;
        } catch (Exception e) {
            return null;
        }
    }

    private void handle(HttpExchange raw) throws IOException {
        try (var exchange = (HttpsExchange) raw) {
            try {
                Role role = authenticate(exchange);
                if (role == null) {
                    error(exchange, 403, "FORBIDDEN");
                    return;
                }
                if (!exchange.getRequestMethod().equals("POST")
                        || exchange.getRequestURI().getRawQuery() != null) {
                    error(exchange, 400, ErrorCode.INVALID_ARGUMENT.name());
                    return;
                }
                String route =
                        exchange.getRequestURI().getRawPath().substring("/internal/v1/".length());
                if (!Set.of(
                                "put",
                                "stat",
                                "read",
                                "delete",
                                "begin",
                                "part",
                                "complete",
                                "abort",
                                "discard-upload",
                                "list",
                                "health")
                        .contains(route)) {
                    error(exchange, 404, ErrorCode.NOT_FOUND.name());
                    return;
                }
                if (role != Role.WRITE
                        && !Set.of("stat", "read", "list", "health").contains(route)) {
                    error(exchange, 403, "FORBIDDEN");
                    return;
                }
                store.check();
                if (route.equals("health")) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                var headers = exchange.getRequestHeaders().get("X-Ama-Request");
                if (headers == null || headers.size() != 1 || headers.getFirst().length() > 131072)
                    throw new StorageException(ErrorCode.INVALID_ARGUMENT);
                Request request;
                try {
                    request =
                            JSON.readValue(
                                    Base64.getUrlDecoder().decode(headers.getFirst()),
                                    Request.class);
                } catch (Exception e) {
                    throw new StorageException(ErrorCode.INVALID_ARGUMENT);
                }
                Object result =
                        switch (route) {
                            case "put" ->
                                    store.put(
                                            request.key(),
                                            exchange.getRequestBody(),
                                            request.size(),
                                            request.sha256());
                            case "stat" -> store.stat(request.key());
                            case "delete" -> {
                                store.delete(request.key());
                                yield null;
                            }
                            case "begin" -> store.beginUpload(request.spec());
                            case "part" ->
                                    store.writePart(
                                            request.token(),
                                            request.part(),
                                            exchange.getRequestBody(),
                                            request.size(),
                                            request.sha256());
                            case "complete" ->
                                    store.complete(
                                            request.token(), request.parts(), request.sha256());
                            case "abort" -> {
                                store.abort(request.token());
                                yield null;
                            }
                            case "discard-upload" -> { store.discardUpload(request.token(),request.key()); yield null; }
                            case "list" ->
                                    store.list(
                                            request.backendId(),
                                            request.namespace(),
                                            request.prefix(),
                                            request.cursor(),
                                            request.limit());
                            case "read" -> {
                                read(exchange, request);
                                yield null;
                            }
                            default -> throw new StorageException(ErrorCode.INVALID_ARGUMENT);
                        };
                if (route.equals("read")) return;
                store.check();
                if (result == null) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                byte[] bytes = JSON.writeValueAsBytes(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (StorageException e) {
                if (exchange.getResponseCode() == -1)
                    error(exchange, status(e.code()), e.code().name());
            } catch (NullPointerException | IllegalArgumentException e) {
                if (exchange.getResponseCode() == -1)
                    error(exchange, 400, ErrorCode.INVALID_ARGUMENT.name());
            } catch (Exception e) {
                if (exchange.getResponseCode() == -1)
                    error(exchange, 503, ErrorCode.STORAGE_UNAVAILABLE.name());
            }
        }
    }

    private void read(HttpsExchange exchange, Request request) throws IOException {
        try (var opened = store.openRead(request.key())) {
            long size = opened.size();
            long offset = request.offset() == null ? 0 : request.offset();
            long length = request.length() == null ? size : request.length();
            int status = 200;
            List<String> ranges = exchange.getRequestHeaders().get("Range");
            if (ranges != null) {
                long[] range;
                try {
                    if (ranges.size() != 1) throw new StorageException(ErrorCode.INVALID_RANGE);
                    range = range(ranges.getFirst(), size);
                } catch (StorageException e) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
                    throw e;
                }
                offset = range[0];
                length = range[1];
                status = 206;
                exchange.getResponseHeaders()
                        .set(
                                "Content-Range",
                                "bytes " + offset + "-" + (offset + length - 1) + "/" + size);
            }
            try (InputStream in = opened.range(offset, length)) {
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Length", Long.toString(length));
                exchange.sendResponseHeaders(status, length == 0 ? -1 : length);
                in.transferTo(exchange.getResponseBody());
            } catch (StorageException e) {
                if (e.code() == ErrorCode.INVALID_RANGE)
                    exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
                throw e;
            }
        }
    }

    private static long[] range(String value, long size) {
        try {
            if (size == 0 || value == null || !value.matches("bytes=(?:[0-9]+-[0-9]*|-[0-9]+)"))
                throw new IllegalArgumentException();
            String[] pair = value.substring(6).split("-", -1);
            long start, end;
            if (pair[0].isEmpty()) {
                long suffix = Long.parseLong(pair[1]);
                if (suffix <= 0) throw new IllegalArgumentException();
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(pair[0]);
                end = pair[1].isEmpty() ? size - 1 : Long.parseLong(pair[1]);
                if (start >= size || end < start || end >= size)
                    throw new IllegalArgumentException();
            }
            return new long[] {start, end - start + 1};
        } catch (IllegalArgumentException e) {
            throw new StorageException(ErrorCode.INVALID_RANGE);
        }
    }

    private static int status(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> 404;
            case CONFLICT, INVALID_UPLOAD_STATE -> 409;
            case INVALID_RANGE -> 416;
            case STORAGE_UNAVAILABLE -> 503;
            default -> 400;
        };
    }

    private static void error(HttpExchange exchange, int status, String code) throws IOException {
        byte[] body = JSON.writeValueAsBytes(new Failure(code));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
