package ama.transcript;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class TranscriptSegmentProvenanceTest {
 @Test void immutableHistoricalMediaProfileKeepsItsTimedProvenance(){assertTrue(TranscriptSegmentProvenance.timing(MediaTranscriptParser.LEGACY_PROFILE_VERSION,0L,11000L,null));assertFalse(TranscriptSegmentProvenance.timing(MediaTranscriptParser.LEGACY_PROFILE_VERSION,0L,7200001L,null));assertFalse(TranscriptSegmentProvenance.timing(MediaTranscriptParser.LEGACY_PROFILE_VERSION,0L,100L,"invented"));}
 @Test void realMediaTimesRequireTheVerifiedMediaProfile(){assertTrue(TranscriptSegmentProvenance.timing(MediaTranscriptParser.PROFILE_VERSION,0L,11000L,null));for(String profile:new String[]{"ama.txt.v1","ama.office-documents.v1",null})assertFalse(TranscriptSegmentProvenance.timing(profile,0L,11000L,null));}
 @Test void textStillHasNoInventedTimeOrSpeaker(){assertTrue(TranscriptSegmentProvenance.timing(MediaTranscriptParser.PROFILE_VERSION,null,null,null));assertTrue(TranscriptSegmentProvenance.timing("ama.office-documents.v1",null,null,null));assertFalse(TranscriptSegmentProvenance.timing(MediaTranscriptParser.PROFILE_VERSION,null,null,"invented"));}
 @Test void corruptOrPartialTimesCannotEnterSavedReadsOrMinutes(){for(Long[] times:new Long[][]{{null,100L},{0L,null},{1L,1L},{-1L,1L},{2L,1L},{0L,7200001L}})assertFalse(TranscriptSegmentProvenance.timing(MediaTranscriptParser.PROFILE_VERSION,times[0],times[1],null));}
}
