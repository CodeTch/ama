package ama.config.json;
import ama.config.*;import ama.contract.ConfigArchiveContracts.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** The two-hour recording profile has its own immutable archive identity. */
class RecordingImportConfigTest {
 @Test void onlyTheExplicitRecordingProfileDeclaresTheLongWavCapability()throws Exception {
  for(String[] entry:new String[][]{{"recording-parser-v1","PARSER_CONFIG","ama.recording-wav.v1"},{"recording-processing-v1","PROCESSING_CONFIG","ama.import-recording-wav.v1"}}){try(var input=getClass().getResourceAsStream("/ama/transcript/"+entry[0]+".json")){assertNotNull(input);byte[] bytes=input.readAllBytes();var state=ConfigWireReaderTest.state();assertDoesNotThrow(()->new StrictConfigArchiveReader().read(state.capture(bytes,false),new Identity(ArtifactType.valueOf(entry[1]),entry[2]),state));}}
 }

 @Test void fullProcessingBindingPinsTheRecordingParserAndRejectsATextStep()throws Exception {
  byte[] parser,processing;try(var input=getClass().getResourceAsStream("/ama/transcript/recording-parser-v1.json")){parser=input.readAllBytes();}try(var input=getClass().getResourceAsStream("/ama/transcript/recording-processing-v1.json")){processing=input.readAllBytes();}
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var manifest=mapper.readTree(ConfigWireReaderTest.fixture("manifest"));String parserSha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(parser)),processingSha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(processing));
  for(var ref:java.util.List.of(manifest.path("configs").path("parser"),manifest.path("steps").path(0).path("parser")))((com.fasterxml.jackson.databind.node.ObjectNode)ref).put("version","ama.recording-wav.v1").put("sha256",parserSha);
  for(var ref:java.util.List.of(manifest.path("configs").path("processing"),manifest.path("steps").path(0).path("config")))((com.fasterxml.jackson.databind.node.ObjectNode)ref).put("version","ama.import-recording-wav.v1").put("sha256",processingSha);
  ((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("steps").path(0)).put("kind","IMPORT_MEDIA_DOCUMENT");var archives=new java.util.ArrayList<>(ConfigBindingsTest.archives());archives.removeIf(a->a.claimedArtifactType()==ArtifactType.PARSER_CONFIG||a.claimedArtifactType()==ArtifactType.PROCESSING_CONFIG);archives.add(new RawArchive(ArtifactType.PARSER_CONFIG,"ama.recording-wav.v1",parser));archives.add(new RawArchive(ArtifactType.PROCESSING_CONFIG,"ama.import-recording-wav.v1",processing));var value=ConfigBindingsTest.service().computeProcessing(mapper.writeValueAsBytes(manifest),ConfigBindingsTest.prerequisites(),archives,ConfigWireReaderTest.B,()->false);assertEquals(230400044L,value.parseBudget().maxSourceBytes());assertEquals(mapper.readTree(parser).path("definition").path("engines").path("modelManifestSha256").asText(),value.engineManifestSha256());
  ((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("steps").path(0)).put("kind","IMPORT_TXT");assertThrows(ama.contract.MinutesContracts.Failure.class,()->ConfigBindingsTest.service().computeProcessing(mapper.writeValueAsBytes(manifest),ConfigBindingsTest.prerequisites(),archives,ConfigWireReaderTest.B,()->false));
 }
}
