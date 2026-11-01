package ama.storage;

import ama.task.RecordingArchiveRuntime;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Renew the genuine lease after detached graph discovery and before the root is locked. */
class RecordingUploadDiscoveryRenewalIT extends RecordingArchiveTaskIT {
 private static DataSource delegate;
 private static final AtomicReference<Runnable> afterDiscovery=new AtomicReference<>();
 @BeforeAll static void observeDiscovery(){
  delegate=source;
  source=(DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(p,m,a)->{
   Object result=invoke(delegate,m,a);
   if(result instanceof java.sql.Connection connection)return Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),new Class<?>[]{java.sql.Connection.class},(cp,cm,ca)->{
    Object value=invoke(connection,cm,ca);
    if(cm.getName().equals("prepareStatement")&&ca[0] instanceof String sql&&value instanceof java.sql.PreparedStatement statement)
     return Proxy.newProxyInstance(java.sql.PreparedStatement.class.getClassLoader(),new Class<?>[]{java.sql.PreparedStatement.class},(sp,sm,sa)->{
      Object returned=invoke(statement,sm,sa);
      if(sm.getName().equals("close")&&sql.startsWith("SELECT COUNT(*)")&&sql.contains(" FROM external_execution WHERE ")&&!sql.contains("FOR UPDATE")&&!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()){
       var action=afterDiscovery.getAndSet(null);if(action!=null)action.run();
      }
      return returned;
     });
    return value;
   });
   return result;
  });
  j=new JdbcTemplate(source);
 }
 private static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
 @AfterEach void disarm(){afterDiscovery.set(null);}
 @AfterAll static void restoreSource(){if(delegate!=null){source=delegate;j=new JdbcTemplate(source);}}
 @Test void renewalAfterDiscoveryBeforeBeginIoKeepsTheFullWriteGate()throws Exception{verify("before-begin");}
 @Test void renewalAfterBeginReceiptDiscoveryDoesNotRepeatNativeIo()throws Exception{verify("begin");}
 @Test void renewalAfterPartReceiptDiscoveryDoesNotConsumeTheStreamAgain()throws Exception{verify("part");}
 @Test void renewalAfterCompletionDiscoveryDoesNotRegisterAStaleGraph()throws Exception{verify("complete");}
 @Test void repeatedDiscoveryChangesStopAfterThreeAttemptsWithoutNativeIo()throws Exception{
  try(var f=new Fixture()){
   var session=f.recorded();RecordingArchiveRuntime.accept(f.queue,f.actor,session.sessionId());
   var lease=f.queue.claim("RECORDING_ARCHIVE","bounded-discovery-renewal").orElseThrow();
   var count=new java.util.concurrent.atomic.AtomicInteger();Runnable[] renew=new Runnable[1];
   renew[0]=()->{assertTrue(f.queue.renew(lease));count.incrementAndGet();afterDiscovery.set(renew[0]);};afterDiscovery.set(renew[0]);
   try{
    var error=assertThrows(ama.contract.MinutesContracts.Failure.class,()->f.uploads.begin(f.actor,f.meeting,UUID.randomUUID().toString(),new ama.contract.FileContracts.Begin("RECORDING_ARCHIVE","bounded.wav",4,"0".repeat(64)),"bounded-discovery"));
    assertEquals(ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE,error.code());
    assertEquals(3,count.get());assertFalse(f.uploadCalls.containsKey("beginUpload"));
    assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",Integer.class,f.meeting));
   }finally{afterDiscovery.set(null);}
  }
 }
 @Test void deletionAfterDiscoveryCannotStartNativeUpload()throws Exception{
  try(var f=new Fixture()){
   afterDiscovery.set(()->txDelete(f));
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.uploads.begin(f.actor,f.meeting,UUID.randomUUID().toString(),new ama.contract.FileContracts.Begin("RECORDING_ARCHIVE","deleted.wav",4,"0".repeat(64)),"deleted-discovery"));
   assertFalse(f.uploadCalls.containsKey("beginUpload"));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",Integer.class,f.meeting));
  }
 }
 private void verify(String phase)throws Exception{
  try(var f=new Fixture()){
   var session=f.recorded();var admitted=RecordingArchiveRuntime.accept(f.queue,f.actor,session.sessionId());
   var lease=f.queue.claim("RECORDING_ARCHIVE","discovery-renewal-"+phase).orElseThrow();assertEquals(admitted.taskId(),lease.taskId());
   var renewed=new java.util.concurrent.atomic.AtomicBoolean();
   Runnable arm=()->afterDiscovery.set(()->{assertTrue(f.queue.renew(lease));renewed.set(true);});
   byte[] bytes={1,2,3,4};String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
   var request=new ama.contract.FileContracts.Begin("RECORDING_ARCHIVE","discovery.wav",bytes.length,hash);
   if(phase.equals("before-begin"))arm.run();else if(phase.equals("begin"))f.afterBegin.set(arm);
   var upload=f.uploads.begin(f.actor,f.meeting,UUID.randomUUID().toString(),request,"discovery-renewal");
   if(phase.equals("part"))f.afterPart.set(arm);
   f.uploads.part(f.actor,upload.uploadId(),1,new java.io.ByteArrayInputStream(bytes),bytes.length,hash,"discovery-renewal");
   if(phase.equals("complete"))f.afterComplete.set(arm);
   var completed=f.uploads.complete(f.actor,upload.uploadId(),new ama.contract.FileContracts.Completion(List.of(new ama.contract.FileContracts.ClientPart(1,hash)),hash),"discovery-renewal");
   assertTrue(renewed.get(),"Renew only after the complete unlocked graph has been discovered");
   for(String method:List.of("beginUpload","writePart","complete"))assertEquals(1,f.uploadCalls.get(method).get(),"Native I/O executes exactly once: "+method);
   assertEquals(hash,j.queryForObject("SELECT sha256 FROM file_object WHERE id=?",String.class,completed.fileId()));
   assertEquals("COMPLETED",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,upload.uploadId()));
  }
 }
}
