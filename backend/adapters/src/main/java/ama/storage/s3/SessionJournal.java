package ama.storage.s3;

import ama.contract.StorageContracts.*;

import java.io.*;
import java.util.*;
import java.util.function.IntFunction;

/** Immutable specs, accepted slots, selected manifests and one terminal decision. */
final class SessionJournal {
    record Session(String id, String specHash, UploadSpec spec) {
        String prefix() {
            return "sessions/v1/" + id + "/";
        }

        StorageKey key() {
            return new StorageKey(spec.backendId(), spec.namespace(), spec.objectKey());
        }
    }

    record Completion(String fingerprint, int count, String hash) {}

    final S3Objects io;
    final S3Layout layout;
    final Staging staging;
    final NativePublisher publisher;

    SessionJournal(S3Objects io, S3Layout layout, Staging staging, NativePublisher publisher) {
        this.io = io;
        this.layout = layout;
        this.staging = staging;
        this.publisher = publisher;
    }

    UploadToken begin(UploadSpec spec) {
        if (spec == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        StorageKey key = new StorageKey(spec.backendId(), spec.namespace(), spec.objectKey());
        layout.data(key);RetiredUploads.live(io,layout,key);
        publisher.interval(spec.expectedSize());
        byte[] encoded =
                ControlCodec.pack(
                        "session-v1",
                        io.config.identity(),
                        spec.backendId(),
                        spec.namespace(),
                        spec.objectKey(),
                        Long.toString(spec.expectedSize()));
        String id = ControlCodec.id();
        io.exact("sessions/v1/" + id + "/spec", encoded);
        return new UploadToken(spec.backendId(), id + "." + ControlCodec.hash(encoded));
    }

    Session session(UploadToken token) {
        if (token == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        layout.backend(token.backendId());
        String[] id = token.sessionId().split("\\.", -1);
        if (id.length != 2 || !id[0].matches("[0-9a-f-]{36}") || !id[1].matches("[0-9a-f]{64}"))
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        ControlCodec.uuid(id[0]);
        byte[] encoded = io.get("sessions/v1/" + id[0] + "/spec");
        if (!ControlCodec.hash(encoded).equals(id[1])) throw ControlCodec.unavailable();
        String[] p = ControlCodec.unpack(encoded, "session-v1", 6);
        if (!p[1].equals(io.config.identity()) || !p[2].equals(token.backendId()))
            throw ControlCodec.unavailable();
        try {
            var spec = new UploadSpec(p[2], p[3], p[4], ControlCodec.number(p[5]));
            Session s = new Session(id[0], id[1], spec);
            layout.data(s.key());
            publisher.interval(spec.expectedSize());
            return s;
        } catch (StorageException e) {
            throw ControlCodec.unavailable();
        }
    }

    private byte[] terminal(Session s) {
        byte[] t = io.optional(s.prefix() + "terminal");
        if (t != null) {
            String[] p = ControlCodec.unpack(t, "terminal-v1", 6);
            if (!p[1].equals(s.specHash()) || !Set.of("ABORTED", "COMPLETE").contains(p[2]))
                throw ControlCodec.unavailable();
            if (p[2].equals("ABORTED")) {
                if (!p[3].isEmpty() || !p[4].equals("0") || !p[5].isEmpty())
                    throw ControlCodec.unavailable();
            } else if (!p[3].matches("[0-9a-f]{64}")
                    || ControlCodec.number(p[4]) < 1
                    || ControlCodec.number(p[4]) > Integer.MAX_VALUE
                    || !p[5].matches("[0-9a-f]{64}")) throw ControlCodec.unavailable();
        }
        return t;
    }

    private void open(Session s) {
        if (terminal(s) != null) throw new StorageException(ErrorCode.INVALID_UPLOAD_STATE);
    }

    private String slot(Session s, int part) {
        return s.prefix() + "parts/" + part;
    }

    private byte[] descriptor(Session s, int part, Staging.Root root) {
        return ControlCodec.pack(
                "part-v1",
                s.specHash(),
                Integer.toString(part),
                Base64.getEncoder().encodeToString(root.bytes()));
    }

    private Staging.Root root(Session s, int part, byte[] descriptor) {
        String[] p = ControlCodec.unpack(descriptor, "part-v1", 4);
        if (!p[1].equals(s.specHash()) || ControlCodec.number(p[2]) != part)
            throw ControlCodec.unavailable();
        try {
            return staging.parse(Base64.getDecoder().decode(p[3]), s.id());
        } catch (IllegalArgumentException e) {
            throw ControlCodec.unavailable();
        }
    }

    private PartReceipt receipt(int part, Staging.Root r, byte[] d) {
        return new PartReceipt(part, r.size(), r.hash(), "s3-v1." + ControlCodec.hash(d));
    }

    PartReceipt write(UploadToken token, int part, InputStream body, long size, String hash) {
        hash = ControlCodec.hashArgument(hash);
        Session s = session(token);
        RetiredUploads.live(io,layout,s.key());
        if (part < 1 || size < 0 || size > s.spec().expectedSize())
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        open(s);
        var candidate = staging.stage(s.id(), body, size, hash);
        open(s);
        byte[] d = io.create(slot(s, part), descriptor(s, part, candidate));
        var winner = root(s, part, d);
        if (winner.size() != size || !winner.hash().equals(hash))
            throw new StorageException(ErrorCode.CONFLICT);
        verify(staging.open(winner), size, hash);
        open(s);
        return receipt(part, winner, d);
    }

    private Staging.Root selected(Session s, PartReceipt r) {
        if (r == null) throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        byte[] d = io.get(slot(s, r.part()));
        var root = root(s, r.part(), d);
        if (!receipt(r.part(), root, d).equals(r))
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        return root;
    }

    private byte[] receiptBytes(PartReceipt r) {
        return ControlCodec.pack(
                "receipt-v1",
                Integer.toString(r.part()),
                Long.toString(r.size()),
                r.sha256(),
                r.providerToken());
    }

    private PartReceipt receiptBytes(byte[] bytes) {
        String[] p = ControlCodec.unpack(bytes, "receipt-v1", 5);
        long n = ControlCodec.number(p[1]);
        if (n < 1 || n > Integer.MAX_VALUE) throw ControlCodec.unavailable();
        try {
            return new PartReceipt((int) n, ControlCodec.number(p[2]), p[3], p[4]);
        } catch (StorageException e) {
            throw ControlCodec.unavailable();
        }
    }

    StoredBlob complete(UploadToken token, List<PartReceipt> parts, String hash) {
        hash = ControlCodec.hashArgument(hash);
        Session s = session(token);
        RetiredUploads.live(io,layout,s.key());
        if (parts == null || parts.isEmpty())
            throw new StorageException(ErrorCode.INVALID_ARGUMENT);
        long total = 0;
        var digest = ControlCodec.digest();
        digest.update(
                ControlCodec.pack(
                        "completion-v1", s.specHash(), hash, Integer.toString(parts.size())));
        for (int i = 0; i < parts.size(); i++) {
            var p = parts.get(i);
            if (p == null || p.part() != i + 1)
                throw new StorageException(ErrorCode.INVALID_ARGUMENT);
            selected(s, p);
            if (p.size() > s.spec().expectedSize() - total)
                throw new StorageException(ErrorCode.SIZE_MISMATCH);
            total += p.size();
            digest.update(receiptBytes(p));
        }
        if (total != s.spec().expectedSize()) throw new StorageException(ErrorCode.SIZE_MISMATCH);
        String fingerprint = ControlCodec.hex(digest.digest());
        byte[] proposed =
                ControlCodec.pack(
                        "terminal-v1",
                        s.specHash(),
                        "COMPLETE",
                        fingerprint,
                        Integer.toString(parts.size()),
                        hash);
        byte[] decided = terminal(s);
        if (decided != null) {
            match(decided, proposed);
            return resume(s, decided);
        }
        verify(sequence(s, parts.size(), i -> parts.get(i)), total, hash);
        String prefix = s.prefix() + "completions/" + fingerprint + "/";
        for (int i = 0; i < parts.size(); i++)
            io.exact(prefix + "page/" + i, receiptBytes(parts.get(i)));
        io.exact(
                prefix + "root",
                ControlCodec.pack(
                        "completion-v1",
                        s.specHash(),
                        hash,
                        Integer.toString(parts.size()),
                        fingerprint));
        decided = io.create(s.prefix() + "terminal", proposed);
        match(decided, proposed);
        return resume(s, decided);
    }

    private void match(byte[] actual, byte[] expected) {
        String[] t = ControlCodec.unpack(actual, "terminal-v1", 6);
        if (t[2].equals("ABORTED")) throw new StorageException(ErrorCode.INVALID_UPLOAD_STATE);
        if (!Arrays.equals(actual, expected)) throw new StorageException(ErrorCode.CONFLICT);
    }

    StoredBlob resume(UploadToken token) {
        Session s = session(token);
        RetiredUploads.live(io,layout,s.key());
        byte[] t = terminal(s);
        if (t == null) throw new StorageException(ErrorCode.INVALID_UPLOAD_STATE);
        return resume(s, t);
    }

    private StoredBlob resume(Session s, byte[] terminal) {
        String[] t = ControlCodec.unpack(terminal, "terminal-v1", 6);
        if (!t[2].equals("COMPLETE")) throw new StorageException(ErrorCode.INVALID_UPLOAD_STATE);
        int count = (int) ControlCodec.number(t[4]);
        String prefix = s.prefix() + "completions/" + t[3] + "/";
        byte[] expected = ControlCodec.pack("completion-v1", s.specHash(), t[5], t[4], t[3]);
        if (!Arrays.equals(io.get(prefix + "root"), expected)) throw ControlCodec.unavailable();
        var digest = ControlCodec.digest();
        digest.update(ControlCodec.pack("completion-v1", s.specHash(), t[5], t[4]));
        long total = 0;
        for (int i = 0; i < count; i++) {
            byte[] bytes = io.get(prefix + "page/" + i);
            PartReceipt r = receiptBytes(bytes);
            if (r.part() != i + 1 || r.size() > s.spec().expectedSize() - total)
                throw ControlCodec.unavailable();
            total += r.size();
            digest.update(bytes);
        }
        if (total != s.spec().expectedSize() || !ControlCodec.hex(digest.digest()).equals(t[3]))
            throw ControlCodec.unavailable();
        IntFunction<PartReceipt> receipt = i -> receiptBytes(io.get(prefix + "page/" + i));
        StoredBlob blob =
                publisher.publish(
                        s.key(),
                        total,
                        t[5],
                        s.id(),
                        new PublicationSource(staging, count, i -> selected(s, receipt.apply(i))));
        byte[] result =
                ControlCodec.pack(
                        "result-v1",
                        ControlCodec.hash(terminal),
                        Long.toString(blob.size()),
                        blob.sha256(),
                        blob.providerVersionId() == null ? "" : blob.providerVersionId());
        io.exact(s.prefix() + "result", result);
        return blob;
    }

    private InputStream sequence(Session s, int count, IntFunction<PartReceipt> receipts) {
        return new InputStream() {
            int index;
            InputStream current = InputStream.nullInputStream();
            boolean closed;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 255;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                Objects.checkFromIndexSize(off, len, b.length);
                if (len == 0) return 0;
                if (closed) throw new IOException("STORAGE_UNAVAILABLE");
                while (true) {
                    int n = current.read(b, off, len);
                    if (n != -1) return n;
                    current.close();
                    if (index == count) return -1;
                    current = staging.open(selected(s, receipts.apply(index++)));
                }
            }

            @Override
            public void close() throws IOException {
                closed = true;
                current.close();
            }
        };
    }

    private void verify(InputStream stream, long size, String hash) {
        try (var in = stream) {
            long n = 0;
            var d = ControlCodec.digest();
            byte[] b = new byte[64 * 1024];
            int k;
            while ((k = in.read(b)) != -1) {
                n = Math.addExact(n, k);
                d.update(b, 0, k);
            }
            if (n != size) throw new StorageException(ErrorCode.SIZE_MISMATCH);
            if (!ControlCodec.hex(d.digest()).equals(hash))
                throw new StorageException(ErrorCode.CHECKSUM_MISMATCH);
        } catch (IOException | ArithmeticException e) {
            throw ControlCodec.unavailable();
        }
    }

    void discard(UploadToken token,StorageKey key){RetiredUploads.discard(io,layout,session(token),key);}

    void abort(UploadToken token) {
        Session s;
        try {
            s = session(token);
        } catch (StorageException e) {
            if (e.code() == ErrorCode.NOT_FOUND) return;
            throw e;
        }
        byte[] t = terminal(s);
        if (t != null) return;
        io.create(
                s.prefix() + "terminal",
                ControlCodec.pack("terminal-v1", s.specHash(), "ABORTED", "", "0", ""));
        if (terminal(s) == null) throw ControlCodec.unavailable();
    }
}
