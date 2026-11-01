package ama.task;

import ama.transcript.JdbcTranscriptAuthority;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Worker-owned resource recovery. No content or business results can be published here. */
final class SpeechExecutionMaintenance implements AutoCloseable {
 private final AtomicBoolean closed=new AtomicBoolean();private final ControlledSpeechProofClient proofs;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"speech-execution-recovery");t.setDaemon(true);return t;});
 SpeechExecutionMaintenance(JdbcTaskQueue q,JdbcTranscriptAuthority authority,Path runtime){
  var profile=ControlledSpeechProfile.fromRuntime(runtime);proofs=new ControlledSpeechProofClient(profile);
  try{
   var observer=new JdbcSpeechExecutionObserver(q,authority,profile,proofs,closed::get);observer.configure();
   timer.scheduleWithFixedDelay(()->{try{observer.recover(16);}catch(CancellationException stopped){}catch(RuntimeException unavailable){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Speech execution recovery deferred");}},0,1,TimeUnit.SECONDS);
  }catch(RuntimeException|Error failure){timer.shutdownNow();proofs.close();throw failure;}
 }
 @Override public void close(){if(closed.compareAndSet(false,true)){timer.shutdownNow();proofs.close();}}
}
