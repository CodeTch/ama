package ama.task;
import ama.contract.ExternalContracts.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class Task08RestartIT {
 @Test void newInstanceAndFreshJvmRecoverPersistedUnknownWithoutResend()throws Exception{
  var f=new Task08Fixture("behavior","T08_RESTART");String type="T08_RESTART_"+UUID.randomUUID().toString().replace("-","");var s=f.spec(type);var marker=f.marker(s);var ext=new JdbcExternalOperations(f.queue(type));assertEquals(ObservationResult.APPLIED,ext.observe(marker,new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"T08_UNKNOWN",null),()->fail("No UNKNOWN effects")));f.due(marker.operationId());
  var partial=new JdbcExternalOperations(f.queue(type),new ReconciliationScanBudget(1,1));assertTrue(partial.claimReconciliation("T08_BEFORE_RESTART").isEmpty());
  var restarted=new JdbcExternalOperations(f.queue(type),new ReconciliationScanBudget(8,16));OperationToken claimed=null;for(int i=0;i<100&&claimed==null;i++)claimed=restarted.claimReconciliation("T08_INSTANCE").orElse(null);assertNotNull(claimed);assertEquals(marker.operationId(),claimed.operationId());assertFalse(restarted.hasCallAuthority(claimed));
  var stillLive=new JdbcExternalOperations(f.queue(type),new ReconciliationScanBudget(64,64));for(int i=0;i<5;i++)assertTrue(stillLive.claimReconciliation("T08_LIVE").isEmpty());f.due(marker.operationId());
  Path log=Files.createTempFile("task08-restart-",".log");Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx64m","-XX:ActiveProcessorCount=2","-cp",System.getProperty("java.class.path"),Task08RestartProbe.class.getName(),type,marker.operationId()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
  try{assertTrue(process.waitFor(30,TimeUnit.SECONDS));String output=Files.readString(log);System.out.println(output);assertEquals(0,process.exitValue(),output);assertFalse(process.isAlive());}finally{if(process.isAlive())process.destroyForcibly().waitFor();}
  assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE operation_id=?",Integer.class,marker.operationId()));assertEquals("UNKNOWN",f.j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,marker.operationId()));
 }
}
