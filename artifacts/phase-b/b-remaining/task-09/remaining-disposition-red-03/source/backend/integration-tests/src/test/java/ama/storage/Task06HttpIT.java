package ama.storage;
import org.junit.jupiter.api.Test;
import java.util.*;import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
class Task06HttpIT extends Task06Fixture {
 @Override boolean installedTranscript(){return true;}
 @Test void installedLOCAL()throws Exception {exercise();}
 @Test void installedS3()throws Exception {exercise();}
 void exercise()throws Exception {
  assertTrue(ama.api.Task06RuntimeProbe.installed(app));String raw=raw(),path="/meetings/"+meeting+"/transcript";
  var current=call("GET",path,"",null);assertEquals(200,current.statusCode(),current.body());assertTrue(body(current).get("saved").isNull());assertEquals("private, no-store",current.headers().firstValue("Cache-Control").orElseThrow());
  var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);
  String save=json.writeValueAsString(Map.of("expectedRevisionToken","1","sourceRevisionId",raw,"segments",List.of(Map.of("segmentId",ids.get(0),"body","HTTP😀 \"quoted\"","marks",List.of("KEY")),Map.of("segmentId",ids.get(1),"body","Beta","marks",List.of()))));
  var saved=call("PUT",path,save,"save");assertEquals(200,saved.statusCode(),saved.body());String working=body(saved).path("result").path("revisionId").asText();assertFalse(working.isBlank());
  var confirmed=call("POST",path+"/confirmations",json.writeValueAsString(Map.of("expectedRevisionToken","2","savedRevisionId",working)),"confirm");assertEquals(200,confirmed.statusCode(),confirmed.body());String confirmation=body(confirmed).path("result").path("confirmationId").asText();
  for(String tail:List.of("","/history","/revisions/"+working,"/marks","/revisions/"+working+"/marks","/confirmation","/confirmations/"+confirmation)){
   var response=call("GET",path+tail,"",null);assertEquals(200,response.statusCode(),tail+response.body());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());Files.writeString(receipt.resolve("http-"+Integer.toHexString(tail.hashCode())+".json"),response.body());
  }
  String file=j.queryForObject("SELECT source_file_id FROM transcript_segment WHERE revision_id=? LIMIT 1",String.class,raw);
  for(String tail:List.of("/sources/"+file,"/revisions/"+working+"/sources/"+file)){var response=call("GET",path+tail,"",null);assertEquals(200,response.statusCode(),response.body());assertEquals("Alpha😀\nBeta",response.body());assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElseThrow());}
  var marks=call("PUT",path+"/marks",json.writeValueAsString(Map.of("expectedRevisionToken","3","sourceRevisionId",working,"segments",ids.stream().map(id->Map.of("segmentId",id,"marks",List.of())).toList())),"marks");assertEquals(200,marks.statusCode(),marks.body());
  assertTrue(body(call("GET",path+"/confirmation","",null)).get("confirmation").isNull());
  var restored=call("POST",path+"/restorations",json.writeValueAsString(Map.of("expectedRevisionToken","4","sourceRevisionId",working)),"restore");assertEquals(200,restored.statusCode(),restored.body());
  var replay=call("PUT",path,save,"save");assertEquals(200,replay.statusCode(),replay.body());assertTrue(body(replay).get("replayed").asBoolean());assertFalse(body(replay).get("resultIsCurrent").asBoolean());
  var bad=call("PUT",path,save.replace("\"expectedRevisionToken\":\"1\"","\"expectedRevisionToken\":\"01\""),"invalid");assertEquals(400,bad.statusCode(),bad.body());assertEquals("private, no-store",bad.headers().firstValue("Cache-Control").orElseThrow());
  Files.writeString(receipt.resolve("installed-composition.json"),json.writeValueAsString(ama.api.Task06RuntimeProbe.actual(app).qualification()));
 }
}
