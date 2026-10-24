package ama.task;
import ama.contract.TaskContracts.*;
import ama.contract.StorageContracts.StorageKey;
import ama.storage.LocalFileClient;
import ama.storage.internal.ServiceTls;
import com.sun.net.httpserver.*;
import java.net.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual queue/Worker cancellation and actual mTLS body. Synthetic TEST_INFO task is only a scheduler boundary fixture. */
class Task05CancellationIT {
 @Test void closeAndActualRenewalLossAbortReaderBeforeFixtureRelease()throws Exception {
  for(boolean renewalLoss:List.of(false,true)){
   var f=new TaskFixture();var q=f.queue();var spec=f.spec();String task=q.enqueue(spec);
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var readerExited=new CountDownLatch(1);var sourceClosed=new CountDownLatch(1);var aborts=new AtomicInteger();
   Path p=Path.of(System.getenv("AMA_TASK05_ROOT"),"pki");char[] password=Files.readString(p.resolve("keystore-password")).toCharArray();
   var tls=ServiceTls.context(p.resolve("server.p12"),password,p.resolve("trust.p12"),password);
   var server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),2);server.setHttpsConfigurator(new HttpsConfigurator(tls){public void configure(HttpsParameters h){var ssl=tls.getDefaultSSLParameters();ssl.setNeedClientAuth(true);h.setSSLParameters(ssl);}});
   var serverPool=Executors.newSingleThreadExecutor();server.setExecutor(serverPool);server.createContext("/internal/v1/",exchange->{try{exchange.sendResponseHeaders(206,64);exchange.getResponseBody().write('A');exchange.getResponseBody().flush();release.await(20,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();sourceClosed.countDown();}});server.start();
   var client=new LocalFileClient(URI.create("https://localhost:"+server.getAddress().getPort()),ServiceTls.context(p.resolve("writer.p12"),password,p.resolve("trust.p12"),password),Duration.ofSeconds(30));
   var leaseSeen=new AtomicReference<TaskLease>();
   try(var loop=ama.worker.WorkerLoop.contextual(q,Map.of(spec.pool(),1),Map.of(spec.type(),(lease,cancel)->{
    leaseSeen.set(lease);
    try(var input=client.read(new StorageKey("local","test","value"),0,64);var registration=cancel.register(()->{aborts.incrementAndGet();try{input.close();}catch(java.io.IOException e){throw new IllegalStateException(e);}})){
     entered.countDown();input.readAllBytes();throw new AssertionError("Body should remain stalled");
    }finally{readerExited.countDown();if(renewalLoss)new CountDownLatch(1).await(10,TimeUnit.SECONDS);}
   }),"TASK05_CANCEL")){
    loop.start();assertTrue(entered.await(3,TimeUnit.SECONDS));
    if(renewalLoss)f.expire(leaseSeen.get());else loop.close();
    assertTrue(readerExited.await(7,TimeUnit.SECONDS),"Actual reader exits before 30-second transport deadline");assertEquals(1,release.getCount());assertEquals(1,aborts.get());
    assertEquals("RUNNING",f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertNull(f.j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,task));
   }finally{release.countDown();assertTrue(sourceClosed.await(2,TimeUnit.SECONDS));server.stop(0);serverPool.shutdownNow();assertTrue(serverPool.awaitTermination(2,TimeUnit.SECONDS));}
   // End this isolated synthetic scheduler fixture through the ordinary real queue after observation.
   var lease=leaseSeen.get();if(renewalLoss)lease=q.claim(spec.pool(),"TASK05_FIXTURE_END").orElseThrow();
   assertTrue(q.complete(lease,new TaskResult(Outcome.FAILED,null,"TASK05_FIXTURE_END")));
  }
 }
}
