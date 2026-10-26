package ama.storage;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Java release validator with synthetic release/checkpoint in an owned public temporary subtree. */
class Task11B2ReadbackPromotionTest {
    static final String ACK="task-11-b2-exact-retry-readback-promoted-configuration-ack-20261026.json";
    static final String CHECKPOINT="task-11-b2-exact-retry-readback-source-promoted-checkpoint-20261026.json";
    final ObjectMapper json=new ObjectMapper();
    Path root,w,temporary,source,releasePath;ObjectNode ack,release;
    @BeforeEach void inputs()throws Exception {
        root=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"));w=root.resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
        ack=(ObjectNode)json.readTree(Files.readAllBytes(w.resolve(ACK)));
        temporary=Files.createTempDirectory(root.resolve(".superpowers"),"readback-promotion-test-");
        for(String key:List.of("transportManifestVerification","transportManifestFacts","hostRetainedPreflight","hostSourceCheckpoint","hostSourceReview","hostRuntimeFacts","hostRuntimeReview","hostRuntimeAdoption","currentSourceCheckpoint","exactRetryPreparation","failedWindowCustody","failedWindowFacts","partialReview","numericDiagnosis","correctedSourceReview","correctedSourceAdoption"))Files.copy(w.resolve(ack.path(key).asText()),temporary.resolve(ack.path(key).asText()));
        source=temporary.resolve("synthetic-source.txt");Files.writeString(source,"inert-source\n");
        var checkpoint=json.createObjectNode();checkpoint.putArray("files").addObject().put("path",root.relativize(source).toString()).put("sha256",Task11B2RuntimeFixture.digest(Files.readAllBytes(source)));
        Files.writeString(temporary.resolve(CHECKPOINT),json.writeValueAsString(checkpoint));
        release=json.createObjectNode();release.put("status","EXACT_RETRY_READBACK_RUNTIME_RELEASED_ONCE").put("label","b2-exact-retry-readback-01").put("group","it").put("selector","Task11B2ExactRetryReadbackIT").put("fixtureName","task11-b2-exact-isolated").put("configurationAckSha256",Task11B2RuntimeFixture.digest(Files.readAllBytes(w.resolve(ACK)))).put("sourceCheckpointSha256",Task11B2RuntimeFixture.digest(Files.readAllBytes(temporary.resolve(CHECKPOINT)))).put("maxInvocations",1).put("oneGrantAuthorized",false).put("receiptOnlyCallsAuthorized",7).put("issuedUtc",Instant.now().minusSeconds(60).toString()).put("startBeforeUtc",Instant.now().plusSeconds(300).toString());
        releasePath=temporary.resolve(ack.path("runtimeReleaseFile").asText());saveRelease();
    }
    void saveRelease()throws Exception {Files.writeString(releasePath,json.writeValueAsString(release));}
    void validate()throws Exception {Task11B2RuntimeFixture.validateReadbackConfiguration(root,temporary,ack);}
    @AfterEach void cleanup()throws Exception {
        if(temporary!=null){assertTrue(temporary.startsWith(root.resolve(".superpowers"))&&temporary.getFileName().toString().startsWith("readback-promotion-test-"));try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    @Test void promotedEntryPinsAndActualJavaValidatorAcceptTheNewImmutableConfiguration()throws Exception {
        assertDoesNotThrow(this::validate);
        assertEquals(ACK,Task11B2RuntimeFixture.READBACK_ACK);
        assertEquals(Task11B2RuntimeFixture.digest(Files.readAllBytes(w.resolve(ACK))),Task11B2RuntimeFixture.READBACK_ACK_SHA);
        assertDoesNotThrow(this::validate);
    }
    @Test void ordinaryPublicJsonStillRejectsTheHistoricalOversizeString()throws Exception {
        assertThrows(com.fasterxml.jackson.core.exc.StreamConstraintsException.class,()->Task11B2RuntimeFixture.publicJson(root.resolve(ack.path("failedWindowResult").asText()),ack.path("failedWindowResultSha256").asText()));
    }
    @Test void dedicatedResultReaderPreservesHashSizeDuplicateAndTrailingGuards()throws Exception {
        String body="X".repeat(65537);Files.writeString(source,json.writeValueAsString(Map.of("body",body)));
        String hash=Task11B2RuntimeFixture.digest(Files.readAllBytes(source));assertEquals(body,Task11B2RuntimeFixture.publicReadbackResult(source,hash).path("body").asText());
        assertThrows(AssertionError.class,()->Task11B2RuntimeFixture.publicReadbackResult(source,"0".repeat(64)));
        for(String invalid:List.of("{\"a\":1,\"a\":2}","{} {}")){
            Files.writeString(source,invalid);String pin=Task11B2RuntimeFixture.digest(Files.readAllBytes(source));assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->Task11B2RuntimeFixture.publicReadbackResult(source,pin));
        }
        Files.writeString(source,"X".repeat(1048577));String pin=Task11B2RuntimeFixture.digest(Files.readAllBytes(source));assertThrows(AssertionError.class,()->Task11B2RuntimeFixture.publicReadbackResult(source,pin));
    }
    @Test void actualJavaValidatorRejectsOldCheckpointNameAndChangedSource()throws Exception {
        ack.put("runtimeSourceCheckpoint","task-11-b2-exact-retry-readback-source-checkpoint-20261026.json");assertThrows(AssertionError.class,this::validate);
        ack.put("runtimeSourceCheckpoint",CHECKPOINT);Files.writeString(source,"changed-source\n");assertThrows(AssertionError.class,this::validate);
    }
    @Test void actualJavaValidatorRejectsMissingExpiredAndWrongRelease()throws Exception {
        Files.delete(releasePath);assertThrows(AssertionError.class,this::validate);saveRelease();
        release.put("issuedUtc",Instant.now().minusSeconds(120).toString()).put("startBeforeUtc",Instant.now().minusSeconds(60).toString());saveRelease();assertThrows(AssertionError.class,this::validate);
        release.put("issuedUtc",Instant.now().minusSeconds(60).toString()).put("startBeforeUtc",Instant.now().plusSeconds(300).toString()).put("configurationAckSha256","0".repeat(64));saveRelease();assertThrows(AssertionError.class,this::validate);
    }
    @Test void actualJavaValidatorRejectsChangedCorrectedReviewAndAdoption()throws Exception {
        for(String key:List.of("correctedSourceReview","correctedSourceAdoption")){
            Path file=temporary.resolve(ack.path(key).asText());byte[] original=Files.readAllBytes(file);Files.writeString(file,"tampered\n");assertThrows(AssertionError.class,this::validate);Files.write(file,original);
        }
    }
}