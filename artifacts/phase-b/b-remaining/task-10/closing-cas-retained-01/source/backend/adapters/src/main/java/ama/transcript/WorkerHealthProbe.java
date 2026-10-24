package ama.transcript;
import ama.storage.DeadlineInputStream;
import javax.net.ssl.SSLContext;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.io.*;
import com.fasterxml.jackson.databind.ObjectMapper;
/** Bounded authenticated observation; a configured URL or bean alone is never installed health. */
public final class WorkerHealthProbe implements Supplier<String> {
 private final HttpClient client;private final URI origin;private final String certSha;private final long maxAge;
 public WorkerHealthProbe(URI origin,SSLContext tls,String certSha,long maxAge){
  if(origin==null||!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!Set.of("","/").contains(origin.getPath())||certSha==null||!certSha.matches("[0-9a-f]{64}")||maxAge<5000||maxAge>60000)throw new IllegalArgumentException("Bounded authenticated worker probe required");
  this.origin=origin;this.certSha=certSha;this.maxAge=maxAge;client=HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 }
 public String get(){
  try{
   String nonce=UUID.randomUUID().toString();var request=HttpRequest.newBuilder(origin.resolve("/internal/worker/ready?nonce="+nonce)).timeout(Duration.ofSeconds(2)).GET().build();
   var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
   try(var in=new DeadlineInputStream(response.body(),Duration.ofSeconds(1),()->{try{response.body().close();}catch(IOException failure){throw new UncheckedIOException(failure);}})){
    if(response.statusCode()!=200)return "UNAVAILABLE";
    var certificate=response.sslSession().orElseThrow().getPeerCertificates()[0];if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())).equals(certSha))return "UNAVAILABLE";
    byte[] bytes=in.readNBytes(2049);if(bytes.length>2048)return "UNAVAILABLE";
    var node=new ObjectMapper().readTree(bytes);
    if(node.size()!=8||!node.path("nonce").asText().equals(nonce)||!node.path("boot").asText().matches("[0-9a-f-]{36}")||!node.path("handler").asText().equals("TRANSCRIPT_IMPORT_V1")||!node.path("pool").asText().equals("TRANSCRIPT_IMPORT")||!node.path("profile").asText().equals("ama.strict-utf8-lines.v1")||!node.path("state").asText().equals("RUNNING")||!node.path("ageMillis").canConvertToLong()||node.path("ageMillis").asLong()<0||node.path("ageMillis").asLong()>maxAge||!node.path("active").canConvertToInt()||node.path("active").asInt()<0||node.path("active").asInt()>64)return "UNAVAILABLE";
    return "INSTALLED";
   }
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return "UNAVAILABLE";}catch(Exception unavailable){return "UNAVAILABLE";}
 }
}
