package ama.config.json;
import ama.config.*;import ama.contract.ConfigArchiveContracts.*;import ama.contract.MinutesContracts.*;
import java.nio.charset.StandardCharsets;import java.util.*;import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;import static org.junit.jupiter.api.Assertions.*;
class ConfigWireReaderTest {
 static final ArchiveReadBudget B=new ArchiveReadBudget(100000,1000000,100,1000,30,100000,30,100,100000,100000,1000000,100000,100,10000,10000);
 static byte[] fixture(String name)throws Exception{try(var in=ConfigWireReaderTest.class.getResourceAsStream("/ama/config/"+name+".json")){assertNotNull(in);return in.readAllBytes();}}
 static ArchiveReadState state(){return new ArchiveReadState(B,()->false);}
 static ArchiveBytes bytes(byte[] raw,ArchiveReadState s){return s.capture(raw,false);}
 @Test void adoptedRawVectorsReadTheirExactIdentitiesAndHashes()throws Exception{
  String[] names={"codec","parser","confirmation","processing"};ArtifactType[] types={ArtifactType.CANONICAL_CODEC,ArtifactType.PARSER_CONFIG,ArtifactType.CONFIRMATION_HASH,ArtifactType.PROCESSING_CONFIG};
  String[] versions={"ama.canonical-value.v1","ama.strict-utf8-lines.v1","ama.transcript-confirmation.v1","test.import.1"};
  String[] hashes={"2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19","7b9f57377ad401fe040287e133f3efa81d9956ea581a5e497ee6c0835051962a","2f94e75e8d53ef37a152a1ccd63b54a61a6853e07508a5c927a4ba8e8209c0aa","e4e58cadff0045688e0cab8200018af7c4407a39a1ca97924f14b68c6d51765b"};
  for(int i=0;i<names.length;i++){var s=state();var expected=new Identity(types[i],versions[i]);var raw=fixture(names[i]);var result=assertDoesNotThrow(()->new StrictConfigArchiveReader().read(bytes(raw,s),expected,s));assertEquals(expected,result.identity());assertEquals(hashes[i],result.rawSha256());}
  var s=state();assertDoesNotThrow(()->new StrictProcessingManifestReader().read(s.capture(fixture("manifest"),true),s));
 }
 @Test void transportRejectsDuplicateUnknownNoncanonicalAndBadUnicode()throws Exception{
  String raw=new String(fixture("codec"),StandardCharsets.UTF_8);var identity=new Identity(ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1");
  for(var bad:List.of("{}"," "+raw,raw+"\n",raw.replace("\"version\":","\"unknown\":"),raw.replace("\"version\":","\"ver\\u0073ion\":\"x\",\"version\":"),raw.replace("ama.canonical-value.v1","\\ud800"),"\ufeff"+raw,raw+"{}")){
   var s=state();assertThrows(Failure.class,()->new StrictConfigArchiveReader().read(bytes(bad.getBytes(StandardCharsets.UTF_8),s),identity,s));
  }
 }
 @Test void rawArraysAreIsolatedAfterBoundCheckAndCumulativeBytesCannotReset()throws Exception{
  byte[] raw=fixture("codec");var s=state();var captured=bytes(raw,s);raw[0]=0;assertEquals("2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19",captured.sha256(()->false));
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->ArchiveBytes.capture(new byte[2],1,100,()->false)).code());
  var tiny=new ArchiveReadBudget(223,223,1,100,30,10000,30,100,1000,1000,10000,223,10,100,100);
  var bounded=new ArchiveReadState(tiny,()->false);bounded.capture(fixture("codec"),false);assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->bounded.capture(new byte[1],true)).code());
 }

 @Test void canonicalControlEscapesAndSupplementaryConfigTextArePreserved()throws Exception{
  byte[] raw=fixture("control-prompt");var expected=new Identity(ArtifactType.PROMPT_CONFIG,"test.generation.prompt.1");var state=state();
  var parsed=new StrictConfigArchiveReader().read(bytes(raw,state),expected,state);
  try(var in=ConfigWireReaderTest.class.getResourceAsStream("/ama/config/control-prompt.sha256")){assertEquals(new String(in.readAllBytes(),StandardCharsets.US_ASCII).strip(),parsed.rawSha256());}
  String shorter=new String(raw,StandardCharsets.UTF_8).replace("\\u000a","\\n");var s=state();
  assertEquals(INVALID_SCHEMA,assertThrows(Failure.class,()->new StrictConfigArchiveReader().read(bytes(shorter.getBytes(StandardCharsets.UTF_8),s),expected,s)).code());
 }
 @Test void unknownProcessingKindIsNotARecognizedFutureCapability()throws Exception{
  String raw=new String(fixture("manifest"),StandardCharsets.UTF_8).replace("\"kind\":\"IMPORT\"","\"kind\":\"FUTURE\"");var s=state();
  assertEquals(INVALID_SCHEMA,assertThrows(Failure.class,()->new StrictProcessingManifestReader().read(s.capture(raw.getBytes(StandardCharsets.UTF_8),true),s)).code());
 }

 @Test void recognizedFutureProcessingAndPinnedMaterialsNeverFallBackToImport()throws Exception{
  String original=new String(fixture("manifest"),StandardCharsets.UTF_8);
  for(var kind:List.of("ASR","ENHANCE")){
   var raw=original.replace("\"kind\":\"IMPORT\"","\"kind\":\""+kind+"\"").getBytes(StandardCharsets.UTF_8);var state=state();
   assertEquals(CAPABILITY_UNAVAILABLE,assertThrows(Failure.class,()->new StrictProcessingManifestReader().read(state.capture(raw,true),state)).code());
  }
  String pinned=original.replace("\"mode\":\"EMPTY\"","\"mode\":\"PINNED\"");var state=state();
  assertEquals(CAPABILITY_UNAVAILABLE,assertThrows(Failure.class,()->new StrictProcessingManifestReader().read(state.capture(pinned.getBytes(StandardCharsets.UTF_8),true),state)).code());
  var future=new String(fixture("codec"),StandardCharsets.UTF_8).replace("ama.profile-contract.v1","ama.profile-contract.v9").getBytes(StandardCharsets.UTF_8);var other=state();
  assertEquals(PROFILE_UNAVAILABLE,assertThrows(Failure.class,()->new StrictConfigArchiveReader().read(other.capture(future,false),new Identity(ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1"),other)).code());
 }
}
