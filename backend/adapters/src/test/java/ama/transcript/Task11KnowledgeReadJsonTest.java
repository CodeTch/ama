package ama.transcript;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task11KnowledgeReadJsonTest {
 @Test void directReadParserAndCanonicalEncoderPreserveStrictWire(){var payload=Task11DocumentViewTest.payload();byte[] canonical=ControlledKnowledgeClient.canonical(payload);var budget=new Task11AcceptanceBindingTest().budget(false);var decoded=ControlledKnowledgeReadJson.parse(canonical,8388608,budget);assertArrayEquals(canonical,ControlledKnowledgeReadJson.canonical(decoded,budget));}
 @Test void duplicateTrailingInvalidUtf8AndNonintegerValuesFail(){for(String value:List.of("{\"x\":1,\"x\":2}","{}{}","[]","{\"x\":1.0}","{\"x\":-1}","{\"x\":\"\\ud800\"}","\uFEFF{}")){assertThrows(RuntimeException.class,()->ControlledKnowledgeReadJson.parse(value.getBytes(StandardCharsets.UTF_8),262144,new Task11AcceptanceBindingTest().budget(false)),value);}assertThrows(RuntimeException.class,()->ControlledKnowledgeReadJson.parse(new byte[]{'{','\"','x','\"',':','\"',(byte)255,'\"','}'},262144,new Task11AcceptanceBindingTest().budget(false)));}
 @Test void cancellationStopsParsingAndCanonicalEncoding(){byte[] value="{}".getBytes(StandardCharsets.UTF_8);assertThrows(ama.contract.MinutesContracts.Failure.class,()->ControlledKnowledgeReadJson.parse(value,262144,new Task11AcceptanceBindingTest().budget(true)));assertThrows(ama.contract.MinutesContracts.Failure.class,()->ControlledKnowledgeReadJson.canonical(Map.of("x","x"),new Task11AcceptanceBindingTest().budget(true)));}
 @Test void explicitWireBoundIsCheckedBeforeParse(){assertThrows(ama.contract.MinutesContracts.Failure.class,()->ControlledKnowledgeReadJson.parse("{\"x\":1}".getBytes(StandardCharsets.UTF_8),2,new Task11AcceptanceBindingTest().budget(false)));}
}
