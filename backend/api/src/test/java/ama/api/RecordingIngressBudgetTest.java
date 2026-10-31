package ama.api;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecordingIngressBudgetTest {
 static final int FRAME=64080;
 final AtomicLong clock=new AtomicLong();final String session=UUID.randomUUID().toString(),collector=UUID.randomUUID().toString();
 RecordingIngressBudget budget(int keys,long rate,long burst,long globalRate,long globalBurst){return new RecordingIngressBudget(new RecordingIngressBudget.Limits(keys,rate,burst,globalRate,globalBurst,Duration.ofSeconds(30)),clock::get);}
 @Test void burstAndRefillAreBoundedWithoutDiscardingAnyAcceptedAudio(){
  var b=budget(2,FRAME/2,FRAME,FRAME*10,FRAME*10);assertTrue(b.admit("owner",session,collector,FRAME));assertFalse(b.admit("owner",session,collector,FRAME));clock.addAndGet(1_000_000_000L);assertFalse(b.admit("owner",session,collector,FRAME));clock.addAndGet(1_000_000_000L);assertTrue(b.admit("owner",session,collector,FRAME));
 }
 @Test void globalBudgetAndFiniteIdleKeySetCannotBeBypassedByChangingCollector(){
  var b=budget(2,FRAME*10,FRAME*10,FRAME/2,FRAME);assertTrue(b.admit("owner",session,collector,FRAME));assertFalse(b.admit("owner",session,UUID.randomUUID().toString(),FRAME));clock.addAndGet(2_000_000_000L);assertFalse(b.admit("owner",session,UUID.randomUUID().toString(),82));clock.addAndGet(31_000_000_000L);assertTrue(b.admit("owner",session,UUID.randomUUID().toString(),FRAME));
 }
 @Test void simultaneousAdmissionCannotSpendTheSameBurstTwice()throws Exception {
  var b=budget(2,FRAME,FRAME,FRAME*10,FRAME*10);var executor=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);try{var values=new ArrayList<Future<Boolean>>();for(int n=0;n<16;n++)values.add(executor.submit(()->{start.await();return b.admit("owner",session,collector,FRAME);}));start.countDown();int accepted=0;for(var value:values)if(value.get(2,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);}finally{executor.shutdownNow();assertTrue(executor.awaitTermination(2,TimeUnit.SECONDS));}
 }
}
