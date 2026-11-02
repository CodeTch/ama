package ama.task;
import ama.transcript.*;import java.time.Duration;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class RecordingPreparationBudgetTest {
 private static TranscriptImportRuntime.Settings configured(long bytes,Duration recording){return new TranscriptImportRuntime.Settings(()->null,ama.minutes.ControlledMinutesAdmin.READ,new TranscriptDocumentParser.ParseBudget(33554432,1048576,4096),Duration.ofSeconds(60),bytes,recording);}
 @Test void onlyTheExplicitRecordingProfileHasTheSeparatelyConfiguredDeadline(){
  var settings=configured(RecordingTranscriptProfile.MAX_BYTES,Duration.ofMinutes(10));
  assertEquals(Duration.ofMinutes(10),settings.deadlineFor(RecordingTranscriptProfile.PARSER));
  assertEquals(Duration.ofSeconds(60),settings.deadlineFor(ControlledSpeechProfile.PARSER));
  assertEquals(Duration.ofSeconds(60),settings.deadlineFor(TextTranscriptParser.PROFILE_VERSION));
  assertEquals(Duration.ofSeconds(60),configured(0,Duration.ofMinutes(10)).deadlineFor(RecordingTranscriptProfile.PARSER));
 }
 @Test void oldConstructorsKeepTheirExistingDeadlineAndTheNewBoundIsFinite(){
  var settings=configured(RecordingTranscriptProfile.MAX_BYTES,Duration.ofMinutes(10));
  var old=new TranscriptImportRuntime.Settings(settings.archives(),settings.archiveBudget(),settings.aggregate(),Duration.ofSeconds(60));
  assertEquals(Duration.ofSeconds(60),old.deadlineFor(RecordingTranscriptProfile.PARSER));
  var five=new TranscriptImportRuntime.Settings(settings.archives(),settings.archiveBudget(),settings.aggregate(),Duration.ofSeconds(60),RecordingTranscriptProfile.MAX_BYTES);
  assertEquals(Duration.ofSeconds(60),five.deadlineFor(RecordingTranscriptProfile.PARSER));
  for(var invalid:new Duration[]{Duration.ZERO,Duration.ofMillis(-1),Duration.ofHours(1).plusMillis(1)})assertThrows(IllegalArgumentException.class,()->configured(RecordingTranscriptProfile.MAX_BYTES,invalid));
 }
}
