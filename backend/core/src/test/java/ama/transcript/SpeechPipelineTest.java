package ama.transcript;

import ama.contract.SpeechContracts.*;
import ama.model.SpeechModelPort;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechPipelineTest {
 private static final WavClip CLIP=new WavClip("original-file",16000,32000);
 private static class Model implements SpeechModelPort {
  final List<String> calls=new ArrayList<>();boolean failPunctuation;
  public List<SpeechSpan> vad(WavClip c){calls.add("VAD");return List.of(new SpeechSpan(16000,48000));}
  public List<SpeakerSpan> diarize(WavClip c,List<SpeechSpan> speech){calls.add("DIARIZATION");return List.of(new SpeakerSpan(16000,48000,"cluster-0"));}
  public RecognitionResult recognize(WavClip c){calls.add("ASR");return new RecognitionResult("非常非常重要 123",null);}
  public String punctuate(String text){calls.add("ENHANCEMENT");if(failPunctuation)throw new IllegalStateException("provider failed");return text+"。";}
  public VoiceEmbedding embed(WavClip c){calls.add("EMBEDDING");return new VoiceEmbedding("fixture-v1",2,List.of(0d,1d));}
 }
 private static class CorrectingModel extends Model {
  boolean failCorrection;
  public Optional<String> correctionModel(){return Optional.of("offline-llm");}
  public RecognitionResult recognize(WavClip c){calls.add("ASR");return new RecognitionResult("因该复核 123",null);}
  public CorrectionResult correct(String text){calls.add("CORRECTION");if(failCorrection)throw new IllegalStateException("correction unavailable");return new CorrectionResult("offline-llm",text,List.of(new TextCorrection(0,"因该","应该","同音错字")));}
 }
 @Test void correctionFailureResumesAfterFiveSavedStagesAndNeverChangesRaw(){
  var first=new CorrectingModel();first.failCorrection=true;var store=new Store();
  assertThrows(IllegalStateException.class,()->new SpeechPipeline(first).transcribe(CLIP,store));assertEquals(5,store.stages.size());
  var second=new CorrectingModel();var result=new SpeechPipeline(second).transcribe(CLIP,store);
  assertEquals(List.of("CORRECTION"),second.calls);assertEquals("因该复核 123",result.raw().text());assertEquals("因该复核 123。",result.enhancedText());assertEquals("应该复核 123。",result.correction().correctedText());
  var replay=new CorrectingModel();assertEquals(result,new SpeechPipeline(replay).transcribe(CLIP,store));assertTrue(replay.calls.isEmpty());
 }
 @Test void savedCorrectionMustMatchPunctuationAndConfiguredModel(){
  var model=new CorrectingModel();var store=new Store();new SpeechPipeline(model).transcribe(CLIP,store);model.calls.clear();
  for(var wrong:List.of(new CorrectionResult("foreign-model","因该复核 123。",List.of()),new CorrectionResult("offline-llm","另一份原文",List.of()))){
   store.stages.put(SpeechPipeline.Stage.CORRECTION,new SpeechPipeline.Correction(wrong));assertThrows(IllegalArgumentException.class,()->new SpeechPipeline(model).transcribe(CLIP,store));assertTrue(model.calls.isEmpty());
  }
 }
 @Test void silentRecognitionSkipsTextModelsAndDoesNotInventWords(){
  var model=new CorrectingModel(){
   public RecognitionResult recognize(WavClip c){return new RecognitionResult("",List.of());}
   public String punctuate(String text){throw new AssertionError("Silence must not call punctuation");}
   public CorrectionResult correct(String text){throw new AssertionError("Silence must not call correction");}
  };
  var result=new SpeechPipeline(model).transcribe(CLIP,new Store());assertEquals("",result.raw().text());assertEquals("",result.enhancedText());assertEquals("",result.correction().correctedText());
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
