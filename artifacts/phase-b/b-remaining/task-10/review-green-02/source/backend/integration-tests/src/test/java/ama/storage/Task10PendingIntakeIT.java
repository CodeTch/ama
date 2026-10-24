package ama.storage;
import java.util.*;import ama.transcript.*;import static org.junit.jupiter.api.Assertions.*;
class Task10PendingIntakeIT extends Task10GenerationFixture {
 @Override void beforeWorker(String task){new JdbcMinutesEditor(ama.api.Task09RuntimeProbe.components(app).authority()).intent(actor(),meeting,"sticky-editor",4);}
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  String file=upload("Second actual import");var response=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","4","orderedFileIds",List.of(file))),"second-import");assertEquals(202,response.statusCode(),response.body());String task=body(response).get("taskId").asText();
  try(var loop=ama.worker.WorkerLoop.contextual(app.getBean(ama.task.JdbcTaskQueue.class),Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(app.getBean(ama.task.JdbcTaskQueue.class),lease,settings(),cancel)),"TASK10_PENDING_IMPORT")){loop.start();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<deadline&&List.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(50);}
  assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertEquals("STALE",j.queryForObject("SELECT state FROM generation_candidate WHERE id=?",String.class,candidate.get("id")));
 }
}
