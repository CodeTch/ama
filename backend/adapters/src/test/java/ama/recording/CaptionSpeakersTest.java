package ama.recording;

import ama.contract.SpeechContracts.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CaptionSpeakersTest {
 @Test void splitOnlyWhenOriginalTextAndEveryTimedTokenCanBeAttributed(){
  var speech=List.of(new SpeakerSpan(32000,48000,"0"),new SpeakerSpan(48000,64000,"1"));
  var timed=new RecognitionResult("甲乙",List.of(new Token("甲",32000,48000,null),new Token("乙",48000,64000,null)));
  assertEquals(List.of(new CaptionSpeakers.Turn(0,3000,"说话人 1"),new CaptionSpeakers.Turn(1,4000,"说话人 2")),CaptionSpeakers.align(timed,speech,16000,32000));
  var punctuated=new RecognitionResult("甲，乙",timed.tokens());
  assertEquals("多位说话人",CaptionSpeakers.align(punctuated,speech,0,32000).getFirst().label());
  assertTrue(CaptionSpeakers.align(timed,List.of(),0,32000).isEmpty());
  var partial=CaptionSpeakers.align(timed,List.of(speech.getLast()),0,32000);
  assertEquals("说话人待识别",partial.getFirst().label());
  assertEquals("说话人 2",partial.getLast().label());
 }
}
