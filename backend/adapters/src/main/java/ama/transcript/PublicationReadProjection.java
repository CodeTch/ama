package ama.transcript;
import java.util.*;
/** Data-only public status. This projection grants no mutation or provider authority. */
final class PublicationReadProjection {
 static Map<String,Object> status(Map<String,Object> root,Map<String,Object> publication,Map<String,List<Map<String,Object>>> rows,boolean owner){
  boolean success=rows.get("external_execution").stream().anyMatch(e->"SUCCEEDED".equals(e.get("state")))||rows.get("external_operation").stream().anyMatch(e->"SUCCEEDED".equals(e.get("state")));
  boolean manual=rows.get("external_operation").stream().anyMatch(e->"MANUAL_REQUIRED".equals(e.get("state")));
  var mappings=rows.get("external_document_mapping");boolean mapped=mappings.size()==1&&Set.of("ACTIVE","WITHDRAWN","OBSOLETE").contains(mappings.getFirst().get("authority_state"))&&"SUCCEEDED".equals(publication.get("state"));boolean activeDocument=PublicationDocumentRead.available(root,publication,rows);
  boolean published=((Number)root.get("ever_published")).longValue()!=0;
  boolean pending=rows.get("external_execution").stream().anyMatch(e->Set.of("MARKED","UNKNOWN").contains(e.get("state")))||rows.get("external_operation").stream().anyMatch(e->Set.of("MARKED","UNKNOWN").contains(e.get("state")));
  boolean locked=manual||success&&!mapped||!mappings.isEmpty()&&!mapped||published&&!mapped;
  String remote=success||mapped||published?"SUCCESS":pending?"UNKNOWN":rows.get("external_execution").stream().anyMatch(e->"FAILED".equals(e.get("state")))?"FAILED":"NOT_OBSERVED";
  String applied=mapped&&!manual?"APPLIED":locked?"FENCED_MANUAL":pending?"PENDING_LOOKUP":"PENDING";
  var actions=new ArrayList<String>();actions.add("VIEW_FROZEN");actions.add("REFRESH_STATUS");if(owner)actions.add("VIEW_ATTEMPTS");
  var result=new LinkedHashMap<String,Object>();result.put("publicationId",publication.get("id"));result.put("confirmationId",publication.get("confirmation_id"));result.put("rawState",publication.get("state"));result.put("remoteOutcome",remote);result.put("applicationOutcome",applied);result.put("manualLocked",locked);result.put("lookupPending",pending&&!locked&&!mapped);result.put("officialDocumentUrl",activeDocument&&!manual?"/api/v1/meetings/"+root.get("id")+"/publications/"+publication.get("id")+"/document":null);result.put("documentViewAvailability",activeDocument&&!manual?"READY":"UNAVAILABLE");result.put("allowedActions",List.copyOf(actions));result.put("readOnlyReason",locked?"PUBLICATION_MANUAL":success||published?"PUBLISHED":"PUBLICATION_FROZEN");result.put("eligibilityAvailability","RECHECK_REQUIRED");
  if(owner){result.put("publicationRevisionToken",publication.get("revision").toString());result.put("currentFinalId",root.get("final_confirmation_id"));result.put("meetingRevisionToken",root.get("revision").toString());var latest=rows.get("publication_submission").stream().max(Comparator.comparingLong(row->((Number)row.get("submission_no")).longValue()));var current=latest.isEmpty()?List.<Map<String,Object>>of():rows.get("external_execution").stream().filter(e->latest.orElseThrow().get("task_id").equals(e.get("task_id"))).toList();result.put("currentTaskId",latest.isEmpty()?null:latest.orElseThrow().get("task_id"));result.put("executionId",current.isEmpty()?null:current.getFirst().get("id"));result.put("executionNoToken",current.isEmpty()?null:current.getFirst().get("execution_no").toString());result.put("operationId",current.isEmpty()?null:current.getFirst().get("operation_id"));}
  return Collections.unmodifiableMap(result);
 }
}
