package ama.storage.s3;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Explicit two-process probe; its caller restarts only the exact owned fixture between phases. */
public final class S3RestartProbe {
    static final String LONG = "q".repeat(512);
    static final List<String> NAMES =
            List.of(
                    "a".repeat(199),
                    "a".repeat(200),
                    "b".repeat(942),
                    String.join("/", Collections.nCopies(256, "a")),
                    LONG);

    static StorageKey key(String name) {
        return new StorageKey("s3", "meeting", name);
    }

    public static void main(String[] args) throws Exception {
        Path record = Path.of(args[1]);
        byte[] large = new byte[9 * 1024 * 1024 + 37];
        Arrays.fill(large, (byte) 3);
        String hash = ControlCodec.hash(large);
        if (args[0].equals("write")) {
            try (var f = new S3Fixture("s3");
                    var s = f.open()) {
                for (String name : NAMES)
                    if (!name.equals(LONG))
                        s.put(
                                key(name),
                                new ByteArrayInputStream(new byte[] {1, 2, 3}),
                                3,
                                ControlCodec.hash(new byte[] {1, 2, 3}));
                var t = s.beginUpload(new UploadSpec("s3", "meeting", LONG, large.length));
                var p = s.writePart(t, 1, new ByteArrayInputStream(large), large.length, hash);
                assertEquals(hash, s.complete(t, List.of(p), hash).sha256());
                Files.write(
                        record,
                        ControlCodec.pack(
                                "restart-probe-v3",
                                f.config.endpoint().toString(),
                                f.config.dataBucket(),
                                f.config.controlBucket(),
                                t.sessionId()));
                System.out.println(
                        "ACK layout=v3 objects=5 ordinaryLogical512NativeMPU=true physical1024=true"
                            + " endpoint="
                                + f.config.endpoint()
                                + " dataBucket="
                                + f.config.dataBucket());
            }
        } else {
            String[] p = ControlCodec.unpack(Files.readAllBytes(record), "restart-probe-v3", 5);
            var config = S3Fixture.config("s3", URI.create(p[1]), p[2], p[3]);
            try (var s = new S3FileStorage(config);
                    var admin = S3Fixture.admin(URI.create(p[1]))) {
                for (String name : NAMES) {
                    byte[] expected = name.equals(LONG) ? large : new byte[] {1, 2, 3};
                    assertEquals(ControlCodec.hash(expected), s.stat(key(name)).sha256());
                    assertArrayEquals(
                            expected,
                            admin.getObjectAsBytes(
                                            b ->
                                                    b.bucket(p[2])
                                                            .key(
                                                                    new S3Layout(config)
                                                                            .data(key(name))))
                                    .asByteArray());
                    try (var in = s.read(key(name), 1, 1)) {
                        assertEquals(expected[1] & 255, in.read());
                        assertEquals(-1, in.read());
                    }
                }
                assertEquals(hash, s.resume(new UploadToken("s3", p[4])).sha256());
                List<String> found = new ArrayList<>();
                String cursor = null;
                do {
                    var page = s.list("s3", "meeting", "", cursor, 1);
                    found.addAll(page.items().stream().map(i -> i.key().objectKey()).toList());
                    cursor = page.nextCursor();
                } while (cursor != null);
                assertEquals(NAMES.stream().sorted().toList(), found);
                assertEquals(2, s.list("s3", "meeting", "a".repeat(199), null, 10).items().size());
                s.delete(key("b".repeat(942)));
                assertEquals(
                        ErrorCode.NOT_FOUND,
                        assertThrows(StorageException.class, () -> s.stat(key("b".repeat(942))))
                                .code());
                System.out.println(
                        "RECOVERED layout=v3 fullByteChecks=5 tokenOnlyResume=true boundedPages=5"
                            + " boundary199Coexist=true exact1024Delete=true");
            }
        }
    }
}
