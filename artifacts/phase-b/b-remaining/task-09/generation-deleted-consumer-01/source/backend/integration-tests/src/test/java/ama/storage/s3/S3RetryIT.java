package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;

class S3RetryIT {
    @ParameterizedTest
    @ValueSource(strings = {"adapter", "fixture"})
    void committedMutationResponseLossNeverTriggersSdkMutationReplay(String mode) throws Exception {
        try (var proxy = new WireProxy();
                var f = new S3Fixture("s3", proxy.endpoint())) {
            proxy.arm(
                    "drop",
                    p ->
                            p.startsWith("PUT ")
                                    && (mode.equals("adapter")
                                            ? p.contains("/chunk/0")
                                            : p.contains("/retry-object")));
            if (mode.equals("adapter")) {
                try (var s = f.open()) {
                    assertEquals(
                            hash("abc"), s.put(key("retry"), body("abc"), 3, hash("abc")).sha256());
                }
            } else {
                try (var client = S3Fixture.admin(proxy.endpoint())) {
                    assertThrows(
                            SdkException.class,
                            () ->
                                    client.putObject(
                                            b ->
                                                    b.bucket(f.config.controlBucket())
                                                            .key("retry-object"),
                                            RequestBody.fromString("abc")));
                }
                assertEquals(
                        "abc",
                        f.admin
                                .getObjectAsBytes(
                                        b -> b.bucket(f.config.controlBucket()).key("retry-object"))
                                .asUtf8String());
            }
            var snapshot = proxy.snapshot();
            var failed = snapshot.stream().filter(WireProxy.Event::fault).toList();
            assertEquals(1, failed.size());
            assertEquals(
                    1,
                    snapshot.stream()
                            .filter(
                                    e ->
                                            e.method().equals("PUT")
                                                    && e.path().equals(failed.getFirst().path()))
                            .count());
            assertEquals(200, failed.getFirst().status());
            capture("retry-max-one-" + mode, proxy);
        }
    }
}
