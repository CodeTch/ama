package ama.storage;
import java.io.*;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** A finite transport lifetime, installed before metadata or error bodies are consumed. */
public final class DeadlineInputStream extends InputStream {
 private enum State { OPEN,EOF_CLOSED,CLOSED,ABORTED }
 private static final Semaphore CAPACITY=new Semaphore(1024);
 private static final ScheduledThreadPoolExecutor TIMER;
 static {TIMER=new ScheduledThreadPoolExecutor(2,r->{var t=new Thread(r,"ama-body-deadline");t.setDaemon(true);return t;});TIMER.setRemoveOnCancelPolicy(true);}
 private final InputStream source;private final Runnable release;private final long deadline;
 private final AtomicReference<State> state=new AtomicReference<>(State.OPEN);
 private volatile ScheduledFuture<?> timer;private volatile IOException closeFailure;
 public DeadlineInputStream(InputStream source,Duration lifetime,Runnable release){
  this.source=Objects.requireNonNull(source);this.release=Objects.requireNonNull(release);
  if(lifetime==null||lifetime.isNegative()||lifetime.isZero()||lifetime.compareTo(Duration.ofHours(1))>0){release.run();throw new IllegalArgumentException("Finite stream lifetime required");}
  deadline=System.nanoTime()+lifetime.toNanos();
  if(!CAPACITY.tryAcquire()){release.run();throw new IllegalStateException("Body lifetime capacity exhausted");}
  timer=TIMER.schedule(()->terminate(State.ABORTED),lifetime.toNanos(),TimeUnit.NANOSECONDS);
 }
 private void terminate(State terminal){
  if(!state.compareAndSet(State.OPEN,terminal))return;
  var scheduled=timer;if(scheduled!=null)scheduled.cancel(false);
  try{release.run();}catch(RuntimeException failure){closeFailure=new IOException("STORAGE_UNAVAILABLE");}finally{CAPACITY.release();}
 }
 private void check()throws IOException{
  if(System.nanoTime()-deadline>=0&&state.get()==State.OPEN)terminate(State.ABORTED);
  if(state.get()==State.ABORTED||state.get()==State.CLOSED||closeFailure!=null)throw new IOException("STORAGE_UNAVAILABLE");
 }
 @Override public int read()throws IOException{byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255;}
 @Override public int read(byte[] b,int off,int len)throws IOException{
  Objects.checkFromIndexSize(off,len,b.length);if(len==0)return 0;check();if(state.get()==State.EOF_CLOSED)return -1;
  try{
   int n=source.read(b,off,len);check();
   if(n<0){terminate(State.EOF_CLOSED);check();}return n;
  }catch(IOException|RuntimeException failure){terminate(State.ABORTED);throw new IOException("STORAGE_UNAVAILABLE");}
 }
 @Override public void close()throws IOException{terminate(State.CLOSED);if(closeFailure!=null)throw closeFailure;}
}
