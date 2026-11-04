package ama.transcript;

import ama.contract.IdentityContracts.Actor;
import ama.task.PreparationCancellation;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Library-authorized formal records. No meeting READ, source bytes, or draft projection is acquired. */
final class PublicationLibraryRecords {
 static Map<String,Object> read(JdbcPublicationService service,Actor actor,String view,String cursor,PreparationCancellation cancellation)throws java.io.IOException{
  if(!Set.of("read","manage").contains(view)||cursor!=null&&!cursor.matches("doc-[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid library records page");
  var budget=service.limits.ledger(service.authority.readBudget(),service.authority.canonicalBudget(),cancellation::cancelled);service.activeKnowledgeActor(actor);
  String path="/v1/knowledge/documents/index?view="+view+"&limit=20"+(cursor==null?"":"&cursor="+cursor);
  var raw=service.client.exchange(service.client.authorized(service.knowledgeReader(actor,budget),"DOCUMENT_READ","GET",path,null),262144,cancellation);
  require(raw.keySet().equals(Set.of("providerId","storeInstanceId","items","cursor","hasManagedLibraries"))&&raw.get("items") instanceof List<?> && raw.get("hasManagedLibraries") instanceof Boolean,PROVENANCE_UNAVAILABLE);
  var remote=(List<?>)raw.get("items");require(remote.size()<=20,PROVENANCE_UNAVAILABLE);Object next=raw.get("cursor");require(next==null||next instanceof String s&&s.matches("doc-[0-9a-f]{32}")&&!s.equals(cursor),PROVENANCE_UNAVAILABLE);
  var items=new ArrayList<Map<String,Object>>();String previous=cursor==null?"":cursor;
  for(Object value:remote){
   budget.check();var row=validate(value);String doc=(String)row.get("documentId");require(doc.compareTo(previous)>0,PROVENANCE_UNAVAILABLE);previous=doc;
   var local=service.j.queryForList("SELECT p.meeting_id,p.confirmation_id,p.published_at,m.revision,m.governance_state,m.owner_id FROM publication p JOIN external_document_mapping d ON d.publication_id=p.id JOIN meeting m ON m.id=p.meeting_id WHERE p.id=? AND p.library_id=? AND p.folder_id=? AND p.state='SUCCEEDED' AND d.external_document_id=? AND d.provider_id=? AND m.lifecycle='ACTIVE' LIMIT 2",row.get("publicationId"),row.get("libraryId"),row.get("folderId"),doc,service.client.identities().get("providerId"));
   if(local.size()!=1)continue;var saved=local.getFirst();String state=(String)saved.get("governance_state");if("ACTIVE".equals(state))state=(String)row.get("state");if(view.equals("read")&&(!state.equals("ACTIVE")||!Boolean.TRUE.equals(row.get("canRead"))))continue;
   require(!view.equals("manage")||Boolean.TRUE.equals(row.get("canManage")),PROVENANCE_UNAVAILABLE);
   String meeting=(String)saved.get("meeting_id"),publication=(String)row.get("publicationId");uuid(meeting);uuid(publication);
   var item=new LinkedHashMap<String,Object>(row);item.put("meetingId",meeting);item.put("providerId",raw.get("providerId"));item.put("storeInstanceId",raw.get("storeInstanceId"));item.put("versionToken",row.get("payloadSha256"));item.put("publishedVersion","1");item.put("publishedBy",saved.get("owner_id"));item.put("publishedAt",JdbcPublicationQueries.normalizeMetadata(saved.get("published_at")));item.put("confirmationId",saved.get("confirmation_id"));item.put("meetingRevisionToken",saved.get("revision").toString());item.put("state",state);item.put("canRead",state.equals("ACTIVE")&&Boolean.TRUE.equals(row.get("canRead")));item.put("documentUrl","/api/v1/meetings/"+meeting+"/publications/"+publication+"/document");items.add(Collections.unmodifiableMap(item));
  }
  service.activeKnowledgeActor(actor);budget.check();var result=new LinkedHashMap<String,Object>();result.put("items",List.copyOf(items));result.put("nextCursor",next);result.put("hasMore",next!=null);result.put("hasManagedLibraries",raw.get("hasManagedLibraries"));return Collections.unmodifiableMap(result);
 }
 @SuppressWarnings("unchecked") static Map<String,Object> validate(Object value){
  require(value instanceof Map<?,?>,PROVENANCE_UNAVAILABLE);var row=(Map<String,Object>)value;require(row.keySet().equals(Set.of("documentId","publicationId","libraryId","folderId","libraryName","libraryDescription","folderPath","title","meetingName","payloadSha256","state","canManage","canRead")),PROVENANCE_UNAVAILABLE);
  require(row.get("documentId") instanceof String d&&d.matches("doc-[0-9a-f]{32}")&&row.get("payloadSha256") instanceof String h&&h.matches("[0-9a-f]{64}")&&row.get("canRead") instanceof Boolean&&row.get("canManage") instanceof Boolean&&Set.of("ACTIVE","WITHDRAWN","OBSOLETE").contains(row.get("state")),PROVENANCE_UNAVAILABLE);uuid((String)row.get("publicationId"));PublicationDestinationPage.id(row.get("libraryId"));PublicationDestinationPage.id(row.get("folderId"));
  for(String key:List.of("title","meetingName","libraryName"))PublicationDestinationPage.text(row.get(key),1024);PublicationDestinationPage.optionalText(row.get("libraryDescription"),4096);
  require(row.get("folderPath") instanceof List<?> paths&&!paths.isEmpty()&&paths.size()<=32,PROVENANCE_UNAVAILABLE);((List<?>)row.get("folderPath")).forEach(p->PublicationDestinationPage.text(p,1024));require("ACTIVE".equals(row.get("state"))||Boolean.FALSE.equals(row.get("canRead")),PROVENANCE_UNAVAILABLE);return row;
 }
}
