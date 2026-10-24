package ama.model;
import ama.task.PreparationCancellation;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.time.Duration;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09TransportAbortTest {
 static final class Cancel implements PreparationCancellation {
  volatile boolean stopped;Runnable action;
  public boolean cancelled(){return stopped;}
  public AutoCloseable register(Runnable r){synchronized(this){if(!stopped){assertNull(action);action=r;return ()->{synchronized(this){if(action==r)action=null;}};}}r.run();return ()->{};}
  void cancel(){Runnable r;synchronized(this){stopped=true;r=action;action=null;}if(r!=null)r.run();}
 }
 static final class Held implements AutoCloseable {
  final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));final CountDownLatch accepted=new CountDownLatch(1),closed=new CountDownLatch(1);final ExecutorService worker=Executors.newSingleThreadExecutor();volatile Socket socket;
  Held(boolean headers)throws IOException{worker.submit(()->{try{socket=server.accept();socket.setSoTimeout(5000);var in=socket.getInputStream();int state=0,count=0;while(state<4){int b=in.read();if(b<0||++count>20000)throw new IOException();state=(state==0||state==2)?(b==13?state+1:0):(b==10?state+1:0);}if(headers){socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\nConnection: close\r\n\r\nabc".getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();}accepted.countDown();while(in.read()!=-1){}closed.countDown();}catch(IOException e){closed.countDown();}return null;});}
  public void close()throws Exception{server.close();if(socket!=null)socket.close();worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
 }
 AutoCloseable transport(Held held,long idle)throws Exception {
  Class<?> c=assertDoesNotThrow(()->Class.forName("ama.model.ControlledMinutesHttpTransport"),"controlled transport must physically own abortable sockets");
  return (AutoCloseable)c.getConstructor(URI.class,String.class,String.class,Duration.class,Duration.class,Duration.class).newInstance(URI.create("http://127.0.0.1:"+held.server.getLocalPort()),"task09-transport-test-only-secret-value","CONTROLLED_INSTALLATION",Duration.ofMillis(500),Duration.ofSeconds(2),Duration.ofMillis(idle));
 }
 Object call(Object o,String name,Class<?>[] types,Object...args)throws Exception {try{return o.getClass().getMethod(name,types).invoke(o,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
 Object exchange(Object t,Cancel cancel)throws Exception{return call(t,"exchange",new Class[]{String.class,String.class,Map.class,byte[].class,PreparationCancellation.class,int.class},"GET","/v1/minutes/executions/test",Map.of(),new byte[0],cancel,1024);}
 @Test void cancellationClosesActualSocketWhileWaitingForHeaders()throws Exception {
  try(var held=new Held(false);var t=transport(held,1000);var worker=Executors.newSingleThreadExecutor()){
   var cancel=new Cancel();var result=worker.submit(()->exchange(t,cancel));assertTrue(held.accepted.await(2,TimeUnit.SECONDS));cancel.cancel();assertThrows(ExecutionException.class,()->result.get(1,TimeUnit.SECONDS));assertTrue(held.closed.await(1,TimeUnit.SECONDS));assertEquals(0,call(t,"activeCount",new Class[]{}));
  }
 }
 @Test void cancellationClosesActualSocketDuringBodyReadAndCloseIsIdempotent()throws Exception {
  try(var held=new Held(true);var t=transport(held,1000);var worker=Executors.newSingleThreadExecutor()){
   var cancel=new Cancel();Object response=exchange(t,cancel);var input=(InputStream)call(response,"body",new Class[]{});var result=worker.submit(()->input.readNBytes(100));assertTrue(held.accepted.await(2,TimeUnit.SECONDS));cancel.cancel();assertThrows(ExecutionException.class,()->result.get(1,TimeUnit.SECONDS));((AutoCloseable)response).close();((AutoCloseable)response).close();assertTrue(held.closed.await(1,TimeUnit.SECONDS));assertEquals(0,call(t,"activeCount",new Class[]{}));
  }
 }
 @Test void readIdleDeadlineFiresWithoutAnyReceivedByte()throws Exception {
  try(var held=new Held(false);var t=transport(held,100)){
   long start=System.nanoTime();assertThrows(IOException.class,()->exchange(t,new Cancel()));long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);assertTrue(millis<1500);assertTrue(held.closed.await(1,TimeUnit.SECONDS));assertEquals(0,call(t,"activeCount",new Class[]{}));
  }
 }
}
