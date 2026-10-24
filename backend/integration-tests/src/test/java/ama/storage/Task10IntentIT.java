package ama.storage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Real HTTP read and formal missing-publication boundary on this task's owned schema. */
class Task10IntentIT extends Task09PipelineFixture {
 @Override javax.sql.DataSource createSource(){return ama.minutes.Task10Source.open();}
 @Test void controlledIntentIsStickyReplaysThroughHttpAndNeverChangesBusinessState()throws Exception {
  var service=new ama.transcript.JdbcMinutesEditor(ama.api.Task09RuntimeProbe.components(app).authority());
  var before=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);var subject=j.queryForMap("SELECT * FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",meeting);
  var first=assertDoesNotThrow(()->service.intent(actor(),meeting,"tab-one",0));assertEquals(false,first.get("replayed"));
  assertEquals(1L,j.queryForObject("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Long.class,meeting));
  service.intent(actor(),meeting,"tab-two",0);
  var replay=call("POST","/meetings/"+meeting+"/minutes/editor-intents","{\"expectedRevision\":0,\"expectedRevisionToken\":\"0\"}","tab-one");
  assertEquals(200,replay.statusCode(),replay.body());assertTrue(body(replay).get("replayed").asBoolean());assertEquals("2",body(replay).at("/current/intentEpochToken").asText());
  assertEquals(before,j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));assertEquals(subject,j.queryForMap("SELECT * FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",meeting));
  // Fixture-only root progression establishes the intent-specific stale-receipt rule.
  j.update("UPDATE meeting SET revision=1 WHERE id=?",meeting);j.update("UPDATE task_subject SET business_version=1 WHERE subject_kind='MEETING' AND subject_id=?",meeting);
  var stale=call("POST","/meetings/"+meeting+"/minutes/editor-intents","{\"expectedRevisionToken\":\"0\"}","tab-one");
  assertEquals(409,stale.statusCode(),stale.body());assertEquals("2",body(stale).at("/current/intentEpochToken").asText());
  var conflict=assertThrows(ama.contract.TranscriptContracts.Failure.class,()->service.intent(actor(),meeting,"tab-one",1));assertEquals("IDEMPOTENCY_CONFLICT",conflict.code());
  j.update("UPDATE meeting_minutes_editor_guard SET intent_epoch=9223372036854775806 WHERE meeting_id=?",meeting);
  var exhausted=assertThrows(ama.contract.TranscriptContracts.Failure.class,()->service.intent(actor(),meeting,"exhausted",1));assertEquals("INTENT_EXHAUSTED",exhausted.code());
  assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='EDIT_INTENT'",Integer.class,meeting));
  j.update("DELETE FROM meeting_minutes_editor_guard WHERE meeting_id=?",meeting);
  assertThrows(ama.contract.TranscriptContracts.Failure.class,()->service.intent(actor(),meeting,"tab-two",0));
  service.intent(actor(),meeting,"historical-unknown",1);assertEquals(1L,j.queryForObject("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Long.class,meeting));
 }
 @Test void genuineZeroUnknownAndFormalNewIntentRemainDistinct()throws Exception {
  String path="/meetings/"+meeting+"/minutes/editor-intent";
  var known=call("GET",path,"",null);assertEquals(200,known.statusCode(),known.body());
  assertEquals("KNOWN",body(known).get("state").asText());assertEquals("0",body(known).get("intentEpochToken").asText());
  var denied=call("POST",path+"s","{\"expectedRevisionToken\":\"0\"}","intent-disabled");
  assertEquals(503,denied.statusCode(),denied.body());assertTrue(denied.body().contains("TASK11_NOT_INSTALLED"));
  assertEquals(0L,j.queryForObject("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Long.class,meeting));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='EDIT_INTENT'",Integer.class,meeting));
  j.update("DELETE FROM meeting_minutes_editor_guard WHERE meeting_id=?",meeting);
  var unknown=call("GET",path,"",null);assertEquals(200,unknown.statusCode(),unknown.body());
  assertEquals("UNKNOWN",body(unknown).get("state").asText());assertTrue(body(unknown).get("intentEpochToken").isNull());
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_minutes_editor_guard WHERE meeting_id=?",Integer.class,meeting));
  token=identity("task10-other-"+java.util.UUID.randomUUID());
  assertEquals(403,call("GET",path,"",null).statusCode());
 }
}
