package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.MinutesContracts.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Pure selection guards. The caller must already hold the actual current meeting capability. */
final class MinutesPersonalTemplateSelection {
 private MinutesPersonalTemplateSelection(){}
 static void requireOwner(MinutesGraphDiscovery.Discovery discovery,String actor){
  if(!Set.of("INITIAL_SETTINGS","SETTINGS_TEMPLATE").contains(discovery.kind()))return;
  var selected=discovery.tables().getOrDefault("template_revision",List.of()).stream().filter(row->Objects.equals(discovery.selected(),row.get("id"))).toList();require(selected.size()==1,PROVENANCE_UNAVAILABLE);
  Object owner=selected.getFirst().get("owner_id");if(owner!=null&&!owner.equals(actor))throw new MeetingAccess.AccessDeniedException();
 }
 static void requireCurrent(MinutesGraphCapture.Graph graph,String revision,String actor,boolean lifecycleRequired){
  var selected=JdbcMinutesFactsReader.one(graph,Table.TEMPLATE_REVISION,revision);String owner=selected.nullableString("owner_id");if(owner!=null&&!owner.equals(actor))throw new MeetingAccess.AccessDeniedException();
  var parents=graph.auxiliary().getOrDefault("minutes_template",List.of()).stream().filter(row->selected.string("template_id").equals(row.get("id"))).toList();require(parents.size()==1,PROVENANCE_UNAVAILABLE);var parent=parents.getFirst();require(Objects.equals(owner,parent.get("owner_id")),PROVENANCE_UNAVAILABLE);
  var states=graph.auxiliary().getOrDefault("personal_template_state",List.of()).stream().filter(row->selected.string("template_id").equals(row.get("template_id"))).toList();
  if(states.isEmpty()){require(!lifecycleRequired,PROVENANCE_UNAVAILABLE);return;}
  require(states.size()==1&&owner!=null&&owner.equals(states.getFirst().get("owner_id")),PROVENANCE_UNAVAILABLE);var state=states.getFirst();
  if(state.get("deleted_at")!=null||!revision.equals(parent.get("current_revision_id")))throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");
  require(parent.get("revision") instanceof Number p&&state.get("command_revision") instanceof Number s&&p.longValue()==selected.number("revision")&&s.longValue()==p.longValue(),PROVENANCE_UNAVAILABLE);
 }
 static void requirePersonal(MinutesGraphCapture.Graph graph,String revision,String actor,boolean capable){
  var selected=JdbcMinutesFactsReader.one(graph,Table.TEMPLATE_REVISION,revision);String owner=selected.nullableString("owner_id");
  if(owner!=null&&!owner.equals(actor))throw new MeetingAccess.AccessDeniedException();
  require(owner!=null&&capable,CAPABILITY_UNAVAILABLE);requireCurrent(graph,revision,actor,true);
  try{ama.minutes.PersonalTemplateDocument.name(selected.string("name"));var layout=ama.minutes.PersonalTemplateDocument.parse(selected.string("themes"));require(selected.number("theme_count")==layout.themes().size(),PROVENANCE_UNAVAILABLE);}
  catch(IllegalArgumentException|IllegalStateException invalid){throw fail(PROFILE_UNAVAILABLE);}
 }
}
