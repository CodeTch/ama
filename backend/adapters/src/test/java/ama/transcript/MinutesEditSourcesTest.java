package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import org.junit.jupiter.api.Test;import static ama.contract.MinutesContracts.*;import static org.junit.jupiter.api.Assertions.*;
class MinutesEditSourcesTest {
 static String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}static final String H="a".repeat(64),TIME="2026-11-01T00:00:00.000000Z";
 final Map<String,Object> root=Map.of("transcript_confirmation_id",id(1),"material_snapshot_id",id(2));
 List<FullRow> rows(){return List.of(new FullRow(Table.TRANSCRIPT_CONFIRMATION,List.of(cell(id(1)),cell(id(3)),cell(id(4)),cell(H),cell("owner"),cell(TIME))),new FullRow(Table.MATERIAL_SNAPSHOT_ITEM,List.of(cell(id(2)),cell(id(5)),cell(1))),new FullRow(Table.PARSED_DOCUMENT,List.of(cell(id(6)),cell(id(5)),cell("parser.v1"),cell(id(7)),cell(1))));}
 List<SourceVersion> versions(){return List.of(new TranscriptVersion(id(1),id(4),id(8),id(9),H),new FileVersion(id(10),id(5),id(6),"parser.v1",H),new KnowledgeVersion(id(5),"provider","document","external-v1",id(10),id(6),"parser.v1",H));}
 @Test void currentTranscriptLocalAndKnowledgeSelectionsRemainEditableAfterTheirStoredProof(){assertDoesNotThrow(()->MinutesEditSources.requireCurrent(root,rows(),versions()));}
 @Test void aRemovedMaterialOrChangedConfirmationCannotBeReintroducedByEditing(){assertThrows(Failure.class,()->MinutesEditSources.requireCurrent(Map.of("transcript_confirmation_id",id(1),"material_snapshot_id",id(11)),rows(),versions()));assertThrows(Failure.class,()->MinutesEditSources.requireCurrent(root,rows(),List.of(new TranscriptVersion(id(11),id(4),id(8),id(9),H))));assertThrows(Failure.class,()->MinutesEditSources.requireCurrent(root,rows(),List.of(new FileVersion(id(10),null,null,null,null))));}
 @Test void snapshotMembershipDoesNotPermitAnotherParserOrDocument(){assertThrows(Failure.class,()->MinutesEditSources.requireCurrent(root,rows(),List.of(new FileVersion(id(10),id(5),id(6),"parser.v2",H))));assertThrows(Failure.class,()->MinutesEditSources.requireCurrent(root,rows(),List.of(new FileVersion(id(10),id(5),id(12),"parser.v1",H))));}
}
