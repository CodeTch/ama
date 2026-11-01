package ama.storage;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RevocableInputStreamTest {
 @Test void repeatedCachedProofsCannotExtendTheirActualAuthorityExpiry()throws Exception{
  var source=new Blocked();var checks=new AtomicInteger();var until=java.time.Instant.now().plusMillis(180);
  try(var stream=new RevocableInputStream(source,until,()->{checks.incrementAndGet();return until;},Duration.ofMillis(500),Duration.ofMillis(30))){
   assertTrue(source.closed.await(350,TimeUnit.MILLISECONDS),"Signed authority expiry must cap the stream even while renewal succeeds");
   assertTrue(checks.get()>0);assertThrows(IOException.class,stream::read);
  }
 }
 @Test void validStreamPreservesBytesAndClosesItsSource()throws Exception{
  var closed=new AtomicBoolean();byte[] bytes="saved 中文😀".repeat(1000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var source=new ByteArrayInputStream(bytes){public void close(){closed.set(true);}};
  try(var stream=new RevocableInputStream(source,()->{})){var output=new ByteArrayOutputStream();assertEquals(bytes.length,stream.transferTo(output));assertArrayEquals(bytes,output.toByteArray());}
  assertTrue(closed.get());
 }
 @Test void revokedAccessClosesAnAlreadyBlockedReadWithoutWaitingForAnotherByte()throws Exception{
  var denied=new AtomicBoolean();var source=new Blocked();var executor=Executors.newSingleThreadExecutor();
  try(var stream=new RevocableInputStream(source,()->{if(denied.get())throw new SecurityException();},Duration.ofMillis(200),Duration.ofMillis(50))){
   var read=executor.submit(()->stream.read());assertTrue(source.started.await(1,TimeUnit.SECONDS));long revoked=System.nanoTime();denied.set(true);
   assertTrue(source.closed.await(1,TimeUnit.SECONDS));assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-revoked)<500);
   var failure=assertThrows(ExecutionException.class,()->read.get(1,TimeUnit.SECONDS));assertInstanceOf(IOException.class,failure.getCause());assertThrows(IOException.class,stream::read);
  }finally{source.close();executor.shutdownNow();}
 }
 @Test void stalledAuthorizationExpiresAndLateSuccessCannotReopenTheStream()throws Exception{
  var started=new CountDownLatch(1);var release=new CountDownLatch(1);var source=new Blocked();
  try(var stream=new RevocableInputStream(source,()->{started.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}},Duration.ofMillis(200),Duration.ofMillis(50))){
   assertTrue(started.await(1,TimeUnit.SECONDS));assertTrue(source.closed.await(1,TimeUnit.SECONDS));release.countDown();assertThrows(IOException.class,stream::read);
  }finally{release.countDown();}
 }
 @Test void bytesReadDuringRevocationAreNeverReturned()throws Exception{
  var source=new Blocked();var denied=new AtomicBoolean();var executor=Executors.newSingleThreadExecutor();
  try(var stream=new RevocableInputStream(source,()->{if(denied.get())throw new SecurityException();},Duration.ofMillis(200),Duration.ofMillis(50))){
   var read=executor.submit(()->stream.read(new byte[20]));assertTrue(source.started.await(1,TimeUnit.SECONDS));denied.set(true);assertTrue(source.closed.await(1,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->read.get(1,TimeUnit.SECONDS));
  }finally{source.close();executor.shutdownNow();}
 }
 private static final class Blocked extends InputStream {
  final CountDownLatch started=new CountDownLatch(1),closed=new CountDownLatch(1);
  public int read()throws IOException{started.countDown();try{if(!closed.await(2,TimeUnit.SECONDS))throw new IOException("Test timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}return 42;}
  public int read(byte[] bytes,int offset,int length)throws IOException{if(length==0)return 0;bytes[offset]=(byte)read();return 1;}
  public void close(){closed.countDown();}
 }
}
