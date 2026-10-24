package ama.worker;
import ama.task.ExternalOperationPort;
import ama.contract.ExternalContracts.OperationToken;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Bounded independent five-second operation renewal; never grants/reissues send authority. */
public final class ExternalOperationHeartbeat implements AutoCloseable {
 private final ExternalOperationPort operations;private final ScheduledExecutorService scheduler;private final Semaphore slots;private final boolean ownsScheduler;
 public ExternalOperationHeartbeat(ExternalOperationPort operations,int capacity){this(operations,capacity,Executors.newScheduledThreadPool(positive(capacity),r->new Thread(r,"ama-operation-heartbeat")),true);}
 public ExternalOperationHeartbeat(ExternalOperationPort operations,int capacity,ScheduledExecutorService scheduler){this(operations,capacity,scheduler,false);}
 private ExternalOperationHeartbeat(ExternalOperationPort operations,int capacity,ScheduledExecutorService scheduler,boolean owns){this.operations=java.util.Objects.requireNonNull(operations);slots=new Semaphore(positive(capacity));this.scheduler=java.util.Objects.requireNonNull(scheduler);ownsScheduler=owns;}
 private static int positive(int capacity){if(capacity<1)throw new IllegalArgumentException("Explicit positive heartbeat capacity required");return capacity;}
 public final class Tracked implements AutoCloseable {
  private final AtomicBoolean lost=new AtomicBoolean();private final AtomicBoolean closed=new AtomicBoolean();private final ScheduledFuture<?> future;
  private Tracked(Runnable tick){future=scheduler.scheduleWithFixedDelay(tick,5,5,TimeUnit.SECONDS);}
  private Tracked(OperationToken token){future=scheduler.scheduleWithFixedDelay(()->{if(lost.get())return;try{if(!operations.renew(token))lost.set(true);}catch(RuntimeException failure){lost.set(true);}},5,5,TimeUnit.SECONDS);}
  public boolean lost(){return lost.get();}
  public void close(){if(closed.compareAndSet(false,true)){future.cancel(false);slots.release();}}
 }
 public Tracked track(OperationToken token){if(!slots.tryAcquire())throw new IllegalStateException("Operation heartbeat capacity exhausted");try{return new Tracked(token);}catch(RuntimeException failure){slots.release();throw failure;}}
 Tracked track(Runnable tick){if(!slots.tryAcquire())throw new IllegalStateException("Operation heartbeat capacity exhausted");try{return new Tracked(tick);}catch(RuntimeException failure){slots.release();throw failure;}}
 public void close(){if(ownsScheduler)scheduler.shutdownNow();}
}
