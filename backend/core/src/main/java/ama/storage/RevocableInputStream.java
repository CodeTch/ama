package ama.storage;

import java.io.*;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A deny-only lifetime for an already freshly authorized stream. Never grants initial access. */
public final class RevocableInputStream extends InputStream {
 private static final Semaphore CAPACITY=new Semaphore(256);
 private static final ScheduledThreadPoolExecutor TIMER;
 static{TIMER=new ScheduledThreadPoolExecutor(2,action->{var thread=new Thread(action,"ama-download-authority-deadline");thread.setDaemon(true);return thread;});TIMER.setRemoveOnCancelPolicy(true);}
 private final InputStream source;private final java.util.function.Supplier<java.time.Instant> authorize;private final long maximumNanos;
 private final AtomicBoolean closed=new AtomicBoolean(),sourceClosed=new AtomicBoolean(),checking=new AtomicBoolean();
 private volatile boolean revoked;private volatile long deadline;private volatile Thread checker;
 private ScheduledFuture<?> expiration,refresh;
 public RevocableInputStream(InputStream source,Runnable authorize)throws IOException{this(source,authorize,Duration.ofSeconds(5),Duration.ofSeconds(2));}
 RevocableInputStream(InputStream source,Runnable authorize,Duration maximum,Duration interval)throws IOException{
  this(source,java.time.Instant.MAX,()->{authorize.run();return java.time.Instant.MAX;},maximum,interval);
 }
 public RevocableInputStream(InputStream source,java.time.Instant initialDeadline,java.util.function.Supplier<java.time.Instant> authorize)throws IOException{this(source,initialDeadline,authorize,Duration.ofSeconds(5),Duration.ofSeconds(2));}
 RevocableInputStream(InputStream source,java.time.Instant initialDeadline,java.util.function.Supplier<java.time.Instant> authorize,Duration maximum,Duration interval)throws IOException{
  this.source=Objects.requireNonNull(source);this.authorize=Objects.requireNonNull(authorize);maximumNanos=maximum.toNanos();
  if(maximumNanos<TimeUnit.MILLISECONDS.toNanos(10)||maximumNanos>TimeUnit.SECONDS.toNanos(5)||interval.isNegative()||interval.isZero()||interval.compareTo(maximum)>=0)throw new IllegalArgumentException("Finite download authorization bounds required");
  Objects.requireNonNull(initialDeadline);
  if(!CAPACITY.tryAcquire()){source.close();throw new IOException("Download capacity unavailable");}
  synchronized(this){long remaining=remaining(initialDeadline);deadline=System.nanoTime()+remaining;expiration=TIMER.schedule(this::expire,remaining,TimeUnit.NANOSECONDS);refresh=TIMER.scheduleAtFixedRate(this::refresh,interval.toNanos(),interval.toNanos(),TimeUnit.NANOSECONDS);}
 }
 private long remaining(java.time.Instant until){var now=java.time.Instant.now();Objects.requireNonNull(until);if(!until.isAfter(now))return 0;if(!until.isBefore(now.plusNanos(maximumNanos)))return maximumNanos;return Duration.between(now,until).toNanos();}
 private void refresh(){
  if(closed.get()||revoked||!checking.compareAndSet(false,true))return;
  Thread thread=Thread.ofVirtual().unstarted(()->{
   try{
    long started=System.nanoTime();if(closed.get()||revoked||started-deadline>=0){revoke();return;}
    var until=authorize.get();
    synchronized(this){long now=System.nanoTime(),remaining=remaining(until);if(closed.get()||revoked)return;if(now-deadline>=0||now-started>=maximumNanos||remaining<=0){revoke();return;}deadline=Math.min(started+maximumNanos,now+remaining);expiration.cancel(false);expiration=TIMER.schedule(this::expire,Math.max(0,deadline-now),TimeUnit.NANOSECONDS);}
   }catch(Throwable failure){revoke();}
   finally{checking.set(false);}
  });checker=thread;thread.start();if(closed.get()||revoked)thread.interrupt();
 }
 private synchronized void expire(){if(closed.get()||revoked)return;long remaining=deadline-System.nanoTime();if(remaining>0){expiration=TIMER.schedule(this::expire,remaining,TimeUnit.NANOSECONDS);return;}revoke();}
 private synchronized void revoke(){
  if(revoked||closed.get())return;revoked=true;if(expiration!=null)expiration.cancel(false);if(refresh!=null)refresh.cancel(false);Thread active=checker;if(active!=null&&active!=Thread.currentThread())active.interrupt();
  // Timer threads must never wait for a storage transport's close operation.
  Thread.ofVirtual().start(()->{try{closeSource();}catch(IOException ignored){/* Access stays denied even if transport cleanup fails. */}});
 }
 private void check()throws IOException{if(!closed.get()&&!revoked&&System.nanoTime()-deadline>=0)revoke();if(closed.get()||revoked)throw new IOException("Download authorization expired or stream closed");}
 @Override public int read()throws IOException{check();int value=source.read();check();return value;}
 @Override public int read(byte[] bytes,int offset,int length)throws IOException{Objects.checkFromIndexSize(offset,length,bytes.length);check();int value=source.read(bytes,offset,length);check();return value;}
 @Override public int available()throws IOException{check();int count=source.available();check();return count;}
 @Override public long transferTo(OutputStream output)throws IOException{Objects.requireNonNull(output);byte[] bytes=new byte[8192];long total=0;for(int size;(size=read(bytes))!=-1;){check();output.write(bytes,0,size);total=Math.addExact(total,size);}return total;}
 private void closeSource()throws IOException{if(sourceClosed.compareAndSet(false,true))source.close();}
 @Override public void close()throws IOException{
  if(!closed.compareAndSet(false,true))return;
  synchronized(this){if(expiration!=null)expiration.cancel(false);if(refresh!=null)refresh.cancel(false);}
  Thread active=checker;if(active!=null&&active!=Thread.currentThread())active.interrupt();
  try{closeSource();}finally{CAPACITY.release();}
 }
}
