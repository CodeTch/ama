package ama.transcript;

import ama.contract.MinutesContracts.Failure;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure guard fixtures, never a substitute for material provenance or a meeting capability. */
class PersonalTemplateMaterialSelectionTest {
 private final PersonalTemplateSelectionTest fixture=new PersonalTemplateSelectionTest();
 private MinutesGraphCapture.Graph graph(boolean pending,boolean published){
  var base=fixture.graph("owner",true,fixture.revision,false);
  var root=new HashMap<String,Object>();
  root.put("revision",4L);root.put("owner_id","owner");
  root.put("template_revision_id",fixture.revision);root.put("exclusion_revision_id",fixture.next);
  root.put("transcript_confirmation_id",fixture.next);root.put("material_snapshot_id",fixture.next);
  root.put("materials_pending",pending?1L:0L);root.put("ever_published",published?1L:0L);
  root.put("lifecycle","ACTIVE");root.put("governance_state","ACTIVE");root.put("stage",published?"PUBLISHED":"MINUTES_PENDING");
  var old=base.discovery();
  var discovery=new MinutesGraphDiscovery.Discovery(old.meeting(),old.kind(),old.selected(),root,old.tables(),old.memberships(),old.identities(),old.selectors());
  var auxiliary=new HashMap<>(base.auxiliary());
  auxiliary.put("meeting_minutes_editor_guard",List.of(Map.of("intent_epoch",1L)));
  return new MinutesGraphCapture.Graph(discovery,base.rows(),auxiliary);
 }
 private void writable(MinutesGraphCapture.Graph graph,boolean template,String source){
  try{
   var method=JdbcMinutesSettingsCommands.class.getDeclaredMethod("writable",MinutesGraphCapture.Graph.class,long.class,boolean.class,String.class);
   method.setAccessible(true);method.invoke(null,graph,4L,template,source);
  }catch(InvocationTargetException failed){throw (RuntimeException)failed.getCause();}
  catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
 }
 @Test void readyMaterialDoesNotPreventSelectingCurrentOwnedPersonalTemplate(){
  var graph=graph(false,false);assertDoesNotThrow(()->writable(graph,true,fixture.revision));
  assertEquals(fixture.next,graph.discovery().root().get("material_snapshot_id"));
 }
 @Test void pendingMaterialsAndPublishedMeetingsStillRejectSettingsChanges(){
  assertThrows(Failure.class,()->writable(graph(true,false),true,fixture.revision));
  assertThrows(Failure.class,()->writable(graph(false,true),true,fixture.revision));
 }
 @Test void readyMaterialRootCanReachTheSeparatelyVerifiedExclusionSourcePolicy(){
  // This root precondition grants no source capability. Typed current-source proofs are tested separately.
  assertDoesNotThrow(()->writable(graph(false,false),false,fixture.next));
 }
}
