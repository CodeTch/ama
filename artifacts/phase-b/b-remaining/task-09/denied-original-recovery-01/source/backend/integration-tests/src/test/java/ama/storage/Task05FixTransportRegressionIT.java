package ama.storage;
import ama.storage.internal.ServiceTls;
import ama.contract.StorageContracts.*;
import com.sun.net.httpserver.*;
import java.nio.file.*;
import java.net.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task05FixTransportRegressionIT {
 static javax.net.ssl.SSLContext tls(String identity)throws Exception{
  Path p=Path.of(System.getenv("AMA_TASK05_ROOT"),"pki");char[] password=Files.readString(p.resolve("keystore-password")).toCharArray();
  return ServiceTls.context(p.resolve(identity+".p12"),password,p.resolve("trust.p12"),password);
 }
 @Test void authenticatedActualLoopHealthRequiresRunningFreshMatchingPeer()throws Exception{
  var source=ama.support.MysqlTestDatabase.dataSource();var fences=new ama.task.JdbcSubjectFences(source,java.util.Set.of("MEETING"));var queue=new ama.task.JdbcTaskQueue(source,fences,new ama.task.JdbcOutbox(source,java.util.Map.of()),java.util.Map.of());
  try(var loop=new ama.worker.WorkerLoop(queue,java.util.Map.of("TASK05_HEALTH_EMPTY",1),java.util.Map.of(),"TASK05_HEALTH");var server=new ama.worker.WorkerHealthServer(new InetSocketAddress("127.0.0.1",0),tls("server"),"spiffe://ama/writer",loop::observation)){
   server.start();Path cert=Path.of(System.getenv("AMA_TASK05_ROOT"),"pki","server.crt");java.security.cert.Certificate certificate;try(var in=Files.newInputStream(cert)){certificate=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
   String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
   URI origin=URI.create("https://localhost:"+server.port());var probe=new ama.transcript.WorkerHealthProbe(origin,tls("writer"),sha,10000);
   assertEquals("UNAVAILABLE",probe.get());loop.start();
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(loop.observation().state().equals("STARTING")&&System.nanoTime()<end)Thread.onSpinWait();
   assertEquals("INSTALLED",probe.get());
   assertEquals("UNAVAILABLE",new ama.transcript.WorkerHealthProbe(origin,tls("outsider"),sha,10000).get());
   assertEquals("UNAVAILABLE",new ama.transcript.WorkerHealthProbe(origin,tls("writer"),"0".repeat(64),10000).get());
   loop.close();assertEquals("UNAVAILABLE",probe.get());
  }
 }
 @Test void localMetadataAndErrorBodiesActuallyExitBeforeFixtureRelease()throws Exception{
  for(int status:new int[]{200,503,206}){
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var exited=new CountDownLatch(1);
   var server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),2);var context=tls("server");
   server.setHttpsConfigurator(new HttpsConfigurator(context){public void configure(HttpsParameters p){var ssl=context.getDefaultSSLParameters();ssl.setNeedClientAuth(true);p.setSSLParameters(ssl);}});
   var pool=Executors.newSingleThreadExecutor();server.setExecutor(pool);
   server.createContext("/internal/v1/",exchange->{
    try{assertTrue(exchange instanceof HttpsExchange);exchange.sendResponseHeaders(status,64);exchange.getResponseBody().write('{');exchange.getResponseBody().flush();entered.countDown();release.await(10,TimeUnit.SECONDS);}
    catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();exited.countDown();}
   });server.start();
   var reader=Executors.newSingleThreadExecutor();
   try{
    var client=new LocalFileClient(URI.create("https://localhost:"+server.getAddress().getPort()),tls("writer"),Duration.ofMillis(350));
    var task=reader.submit(()->{if(status==206){try(var body=client.read(new StorageKey("local","test","value"),0,64)){return body.readAllBytes();}}return client.stat(new StorageKey("local","test","value"));});
    assertTrue(entered.await(3,TimeUnit.SECONDS));
    assertThrows(ExecutionException.class,()->task.get(2,TimeUnit.SECONDS),"Actual reader must terminate while body remains stalled");
    assertEquals(1,release.getCount(),"Fixture release must not cause the asserted termination");
   }finally{release.countDown();assertTrue(exited.await(3,TimeUnit.SECONDS));server.stop(0);pool.shutdownNow();reader.shutdownNow();assertTrue(reader.awaitTermination(2,TimeUnit.SECONDS));}
  }
 }
}
