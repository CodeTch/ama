package ama.storage.s3;

import ama.contract.StorageContracts.*;

import java.io.*;
import java.security.*;
import java.util.*;

/**
 * Canonical, length-prefixed UTF-16 records: preserves unpaired code units, rejects trailing bytes.
 */
final class ControlCodec {
    static final int MAX = 1024 * 1024;

    static StorageException unavailable() {
        return new StorageException(ErrorCode.STORAGE_UNAVAILABLE);
    }

    static byte[] pack(String... values) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(0x414d4132);
            out.writeInt(values.length);
            for (String value : values) {
                if (value == null || value.length() > MAX / 2) throw unavailable();
                out.writeInt(value.length());
                for (int i = 0; i < value.length(); i++) out.writeChar(value.charAt(i));
            }
            if (bytes.size() > MAX) throw unavailable();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw unavailable();
        }
    }

    static String[] unpack(byte[] bytes, String schema, int fields) {
        try {
            if (bytes == null || bytes.length > MAX) throw unavailable();
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0x414d4132 || in.readInt() != fields) throw unavailable();
            String[] result = new String[fields];
            for (int f = 0; f < fields; f++) {
                int n = in.readInt();
                if (n < 0 || n > in.available() / 2) throw unavailable();
                char[] value = new char[n];
                for (int i = 0; i < n; i++) value[i] = in.readChar();
                result[f] = new String(value);
            }
            if (in.available() != 0 || !schema.equals(result[0])) throw unavailable();
            return result;
        } catch (IOException e) {
            throw unavailable();
        }
    }

    static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    static String hash(byte[] bytes) {
        return hex(digest().digest(bytes));
    }

    static String hashArgument(String h) {
        if (h == null || !h.matches("[0-9a-fA-F]{64}"))
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        return h.toLowerCase(Locale.ROOT);
    }

    static long number(String s) {
        try {
            long n = Long.parseLong(s);
            if (n < 0 || !s.equals(Long.toString(n))) throw unavailable();
            return n;
        } catch (NumberFormatException e) {
            throw unavailable();
        }
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    static void uuid(String s) {
        if (s == null || !s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw unavailable();
    }
}
