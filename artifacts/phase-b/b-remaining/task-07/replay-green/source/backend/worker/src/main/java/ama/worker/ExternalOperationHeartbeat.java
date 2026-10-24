package ama.worker;
import ama.task.ExternalOperationPort;
import ama.contract.ExternalContracts.OperationToken;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Bounded independent five-second operation renewal; never grants/reissues send authority. */
public final class ExternalOperationHeartbeat implements AutoCloseable {
 private final ExternalOperationPort operations;private final ScheduledExecutorService scheduler;private final Semaphore slots;
 public ExternalOperationHeartbeat(ExternalOperationPort operations,int capacity){if(capacity<1)throw new IllegalArgumentException("Explicit positive heartbeat capacity required");this.operations=operations;slots=new Semaphore(capacity);scheduler=Executors.newScheduledThreadPool(capacity,r->new Thread(r,"ama-operation-heartbeat"));}
 public final class Tracked implements AutoCloseable {
  private final AtomicBoolean lost=new AtomicBoolean();private final AtomicBoolean closed=new AtomicBoolean();private final ScheduledFuture<?> future;
  private Tracked(OperationToken token){future=scheduler.scheduleWithFixedDelay(()->{if(lost.get())return;try{if(!operations.renew(token))lost.set(true);}catch(RuntimeException failure){lost.set(true);}},5,5,TimeUnit.SECONDS);}
  public boolean lost(){return lost.get();}
  public void close(){if(closed.compareAndSet(false,true)){future.cancel(false);slots.release();}}
 }
 public Tracked track(OperationToken token){if(!slots.tryAcquire())throw new IllegalStateException("Operation heartbeat capacity exhausted");try{return new Tracked(token);}catch(RuntimeException failure){slots.release();throw failure;}}
 public void close(){scheduler.shutdownNow();}
}
