package ama.task;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import ama.worker.WorkerLoop;
import java.nio.file.*;
import java.util.*;
/** Child exits only when killed by its parent after a committed terminal result, before settlement. */
public final class Task07TerminalCrashProbe {
 public static void main(String[] args)throws Exception{
  var ready=Path.of(args[0]);var f=new Task07PreparedExternalIT();f.setup();f.jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",f.lease.taskId());
  var external=new JdbcExternalOperations(f.queue);String model="T07_"+UUID.randomUUID().toString().replace("-","");
  var actual=new JdbcModelPermitPool(external,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException();});actual.configure();
  ModelPermitPool stopBeforeSettlement=new ModelPermitPool(){
   public Optional<Permit> acquire(OperationToken t,String m,java.time.Instant d){return actual.acquire(t,m,d);}
   public boolean settled(Permit p){try{Files.writeString(ready,p.id()+"\n"+p.executionId()+"\n"+p.model()+"\n"+f.lease.taskId()+"\n");new java.util.concurrent.CountDownLatch(1).await();throw new AssertionError();}catch(Exception e){throw new IllegalStateException(e);}}
  };
  try(var worker=WorkerLoop.external(f.queue,external,stopBeforeSettlement,Map.of(f.spec.pool(),1),Map.of(f.spec.type(),s->{if(s.begin(f.preparedRequest))s.send(()->f.preparedObservation);}),Map.of(f.spec.type(),model),"T07_TERMINAL_CRASH")){
   worker.pollOnce(f.spec.pool());new java.util.concurrent.CountDownLatch(1).await();
  }
 }
}
