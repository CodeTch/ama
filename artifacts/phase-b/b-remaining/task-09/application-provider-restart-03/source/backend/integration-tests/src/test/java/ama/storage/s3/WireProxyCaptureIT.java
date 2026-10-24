package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class WireProxyCaptureIT {
    @Test
    void settledSnapshotsAreImmutableAcrossConcurrentAppendAndReset() throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint());
                var s = f.open();
                var pool = Executors.newFixedThreadPool(4)) {
            s.put(key("capture"), body("abc"), 3, hash("abc"));
            proxy.reset();
            List<Future<?>> work = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                work.add(
                        pool.submit(
                                () -> assertEquals(hash("abc"), s.stat(key("capture")).sha256())));
            for (var job : work) job.get(20, TimeUnit.SECONDS);
            var first = proxy.snapshot();
            assertEquals(
                    12, first.size()); // Four actual stat calls: scope, key binding, final GET.
            assertThrows(UnsupportedOperationException.class, () -> first.clear());
            assertEquals(hash("abc"), s.stat(key("capture")).sha256());
            assertEquals(15, proxy.snapshot().size());
            assertEquals(12, first.size());
            capture("append-snapshot", proxy);
            proxy.reset();
            assertTrue(proxy.snapshot().isEmpty());
            assertEquals(hash("abc"), s.stat(key("capture")).sha256());
            assertEquals(3, proxy.snapshot().size());
            assertEquals(12, first.size());
        }
    }
}
