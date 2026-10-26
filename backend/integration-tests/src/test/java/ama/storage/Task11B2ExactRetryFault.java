package ama.storage;

import javax.sql.DataSource;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Observes delegated physical writes. Test faults never manufacture domain authorization. */
final class Task11B2ExactRetryFault {
 enum Point { RECEIPT_INSERT,TASK_INSERT,OPERATION_CAS,EXECUTION_INSERT,SUBMISSION_INSERT,PUBLICATION,RECEIPT_RESULT,AUDIT,TASK_OUTBOX,PUBLICATION_OUTBOX,SEQUENCE,PRE_COMMIT }
 interface Evidence {void record(String phase,Map<String,Object> value)throws Exception;}
 final Evidence evidence;final String meeting,publication,operation;
 volatile Point armed;volatile String keyHash;volatile boolean hit,rollbackObserved,concurrent,aborted;
 final AtomicInteger admitted=new AtomicInteger(),commits=new AtomicInteger();
 volatile CountDownLatch arrivals,firstCommit;volatile Semaphore[] permits;
 final List<String> writes=Collections.synchronizedList(new ArrayList<>());
 Task11B2ExactRetryFault(String meeting,String publication,String operation,Evidence evidence){this.meeting=meeting;this.publication=publication;this.operation=operation;this.evidence=evidence;}
 void arm(Point point,String key)throws Exception {assertNull(armed);assertFalse(concurrent);armed=point;aborted=false;keyHash=commandKeyHash(key);hit=false;rollbackObserved=false;writes.clear();}
 void race(String key)throws Exception {assertNull(armed);keyHash=commandKeyHash(key);concurrent=true;aborted=false;admitted.set(0);commits.set(0);arrivals=new CountDownLatch(2);firstCommit=new CountDownLatch(1);permits=new Semaphore[]{new Semaphore(0),new Semaphore(0)};}
 void release(int slot)throws Exception {assertTrue(concurrent&&!aborted);evidence.record("BEFORE_RECEIPT_ADMISSION_"+slot,Map.of("slot",slot,"commits",commits.get()));permits[slot].release();}
 void disarm(){armed=null;concurrent=false;keyHash=null;}
 void abort(){aborted=true;if(permits!=null)for(var permit:permits)permit.release();}
 Map<String,Object> observation(){return Map.of("hit",hit,"physicalRollbackObserved",rollbackObserved,"executedWrites",List.copyOf(writes),"admissions",admitted.get(),"physicalCommits",commits.get());}
 static String commandKeyHash(String key)throws Exception {byte[] value=key.getBytes(java.nio.charset.StandardCharsets.UTF_8);var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);out.writeInt(value.length);out.write(value);return Task11B2RuntimeFixture.digest(bytes.toByteArray());}
 static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
 static void bound(Connection connection)throws Exception {assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(connection.getAutoCommit());for(Object value:TransactionSynchronizationManager.getResourceMap().values())if(value instanceof ConnectionHolder holder&&holder.getConnection().unwrap(Connection.class)==connection.unwrap(Connection.class))return;throw new AssertionError("Exact physical transaction holder required");}
 DataSource wrap(DataSource actual){return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{
  Object value=invoke(actual,m,a);if(!(value instanceof Connection connection))return value;
  boolean[] selected={false};int[] slot={-1};String[] task={null},execution={null};var txWrites=new ArrayList<String>();
  return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{
   if(cm.getName().equals("commit")&&selected[0]&&armed==Point.PRE_COMMIT&&!hit){bound(connection);assertTrue(txWrites.stream().anyMatch(sql->sql.startsWith("UPDATE task_outbox_sequence")));connection.rollback();rollbackObserved=true;hit=true;evidence.record("PRE_COMMIT_PHYSICAL_ROLLBACK",Map.of("point",armed.name(),"physicalCommitCalled",false));throw new DataAccessResourceFailureException("Controlled exact retry rollback before physical commit");}
   Object result=invoke(connection,cm,ca);
   if(cm.getName().equals("rollback")&&selected[0]){rollbackObserved=true;evidence.record("PHYSICAL_ROLLBACK_RETURNED",Map.of("point",armed==null?"NONE":armed.name(),"writes",List.copyOf(txWrites)));}
   if(cm.getName().equals("commit")&&selected[0]){int count=commits.incrementAndGet();evidence.record("PHYSICAL_COMMIT_RETURNED",Map.of("admission",slot[0],"physicalCommitCount",count,"newTaskId",Objects.toString(task[0],"NONE"),"newExecutionId",Objects.toString(execution[0],"NONE")));if(slot[0]==0)firstCommit.countDown();}
   if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;var parameters=new HashMap<Integer,Object>();
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{
    if(sm.getName().startsWith("set")&&sa!=null&&sa.length>=2&&sa[0] instanceof Integer index)parameters.put(index,sa[1]);
    boolean execute=sm.getName().startsWith("execute");boolean receipt=execute&&sql.startsWith("INSERT INTO idempotency_record")&&"PUBLICATION_ZERO_EFFECT_RETRY".equals(parameters.get(4))&&meeting.equals(parameters.get(3))&&Objects.equals(keyHash,parameters.get(5));
    if(receipt){bound(connection);if(aborted)throw new DataAccessResourceFailureException("Exact retry window aborted before receipt write");selected[0]=true;if(concurrent){slot[0]=admitted.getAndIncrement();assertTrue(slot[0]<2);evidence.record("BEFORE_RECEIPT_INSERT_BARRIER",Map.of("admission",slot[0],"physicalWrites",0));arrivals.countDown();if(!permits[slot[0]].tryAcquire(10,TimeUnit.SECONDS)||aborted)throw new DataAccessResourceFailureException("Bounded retry admission aborted before write");}}
    Object answer=invoke(statement,sm,sa);
    if(execute&&selected[0]&&(sql.startsWith("INSERT ")||sql.startsWith("UPDATE "))){bound(connection);txWrites.add(sql);writes.add(sql);
     if(sql.startsWith("INSERT INTO durable_task"))task[0]=Objects.toString(parameters.get(1));if(sql.startsWith("INSERT INTO external_execution"))execution[0]=Objects.toString(parameters.get(1));
     Point point=armed;boolean match=point!=null&&switch(point){
      case RECEIPT_INSERT->receipt;
      case TASK_INSERT->sql.startsWith("INSERT INTO durable_task");
      case OPERATION_CAS->sql.startsWith("UPDATE external_operation SET request_hash=")&&parameters.containsValue(operation);
      case EXECUTION_INSERT->sql.startsWith("INSERT INTO external_execution")&&parameters.containsValue(operation);
      case SUBMISSION_INSERT->sql.startsWith("INSERT INTO publication_submission")&&parameters.containsValue(publication);
      case PUBLICATION->sql.startsWith("UPDATE publication SET confirmation_id=")&&parameters.containsValue(publication);
      case RECEIPT_RESULT->sql.startsWith("UPDATE idempotency_record SET result_id=?,http_status=202");
      case AUDIT->sql.startsWith("INSERT INTO audit_event")&&parameters.containsValue("PUBLICATION_ZERO_EFFECT_RETRIED");
      case TASK_OUTBOX->sql.startsWith("INSERT INTO task_outbox(")&&parameters.containsValue("TASK_ENQUEUED");
      case PUBLICATION_OUTBOX->sql.startsWith("INSERT INTO task_outbox(")&&parameters.containsValue("PUBLICATION_ACCEPTED");
      case SEQUENCE->sql.startsWith("UPDATE task_outbox_sequence");case PRE_COMMIT->false;};
     if(match&&!hit){assertTrue(answer instanceof Number&&((Number)answer).intValue()==1,"Fault follows exactly one actual affected row");hit=true;evidence.record("AFTER_ACTUAL_WRITE_BEFORE_FAULT",Map.of("point",point.name(),"affectedRows",1,"writes",List.copyOf(txWrites)));throw new DataAccessResourceFailureException("Controlled exact retry write rollback");}
    }return answer;
   });
  });
 });}
}
