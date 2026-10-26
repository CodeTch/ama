package ama.storage;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.ObjectNode;import java.nio.file.*;import java.time.Instant;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
/** Real B3 configuration/release boundary with only synthetic public release/source; no runtime inspection. */
class Task11B3BudgetInputsTest {
 final ObjectMapper json=new ObjectMapper();Path root,w,temporary,source,releasePath;ObjectNode cfg,release;
 @BeforeEach void inputs()throws Exception {
  root=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"));w=root.resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");cfg=(ObjectNode)json.readTree(Files.readAllBytes(w.resolve(Task11B3BudgetInputs.ACK)));
  temporary=Files.createTempDirectory(root.resolve(".superpowers"),"b3-budget-inputs-test-");
  for(JsonNode pin:cfg.path("inputPins"))Files.copy(w.resolve(pin.path("path").asText()),temporary.resolve(pin.path("path").asText()));
  source=temporary.resolve("inert-source.txt");Files.writeString(source,"synthetic\n");var checkpoint=json.createObjectNode();checkpoint.putArray("files").addObject().put("path",root.relativize(source).toString()).put("sha256",Task11B2RuntimeFixture.digest(Files.readAllBytes(source)));Path cp=temporary.resolve(cfg.path("runtimeSourceCheckpoint").asText());Files.writeString(cp,json.writeValueAsString(checkpoint));
  release=json.createObjectNode();release.put("status","B3_BUDGET_DIAGNOSTIC_RUNTIME_RELEASED_ONCE").put("label",Task11B3BudgetInputs.LABEL).put("group","it").put("selector",Task11B3BudgetInputs.SELECTOR).put("fixtureName","task11-b2-exact-isolated").put("configurationAckSha256",Task11B3BudgetInputs.ACK_SHA).put("sourceCheckpointSha256",Task11B2RuntimeFixture.digest(Files.readAllBytes(cp))).put("maxInvocations",1).put("oneGrantAuthorized",false).put("nativeStartAuthorized",false).put("sendAuthorized",false).put("issuedUtc",Instant.now().minusSeconds(60).toString()).put("startBeforeUtc",Instant.now().plusSeconds(300).toString());release.set("budget",cfg.path("budget").deepCopy());releasePath=temporary.resolve(cfg.path("runtimeReleaseFile").asText());save();
 }
 void save()throws Exception {Files.writeString(releasePath,json.writeValueAsString(release));}
 void validate()throws Exception {Task11B3BudgetInputs.validateConfiguration(root,temporary,cfg);}
 @AfterEach void cleanup()throws Exception {if(temporary!=null){assertTrue(temporary.startsWith(root.resolve(".superpowers"))&&temporary.getFileName().toString().startsWith("b3-budget-inputs-test-"));try(var paths=Files.walk(temporary)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}}
 @Test void exactValidConfigurationReachesTheActualB3Boundary()throws Exception {assertDoesNotThrow(this::validate);}
 @Test void missingExpiredAndMismatchedReleaseRejectBeforeRuntime()throws Exception {
  Files.delete(releasePath);assertThrows(AssertionError.class,this::validate);save();
  release.put("startBeforeUtc",Instant.now().minusSeconds(1).toString());save();assertThrows(AssertionError.class,this::validate);
  release.put("startBeforeUtc",Instant.now().plusSeconds(300).toString()).put("selector","WrongIT");save();assertThrows(AssertionError.class,this::validate);
 }
 @Test void expandedEffectsOrWrongKnownSuccessorAreRejected()throws Exception {
  ((ObjectNode)cfg.path("budget")).put("grants",1);assertThrows(AssertionError.class,this::validate);((ObjectNode)cfg.path("budget")).put("grants",0);
  cfg.put("taskId","00000000-0000-4000-8000-000000000001");assertThrows(AssertionError.class,this::validate);
 }
 @Test void originalUnapprovedCheckpointCannotBeSelectedByPromotedConfiguration()throws Exception {for(String name:List.of("task-11-b3-budget-diagnostic-r1-source-checkpoint-20261026.json","task-11-b3-budget-diagnostic-source-checkpoint-20261026.json","task-11-b3-lifecycle-source-checkpoint-20261026.json","task-11-b3-prepared-local-source-checkpoint-20261026.json","task-11-b3-prepared-local-r1-source-checkpoint-20261026.json")){cfg.put("runtimeSourceCheckpoint",name);assertThrows(AssertionError.class,this::validate);}}
 @Test void actualEntryRejectsMissingPublicReleaseBeforePrivateRuntimeInspection()throws Exception {Files.copy(w.resolve(Task11B3BudgetInputs.ACK),temporary.resolve(Task11B3BudgetInputs.ACK));Files.delete(releasePath);var failure=assertThrows(AssertionError.class,()->Task11B3BudgetInputs.inspect(root,temporary));assertEquals("B3 budget diagnostic release required before runtime inspection ==> expected: <true> but was: <false>",failure.getMessage());}
 @Test void changedReviewAndChangedReleasedSourceReject()throws Exception {
  Path pin=temporary.resolve(cfg.at("/inputPins/sourceReview/path").asText());byte[] before=Files.readAllBytes(pin);Files.writeString(pin,"tampered\n");assertThrows(AssertionError.class,this::validate);Files.write(pin,before);
  Files.writeString(source,"changed\n");assertThrows(AssertionError.class,this::validate);
 }
 @Test void namedControlCannotExpandItsFiniteDimensionOrSwapOriginalProfile()throws Exception {var control=(ObjectNode)cfg.path("profiles").get(1);control.put("archiveTotalBytes",4194304);assertThrows(AssertionError.class,this::validate);control.put("archiveTotalBytes",2097152);((ObjectNode)cfg.path("profiles").get(0)).put("name","CONTROL_2M");assertThrows(AssertionError.class,this::validate);}
}