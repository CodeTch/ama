package ama.storage.s3;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Test-only streaming HTTP proxy. Preserves signed Host, counts bytes read from real upstream. */
public final class Task05Fix02Proxy implements AutoCloseable {
  record Event(
      String method,
      String path,
      Map<String, String> request,
      int status,
      Map<String, String> response,
      long upstreamBytes,
      long downstreamBytes,
      boolean fault) {}

  public static final class Fault {
    final Predicate<String> selector;
    final String action;
    public final CountDownLatch reached = new CountDownLatch(1), release = new CountDownLatch(1);

    Fault(Predicate<String> selector, String action) {
      this.selector = selector;
      this.action = action;
    }
  }

  final ServerSocket listener;
  final URI upstream;
  final ExecutorService pool = Executors.newCachedThreadPool();
  private final List<Event> events = new ArrayList<>();
  final AtomicReference<Fault> fault = new AtomicReference<>();
  public final AtomicInteger requestsSeen=new AtomicInteger();
  final AtomicInteger active = new AtomicInteger();
  final AtomicInteger upstreamActive = new AtomicInteger(), peakUpstream = new AtomicInteger();
  volatile boolean closed;

  public Task05Fix02Proxy() throws IOException {
    upstream = URI.create(S3Fixture.required("AMA_S3_ENDPOINT"));
    listener = new ServerSocket(0, 64, InetAddress.getLoopbackAddress());
    pool.submit(
        () -> {
          while (!closed)
            try {
              Socket socket = listener.accept();
              synchronized (events) {
                active.incrementAndGet();
              }
              pool.submit(() -> handle(socket));
            } catch (IOException e) {
              if (!closed) throw new UncheckedIOException(e);
            }
        });
  }

  public URI endpoint() {
    return URI.create("http://127.0.0.1:" + listener.getLocalPort());
  }

  public Fault arm(String action, Predicate<String> selector) {
    Fault f = new Fault(selector, action);
    if (!fault.compareAndSet(null, f)) throw new IllegalStateException();
    return f;
  }

  static String line(InputStream in) throws IOException {
    var out = new ByteArrayOutputStream();
    int prior = -1, n;
    while ((n = in.read()) != -1) {
      if (prior == '\r' && n == '\n') {
        byte[] b = out.toByteArray();
        return new String(b, 0, b.length - 1, StandardCharsets.ISO_8859_1);
      }
      out.write(n);
      prior = n;
      if (out.size() > 65536) throw new IOException("header bound");
    }
    if (out.size() == 0) return null;
    throw new EOFException();
  }

  static Map<String, String> headers(InputStream in) throws IOException {
    Map<String, String> h = new LinkedHashMap<>();
    String l;
    while ((l = line(in)) != null && !l.isEmpty()) {
      int colon = l.indexOf(':');
      if (colon < 1) throw new IOException("bad header");
      h.put(l.substring(0, colon).toLowerCase(Locale.ROOT), l.substring(colon + 1).trim());
    }
    return h;
  }

  static void text(OutputStream out, String s) throws IOException {
    out.write(s.getBytes(StandardCharsets.ISO_8859_1));
  }

  static long copy(InputStream in, OutputStream out, long length) throws IOException {
    byte[] b = new byte[8192];
    long n = 0;
    while (n < length) {
      int k = in.read(b, 0, (int) Math.min(b.length, length - n));
      if (k < 0) throw new EOFException();
      out.write(b, 0, k);
      out.flush();
      n += k;
    }
    return n;
  }

  void handle(Socket downstream) {
    String method = "", path = "";
    Map<String, String> request = Map.of(), response = Map.of();
    int status = 0;
    long received = 0, sent = 0;
    boolean injected = false;
    boolean upstreamInFlight = false;
    try (downstream;
        Socket backend = new Socket(upstream.getHost(), upstream.getPort())) {
      downstream.setSoTimeout(60000);
      backend.setSoTimeout(60000);
      downstream.setSendBufferSize(8192);
      backend.setReceiveBufferSize(8192);
      var clientIn = new BufferedInputStream(downstream.getInputStream(), 8192);
      var clientOut = downstream.getOutputStream();
      // Count every upstream body byte read from the socket, without hidden user-space
      // readahead.
      var serverIn = backend.getInputStream();
      var serverOut = backend.getOutputStream();
      String first = line(clientIn);
      if (first == null) return;
      requestsSeen.incrementAndGet();
      String[] split = first.split(" ");
      method = split[0];
      path = split[1];
      request = headers(clientIn);
      Fault f = fault.get();
      if (f != null && f.selector.test(method + " " + path) && fault.compareAndSet(f, null)) {
        injected = true;
      } else f = null;
      upstreamInFlight = true;
      peakUpstream.accumulateAndGet(upstreamActive.incrementAndGet(), Math::max);
      text(serverOut, first + "\r\n");
      for (var h : request.entrySet())
        if (!h.getKey().equals("connection") && !h.getKey().equals("expect"))
          text(serverOut, h.getKey() + ": " + h.getValue() + "\r\n");
      text(serverOut, "Connection: close\r\n\r\n");
      serverOut.flush();
      if (request.containsKey("expect")) {
        text(clientOut, "HTTP/1.1 100 Continue\r\n\r\n");
        clientOut.flush();
      }
      long length = Long.parseLong(request.getOrDefault("content-length", "0"));
      copy(clientIn, serverOut, length);
      String statusLine = line(serverIn);
      status = Integer.parseInt(statusLine.split(" ")[1]);
      response = headers(serverIn);
      long body =
          method.equals("HEAD") ? 0 : Long.parseLong(response.getOrDefault("content-length", "0"));
      if (body == 0) {
        upstreamActive.decrementAndGet();
        upstreamInFlight = false;
      }
      if (f != null && f.action.equals("pause")) {
        f.reached.countDown();
        if (!f.release.await(45, TimeUnit.SECONDS)) throw new IOException("barrier timeout");
      }
      if (f != null && f.action.equals("drop")) {
        received = copy(serverIn, OutputStream.nullOutputStream(), body);
        f.reached.countDown();
        return;
      }
      if (f != null && f.action.equals("stall-error")) {
        text(clientOut,"HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/xml\r\nContent-Length: 64\r\nConnection: close\r\n\r\n<");clientOut.flush();
        f.reached.countDown();if(!f.release.await(10,TimeUnit.SECONDS))throw new IOException("barrier timeout");return;
      }
      Map<String, String> outgoing = new LinkedHashMap<>(response);
      String sendStatus = statusLine;
      if (f != null && f.action.equals("ignore-range")) {
        sendStatus = "HTTP/1.1 200 OK";
        outgoing.remove("content-range");
      }
      if (f != null && f.action.equals("wrong-range")) outgoing.put("content-range", "bytes 0-0/1");
      if (f != null && f.action.equals("overlong"))
        outgoing.put("content-length", Long.toString(body + 1));
      if (f != null && Set.of("omit-length", "chunked-no-length").contains(f.action))
        outgoing.remove("content-length");
      boolean chunked = f != null && f.action.equals("chunked-no-length");
      if (chunked) outgoing.put("transfer-encoding", "chunked");
      if (f != null && f.action.equals("wrong-version"))
        outgoing.put("x-amz-version-id", "wrong-version");
      if (f != null && Set.of("error200", "error409", "error412", "nosuch").contains(f.action)) {
        received = copy(serverIn, OutputStream.nullOutputStream(), body);
        String code =
            switch (f.action) {
              case "error409" -> "ConditionalRequestConflict";
              case "error412" -> "PreconditionFailed";
              case "nosuch" -> "NoSuchUpload";
              default -> "InternalError";
            };
        String http =
            switch (f.action) {
              case "error409" -> "409 Conflict";
              case "error412" -> "412 Precondition Failed";
              case "nosuch" -> "404 Not Found";
              default -> "200 OK";
            };
        byte[] error =
            ("<Error><Code>"
                    + code
                    + "</Code><Message>injected after real"
                    + " commit</Message></Error>")
                .getBytes(StandardCharsets.UTF_8);
        text(
            clientOut,
            "HTTP/1.1 "
                + http
                + "\r\nContent-Type: application/xml\r\nContent-Length: "
                + error.length
                + "\r\nConnection: close\r\n\r\n");
        clientOut.write(error);
        clientOut.flush();
        f.reached.countDown();
        return;
      }
      text(clientOut, sendStatus + "\r\n");
      for (var h : outgoing.entrySet())
        if (!h.getKey().equals("connection"))
          text(clientOut, h.getKey() + ": " + h.getValue() + "\r\n");
      text(clientOut, "Connection: close\r\n\r\n");
      clientOut.flush();
      if (f != null && f.action.equals("stall-body")) {
        f.reached.countDown();if(!f.release.await(10,TimeUnit.SECONDS))throw new IOException("barrier timeout");
      }
      byte[] b = new byte[8192];
      long bound = f != null && f.action.equals("truncate") ? body / 2 : body;
      while (received < bound) {
        int k = serverIn.read(b, 0, (int) Math.min(b.length, bound - received));
        if (k < 0) break;
        received += k;
        if (received == body && upstreamInFlight) {
          // Upstream finished before the last byte can let the client release admission.
          upstreamActive.decrementAndGet();
          upstreamInFlight = false;
        }
        if (chunked) text(clientOut, Integer.toHexString(k) + "\r\n");
        clientOut.write(b, 0, k);
        if (chunked) text(clientOut, "\r\n");
        clientOut.flush();
        sent += k;
      }
      if (chunked && received == body) {
        text(clientOut, "0\r\n\r\n");
        clientOut.flush();
      }
      if (f != null) f.reached.countDown();
    } catch (Exception e) {
      /* Downstream abort is expected; evidence keeps actual counters. */
    } finally {
      if (upstreamInFlight) upstreamActive.decrementAndGet();
      Map<String, String> safe = new LinkedHashMap<>();
      for (String k : List.of("range", "if-match", "if-none-match", "content-length"))
        if (request.containsKey(k)) safe.put(k, request.get(k));
      safe.put("signed", Boolean.toString(request.containsKey("authorization")));
      // Native UploadId and credentials never enter retained events.
      synchronized (events) {
        events.add(
            new Event(
                method,
                path.replaceAll("uploadId=[^& ]+", "uploadId=REDACTED"),
                Map.copyOf(safe),
                status,
                response.entrySet().stream()
                    .filter(
                        e ->
                            Set.of("content-length", "content-range", "x-amz-version-id", "etag")
                                .contains(e.getKey()))
                    .collect(
                        java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                received,
                sent,
                injected));
        active.decrementAndGet();
      }
    }
  }

  void settle() throws InterruptedException {
    settled(() -> null);
  }

  List<Event> snapshot() throws InterruptedException {
    return settled(() -> List.copyOf(events));
  }

  void reset() throws InterruptedException {
    settled(
        () -> {
          events.clear();
          return null;
        });
  }

  private <T> T settled(Supplier<T> action) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (true) {
      synchronized (events) {
        if (active.get() == 0) return action.get();
      }
      if (System.nanoTime() >= deadline)
        throw new AssertionError("proxy still has active exchanges; counters are incomplete");
      Thread.sleep(10);
    }
  }

  @Override
  public void close() throws Exception {
    closed = true;
    listener.close();
    pool.shutdownNow();
    if(!pool.awaitTermination(5, TimeUnit.SECONDS))throw new AssertionError("Owned fix proxy threads did not terminate");
  }
}
