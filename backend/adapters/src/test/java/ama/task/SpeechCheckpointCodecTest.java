package ama.task;

import ama.transcript.SpeechPipeline;
import ama.contract.SpeechContracts.*;
import ama.model.SpeechModelPort;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechCheckpointCodecTest {
 @Test void allFiveTypedSnapshotsRoundTripWithoutInventedOptionalFields(){
  var values=List.of(new SpeechPipeline.Vad(List.of(new SpeechModelPort.SpeechSpan(16000,48000))),
   new SpeechPipeline.Diarization(List.of(new SpeakerSpan(16000,48000,"cluster-0"))),
   new SpeechPipeline.Recognition(new RecognitionResult("非常非常重要 123",null)),
   new SpeechPipeline.Enhancement("非常非常重要 123。"),new SpeechPipeline.Embedding(new VoiceEmbedding("fixture-v1",2,List.of(0d,1d))));
  var stages=SpeechPipeline.Stage.values();for(int i=0;i<stages.length;i++){
   byte[] bytes=SpeechCheckpointCodec.encode(stages[i],values.get(i));assertEquals(values.get(i),SpeechCheckpointCodec.decode(stages[i],bytes));assertArrayEquals(bytes,SpeechCheckpointCodec.encode(stages[i],values.get(i)));
  }
  var raw=(SpeechPipeline.Recognition)SpeechCheckpointCodec.decode(SpeechPipeline.Stage.ASR,SpeechCheckpointCodec.encode(SpeechPipeline.Stage.ASR,values.get(2)));assertNull(raw.result().tokens());
 }
 @Test void differentStageOrUnrecognizedFieldsCannotBeReinterpretedAsSuccess(){
  byte[] good=SpeechCheckpointCodec.encode(SpeechPipeline.Stage.ENHANCEMENT,new SpeechPipeline.Enhancement("saved"));
  assertThrows(IllegalArgumentException.class,()->SpeechCheckpointCodec.decode(SpeechPipeline.Stage.ASR,good));
  assertThrows(IllegalArgumentException.class,()->SpeechCheckpointCodec.encode(SpeechPipeline.Stage.ASR,new SpeechPipeline.Enhancement("wrong")));
  for(String text:List.of("{\"schema\":\"ama.speech-checkpoint.v1\",\"stage\":\"ENHANCEMENT\",\"value\":{\"text\":\"x\",\"injected\":true}}",
   "{\"schema\":\"ama.speech-checkpoint.v1\",\"stage\":\"ENHANCEMENT\",\"stage\":\"ASR\",\"value\":{\"text\":\"x\"}}",
   new String(good,StandardCharsets.UTF_8)+" {}"))assertThrows(IllegalArgumentException.class,()->SpeechCheckpointCodec.decode(SpeechPipeline.Stage.ENHANCEMENT,text.getBytes(StandardCharsets.UTF_8)));
 }
 @Test void invalidNestedValuesAndOversizedWireDataFailClosed(){
  for(String value:List.of("{\"result\":{\"modelVersion\":\"v1\",\"dimension\":2,\"values\":[1]}}","{\"result\":{\"modelVersion\":\"v1\",\"values\":[1]}}")){
   String json="{\"schema\":\"ama.speech-checkpoint.v1\",\"stage\":\"EMBEDDING\",\"value\":"+value+"}";
   var error=assertThrows(IllegalArgumentException.class,()->SpeechCheckpointCodec.decode(SpeechPipeline.Stage.EMBEDDING,json.getBytes(StandardCharsets.UTF_8)));assertFalse(error.getMessage().contains(value));
  }
  assertThrows(IllegalArgumentException.class,()->SpeechCheckpointCodec.decode(SpeechPipeline.Stage.ASR,new byte[1048577]));
 }
}
