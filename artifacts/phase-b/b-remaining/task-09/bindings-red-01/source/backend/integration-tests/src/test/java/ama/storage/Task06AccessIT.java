package ama.storage;
import org.junit.jupiter.api.Test;
import java.util.*;import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
class Task06AccessIT extends Task06Fixture {
 @Override boolean installedTranscript(){return true;}
 @Test void effectiveMemberOwnerHistoryAndEpochTransferRemainDistinct()throws Exception {
  String raw=raw(),path="/meetings/"+meeting+"/transcript",ownerToken=token;
  var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);
  String save=json.writeValueAsString(Map.of("expectedRevisionToken","1","sourceRevisionId",raw,"segments",ids.stream().map(id->Map.of("segmentId",id,"body","Stored😀","marks",List.of())).toList()));
  var saved=call("PUT",path,save,"save");assertEquals(200,saved.statusCode(),saved.body());String working=body(saved).path("result").path("revisionId").asText();
  String member="task06-member-"+UUID.randomUUID(),memberToken=identity(member);
  var info=call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken","2","rosterDraftIds",List.of(member))),"info");assertEquals(200,info.statusCode(),info.body());
  assertEquals(200,call("POST","/meetings/"+meeting+"/roster-confirmations","{\"expectedRevisionToken\":\"3\"}","roster").statusCode());
  token=memberToken;assertNotEquals(200,call("GET",path,"",null).statusCode());assertEquals(403,call("GET",path+"/history","",null).statusCode());
  token=ownerToken;var confirmation=call("POST",path+"/confirmations",json.writeValueAsString(Map.of("expectedRevisionToken","4","savedRevisionId",working)),"confirm");assertEquals(200,confirmation.statusCode(),confirmation.body());
  token=memberToken;var memberRead=call("GET",path,"",null);assertEquals(200,memberRead.statusCode(),memberRead.body());assertFalse(body(memberRead).path("saved").has("processingInputId"));assertFalse(body(memberRead).has("selectedConfirmation"));assertEquals(403,call("PUT",path,save,"save").statusCode());assertEquals(403,call("GET",path+"/revisions/"+working,"",null).statusCode());
  String file=j.queryForObject("SELECT source_file_id FROM transcript_segment WHERE revision_id=? LIMIT 1",String.class,raw);assertEquals(200,call("GET",path+"/sources/"+file,"",null).statusCode());
  token=ownerToken;String next="task06-new-owner-"+UUID.randomUUID(),nextToken=identity(next);
  // Task12 governance is not installed. Controlled same-root transition exercises the existing
  // B03 authority/fence consequences, without claiming a transfer HTTP product endpoint.
  var tx=app.getBean(ama.db.TransactionRunner.class);var access=app.getBean(ama.access.MeetingAccess.class);var fences=app.getBean(ama.task.JdbcSubjectFences.class);
  tx.required(()->{var identities=access.tryLockIdentities(actor(),List.of(owner,next),false).orElseThrow();var gate=access.lockPrepared(identities,meeting,false).orElseThrow();access.require(gate,ama.access.MeetingAccess.Action.EDIT);var subject=fences.lock(new ama.contract.TaskContracts.SubjectRef("MEETING",meeting),false).orElseThrow();
   j.update("UPDATE meeting SET owner_id=?,revision=revision+1,access_revision=access_revision+1,cancel_epoch=cancel_epoch+1,transcript_confirmation_id=NULL,final_confirmation_id=NULL WHERE id=?",next,meeting);
   j.update("UPDATE meeting_search_projection SET meeting_revision=meeting_revision+1 WHERE meeting_id=?",meeting);fences.advance(subject,subject.businessVersion()+1,subject.cancelEpoch()+1,false);return null;});
  assertEquals(403,call("PUT",path,save,"save").statusCode());assertEquals(403,call("GET",path+"/revisions/"+working,"",null).statusCode());
  token=nextToken;var history=call("GET",path+"/revisions/"+working,"",null);assertEquals(200,history.statusCode(),history.body());assertEquals("Stored😀",body(history).path("selected").path("segments").get(0).path("body").asText());assertEquals("INELIGIBLE",body(history).path("sourceBytes").get(0).path("eligibility").asText());assertFalse(body(history).path("capabilities").path("canRestore").asBoolean());
  assertNotEquals(200,call("GET",path+"/revisions/"+working+"/sources/"+file,"",null).statusCode());
  assertNotEquals(200,call("POST",path+"/restorations",json.writeValueAsString(Map.of("expectedRevisionToken","6","sourceRevisionId",working)),"restore").statusCode());
  Files.writeString(receipt.resolve("member-projection.json"),memberRead.body());Files.writeString(receipt.resolve("post-transfer-stored-inspection.json"),history.body());
 }
}
