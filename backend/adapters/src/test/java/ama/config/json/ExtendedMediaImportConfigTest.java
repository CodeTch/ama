package ama.config.json;

import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.transcript.MediaTranscriptParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The new capability uses a distinct archive identity; v1 archives keep their original bytes. */
class ExtendedMediaImportConfigTest {
 private byte[] resource(String name)throws Exception{
  try(var input=getClass().getResourceAsStream("/ama/transcript/"+name+".json")){assertNotNull(input);return input.readAllBytes();}
 }
 @Test void newProfileArchivesAreStrictlyReadable()throws Exception{
  for(String[] entry:new String[][]{{"media-parser-v2","PARSER_CONFIG","ama.media-documents.v2"},{"media-processing-v2","PROCESSING_CONFIG","ama.import-media-documents.v2"}}){
   var state=ConfigWireReaderTest.state();byte[] raw=resource(entry[0]);
   assertDoesNotThrow(()->new StrictConfigArchiveReader().read(state.capture(raw,false),new Identity(ArtifactType.valueOf(entry[1]),entry[2]),state));
  }
 }
 @Test void processingDependencyBindsExactParserBytesAndExtendedCapability()throws Exception{
  var mapper=new ObjectMapper();byte[] raw=resource("media-parser-v2");var parser=mapper.readTree(raw);var processing=mapper.readTree(resource("media-processing-v2"));
  assertEquals(MediaTranscriptParser.FORMATS,mapper.convertValue(parser.path("definition").path("formats"),new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){}));
  var dependency=processing.path("dependencies").get(2).path("ref");
  assertEquals("ama.media-documents.v2",dependency.path("version").asText());
  assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)),dependency.path("sha256").asText());
  assertEquals("pyav.19.0.2",parser.path("definition").path("engines").path("decoder").asText());
  assertEquals("ama.strict-timed-subtitles.v1",parser.path("definition").path("engines").path("subtitles").asText());
 }
}
