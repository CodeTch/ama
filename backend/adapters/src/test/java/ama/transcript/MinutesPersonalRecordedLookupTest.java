package ama.transcript;

import ama.canonical.*;
import ama.config.CanonicalJson;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.contract.TaskContracts.*;
import ama.minutes.contract.ContractValues;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.canonical.CanonicalValue.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure recorded-wire validation, never an execution capability or a fabricated business result. */
class MinutesPersonalRecordedLookupTest {
 static final CanonicalBudget BUDGET=new CanonicalBudget(1048576,32,10000,65536,1000);
 static final String INPUT="00000000-0000-4000-8000-000000000001",MEETING="00000000-0000-4000-8000-000000000002",TASK="00000000-0000-4000-8000-000000000003",FP="a".repeat(64),PAYLOAD="b".repeat(64);
 record Data(String manifest,TaskSpec spec,Map<String,Object> input,Map<String,Object> row){}
 Data data(String protocol,String hashProfile){
  var roots=new ArrayList<CanonicalValue>();for(String kind:List.of("MODEL_CONFIG","PROCESSING_CONFIG","PROMPT_CONFIG"))roots.add(object(field("artifactType",string(kind)),field("version",string("recorded."+kind)),field("sha256",string("c".repeat(64)))));
  var envelope=object(field("logicalBusinessKey",string("minutes:"+INPUT)),field("inputId",string(INPUT)),field("inputFingerprint",string(FP)),field("protocolVersion",string(protocol)),field("modelIdentity",string("CONTROLLED_MINUTES_V1")),field("payloadSha256",string(PAYLOAD)),field("roots",new Sequence(roots)));
  String hash=new CanonicalValueCodec().sha256(ContractValues.envelope(hashProfile,"MODEL_REQUEST",envelope),BUDGET,()->false);
  var manifest=object(field("protocolVersion",string(protocol)),field("providerId",string("CONTROLLED_HTTP_V1")),field("modelIdentity",string("CONTROLLED_MINUTES_V1")),field("taskId",string(TASK)),field("inputId",string(INPUT)),field("inputFingerprint",string(FP)),field("requesterId",string("recorded-owner")),field("logicalBusinessKey",string("minutes:"+INPUT)),field("payloadSha256",string(PAYLOAD)),field("archiveIndexSha256",string("d".repeat(64))),field("roots",new Sequence(roots)),field("base",object(field("businessVersion",number(7)),field("cancelEpoch",number(2)))));
  var spec=new TaskSpec("MINUTES_GENERATE_V1","MINUTES_GENERATE",new SubjectRef("MEETING",MEETING),"e".repeat(64),new TypedRef("GENERATION_INPUT",INPUT),FP,7,0,2,3);
  var input=new HashMap<String,Object>();input.put("meeting_id",MEETING);input.put("input_fingerprint",FP);input.put("base_revision",7L);input.put("scope","FULL");input.put("target_element_id",null);input.put("prompt_version","recorded.PROMPT_CONFIG");input.put("model_config_version","recorded.MODEL_CONFIG");input.put("material_snapshot_id",null);
  return new Data(new String(CanonicalJson.encode(manifest,65536,()->false),StandardCharsets.UTF_8),spec,input,Map.of("request_hash",hash,"logical_hash",hash,"business_key","minutes:"+INPUT,"operation_kind","MINUTES_GENERATE"));
 }
 MinutesRecordedLookup.Reconstruction verify(Data data){return MinutesRecordedLookup.reconstruct(data.manifest(),data.spec(),TASK,data.input(),data.row(),BUDGET);}
 @Test void reconstructsExactRecordedVersionWithItsOwnRequestHashDomain(){
  for(int version:List.of(1,2,3,4)){var data=data("ama.controlled-minutes-http.v"+version,"ama.minutes-model-request.v"+version);var result=assertDoesNotThrow(()->verify(data));assertEquals(data.row().get("request_hash"),result.requestHash());assertEquals("recorded-owner",result.requester());assertEquals(PAYLOAD,result.payloadHash());}
 }
 @Test void aV2ManifestCannotReuseV1HashOrUnknownProtocolEvenIfOtherFieldsMatch(){
  assertThrows(RuntimeException.class,()->verify(data("ama.controlled-minutes-http.v2","ama.minutes-model-request.v1")));
  assertThrows(RuntimeException.class,()->verify(data("ama.controlled-minutes-http.v3","ama.minutes-model-request.v2")));
  assertThrows(RuntimeException.class,()->verify(data("ama.controlled-minutes-http.v4","ama.minutes-model-request.v3")));
  assertThrows(RuntimeException.class,()->verify(data("ama.controlled-minutes-http.v99","ama.minutes-model-request.v99")));
 }
}
