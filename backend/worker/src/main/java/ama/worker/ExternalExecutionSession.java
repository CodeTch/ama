package ama.worker;

import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import ama.task.ExternalOperationPort;
import ama.task.ExternalOperationPort.*;
import ama.task.ModelPermitPool;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Bounded single-owner state. No JDBC or transport call runs while its monitor is held. */
public final class ExternalExecutionSession implements ExternalTaskExecution,AutoCloseable {
 @FunctionalInterface interface Admission {boolean drain() throws InterruptedException;}
 private enum Phase {ORDINARY,MARKING,MARKER_UNKNOWN,OPERATION,ADMITTING,SETTLING,FINISHED}
 private final Object monitor;private final TaskLease lease;private final ExternalOperationPort operations;private final ModelPermitPool permits;
 private final ExternalOperationHeartbeat heartbeats;private final String model;private final BooleanSupplier open;private final Admission admission;
 private Phase phase=Phase.ORDINARY;private OperationToken token;private Permit permit;private ExternalOperationHeartbeat.Tracked tracker;
 private boolean receiptOnly,renewing,sent,recoverReceipt;private PreparedObservation pending;
 ExternalExecutionSession(Object monitor,TaskLease lease,ExternalOperationPort operations,ModelPermitPool permits,ExternalOperationHeartbeat heartbeats,String model,BooleanSupplier open,Admission admission){
  this.monitor=monitor;this.lease=lease;this.operations=operations;this.permits=permits;this.heartbeats=heartbeats;this.model=model;this.open=open;this.admission=admission;
 }
 static ExternalExecutionSession reconciliation(OperationToken token,ExternalOperationPort operations,ModelPermitPool permits,ExternalOperationHeartbeat heartbeats,BooleanSupplier open){
  var session=new ExternalExecutionSession(new Object(),null,operations,permits,heartbeats,null,open,()->false);
  synchronized(session.monitor){session.token=token;session.phase=Phase.OPERATION;session.sent=true;}
  session.tracker=heartbeats.track(session::renew);return session;
 }
 public TaskLease lease(){if(lease==null)throw new IllegalStateException("Reconciliation has no ordinary lease");return lease;}
 public OperationToken token(){synchronized(monitor){if(token==null)throw new IllegalStateException("No acknowledged marker");return token;}}
 public boolean begin(PreparedRequest request)throws InterruptedException{
  synchronized(monitor){if(phase!=Phase.ORDINARY||!open.getAsBoolean())return false;}
  if(!admission.drain())return false;
  synchronized(monitor){if(phase!=Phase.ORDINARY||!open.getAsBoolean())return false;phase=Phase.MARKING;}
  try{
   var marked=operations.beginPrepared(lease,request);
   synchronized(monitor){if(marked.isEmpty()){phase=Phase.FINISHED;return false;}token=marked.orElseThrow();phase=Phase.OPERATION;}
   var tracked=heartbeats.track(this::renew);
   synchronized(monitor){tracker=tracked;if(!open.getAsBoolean())receiptOnly=true;return !receiptOnly;}
  }catch(RuntimeException failure){synchronized(monitor){phase=token==null?Phase.MARKER_UNKNOWN:Phase.OPERATION;receiptOnly=true;}throw failure;}
 }
 void renew(){
  OperationToken current;
  synchronized(monitor){if(phase!=Phase.OPERATION||renewing||receiptOnly||!open.getAsBoolean())return;renewing=true;current=token;}
  boolean ok=false;try{ok=operations.renew(current);}catch(RuntimeException unavailable){/* sticky receipt-only */}
  finally{synchronized(monitor){renewing=false;if(!ok)receiptOnly=true;monitor.notifyAll();}}
 }
 public boolean send(Callable<PreparedObservation> transport)throws Exception{
  Objects.requireNonNull(transport);
  synchronized(monitor){if(phase!=Phase.OPERATION||receiptOnly||sent||!open.getAsBoolean())return false;phase=Phase.ADMITTING;}
  try{
   Permit admitted=null;
   if(model!=null){var value=permits.acquire(token,model,null);if(value.isEmpty()){synchronized(monitor){receiptOnly=true;phase=Phase.OPERATION;}return false;}admitted=value.orElseThrow();}
   synchronized(monitor){permit=admitted;phase=Phase.OPERATION;if(receiptOnly||!open.getAsBoolean())return false;sent=true;}
  }catch(RuntimeException failure){synchronized(monitor){receiptOnly=true;phase=Phase.OPERATION;}throw failure;}
  // The one-shot admission above is the close linearization point. No monitor/DB root crosses I/O.
  var result=Objects.requireNonNull(transport.call(),"Prepared authoritative receipt");
  observe(result);return true;
 }
 public ObservationResult observe(PreparedObservation observation)throws InterruptedException{
  Objects.requireNonNull(observation);boolean factsOnly;
  synchronized(monitor){
   if(token==null||phase==Phase.MARKING||phase==Phase.MARKER_UNKNOWN||phase==Phase.ADMITTING||phase==Phase.SETTLING)throw new IllegalStateException("Observation ownership unavailable");
   if(phase==Phase.FINISHED)return ObservationResult.STALE_TOKEN;
   if(pending!=null&&pending!=observation)throw new IllegalArgumentException("Only the same pending receipt may retry");pending=observation;
   phase=Phase.SETTLING;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(renewing){long left=end-System.nanoTime();if(left<=0){receiptOnly=true;phase=Phase.OPERATION;throw new IllegalStateException("Operation renewal drain expired");}try{TimeUnit.NANOSECONDS.timedWait(monitor,left);}catch(InterruptedException interrupted){receiptOnly=true;phase=Phase.OPERATION;throw interrupted;}}
   factsOnly=receiptOnly||!open.getAsBoolean();
  }
  try{
   var result=recoverReceipt?operations.recoverPreparedFact(token,observation):factsOnly?operations.observePreparedFact(token,observation):operations.observePrepared(token,observation);
   boolean acknowledged=result!=ObservationResult.STALE_TOKEN;
   synchronized(monitor){if(acknowledged){pending=null;phase=Phase.FINISHED;}else{receiptOnly=true;recoverReceipt=true;phase=Phase.OPERATION;}}
   if(acknowledged){if(tracker!=null)tracker.close();if(permit!=null)try{permits.settled(permit);}catch(RuntimeException unavailable){/* Durable terminal debt is recovered independently. */}}
   return result;
  }catch(RuntimeException unavailable){synchronized(monitor){phase=Phase.OPERATION;}throw unavailable;}
 }
 boolean pending(){synchronized(monitor){return pending!=null;}}
 void retry(){PreparedObservation observation;synchronized(monitor){observation=pending;}if(observation==null)return;try{observe(observation);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(RuntimeException unavailable){/* one bounded retry on the existing poll cadence */}}
 public void close(){ExternalOperationHeartbeat.Tracked current;synchronized(monitor){receiptOnly=true;current=tracker;monitor.notifyAll();}if(current!=null)current.close();}
}
