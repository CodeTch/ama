package ama.config.json;
import ama.canonical.*;import ama.model.*;import ama.minutes.contract.ContractValues;import java.util.*;import org.junit.jupiter.api.Test;
import static ama.canonical.CanonicalValue.*;import static ama.config.WireValues.*;import static ama.config.json.RawHashChainTest.GRAPH;import static org.junit.jupiter.api.Assertions.*;
/** Framing and hash consistency only; synthetic envelopes are not stored graph proofs. */
class RegionProtocolTest extends Task09ControlledProtocolTest {
 private Wire region(String protocol,String instructions,boolean pin)throws Exception{
  var old=wire();var original=(ObjectValue)BoundedModelJson.parse(old.body(),ControlledMinutesProtocol.MAX_REQUEST);var payload=(ObjectValue)fields(original,"protocolVersion","logicalBusinessKey","logicalRequestHash","executionRequestHash","executionId","executionNo","payloadSha256","payload").get("payload");
  var manifest=(ObjectValue)fields(payload,"protocolVersion","modelIdentity","parameters","instructionText","inputManifest","transcript","template","pinnedElements","exclusions").get("inputManifest");
  String set="99999999-9999-4999-8999-999999999999";manifest=replace(manifest,"scope",object(field("kind",string("REGION")),field("target",object(field("baseElementSetId",string(set)),field("category",string("ACTION")),field("instructions",string(instructions))))));
  if(pin)manifest=replace(manifest,"elements",object(field("mode",string("PINNED")),field("setId",string(set)),field("revision",number(1)),field("contentHash",string("a".repeat(64))),field("sourceGraph",object())));
  payload=replace(replace(payload,"protocolVersion",string(protocol)),"inputManifest",manifest);String fp=new CanonicalValueCodec().sha256(ContractValues.envelope("ama.generation-input.v3","GENERATION_INPUT",manifest),GRAPH.canonical(),()->false),ph=sha(json(payload));var envelope=replace(replace(replace(old.envelope(),"protocolVersion",string(protocol)),"payloadSha256",string(ph)),"inputFingerprint",string(fp));
  String hash=new CanonicalValueCodec().sha256(ContractValues.envelope(ControlledMinutesProtocol.requestHashProfile(protocol),"MODEL_REQUEST",envelope),GRAPH.canonical(),()->false);var body=replace(replace(replace(replace(replace(original,"protocolVersion",string(protocol)),"payload",payload),"payloadSha256",string(ph)),"logicalRequestHash",string(hash)),"executionRequestHash",string(hash));return new Wire(json(body),header(envelope),envelope);
 }
 @Test void v3RegionRequiresPinnedBaseAndBoundedInstructionsWhileV2RemainsFullOnly()throws Exception{
  var accepted=region(ControlledMinutesProtocol.REGION_VERSION,"保留已有行动",true);assertNotNull(verify(accepted.body(),accepted.header()));
  for(var invalid:List.of(region(ControlledMinutesProtocol.PERSONAL_VERSION,"保留已有行动",true),region(ControlledMinutesProtocol.REGION_VERSION," ",true),region(ControlledMinutesProtocol.REGION_VERSION,"x".repeat(4097),true),region(ControlledMinutesProtocol.REGION_VERSION,"保留已有行动",false)))assertThrows(RuntimeException.class,()->verify(invalid.body(),invalid.header()));
 }
 @Test void v4KeepsRegionInputHashAndUsesADistinctRequestEnvelopeProfile()throws Exception{var safe=region(ControlledMinutesProtocol.SOURCE_EXCLUSION_VERSION,"仅核对当前区域",true);assertNotNull(verify(safe.body(),safe.header()));assertEquals("ama.minutes-model-request.v4",ControlledMinutesProtocol.requestHashProfile(ControlledMinutesProtocol.SOURCE_EXCLUSION_VERSION));assertEquals("ama.minutes-model-request.v3",ControlledMinutesProtocol.requestHashProfile(ControlledMinutesProtocol.REGION_VERSION));}
}
