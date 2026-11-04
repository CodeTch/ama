package ama.transcript;

import ama.contract.SpeechContracts.*;
import ama.model.SpeechModelPort;
import java.util.*;
import java.util.function.*;

/** Successful checkpoints belong to the caller's exact input/clip/configuration scope.
 * The store must establish that scope and persist before the next external call. */
public final class SpeechPipeline {
 public enum Stage { VAD, DIARIZATION, ASR, ENHANCEMENT, EMBEDDING, CORRECTION }
 public sealed interface Snapshot permits Vad,Diarization,Recognition,Enhancement,Embedding,Correction {}
 public record Vad(List<SpeechModelPort.SpeechSpan> spans) implements Snapshot {public Vad{spans=List.copyOf(spans);}}
 public record Diarization(List<SpeakerSpan> spans) implements Snapshot {public Diarization{spans=List.copyOf(spans);}}
 public record Recognition(RecognitionResult result) implements Snapshot {public Recognition{Objects.requireNonNull(result);}}
 public record Enhancement(String text) implements Snapshot {public Enhancement{SpeechPipeline.text(text);}}
 public record Embedding(VoiceEmbedding result) implements Snapshot {public Embedding{Objects.requireNonNull(result);}}
 public record Correction(CorrectionResult result) implements Snapshot {public Correction{Objects.requireNonNull(result);}}
 public interface Store {
  Optional<Snapshot> load(Stage stage);
  /** Immutable put: an acknowledged success cannot be replaced by another response. */
  void save(Stage stage,Snapshot value);
 }
 public record Result(List<SpeechModelPort.SpeechSpan> speech,List<SpeakerSpan> speakers,
                      RecognitionResult raw,String enhancedText,VoiceEmbedding embedding,CorrectionResult correction) {
  public Result{speech=List.copyOf(speech);speakers=List.copyOf(speakers);Objects.requireNonNull(raw);text(enhancedText);Objects.requireNonNull(embedding);}
 }
 private final SpeechModelPort model;
 public SpeechPipeline(SpeechModelPort model){this.model=Objects.requireNonNull(model);}
 public Result transcribe(WavClip clip,Store store){
  return transcribe(clip,store,List::of);
 }
 public Result transcribe(WavClip clip,Store store,Supplier<List<String>> entities){
  Objects.requireNonNull(clip);Objects.requireNonNull(store);clip.endSampleExclusive();
  var vad=stage(store,Stage.VAD,Vad.class,()->new Vad(model.vad(clip)),v->{for(var span:v.spans())range(clip,span.startSample(),span.endSample());});
  var speakers=stage(store,Stage.DIARIZATION,Diarization.class,()->new Diarization(model.diarize(clip,vad.spans())),v->{for(var span:v.spans()){range(clip,span.startSample(),span.endSample());if(span.enterpriseId()!=null)throw new IllegalArgumentException("Model clusters cannot grant a human identity");}});
  var raw=stage(store,Stage.ASR,Recognition.class,()->new Recognition(model.recognize(clip)),v->{text(v.result().text());if(v.result().tokens()!=null)for(var token:v.result().tokens())range(clip,token.startSample(),token.endSample());});
  var enhanced=stage(store,Stage.ENHANCEMENT,Enhancement.class,()->new Enhancement(raw.result().text().isBlank()?raw.result().text():model.punctuate(raw.result().text())),v->{text(v.text());if(raw.result().text().isBlank()!=v.text().isBlank())throw new IllegalArgumentException("Enhancement cannot invent or erase speech");});
  var embedding=stage(store,Stage.EMBEDDING,Embedding.class,()->new Embedding(model.embed(clip)),v->{});
  CorrectionResult correction=null;var correctionModel=model.correctionModel();
  if(correctionModel.isPresent()){
   correction=stage(store,Stage.CORRECTION,Correction.class,()->new Correction(enhanced.text().isBlank()?new CorrectionResult(correctionModel.get(),enhanced.text(),List.of()):model.correct(enhanced.text(),entities.get())),v->{
    if(!v.result().sourceText().equals(enhanced.text())||!v.result().modelVersion().equals(correctionModel.get()))throw new IllegalArgumentException("Correction checkpoint source or model mismatch");
   }).result();
  }
  return new Result(vad.spans(),speakers.spans(),raw.result(),enhanced.text(),embedding.result(),correction);
 }
 private static <T extends Snapshot> T stage(Store store,Stage stage,Class<T> type,Supplier<T> invoke,Consumer<T> validate){
  var saved=Objects.requireNonNull(store.load(stage));
  if(saved.isPresent()){if(!type.isInstance(saved.get()))throw new IllegalArgumentException("Checkpoint stage mismatch");var value=type.cast(saved.get());validate.accept(value);return value;}
  var value=Objects.requireNonNull(invoke.get());validate.accept(value);store.save(stage,value);return value;
 }
 private static void range(WavClip clip,long start,long end){if(start<clip.startSample()||end<=start||end>clip.endSampleExclusive())throw new IllegalArgumentException("Stage output outside original clip");}
 private static void text(String value){if(value==null||value.length()>65536)throw new IllegalArgumentException("Bounded speech text required");ama.contract.MinutesContracts.scalars(value);}
}
