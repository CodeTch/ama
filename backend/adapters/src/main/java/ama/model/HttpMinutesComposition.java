package ama.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import ama.config.CanonicalJson;
import static ama.config.WireValues.*;

/** Explicit sibling-harness integration boundary; semantic quality is not attested. */
public final class HttpMinutesComposition implements AutoCloseable {
 public static final class Rejected extends IOException {Rejected(){super("Minutes input rejected by harness");}}
 private final URI origin;
 private final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"minutes-composition-deadline");t.setDaemon(true);return t;});
 public HttpMinutesComposition(URI origin){if(!"http://127.0.0.1:8792".equals(origin.toString()))throw new IllegalArgumentException("Minutes integration must use the fixed sibling harness origin");this.origin=origin;}
 public byte[] execute(ControlledMinutesProtocol.VerifiedRequest request)throws IOException{
  var outbound=HttpRequest.newBuilder(origin.resolve("/v1/minutes/compositions/"+request.executionId())).timeout(Duration.ofSeconds(90)).header("Content-Type","application/json").header("X-Ama-Input-Fingerprint",request.inputFingerprint()).header("X-Ama-Payload-Sha256",request.payloadSha256()).POST(HttpRequest.BodyPublishers.ofByteArray(request.payload())).build();
  try{
   var response=client.send(outbound,HttpResponse.BodyHandlers.ofInputStream());
   try(var body=response.body()){
    var deadline=deadlines.schedule(()->{try{body.close();}catch(IOException ignored){}},90,TimeUnit.SECONDS);
    try{if(response.statusCode()==400||response.statusCode()==422)throw new Rejected();if(response.statusCode()!=200)throw new IOException("Minutes harness returned HTTP "+response.statusCode());byte[] bytes=body.readNBytes(ControlledMinutesStore.MAX_OUTPUT+1);if(bytes.length>ControlledMinutesStore.MAX_OUTPUT)throw new IllegalArgumentException("Minutes response exceeds budget");return verify(request.inputFingerprint(),bytes);}finally{deadline.cancel(false);}
   }
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Minutes request interrupted",interrupted);}
 }
 public static byte[] verify(String fingerprint,byte[] result){
  try{var value=BoundedModelJson.parse(result,ControlledMinutesStore.MAX_OUTPUT);var values=value.entries().stream().collect(java.util.stream.Collectors.toMap(ama.canonical.CanonicalValue.Entry::key,ama.canonical.CanonicalValue.Entry::value));if(!"ama.minutes-output.v2".equals(text(values.get("schemaVersion")))||!fingerprint.equals(text(values.get("inputFingerprint"))))throw new IllegalArgumentException("Minutes response does not bind the requested input");return CanonicalJson.encode(value,ControlledMinutesStore.MAX_OUTPUT,()->false);}
  catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid minutes composition response",invalid);}
 }
 public void close(){client.shutdownNow();deadlines.shutdownNow();}
}
