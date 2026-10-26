package ama.transcript;

import ama.contract.IdentityContracts.*;import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;import ama.contract.MinutesContracts.CanonicalBudget;import ama.task.PreparationCancellation;import ama.db.TransactionRunner;import ama.meeting.JdbcMeetingAccess;import ama.storage.*;import ama.task.*;import javax.sql.DataSource;import java.net.URI;import java.nio.file.Path;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import static org.junit.jupiter.api.Assertions.*;

/** Actual registered service/terminal capture. No manufactured Call, marker, lease or reflection. */
public final class Task11B2RejectedTransportProbe implements AutoCloseable {
 public static final String ARCHIVE_PIN="7c9dbd2ffdac3292077a56fb40cf4b22c89ff30f8a90ca4e3f434d06d015be30";
 final ControlledKnowledgeClient client;final JdbcPublicationService service;final Actor actor;final String meeting,publication;final AtomicInteger archiveCalls=new AtomicInteger();
 public Task11B2RejectedTransportProbe(DataSource source,Path identities,int port,String owner,String meeting,String publication)throws Exception {
  this.actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));this.meeting=meeting;this.publication=publication;
  var tx=new TransactionRunner(source,10);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
  var read=new ArchiveReadBudget(65536,1048576,64,128,16,200000,32,255,65536,262144,1048576,65536,32,250000,500000);var canonical=new CanonicalBudget(16777216,32,1000000,2097152,250000);
  var a=JdbcTranscriptAuthority.create(source,tx,access,new TranscriptFilePurposePolicy(),new FileBackendCatalog(source,List.of(),null),Map.of(),read,canonical);
  var outbox=new JdbcOutbox(source,new MinutesEventReferences(source).validators());var queue=new JdbcTaskQueue(source,fences,outbox,Map.of("KNOWLEDGE_PUBLISH_V1",a.authority().publicationTaskPolicy()));
  client=new ControlledKnowledgeClient(identities,URI.create("http://127.0.0.1:"+port),"ISOLATED_LOOPBACK_TEST");
  service=new JdbcPublicationService(a.authority(),()->{archiveCalls.incrementAndGet();throw new AssertionError("Terminal receipt must not open archive");},ARCHIVE_PIN,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256),null,queue,outbox,client);
 }
 public Map<String,Object> fullService()throws Exception{return service.readRejectedReceipt(actor,meeting,publication);}
 public Map<String,Long> traffic(){return client.traffic();}public int archiveCalls(){return archiveCalls.get();}
 static final class Cancel implements PreparationCancellation {
  final AtomicBoolean flag=new AtomicBoolean();final AtomicReference<Runnable> action=new AtomicReference<>();
  public boolean cancelled(){return flag.get();}public AutoCloseable register(Runnable r){action.set(r);if(flag.get())r.run();return ()->action.compareAndSet(r,null);}void cancel(){flag.set(true);var r=action.get();if(r!=null)r.run();}
 }
 /** Test-only exchange controls after genuine terminal capture + registered service authorization. */
 public Map<String,Object> controlled(String mode,Runnable waitForBody)throws Exception {
  var terminal=PublicationRecordedLookup.terminal(service,actor,meeting,publication,"FAILED");terminal.requireOwner();
  var auth=service.authorize(actor,meeting,"RECEIPT_LOOKUP",terminal.capture.budget());
  var call=client.authorized(auth,"RECEIPT_LOOKUP","GET","/v1/knowledge/publications/"+publication+"/executions/"+terminal.reference.executionId(),null);
  var cancel=new Cancel();var done=new CountDownLatch(1);var admission=new CountDownLatch(mode.equals("ADMISSION_CLOSE")?1:0);var invocation=new AtomicReference<java.io.IOException>();var workerFailure=new AtomicReference<Throwable>();var interruptedWorker=new AtomicBoolean();
  Thread thread=new Thread(()->{
   try{admission.await();try{client.exchange(call,65536,cancel);}catch(java.io.IOException expected){invocation.set(expected);}}
   catch(Throwable failure){workerFailure.set(failure);}
   finally{interruptedWorker.set(Thread.currentThread().isInterrupted());done.countDown();}
  },"b6-owned-exchange");
  Throwable primary=null;boolean started=false;boolean[] interrupted={Thread.interrupted()};
  try{
   if(interrupted[0])throw new InterruptedException("Harness interrupted before controlled exchange");
   if(mode.equals("CLOSE_BEFORE"))client.close();thread.start();started=true;
   if(mode.equals("ADMISSION_CLOSE")){client.close();admission.countDown();}
   else if(!mode.equals("CLOSE_BEFORE")){waitForBody.run();if(mode.equals("INTERRUPT")){thread.interrupt();cancel.cancel();}else if(mode.equals("CANCEL"))cancel.cancel();else client.close();}
   assertTrue(done.await(7,TimeUnit.SECONDS),"Controlled exchange did not terminate");
   if(workerFailure.get()!=null)throw new AssertionError("Controlled exchange worker harness failure",workerFailure.get());
   assertNotNull(invocation.get(),"Expected actual IOException from cancelled/closed exchange");if(mode.equals("INTERRUPT"))assertTrue(interruptedWorker.get());
   return Map.of("invocationFailureType",invocation.get().getClass().getName(),"interruptPreserved",interruptedWorker.get(),"qualification","actual terminal/service-authorized exchange control; full service verifier not called");
  }catch(Throwable failure){primary=failure;remember(failure,interrupted);throw failure;}
  finally{
   Throwable cleanup=null;
   try{
    admission.countDown();try{cancel.cancel();}catch(Throwable cancelFailure){cleanup=merge(cleanup,cancelFailure);}
    try{client.close();if(Thread.interrupted()){interrupted[0]=true;throw new InterruptedException("Harness interrupted during control client close");}}catch(Throwable close){remember(close,interrupted);cleanup=merge(cleanup,close);}
    if(started)try{joinOwned(thread,interrupted);}catch(Throwable join){remember(join,interrupted);cleanup=merge(cleanup,join);}
    if(interrupted[0]&&primary==null)cleanup=merge(cleanup,new InterruptedException("Harness interrupted during control cleanup"));
    if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else failWith(cleanup);}
   }finally{if(interrupted[0])Thread.currentThread().interrupt();}
  }
 }
 static Throwable merge(Throwable primary,Throwable next){if(primary==null)return next;if(primary!=next)primary.addSuppressed(next);return primary;}
 static void remember(Throwable failure,boolean[] interrupted){if(Thread.interrupted())interrupted[0]=true;for(var cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof InterruptedException)interrupted[0]=true;}
 static void failWith(Throwable failure)throws Exception {if(failure instanceof Exception exception)throw exception;if(failure instanceof Error error)throw error;throw new AssertionError(failure);}
 static void joinOwned(Thread thread,boolean[] interrupted)throws Exception {
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);Throwable failure=null;
  while(thread.isAlive()&&System.nanoTime()<end)try{TimeUnit.NANOSECONDS.timedJoin(thread,Math.max(1,end-System.nanoTime()));}catch(InterruptedException stop){interrupted[0]=true;failure=merge(failure,stop);}
  if(thread.isAlive())failure=merge(failure,new AssertionError("Owned exchange thread failed bounded join"));if(failure!=null)failWith(failure);
 }

 public void close(){client.close();}
}
