package ama.task;

import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class Task08CursorIT {
 Task08Fixture fixture(){return new Task08Fixture("behavior","T08_CURSOR");}
 String type(){return "T08_"+UUID.randomUUID().toString().replace("-","");}
 JdbcExternalOperations scan(Task08Fixture f,String type,int p,int b){return new JdbcExternalOperations(f.queue(type),new ReconciliationScanBudget(p,b));}
 OperationToken next(JdbcExternalOperations scan){int completed=0;for(int i=0;i<1000;i++){var t=scan.claimReconciliation("T08_SCAN");if(t.isPresent())return t.orElseThrow();try{if(!active(scan)&&++completed==2)break;}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}catch(Exception error){throw new IllegalStateException(error);}}throw new AssertionError("Eligible test target must be reached within two finite passes");}
 static Object cursor(JdbcExternalOperations scanner)throws Exception{var f=JdbcExternalOperations.class.getDeclaredField("scanCursor");f.setAccessible(true);return f.get(scanner);}
 static long position(JdbcExternalOperations scanner,String field)throws Exception{Object c=cursor(scanner);var f=c.getClass().getDeclaredField(field);f.setAccessible(true);return f.getLong(c);}
 static boolean active(JdbcExternalOperations scanner)throws Exception{Object c=cursor(scanner);var f=c.getClass().getDeclaredField("active");f.setAccessible(true);return f.getBoolean(c);}
 static void outsideUpdate(String sql,Object id){try(var c=Task08Fixture.dataSource("behavior").getConnection();var q=c.prepareStatement(sql)){q.setObject(1,id);assertEquals(1,q.executeUpdate());}catch(SQLException e){throw new IllegalStateException(e);}}
 @Test void successConsumesOnlyReturnedRowAndRefetchesPrefetchedTail()throws Exception{
  var f=fixture();String type=type();var a=f.marker(f.spec(type));var b=f.marker(f.spec(type));var c=f.marker(f.spec(type));for(var t:List.of(a,b,c))f.due(t.operationId());
  var scan=scan(f,type,16,32);
  assertEquals(a.operationId(),next(scan).operationId());assertEquals(f.seq(a.operationId()),position(scan,"after"));
  f.due(a.operationId());assertEquals(b.operationId(),next(scan).operationId());assertEquals(f.seq(b.operationId()),position(scan,"after"));
  f.due(a.operationId());f.due(b.operationId());assertEquals(c.operationId(),next(scan).operationId());assertFalse(active(scan));
  assertEquals(a.operationId(),next(scan).operationId());
 }
 @Test void constantArrivalsCannotExtendFixedPass()throws Exception{
  var f=fixture();String type=type();var tail=f.marker(f.spec(type));f.due(tail.operationId());var scan=scan(f,type,4,4);
  assertTrue(scan.claimReconciliation("T08_CAPTURE").isEmpty());long high=position(scan,"upper");assertEquals(f.seq(tail.operationId()),high);
  int limit=f.j.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class)+1;List<String> arrivals=new ArrayList<>();OperationToken found=null;
  for(int i=0;i<limit;i++){
   var t=f.marker(f.spec(type));f.due(t.operationId());arrivals.add(t.operationId());
   var result=scan.claimReconciliation("T08_CAPTURE");assertEquals(high,position(scan,"upper"));
   if(result.isPresent()){found=result.orElseThrow();break;}
  }
  assertNotNull(found);assertEquals(tail.operationId(),found.operationId());assertFalse(active(scan));
  assertEquals(arrivals.getFirst(),next(scan).operationId());
 }
 @Test void freshLockedDueAndFullSpecAreRechecked()throws Exception{
  var f=fixture();String type=type();var spec=f.spec(type);var original=f.marker(spec);f.due(original.operationId());var scan=scan(f,type,16,32);
  var once=new AtomicBoolean();f.source.after=s->{if(s.text().startsWith("SELECT *, (next_check_at")&&!s.text().contains("FOR UPDATE")&&original.operationId().equals(s.parameters().get(1))&&once.compareAndSet(false,true))outsideUpdate("UPDATE external_operation SET next_check_at=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE id=?",original.operationId());};
  for(int i=0;i<100&&(!once.get()||active(scan));i++)assertTrue(scan.claimReconciliation("T08_DUE").isEmpty());
  assertTrue(once.get());assertEquals(original.reconcileEpoch(),f.j.queryForObject("SELECT reconcile_epoch FROM external_operation WHERE id=?",Long.class,original.operationId()));f.source.after=s->{};
  f.due(original.operationId());assertEquals(original.operationId(),next(scan).operationId());
  f.due(original.operationId());var scanner=scan(f,type,16,32);AtomicBoolean changed=new AtomicBoolean();String intendedTask=f.j.queryForObject("SELECT task_id FROM external_execution WHERE id=?",String.class,original.executionId());
  f.source.after=s->{if(s.text().equals("SELECT * FROM durable_task WHERE id=?")&&intendedTask.equals(s.parameters().get(1))&&changed.compareAndSet(false,true))outsideUpdate("UPDATE durable_task SET priority=priority+1 WHERE id=?",s.parameters().get(1));};
  f.source.clear();for(int i=0;i<100&&(!changed.get()||active(scanner));i++)assertTrue(scanner.claimReconciliation("T08_SPEC").isEmpty());
  f.source.after=s->{};assertTrue(changed.get());assertFalse(f.source.sql.stream().anyMatch(s->s.text().contains("scan_due")&&s.text().contains("FOR UPDATE")&&original.operationId().equals(s.parameters().get(1))),"Changed full spec rejected before operation lock");
 }
 @Test void preparationExceptionConsumesPositionAndFinalEmptyDoesNotWrap()throws Exception{
  var f=fixture();String type=type();var t=f.marker(f.spec(type));f.due(t.operationId());var scan=scan(f,type,16,32);f.policy.preparing=()->{throw new IllegalStateException("T08_POLICY_FAILURE");};
  RuntimeException error=null;for(int i=0;i<100&&error==null;i++){try{assertTrue(scan.claimReconciliation("T08_ERROR").isEmpty());}catch(IllegalStateException e){error=e;}}
  assertNotNull(error);assertEquals("T08_POLICY_FAILURE",error.getMessage());assertEquals(f.seq(t.operationId()),position(scan,"after"));assertTrue(active(scan));
  f.policy.preparing=()->{};f.source.clear();assertTrue(scan.claimReconciliation("T08_EMPTY").isEmpty());assertEquals(1,f.source.sql.size());assertEquals(0,f.source.rows);assertFalse(active(scan));
  System.out.println("TASK08 final-empty SQL="+f.source.sql+" controls="+f.source.controls+" rows="+f.source.rows);
  assertEquals(t.operationId(),next(scan).operationId());
 }
 @Test void discoveryFailureDoesNotAdvanceAndCandidateFailureReachesTail()throws Exception{
  var f=fixture();String type=type();var a=f.marker(f.spec(type));var b=f.marker(f.spec(type));f.due(a.operationId());f.due(b.operationId());var scan=scan(f,type,8,16);
  var once=new AtomicBoolean();f.source.before=s->{if(s.text().startsWith("SELECT reconcile_scan_seq,id")&&once.compareAndSet(false,true))throw new IllegalStateException("T08_DISCOVERY");};
  assertEquals("T08_DISCOVERY",assertThrows(IllegalStateException.class,()->scan.claimReconciliation("T08_ERROR")).getMessage());assertEquals(0,position(scan,"after"));f.source.before=s->{};
  f.policy.preparing=()->{throw new IllegalStateException("T08_CANDIDATE");};boolean failed=false;for(int i=0;i<100&&!failed;i++){try{scan.claimReconciliation("T08_ERROR");}catch(IllegalStateException expected){assertEquals("T08_CANDIDATE",expected.getMessage());failed=true;}}
  assertTrue(failed);assertEquals(f.seq(a.operationId()),position(scan,"after"));f.policy.preparing=()->{};assertEquals(b.operationId(),next(scan).operationId());
 }
 @Test void lostCommitAckReturnsNoTokenButProgressAndLeaseRemain()throws Exception{
  var f=fixture();String type=type();var a=f.marker(f.spec(type));var b=f.marker(f.spec(type));f.due(a.operationId());f.due(b.operationId());var scan=scan(f,type,16,32);
  var armed=new AtomicBoolean();f.source.after=s->{if(s.text().startsWith("UPDATE external_operation SET reconcile_epoch"))armed.set(true);};f.source.committed=()->{if(armed.compareAndSet(true,false))throw new IllegalStateException("T08_LOST_ACK");};
  RuntimeException failure=null;for(int i=0;i<100&&failure==null;i++){try{scan.claimReconciliation("T08_ACK");}catch(RuntimeException e){failure=e;}}
  assertNotNull(failure);assertEquals("T08_LOST_ACK",failure.getMessage());f.source.after=s->{};f.source.committed=()->{};
  assertEquals(f.seq(a.operationId()),position(scan,"after"));assertEquals(a.reconcileEpoch()+1,f.j.queryForObject("SELECT reconcile_epoch FROM external_operation WHERE id=?",Long.class,a.operationId()));assertEquals(b.operationId(),next(scan).operationId());
  assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE operation_id=?",Integer.class,a.operationId()));
 }
 @Test void sameInstanceTryLockDoesNotBlockIndependentRenewAndObserve()throws Exception{
  var f=fixture();String type=type();var spec=f.spec(type);var live=f.marker(spec);var target=f.marker(f.spec(type));f.due(target.operationId());var scan=scan(f,type,16,32);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);AtomicReference<Thread> worker=new AtomicReference<>();
  f.policy.preparing=()->{if(Thread.currentThread()==worker.get()){entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}};
  try(var threads=Executors.newSingleThreadExecutor()){
   var work=threads.submit(()->{worker.set(Thread.currentThread());return next(scan);});
   try{assertTrue(entered.await(10,TimeUnit.SECONDS));long start=System.nanoTime();assertTrue(scan.claimReconciliation("T08_CONTENDED").isEmpty());assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));assertTrue(scan.renew(live));assertEquals(ObservationResult.APPLIED,scan.observe(live,new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"T08_UNKNOWN",null),()->fail("UNKNOWN never sends")));}finally{release.countDown();}
   assertEquals(target.operationId(),work.get(10,TimeUnit.SECONDS).operationId());
  }finally{f.policy.preparing=()->{};}
 }
 @Test void concurrentInstancesFenceOneLiveClaimAndUnknownRemainsLookupOnly()throws Exception{
  var f=fixture();String type=type();var spec=f.spec(type);var token=f.marker(spec);var producer=new JdbcExternalOperations(f.queue(type));assertEquals(ObservationResult.APPLIED,producer.observe(token,new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"T08_UNKNOWN",null),()->fail("UNKNOWN callback")));f.due(token.operationId());
  var one=scan(f,type,16,32);var two=scan(f,type,16,32);int calls=(f.j.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class)+31)/32+1;
  try(var pool=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);Callable<List<OperationToken>> a=()->{gate.await();var result=new ArrayList<OperationToken>();for(int i=0;i<calls;i++)one.claimReconciliation("T08_ONE").ifPresent(result::add);return result;};Callable<List<OperationToken>> b=()->{gate.await();var result=new ArrayList<OperationToken>();for(int i=0;i<calls;i++)two.claimReconciliation("T08_TWO").ifPresent(result::add);return result;};var first=pool.submit(a);var second=pool.submit(b);gate.countDown();var tokens=new ArrayList<>(first.get(20,TimeUnit.SECONDS));tokens.addAll(second.get(20,TimeUnit.SECONDS));assertEquals(1,tokens.size());assertFalse(one.hasCallAuthority(tokens.getFirst()));assertFalse(two.hasCallAuthority(tokens.getFirst()));}
  assertEquals("UNKNOWN",f.j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,token.operationId()));assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE operation_id=?",Integer.class,token.operationId()));
 }
 @Test void lateReservedCommitBehindCursorIsRevisitedAndRolledBackGapIsSafe()throws Exception{
  var f=fixture();String type=type();var lowSpec=f.spec(type);var highSpec=f.spec(type);
  // Run a genuine beginExternal against a connection whose commit acknowledgement is gated
  // BEFORE the physical commit; its allocated ordinal is invisible to discovery until released.
  var lowQueue=f.queue(type);lowQueue.enqueue(lowSpec);var lease=lowQueue.claim(lowSpec.pool(),"T08_LOW").orElseThrow();
  var ready=new CountDownLatch(1);var release=new CountDownLatch(1);AtomicReference<Thread> lowThread=new AtomicReference<>();AtomicReference<String> lowId=new AtomicReference<>();
  f.source.after=s->{if(Thread.currentThread()==lowThread.get()&&s.text().startsWith("INSERT INTO external_operation")){lowId.set((String)s.parameters().get(1));ready.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}};
  try(var pool=Executors.newSingleThreadExecutor()){
   var low=pool.submit(()->{lowThread.set(Thread.currentThread());return new JdbcExternalOperations(lowQueue).beginExternal(lease,f.request(lowSpec)).orElseThrow();});
   try{assertTrue(ready.await(10,TimeUnit.SECONDS));var high=f.marker(highSpec);f.due(high.operationId());var scan=scan(f,type,16,32);assertEquals(high.operationId(),next(scan).operationId());assertFalse(active(scan));release.countDown();var lower=low.get(10,TimeUnit.SECONDS);assertTrue(f.seq(lower.operationId())<f.seq(high.operationId()));f.due(lower.operationId());assertEquals(lower.operationId(),next(scan).operationId());}finally{release.countDown();}
  }finally{f.source.after=s->{};}
 }
 @Test void mixedHeldSubjectDomainSourceTaskOperationExecutionAllReleaseBeforeTail()throws Exception{
  for(String phase:List.of("subject","meeting","input","task","operation","execution")){
   var f=fixture();String type=type();var spec=f.spec(type);var blocked=f.marker(spec);var tail=f.marker(f.spec(type));f.due(blocked.operationId());f.due(tail.operationId());var scan=scan(f,type,16,32);
   String sql=switch(phase){case "subject"->"SELECT subject_id FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? FOR UPDATE";case "meeting"->"SELECT id FROM meeting WHERE id=? FOR UPDATE";case "input"->"SELECT id FROM meeting_info_revision WHERE id=? FOR UPDATE";case "task"->"SELECT task_id FROM external_execution WHERE id=?";case "operation"->"SELECT id FROM external_operation WHERE id=? FOR UPDATE";default->"SELECT id FROM external_execution WHERE id=? FOR UPDATE";};
   Object id=switch(phase){case "subject","meeting"->spec.subject().id();case "input"->spec.input().id();case "operation"->blocked.operationId();default->blocked.executionId();};
   if(phase.equals("task")){id=f.j.queryForObject(sql,String.class,id);sql="SELECT id FROM durable_task WHERE id=? FOR UPDATE";}
   String before=f.skippedSnapshot(tail.operationId());
   try(var c=f.source.getConnection();var st=c.prepareStatement(sql)){c.setAutoCommit(false);st.setObject(1,id);try(var rows=st.executeQuery()){assertTrue(rows.next());}assertEquals(tail.operationId(),next(scan).operationId(),phase);assertEquals(before,f.skippedSnapshot(tail.operationId()),phase+" skipped full graph unchanged");c.rollback();}
   assertEquals(blocked.operationId(),next(scan).operationId(),phase+" revisit");
  }
 }

 @Test void changedCurrentExecutionIsRejectedWithoutLockingItsNewTaskUnderOldOperation()throws Exception{
  var f=fixture();String type=type();var spec=f.spec(type);var old=f.marker(spec);f.due(old.operationId());var scanner=scan(f,type,16,32);
  String oldTask=f.j.queryForObject("SELECT task_id FROM external_execution WHERE id=?",String.class,old.executionId());var once=new AtomicBoolean();var newTask=new AtomicReference<String>();
  try(var pool=Executors.newSingleThreadExecutor()){
   f.source.after=sql->{if(sql.text().equals("SELECT * FROM durable_task WHERE id=?")&&oldTask.equals(sql.parameters().get(1))&&once.compareAndSet(false,true)){
    try{newTask.set(pool.submit(()->{var other=scan(f,type,16,32);var takeover=next(other);assertEquals(old.operationId(),takeover.operationId());assertEquals(ObservationResult.APPLIED,other.observe(takeover,new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"T08_REJECTED",new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"T08_ZERO",java.time.Instant.now())),()->{}));var retry=new TaskSpec(spec.type(),spec.pool(),spec.subject(),Task08Fixture.hash(UUID.randomUUID().toString()),spec.input(),spec.inputFingerprint(),spec.businessVersion(),spec.priority(),spec.cancelEpoch(),spec.maxAttempts());return other.retryAfterZeroEffect(old.operationId(),1,retry,f.request(spec));}).get(15,TimeUnit.SECONDS));}catch(Exception error){throw new IllegalStateException(error);}
   }};
   for(int i=0;i<100&&(!once.get()||active(scanner));i++)assertTrue(scanner.claimReconciliation("T08_CURRENT").isEmpty());
   f.source.after=sql->{};assertTrue(once.get());assertNotNull(newTask.get());assertEquals(2,f.j.queryForObject("SELECT current_execution_no FROM external_operation WHERE id=?",Integer.class,old.operationId()));
   assertEquals("PREPARED",f.j.queryForObject("SELECT state FROM external_execution WHERE task_id=?",String.class,newTask.get()));assertFalse(scanner.hasCallAuthority(old));
  }finally{f.source.after=sql->{};}
 }

}
