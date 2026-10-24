package ama.storage.s3;

import ama.contract.StorageContracts.*;

import java.util.*;

/** Exact UTF-16 identity and order-preserving, bounded-depth physical layout v3. */
final class S3Layout {
    static final int PAYLOAD = 199;
    final S3Config config;

    S3Layout(S3Config config) {
        this.config = config;
    }

    static String encode(String s) {
        var out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') out.append(".0");
            else if (c == '/') out.append(".1");
            else if (c < '~') out.append(c);
            else out.append('~').append(String.format(Locale.ROOT, "%04x", (int) c));
        }
        return out.toString();
    }

    static String decode(String s) {
        var out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (++i >= s.length() || (s.charAt(i) != '0' && s.charAt(i) != '1'))
                    throw ControlCodec.unavailable();
                out.append(s.charAt(i) == '0' ? '.' : '/');
            } else if (c == '~') {
                if (i + 4 >= s.length()) throw ControlCodec.unavailable();
                String h = s.substring(i + 1, i + 5);
                if (!h.matches("[0-9a-f]{4}")) throw ControlCodec.unavailable();
                int n = Integer.parseInt(h, 16);
                if (n < '~') throw ControlCodec.unavailable();
                out.append((char) n);
                i += 4;
            } else {
                if (c >= '~'
                        || c == '/'
                        || c == '\\'
                        || c == '%'
                        || c == ':'
                        || Character.isISOControl(c)) throw ControlCodec.unavailable();
                out.append(c);
            }
        }
        String result = out.toString();
        if (!encode(result).equals(s)) throw ControlCodec.unavailable();
        return result;
    }

    static String segment(String encoded) {
        var out = new StringBuilder();
        for (int i = 0; i < encoded.length(); i += PAYLOAD) {
            if (i > 0) out.append('/');
            out.append('x').append(encoded, i, Math.min(encoded.length(), i + PAYLOAD));
        }
        return out.toString();
    }

    static String unsegment(String physical) {
        String[] parts = physical.split("/", -1);
        var encoded = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() < 2
                    || p.length() > 200
                    || p.charAt(0) != 'x'
                    || (i + 1 < parts.length && p.length() != 200))
                throw ControlCodec.unavailable();
            encoded.append(p, 1, p.length());
        }
        return decode(encoded.toString());
    }

    static String physical(String key, int budget) {
        if (key == null || key.length() > budget)
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        String[] segments = key.split("/", -1);
        if (segments.length > 8) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        for (String s : segments)
            if (s.isEmpty()
                    || s.length() > 200
                    || s.equals(".")
                    || s.equals("..")
                    || s.chars().anyMatch(c -> c > 127 || c < 32 || c == 127))
                throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        return key;
    }

    String scopeHash(String namespace) {
        return ControlCodec.hash(
                ControlCodec.pack(
                        "scope-identity-v3", config.backendId(), namespace, config.identity()));
    }

    String scope(String namespace) {
        return "data/v3/" + scopeHash(namespace) + "/";
    }

    String scopeKey(String namespace) {
        return "scopes/v3/" + scopeHash(namespace) + "/spec";
    }

    byte[] scopeBinding(String namespace) {
        return ControlCodec.pack(
                "scope-v3",
                config.backendId(),
                namespace,
                Base64.getEncoder().encodeToString(config.physicalBinding()),
                "199",
                "200",
                "8");
    }

    String prefix(String namespace, String prefix) {
        return scope(namespace) + segment(encode(prefix));
    }

    String data(StorageKey key) {
        if (key == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        backend(key.backendId());
        return physical(prefix(key.namespace(), key.objectKey()), config.keyBudget());
    }

    StorageKey fromData(String namespace, String path) {
        String base = scope(namespace);
        if (!path.startsWith(base)) throw ControlCodec.unavailable();
        try {
            StorageKey key =
                    new StorageKey(
                            config.backendId(),
                            namespace,
                            unsegment(path.substring(base.length())));
            if (!data(key).equals(path)) throw ControlCodec.unavailable();
            return key;
        } catch (StorageException e) {
            throw ControlCodec.unavailable();
        }
    }

    void backend(String id) {
        if (!config.backendId().equals(id)) throw ControlCodec.unavailable();
    }

    byte[] binding(StorageKey k) {
        data(k);
        return ControlCodec.pack(
                "key-v3",
                Base64.getEncoder().encodeToString(config.physicalBinding()),
                k.backendId(),
                k.namespace(),
                k.objectKey());
    }

    String bindingKey(StorageKey k) {
        return "keys/v3/"
                + ControlCodec.hash(
                        ControlCodec.pack(
                                "key-identity-v3", k.backendId(), k.namespace(), k.objectKey()))
                + "/spec";
    }
}
