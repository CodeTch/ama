package ama.transcript;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TranscriptCorrectionRangeTest {
 @Test void repeatedWordsAreLocatedWithoutReplacingAnotherOccurrence(){
  var range=TranscriptCorrectionRange.between("基金😀基金收益","基金😀产品收益");
  assertEquals(3,range.startScalar());assertEquals(5,range.endScalar());assertEquals("基金",range.original());assertEquals("产品",range.replacement());
 }
 @Test void insertionAndDeletionRemainExact(){
  assertEquals(new TranscriptCorrectionRange(2,2,"","。"),TranscriptCorrectionRange.between("结论","结论。"));
  assertEquals(new TranscriptCorrectionRange(2,3,"。",""),TranscriptCorrectionRange.between("结论。","结论"));
 }
}
