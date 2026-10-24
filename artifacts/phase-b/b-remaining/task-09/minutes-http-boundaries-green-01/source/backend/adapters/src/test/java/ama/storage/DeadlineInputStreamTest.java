package ama.storage;
import java.io.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeadlineInputStreamTest {
 @Test void normalEofCloseAndWatchdogReleaseExactlyOnce()throws Exception{
  for(int n=0;n<30;n++){
   var releases=new AtomicInteger();var closed=new CountDownLatch(1);var started=new CountDownLatch(1);
   var source=new InputStream(){public int read()throws IOException{started.countDown();try{closed.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}throw new IOException("closed");}};
   var body=new DeadlineInputStream(source,Duration.ofMillis(15),()->{releases.incrementAndGet();closed.countDown();});var reader=Executors.newSingleThreadExecutor();
   try{var future=reader.submit(()->body.read());assertTrue(started.await(1,TimeUnit.SECONDS));if(n%2==0)body.close();assertThrows(ExecutionException.class,()->future.get(1,TimeUnit.SECONDS));body.close();assertEquals(1,releases.get());}
   finally{body.close();reader.shutdownNow();assertTrue(reader.awaitTermination(1,TimeUnit.SECONDS));}
  }
  var releases=new AtomicInteger();try(var body=new DeadlineInputStream(new ByteArrayInputStream(new byte[]{1}),Duration.ofSeconds(1),releases::incrementAndGet)){assertEquals(1,body.read());assertEquals(-1,body.read());assertEquals(-1,body.read());}assertEquals(1,releases.get());
 }
}
