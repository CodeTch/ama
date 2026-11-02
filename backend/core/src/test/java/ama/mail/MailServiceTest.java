package ama.mail;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MailServiceTest {
 @Test void unknownDeliveryDoesNotTriggerBlindRetry(){
  int[] sends={0},queries={0};var request=new MailPort.Request(UUID.randomUUID().toString(),List.of("person@example.invalid"),"纪要已发布","https://example.invalid/meeting/authorized");
  MailPort port=new MailPort(){public Outcome send(Request value){sends[0]++;throw new IllegalStateException("lost ACK");}public Outcome findByBusinessKey(String key){queries[0]++;return new Outcome(State.NOT_FOUND,null);}};
  assertEquals(MailPort.State.UNKNOWN,MailService.attempt(request,port,true).state());
  for(int i=0;i<3;i++)assertEquals(MailPort.State.UNKNOWN,MailService.attempt(request,port,false).state());
  assertEquals(1,sends[0]);assertEquals(3,queries[0]);
 }
 @Test void lostSuccessfulResponseCanReconcileByStableKey(){
  var request=new MailPort.Request("job-key",List.of("person@example.invalid"),"纪要","link");
  MailPort port=new MailPort(){public Outcome send(Request value){throw new AssertionError("must not send");}public Outcome findByBusinessKey(String key){assertEquals("job-key",key);return new Outcome(State.SENT,"message-1");}};
  assertEquals("message-1",MailService.attempt(request,port,false).messageId());
 }
}
