package ama.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

class HttpMinutesCompositionTest {
 @Test void onlyAnExactFingerprintCanBecomeAProviderResult(){
  var good=("{\"schemaVersion\":\"ama.minutes-output.v2\",\"inputFingerprint\":\""+"a".repeat(64)+"\",\"body\":\"会议内容\"}").getBytes(StandardCharsets.UTF_8);
  assertDoesNotThrow(()->HttpMinutesComposition.verify("a".repeat(64),good));
  assertThrows(IllegalArgumentException.class,()->HttpMinutesComposition.verify("b".repeat(64),good));
  assertThrows(IllegalArgumentException.class,()->HttpMinutesComposition.verify("a".repeat(64),"{bad".getBytes(StandardCharsets.UTF_8)));
 }
}
