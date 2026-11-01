package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.MinutesContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure guards on already captured metadata; these fixtures confer no read or write authority. */
class PersonalTemplateSelectionTest {
 final String meeting="00000000-0000-4000-8000-000000000001",template="00000000-0000-4000-8000-000000000002",revision="00000000-0000-4000-8000-000000000003",next="00000000-0000-4000-8000-000000000004";
 MinutesGraphCapture.Graph graph(String owner,boolean state,String current,boolean deleted){
  var row=new FullRow(Table.TEMPLATE_REVISION,List.of(cell(revision),cell(template),cell(1L),cell(owner),cell("个人模板"),cell(new ama.minutes.PersonalTemplateDocument(List.of("会议决定")).json()),cell(1L),cell("2026-10-31T00:00:00.000000Z")));
  var parents=List.<Map<String,Object>>of(Map.of("id",template,"owner_id",owner,"current_revision_id",current,"revision",1L));
  var auxiliary=new HashMap<String,List<Map<String,Object>>>();auxiliary.put("minutes_template",parents);
  if(state){var lifecycle=new HashMap<String,Object>();lifecycle.put("template_id",template);lifecycle.put("owner_id",owner);lifecycle.put("command_revision",deleted?2L:1L);lifecycle.put("deleted_at",deleted?"2026-10-31T01:00:00.000000Z":null);auxiliary.put("personal_template_state",List.of(lifecycle));}
  var metadata=Map.<String,Object>of("id",revision,"template_id",template,"owner_id",owner);var discovery=new MinutesGraphDiscovery.Discovery(meeting,"SETTINGS_TEMPLATE",revision,Map.of(),Map.of("template_revision",List.of(metadata)),Map.of(),Set.of(owner),List.of());
  return new MinutesGraphCapture.Graph(discovery,List.of(row),auxiliary);
 }
 @Test void currentLifecycleAndLegacyFixedTemplateRemainSeparateCapabilities(){
  assertDoesNotThrow(()->MinutesPersonalTemplateSelection.requireCurrent(graph("owner",true,revision,false),revision,"owner",true));
  assertDoesNotThrow(()->MinutesPersonalTemplateSelection.requireCurrent(graph("owner",false,revision,false),revision,"owner",false));
  assertThrows(Failure.class,()->MinutesPersonalTemplateSelection.requireCurrent(graph("owner",false,revision,false),revision,"owner",true));
 }
 @Test void deletedOrSupersededTemplatesCannotBeNewlySelected(){
  for(var graph:List.of(graph("owner",true,revision,true),graph("owner",true,next,false))){var failure=assertThrows(ama.contract.TranscriptContracts.Failure.class,()->MinutesPersonalTemplateSelection.requireCurrent(graph,revision,"owner",true));assertEquals(409,failure.status());}
 }
 @Test void foreignPrivateOwnerIsRejectedBeforeLifecycleOrThemeInspection(){
  var graph=graph("foreign",true,next,true);
  assertThrows(MeetingAccess.AccessDeniedException.class,()->MinutesPersonalTemplateSelection.requireOwner(graph.discovery(),"owner"));
  assertThrows(MeetingAccess.AccessDeniedException.class,()->MinutesPersonalTemplateSelection.requireCurrent(graph,revision,"owner",true));
 }
 @Test void firstAndLaterPersonalSelectionRequireTheSameExplicitCapabilityAndValidImmutableLayout(){
  var graph=graph("owner",true,revision,false);
  assertDoesNotThrow(()->MinutesPersonalTemplateSelection.requirePersonal(graph,revision,"owner",true));
  assertThrows(Failure.class,()->MinutesPersonalTemplateSelection.requirePersonal(graph,revision,"owner",false));
  assertThrows(Failure.class,()->MinutesPersonalTemplateSelection.requirePersonal(graph("owner",false,revision,false),revision,"owner",true));
 }
}
