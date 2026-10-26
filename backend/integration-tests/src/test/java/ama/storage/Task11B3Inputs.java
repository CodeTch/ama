package ama.storage;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;import java.time.Instant;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public release/source validation precedes private path inspection, JDBC, context or owned children. */
final class Task11B3Inputs {
 static final String ACK="task-11-b3-prepared-local-r2-configuration-ack-20261026.json",ACK_SHA="db267767a115d8a1262c10b31a85d06eb02bf3bb73820b8f53808bcb5bce4727",LABEL="b3-prepared-local-01",SELECTOR="Task11B3PreparedLocalIT";
 static final String FIXTURE="task11-b2-exact-isolated",OWNER="task11b2-d9e2fdcc611e4e21bb360e539ca46258";
 static final Set<String> PINS=Set.of("readbackAdoption","readbackReview","readbackFacts","readbackCustody","sourceBase","allocation","overlay","credentials","retainedOrigin");
 static final Map<String,Integer> BUDGET=Map.ofEntries(Map.entry("servletRequests",22),Map.entry("parentContexts",2),Map.entry("childContexts",1),Map.entry("sessionInsertsMax",6),Map.entry("sessionDeletesMax",6),Map.entry("faultPoints",12),Map.entry("localDecisions",1),Map.entry("newSealedAcknowledgments",1),Map.entry("maintenanceScheduledPassesMax",3),Map.entry("freshJvmChildren",1),Map.entry("providerGetIncrements",0),Map.entry("destinationPostIncrements",0),Map.entry("publicationPostIncrements",0),Map.entry("sqliteObservations",0),Map.entry("grants",0),Map.entry("nativeStarts",0),Map.entry("claims",0),Map.entry("successors",0));
 record Inputs(Path root,Path workspace,Path owned,Path evidence,JsonNode configuration,JsonNode allocation,JsonNode origin){}
 static JsonNode pin(Path w,JsonNode cfg,String key)throws Exception {var pin=cfg.path("inputPins").path(key);assertTrue(Path.of(pin.path("path").asText()).getNameCount()==1);return Task11B2RuntimeFixture.publicJson(w.resolve(pin.path("path").asText()),pin.path("sha256").asText());}
 static void budget(JsonNode budget){assertTrue(budget.isObject());assertEquals(BUDGET.keySet(),fields(budget));BUDGET.forEach((key,value)->{var n=budget.get(key);assertTrue(n.isIntegralNumber());assertEquals(java.math.BigInteger.valueOf(value),n.bigIntegerValue());});}
 static Set<String> fields(JsonNode value){var names=new HashSet<String>();value.fieldNames().forEachRemaining(names::add);return names;}
 static void validateConfiguration(Path root,Path w,JsonNode cfg)throws Exception {
  assertEquals("B3_PREPARED_LOCAL_CONFIGURATION_INERT",cfg.path("status").asText());
  for(String key:List.of("runtimeAuthorized","grantAuthorized","nativeStartAuthorized","sendAuthorized")){assertTrue(cfg.path(key).isBoolean());assertFalse(cfg.get(key).asBoolean());}
  assertEquals(LABEL,cfg.path("label").asText());assertEquals("it",cfg.path("group").asText());assertEquals(SELECTOR,cfg.path("selector").asText());assertEquals(FIXTURE,cfg.path("fixtureName").asText());assertEquals(OWNER,cfg.path("owner").asText());
  assertEquals("ama_t11b2_d9e2fdcc611e_behavior",cfg.path("schema").asText());assertEquals("t11b2_d9e2fdcc611e",cfg.path("username").asText());assertEquals("11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad",cfg.path("cid").asText());assertEquals(46151,cfg.path("port").asInt());
  assertEquals("3588aaba-ecfa-4889-8954-46c1454447d4",cfg.path("taskId").asText());assertEquals("65fa6b21-22da-4a2e-a886-341b16978106",cfg.path("executionId").asText());assertEquals("896456c1-cc73-4a8c-803d-8b5bb058d4c7",cfg.path("operationId").asText());assertEquals("142a236c-bcd6-4608-8e93-2dd5446c5fa9",cfg.path("publicationId").asText());assertEquals("36db643e-5fbd-4f9a-9a85-a7e6b7d76e38",cfg.path("meetingId").asText());assertEquals(13,cfg.path("meetingRevision").asInt());assertEquals(6,cfg.path("publicationRevision").asInt());
  budget(cfg.path("budget"));assertEquals(PINS,fields(cfg.path("inputPins")));
  for(String key:PINS){var pin=cfg.path("inputPins").get(key);assertTrue(Path.of(pin.path("path").asText()).getNameCount()==1);assertEquals(pin.path("sha256").asText(),Task11B2RuntimeFixture.digest(Task11B2RuntimeFixture.publicBytes(w.resolve(pin.path("path").asText()))));}
  var adoption=pin(w,cfg,"readbackAdoption");assertEquals("EXISTING_SUCCESSOR_READBACK_ADOPTED_FOR_B3",adoption.path("status").asText());assertEquals(cfg.at("/inputPins/readbackFacts/sha256"),adoption.get("factsSha256"));assertEquals(cfg.at("/inputPins/readbackReview/sha256"),adoption.get("reviewSha256"));assertEquals(0,adoption.path("sqliteBudgetRemainingForReadbackScope").asInt(-1));
  var facts=pin(w,cfg,"readbackFacts");assertEquals(cfg.path("taskId"),facts.get("existingTaskId"));assertEquals(cfg.path("executionId"),facts.get("existingExecutionId"));assertTrue(facts.path("businessBeforeAfterIdentical").asBoolean());assertEquals("READY",facts.path("taskState").asText());assertEquals("PREPARED",facts.path("executionState").asText());assertEquals(cfg.path("publicationRevision"),facts.get("publicationRevision"));
  assertEquals("artifacts/phase-b/b-remaining/task-11/b2-exact-retry-readback-01/accepted-rows-after.json",cfg.path("baselineRows").asText());assertEquals(17,Task11B2RuntimeFixture.publicJson(root.resolve(cfg.path("baselineRows").asText()),cfg.path("baselineRowsSha256").asText()).path("rows").size());
  assertEquals("task-11-b3-prepared-local-r2-source-checkpoint-20261026.json",cfg.path("runtimeSourceCheckpoint").asText());assertEquals("task-11-b3-prepared-local-runtime-release-20261026.json",cfg.path("runtimeReleaseFile").asText());
  var release=Task11B2RuntimeFixture.JSON.readTree(Task11B2RuntimeFixture.publicBytes(w.resolve(cfg.path("runtimeReleaseFile").asText())));
  assertEquals("B3_PREPARED_LOCAL_RUNTIME_RELEASED_ONCE",release.path("status").asText());for(String key:List.of("label","group","selector","fixtureName"))assertEquals(cfg.get(key),release.get(key));assertEquals(ACK_SHA,release.path("configurationAckSha256").asText());
  assertTrue(release.path("maxInvocations").isIntegralNumber());assertEquals(java.math.BigInteger.ONE,release.get("maxInvocations").bigIntegerValue());
  for(String key:List.of("oneGrantAuthorized","nativeStartAuthorized","sendAuthorized")){assertTrue(release.path(key).isBoolean());assertFalse(release.get(key).asBoolean());}budget(release.path("budget"));
  Instant issued=Instant.parse(release.path("issuedUtc").asText()),deadline=Instant.parse(release.path("startBeforeUtc").asText()),now=Instant.now();assertFalse(now.isBefore(issued));assertTrue(issued.isBefore(deadline)&&now.isBefore(deadline));
  String checkpointSha=release.path("sourceCheckpointSha256").asText();assertTrue(checkpointSha.matches("[0-9a-f]{64}"));var cp=Task11B2RuntimeFixture.publicJson(w.resolve(cfg.path("runtimeSourceCheckpoint").asText()),checkpointSha);assertTrue(cp.path("files").isArray()&&!cp.get("files").isEmpty());assertTrue(cp.get("files").size()<=2048);
  for(JsonNode file:cp.get("files"))assertEquals(file.path("sha256").asText(),Task11B2RuntimeFixture.digest(Task11B2RuntimeFixture.publicBytes(root.resolve(file.path("path").asText()))));
 }
 static Inputs inspect()throws Exception {
  Path root=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"));Path w=root.resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");JsonNode cfg=Task11B2RuntimeFixture.publicJson(w.resolve(ACK),ACK_SHA);validateConfiguration(root,w,cfg);
  assertEquals(FIXTURE,System.getenv("AMA_TASK11_FIXTURE"));Path owned=root.resolve(".tools").resolve(OWNER);assertEquals(owned.toString(),System.getenv("AMA_TASK11_RUN_ROOT"));assertEquals(OWNER,System.getenv("AMA_TASK11_OWNER"));Task11B2RuntimeFixture.privateDirectory(owned,owned);
  var allocation=pin(w,cfg,"allocation");assertEquals(owned.toString(),allocation.path("privateRoot").asText());
  Path evidence=root.resolve("artifacts/phase-b/b-remaining/task-11").resolve(LABEL);assertEquals(evidence.toString(),System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertFalse(Files.isSymbolicLink(evidence));assertTrue(Files.isDirectory(evidence));assertEquals(evidence,evidence.toRealPath());
  assertNull(System.getenv("AMA_TASK11_S3_ENV"));assertNull(System.getenv("AMA_TASK09_S3_ENV"));assertNull(System.getenv("AMA_TASK11_TEST_OWNED_PROVIDER_ROOT"));
  return new Inputs(root,w,owned,evidence,cfg,allocation,pin(w,cfg,"retainedOrigin"));
 }
}