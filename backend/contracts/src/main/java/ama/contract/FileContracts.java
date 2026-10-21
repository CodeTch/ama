package ama.contract;

import java.util.*;

/** Browser values contain no storage locator or provider receipt. */
public final class FileContracts {
    private FileContracts() {}

    public record Begin(String purpose, String name, long size, String sha256) {
        public Begin {
            code(purpose);
            text(name);
            if (size < 0) throw new IllegalArgumentException("Negative size");
            if (sha256 != null) sha256 = hash(sha256);
        }
    }

    public record ClientPart(int part, String sha256) {
        public ClientPart {
            if (part < 1) throw new IllegalArgumentException("Invalid part");
            sha256 = hash(sha256);
        }
    }

    public record Completion(List<ClientPart> parts, String sha256) {
        public Completion {
            parts = List.copyOf(parts);
            sha256 = hash(sha256);
            if (parts.isEmpty()) throw new Conflict();
        }
    }

    public record Accepted(String uploadId, long partSize) {}

    public record Completed(String fileId) {}

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super("File request conflicts");
        }
    }

    public static final class NotFound extends RuntimeException {
        public NotFound() {
            super("File not found");
        }
    }

    public static final class Unavailable extends RuntimeException {
        public Unavailable() {
            super("File storage unavailable");
        }
    }

    public static String hash(String hash) {
        if (hash == null || !hash.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Invalid checksum");
        return hash.toLowerCase(Locale.ROOT);
    }

    public static void code(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid protocol code");
    }

    public static void text(String value) {
        Objects.requireNonNull(value);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new IllegalArgumentException("Invalid Unicode");
            } else if (Character.isLowSurrogate(c))
                throw new IllegalArgumentException("Invalid Unicode");
        }
    }
}
