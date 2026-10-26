package ama.storage;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only allocated-input guards. No lifecycle hook, resource creation, client or process startup. */
final class Task11B2RuntimeFixture {
    static final String READBACK_ACK="task-11-b2-exact-retry-readback-promoted-configuration-ack-20261026.json", READBACK_ACK_SHA="5c2c7cdb5674795bd113f000659d91049f2aed09a0f8e1ab946110d58031e8c5", READBACK_LABEL="b2-exact-retry-readback-01";
    static final String EXACT_RETRY_ACK="task-11-b2-exact-retry-configuration-ack-r1-20261026.json", EXACT_RETRY_ACK_SHA="f2db0e2e19d3a21fe06a87073cc9c0336e809cc00399b1b22cbe72faeadbc591";
    static final String EXACT_RETRY_LABEL="b2-exact-retry-01";
    static final String OWNER="task11b2-d9e2fdcc611e4e21bb360e539ca46258";
    static final String ACK="task-11-b2-runtime-fixture-application-ack-20261026.json";
    static final String ACK_SHA="b0e4c66e3eaf570e40182a000f43af924ad4699ad7f906f4232043c80b16c204";
    static final String PIPELINE_ACK="task-11-b2-pipeline-fixture-configuration-ack-20261026.json";
    static final String PIPELINE_ACK_SHA="acdd3252b97f82637faf54d5fcf75299e83ec9a541a2aca3f09d11e9a4a2d69a";
    static final String LOCAL_ACK="task-11-b2-local-acceptance-configuration-ack-20261026.json";
    static final String LOCAL_ACK_SHA="5d2a74f2be5ac910c3cc84f59113b5678e4a39c71caf4fcdadb3cf9c5d38e279";
    static final String LOCAL_CONTINUATION_ACK="task-11-b2-local-continuation-configuration-ack-20261026.json";
    static final String LOCAL_CONTINUATION_ACK_SHA="7473a20f46ecc86d56403acd4e414f56a79a9cd552643e32950927896ab66e26";
    static final String REJECTION_ACK="task-11-b2-rejection-configuration-ack-20261026.json";
    static final String REJECTION_ACK_SHA="45c793ed0d72b3c2cdf79d9c6b1e4fe6595629a99cdc6da6ded5f946b66e1ffb";
    static final String TRANSPORT_ACK="task-11-b2-rejected-transport-configuration-ack-20261026.json";
    static final String TRANSPORT_ACK_SHA="40f34957837c5ddccf98abf87e50ef3ff5fb40a00be208929c2c00efb1745837";
    static final String TRANSPORT_CONTINUATION_ACK="task-11-b2-rejected-transport-continuation-configuration-ack-20261026.json";
    static final String TRANSPORT_CONTINUATION_ACK_SHA="60499f4b2a259902ee0af5bfa6a72886f4d909446e0067acb00fe6714c187343";
    static final String TRANSPORT_MANIFEST_ACK="task-11-b2-rejected-transport-manifest-configuration-ack-20261026.json";
    static final String TRANSPORT_MANIFEST_ACK_SHA="80ccbb1d8d15730745b2c0924aa68776c2d02cdab83fb0b13c6bf8e09307ab0e";
    static final String HOST_PREFLIGHT_ACK="task-11-b6-host-preflight-configuration-ack-20261026.json";
    static final String HOST_PREFLIGHT_ACK_SHA="9a647cddfce9c7639b1820d20a8c7363e800b573518e489a5898f0e412ce3cca";
    static final String HOST_MATRIX_ACK="task-11-b6-host-matrix-configuration-ack-20261026.json";
    static final String HOST_MATRIX_ACK_SHA="d4c2820fac12717ade12074633e3a4f8fe81438f6ed89399920e8ebaf4a382d5";
    static final String HOST_PREFLIGHT_LABEL="b2-rejected-transport-host-preflight-01";
    static final String HOST_MATRIX_LABEL="b2-rejected-transport-host-01";
    static final String HOST_PREFLIGHT_RESULT="artifacts/phase-b/b-remaining/task-11/"+HOST_PREFLIGHT_LABEL+"/rejected-transport-host-preflight.json";
    static final String HOST_BUSINESS_VERIFICATION="task-11-b6-host-business-preflight-root-verification-20261026.json";
    static final String HOST_RUNTIME_CHECKPOINT="task-11-b6-host-runtime-source-checkpoint-20261026.json";
    static final String HOST_RETAINED_SHA="be3bff5b61690ac21792d28b63efca909c2aeac0f6661e50ab1e682aa1c86faf";
    static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(65536).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    record Inputs(Path root,Path owned,Path evidence,JsonNode configuration,JsonNode allocation,JsonNode overlay,JsonNode credentials) {}
    private Task11B2RuntimeFixture() {}
    static void need(boolean condition,String safeMessage){assertTrue(condition,safeMessage);}
    static String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static JsonNode publicJson(Path path,String expectedSha)throws Exception {
        byte[] bytes=publicBytes(path);need(digest(bytes).equals(expectedSha),"Public input hash mismatch");return JSON.readTree(bytes);
    }
    /** Hash-bound consumed result includes the intentional 65537-character request negative; file stays <=1MiB. */
    static JsonNode publicReadbackResult(Path path,String expectedSha)throws Exception {
        byte[] bytes=publicBytes(path);need(digest(bytes).equals(expectedSha),"Public readback result hash mismatch");
        var evidence=JSON.copy();evidence.getFactory().setStreamReadConstraints(JSON.getFactory().streamReadConstraints().rebuild().maxStringLength(1048576).build());
        return evidence.readTree(bytes);
    }
    static byte[] publicBytes(Path path)throws Exception {
        Path root=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"));
        need(root.isAbsolute()&&!Files.isSymbolicLink(root)&&root.toRealPath().equals(root),"Repository custody mismatch");
        need(!path.toString().contains("/../")&&path.startsWith(root)&&!path.startsWith(root.resolve(".tools")),"Public input path outside repository");
        Path current=root;
        for(Path part:root.relativize(path)){current=current.resolve(part);need(!Files.isSymbolicLink(current)&&Files.exists(current,LinkOption.NOFOLLOW_LINKS),"Missing/symlink public input");}
        need(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&path.toRealPath().equals(path)&&Files.size(path)<=1048576,"Public input size/type/canonical mismatch");
        return Files.readAllBytes(path);
    }
    static Inputs inspectPublicInputs()throws Exception {return inspect(0);}
    static Inputs inspectPipelineInputs()throws Exception {return inspect(1);}
    static Inputs inspectLocalInputs()throws Exception {return inspect(2);}
    static Inputs inspectLocalContinuationInputs()throws Exception {return inspect(3);}
    static Inputs inspectRejectionInputs()throws Exception {return inspect(4);}
    static Inputs inspectRejectedTransportInputs()throws Exception {return inspect(5);}
    static Inputs inspectRejectedTransportContinuationInputs()throws Exception {return inspect(6);}
    static Inputs inspectRejectedTransportManifestInputs()throws Exception {return inspect(7);}
    static Inputs inspectRejectedTransportHostInputs()throws Exception {return inspect(8);}
    static Inputs inspectRejectedTransportHostPreflightInputs()throws Exception {return inspect(9);}
    static Inputs inspectExactRetryInputs()throws Exception {return inspect(10);}
    static Inputs inspectExactRetryReadbackInputs()throws Exception {return inspect(11);}
    private static Inputs inspect(int stage)throws Exception {
        boolean readback=stage==11,exactRetry=stage==10,hostMatrix=stage==8,hostPreflight=stage==9,host=hostMatrix||hostPreflight,manifestContinuation=stage==7||host||exactRetry||readback,transportContinuation=stage==6||manifestContinuation,transport=stage==5||transportContinuation,pipeline=stage==1,rejection=stage==4||transport,continuation=stage==3||rejection,local=stage==2||continuation;
        need("task11-b2-exact-isolated".equals(System.getenv("AMA_TASK11_FIXTURE")),"Exact B2 runtime fixture required");
        Path root=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"));Path w=root.resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
        JsonNode cfg=publicJson(w.resolve(readback?READBACK_ACK:exactRetry?EXACT_RETRY_ACK:hostPreflight?HOST_PREFLIGHT_ACK:hostMatrix?HOST_MATRIX_ACK:manifestContinuation?TRANSPORT_MANIFEST_ACK:transportContinuation?TRANSPORT_CONTINUATION_ACK:transport?TRANSPORT_ACK:rejection?REJECTION_ACK:continuation?LOCAL_CONTINUATION_ACK:local?LOCAL_ACK:pipeline?PIPELINE_ACK:ACK),readback?READBACK_ACK_SHA:exactRetry?EXACT_RETRY_ACK_SHA:hostPreflight?HOST_PREFLIGHT_ACK_SHA:hostMatrix?HOST_MATRIX_ACK_SHA:manifestContinuation?TRANSPORT_MANIFEST_ACK_SHA:transportContinuation?TRANSPORT_CONTINUATION_ACK_SHA:transport?TRANSPORT_ACK_SHA:rejection?REJECTION_ACK_SHA:continuation?LOCAL_CONTINUATION_ACK_SHA:local?LOCAL_ACK_SHA:pipeline?PIPELINE_ACK_SHA:ACK_SHA);
        need((readback?"EXACT_RETRY_READBACK_CONFIGURATION_ACK_NO_STAGE_EXECUTION":exactRetry?"EXACT_RETRY_CONFIGURATION_ACK_NO_STAGE_EXECUTION":hostPreflight?"HOST_PREFLIGHT_CONFIGURATION_ACK_NO_STAGE_EXECUTION":hostMatrix?"HOST_MATRIX_CONFIGURATION_ACK_NO_STAGE_EXECUTION":manifestContinuation?"MANIFEST_CONTINUATION_INERT_CONFIGURATION_SOURCE_ONLY":transportContinuation?"REJECTED_TRANSPORT_CONTINUATION_CONFIGURATION_ACK_NO_STAGE_EXECUTION":transport?"REJECTED_TRANSPORT_CONFIGURATION_ACK_NO_STAGE_EXECUTION":rejection?"REJECTION_CONFIGURATION_ACK_NO_STAGE_EXECUTION":continuation?"LOCAL_CONTINUATION_CONFIGURATION_ACK_NO_STAGE_EXECUTION":local?"LOCAL_ACCEPTANCE_CONFIGURATION_ACK_NO_STAGE_EXECUTION":pipeline?"PIPELINE_CONFIGURATION_ACK_NO_STAGE_EXECUTION":"OPERATIONAL_CONFIGURATION_ACK_NO_STAGE_EXECUTION").equals(cfg.path("status").asText())&&OWNER.equals(cfg.path("owner").asText()),"Operational configuration binding mismatch");
        if(pipeline){
            need("b2-pipeline-01".equals(cfg.at("/initialAllowedStage/label").asText())&&"it".equals(cfg.at("/initialAllowedStage/group").asText())&&"Task11B2PipelineIT".equals(cfg.at("/initialAllowedStage/selector").asText()),"Exact pipeline stage required");
            need("task-11-b2-runtime-preflight-root-verification-20261026.json".equals(cfg.get("preflightVerification").asText())&&"b76b5469131ca9df5dc6521f8d4432ba3799c0d49aa72b942ce630ab3cd710f9".equals(cfg.get("preflightVerificationSha256").asText()),"Preflight prerequisite binding mismatch");
            publicJson(w.resolve(cfg.get("preflightVerification").asText()),cfg.get("preflightVerificationSha256").asText());
        }
        if(local){
            need((readback?READBACK_LABEL:exactRetry?EXACT_RETRY_LABEL:hostPreflight?HOST_PREFLIGHT_LABEL:hostMatrix?HOST_MATRIX_LABEL:manifestContinuation?"b2-rejected-transport-manifest-01":transportContinuation?"b2-rejected-transport-continuation-01":transport?"b2-rejected-transport-01":rejection?"b2-rejection-01":continuation?"b2-local-acceptance-continuation-01":"b2-local-acceptance-01").equals(cfg.at("/initialAllowedStage/label").asText())&&"it".equals(cfg.at("/initialAllowedStage/group").asText())&&(readback?"Task11B2ExactRetryReadbackIT":exactRetry?"Task11B2ExactRetryIT":hostPreflight?"Task11B2RejectedTransportHostPreflightIT":hostMatrix?"Task11B2RejectedTransportHostContinuationIT":manifestContinuation?"Task11B2RejectedTransportManifestContinuationIT":transportContinuation?"Task11B2RejectedTransportContinuationIT":transport?"Task11B2RejectedTransportIT":rejection?"Task11B2RejectedContinuationIT":continuation?"Task11B2LocalAcceptanceContinuationIT":"Task11B2LocalAcceptanceIT").equals(cfg.at("/initialAllowedStage/selector").asText()),"Exact local stage required");
            need("task-11-b2-pipeline-root-verification-20261026.json".equals(cfg.get("pipelineVerification").asText())&&"7dcbde521d7094f4b01f5846e1dff3c08e254f48d43e6e0f0675b1bcccaf9458".equals(cfg.get("pipelineVerificationSha256").asText()),"Pipeline prerequisite mismatch");
            publicJson(w.resolve(cfg.get("pipelineVerification").asText()),cfg.get("pipelineVerificationSha256").asText());
            need("task-11-b2-pipeline-attained-facts-20261026.json".equals(cfg.get("pipelineAttainedFacts").asText())&&"46dce27194f97d410ccf87768d9a382a47a2f38f0ad427626db15a231c6229a0".equals(cfg.get("pipelineAttainedFactsSha256").asText()),"Pipeline facts prerequisite mismatch");
            publicJson(w.resolve(cfg.get("pipelineAttainedFacts").asText()),cfg.get("pipelineAttainedFactsSha256").asText());
        }
        if(continuation){
            need(!cfg.path("initializationAuthorized").asBoolean(true)&&!cfg.path("grantAuthorized").asBoolean(true),"Continuation cannot initialize or grant");
            need("task-11-b2-local-acceptance-root-verification-20261026.json".equals(cfg.get("partialVerification").asText())&&"9a24397f74bc52d1aeb8d7581a58ba3e484af6a07c18b622f4c246da14bdf388".equals(cfg.get("partialVerificationSha256").asText()),"Partial verification mismatch");
            publicJson(w.resolve(cfg.get("partialVerification").asText()),cfg.get("partialVerificationSha256").asText());
            need("task-11-b2-local-acceptance-attained-facts-20261026.json".equals(cfg.get("partialAttainedFacts").asText())&&"7d2c74011cd49aee871ec94de9c48920a8d689deb299956706d4efb0568c996d".equals(cfg.get("partialAttainedFactsSha256").asText()),"Partial attained facts mismatch");
            publicJson(w.resolve(cfg.get("partialAttainedFacts").asText()),cfg.get("partialAttainedFactsSha256").asText());
        }
        if(rejection){
            need("task-11-b2-local-continuation-root-verification-20261026.json".equals(cfg.get("localClosureVerification").asText())&&"81e73a4711d16cb51c60a923ac037d408c9dfecd83d612033da5ead312fcd435".equals(cfg.get("localClosureVerificationSha256").asText()),"Local closure verification mismatch");
            publicJson(w.resolve(cfg.get("localClosureVerification").asText()),cfg.get("localClosureVerificationSha256").asText());
            need("task-11-b2-local-continuation-attained-facts-20261026.json".equals(cfg.get("localClosureAttainedFacts").asText())&&"02c4749472290ea1a4a8f9a560a2e15fba1602c3ca87b94530c20f45f4d8708c".equals(cfg.get("localClosureAttainedFactsSha256").asText()),"Local closure origin mismatch");
            publicJson(w.resolve(cfg.get("localClosureAttainedFacts").asText()),cfg.get("localClosureAttainedFactsSha256").asText());
        }
        if(transport){
            need(!cfg.path("revokeAuthorized").asBoolean(true),"Transport cannot revoke");
            publicJson(w.resolve(cfg.get("rejectionVerification").asText()),"e99d41f0a56b07338f7805ca0e7f03ef10dca674b8d6416a771bacff7e1c3106");
            publicJson(w.resolve(cfg.get("rejectionAttainedFacts").asText()),"fd2ac24399e9443af4dd9fe7078c6c2b4ee0a8f7201428249bbf16551b0ef556");
        }
        if(transportContinuation){
            need("task-11-b2-rejected-transport-root-verification-20261026.json".equals(cfg.get("transportPartialVerification").asText()),"Exact transport partial verification required");
            publicJson(w.resolve(cfg.get("transportPartialVerification").asText()),"ad5b64f49e877ca3f80e4878fe31a799818a6fd2297a20cb646fd704719541bf");
            need("task-11-b2-rejected-transport-attained-facts-20261026.json".equals(cfg.get("transportPartialFacts").asText()),"Exact transport partial facts required");
            publicJson(w.resolve(cfg.get("transportPartialFacts").asText()),"43e580194ac602fbabcb2f5b457ecd3b9c96b6fa0078a17806000834035f9cd5");
        }
        if(manifestContinuation){
            need("task-11-b2-rejected-transport-continuation-root-verification-20261026.json".equals(cfg.get("transportContinuationVerification").asText()),"Latest transport verification required");
            publicJson(w.resolve(cfg.get("transportContinuationVerification").asText()),"5f67bff3da16023cc411d762165083a6162929b2ca15fdfe83072290a5252f18");
            need("task-11-b2-rejected-transport-continuation-attained-facts-20261026.json".equals(cfg.get("transportContinuationFacts").asText()),"Latest transport facts required");
            publicJson(w.resolve(cfg.get("transportContinuationFacts").asText()),"a70911410827f27aa748fec8ab63ae7bf501ea1298edc6666d86793dc828271f");
        }
        if(readback)validateReadbackConfiguration(root,w,cfg);
        if(exactRetry)validateExactRetryConfiguration(root,w,cfg);
        if(host)validateHostConfiguration(cfg,w,hostPreflight);
        if(hostMatrix)hostBusinessPreflight(root,w,cfg);
        JsonNode pins=cfg.get("publicInputPins");
        for(var fields=pins.fields();fields.hasNext();){var entry=fields.next();need(Path.of(entry.getKey()).getNameCount()==1,"Public input basename required");byte[] bytes=publicBytes(w.resolve(entry.getKey()));need(digest(bytes).equals(entry.getValue().asText()),"Pinned public input changed");}
        publicJson(w.resolve(cfg.get("sourceBaselineManifest").asText()),cfg.get("sourceBaselineSha256").asText());
        need(digest(publicBytes(w.resolve(cfg.get("sourceReview").asText()))).equals(cfg.get("sourceReviewSha256").asText()),"Adopted source review pin mismatch");
        JsonNode allocation=JSON.readTree(publicBytes(w.resolve("task-11-b2-runtime-allocation-intent-20261026.json")));
        JsonNode overlay=JSON.readTree(publicBytes(w.resolve("task-11-b2-runtime-overlay-prepared-ack-20261026.json")));
        JsonNode credentials=JSON.readTree(publicBytes(w.resolve("task-11-b2-credentials-pki-created-ack-20261026.json")));
        Path owned=root.resolve(".tools").resolve(OWNER);
        need(owned.toString().equals(allocation.path("privateRoot").asText())&&OWNER.equals(System.getenv("AMA_TASK11_OWNER"))&&owned.toString().equals(System.getenv("AMA_TASK11_RUN_ROOT")),"Allocated private root mismatch");
        var expected=new LinkedHashMap<String,String>();expected.put("AMA_TASK11_PKI_ROOT",allocation.at("/native/pkiRoot").asText());expected.put("AMA_TASK11_PROVIDER_ROOT",allocation.at("/knowledge/root").asText());expected.put("AMA_TASK11_MODEL_ROOT",allocation.at("/model/root").asText());expected.put("AMA_TASK11_NATIVE_ROOT",allocation.at("/native/root").asText());expected.put("AMA_TASK11_ARCHIVE_ROOT",allocation.at("/archive/root").asText());expected.put("AMA_TASK11_MODEL_AUTHORIZATION_FILE",overlay.get("modelAuthorizationFile").asText());expected.put("AMA_TASK11_ALLOCATION",w.resolve("task-11-b2-runtime-allocation-intent-20261026.json").toString());
        for(var entry:expected.entrySet())need(entry.getValue().equals(System.getenv(entry.getKey())),"Allocated environment path mismatch");
        need(System.getenv("AMA_TASK11_S3_ENV")==null&&System.getenv("AMA_TASK09_S3_ENV")==null&&System.getenv("AMA_TASK11_TEST_OWNED_PROVIDER_ROOT")==null,"Old/unallocated runtime aliases forbidden");
        Path evidence=root.resolve("artifacts/phase-b/b-remaining/task-11/"+(readback?READBACK_LABEL:exactRetry?EXACT_RETRY_LABEL:hostPreflight?HOST_PREFLIGHT_LABEL:hostMatrix?HOST_MATRIX_LABEL:manifestContinuation?"b2-rejected-transport-manifest-01":transportContinuation?"b2-rejected-transport-continuation-01":transport?"b2-rejected-transport-01":rejection?"b2-rejection-01":continuation?"b2-local-acceptance-continuation-01":local?"b2-local-acceptance-01":pipeline?"b2-pipeline-01":"b2-runtime-preflight-01"));
        need(evidence.toString().equals(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"))&&!Files.isSymbolicLink(evidence)&&Files.isDirectory(evidence)&&evidence.toRealPath().equals(evidence),"Exact public evidence window required");
        need(!Files.isSymbolicLink(root.resolve(".tools"))&&root.resolve(".tools").toRealPath().equals(root.resolve(".tools"))&&owned.toRealPath().equals(owned),"Canonical private owner path required");
        privateDirectory(owned,owned);return new Inputs(root,owned,evidence,cfg,allocation,overlay,credentials);
    }
    static void validateReadbackConfiguration(Path root,Path w,JsonNode cfg)throws Exception {
        for(String field:List.of("runtimeAuthorized","grantAuthorized","sendAuthorized","initializationAuthorized","revokeAuthorized"))need(cfg.path(field).isBoolean()&&!cfg.get(field).asBoolean(),"Readback ACK remains inert");
        for(String key:List.of("transportManifestVerification","transportManifestFacts","hostRetainedPreflight","hostSourceCheckpoint","hostSourceReview","hostRuntimeFacts","hostRuntimeReview","hostRuntimeAdoption","currentSourceCheckpoint","exactRetryPreparation","failedWindowCustody","failedWindowFacts","partialReview","numericDiagnosis","correctedSourceReview","correctedSourceAdoption"))need(digest(publicBytes(w.resolve(cfg.path(key).asText()))).equals(cfg.path(key+"Sha256").asText()),"Readback prerequisite hash mismatch");
        var failed=publicReadbackResult(root.resolve(cfg.path("failedWindowResult").asText()),cfg.path("failedWindowResultSha256").asText());need("PARTIAL_UNQUALIFIED".equals(failed.path("status").asText())&&"b2-exact-retry-01".equals(failed.path("label").asText())&&failed.path("servletRequestsAttempted").asInt()==36&&failed.path("sqliteObservationsAttempted").asInt()==2&&failed.path("grantAttempts").asInt()==1,"Consumed partial window required");
        need("3588aaba-ecfa-4889-8954-46c1454447d4".equals(cfg.path("existingTaskId").asText())&&"65fa6b21-22da-4a2e-a886-341b16978106".equals(cfg.path("existingExecutionId").asText())&&Task11B2ExactRetryOracle.KEY.equals(cfg.path("existingCommandKey").asText()),"Exact existing successor/key required");
        need("ad4cdad9a92e60239d3c8e68381ca6ef3a530ea49bc931c95e7234291c894b25".equals(cfg.path("currentSourceCheckpointSha256").asText()),"Frozen R1 origin required");
        need("task-11-b2-exact-retry-readback-runtime-release-20261026.json".equals(cfg.path("runtimeReleaseFile").asText())&&"task-11-b2-exact-retry-readback-source-promoted-checkpoint-20261026.json".equals(cfg.path("runtimeSourceCheckpoint").asText()),"Readback release paths required");
        var release=JSON.readTree(publicBytes(w.resolve(cfg.path("runtimeReleaseFile").asText())));
        need("EXACT_RETRY_READBACK_RUNTIME_RELEASED_ONCE".equals(release.path("status").asText())&&READBACK_LABEL.equals(release.path("label").asText())&&"it".equals(release.path("group").asText())&&"Task11B2ExactRetryReadbackIT".equals(release.path("selector").asText())&&"task11-b2-exact-isolated".equals(release.path("fixtureName").asText())&&READBACK_ACK_SHA.equals(release.path("configurationAckSha256").asText()),"Exact one-shot readback release required");
        need(release.path("oneGrantAuthorized").isBoolean()&&!release.get("oneGrantAuthorized").asBoolean()&&release.path("receiptOnlyCallsAuthorized").isIntegralNumber()&&release.get("receiptOnlyCallsAuthorized").bigIntegerValue().equals(java.math.BigInteger.valueOf(7))&&release.path("maxInvocations").isIntegralNumber()&&release.get("maxInvocations").bigIntegerValue().equals(java.math.BigInteger.ONE),"Only seven readback calls and no grant may be released");
        Instant issued=Instant.parse(release.path("issuedUtc").asText()),deadline=Instant.parse(release.path("startBeforeUtc").asText()),now=Instant.now();need(!now.isBefore(issued)&&issued.isBefore(deadline)&&now.isBefore(deadline),"Readback release interval closed");
        var checkpoint=publicJson(w.resolve(cfg.path("runtimeSourceCheckpoint").asText()),releaseHash(release,"sourceCheckpointSha256"));for(var file:checkpoint.get("files"))need(digest(publicBytes(root.resolve(file.path("path").asText()))).equals(file.path("sha256").asText()),"Released readback source changed");
        need(cfg.path("retainedRelayBaseline").equals(JSON.valueToTree(Map.of("get",39,"postAuthorization",17,"postPublication",1,"failures",0,"blockedPublicationAttempts",0,"withheld",false))),"Readback relay baseline mismatch");
        String[] keys={"servletRequests","receiptGetIncrements","destinationPostIncrements","publicationPostIncrements","relayFinalGet","relayFinalDestinationPost","relayFinalPublicationPost","grantRestorations","nativeReopens","sqliteReadonlyObservations","durableSuccessors","sessionsMax","sessionInsertsMax","sessionDeletesMax","concurrentHttpMax","receiptOnlyContexts","businessWrites"};int[] values={7,0,0,0,39,17,1,0,0,1,0,4,4,4,1,1,0};var budget=cfg.path("futureBudget");need(budget.size()==keys.length,"Exact readback budget keys");for(int i=0;i<keys.length;i++)need(budget.path(keys[i]).isIntegralNumber()&&budget.get(keys[i]).bigIntegerValue().equals(java.math.BigInteger.valueOf(values[i])),"Readback budget mismatch");
    }
    static void validateExactRetryConfiguration(Path root,Path w,JsonNode cfg)throws Exception {
        for(String field:List.of("runtimeAuthorized","grantAuthorized","sendAuthorized","initializationAuthorized","revokeAuthorized"))need(cfg.path(field).isBoolean()&&!cfg.get(field).asBoolean(),"Exact retry ACK must remain inert");
        for(String key:List.of("transportManifestVerification","transportManifestFacts","hostRetainedPreflight","hostSourceCheckpoint","hostSourceReview","hostRuntimeFacts","hostRuntimeReview","hostRuntimeAdoption","currentSourceCheckpoint","exactRetryPreparation"))need(digest(publicBytes(w.resolve(cfg.path(key).asText()))).equals(cfg.path(key+"Sha256").asText()),"Exact retry public pin changed");
        need("84732b029d9fa3caec2d865bd6adbfa1ace00f487f480827a051e3ccad429587".equals(cfg.path("hostRuntimeFactsSha256").asText())&&"ba5c5b4d34c6380c258697aa01882285bf972e1a2f78f2f72d4b2afd83f7f94f".equals(cfg.path("hostRuntimeAdoptionSha256").asText()),"Adopted Host origin required");
        need("task-11-b2-exact-retry-runtime-release-20261026.json".equals(cfg.path("runtimeReleaseFile").asText())&&"task-11-b2-exact-retry-source-r1-checkpoint-20261026.json".equals(cfg.path("runtimeSourceCheckpoint").asText()),"Exact retry release paths required");
        var release=JSON.readTree(publicBytes(w.resolve(cfg.get("runtimeReleaseFile").asText())));
        need("EXACT_RETRY_RUNTIME_RELEASED_ONCE".equals(release.path("status").asText())&&EXACT_RETRY_LABEL.equals(release.path("label").asText())&&"it".equals(release.path("group").asText())&&"Task11B2ExactRetryIT".equals(release.path("selector").asText())&&"task11-b2-exact-isolated".equals(release.path("fixtureName").asText())&&EXACT_RETRY_ACK_SHA.equals(release.path("configurationAckSha256").asText()),"One-shot exact retry root release required");
        need(release.path("maxInvocations").isIntegralNumber()&&release.get("maxInvocations").asInt()==1&&release.path("oneGrantAuthorized").isBoolean()&&release.get("oneGrantAuthorized").asBoolean(),"Exact one-grant one-invocation release required");
        need(cfg.get("hostRuntimeAdoptionSha256").asText().equals(releaseHash(release,"hostRuntimeAdoptionSha256")),"Release adoption mismatch");
        Instant issued=Instant.parse(release.path("issuedUtc").asText()),deadline=Instant.parse(release.path("startBeforeUtc").asText()),now=Instant.now();need(!now.isBefore(issued)&&issued.isBefore(deadline)&&now.isBefore(deadline),"Exact retry release interval closed");
        var checkpoint=publicJson(w.resolve(cfg.get("runtimeSourceCheckpoint").asText()),releaseHash(release,"sourceCheckpointSha256"));
        for(var file:checkpoint.get("files"))need(digest(publicBytes(root.resolve(file.path("path").asText()))).equals(file.path("sha256").asText()),"Released exact retry source changed");
        need(cfg.path("retainedRelayBaseline").equals(JSON.valueToTree(Map.of("get",25,"postAuthorization",3,"postPublication",1,"failures",0,"blockedPublicationAttempts",0,"withheld",false))),"Exact retry relay baseline mismatch");
        var budget=cfg.path("futureBudget");String[] keys={"servletRequests","receiptGetIncrements","destinationPostIncrements","publicationPostIncrements","relayFinalGet","relayFinalDestinationPost","relayFinalPublicationPost","grantRestorations","nativeReopens","sqliteReadonlyObservations","durableSuccessors","sessionsMax","concurrentHttpMax"};int[] values={43,14,14,0,39,17,1,1,1,3,1,4,2};for(int i=0;i<keys.length;i++)need(budget.path(keys[i]).isIntegralNumber()&&budget.get(keys[i]).asInt()==values[i],"Exact retry budget mismatch");
        var grant=cfg.path("grantRestorationIntent");need(grant.path("before").equals(JSON.valueToTree(List.of(1,0,2)))&&grant.path("after").equals(JSON.valueToTree(List.of(1,1,3)))&&grant.path("count").asInt()==1&&!grant.path("currentlyAuthorized").asBoolean(true)&&grant.path("requiresSeparateRuntimeRelease").asBoolean()&&!grant.path("initialization").asBoolean(true)&&!grant.path("revoke").asBoolean(true),"Exact restoration intent required");
    }
    static void validateHostConfiguration(JsonNode cfg,Path w,boolean preflight)throws Exception {
        need(cfg.path("runtimeAuthorized").isBoolean()&&!cfg.get("runtimeAuthorized").asBoolean()&&cfg.path("sendAuthorized").isBoolean()&&!cfg.get("sendAuthorized").asBoolean(),"Host configuration is inert");
        var pins=Map.of(
            "transportManifestVerification",List.of("task-11-b2-rejected-transport-manifest-root-verification-20261026.json","3e61016a1f94840289bb325cd0a67d4747c74dffd65f555b5587e56ca1d226ad"),
            "transportManifestFacts",List.of("task-11-b2-rejected-transport-manifest-attained-facts-20261026.json","9588a52656f0a6a4df6a28a8c38fbcf2c67ba149e2715e3af267bf3f0afb416c"),
            "hostRetainedPreflight",List.of("task-11-b6-host-retained-preflight-20261026.json",HOST_RETAINED_SHA),
            "hostSourceCheckpoint",List.of("task-11-b6-host-source-checkpoint-20261026.json","cd6cc0bc2ec3dc982caeb827430a19933ddc1a6b3e802670f78817d21b28f506"),
            "hostSourceReview",List.of("task-11-b6-host-source-independent-review-20261026.md","162a8f5ae0adbdd7094a206b5a78122d4f00c5c1e156826609dec53216b5de01"));
        for(var entry:pins.entrySet()){
            need(entry.getValue().get(0).equals(cfg.path(entry.getKey()).asText())&&entry.getValue().get(1).equals(cfg.path(entry.getKey()+"Sha256").asText()),"Host public pin binding mismatch");
            need(digest(publicBytes(w.resolve(entry.getValue().get(0)))).equals(entry.getValue().get(1)),"Host public evidence changed");
        }
        JsonNode baseline=cfg.path("retainedRelayBaseline");
        need(baseline.equals(JSON.valueToTree(Map.of("get",1,"postAuthorization",3,"postPublication",1,"failures",0,"blockedPublicationAttempts",0,"withheld",false))),"Exact observed cumulative relay baseline required");
        need(cfg.path("incrementalGetBudget").isIntegralNumber()&&cfg.get("incrementalGetBudget").asLong()==(preflight?0:24)&&cfg.path("expectedCumulativeGetAfter").isIntegralNumber()&&cfg.get("expectedCumulativeGetAfter").asLong()==(preflight?1:25),"Host incremental budget mismatch");
        var retained=publicJson(w.resolve(cfg.get("hostRetainedPreflight").asText()),HOST_RETAINED_SHA);
        need("ENDPOINT_PROVIDER_COUNTERS_VERIFIED_BUSINESS_PREFLIGHT_PENDING".equals(retained.path("status").asText()),"Retained observation status mismatch");
        for(var fields=baseline.fields();fields.hasNext();){var entry=fields.next();need(entry.getValue().equals(retained.path("relayCounters").get(entry.getKey())),"Observed relay counter mismatch");}
    }
    static String releaseHash(JsonNode release,String field){String value=release.path(field).asText();need(value.matches("[0-9a-f]{64}"),"Exact release hash required");return value;}
    static JsonNode hostBusinessPreflight(Path root,Path w,JsonNode cfg)throws Exception {
        var required=cfg.path("requiredBusinessPreflight");
        need(HOST_PREFLIGHT_LABEL.equals(required.path("label").asText())&&HOST_PREFLIGHT_RESULT.equals(required.path("result").asText())&&HOST_BUSINESS_VERIFICATION.equals(required.path("rootVerification").asText()),"Exact business preflight paths required");
        var release=JSON.readTree(publicBytes(w.resolve("task-11-b6-host-matrix-runtime-release-20261026.json")));
        need("HOST_MATRIX_RUNTIME_RELEASED_ONCE".equals(release.path("status").asText())&&HOST_MATRIX_LABEL.equals(release.path("label").asText())&&"it".equals(release.path("group").asText())&&"Task11B2RejectedTransportHostContinuationIT".equals(release.path("selector").asText())&&"task11-b2-exact-isolated".equals(release.path("fixtureName").asText())&&HOST_MATRIX_ACK_SHA.equals(release.path("configurationAckSha256").asText()),"Exact one-shot Host release required");
        need(release.path("maxInvocations").isIntegralNumber()&&release.get("maxInvocations").asLong()==1,"Exactly one invocation required");
        Instant issued=Instant.parse(release.path("issuedUtc").asText()),deadline=Instant.parse(release.path("startBeforeUtc").asText()),now=Instant.now();
        need(!now.isBefore(issued)&&issued.isBefore(deadline)&&now.isBefore(deadline),"Host release start interval closed");
        publicJson(w.resolve(HOST_RUNTIME_CHECKPOINT),releaseHash(release,"sourceCheckpointSha256"));
        need(digest(publicBytes(w.resolve(HOST_BUSINESS_VERIFICATION))).equals(releaseHash(release,"businessPreflightRootVerificationSha256")),"Business root verification changed");
        var result=publicJson(root.resolve(HOST_PREFLIGHT_RESULT),releaseHash(release,"businessPreflightResultSha256"));
        need("PASS".equals(result.path("status").asText())&&HOST_PREFLIGHT_LABEL.equals(result.path("label").asText())&&HOST_PREFLIGHT_ACK_SHA.equals(result.path("configurationAckSha256").asText())&&HOST_RETAINED_SHA.equals(result.path("hostRetainedPreflightSha256").asText()),"Successful exact business preflight required");
        need(result.path("businessPreimage").isObject()&&result.get("businessPreimage").size()==17&&result.path("allThreeConsumedPreimagesMatch").asBoolean()&&result.path("allThreeConsumedFrontsAbsent").asBoolean(),"Complete preflight preimages required");
        return result;
    }
    static void verifyHostBusinessPreimages(Inputs inputs,Map<String,String> current)throws Exception {
        need(current.size()==17,"Exact17-table image required");Path w=inputs.root().resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
        String[] keys={"transportPartialFacts","transportContinuationFacts","transportManifestFacts"};
        String[] labels={"b2-rejected-transport-01","b2-rejected-transport-continuation-01","b2-rejected-transport-manifest-01"};
        long[] pids={2659369,2660337,2662310};String[] ticks={"21768352","21862512","21980854"};
        for(int i=0;i<keys.length;i++){
            var prior=publicJson(w.resolve(inputs.configuration().get(keys[i]).asText()),inputs.configuration().get(keys[i]+"Sha256").asText());
            need(labels[i].equals(prior.path("window").asText())&&prior.path("exitCode").asInt()==1,"Consumed window identity/result mismatch");
            need(prior.at("/frontCleanup/frontPid").asLong()==pids[i]&&ticks[i].equals(prior.at("/frontCleanup/frontStartTicks").asText())&&prior.at("/frontCleanup/frontStopped").asBoolean()&&prior.at("/frontCleanup/frontExitCode").asInt()==0&&prior.at("/front/sinkJoined").asBoolean(),"Consumed front custody mismatch");
            need(!ProcessHandle.of(pids[i]).map(ProcessHandle::isAlive).orElse(false),"Consumed front must be absent; never kill or reuse PID");
            need(prior.path("businessPreimage").equals(JSON.valueToTree(current)),"Current17-table image differs from consumed preimage");
            if(i==2)need(prior.at("/totals/failures").asInt()==1&&prior.at("/totals/errors").asInt()==0&&prior.at("/front/requests").asInt()==1&&prior.at("/front/forwards").asInt()==1&&prior.at("/front/posts").asInt()==0&&!prior.path("normalReceiptVerified").asBoolean()&&!prior.path("businessAfterComparisonReached").asBoolean()&&!prior.path("providerSecondObservationReached").asBoolean(),"Consumed manifest attainment mismatch");
        }
    }
    static void privateDirectory(Path owned,Path directory)throws Exception {
        need(directory.isAbsolute()&&directory.normalize().equals(directory)&&directory.startsWith(owned),"Private directory outside allocation");
        need(!Files.isSymbolicLink(owned)&&Files.isDirectory(owned)&&Files.getPosixFilePermissions(owned,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")),"Private owner mode/type mismatch");
        Path current=owned;
        for(Path part:owned.relativize(directory)){current=current.resolve(part);need(!Files.isSymbolicLink(current)&&Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS)&&Files.getPosixFilePermissions(current,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")),"Private ancestor mode/type mismatch");}
        need(directory.toRealPath().startsWith(owned.toRealPath()),"Private canonical directory escaped owner");
    }
    static void absent(Inputs inputs,Path path)throws Exception {
        need(path.isAbsolute()&&path.normalize().equals(path)&&path.startsWith(inputs.owned()),"Absent origin outside allocation");
        Path current=inputs.owned();
        for(Path part:inputs.owned().relativize(path)){current=current.resolve(part);need(!Files.isSymbolicLink(current),"Absent origin contains symlink");if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS))break;need(current.toRealPath().startsWith(inputs.owned().toRealPath()),"Absent origin escaped owner");}
        need(!Files.exists(path,LinkOption.NOFOLLOW_LINKS),"Allocated runtime origin already exists; stop, never reset");
    }
    static List<String> absentOrigins(Inputs inputs)throws Exception {
        Path nativeRoot=Path.of(inputs.allocation().at("/native/root").asText());Path kb=Path.of(inputs.credentials().get("providerRoot").asText());
        var paths=List.of(nativeRoot,nativeRoot.resolveSibling(nativeRoot.getFileName()+"-role"),nativeRoot.resolveSibling(nativeRoot.getFileName()+"-managed.properties"),nativeRoot.resolveSibling(nativeRoot.getFileName()+"-managed-service.log"),Path.of(inputs.allocation().at("/archive/root").asText()),Path.of(inputs.allocation().at("/model/root").asText()),kb.resolve("runtime/knowledge.sqlite"),kb.resolve("runtime/knowledge.sqlite-wal"),kb.resolve("runtime/knowledge.sqlite-shm"),kb.resolve("runtime/endpoint.json"));
        for(Path path:paths)absent(inputs,path);return paths.stream().map(Path::toString).toList();
    }
    private static final class PrivateFiles implements AutoCloseable {
        final Inputs inputs;final Map<Path,byte[]> read=new LinkedHashMap<>();
        PrivateFiles(Inputs inputs){this.inputs=inputs;}
        byte[] bytes(Path path)throws Exception {
            if(read.containsKey(path))return read.get(path);
            available(path);
            byte[] value;try(var in=Files.newInputStream(path)){value=in.readNBytes(65537);}need(value.length<=65536,"Bounded private read exceeded");read.put(path,value);return value;
        }
        void available(Path path)throws Exception {privateDirectory(inputs.owned(),path.getParent());need(path.normalize().equals(path)&&!Files.isSymbolicLink(path)&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&path.toRealPath().startsWith(inputs.owned().toRealPath())&&Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))&&Files.size(path)>0&&Files.size(path)<=65536,"Restricted allocated private file required");}
        JsonNode metadata(Path path)throws Exception {try{return JSON.readTree(bytes(path));}catch(JsonProcessingException invalid){throw new IllegalStateException("Private role metadata unavailable");}}
        void unchanged()throws Exception {for(var entry:read.entrySet()){available(entry.getKey());byte[] fresh;try(var in=Files.newInputStream(entry.getKey())){fresh=in.readNBytes(65537);}try{need(fresh.length<=65536&&MessageDigest.isEqual(entry.getValue(),fresh),"Allocated private bytes changed during read-only validation");}finally{Arrays.fill(fresh,(byte)0);}}}
        public void close(){read.values().forEach(bytes->Arrays.fill(bytes,(byte)0));}
    }
    static Map<String,Object> validatePrivateInputs(Inputs inputs)throws Exception {
        try(var files=new PrivateFiles(inputs)){
            Path pki=Path.of(inputs.credentials().get("pkiRoot").asText());privateDirectory(inputs.owned(),pki);
            try(var listed=Files.list(pki)){need(new HashSet<>(listed.map(p->p.getFileName().toString()).toList()).equals(Set.of("keystore-password","ca.pem","server.p12","writer.p12","trust.p12")),"Exact allocated PKI inventory required");}
            byte[] passwordBytes=files.bytes(pki.resolve("keystore-password"));need(new String(passwordBytes,StandardCharsets.US_ASCII).matches("[A-Za-z0-9_-]{43}"),"PKI password format mismatch");char[] password=new String(passwordBytes,StandardCharsets.US_ASCII).toCharArray();
            Map<String,Object> certs;
            try{
                var factory=CertificateFactory.getInstance("X.509");var ca=(X509Certificate)factory.generateCertificate(new ByteArrayInputStream(files.bytes(pki.resolve("ca.pem"))));ca.checkValidity();need(ca.getBasicConstraints()==0&&ca.getKeyUsage()[5]&&ca.getKeyUsage()[6],"Controlled CA constraints mismatch");
                certs=new LinkedHashMap<>();certs.put("server",leaf(files,pki,"server",password,ca,inputs.credentials().at("/certificates/server")));certs.put("writer",leaf(files,pki,"writer",password,ca,inputs.credentials().at("/certificates/writer")));
                var trust=KeyStore.getInstance("PKCS12");trust.load(new ByteArrayInputStream(files.bytes(pki.resolve("trust.p12"))),password);var aliases=Collections.list(trust.aliases());need(aliases.size()==1&&!trust.isKeyEntry(aliases.getFirst())&&MessageDigest.isEqual(trust.getCertificate(aliases.getFirst()).getEncoded(),ca.getEncoded()),"Truststore must contain only allocated CA");
            }catch(GeneralSecurityException invalid){throw new IllegalStateException("Allocated PKI validation unavailable");}finally{Arrays.fill(password,'\0');}
            for(String role:List.of("application","provider")){
                Path path=Path.of(inputs.credentials().at("/publicIdentityPaths/"+role).asText());JsonNode actual=files.metadata(path);var names=new HashSet<String>();actual.fieldNames().forEachRemaining(names::add);need(names.equals(Set.of("providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId","role","paths"))&&role.equals(actual.path("role").asText()),"Exact private role discriminator/fields required");
                for(var it=inputs.credentials().get("identities").fields();it.hasNext();){var entry=it.next();need(entry.getValue().equals(actual.get(entry.getKey())),"New controlled identity mismatch");}
                need(inputs.credentials().at("/paths/"+role).equals(actual.get("paths")),"New role path binding mismatch");
            }
            byte[] bearer=files.bytes(Path.of(inputs.credentials().at("/paths/application/serviceBearer").asText()));need(new String(bearer,StandardCharsets.US_ASCII).matches("[A-Za-z0-9_-]{43}"),"Controlled bearer encoding mismatch");byte[] verifier=files.bytes(Path.of(inputs.credentials().at("/paths/provider/serviceBearerVerifier").asText()));need(new String(verifier,StandardCharsets.US_ASCII).matches("[0-9a-f]{64}"),"Controlled verifier format mismatch");
            for(String pointer:List.of("/paths/application/delegationPrivateKey","/paths/provider/delegationPublicKey","/paths/provider/assertionPrivateKey","/paths/application/providerAssertionPublicKey"))files.available(Path.of(inputs.credentials().at(pointer).asText()));
            byte[] model=files.bytes(Path.of(inputs.overlay().get("modelAuthorizationFile").asText()));need(new String(model,StandardCharsets.US_ASCII).matches("[A-Za-z0-9_-]{43}"),"Model authorization format mismatch");files.unchanged();
            return Map.of("certificates",certs,"trustOnlyAllocatedCA",true,"credentialFilesAvailable",true,"roleAndIdentityPathsVerified",true,"keyPairQualification","Retained independently reviewed creation ACK4806b9997a5ca32f40eb70601535b1df7b538343a7bf5b165101c58a7a6389b4; not repeated","modelAuthorizationLength",model.length,"privateFilesRead",files.read.size(),"privateBytesUnchanged",true);
        }
    }
    private static Map<String,Object> leaf(PrivateFiles files,Path pki,String identity,char[] password,X509Certificate ca,JsonNode expected)throws Exception {
        var keyStore=KeyStore.getInstance("PKCS12");keyStore.load(new ByteArrayInputStream(files.bytes(pki.resolve(identity+".p12"))),password);var aliases=Collections.list(keyStore.aliases());need(aliases.size()==1&&keyStore.isKeyEntry(aliases.getFirst()),"Single allocated private-key entry required");String alias=aliases.getFirst();var leaf=(X509Certificate)keyStore.getCertificate(alias);leaf.checkValidity();var chain=keyStore.getCertificateChain(alias);need(chain.length==2&&MessageDigest.isEqual(chain[1].getEncoded(),ca.getEncoded())&&leaf.getBasicConstraints()==-1,"Leaf CA/chain mismatch");
        var sans=new TreeSet<String>();for(var san:leaf.getSubjectAlternativeNames()){int type=((Number)san.get(0)).intValue();sans.add((type==2?"DNS:":type==7?"IP:":type==6?"URI:":"UNEXPECTED:")+san.get(1));}
        Set<String> allowed=identity.equals("server")?Set.of("DNS:localhost","IP:127.0.0.1"):Set.of("DNS:localhost","IP:127.0.0.1","URI:spiffe://ama/writer");String eku=identity.equals("server")?"1.3.6.1.5.5.7.3.1":"1.3.6.1.5.5.7.3.2";
        need(sans.equals(allowed)&&leaf.getExtendedKeyUsage().equals(List.of(eku)),"Exact native SAN/EKU mismatch");need(Duration.between(leaf.getNotBefore().toInstant(),leaf.getNotAfter().toInstant()).compareTo(Duration.ofDays(7))<=0,"Native validity exceeds bounded policy");
        need(leaf.getNotBefore().toInstant().equals(Instant.parse(expected.get("notBefore").asText()))&&leaf.getNotAfter().toInstant().equals(Instant.parse(expected.get("notAfter").asText())),"Allocated public validity binding mismatch");
        return Map.of("notBefore",leaf.getNotBefore().toInstant().toString(),"notAfter",leaf.getNotAfter().toInstant().toString(),"san",sans,"eku",eku);
    }
}
