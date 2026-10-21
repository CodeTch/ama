package ama.storage.s3;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;

class S3ReliabilityIT {
  static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  static ByteArrayInputStream body(String s) {
    return new ByteArrayInputStream(bytes(s));
  }

  static String hash(String s) {
    return ControlCodec.hash(bytes(s));
  }

  static StorageKey key(String name) {
    return new StorageKey("s3", "meeting", name);
  }

  static void error(ErrorCode code, org.junit.jupiter.api.function.Executable call) {
    assertEquals(code, assertThrows(StorageException.class, call).code());
  }

  static void capture(String name, WireProxy proxy) throws Exception {
    proxy.settle();
    String group = System.getenv().getOrDefault("AMA_S3_EVIDENCE_GROUP", "unlabelled");
    if (!group.matches("[a-zA-Z0-9_-]+"))
      throw new IllegalArgumentException("invalid evidence group");
    Path root = Path.of("../../artifacts/phase-b/b04-s3/wire", group);
    Files.createDirectories(root);
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    json.writerWithDefaultPrettyPrinter()
        .writeValue(root.resolve(name + "-" + UUID.randomUUID() + ".json").toFile(), proxy.events);
  }

  @Test
  void rangesTransferOnlyRequestedFinalPayloadAndAbortWithoutDrain() throws Exception {
    try (var proxy = new WireProxy();
        var f = new S3Fixture("s3", proxy.endpoint());
        var s = f.open()) {
      byte[] big = new byte[16 * 1024 * 1024];
      Arrays.fill(big, (byte) 'x');
      s.put(key("large"), new ByteArrayInputStream(big), big.length, ControlCodec.hash(big));
      proxy.settle();
      proxy.events.clear();
      for (long offset : new long[] {0, 8193, big.length - 1})
        try (var in = s.read(key("large"), offset, 1)) {
          assertArrayEquals(bytes("x"), in.readAllBytes());
        }
      try (var in = s.read(key("large"), 0, 0)) {
        assertEquals(-1, in.read());
      }
      try (var in = s.read(key("large"), big.length, 0)) {
        assertEquals(-1, in.read());
      }
      error(ErrorCode.INVALID_RANGE, () -> s.read(key("large"), -1, 1));
      error(ErrorCode.INVALID_RANGE, () -> s.read(key("large"), Long.MAX_VALUE, 2));
      error(ErrorCode.INVALID_RANGE, () -> s.read(key("large"), big.length, 1));
      proxy.settle();
      var gets =
          proxy.events.stream()
              .filter(e -> e.method().equals("GET") && e.path().contains("/data/v3/"))
              .toList();
      assertEquals(3, gets.size());
      assertEquals(3, gets.stream().mapToLong(WireProxy.Event::upstreamBytes).sum());
      assertTrue(
          gets.stream().allMatch(e -> e.status() == 206 && e.request().containsKey("if-match")));
      assertTrue(
          proxy.events.stream()
              .filter(e -> e.method().equals("HEAD"))
              .allMatch(e -> e.upstreamBytes() == 0));
      capture("range-one-zero-invalid", proxy);
      proxy.events.clear();
      try (var in = s.read(key("large"), 0, big.length)) {
        assertEquals('x', in.read());
      }
      proxy.settle();
      long cancel =
          proxy.events.stream()
              .filter(e -> e.path().contains("/data/v3/"))
              .mapToLong(WireProxy.Event::upstreamBytes)
              .sum();
      assertEquals(
          1,
          proxy.events.stream()
              .filter(e -> e.method().equals("GET") && e.path().contains("/data/v3/"))
              .count());
      assertTrue(cancel < 1024 * 1024, "cancellation drains object: " + cancel);
      capture("range-cancel", proxy);
      for (String fault : List.of("ignore-range", "wrong-range", "overlong", "truncate")) {
        proxy.events.clear();
        proxy.arm(fault, p -> p.startsWith("GET ") && p.contains("/data/v3/"));
        if (fault.equals("truncate"))
          try (var in = s.read(key("large"), 0, 100)) {
            assertThrows(IOException.class, in::readAllBytes);
          }
        else error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.read(key("large"), 0, big.length));
        proxy.settle();
        assertTrue(
            proxy.events.stream()
                    .filter(e -> e.path().contains("/data/v3/"))
                    .mapToLong(WireProxy.Event::upstreamBytes)
                    .sum()
                < 1024 * 1024);
        assertEquals(
            1,
            proxy.events.stream()
                .filter(e -> e.method().equals("GET") && e.path().contains("/data/v3/"))
                .count());
        capture("range-" + fault, proxy);
      }
    }
  }

  @Test
  void immutableResponseLossReconcilesFixedKeysAndRetainsUnknownCreate() throws Exception {
    try (var proxy = new WireProxy();
        var f = new S3Fixture("s3", proxy.endpoint());
        var s = f.open()) {
      for (String suffix :
          List.of("/spec", "/chunk/0", "/root", "/parts/1", "/terminal", "/result")) {
        var token = s.beginUpload(new UploadSpec("s3", "meeting", "loss-" + UUID.randomUUID(), 3));
        proxy.arm("drop", p -> p.startsWith("PUT ") && p.contains(suffix));
        if (suffix.equals("/spec")) {
          var second = s.beginUpload(new UploadSpec("s3", "meeting", "lost-spec", 0));
          s.abort(second);
        } else {
          var r = s.writePart(token, 1, body("abc"), 3, hash("abc"));
          var result = s.complete(token, List.of(r), hash("abc"));
          assertEquals(hash("abc"), result.sha256());
        }
        assertNull(proxy.fault.get(), suffix + " was never injected");
      }
      var aborted = s.beginUpload(new UploadSpec("s3", "meeting", "lost-abort", 0));
      proxy.arm("drop", p -> p.startsWith("PUT ") && p.endsWith("/terminal"));
      s.abort(aborted);
      s.abort(aborted);
      error(ErrorCode.INVALID_UPLOAD_STATE, () -> s.writePart(aborted, 1, body(""), 0, hash("")));
      byte[] large = new byte[9 * 1024 * 1024];
      Arrays.fill(large, (byte) 7);
      var t = s.beginUpload(new UploadSpec("s3", "meeting", "unknown-create", large.length));
      var part =
          s.writePart(
              t, 1, new ByteArrayInputStream(large), large.length, ControlCodec.hash(large));
      proxy.arm("drop", p -> p.startsWith("POST ") && p.contains("uploads"));
      error(
          ErrorCode.STORAGE_UNAVAILABLE,
          () -> s.complete(t, List.of(part), ControlCodec.hash(large)));
      assertEquals(large.length, s.resume(t).size());
      assertFalse(
          f.admin.listMultipartUploads(b -> b.bucket(f.config.dataBucket())).uploads().isEmpty(),
          "unknown native upload must remain retained");
      capture("response-loss", proxy);
      assertTrue(
          proxy.events.stream()
              .filter(e -> e.method().equals("PUT") && !e.path().contains("partNumber="))
              .allMatch(e -> "*".equals(e.request().get("if-none-match"))));
      assertTrue(
          proxy.events.stream()
              .filter(e -> e.method().equals("POST") && e.path().contains("uploadId="))
              .allMatch(e -> "*".equals(e.request().get("if-none-match"))));
    }
  }

  record Worker(Process process, BufferedReader output) {
    void go() throws Exception {
      process.getOutputStream().write('\n');
      process.getOutputStream().flush();
    }

    String finish() throws Exception {
      assertTrue(process.waitFor(45, TimeUnit.SECONDS));
      return output.lines().reduce("", (a, b) -> a + b + "\n");
    }
  }

  static Worker worker(S3Fixture f, String... operation) throws Exception {
    List<String> command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Xmx192m",
                "-cp",
                System.getProperty("java.class.path"),
                S3Process.class.getName(),
                f.config.endpoint().toString(),
                f.config.dataBucket(),
                f.config.controlBucket()));
    command.addAll(List.of(operation));
    Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
    var out = new BufferedReader(new InputStreamReader(p.getInputStream()));
    assertEquals("READY", out.readLine());
    return new Worker(p, out);
  }

  @ParameterizedTest
  @ValueSource(strings = {"never", "Enabled", "Suspended"})
  void distinctJvmWritersHaveOneWinnerInEveryVersionMode(String mode) throws Exception {
    try (var f = new S3Fixture("s3")) {
      if (!mode.equals("never")) {
        f.version("Enabled");
        if (mode.equals("Suspended")) f.version(mode);
      }
      var a = worker(f, "put", "race", "abc");
      var b = worker(f, "put", "race", "xyz");
      a.go();
      b.go();
      String first = a.finish(), second = b.finish();
      assertEquals(1, (first.startsWith("OK") ? 1 : 0) + (second.startsWith("OK") ? 1 : 0));
      assertTrue((first + second).contains("ERROR CONFLICT"));
      try (var s = f.open()) {
        var stat = s.stat(key("race"));
        assertTrue(Set.of(hash("abc"), hash("xyz")).contains(stat.sha256()));
        if (mode.equals("Enabled")) assertNotNull(stat.providerVersionId());
        s.delete(key("race"));
        error(ErrorCode.NOT_FOUND, () -> s.stat(key("race")));
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/terminal", "/known-upload", "/result"})
  void killedJvmResumesFrozenDecisionUsingOnlyToken(String boundary) throws Exception {
    try (var proxy = new WireProxy();
        var f = new S3Fixture("s3", proxy.endpoint());
        var s = f.open()) {
      byte[] data = new byte[9 * 1024 * 1024];
      Arrays.fill(data, (byte) 3);
      String hash = ControlCodec.hash(data);
      var token = s.beginUpload(new UploadSpec("s3", "meeting", "restart", data.length));
      var r = s.writePart(token, 1, new ByteArrayInputStream(data), data.length, hash);
      var barrier = proxy.arm("pause", p -> p.startsWith("PUT ") && p.endsWith(boundary));
      var process =
          worker(
              f,
              "complete",
              token.sessionId(),
              hash,
              r.part() + "," + r.size() + "," + r.sha256() + "," + r.providerToken());
      process.go();
      assertTrue(barrier.reached.await(30, TimeUnit.SECONDS), "durable boundary not reached");
      process.process().destroyForcibly();
      assertTrue(process.process().waitFor(5, TimeUnit.SECONDS));
      barrier.release.countDown();
      var fresh = worker(f, "resume", token.sessionId());
      fresh.go();
      assertTrue(fresh.finish().startsWith("OK " + data.length + " " + hash));
      assertEquals(hash, s.stat(key("restart")).sha256());
      capture("jvm-kill-" + boundary.substring(1), proxy);
    }
  }

  @Test
  void scopedProviderListingAndUnicodeKeyIdentity() throws Exception {
    try (var proxy = new WireProxy();
        var f = new S3Fixture("s3", proxy.endpoint());
        var s = f.open()) {
      List<String> keys =
          List.of(
              "p/a", "p/a? &<>#", "p/b", "p/~", "p/é", "p/\ud800", "p/\ud83d\ude00", "unrelated");
      try {
        for (String name : keys) s.put(key(name), body("x"), 1, hash("x"));
      } finally {
        capture("key-identity-setup", proxy);
      }
      proxy.settle();
      proxy.events.clear();
      List<String> found = new ArrayList<>();
      String cursor = null;
      do {
        var page = s.list("s3", "meeting", "p/", cursor, 2);
        found.addAll(page.items().stream().map(i -> i.key().objectKey()).toList());
        cursor = page.nextCursor();
      } while (cursor != null);
      assertEquals(keys.stream().filter(k -> k.startsWith("p/")).sorted().toList(), found);
      proxy.settle();
      var lists = proxy.events.stream().filter(e -> e.path().contains("list-type=")).toList();
      assertEquals(4, lists.size());
      assertTrue(
          lists.stream()
              .allMatch(
                  e ->
                      e.path().contains("max-keys=2")
                          && e.path()
                              .contains(
                                  "prefix="
                                      + java.net.URLEncoder.encode(
                                          new S3Layout(f.config).prefix("meeting", "p/"),
                                          java.nio.charset.StandardCharsets.UTF_8))));
      capture("scoped-list", proxy);
      String max = "a".repeat(942);
      s.put(key(max), body(""), 0, hash(""));
      error(ErrorCode.INVALID_ARGUMENT, () -> s.put(key(max + "a"), body(""), 0, hash("")));
      assertThrows(StorageException.class, () -> S3Layout.decode("~0061"));
      assertThrows(StorageException.class, () -> S3Layout.decode("~D800"));
    }
  }

  @Test
  void wrongWholeHashIsRetryableAndCorruptSelectedBytesFailClosed() throws Exception {
    try (var f = new S3Fixture("s3");
        var s = f.open()) {
      var t = s.beginUpload(new UploadSpec("s3", "meeting", "hash", 3));
      var r = s.writePart(t, 1, body("abc"), 3, hash("abc"));
      error(ErrorCode.CHECKSUM_MISMATCH, () -> s.complete(t, List.of(r), hash("xyz")));
      assertEquals(hash("abc"), s.complete(t, List.of(r), hash("abc")).sha256());
      var corrupt = s.beginUpload(new UploadSpec("s3", "meeting", "corrupt", 3));
      var part = s.writePart(corrupt, 1, body("abc"), 3, hash("abc"));
      String session = corrupt.sessionId().split("\\.")[0];
      var page =
          f.admin.listObjectsV2(
              b -> b.bucket(f.config.controlBucket()).prefix("staging/v1/" + session + "/"));
      String chunk =
          page.contents().stream()
              .map(S3Object::key)
              .filter(k -> k.contains("/chunk/"))
              .findFirst()
              .orElseThrow();
      f.admin.putObject(
          b -> b.bucket(f.config.controlBucket()).key(chunk), RequestBody.fromBytes(bytes("xyz")));
      error(ErrorCode.STORAGE_UNAVAILABLE, () -> s.complete(corrupt, List.of(part), hash("abc")));
      error(ErrorCode.NOT_FOUND, () -> s.stat(key("corrupt")));
    }
  }
}
