package ama.worker;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
/** Explicit separate JVM. Without an installed reviewed domain provider startup fails closed. */
public final class WorkerMain {
 private WorkerMain(){}
 public static void main(String[] args)throws Exception{
  var providers=ServiceLoader.load(WorkerRuntimeProvider.class).stream().toList();
  if(providers.size()!=1)throw new IllegalStateException("Exactly one reviewed worker runtime provider is required");
  run(providers.getFirst().get().create());
 }
 public static void run(WorkerRuntime runtime)throws Exception{
  try(var loop=runtime){
   Runtime.getRuntime().addShutdownHook(new Thread(loop::close,"ama-worker-shutdown"));
   loop.start();new CountDownLatch(1).await();
  }
 }
}
