package ama.task;
import ama.contract.TaskContracts.*;import com.fasterxml.jackson.databind.*;import java.nio.file.*;import java.time.*;import java.util.concurrent.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Exactly the explicit owned positive fixture; graceful same-ID restart retains volume, SQL and history. */
class DatabaseRecoveryIT {
 @Test void realDatabaseRestartThenWorkerRecoversWithinThirtySeconds()throws Exception{
  String container=System.getenv("AMA_B05_RECOVERY_CONTAINER");assertNotNull(container,"Requires explicit coordinator-approved own recovery container");assertEquals(System.getenv("AMA_TEST_CONTAINER"),container);
  var json=new ObjectMapper();var inspect=new ProcessBuilder("docker","inspect",container).start();JsonNode before=json.readTree(inspect.getInputStream()).get(0);assertEquals(0,inspect.waitFor());assertFalse(before.at("/HostConfig/AutoRemove").asBoolean());assertEquals("b01",before.at("/Config/Labels/ama.test-fixture").asText());assertTrue(before.get("Mounts").size()>0);assertTrue(before.get("Mounts").findValuesAsText("Destination").contains("/var/lib/mysql"));String exact=before.get("Id").asText();
  Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("tools/dev/mysql_fixture.py")))root=root.getParent();assertTrue(Files.exists(root.resolve(".tools/mysql-fixture").resolve(container).resolve("env.sh")));
  var f=new TaskFixture();var q=f.queue();var s=f.spec("DB_RECOVERY",3);String id=q.enqueue(s);var old=q.claim(s.pool(),"before-outage").orElseThrow();
  String prefix="recovery-"+System.currentTimeMillis();Path log=root.resolve("artifacts/phase-b/b05-functional/"+prefix+".log");
  var capture=new ProcessBuilder("python3",root.resolve("artifacts/phase-b/b05-functional/capture.py").toString(),container,prefix+"-before").redirectErrorStream(true).redirectOutput(log.toFile()).start();assertEquals(0,capture.waitFor());
  try(var oldConnection=f.source.getConnection()){
   var restart=new ProcessBuilder("docker","restart","--time","10",exact).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();assertTrue(restart.waitFor(60,TimeUnit.SECONDS));assertEquals(0,restart.exitValue());
   WorkerProcessIT.await(()->{try{return f.j.queryForObject("SELECT 1",Integer.class)==1;}catch(RuntimeException unavailable){return false;}},30);
   long available=System.nanoTime();assertThrows(java.sql.SQLException.class,()->oldConnection.createStatement().execute("SELECT 1"));
   var processFixture=new WorkerProcessIT();processFixture.f=f;Process worker=processFixture.process(s,false,root.resolve("artifacts/phase-b/b05-functional/"+prefix+"-worker.log"));
   try{WorkerProcessIT.await(()->"SUCCEEDED".equals(f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,id)),30);long millis=Duration.ofNanos(System.nanoTime()-available).toMillis();assertTrue(millis<30000);assertFalse(q.complete(old,f.success(s)));Files.writeString(root.resolve("artifacts/phase-b/b05-functional/"+prefix+"-elapsed.json"),"{\"from_real_db_connectable_to_completed_ms\":"+millis+"}");}finally{worker.destroy();worker.waitFor(5,TimeUnit.SECONDS);if(worker.isAlive())worker.destroyForcibly();}
  }
  var afterProcess=new ProcessBuilder("docker","inspect",exact).start();var after=json.readTree(afterProcess.getInputStream()).get(0);assertEquals(0,afterProcess.waitFor());assertEquals(before.get("Mounts"),after.get("Mounts"));assertEquals(before.get("Args"),after.get("Args"));assertEquals(before.at("/NetworkSettings/Ports"),after.at("/NetworkSettings/Ports"));assertEquals(8,f.j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
  var afterCapture=new ProcessBuilder("python3",root.resolve("artifacts/phase-b/b05-functional/capture.py").toString(),container,prefix+"-after").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();assertEquals(0,afterCapture.waitFor());
 }
}
