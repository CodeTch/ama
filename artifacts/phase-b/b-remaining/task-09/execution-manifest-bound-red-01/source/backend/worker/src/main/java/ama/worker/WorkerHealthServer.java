package ama.worker;
import com.sun.net.httpserver.*;
import javax.net.ssl.SSLContext;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
/** Private mTLS observation of the actual loop, without queue scanning or source reads. */
public final class WorkerHealthServer implements AutoCloseable {
 private final HttpsServer server;private final ExecutorService executor;private final String boot=UUID.randomUUID().toString();
 public WorkerHealthServer(InetSocketAddress address,SSLContext tls,String peer,Supplier<WorkerLoop.Observation> observation)throws java.io.IOException {
  if(!address.getAddress().isLoopbackAddress()||peer==null||!peer.startsWith("spiffe://"))throw new IllegalArgumentException("Private worker management binding required");
  server=HttpsServer.create(address,8);server.setHttpsConfigurator(new HttpsConfigurator(tls){public void configure(HttpsParameters p){var parameters=tls.getDefaultSSLParameters();parameters.setNeedClientAuth(true);p.setSSLParameters(parameters);}});
  executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"ama-worker-health");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());server.setExecutor(executor);
  server.createContext("/internal/worker/ready",exchange->{try {
   boolean allowed=false;
   if(exchange instanceof HttpsExchange https){var cert=(X509Certificate)https.getSSLSession().getPeerCertificates()[0];var names=cert.getSubjectAlternativeNames();if(names!=null)allowed=names.stream().anyMatch(n->n.get(0).equals(6)&&peer.equals(n.get(1)));}
   String nonce=exchange.getRequestURI().getRawQuery();
   if(!allowed||!exchange.getRequestMethod().equals("GET")||nonce==null||!nonce.matches("nonce=[0-9a-f-]{36}")||exchange.getRequestHeaders().containsKey("Transfer-Encoding")||exchange.getRequestHeaders().getFirst("Content-Length")!=null){exchange.sendResponseHeaders(403,-1);return;}
   var o=observation.get();String body="{\"boot\":\""+boot+"\",\"nonce\":\""+nonce.substring(6)+"\",\"handler\":\"TRANSCRIPT_IMPORT_V1\",\"pool\":\"TRANSCRIPT_IMPORT\",\"profile\":\"ama.strict-utf8-lines.v1\",\"state\":\""+o.state()+"\",\"ageMillis\":"+o.progressAgeMillis()+",\"active\":"+o.active()+"}";
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
  }catch(Exception denied){try{exchange.sendResponseHeaders(503,-1);}catch(Exception ignored){}}finally{exchange.close();}});
 }
 public void start(){server.start();}public int port(){return server.getAddress().getPort();}
 public void close(){server.stop(0);executor.shutdownNow();}
}
