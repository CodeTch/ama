package ama.storage;
import java.nio.file.*;import java.util.*;import com.fasterxml.jackson.databind.node.ObjectNode;import static org.junit.jupiter.api.Assertions.*;
/** A0: sole fresh initial rejection. No replacement acceptance/claim/send. */
class Task11Fix02LastBaselineIT extends Task11ZeroEffectIT {
 Path retainedProvider()throws Exception{Path root=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"artifacts/phase-b/b-remaining/task-11/replacement-fix02-composition-baseline-01");try(var paths=Files.list(root)){return paths.filter(p->p.getFileName().toString().startsWith("pipeline-")).findFirst().orElseThrow().resolve("knowledge-retained-provider-attached.json");}}
 @Override Task11KnowledgeFixture knowledgeFixture()throws Exception{
  assertEquals("task11-fix02-isolated",System.getenv("AMA_TASK11_FIXTURE"));preserveKnowledge=true;Path result=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),".superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-fix02-provider-restart-result-20261025.json");var restarted=json.readTree(Files.readAllBytes(result));assertTrue(restarted.get("logicalStateUnchanged").asBoolean());assertEquals(1555480L,restarted.get("newPid").asLong());assertEquals("17005861",restarted.get("startTicks").asText());assertEquals("17005861",Files.readString(Path.of("/proc/1555480/stat")).split(" ")[21]);assertEquals("6141572ab7f9376760261894f233f8bf3a24c23e366ca8d4091fd300c8c6a658",restarted.get("sourceSha256").asText());
  var retained=(ObjectNode)json.readTree(Files.readAllBytes(retainedProvider()));retained.put("pid",restarted.get("newPid").asLong());retained.put("pgid",restarted.get("pgid").asLong());Path current=receipt.resolve("provider-current-source-retained.json");Files.writeString(current,json.writeValueAsString(retained));var provider=new Task11KnowledgeFixture(receipt,owner,current);provider.grant(owner);revocation=new Task11ResponseLossFixture(provider,receipt,"REVOKE_BEFORE_PUBLISH",owner);return provider;
 }

 @Override void runPublication(Map<String,Object> accepted)throws Exception{
  String task=(String)accepted.get("taskId");assertEquals(List.of(task),j.queryForList("SELECT id FROM durable_task WHERE pool='KNOWLEDGE_PUBLISH' AND state IN ('READY','RUNNING') ORDER BY id",String.class));super.runPublication(accepted);
  knowledge.grant(owner);Files.writeString(receipt.resolve("fix02-last-baseline.json"),json.writeValueAsString(Map.of("accepted",accepted,"actor",owner,"initialPublicationPosts",1,"replacementPublicationPosts",0,"sameActorRegranted",true,"qualification","One fresh initial real permanent rejection; no replacement yet. Closed history held for A1 final S/F race and A2 same-operation marker/contradiction.")));
 }
}
