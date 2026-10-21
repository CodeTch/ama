package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.util.*;

class S3NativeRaceIT {
    @ParameterizedTest
    @ValueSource(strings = {"never", "Enabled", "Suspended"})
    void distinctJvmsRaceActualNativeCompletionsAndConditionalPut(String mode) throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            if (!mode.equals("never")) {
                f.version("Enabled");
                if (mode.equals("Suspended")) f.version(mode);
            }
            byte[] a = new byte[9 * 1024 * 1024];
            Arrays.fill(a, (byte) 1);
            for (boolean put : new boolean[] {false, true})
                for (boolean same : new boolean[] {false, true}) {
                    byte[] b = a.clone();
                    if (!same) Arrays.fill(b, (byte) 2);
                    String name = "native-" + put + "-" + same,
                            ha = ControlCodec.hash(a),
                            hb = ControlCodec.hash(b);
                    var ta = s.beginUpload(new UploadSpec("s3", "meeting", name, a.length));
                    var ra = s.writePart(ta, 1, new ByteArrayInputStream(a), a.length, ha);
                    var first =
                            worker(
                                    f,
                                    "complete",
                                    ta.sessionId(),
                                    ha,
                                    "1," + a.length + "," + ha + "," + ra.providerToken());
                    Worker second;
                    if (put)
                        second =
                                worker(
                                        f,
                                        "put-generated",
                                        name,
                                        same ? "1" : "2",
                                        Integer.toString(a.length));
                    else {
                        var tb = s.beginUpload(new UploadSpec("s3", "meeting", name, b.length));
                        var rb = s.writePart(tb, 1, new ByteArrayInputStream(b), b.length, hb);
                        second =
                                worker(
                                        f,
                                        "complete",
                                        tb.sessionId(),
                                        hb,
                                        "1," + b.length + "," + hb + "," + rb.providerToken());
                    }
                    first.go();
                    second.go();
                    String one = first.finish(), two = second.finish();
                    assertEquals(
                            same ? 2 : 1,
                            (one.startsWith("OK") ? 1 : 0) + (two.startsWith("OK") ? 1 : 0));
                    if (!same) assertTrue((one + two).contains("ERROR CONFLICT"));
                    assertTrue(List.of(ha, hb).contains(s.stat(key(name)).sha256()));
                }
            proxy.settle();
            assertTrue(
                    proxy.snapshot().stream()
                            .filter(
                                    e ->
                                            e.method().equals("POST")
                                                    && e.path().contains("uploadId="))
                            .allMatch(e -> "*".equals(e.request().get("if-none-match"))));
            assertTrue(proxy.snapshot().stream().anyMatch(e -> e.path().contains("partNumber=")));
            capture("v3-native-process-race-" + mode, proxy);
        }
    }
}
