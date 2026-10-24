package ama.storage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Real HTTP read and formal missing-publication boundary on this task's owned schema. */
class Task10IntentIT extends Task09PipelineFixture {
 @Override javax.sql.DataSource createSource(){return ama.minutes.Task10Source.open();}
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
