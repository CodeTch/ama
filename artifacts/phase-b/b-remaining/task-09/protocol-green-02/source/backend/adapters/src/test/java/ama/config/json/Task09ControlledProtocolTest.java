package ama.config.json;
import ama.config.*;
import ama.canonical.*;
import ama.contract.MinutesContracts.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.minutes.contract.ContractValues;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.canonical.CanonicalValue.*;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
class Task09ControlledProtocolTest {
 static final String VERSION="ama.controlled-minutes-http.v1",MODEL="CONTROLLED_MINUTES_V1",E="00000000-0000-0000-0000-000000000099";
 record Wire(byte[] body,String header,CanonicalValue.ObjectValue envelope){}
 static CanonicalValue.ObjectValue replace(CanonicalValue.ObjectValue o,String key,CanonicalValue value){return new CanonicalValue.ObjectValue(o.entries().stream().map(e->e.key().equals(key)?field(key,value):e).toList());}
 static byte[] json(CanonicalValue v){return CanonicalJson.encode(v,16*1024*1024,()->false);}
 static String sha(byte[] b){return HexFormat.of().formatHex(CanonicalValueCodec.digest().digest(b));}
 static String header(CanonicalValue o){return Base64.getUrlEncoder().withoutPadding().encodeToString(json(o));}
 static Wire wire()throws Exception {
  var binding=service().computeGeneration(draft(),beforeGeneration(),new Fence(10,11,2),full(),GRAPH,B,()->false);
  var payload=object(field("protocolVersion",string(VERSION)),field("modelIdentity",string(MODEL)),field("parameters",object(field("temperatureMicros",number(0)),field("maxOutputTokens",number(4096)),field("seed",number(0)))),field("instructionText",string("Controlled synthetic response.")),field("inputManifest",binding.manifest()),field("transcript",list()),field("template",object(field("revisionId",string(draft().templateRevisionId())),field("name",string("Controlled minutes")),field("themes",list(string("OVERVIEW"),string("DISCUSSION"),string("CONCLUSION"),string("ACTION"),string("RISK"),string("OPEN_QUESTION"),string("DISAGREEMENT"))))),field("pinnedElements",object(field("mode",string("EMPTY")),field("rows",list()))),field("exclusions",object(field("revisionId",string(draft().exclusionRevisionId())),field("entries",list()))));
  var roots=new ArrayList<CanonicalValue>();for(var raw:full().stream().filter(r->Set.of(ArtifactType.PROCESSING_CONFIG,ArtifactType.PROMPT_CONFIG,ArtifactType.MODEL_CONFIG).contains(r.claimedArtifactType())).sorted(Comparator.comparing((RawArchive r)->r.claimedArtifactType().name()).thenComparing(RawArchive::claimedVersion)).toList())roots.add(object(field("artifactType",string(raw.claimedArtifactType().name())),field("version",string(raw.claimedVersion())),field("sha256",string(sha(raw.bytes())))));
  String ph=sha(json(payload));var envelope=object(field("logicalBusinessKey",string("minutes:"+G)),field("inputId",string(G)),field("inputFingerprint",string(binding.fingerprint())),field("protocolVersion",string(VERSION)),field("modelIdentity",string(MODEL)),field("payloadSha256",string(ph)),field("roots",new Sequence(roots)));
  String hash=new CanonicalValueCodec().sha256(ContractValues.envelope("ama.minutes-model-request.v1","MODEL_REQUEST",envelope),GRAPH.canonical(),()->false);
  var body=object(field("protocolVersion",string(VERSION)),field("logicalBusinessKey",string("minutes:"+G)),field("logicalRequestHash",string(hash)),field("executionRequestHash",string(hash)),field("executionId",string(E)),field("executionNo",number(1)),field("payloadSha256",string(ph)),field("payload",payload));return new Wire(json(body),header(envelope),envelope);
 }
 Object verify(byte[] body,String header)throws Exception {
  Class<?> c=assertDoesNotThrow(()->Class.forName("ama.model.ControlledMinutesProtocol"));
  try{return c.getMethod("verify",byte[].class,String.class).invoke(null,body,header);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw e;}
 }
 @Test void recomputesRealV2InputAndLogicalEnvelopeInsteadOfComparingSuppliedHashes()throws Exception {
  var w=wire();assertNotNull(verify(w.body(),w.header()));
  var roots=(Sequence)w.envelope().entries().stream().filter(e->e.key().equals("roots")).findFirst().orElseThrow().value();var altered=new ArrayList<>(roots.values());altered.set(0,replace((ObjectValue)altered.getFirst(),"sha256",string("b".repeat(64))));
  assertThrows(RuntimeException.class,()->verify(w.body(),header(replace(w.envelope(),"roots",new Sequence(altered)))));
 }
 @Test void rejectsWrongInputFingerprintAndDuplicateUnsortedRoots()throws Exception {
  var w=wire();assertThrows(RuntimeException.class,()->verify(w.body(),header(replace(w.envelope(),"inputFingerprint",string("b".repeat(64))))));
  var roots=(Sequence)w.envelope().entries().stream().filter(e->e.key().equals("roots")).findFirst().orElseThrow().value();var reversed=new ArrayList<>(roots.values());Collections.reverse(reversed);
  assertThrows(RuntimeException.class,()->verify(w.body(),header(replace(w.envelope(),"roots",new Sequence(reversed)))));
  assertThrows(RuntimeException.class,()->verify(w.body(),header(replace(w.envelope(),"roots",list(roots.values().getFirst(),roots.values().getFirst())))));
 }
 @Test void rejectsMissingCombinedPaddingNoncanonicalAndOversizeHeaders()throws Exception {
  var w=wire();for(String bad:Arrays.asList(null,"",w.header()+"=",w.header()+","+w.header()," "+w.header(),Base64.getUrlEncoder().withoutPadding().encodeToString((" "+new String(json(w.envelope()),StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)),"A".repeat(8193)))assertThrows(RuntimeException.class,()->verify(w.body(),bad));
 }
 @Test void rejectsNoncanonicalBodyDuplicateNumbersAndWrongExecution()throws Exception {
  var w=wire();String body=new String(w.body(),StandardCharsets.UTF_8);
  for(String bad:List.of(" "+body,body+"{}",body.replace("\"executionNo\":1","\"executionNo\":2"),body.replace("\"executionNo\":1","\"executionNo\":1.0"),body.replace("\"executionNo\":1","\"executionNo\":1,\"executionNo\":1")))assertThrows(RuntimeException.class,()->verify(bad.getBytes(StandardCharsets.UTF_8),w.header()));
 }
}
