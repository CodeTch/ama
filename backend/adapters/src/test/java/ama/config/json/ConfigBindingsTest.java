package ama.config.json;
import ama.config.*;import ama.contract.MinutesContracts.*;import ama.contract.ConfigArchiveContracts.*;import ama.contract.TaskContracts.*;
import java.util.*;import java.nio.charset.StandardCharsets;import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;import static org.junit.jupiter.api.Assertions.*;
class ConfigBindingsTest {
 static final String MEETING="11111111-1111-4111-8111-111111111111",INPUT="22222222-2222-4222-8222-222222222222",BATCH="33333333-3333-4333-8333-333333333333",FILE="44444444-4444-4444-8444-444444444444",SHA="559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd",TIME="2026-10-22T00:00:00.000000Z",H="0".repeat(64);
 static final ArchiveReadBudget B=ConfigWireReaderTest.B;
 static byte[] fixture(String name)throws Exception{return ConfigWireReaderTest.fixture(name);}
 static FullRow row(Table t,Object... values){var cells=new ArrayList<Cell>();for(var v:values)cells.add(v==null?new NullCell():v instanceof Number n?cell(n.longValue()):cell((String)v));return new FullRow(t,cells);}
 static List<RawArchive> archives()throws Exception{return List.of(new RawArchive(ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1",fixture("codec")),new RawArchive(ArtifactType.PARSER_CONFIG,"ama.strict-utf8-lines.v1",fixture("parser")),new RawArchive(ArtifactType.CONFIRMATION_HASH,"ama.transcript-confirmation.v1",fixture("confirmation")),new RawArchive(ArtifactType.PROCESSING_CONFIG,"test.import.1",fixture("processing")));}
 static StoredProvenanceFacts prerequisites(){
  return new StoredProvenanceFacts(List.of(row(Table.INPUT_BATCH,BATCH,MEETING,"TRANSCRIPT",H,7L,TIME),row(Table.INPUT_BATCH_FILE,BATCH,1L,FILE,SHA)),List.of(new ContentFile(FILE,SHA,1,MEETING)),List.of(),List.of(),100000L);
 }
 static ConfigBindings service(){return new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader());}
 static String expected()throws Exception {try(var in=ConfigBindingsTest.class.getResourceAsStream("/ama/config/processing-domain.sha256")){return new String(in.readAllBytes(),StandardCharsets.US_ASCII).strip();}}
 static FullRow input(String fingerprint,String manifest){return row(Table.TRANSCRIPTION_INPUT,INPUT,MEETING,BATCH,"IMPORT",null,null,"EMPTY","test.import.1","ama.strict-utf8-lines.v1",null,null,1L,manifest,fingerprint,7L,2L,TIME);}
 @Test void draftComputeNeedsNoOwnStoredFingerprintTaskOrOutputRevision()throws Exception{
  var binding=service().computeProcessing(fixture("manifest"),prerequisites(),archives(),B,()->false);assertNotNull(binding);assertEquals(expected(),binding.fingerprint());
  try(var stream=ConfigBindingsTest.class.getResourceAsStream("/ama/config/processing-domain.hex")){
   String hex=new String(stream.readAllBytes(),StandardCharsets.US_ASCII).strip();assertEquals(hex,HexFormat.of().formatHex(new ama.canonical.CanonicalValueCodec().encode(ama.minutes.contract.ContractValues.envelope("ama.transcription-input.v1","TRANSCRIPTION_INPUT",binding.manifest()),binding.canonicalBudget(),()->false)));
  }
 }
 @Test void storedVerifyRequiresActualDigestTaskAndProducerChain()throws Exception{
  String fp=expected();var p=prerequisites();var rows=new ArrayList<>(p.rows());var input=input(fp,new String(fixture("manifest"),StandardCharsets.UTF_8));rows.add(input);
  var task=new TaskSpec("TRANSCRIBE","TRANSCRIBE",new SubjectRef("MEETING",MEETING),H,new TypedRef("TRANSCRIPTION_INPUT",INPUT),fp,9,0,2,1);
  var facts=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),List.of(task),100000L);
  var binding=service().verifyProcessing(input,facts,archives(),B,()->false);assertNotNull(binding);assertEquals(fp,binding.fingerprint());
  var bad=input(H,input.string("config_manifest"));assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->service().verifyProcessing(bad,facts,archives(),B,()->false)).code());
 }
 @Test void originalParseBudgetSurvivesCompleteClosure()throws Exception{
  var binding=service().computeProcessing(fixture("manifest"),prerequisites(),archives(),B,()->false);
  var method=Arrays.stream(binding.getClass().getMethods()).filter(m->m.getName().equals("parseBudget")).findFirst();
  assertTrue(method.isPresent(),"Completed original binding must retain parser budget");
  assertEquals(new ama.transcript.TranscriptDocumentParser.ParseBudget(1024,1024,16),method.orElseThrow().invoke(binding));
 }
 @Test void invalidRawOrIncompleteArchiveClosureCannotMintBinding()throws Exception{
  var service=service();var archives=archives();var manifest=fixture("manifest");
  assertThrows(Failure.class,()->service.computeProcessing("{}".getBytes(StandardCharsets.UTF_8),prerequisites(),archives,B,()->false));
  assertEquals(PROFILE_UNAVAILABLE,assertThrows(Failure.class,()->service.computeProcessing(manifest,prerequisites(),archives.subList(0,3),B,()->false)).code());
  var duplicate=new ArrayList<>(archives);duplicate.add(archives.getFirst());
  assertThrows(Failure.class,()->service.computeProcessing(manifest,prerequisites(),duplicate,B,()->false));
 }

 @Test void matchingArchiveIdentityCannotSubstituteDifferentRawDefinitionOrExtraNodes()throws Exception{
  var raw=new ArrayList<>(archives());var processing=raw.get(3);
  byte[] different=new String(processing.bytes(),StandardCharsets.UTF_8).replace("\"maxNodes\":4096","\"maxNodes\":4095").getBytes(StandardCharsets.UTF_8);
  assertFalse(Arrays.equals(different,processing.bytes()));raw.set(3,new RawArchive(processing.claimedArtifactType(),processing.claimedVersion(),different));
  assertThrows(Failure.class,()->service().computeProcessing(fixture("manifest"),prerequisites(),raw,B,()->false));
  var extra=new ArrayList<>(archives());extra.add(RawHashChainTest.full().stream().filter(a->a.claimedArtifactType()==ArtifactType.REVIEW_HASH).findFirst().orElseThrow());
  assertEquals(PROFILE_UNAVAILABLE,assertThrows(Failure.class,()->service().computeProcessing(fixture("manifest"),prerequisites(),extra,B,()->false)).code());
 }
 @Test void fileMeetingSizeHashAndSavedTaskFencesAreRequiredFacts()throws Exception{
  var p=prerequisites();
  for(var bad:List.of(new ContentFile(FILE,SHA,2,MEETING),new ContentFile(FILE,H,1,MEETING),new ContentFile(FILE,SHA,1,"00000000-0000-0000-0000-000000000099"),new ContentFile(FILE,SHA,1))){
   var f=new StoredProvenanceFacts(p.rows(),List.of(bad),List.of(),List.of(),100000L);
   assertEquals(INPUT_INVALID,assertThrows(Failure.class,()->service().computeProcessing(fixture("manifest"),f,archives(),B,()->false)).code());
  }
  var rows=new ArrayList<>(p.rows());var input=input(expected(),new String(fixture("manifest"),StandardCharsets.UTF_8));rows.add(input);
  var missing=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),100000L);
  assertEquals(PROFILE_UNAVAILABLE,assertThrows(Failure.class,()->service().verifyProcessing(input,missing,archives(),B,()->false)).code());
  var wrong=new TaskSpec("TRANSCRIBE","TRANSCRIBE",new SubjectRef("MEETING",MEETING),H,new TypedRef("TRANSCRIPTION_INPUT",INPUT),expected(),10,0,2,1);
  var inconsistent=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),List.of(wrong),100000L);
  assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->service().verifyProcessing(input,inconsistent,archives(),B,()->false)).code());
 }
 @Test void injectedParsedDtoCannotAttestRawArchiveAndCancellationNeverMintsSuccess()throws Exception{
  ConfigArchiveReader impostor=(bytes,identity,state)->{
   var parsed=new StrictConfigArchiveReader().read(bytes,identity,state);
   return new ConfigArchiveReader.ParsedArchive(parsed.identity(),H,parsed.wire());
  };
  assertEquals(PROFILE_UNAVAILABLE,assertThrows(Failure.class,()->new ConfigBindings(impostor,new StrictProcessingManifestReader()).computeProcessing(fixture("manifest"),prerequisites(),archives(),B,()->false)).code());
  var failure=assertThrows(Failure.class,()->service().computeProcessing(fixture("manifest"),prerequisites(),archives(),B,()->true));
  assertEquals(CANCELLED,failure.code());assertEquals("CANCELLED",failure.getMessage());assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
  Thread.currentThread().interrupt();try{assertEquals(CANCELLED,assertThrows(Failure.class,()->service().computeProcessing(fixture("manifest"),prerequisites(),archives(),B,()->false)).code());assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
}
