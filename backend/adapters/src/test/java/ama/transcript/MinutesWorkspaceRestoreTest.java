package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import org.junit.jupiter.api.Test;import static ama.transcript.MinutesRegionMergeTest.*;import static org.junit.jupiter.api.Assertions.*;
class MinutesWorkspaceRestoreTest {
 @Test void restoresBodyAndElementsTogetherWithFreshIdsAndNoInheritedReviews(){
  var source=new ArrayList<>(rows());source.add(row(Table.MINUTES_REVISION,id(20),id(1),4L,null,"人工标题","HUMAN","人工正文",id(7),"owner",TIME));
  var plan=MinutesWorkspaceSnapshots.copy(id(20),id(2),source,id(21),"owner",Task11PublicationManifestTest.ledger());var copied=plan.rows(8,9);
  assertNotEquals(id(20),plan.body());assertNotEquals(id(2),plan.set());var body=copied.stream().filter(r->r.table()==Table.MINUTES_REVISION).findFirst().orElseThrow();assertEquals("人工标题",body.string("title"));assertEquals(id(21),body.string("parent_id"));assertEquals(8,body.number("revision"));assertTrue(copied.stream().anyMatch(r->r.table()==Table.ACTION_ITEM&&r.string("owner_enterprise_id").equals("owner")));assertTrue(copied.stream().noneMatch(r->r.table()==Table.RESULT_REVIEW));
 }
 @Test void rejectsSnapshotFromAnotherTranscriptOrMaterialVersion(){
  var before=new HashMap<String,Object>();before.put("transcript_confirmation_id",id(1));before.put("material_snapshot_id",null);var current=new HashMap<>(before);assertTrue(MinutesWorkspaceSnapshots.sameSources(before,current));current.put("material_snapshot_id",id(2));assertFalse(MinutesWorkspaceSnapshots.sameSources(before,current));current.put("material_snapshot_id",null);current.put("transcript_confirmation_id",id(3));assertFalse(MinutesWorkspaceSnapshots.sameSources(before,current));
 }
 @Test void exactRegeneratedActionKeepsAcceptedIdentityAndNullableDate(){
  var source=new ArrayList<>(rows());source.add(row(Table.MINUTES_ELEMENT,id(30),id(3),"ACTION",1L,"人工行动项","UNSUPPORTED"));source.add(row(Table.ACTION_ITEM,id(30),null,null,null));source.add(row(Table.EVIDENCE_LINK,id(31),id(8),id(30),null,"UNSUPPORTED","anchor retained"));
  var copied=MinutesSavedGraphPlan.copy(null,id(3),source,null,"owner",Task11PublicationManifestTest.ledger()).preserveActions(source,id(2)).rows(1,8);var action=copied.stream().filter(r->r.table()==Table.ACTION_ITEM).findFirst().orElseThrow();assertEquals("owner",action.nullableString("owner_enterprise_id"));assertEquals("负责人",action.nullableString("owner_display"));assertNull(action.nullableString("due_on"));
 }
}
