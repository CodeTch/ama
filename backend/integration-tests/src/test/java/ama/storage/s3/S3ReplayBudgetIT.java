package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

class S3ReplayBudgetIT {
    @Test
    void nativeIntervalReplayReadsBoundedMultipleOfActualPayload() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open()) {
            for (int mib : new int[] {16, 64}) {
                byte[] data = new byte[mib * 1024 * 1024];
                Arrays.fill(data, (byte) 7);
                proxy.settle();
                proxy.events.clear();
                s.put(
                        key("replay-" + mib),
                        new ByteArrayInputStream(data),
                        data.length,
                        ControlCodec.hash(data));
                proxy.settle();
                long bytes =
                        proxy.events.stream()
                                .filter(
                                        e ->
                                                e.method().equals("GET")
                                                        && e.path().contains("/staging/")
                                                        && e.path().contains("/chunk/"))
                                .mapToLong(WireProxy.Event::upstreamBytes)
                                .sum();
                long parts =
                        proxy.events.stream()
                                .filter(
                                        e ->
                                                e.method().equals("PUT")
                                                        && e.path().contains("partNumber="))
                                .count();
                capture("v3-replay-budget-" + mib, proxy);
                System.out.println(
                        "payload="
                                + data.length
                                + " nativeParts="
                                + parts
                                + " stagingGETBody="
                                + bytes);
                assertTrue(
                        bytes <= 8L * data.length,
                        "staging payload amplification must remain constant: "
                                + bytes
                                + " for "
                                + data.length);
            }
        }
    }
}
