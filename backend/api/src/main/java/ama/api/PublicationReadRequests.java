package ama.api;

import ama.task.PreparationCancellation;
import ama.contract.TranscriptContracts.Failure;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.async.WebAsyncTask;

/** Two admitted reads per API process, no queue; each request owns its socket cancellation. */
@org.springframework.stereotype.Component
final class PublicationReadRequests implements AutoCloseable,org.springframework.beans.factory.DisposableBean {
 private final Set<Cancellation> active=new HashSet<>();private boolean closed;
 private final ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor();
 PublicationReadRequests(){executor.setCorePoolSize(2);executor.setMaxPoolSize(2);executor.setQueueCapacity(0);executor.setThreadNamePrefix("publication-read-");executor.initialize();}
 @FunctionalInterface interface Read<T>{T run(PreparationCancellation cancellation)throws Exception;}
 <T> WebAsyncTask<T> start(Read<T> work) {
  var cancellation=new Cancellation();
  synchronized(active){if(closed||active.size()>=2||executor.getActiveCount()>=2)throw unavailable();active.add(cancellation);}
  var task=new WebAsyncTask<T>(30000L,executor,()->{
   synchronized(active){if(closed)throw unavailable();}
   try{cancellation.check();T value=work.run(cancellation);cancellation.check();return value;}
   catch(InterruptedException interrupted){cancellation.cancel();Thread.currentThread().interrupt();throw unavailable();}
   finally{cancellation.cancel();}
  });
  Runnable complete=()->{cancellation.cancel();synchronized(active){active.remove(cancellation);}};
  task.onTimeout(()->{complete.run();throw unavailable();});
  task.onError(()->{complete.run();throw unavailable();});
  task.onCompletion(complete);return task;
 }
 static Failure unavailable(){return new Failure(503,"PUBLICATION_READ_UNAVAILABLE");}
 static final class Cancellation implements PreparationCancellation {
  private boolean stopped;private final Set<Runnable> actions=Collections.newSetFromMap(new IdentityHashMap<>());
  @Override public synchronized boolean cancelled(){return stopped||Thread.currentThread().isInterrupted();}
  @Override public AutoCloseable register(Runnable abort){Objects.requireNonNull(abort);synchronized(this){if(!stopped){actions.add(abort);return ()->{synchronized(this){actions.remove(abort);}};}}abort.run();return ()->{};}
  void cancel(){List<Runnable> owned;synchronized(this){if(stopped)return;stopped=true;owned=List.copyOf(actions);actions.clear();}for(var abort:owned)try{abort.run();}catch(RuntimeException ignored){}}
  void check(){if(cancelled())throw unavailable();}
 }
 @Override public void destroy(){close();}
 @Override public void close(){List<Cancellation> owned;synchronized(active){closed=true;owned=List.copyOf(active);active.clear();}owned.forEach(Cancellation::cancel);var pool=executor.getThreadPoolExecutor();pool.shutdownNow();try{if(!pool.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Publication read executor failed to stop");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Publication read shutdown interrupted",interrupted);}}
}
