package ama.transcript;
import com.fasterxml.jackson.databind.ObjectMapper;import java.nio.charset.StandardCharsets;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11KnowledgeCanonicalTest {
 @Test void exactRendererJsonBytesSurviveTransportAndFieldOrdering()throws Exception {try(var in=getClass().getResourceAsStream("/ama/knowledge/canonical-v1.json")){var cases=new ObjectMapper().readTree(in);for(var test:cases){var value=new ObjectMapper().convertValue(test.get("value"),Object.class);assertArrayEquals(test.get("canonical").asText().getBytes(StandardCharsets.UTF_8),ControlledKnowledgeClient.canonical(value));}}}
}
