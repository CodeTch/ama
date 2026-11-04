package ama.meeting;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
/** Bounded external preparation outside caller transactions; never a content authorization. */
public final class WorkspaceReadPreparation {
 private WorkspaceReadPreparation(){}
 private static final ExecutorService POOL=new ThreadPoolExecutor(8,8,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"workspace-authority-prepare");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public static void run(List<String> meetings,WorkspaceReadBudget budget,Consumer<String> prepare){
  if(meetings.size()>128)throw new WorkspaceReadBudget.Unavailable();
  var jobs=new ArrayList<Future<?>>();
  try{
   for(String meeting:meetings){budget.check();jobs.add(POOL.submit(()->{budget.check();prepare.accept(meeting);}));}
   for(var job:jobs){job.get(budget.remainingNanos(),TimeUnit.NANOSECONDS);budget.check();}
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new WorkspaceReadBudget.Unavailable(interrupted);}
  catch(ExecutionException failed){if(failed.getCause() instanceof RuntimeException runtime)throw runtime;throw new WorkspaceReadBudget.Unavailable(failed);}
  catch(TimeoutException|RejectedExecutionException unavailable){throw new WorkspaceReadBudget.Unavailable(unavailable);}
  finally{for(var job:jobs)if(!job.isDone())job.cancel(true);}
 }
}
