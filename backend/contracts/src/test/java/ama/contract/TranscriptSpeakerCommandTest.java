package ama.contract;
import ama.contract.TranscriptContracts.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class TranscriptSpeakerCommandTest {
 static final String R="11111111-1111-4111-8111-111111111111",S="22222222-2222-4222-8222-222222222222";
 @Test void humanSpeakerIntentIsBoundToExplicitSegmentsAndCannotCarryArbitrarySpeakerIds(){
  var intent=new SpeakerEdit(List.of(S),"人工核对的王工",null);var command=new Command(Operation.SPEAKERS,7,R,List.of(),null,null,intent);assertEquals(List.of(S),command.speakers().segmentIds());
  assertThrows(RuntimeException.class,()->new SpeakerEdit(List.of(S,S),"王工",null));assertThrows(RuntimeException.class,()->new SpeakerEdit(List.of(),"王工",null));assertThrows(RuntimeException.class,()->new SpeakerEdit(List.of(S)," ",null));assertThrows(RuntimeException.class,()->new SpeakerEdit(List.of(S),"x".repeat(257),null));assertThrows(RuntimeException.class,()->new Command(Operation.SAVE,7,R,List.of(new Edit(S,"body",List.of())),null,null,intent));
 }
}
