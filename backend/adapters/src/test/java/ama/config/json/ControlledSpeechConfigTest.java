package ama.config.json;

import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlledSpeechConfigTest {
 byte[] resource(String name)throws Exception {try(var in=getClass().getResourceAsStream("/ama/transcript/"+name+".json")){assertNotNull(in,name);return in.readAllBytes();}}
 @Test void newArchivesBindTheExactSpeechEngineAndRetainHistoricalParser()throws Exception {
  byte[] parser=resource("media-parser-v3"),processing=resource("media-processing-v3"),engine=resource("speech-engine-v1");var json=new ObjectMapper();
  for(var entry:List.of(new String[]{"media-parser-v3","PARSER_CONFIG","ama.media-documents.v3"},new String[]{"media-processing-v3","PROCESSING_CONFIG","ama.import-media-documents.v3"})){
   var state=ConfigWireReaderTest.state();assertDoesNotThrow(()->new StrictConfigArchiveReader().read(state.capture(resource(entry[0]),false),new Identity(ArtifactType.valueOf(entry[1]),entry[2]),state));
  }
  var engines=json.readTree(parser).path("definition").path("engines");
  assertEquals(sha(engine),engines.path("modelManifestSha256").asText());assertEquals("ama.harness-speech.v1",engines.path("asr").asText());
  assertEquals("ffmpeg.pcm16.v1",engines.path("decoder").asText());
  assertEquals(sha(parser),json.readTree(processing).path("dependencies").get(2).path("ref").path("sha256").asText());
  assertEquals("faster-whisper.1.2.1",json.readTree(resource("media-parser-v2")).path("definition").path("engines").path("asr").asText());
 }
 String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
