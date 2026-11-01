package ama.config.json;

import ama.canonical.*;
import ama.config.CanonicalJson;
import ama.minutes.contract.ContractValues;
import ama.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.canonical.CanonicalValue.*;
import static ama.config.WireValues.*;
import static ama.config.json.RawHashChainTest.GRAPH;
import static org.junit.jupiter.api.Assertions.*;

/** Transport consistency only; production graph/archives must separately bind actual templates. */
class PersonalTemplateProtocolTest extends Task09ControlledProtocolTest {
 static final String PERSONAL="ama.controlled-minutes-http.v2";
 static Wire personal(String protocol,List<String> themes)throws Exception{
  var original=wire();var body=BoundedModelJson.parse(original.body(),16*1024*1024);
  var payload=(CanonicalValue.ObjectValue)fields(body,"protocolVersion","logicalBusinessKey","logicalRequestHash","executionRequestHash","executionId","executionNo","payloadSha256","payload").get("payload");
  payload=replace(payload,"protocolVersion",string(protocol));
  var oldTemplate=(CanonicalValue.ObjectValue)fields(payload,"protocolVersion","modelIdentity","parameters","instructionText","inputManifest","transcript","template","pinnedElements","exclusions").get("template");
  payload=replace(payload,"template",replace(oldTemplate,"themes",new Sequence(themes.stream().map(t->(CanonicalValue)string(t)).toList())));
  String digest=sha(json(payload));var envelope=replace(replace(original.envelope(),"protocolVersion",string(protocol)),"payloadSha256",string(digest));
  String request=new CanonicalValueCodec().sha256(ContractValues.envelope(protocol.equals(PERSONAL)?"ama.minutes-model-request.v2":"ama.minutes-model-request.v1","MODEL_REQUEST",envelope),GRAPH.canonical(),()->false);
  var updated=replace(replace(replace(replace(replace(body,"protocolVersion",string(protocol)),"payload",payload),"payloadSha256",string(digest)),"logicalRequestHash",string(request)),"executionRequestHash",string(request));
  return new Wire(json(updated),header(envelope),envelope);
 }
 @Test void explicitV2AcceptsOneOrEightPersonalThemesWhileV1RemainsFixed()throws Exception{
  for(var themes:List.of(List.of("会议结论😀"),List.of("背景","讨论","结论","行动","风险","问题","分歧","后续"))){
   var selected=personal(PERSONAL,themes);assertNotNull(verify(selected.body(),selected.header()));
   var legacy=personal(VERSION,themes);assertThrows(RuntimeException.class,()->verify(legacy.body(),legacy.header()));
  }
 }
 @Test void v2RejectsInvalidThemeCountsAndScalarContentDespiteRecomputedRequestHashes()throws Exception{
  for(var themes:List.of(List.<String>of(),Collections.nCopies(9,"主题"),List.of(" \u00a0"),List.of("x".repeat(8193)))){
   var selected=personal(PERSONAL,themes);assertThrows(RuntimeException.class,()->verify(selected.body(),selected.header()));
  }
 }
 @Test void acceptedPersonalRequestAndPayloadSurviveProviderStoreReopen()throws Exception{
  var wire=personal(PERSONAL,List.of("决定","后续"));var root=java.nio.file.Files.createTempDirectory("ama-personal-provider-");
  try(var store=new ControlledMinutesProviderStore(root,"CONTROLLED_INSTALLATION")){var first=store.reserve(wire.body(),wire.header());assertEquals("UNKNOWN",first.state());store.commitSuccess(first.executionId(),"original exact output".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  try(var store=new ControlledMinutesProviderStore(root,"CONTROLLED_INSTALLATION")){var replay=store.reserve(wire.body(),wire.header());assertEquals("SUCCEEDED",replay.state());assertArrayEquals("original exact output".getBytes(java.nio.charset.StandardCharsets.UTF_8),store.result(replay.resultId()));}
 }
}
