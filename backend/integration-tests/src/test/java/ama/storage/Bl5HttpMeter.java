package ama.storage;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
/** Test-only transparent bounded forwarding. Both TLS legs validate the fresh CA; native legs use mTLS. */
final class Bl5HttpMeter implements AutoCloseable {
 static final int MAX=16*1024*1024;
 static final Set<String> HOP=Set.of("connection","keep-alive","proxy-authenticate","proxy-authorization","te","trailer","transfer-encoding","upgrade","content-length","expect");
 final HttpServer server;final HttpClient client;final ExecutorService executor=Executors.newFixedThreadPool(2);final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor();
 final Semaphore permits=new Semaphore(2);final Path root;final String kind;final URI target;final boolean secure;
 Bl5HttpMeter(Path root,String kind,URI target,SSLContext incoming,SSLContext outgoing,boolean mutual)throws Exception {
  this.root=root;this.kind=kind;this.target=target;secure=incoming!=null;
  if(!Set.of("127.0.0.1","localhost").contains(target.getHost())||target.getPort()<1||!Set.of("http","https").contains(target.getScheme()))throw new IllegalArgumentException("Owned meter target");
  if(secure){var https=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),4);https.setHttpsConfigurator(new HttpsConfigurator(incoming){public void configure(HttpsParameters p){var ssl=getSSLContext().getDefaultSSLParameters();ssl.setNeedClientAuth(mutual);p.setSSLParameters(ssl);}});server=https;}else server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
  var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1);if(outgoing!=null)builder.sslContext(outgoing);client=builder.build();server.setExecutor(executor);server.createContext("/",this::forward);server.start();
 }
 URI origin(){return URI.create((secure?"https":"http")+"://127.0.0.1:"+server.getAddress().getPort());}
 void forward(HttpExchange exchange)throws java.io.IOException {
  if(!permits.tryAcquire()){exchange.sendResponseHeaders(503,-1);exchange.close();return;}
  final java.util.concurrent.atomic.AtomicReference<java.io.InputStream> responseBody=new java.util.concurrent.atomic.AtomicReference<>();
  final java.util.concurrent.atomic.AtomicReference<CompletableFuture<?>> flight=new java.util.concurrent.atomic.AtomicReference<>();var timeout=deadlines.schedule(()->{var f=flight.get();if(f!=null)f.cancel(true);var body=responseBody.get();if(body!=null)try{body.close();}catch(java.io.IOException ignored){}exchange.close();},30,TimeUnit.SECONDS);
  try{
   Bl5Budget.http(root,kind,exchange.getRequestMethod(),exchange.getRequestURI().getPath());byte[] body=exchange.getRequestBody().readNBytes(MAX+1);if(body.length>MAX)throw new IllegalArgumentException("Meter body budget");
   String path=exchange.getRequestURI().toASCIIString();if(!path.startsWith("/")||path.startsWith("//"))throw new IllegalArgumentException("Meter relative route");
   var request=HttpRequest.newBuilder(URI.create(target.toString()+path)).timeout(Duration.ofSeconds(25));
   exchange.getRequestHeaders().forEach((name,values)->{if(!HOP.contains(name.toLowerCase(Locale.ROOT))&&(!name.equalsIgnoreCase("Host")||kind.equals("s3")))for(String value:values)request.header(name,value);});
   if(!kind.equals("s3"))request.header("Host",target.getAuthority());
   request.method(exchange.getRequestMethod(),body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
   var sent=client.sendAsync(request.build(),HttpResponse.BodyHandlers.ofInputStream());flight.set(sent);var response=sent.get(25,TimeUnit.SECONDS);responseBody.set(response.body());long length=response.headers().firstValueAsLong("content-length").orElse(-1);if(length>MAX)throw new IllegalArgumentException("Meter response budget");
   response.headers().map().forEach((name,values)->{if(!HOP.contains(name.toLowerCase(Locale.ROOT)))exchange.getResponseHeaders().put(name,new ArrayList<>(values));});
   boolean noBody=exchange.getRequestMethod().equals("HEAD")||response.statusCode()==204||response.statusCode()==304;
   if(exchange.getRequestMethod().equals("HEAD"))response.headers().firstValue("content-length").ifPresent(value->exchange.getResponseHeaders().set("Content-Length",value));
   exchange.sendResponseHeaders(response.statusCode(),noBody?-1:length>0?length:0);
   if(!noBody){byte[] chunk=new byte[32768];long total=0;for(int n;(n=response.body().read(chunk))!=-1;){total+=n;if(total>MAX)throw new IllegalArgumentException("Meter response budget");exchange.getResponseBody().write(chunk,0,n);exchange.getResponseBody().flush();}if(length>=0&&total!=length)throw new java.io.IOException("Truncated real upstream body");}
  }catch(Exception failure){try{exchange.sendResponseHeaders(503,-1);}catch(Exception ignored){}}
  finally{timeout.cancel(false);var stream=responseBody.get();if(stream!=null)try{stream.close();}catch(java.io.IOException ignored){}var pending=flight.get();if(pending!=null&&!pending.isDone())pending.cancel(true);exchange.close();permits.release();}
 }
 public void close(){server.stop(0);deadlines.shutdownNow();executor.shutdownNow();client.close();}
}
