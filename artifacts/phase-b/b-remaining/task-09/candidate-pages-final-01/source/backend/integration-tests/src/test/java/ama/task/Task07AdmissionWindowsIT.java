package ama.task;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static ama.task.Task07ExternalWorkerIT.*;

/** Actual Worker close at JDBC marker/permit commit boundaries, with durable row assertions. */
class Task07AdmissionWindowsIT {
 final Task07ExternalWorkerIT h=new Task07ExternalWorkerIT();
 @BeforeEach void setup(){h.setup();}
 @AfterEach void close(){h.close();}
 void waitForClose(CountDownLatch entered){entered.countDown();try{new CountDownLatch(1).await(10,TimeUnit.SECONDS);fail("Close must interrupt selected admission");}catch(InterruptedException close){Thread.interrupted();}}
 void start(){h.start(h.queue(()->{}),s->{if(s.begin(h.f.preparedRequest))s.send(h::response);});}
 void noSend(int reservations){assertNull(h.error.get());assertEquals(0,h.sends.get());assertEquals(0,h.ordinary.get());assertEquals("RECONCILING",h.f.state());assertEquals(0,h.f.writes.get());assertEquals(reservations,h.f.jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation r JOIN external_execution e ON e.id=r.execution_id WHERE e.task_id=? AND r.settled_at IS NULL",Integer.class,h.f.lease.taskId()));}
 @Test void closeWhileMarkerIsCommittingDoesNotAdmitSend(){
  var entered=new CountDownLatch(1);var once=new AtomicBoolean();h.beforeBegin=()->h.f.source.committing=()->{if(once.compareAndSet(false,true))waitForClose(entered);};
  start();await(entered);h.worker.close();await(h.done);h.f.source.committing=()->{};assertInstanceOf(RejectedExecutionException.class,h.error.get());h.error.set(null);noSend(0);assertEquals(1,h.markers.get());
 }
 @Test void closeWhilePermitAdmissionIsCommittingKeepsExactReservationAndSendsNothing(){
  var entered=new CountDownLatch(1);var acquiring=new AtomicBoolean();var once=new AtomicBoolean();h.f.source.before=e->{if(e.text().startsWith("INSERT INTO model_reservation"))acquiring.set(true);};h.f.source.committing=()->{if(acquiring.get()&&once.compareAndSet(false,true))waitForClose(entered);};
  start();await(entered);h.worker.close();await(h.done);h.f.source.committing=()->{};noSend(1);assertEquals(1,h.markers.get());
 }
 @Test void permitCommitBeforeLostAcknowledgementRetainsDebtWithoutSend(){
  var acquiring=new AtomicBoolean();var once=new AtomicBoolean();h.f.source.before=e->{if(e.text().startsWith("INSERT INTO model_reservation"))acquiring.set(true);};h.f.source.committed=()->{if(acquiring.get()&&once.compareAndSet(false,true))throw new IllegalStateException("T07_PERMIT_ACK_LOST");};
  start();await(h.done);h.f.source.committed=()->{};assertInstanceOf(IllegalStateException.class,h.error.get());h.error.set(null);noSend(1);assertEquals(0,h.permits.recoverSettlements(8));
 }
}
