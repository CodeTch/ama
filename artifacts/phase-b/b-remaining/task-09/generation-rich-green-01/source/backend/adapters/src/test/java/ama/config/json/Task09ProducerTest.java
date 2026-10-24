package ama.config.json;
import ama.canonical.*;
import ama.contract.MinutesContracts.*;
import org.junit.jupiter.api.Test;
import static ama.canonical.CanonicalValue.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09ProducerTest {
 @Test void completeFrozenProducerEnvelopeIncludesEveryFenceAndHasExactProfile()throws Exception {
  String meeting="11111111-1111-4111-8111-111111111111",input="22222222-2222-4222-8222-222222222222",fingerprint="a".repeat(64);var budget=new CanonicalBudget(65536,16,1000,4096,100);
  Class<?> type=assertDoesNotThrow(()->Class.forName("ama.minutes.MinutesGenerateProducer"));var method=type.getMethod("compute",String.class,String.class,String.class,long.class,long.class,CanonicalBudget.class,java.util.function.BooleanSupplier.class);
  Object actual=method.invoke(null,meeting,input,fingerprint,7L,3L,budget,(java.util.function.BooleanSupplier)()->false);
  var envelope=object(field("profile",string("ama.minutes-generate-producer.v1")),field("kind",string("TASK_PRODUCER")),field("data",object(field("taskType",string("MINUTES_GENERATE_V1")),field("meetingId",string(meeting)),field("inputId",string(input)),field("inputFingerprint",string(fingerprint)),field("businessVersion",number(7)),field("cancelEpoch",number(3)))));
  assertEquals(new CanonicalValueCodec().sha256(envelope,budget,()->false),actual);assertNotEquals(actual,method.invoke(null,meeting,input,fingerprint,7L,4L,budget,(java.util.function.BooleanSupplier)()->false));
 }
}
