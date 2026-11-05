package ama.transcript;
import ama.contract.MinutesContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;
import static org.junit.jupiter.api.Assertions.*;

class MinutesTranscriptSourceTest {
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 static final String H="a".repeat(64),TIME="2026-11-05T00:00:00.000000Z";
 FullRow row(Table t,Object... values){return new FullRow(t,Arrays.stream(values).map(v->v==null?(Cell)new NullCell():v instanceof Long n?cell(n):cell((String)v)).toList());}
 FullRow segment(Long start,String speaker){return row(Table.TRANSCRIPT_SEGMENT,id(1),id(2),0L,"来源",id(3),1L,start,start==null?null:1500L,speaker);}
 List<FullRow> rows(){return List.of(row(Table.TRANSCRIPT_REVISION,id(1),id(4),1L,"WORKING",id(5),null,H,"owner",TIME,id(6),null),row(Table.TRANSCRIPTION_INPUT,id(6),id(4),id(5),"IMPORT",null,null,"EMPTY","config","parser.v1",null,null,1L,"{}",H,1L,0L,TIME),row(Table.TRANSCRIPT_SPEAKER_SNAPSHOT,id(7),id(4),"已保存的标签",null,"HUMAN",H));}
 Map<String,Object> file(){return Map.of("id",id(3),"meeting_id",id(4),"size_bytes",4L,"sha256",H);}
 @Test void onlyExactCapturedSpeakerSegmentAndOriginalFileAreProjected(){var result=MinutesTranscriptSource.read(rows(),List.of(file()),segment(500L,id(7)));assertEquals("已保存的标签",result.get("speakerLabel"));assertEquals(500L,result.get("startMs"));assertEquals(id(3),result.get("fileId"));assertEquals("4",result.get("sizeBytesToken"));assertEquals(id(1),result.get("revisionId"));assertFalse(result.containsKey("enterpriseId"));}
 @Test void untimedAnonymousSourceDoesNotInventAnAudioPositionOrIdentity(){var result=MinutesTranscriptSource.read(rows(),List.of(file()),segment(null,null));assertNull(result.get("startMs"));assertNull(result.get("endMs"));assertNull(result.get("speakerLabel"));}
 @Test void absentDuplicateCrossMeetingOrUnknownSpeakerFactsCannotProject(){assertThrows(Failure.class,()->MinutesTranscriptSource.read(rows(),List.of(),segment(null,null)));assertThrows(Failure.class,()->MinutesTranscriptSource.read(rows(),List.of(file(),file()),segment(null,null)));var wrong=new HashMap<>(file());wrong.put("meeting_id",id(9));assertThrows(Failure.class,()->MinutesTranscriptSource.read(rows(),List.of(wrong),segment(null,null)));assertThrows(Failure.class,()->MinutesTranscriptSource.read(rows(),List.of(file()),segment(500L,id(9))));}
}
