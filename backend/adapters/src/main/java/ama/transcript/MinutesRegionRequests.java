package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Inert selection metadata. Caller must still prove the complete generation input graph. */
final class MinutesRegionRequests {
 static GenerationDraft draft(FullRow input){return new GenerationDraft(input.string("id"),input.string("meeting_id"),input.string("transcript_confirmation_id"),input.nullableString("material_snapshot_id"),input.string("template_revision_id"),input.string("exclusion_revision_id"),input.nullableString("element_set_id"),input.string("prompt_version"),input.string("model_config_version"),Scope.valueOf(input.string("scope")),input.nullableString("target_element_id"));}
 static ValidationContext context(List<FullRow> rows,ama.contract.TaskContracts.TaskSpec spec,Catalog catalog){var inputs=rows.stream().filter(r->r.table()==Table.GENERATION_INPUT&&spec.input().id().equals(r.string("id"))).toList();require(inputs.size()==1,PROVENANCE_UNAVAILABLE);var draft=draft(inputs.getFirst());var region=read(rows,draft);return new ValidationContext(spec.inputFingerprint(),spec.inputFingerprint(),draft.scope(),catalog,region==null?null:region.category());}
 static RegionRequest read(List<FullRow> rows,GenerationDraft draft){
  var selected=rows.stream().filter(r->r.table()==Table.GENERATION_REGION_REQUEST&&draft.inputId().equals(r.string("id"))).toList();
  if(draft.scope()==Scope.FULL){require(selected.isEmpty(),INPUT_INVALID);return null;}
  require(draft.scope()==Scope.REGION&&selected.size()==1,PROVENANCE_UNAVAILABLE);var row=selected.getFirst();require(draft.meetingId().equals(row.string("meeting_id")),SOURCE_MEMBERSHIP_MISMATCH);
  var value=new RegionRequest(row.string("base_element_set_id"),Category.valueOf(row.string("category")),row.string("instructions"));require(value.baseElementSetId().equals(draft.elementSetId()),INPUT_INVALID);return value;
 }
 static FullRow row(String input,String meeting,RegionRequest value){return new FullRow(Table.GENERATION_REGION_REQUEST,List.of(cell(input),cell(meeting),cell(value.baseElementSetId()),cell(value.category().name()),cell(value.instructions())));}
 static void current(RegionRequest value,Map<String,Object> root){if(value!=null&&(!value.baseElementSetId().equals(root.get("element_set_id"))||root.get("minutes_revision_id")==null))throw new ama.contract.TranscriptContracts.Failure(409,"REGION_BASE_CONFLICT");}
 private MinutesRegionRequests(){}
}
