package ama.storage;

import java.io.*;import java.util.*;import java.util.concurrent.TimeUnit;import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;

/** Offline only: fake Process and wait contracts, no fixture constructor, process launch, SQL or file access. */
class Task11B2ExactRetryCleanupTest {
 static final class FakeProcess extends Process {
  boolean alive=true,forced;int destroys,forces,waits;boolean interruptDuringWait,requireForce;
  FakeProcess(boolean interruptDuringWait,boolean requireForce){this.interruptDuringWait=interruptDuringWait;this.requireForce=requireForce;}
  @Override public long pid(){return 12345;}
  @Override public boolean isAlive(){return alive;}
  @Override public void destroy(){destroys++;}
  @Override public Process destroyForcibly(){forced=true;forces++;return this;}
  @Override public boolean waitFor(long value,TimeUnit unit)throws InterruptedException {
   waits++;assertTrue(value>0&&unit.toNanos(value)<=TimeUnit.SECONDS.toNanos(5));
   if(Thread.interrupted())throw new InterruptedException("fake interrupt on entry");
   if(interruptDuringWait){interruptDuringWait=false;throw new InterruptedException("fake interrupt during wait");}
   if(requireForce&&!forced){TimeUnit.NANOSECONDS.sleep(unit.toNanos(value));return false;}
   alive=false;return true;
  }
  @Override public int waitFor(){throw new AssertionError("Unbounded wait forbidden");}
  @Override public int exitValue(){if(alive)throw new IllegalThreadStateException();return forced?137:0;}
  @Override public OutputStream getOutputStream(){throw new AssertionError("No real process IO");}
  @Override public InputStream getInputStream(){throw new AssertionError("No real process IO");}
  @Override public InputStream getErrorStream(){throw new AssertionError("No real process IO");}
 }
 @Test void interruptOnEntryStillJoinsOwnedNativeAndPreservesFlag()throws Exception {
  var process=new FakeProcess(false,false);var custody=new HashMap<String,Object>();Thread.currentThread().interrupt();
  try{assertDoesNotThrow(()->Task11B2SameGraphFixture.stopExactNative(process,custody,()->{}));assertFalse(process.isAlive());assertEquals(1,process.destroys);assertEquals(0,custody.get("nativeExit"));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
 @Test void interruptDuringGracefulWaitStillForcesAndJoins()throws Exception {
  var process=new FakeProcess(true,true);var custody=new HashMap<String,Object>();
  try{assertDoesNotThrow(()->Task11B2SameGraphFixture.stopExactNative(process,custody,()->{}));assertFalse(process.isAlive());assertEquals(1,process.forces);assertTrue(process.waits>=3);assertEquals(137,custody.get("nativeExit"));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
 @Test void forcePathRecordsExitWithoutIntroducingInterrupt()throws Exception {
  var process=new FakeProcess(false,true);var custody=new HashMap<String,Object>();
  try{Task11B2SameGraphFixture.stopExactNative(process,custody,()->{});assertFalse(process.isAlive());assertEquals(1,process.destroys);assertEquals(1,process.forces);assertEquals(137,custody.get("nativeExit"));assertFalse(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
 @Test void logFailureCannotEraseExitCustody()throws Exception {
  var process=new FakeProcess(false,false);var custody=new HashMap<String,Object>();
  try{assertThrows(IOException.class,()->Task11B2SameGraphFixture.stopExactNative(process,custody,()->{throw new IOException("fake log copy failure");}));assertFalse(process.isAlive());assertEquals(12345L,custody.get("nativePid"));assertEquals(false,custody.get("nativeAlive"));assertEquals(0,custody.get("nativeExit"));assertFalse(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
 @Test void clientJoinToleratesEntryInterruptAndPreservesIt()throws Exception {
  var shutdown=new AtomicInteger();var waits=new AtomicInteger();Thread.currentThread().interrupt();
  try{assertDoesNotThrow(()->Task11B2SameGraphFixture.joinExactOwned(shutdown::incrementAndGet,nanos->{waits.incrementAndGet();if(Thread.interrupted())throw new InterruptedException();return true;}));assertEquals(1,shutdown.get());assertTrue(waits.get()>=1);assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
 @Test void executorJoinContinuesAfterInterruptDuringWait()throws Exception {
  var shutdown=new AtomicInteger();var waits=new AtomicInteger();
  try{assertDoesNotThrow(()->Task11B2SameGraphFixture.joinExactOwned(shutdown::incrementAndGet,nanos->{assertTrue(nanos>0&&nanos<=TimeUnit.SECONDS.toNanos(5));if(waits.getAndIncrement()==0)throw new InterruptedException();return true;}));assertEquals(1,shutdown.get());assertEquals(2,waits.get());assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
}
