package ama.storage;

import ama.contract.StorageContracts.*;
import ama.storage.internal.LocalFileWire.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import javax.net.ssl.SSLContext;

/** Streaming authenticated client for the separate local file process. */
public final class LocalFileClient implements FileStoragePort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI base;
    private final HttpClient http;

    public LocalFileClient(URI base, SSLContext tls) {
        if (base == null
                || !"https".equals(base.getScheme())
                || base.getHost() == null
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null
                || !Set.of("", "/").contains(base.getPath()))
            throw new IllegalArgumentException("HTTPS service origin required");
        this.base = base;
        this.http =
                HttpClient.newBuilder()
                        .sslContext(Objects.requireNonNull(tls))
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    private static Request request(
            StorageKey key,
            UploadToken token,
            Long size,
            String hash,
            Integer part,
            List<PartReceipt> parts,
            UploadSpec spec) {
        return new Request(
                key, spec, token, part, size, hash, parts, null, null, null, null, null, null,
                null);
    }

    private HttpResponse<InputStream> send(String operation, Request value, InputStream body) {
        try {
            String metadata =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(JSON.writeValueAsBytes(value));
            var request =
                    HttpRequest.newBuilder(base.resolve("/internal/v1/" + operation))
                            .timeout(Duration.ofMinutes(5))
                            .header("X-Ama-Request", metadata);
            if (operation.equals("read") && value.length() != null && value.length() > 0)
                request.header(
                        "Range",
                        "bytes=" + value.offset() + "-" + (value.offset() + value.length() - 1));
            request.POST(
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofInputStream(() -> body));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (var in = response.body()) {
                    byte[] bytes = in.readNBytes(4097);
                    if (bytes.length > 4096) throw unavailable();
                    Failure error = JSON.readValue(bytes, Failure.class);
                    ErrorCode code;
                    try {
                        code = ErrorCode.valueOf(error.code());
                    } catch (Exception e) {
                        throw unavailable();
                    }
                    throw new StorageException(code);
                }
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | IllegalArgumentException e) {
            throw unavailable();
        }
    }

    private <T> T json(String operation, Request value, InputStream body, Class<T> type) {
        try (var in = send(operation, value, body).body()) {
            byte[] bytes = in.readNBytes(1048577);
            if (bytes.length > 1048576) throw unavailable();
            return JSON.readValue(bytes, type);
        } catch (IOException e) {
            throw unavailable();
        }
    }

    private void empty(String operation, Request value) {
        try (var in = send(operation, value, null).body()) {
            if (in.read() != -1) throw unavailable();
        } catch (IOException e) {
            throw unavailable();
        }
    }

    @Override
    public StoredBlob put(StorageKey key, InputStream body, long size, String hash) {
        return json(
                "put", request(key, null, size, hash, null, null, null), body, StoredBlob.class);
    }

    @Override
    public BlobStat stat(StorageKey key) {
        return json("stat", request(key, null, null, null, null, null, null), null, BlobStat.class);
    }

    @Override
    public InputStream read(StorageKey key, long offset, long length) {
        if (offset < 0 || length < 0 || length > Long.MAX_VALUE - offset)
            throw new StorageException(ErrorCode.INVALID_RANGE);
        return send(
                        "read",
                        new Request(
                                key, null, null, null, null, null, null, null, null, null, null,
                                null, offset, length),
                        null)
                .body();
    }

    @Override
    public void delete(StorageKey key) {
        empty("delete", request(key, null, null, null, null, null, null));
    }

    @Override
    public UploadToken beginUpload(UploadSpec spec) {
        return json(
                "begin",
                request(null, null, null, null, null, null, spec),
                null,
                UploadToken.class);
    }

    @Override
    public PartReceipt writePart(
            UploadToken token, int part, InputStream body, long size, String hash) {
        return json(
                "part",
                request(null, token, size, hash, part, null, null),
                body,
                PartReceipt.class);
    }

    @Override
    public StoredBlob complete(UploadToken token, List<PartReceipt> parts, String hash) {
        return json(
                "complete",
                request(null, token, null, hash, null, parts, null),
                null,
                StoredBlob.class);
    }

    @Override
    public void abort(UploadToken token) {
        empty("abort", request(null, token, null, null, null, null, null));
    }

    @Override
    public BlobPage list(
            String backend, String namespace, String prefix, String cursor, int limit) {
        return json(
                "list",
                new Request(
                        null, null, null, null, null, null, null, backend, namespace, prefix,
                        cursor, limit, null, null),
                null,
                BlobPage.class);
    }

    private static StorageException unavailable() {
        return new StorageException(ErrorCode.STORAGE_UNAVAILABLE);
    }
}
