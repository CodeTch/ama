package ama.storage;
import ama.contract.TranscriptContracts.*;
import ama.contract.MinutesContracts.Mark;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task06SavedCommandsIT extends Task06Fixture {
 @Test void immutableSaveMarksRestoreAndStableConfirmationReceipts()throws Exception {
  String raw=raw();var service=isolated();var segments=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);
  var command=new Command(Operation.SAVE,1,raw,List.of(new Edit(segments.get(0),"Edited😀",List.of(Mark.KEY)),new Edit(segments.get(1),"Beta",List.of())));
  var saved=assertDoesNotThrow(()->service.mutate(actor(),meeting,"save",command,UUID.randomUUID().toString()),"Explicit save must create full immutable WORKING");
  String working=(String)((Map<?,?>)saved.get("result")).get("revisionId");
  assertNotEquals(raw,working);assertEquals(2,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));
  assertEquals(raw,j.queryForObject("SELECT parent_id FROM transcript_revision WHERE id=?",String.class,working));assertNull(j.queryForObject("SELECT producer_task_id FROM transcript_revision WHERE id=?",String.class,working));
  var confirm=new Command(Operation.CONFIRM,2,working,List.of());var receipt=service.mutate(actor(),meeting,"confirm",confirm,UUID.randomUUID().toString());
  String confirmation=(String)((Map<?,?>)receipt.get("result")).get("confirmationId");assertNotNull(confirmation);
  assertEquals(3,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));
  var repeat=service.mutate(actor(),meeting,"repeat",new Command(Operation.CONFIRM,3,working,List.of()),UUID.randomUUID().toString());assertEquals(confirmation,((Map<?,?>)repeat.get("result")).get("confirmationId"));assertEquals(3,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));
  var cleared=service.mutate(actor(),meeting,"marks",new Command(Operation.MARKS,3,working,segments.stream().map(id->new Edit(id,null,List.of())).toList()),UUID.randomUUID().toString());
  assertNull(j.queryForObject("SELECT transcript_confirmation_id FROM meeting WHERE id=?",String.class,meeting));assertEquals(4,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));
  var replay=service.mutate(actor(),meeting,"confirm",confirm,UUID.randomUUID().toString());assertEquals(true,replay.get("replayed"));assertEquals(false,replay.get("resultIsCurrent"));assertNull(j.queryForObject("SELECT transcript_confirmation_id FROM meeting WHERE id=?",String.class,meeting));
  service.mutate(actor(),meeting,"restore",new Command(Operation.RESTORE,4,working,List.of()),UUID.randomUUID().toString());assertEquals(5,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_segment_mark WHERE revision_id=(SELECT transcript_revision_id FROM meeting WHERE id=?)",Integer.class,meeting));
  assertEquals(List.of("Alpha😀","Beta"),j.queryForList("SELECT body FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw));
 }
 @Test void newIntakeClearsPointerThenNewConfirmationReattachesOriginalIdentity()throws Exception {
  String raw=raw();var service=isolated();var segments=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);var saved=service.mutate(actor(),meeting,"save",new Command(Operation.SAVE,1,raw,segments.stream().map(id->new Edit(id,"Stable",List.of())).toList()),UUID.randomUUID().toString());String working=(String)((Map<?,?>)saved.get("result")).get("revisionId");var command=new Command(Operation.CONFIRM,2,working,List.of());var first=service.mutate(actor(),meeting,"confirm",command,UUID.randomUUID().toString());String confirmation=(String)((Map<?,?>)first.get("result")).get("confirmationId");
  var intake=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","3","orderedFileIds",List.of(upload("next source")))),"next-input");assertEquals(202,intake.statusCode(),intake.body());String task=body(intake).get("taskId").asText();var queue=app.getBean(ama.task.JdbcTaskQueue.class);try(var loop=ama.worker.WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),cancel)),"TASK06_REATTACH")){loop.start();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end&&List.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(40);}assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
  assertNull(j.queryForObject("SELECT transcript_confirmation_id FROM meeting WHERE id=?",String.class,meeting));var replay=service.mutate(actor(),meeting,"confirm",command,UUID.randomUUID().toString());assertEquals(false,replay.get("resultIsCurrent"));assertNull(j.queryForObject("SELECT transcript_confirmation_id FROM meeting WHERE id=?",String.class,meeting));
  var reattached=service.mutate(actor(),meeting,"reattach",new Command(Operation.CONFIRM,4,working,List.of()),UUID.randomUUID().toString());assertEquals(confirmation,((Map<?,?>)reattached.get("result")).get("confirmationId"));assertEquals(5,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_confirmation WHERE revision_id=?",Integer.class,working));
 }
}
