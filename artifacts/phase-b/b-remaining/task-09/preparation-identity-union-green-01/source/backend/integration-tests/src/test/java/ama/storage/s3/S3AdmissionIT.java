package ama.storage.s3;

import static ama.storage.s3.S3ReliabilityIT.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.interceptor.*;
import software.amazon.awssdk.http.urlconnection.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

class S3AdmissionIT {
  @Test
  void concurrentNativeBodiesCannotConsumeTheirOwnStagingRequestAdmission() throws Exception {
    try (var proxy = new WireProxy();
        var f = new S3Fixture("s3", proxy.endpoint());
        var pool = Executors.newFixedThreadPool(2)) {
      var c = f.config;
      var config =
          new S3Config(
              c.backendId(),
              c.endpoint(),
              c.region(),
              c.dataBucket(),
              c.controlBucket(),
              c.credentials(),
              c.pathStyle(),
              c.keyBudget(),
              c.chunkBytes(),
              c.nativeMinimum(),
              c.nativeMaximum(),
              c.nativeMaxParts(),
              c.maximumObject(),
              2,
              Duration.ofSeconds(5));
      var arrivals = new CountDownLatch(2);
      ExecutionInterceptor barrier =
          new ExecutionInterceptor() {
            @Override
            public void beforeExecution(
                Context.BeforeExecution context, ExecutionAttributes attributes) {
              if (context.request() instanceof UploadPartRequest p && p.partNumber() == 1) {
                arrivals.countDown();
                // Bounded rendezvous also permits a correct implementation to
                // serialize
                // native requests while retaining a staging request slot.
                try {
                  arrivals.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
              }
            }
          };
      try (var client =
          S3Client.builder()
              .endpointOverride(config.endpoint())
              .region(Region.US_EAST_1)
              .credentialsProvider(config.credentials())
              .forcePathStyle(true)
              .httpClientBuilder(
                  UrlConnectionHttpClient.builder()
                      .proxyConfiguration(
                          ProxyConfiguration.builder()
                              .useSystemPropertyValues(false)
                              .useEnvironmentVariablesValues(false)
                              .build()))
              .overrideConfiguration(
                  b ->
                      b.retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                          .apiCallTimeout(Duration.ofSeconds(20))
                          .addExecutionInterceptor(barrier))
              .build()) {
        var io = new S3Objects(client, config);
        var staging = new Staging(io);
        var publisher = new NativePublisher(io, new S3Layout(config));
        byte[] data = new byte[9 * 1024 * 1024];
        Arrays.fill(data, (byte) 8);
        String hash = ControlCodec.hash(data);
        List<Future<?>> jobs = new ArrayList<>();
        List<Staging.Root> roots = new ArrayList<>();
        for (int i = 0; i < 2; i++)
          roots.add(
              staging.stage(ControlCodec.id(), new ByteArrayInputStream(data), data.length, hash));
        for (int i = 0; i < 2; i++) {
          int n = i;
          Staging.Root root = roots.get(i);
          jobs.add(
              pool.submit(
                  () ->
                      publisher.publish(
                          key("admission-" + n),
                          data.length,
                          hash,
                          root.owner(),
                          new PublicationSource(staging, 1, x -> root))));
        }
        List<Throwable> failures = new ArrayList<>();
        for (var job : jobs)
          try {
            job.get(40, TimeUnit.SECONDS);
          } catch (ExecutionException e) {
            failures.add(e.getCause());
          }
        capture("v3-native-admission", proxy);
        assertTrue(failures.isEmpty(), () -> "nested staging admission failures=" + failures);
        assertTrue(proxy.peakUpstream.get() <= 2);
        System.out.println(
            "configuredAdmission=2 observedPeakUpstreamHTTP=" + proxy.peakUpstream.get());
        for (int i = 0; i < 2; i++)
          assertEquals(hash, io.stat(new S3Layout(config).data(key("admission-" + i))).sha256());
      }
    }
  }
}
