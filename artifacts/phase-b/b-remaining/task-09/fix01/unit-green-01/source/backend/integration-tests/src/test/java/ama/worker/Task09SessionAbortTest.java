package ama.worker;
import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import ama.task.*;
import ama.model.ControlledMinutesHttpTransport;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.time.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real session/physical-socket cancellation tests. Port doubles do not prove DB send admission. */
class Task09SessionAbortTest {
 static String id(){return UUID.randomUUID().toString();}
 static final class Session implements AutoCloseable {
  final Object monitor=new Object();final AtomicInteger settlements=new AtomicInteger();final AtomicBoolean renewal=new AtomicBoolean(true);final ExternalOperationHeartbeat heartbeat;final ExternalExecutionSession session;
  Session(){var subject=new SubjectRef("MEETING",id());var token=new OperationToken(id(),id(),1,1,"TASK09",Instant.now().plusSeconds(30),subject);var spec=new TaskSpec("MINUTES_GENERATE_V1","MINUTES_GENERATE",subject,"a".repeat(64),new TypedRef("GENERATION_INPUT",id()),"b".repeat(64),1,0,0,3);var lease=new TaskLease(id(),1,"TASK09",Instant.now().plusSeconds(20),spec);
   var operations=(ExternalOperationPort)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ExternalOperationPort.class},(p,m,a)->switch(m.getName()){case "beginPrepared"->Optional.of(token);case "renew"->renewal.get();default->throw new UnsupportedOperationException(m.getName());});
   var permits=(ModelPermitPool)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ModelPermitPool.class},(p,m,a)->{if(m.getName().equals("acquire"))return Optional.of(new Permit(id(),"CONTROLLED_MINUTES_V1",token.executionId(),1,null));if(m.getName().equals("settled")){settlements.incrementAndGet();return true;}throw new UnsupportedOperationException();});
   heartbeat=new ExternalOperationHeartbeat(operations,1);session=new ExternalExecutionSession(monitor,lease,operations,permits,heartbeat,"CONTROLLED_MINUTES_V1",()->true,()->true);
  }
  PreparationCancellation cancellation(){return assertDoesNotThrow(()->(PreparationCancellation)ExternalTaskExecution.class.getMethod("cancellation").invoke(session));}
  public void close(){session.close();heartbeat.close();}
 }
 static final class Held implements AutoCloseable {
  final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));final CountDownLatch accepted=new CountDownLatch(1),closed=new CountDownLatch(1);final ExecutorService worker=Executors.newSingleThreadExecutor();volatile Socket socket;
  Held(boolean body)throws Exception{worker.submit(()->{try{socket=server.accept();socket.setSoTimeout(5000);var input=socket.getInputStream();int state=0,n=0;while(state<4){int b=input.read();if(b<0||++n>16384)throw new IOException();state=(state==0||state==2)?(b==13?state+1:0):(b==10?state+1:0);}if(body){socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\nConnection: close\r\n\r\nabc".getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();}accepted.countDown();if(input.read()==-1)closed.countDown();}catch(IOException ignored){}return null;});}
  public void close()throws Exception{server.close();if(socket!=null)socket.close();worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
 }
 void physical(boolean body,boolean renewLoss)throws Exception {
  try(var s=new Session();var held=new Held(body);var transport=new ControlledMinutesHttpTransport(URI.create("http://127.0.0.1:"+held.server.getLocalPort()),"task09-session-only-controlled-test-secret","CONTROLLED_INSTALLATION",Duration.ofMillis(500),Duration.ofSeconds(3),Duration.ofSeconds(2));var executor=Executors.newSingleThreadExecutor()){
   var cancellation=s.cancellation();assertTrue(s.session.begin(new ExternalOperationPort.PreparedRequest(){}));var result=executor.submit(()->s.session.send(()->{try(var response=transport.exchange("GET","/v1/minutes/executions/test",Map.of(),new byte[0],cancellation,1024)){response.body().readAllBytes();throw new AssertionError("fixture response must remain incomplete");}}));
   assertTrue(held.accepted.await(2,TimeUnit.SECONDS));if(renewLoss){s.renewal.set(false);s.session.renew();}else s.session.close();assertThrows(ExecutionException.class,()->result.get(1,TimeUnit.SECONDS));assertTrue(held.closed.await(1,TimeUnit.SECONDS));assertEquals(0,transport.activeCount());assertEquals(0,s.settlements.get(),"Unknown possibly-sent execution retains global permit");assertTrue(cancellation.cancelled());assertFalse(s.session.send(()->{throw new AssertionError("one-shot");}));
  }
 }
 @Test void closePhysicallyAbortsHeldHeadersAndBodyWithoutSettlingUnknownPermit()throws Exception {physical(false,false);physical(true,false);}
 @Test void renewalLossPhysicallyAbortsHeldHeadersAndBodyWithoutRegainingSend()throws Exception {physical(false,true);physical(true,true);}
 @Test void singleRegistrationLateCancellationAndAbortRunOutsideMonitor()throws Exception {
  try(var s=new Session()) {var view=s.cancellation();var calls=new AtomicInteger();AutoCloseable registration=view.register(()->{assertFalse(Thread.holdsLock(s.monitor));calls.incrementAndGet();});assertThrows(IllegalStateException.class,()->view.register(()->{}));s.session.close();s.session.close();registration.close();assertEquals(1,calls.get());view.register(()->{assertFalse(Thread.holdsLock(s.monitor));calls.incrementAndGet();}).close();assertEquals(2,calls.get());assertTrue(view.cancelled());}
 }
}
