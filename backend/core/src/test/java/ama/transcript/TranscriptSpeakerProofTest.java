package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class TranscriptSpeakerProofTest {
 static final String M=id(1),RAW=id(2),WORK=id(3),SEG=id(4),SP=id(5),FILE=id(6),BATCH=id(7),INPUT=id(8),TASK=id(9);
 static String id(int n){return new UUID(0,n).toString();}
 static FullRow row(Table table,Object...values){var cells=new ArrayList<Cell>();for(Object value:values)cells.add(value==null?new NullCell():value instanceof Long n?new UIntCell(n):new TextCell((String)value));return new FullRow(table,cells);}
 static FullRow revision(String id,long n,String kind,String parent){return row(Table.TRANSCRIPT_REVISION,id,M,n,kind,BATCH,parent,"0".repeat(64),"owner","2026-11-01T00:00:00.000000Z",INPUT,parent==null?TASK:null);}
 static FullRow segment(String revision,String speaker){return row(Table.TRANSCRIPT_SEGMENT,revision,SEG,1L,"原始内容",FILE,1L,0L,8000L,speaker);}
 static List<FullRow> facts(){return List.of(revision(RAW,0,"RAW",null),revision(WORK,1,"WORKING",RAW),segment(RAW,null),segment(WORK,SP),row(Table.TRANSCRIPT_SPEAKER_SNAPSHOT,SP,M,"人工王工",null,"HUMAN",TranscriptSpeakerProof.digest(SP,M,"人工王工",null)),row(Table.TRANSCRIPT_SPEAKER_CHANGE,WORK,SEG,null,SP));}
 @Test void exactImmutableSnapshotAndChangeProofAreRequired(){assertDoesNotThrow(()->TranscriptSpeakerProof.validate(facts()));var missing=facts().stream().filter(r->r.table()!=Table.TRANSCRIPT_SPEAKER_CHANGE).toList();assertThrows(RuntimeException.class,()->TranscriptSpeakerProof.validate(missing));assertThrows(RuntimeException.class,()->TranscriptSpeakerProof.validate(facts().stream().filter(r->r.table()!=Table.TRANSCRIPT_SPEAKER_SNAPSHOT).toList()));}
 @Test void renamedLabelsCannotReinterpretAStoredSpeakerId(){var modified=new ArrayList<>(facts());modified.set(4,row(Table.TRANSCRIPT_SPEAKER_SNAPSHOT,SP,M,"伪造李工",null,"HUMAN",TranscriptSpeakerProof.digest(SP,M,"人工王工",null)));assertThrows(RuntimeException.class,()->TranscriptSpeakerProof.validate(modified));}
 @Test void rawAttributionsAndUnchangedSegmentProofsAreRejected(){var modified=new ArrayList<>(facts());modified.set(2,segment(RAW,SP));assertThrows(RuntimeException.class,()->TranscriptSpeakerProof.validate(modified));modified.set(2,segment(RAW,null));modified.set(3,segment(WORK,null));assertThrows(RuntimeException.class,()->TranscriptSpeakerProof.validate(modified));}
 @Test void anonymousHistoricalGraphsKeepTheirOriginalShape(){assertDoesNotThrow(()->TranscriptSpeakerProof.validate(List.of(revision(RAW,0,"RAW",null),segment(RAW,null))));}
}
