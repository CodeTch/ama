package ama.task;

import ama.worker.*;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import ama.task.ExternalOperationPort.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Barriers wrap real queue/operation transactions. No mocked DB outcomes. */
class Task07ExternalWorkerIT {
 final Task07PreparedExternalIT f=new Task07PreparedExternalIT();
 JdbcExternalOperations actual;JdbcModelPermitPool permits;String model;WorkerLoop worker;
 final AtomicInteger sends=new AtomicInteger(),ordinary=new AtomicInteger(),renews=new AtomicInteger(),observations=new AtomicInteger(),markers=new AtomicInteger();
 final AtomicReference<ExternalTaskExecution> session=new AtomicReference<>();final AtomicReference<Throwable> error=new AtomicReference<>();final CountDownLatch done=new CountDownLatch(1);
 Runnable beforeBegin=()->{},afterBegin=()->{},beforeRenew=()->{},afterObserve=()->{};boolean loseRenew;
 final ExternalOperationPort port=new ExternalOperationPort(){
  public Optional<OperationToken> beginExternal(TaskLease l,ExternalRequest r){return actual.beginExternal(l,r);}
  public Optional<OperationToken> beginPrepared(TaskLease l,PreparedRequest r){beforeBegin.run();var value=actual.beginPrepared(l,r);markers.incrementAndGet();afterBegin.run();return value;}
  public Optional<OperationToken> claimReconciliation(String o){return actual.claimReconciliation(o);}
  public boolean renew(OperationToken t){beforeRenew.run();return !loseRenew&&actual.renew(t);}
  public ObservationResult observe(OperationToken t,ExternalObservation o,Runnable e){return actual.observe(t,o,e);}
  public ObservationResult observePrepared(OperationToken t,PreparedObservation o){observations.incrementAndGet();var r=actual.observePrepared(t,o);afterObserve.run();return r;}
  public ObservationResult observePreparedFact(OperationToken t,PreparedObservation o){observations.incrementAndGet();return actual.observePreparedFact(t,o);}
  public String retryAfterZeroEffect(String id,long n,TaskSpec s,ExternalRequest r){return actual.retryAfterZeroEffect(id,n,s,r);}
 };
 @BeforeEach void setup(){f.setup();f.jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",f.lease.taskId());actual=new JdbcExternalOperations(f.queue);model="T07_"+UUID.randomUUID().toString().replace("-","");permits=new JdbcModelPermitPool(actual,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException();});permits.configure();}
 @AfterEach void close(){if(worker!=null)worker.close();}
 TaskQueue queue(Runnable ordinaryRenew){return new TaskQueue(){public String enqueue(TaskSpec s){return f.queue.enqueue(s);}public Optional<TaskLease> claim(String p,String o){return f.queue.claim(p,o);}public boolean renew(TaskLease l){renews.incrementAndGet();ordinaryRenew.run();return f.queue.renew(l);}public boolean complete(TaskLease l,TaskResult r){ordinary.incrementAndGet();return f.queue.complete(l,r);}public boolean completeWithEffects(TaskLease l,TaskResult r,Runnable e){throw new AssertionError();}};}
 void start(TaskQueue q,WorkerLoop.ExternalHandler handler){worker=WorkerLoop.external(q,port,permits,Map.of(f.spec.pool(),1),Map.of(f.spec.type(),e->{session.set(e);try{handler.handle(e);}catch(Throwable t){error.set(t);throw t;}finally{done.countDown();}}),Map.of(f.spec.type(),model),"T07_WORKER_RACE");worker.pollOnce(f.spec.pool());}
 PreparedObservation response(){assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(TransactionSynchronizationManager.hasResource(f.source));try{var m=ExternalExecutionSession.class.getDeclaredField("monitor");m.setAccessible(true);assertFalse(Thread.holdsLock(m.get(session.get())));}catch(ReflectiveOperationException e){throw new AssertionError(e);}sends.incrementAndGet();return f.preparedObservation;}
 static void await(CountDownLatch latch){try{assertTrue(latch.await(10,TimeUnit.SECONDS),"Finite barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
 static void eventually(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!condition.getAsBoolean()){if(System.nanoTime()>end)fail("Finite condition timed out");Thread.sleep(10);}}
 static void invoke(Object object,String method){try{var m=object.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(object);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 Object execution()throws Exception{var field=WorkerLoop.class.getDeclaredField("executions");field.setAccessible(true);var set=(Set<?>)field.get(worker);eventually(()->!set.isEmpty());return set.iterator().next();}
 @Test void ordinaryRenewDrainsBeforeMarkerAndQueuedTickCannotCrossHandoff()throws Exception{
  var ready=new CountDownLatch(1);var begin=new CountDownLatch(1);var renewing=new CountDownLatch(1);var releaseRenew=new CountDownLatch(1);var marking=new CountDownLatch(1);var releaseMarker=new CountDownLatch(1);
  beforeBegin=()->{marking.countDown();await(releaseMarker);};
  start(queue(()->{renewing.countDown();await(releaseRenew);}),e->{ready.countDown();await(begin);assertTrue(e.begin(f.preparedRequest));assertTrue(e.send(this::response));});
  await(ready);Object execution=execution();var tick=Executors.newSingleThreadExecutor();
  try{var future=tick.submit(()->invoke(execution,"renew"));await(renewing);begin.countDown();var phase=execution.getClass().getDeclaredField("phase");phase.setAccessible(true);eventually(()->{try{synchronized(execution){return phase.get(execution).toString().equals("DRAINING_RENEWAL");}}catch(Exception x){throw new AssertionError(x);}});
   assertEquals(1,marking.getCount());releaseRenew.countDown();future.get(5,TimeUnit.SECONDS);await(marking);invoke(execution,"renew");assertEquals(1,renews.get());releaseMarker.countDown();await(done);
  }finally{releaseRenew.countDown();releaseMarker.countDown();tick.shutdownNow();}
  assertNull(error.get());assertEquals(1,sends.get());assertEquals(0,ordinary.get());assertEquals("SUCCEEDED",f.state());
 }
 @Test void operationHeartbeatLossBeforeResponseIsStickyReceiptOnly()throws Exception{
  var inflight=new CountDownLatch(1);var release=new CountDownLatch(1);
  start(queue(()->{}),e->{assertTrue(e.begin(f.preparedRequest));e.send(()->{inflight.countDown();await(release);return response();});});await(inflight);loseRenew=true;invoke(session.get(),"renew");release.countDown();await(done);
  assertNull(error.get());assertEquals(0,f.writes.get());assertEquals("STALE",f.state());assertEquals("SUCCEEDED",f.jdbc.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,session.get().token().operationId()));assertEquals(0,ordinary.get());
 }
 @Test void settlementWaitsForSelectedOperationRenewalAndLossWins()throws Exception{
  var inflight=new CountDownLatch(1);var response=new CountDownLatch(1);var renewing=new CountDownLatch(1);var release=new CountDownLatch(1);beforeRenew=()->{renewing.countDown();await(release);};loseRenew=true;
  start(queue(()->{}),e->{assertTrue(e.begin(f.preparedRequest));e.send(()->{inflight.countDown();await(response);return response();});});await(inflight);
  var tick=Executors.newSingleThreadExecutor();try{var future=tick.submit(()->invoke(session.get(),"renew"));await(renewing);response.countDown();eventually(()->{try{var p=ExternalExecutionSession.class.getDeclaredField("phase");p.setAccessible(true);var m=ExternalExecutionSession.class.getDeclaredField("monitor");m.setAccessible(true);synchronized(m.get(session.get())){return p.get(session.get()).toString().equals("SETTLING");}}catch(Exception e){throw new AssertionError(e);}});assertEquals(0,observations.get());release.countDown();future.get(5,TimeUnit.SECONDS);await(done);}finally{release.countDown();tick.shutdownNow();}
  assertNull(error.get());assertEquals("STALE",f.state());assertEquals(0,f.writes.get());
 }
 @Test void markerAcknowledgementLossSendsNothingAndNeverOrdinaryCompletes(){
  afterBegin=()->{throw new IllegalStateException("T07_MARKER_ACK_LOST");};start(queue(()->{}),e->{if(e.begin(f.preparedRequest))e.send(this::response);});await(done);
  assertInstanceOf(IllegalStateException.class,error.get());assertEquals(0,sends.get());assertEquals(0,ordinary.get());assertEquals("RECONCILING",f.state());assertEquals(1,markers.get());
 }
 @Test void terminalAcknowledgementLossRetriesSameFactAndSettlesWithoutSecondSend()throws Exception{
  var once=new AtomicBoolean();afterObserve=()->{if(once.compareAndSet(false,true))throw new IllegalStateException("T07_TERMINAL_ACK_LOST");};
  start(queue(()->{}),e->{assertTrue(e.begin(f.preparedRequest));e.send(this::response);});await(done);
  eventually(()->worker.observation().active()==0);assertEquals(1,sends.get());assertEquals(1,f.writes.get());assertEquals(0,ordinary.get());assertEquals(2,observations.get());assertEquals("SUCCEEDED",f.state());
  assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE execution_id=? AND settled_at IS NOT NULL",Integer.class,session.get().token().executionId()));
 }
 @Test void closeDuringTransportRetainsLateReceiptWithoutBusinessEffects()throws Exception{
  var inflight=new CountDownLatch(1);start(queue(()->{}),e->{assertTrue(e.begin(f.preparedRequest));e.send(()->{inflight.countDown();try{new CountDownLatch(1).await(10,TimeUnit.SECONDS);}catch(InterruptedException close){Thread.interrupted();}return response();});});await(inflight);worker.close();await(done);
  assertNull(error.get());assertEquals(1,sends.get());assertEquals(0,f.writes.get());assertEquals("STALE",f.state());assertEquals(0,ordinary.get());
 }
 @Test void schedulerRejectionAfterCommittedMarkerNeverSends()throws Exception{
  var ready=new CountDownLatch(1);var release=new CountDownLatch(1);start(queue(()->{}),e->{ready.countDown();await(release);if(e.begin(f.preparedRequest))e.send(this::response);});await(ready);
  var scheduler=WorkerLoop.class.getDeclaredField("heartbeats");scheduler.setAccessible(true);((ScheduledExecutorService)scheduler.get(worker)).shutdownNow();release.countDown();await(done);
  assertInstanceOf(RejectedExecutionException.class,error.get());assertEquals(0,sends.get());assertEquals(0,ordinary.get());assertEquals("RECONCILING",f.state());
 }
 @Test void duplicateCompletedObservationDoesNotRetainALocalSlot()throws Exception{
  start(queue(()->{}),e->{assertTrue(e.begin(f.preparedRequest));assertTrue(e.send(this::response));assertEquals(ObservationResult.STALE_TOKEN,e.observe(f.preparedObservation));});await(done);
  eventually(()->worker.observation().active()==0);assertNull(error.get());assertEquals(1,sends.get());assertEquals(1,f.writes.get());
 }
 @Test void saturatedReconcilerStillMaintainsTerminalReservations()throws Exception{
  // Own UNKNOWN execution occupies the only reconciliation slot.
  var token=actual.beginPrepared(f.queue.claim(f.spec.pool(),"T07_SATURATED_MARKER").orElseThrow(),f.preparedRequest).orElseThrow();
  f.jdbc.update("UPDATE external_operation SET reconcile_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND,next_check_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",token.operationId());
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var loop=ReconciliationLoop.prepared(actual,permits,1,s->{entered.countDown();await(release);},"T07_SATURATED")){
   int scanCalls=(f.jdbc.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class)+63)/64+2;
   for(int i=0;i<scanCalls&&entered.getCount()!=0;i++)invoke(loop,"poll");await(entered);

   var debt=new Task07PreparedExternalIT();debt.setup();var external=new JdbcExternalOperations(debt.queue);var other=external.beginPrepared(debt.lease,debt.preparedRequest).orElseThrow();String m="T07_"+UUID.randomUUID().toString().replace("-","");var p=new JdbcModelPermitPool(external,Map.of(m,new ModelConfiguration(m,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,x,d)->{throw new IllegalStateException();});p.configure();var permit=p.acquire(other,m,null).orElseThrow();external.observePrepared(other,debt.preparedObservation);
   // Invoke the existing cadence entry while its worker slot is saturated; no second scheduler.
   int maintenanceCalls=2*((debt.jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation",Integer.class)+7)/8)+2;
   for(int i=0;i<maintenanceCalls&&!debt.jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,permit.id());i++)invoke(loop,"poll");
   assertTrue(debt.jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,permit.id()));release.countDown();
  }finally{release.countDown();}
 }
}
