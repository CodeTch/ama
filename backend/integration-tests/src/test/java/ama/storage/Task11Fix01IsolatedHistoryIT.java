package ama.storage;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine isolated history; original primary queued replacement is never in this queue. */
class Task11Fix01IsolatedHistoryIT extends Task11ZeroEffectIT {
 @Override Task11KnowledgeFixture knowledgeFixture() throws Exception {
  assertEquals("task11-fix01-isolated",System.getenv("AMA_TASK11_FIXTURE"));
  Path original=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"artifacts/phase-b/b-remaining/task-11/publication-zero-effect-01");
  Path retained;try(var paths=Files.list(original)){retained=paths.filter(x->x.getFileName().toString().startsWith("pipeline-")).findFirst().orElseThrow().resolve("knowledge-retained-unresolved.json");}
  var attached=new Task11KnowledgeFixture(receipt,owner,retained);
  attached.grant(owner);
  revocation=new Task11ResponseLossFixture(attached,receipt,"REVOKE_BEFORE_PUBLISH",owner);
  preserveKnowledge=true;
  return attached;
 }
 @Override void runPublication(Map<String,Object> accepted) throws Exception {
  super.runPublication(accepted);
  String publication=(String)accepted.get("publicationId"),old=(String)accepted.get("taskId");
  var service=ama.api.Task11RuntimeProbe.publication(app);
  knowledge.grant(owner);
  var before=j.queryForList("SELECT id,state,request_hash,request_manifest,no_effect_basis,no_effect_evidence,CAST(no_effect_verified_at AS CHAR) verified FROM external_execution WHERE task_id=?",old);
  var next=service.resubmit(actor(),meeting,publication,"fix01-same-logical",revision(),j.queryForObject("SELECT revision FROM publication WHERE id=?",Long.class,publication),(String)accepted.get("confirmationId"),"CONTROLLED_KNOWLEDGE_V1","controlled-library","controlled-root",UUID.randomUUID().toString());
  assertEquals(ama.contract.ExternalContracts.ObservationResult.APPLIED,service.recoverOriginalReceipt(actor(),meeting,publication,old));
  assertEquals(before,j.queryForList("SELECT id,state,request_hash,request_manifest,no_effect_basis,no_effect_evidence,CAST(no_effect_verified_at AS CHAR) verified FROM external_execution WHERE task_id=?",old));
  String task=(String)next.get("taskId");
  assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=?",Integer.class,task));
  assertEquals("PREPARED",j.queryForObject("SELECT state FROM external_execution WHERE task_id=?",String.class,task));
  assertEquals(1,revocation.read().get("postPublication").asInt());
  Files.writeString(receipt.resolve("fix01-isolated-history.json"),json.writeValueAsString(Map.of("original",accepted,"replacement",next,"oldExecution",before,"sameLogicalNewSubmissionAck","APPLIED","publicationPosts",1,"replacementPublicationPosts",0,"qualification","One genuine initial permanent rejection; same-key no2 accepted and immutable old ACK; no replacement claim/marker/send")));
 }
}
