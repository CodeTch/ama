package ama.storage;

import ama.transcript.*;
import ama.task.*;
import ama.contract.MinutesContracts.Failure;
import ama.contract.MinutesContracts.FailureCode;
import ama.contract.ProcessingContracts.ImportRequest;
import ama.contract.TranscriptContracts.*;
import ama.contract.TaskContracts.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine committed HTTP winners interposed after initial no-receipt lookup; injected preparation failures. */
class Task13ReplayWinnerIT extends Task11B5RetainedDecisionsIT {
 @FunctionalInterface interface Action {void run()throws Exception;}
 final List<Map<String,Object>> outcomes=new ArrayList<>();
 final class Race {
  final Failure failure;final Action winner;int calls;com.fasterxml.jackson.databind.JsonNode accepted;Map<String,List<Map<String,Object>>> committed;
  Race(FailureCode code,Action winner){failure=new Failure(code);this.winner=winner;}
  void fire(){assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());assertEquals(0,calls++,"Winner must execute once outside preparation roots");try{winner.run();committed=rows(j);}catch(Exception error){throw new IllegalStateException("Actual HTTP winner failed",error);}throw failure;}
  void rejected(Action outer)throws Exception{assertSame(failure,assertThrows(Failure.class,outer::run));assertEquals(1,calls);assertTrue(committed.equals(rows(j)),"Losing preparation changed winner rows");}
  void replayed(String family){assertEquals(1,calls);assertTrue(committed.equals(rows(j)),"Already accepted ACK changed winner rows");outcomes.add(Map.of("meeting",meeting,"family",family,"failure",failure.code().name(),"winnerCommittedBeforeFailure",true,"loserPreservedOriginalFailure",true,"acceptedReplayIndependentOfPreparation",true,"tables",committed.keySet()));}
 }
 TranscriptAcquisitionBudget limits(){return new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256);}
 @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {
  if(knowledge!=null)return knowledge;
  Path baseline=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),".superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-retained-runtime-20261027/retained-decisions-02/pipeline-6eb73cdf-e466-4b3f-a44e-f5df5c3a732c");
  var prior=json.readTree(Files.readAllBytes(baseline.resolve("knowledge-before-start.json")));assertFalse(json.readTree(Files.readAllBytes(baseline.resolve("knowledge-closed.json"))).path("alive").asBoolean());
  return knowledge=Task11KnowledgeFixture.reopenOnly(receipt,owner,ROOT.resolve("knowledge"),prior.path("port").asInt(),json.convertValue(prior.path("identities"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
 }
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  Path out=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertEquals("replay-winner-02",out.getFileName().toString());assertTrue(Files.isRegularFile(out.resolve("consumed.json")));
  Map<String,List<Map<String,Object>>> first=null;
  try {
   for(var code:List.of(FailureCode.PROFILE_UNAVAILABLE,FailureCode.PROVENANCE_UNAVAILABLE)){
    if(first!=null)newMeeting();runWinners(code);if(first==null)first=rows(j);else preserve(first);
   }
   assertEquals(8,outcomes.size());assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",0L),ama.api.Task11RuntimeProbe.publication(app).traffic());
   Files.writeString(receipt.resolve("actual-preparation-winners.json"),json.writeValueAsString(Map.of("outcomes",outcomes,"noModelProviderStarted",true,"noExternalExecutionForNewTasks",true,"faultType","injected exact failure after independent actual HTTP winner committed; no corrupt archive/SQL state manufacture")),StandardOpenOption.CREATE_NEW);
  }finally{preserve(previous);Files.writeString(out.resolve("prior-business-preserved.json"),json.writeValueAsString(Map.of("tables",previous.keySet(),"oldRowsPreserved",true)),StandardOpenOption.CREATE_NEW);}
 }
 void runWinners(FailureCode code)throws Exception {
  String file=upload("Actual replay integrity source"),key="winner-intake";var request=new ImportRequest(0,List.of(file));var accepted=new com.fasterxml.jackson.databind.JsonNode[1];
  var intakeRace=new Race(code,()->{var reply=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),key);assertEquals(202,reply.statusCode());accepted[0]=body(reply);});
  var c=ama.api.Task09RuntimeProbe.components(app);var queue=app.getBean(JdbcTaskQueue.class);var outbox=app.getBean(JdbcOutbox.class);
  var intake=new JdbcProcessingIntake(source,app.getBean(ama.db.TransactionRunner.class),app.getBean(ama.meeting.JdbcMeetingAccess.class),c,app.getBean(JdbcSubjectFences.class),queue,outbox,new ama.audit.JdbcAuditAdapter(source),(a,m,r)->{intakeRace.fire();throw new AssertionError("unreachable");});
  intakeRace.rejected(()->intake.accept(actor(),meeting,key,request,UUID.randomUUID().toString()));String rawTask=accepted[0].path("taskId").asText();assertFalse(rawTask.isEmpty());assertEquals(rawTask,intake.accept(actor(),meeting,key,request,UUID.randomUUID().toString()).taskId());intakeRace.replayed("PROCESSING_IMPORT");
  runRaw(rawTask);String raw=j.queryForObject("SELECT output_id FROM durable_task WHERE id=? AND subject_id=? AND state='SUCCEEDED' AND output_kind='TRANSCRIPT_REVISION'",String.class,rawTask,meeting);assertNotNull(raw,"Successful RAW task must carry its own output revision");assertNull(j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting),"RAW does not create the human-saved transcript pointer");assertEquals(1,revision());
  var edits=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw).stream().map(id->new Edit(id,"Actual saved winner",List.of())).toList();
  var command=new Command(Operation.SAVE,1,raw,edits);var saveRace=new Race(code,()->{var payload=Map.of("expectedRevisionToken","1","sourceRevisionId",raw,"segments",edits.stream().map(e->Map.of("segmentId",e.segmentId(),"body",e.body(),"marks",e.marks())).toList());var reply=call("PUT","/meetings/"+meeting+"/transcript",json.writeValueAsString(payload),"winner-save");assertEquals(200,reply.statusCode());});
  var armed=new AtomicBoolean();var correction=ama.api.Task09RuntimeProbe.actual(app).correction();
  var saved=new JdbcSavedTranscripts(c.authority(),()->{if(armed.get())saveRace.fire();return snapshot();},limits(),correction,app.getBean(JdbcSubjectFences.class),outbox,new ama.audit.JdbcAuditAdapter(source),new SavedTranscriptTransitions("AUTHORITY_CONTROLLED","AUTHORITY_CONTROLLED","2",List.of(),List.of()));armed.set(true);
  saveRace.rejected(()->saved.mutate(actor(),meeting,"winner-save",command,UUID.randomUUID().toString()));assertEquals(true,saved.mutate(actor(),meeting,"winner-save",command,UUID.randomUUID().toString()).get("replayed"));saveRace.replayed("TRANSCRIPT_SAVE");
  String working=j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting);var confirmed=call("POST","/meetings/"+meeting+"/transcript/confirmations",json.writeValueAsString(Map.of("expectedRevisionToken","2","savedRevisionId",working)),"winner-confirm");assertEquals(200,confirmed.statusCode());assertEquals(3,revision());
  install();knowledge=knowledgeFixture();restartForFinal();var authority=ama.api.Task09RuntimeProbe.components(app).authority();
  var initialRace=new Race(code,()->postMinutes("/initial-settings",Map.of("expectedRevisionToken","3","templateRevisionId",revision),"winner-initial",200));
  var initial=new JdbcMinutesInitialSettings(authority,()->{initialRace.fire();return snapshot();},prompt,model);
  initialRace.rejected(()->initial.initialize(actor(),meeting,"winner-initial",3,revision,UUID.randomUUID().toString()));assertEquals(true,initial.initialize(actor(),meeting,"winner-initial",3,revision,UUID.randomUUID().toString()).get("replayed"));initialRace.replayed("MINUTES_INITIAL_SETTINGS");assertEquals(4,revision());
  var generation=new com.fasterxml.jackson.databind.JsonNode[1];var generationRace=new Race(code,()->generation[0]=postMinutes("/generations",Map.of("expectedRevisionToken","4","scope","FULL"),"winner-generation",202));
  var preparation=new JdbcMinutesGenerationPreparation(authority,()->{generationRace.fire();return snapshot();},pin,prompt,model,limits());queue=app.getBean(JdbcTaskQueue.class);outbox=app.getBean(JdbcOutbox.class);
  var commands=new JdbcMinutesGenerationCommands(authority,preparation,queue,outbox,new ama.audit.JdbcAuditAdapter(source));generationRace.rejected(()->commands.enqueue(actor(),meeting,"winner-generation",4,UUID.randomUUID().toString()));assertEquals(true,commands.enqueue(actor(),meeting,"winner-generation",4,UUID.randomUUID().toString()).get("replayed"));generationRace.replayed("MINUTES_GENERATE");
  String task=generation[0].at("/result/taskId").asText();assertFalse(task.isEmpty());var lease=queue.claim("MINUTES_GENERATE","REPLAY_WINNER_OWNED_STOP").orElseThrow();assertEquals(task,lease.taskId());assertTrue(queue.complete(lease,new TaskResult(Outcome.FAILED,null,"CONTROLLED_TEST_STOP")));assertEquals("FAILED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Integer.class,task));
  Files.writeString(receipt.resolve("actual-winner-"+code+".json"),json.writeValueAsString(Map.of("meeting",meeting,"failure",code.name(),"rawTask",rawTask,"generationTask",task,"generationEnd","FAILED_CONTROLLED_TEST_STOP_BEFORE_MARKER","cases",outcomes.stream().filter(r->r.get("meeting").equals(meeting)).toList())),StandardOpenOption.CREATE_NEW);
 }
 void runRaw(String task)throws Exception {
  var queue=app.getBean(JdbcTaskQueue.class);try(var loop=ama.worker.WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->{assertEquals(task,lease.taskId());return TranscriptImportRuntime.prepare(queue,lease,settings(),cancel);}),"REPLAY_WINNER_RAW")){
   loop.pollOnce("TRANSCRIPT_IMPORT");long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end&&Set.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(50);
  }assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
 }
}
