package ama.transcript;

import ama.contract.SpeechContracts.*;
import ama.model.SpeechModelPort;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechPipelineTest {
 private static final WavClip CLIP=new WavClip("original-file",16000,32000);
 private static final class Model implements SpeechModelPort {
  final List<String> calls=new ArrayList<>();boolean failPunctuation;
  public List<SpeechSpan> vad(WavClip c){calls.add("VAD");return List.of(new SpeechSpan(16000,48000));}
  public List<SpeakerSpan> diarize(WavClip c,List<SpeechSpan> speech){calls.add("DIARIZATION");return List.of(new SpeakerSpan(16000,48000,"cluster-0"));}
  public RecognitionResult recognize(WavClip c){calls.add("ASR");return new RecognitionResult("非常非常重要 123",null);}
  public String punctuate(String text){calls.add("ENHANCEMENT");if(failPunctuation)throw new IllegalStateException("provider failed");return text+"。";}
  public VoiceEmbedding embed(WavClip c){calls.add("EMBEDDING");return new VoiceEmbedding("fixture-v1",2,List.of(0d,1d));}
 }
 private static class Store implements SpeechPipeline.Store {
  final Map<SpeechPipeline.Stage,SpeechPipeline.Snapshot> stages=new EnumMap<>(SpeechPipeline.Stage.class);
  public Optional<SpeechPipeline.Snapshot> load(SpeechPipeline.Stage stage){return Optional.ofNullable(stages.get(stage));}
  public void save(SpeechPipeline.Stage stage,SpeechPipeline.Snapshot value){assertNull(stages.putIfAbsent(stage,value),"A completed stage must be immutable");}
 }
 @Test void restartAfterEnhancementFailureReusesExactlyTheThreeSuccessfulStages(){
  var first=new Model();first.failPunctuation=true;var store=new Store();
  assertThrows(IllegalStateException.class,()->new SpeechPipeline(first).transcribe(CLIP,store));
  assertEquals(List.of("VAD","DIARIZATION","ASR","ENHANCEMENT"),first.calls);assertEquals(3,store.stages.size());
  var restarted=new Model();var result=new SpeechPipeline(restarted).transcribe(CLIP,store);
  assertEquals(List.of("ENHANCEMENT","EMBEDDING"),restarted.calls);
  assertEquals("非常非常重要 123",result.raw().text());assertEquals("非常非常重要 123。",result.enhancedText());
  assertNull(result.raw().tokens());assertNull(result.speakers().getFirst().enterpriseId());
  var completed=new Model();assertEquals(result,new SpeechPipeline(completed).transcribe(CLIP,store));assertTrue(completed.calls.isEmpty());
 }
 @Test void checkpointFailurePreventsTheNextExternalStage(){
  var model=new Model();var store=new Store(){@Override public void save(SpeechPipeline.Stage stage,SpeechPipeline.Snapshot value){throw new IllegalStateException("storage unavailable");}};
  assertThrows(IllegalStateException.class,()->new SpeechPipeline(model).transcribe(CLIP,store));assertEquals(List.of("VAD"),model.calls);
 }
 @Test void persistedStageOutsideOriginalClipIsRejectedBeforeAnyNewProviderCall(){
  var model=new Model();var store=new Store();store.stages.put(SpeechPipeline.Stage.VAD,new SpeechPipeline.Vad(List.of(new SpeechModelPort.SpeechSpan(0,16000))));
  assertThrows(IllegalArgumentException.class,()->new SpeechPipeline(model).transcribe(CLIP,store));assertTrue(model.calls.isEmpty());
 }
 @Test void persistedStageTypeCannotBeReinterpretedAsAnotherStage(){
  var model=new Model();var store=new Store();store.stages.put(SpeechPipeline.Stage.VAD,new SpeechPipeline.Enhancement("wrong-stage"));
  assertThrows(IllegalArgumentException.class,()->new SpeechPipeline(model).transcribe(CLIP,store));assertTrue(model.calls.isEmpty());
 }
}
