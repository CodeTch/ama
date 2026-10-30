package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.task.PreparationCancellation;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Exact read grants over the existing pinned authenticated transport; no publication grant is reused. */
final class MaterialKnowledge {
 private final JdbcTranscriptAuthority authority;private final ControlledKnowledgeClient client;
 MaterialKnowledge(JdbcTranscriptAuthority authority,ControlledKnowledgeClient client){this.authority=authority;this.client=client;}
 static final class Authorization {
  private final MaterialKnowledge issuer;private final String actor;private final long expires;final TranscriptAcquisitionBudget.Ledger budget;
  private Authorization(MaterialKnowledge issuer,Actor actor,TranscriptAcquisitionBudget.Ledger budget){this.issuer=issuer;this.actor=actor.enterpriseId();this.budget=budget;expires=Instant.now().getEpochSecond()+30;}
  void require(ControlledKnowledgeClient client){if(issuer.client!=client||expires<Instant.now().getEpochSecond())throw new IllegalArgumentException("Foreign or expired material read authorization");budget.check();}
  String actor(){return actor;}
 }
 record Document(String provider,String version,String name,byte[] bytes,String text){Document{bytes=bytes.clone();}@Override public byte[] bytes(){return bytes.clone();}}
 private Map<String,Object> read(Actor actor,String meeting,String path,int limit)throws IOException {
  ama.contract.MinutesContracts.uuid(meeting);var budget=authority.publicationBudget();
  var initial=authority.transactionRunner().required(()->{authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),MeetingAccess.Action.EDIT);return root(meeting);});
  var authorization=new Authorization(this,actor,budget);Map<String,Object> result;
  try{result=client.exchange(client.materialRead(authorization,path),limit,JdbcPublicationService.NEVER);}
  catch(ControlledKnowledgeClient.ProviderFailure denied){if(denied.status==401||denied.status==403)throw JdbcMaterials.failure(403,"MATERIAL_KNOWLEDGE_FORBIDDEN");if(denied.status==404)throw JdbcMaterials.failure(404,"MATERIAL_KNOWLEDGE_NOT_FOUND");if(denied.status==409)throw JdbcMaterials.failure(409,"MATERIAL_KNOWLEDGE_VERSION_CONFLICT");throw denied;}
  authority.transactionRunner().required(()->{authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),MeetingAccess.Action.EDIT);if(!initial.equals(root(meeting)))throw JdbcMaterials.failure(409,"REVISION_CONFLICT");return null;});budget.check();return result;
 }
 private Map<String,Object> root(String meeting){var j=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);return j.queryForMap("SELECT owner_id,revision,cancel_epoch,access_revision,lifecycle,governance_state FROM meeting WHERE id=?",meeting);}
 Map<String,Object> page(Actor actor,String meeting,String library,String search,String cursor,int limit)throws IOException {
  ama.contract.MinutesContracts.uuid(meeting);if(library!=null)id(library);if(search==null)search="";ama.contract.FileContracts.text(search);if(search.length()>128||search.codePoints().anyMatch(Character::isISOControl)||limit<1||limit>50)throw new IllegalArgumentException("Bounded material query required");
  String after="";if(cursor!=null){if(cursor.length()>4096)throw new IllegalArgumentException("Material cursor budget");var binding=ControlledKnowledgeClient.parse(ControlledKnowledgeClient.unbase64(cursor),3072);if(!binding.keySet().equals(Set.of("actorId","meetingId","libraryId","search","after","providerId","storeInstanceId"))||!actor.enterpriseId().equals(binding.get("actorId"))||!meeting.equals(binding.get("meetingId"))||!Objects.equals(library,binding.get("libraryId"))||!search.equals(binding.get("search"))||!client.identities().get("providerId").equals(binding.get("providerId"))||!client.identities().get("storeInstanceId").equals(binding.get("storeInstanceId")))throw new IllegalArgumentException("Foreign material cursor");after=id(binding.get("after"));}
  String path="/v1/knowledge/material-libraries"+(library==null?"":"/"+encode(library)+"/documents")+"?limit="+limit;if(!search.isEmpty())path+="&search="+encode(search);if(!after.isEmpty())path+="&cursor="+encode(after);
  var response=read(actor,meeting,path,262144);if(!response.keySet().equals(Set.of("providerId","storeInstanceId","items","cursor"))||!(response.get("items") instanceof List<?> items)||items.size()>limit)throw new IOException("INVALID_MATERIAL_PAGE");
  var values=new ArrayList<Map<String,Object>>();String previous=after;
  for(var value:items){if(!(value instanceof Map<?,?> item))throw new IOException("INVALID_MATERIAL_PAGE");String itemId=id(item.get("id"));if(itemId.compareTo(previous)<=0)throw new IOException("INVALID_MATERIAL_PAGE");previous=itemId;String name=ControlledKnowledgeClient.string(cast(item),"name");if(name.length()>1024)throw new IOException("INVALID_MATERIAL_PAGE");if(library==null){if(!item.keySet().equals(Set.of("id","name")))throw new IOException("INVALID_MATERIAL_PAGE");}else{if(!item.keySet().equals(Set.of("id","libraryId","name","version","sha256","sizeBytesToken"))||!library.equals(item.get("libraryId"))||!Objects.equals(item.get("version"),item.get("sha256"))||!(item.get("version") instanceof String version)||!version.matches("[0-9a-f]{64}")||!(item.get("sizeBytesToken") instanceof String size)||!size.matches("[1-9][0-9]{0,7}"))throw new IOException("INVALID_MATERIAL_PAGE");}values.add(cast(item));}
  Object next=response.get("cursor");String nextCursor=null;if(next!=null){if(values.size()!=limit||!previous.equals(next))throw new IOException("INVALID_MATERIAL_PAGE");var binding=new TreeMap<String,Object>();binding.put("actorId",actor.enterpriseId());binding.put("meetingId",meeting);binding.put("libraryId",library);binding.put("search",search);binding.put("after",next);binding.put("providerId",client.identities().get("providerId"));binding.put("storeInstanceId",client.identities().get("storeInstanceId"));nextCursor=ControlledKnowledgeClient.base64(ControlledKnowledgeClient.canonical(binding));}var page=new LinkedHashMap<String,Object>();page.put("items",List.copyOf(values));page.put("cursor",nextCursor);return page;
 }
 Document document(Actor actor,String meeting,String library,String document,String version)throws IOException {
  id(library);id(document);if(version==null||!version.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact knowledge version required");var result=read(actor,meeting,"/v1/knowledge/material-documents/"+encode(document)+"?libraryId="+encode(library)+"&version="+version,8454144);
  if(!result.keySet().equals(Set.of("providerId","storeInstanceId","libraryId","documentId","version","sha256","payload"))||!library.equals(result.get("libraryId"))||!document.equals(result.get("documentId"))||!version.equals(result.get("version"))||!version.equals(result.get("sha256"))||!(result.get("payload") instanceof Map<?,?> raw))throw new IOException("INVALID_MATERIAL_DOCUMENT");var payload=cast(raw);byte[] bytes=ControlledKnowledgeClient.canonical(payload);if(bytes.length>8388608||!ControlledKnowledgeClient.digest(bytes).equals(version))throw new IOException("MATERIAL_DOCUMENT_HASH_MISMATCH");String title=ControlledKnowledgeClient.string(payload,"title");if(title.length()>1024)throw new IOException("MATERIAL_DOCUMENT_NAME_BUDGET");return new Document(client.identities().get("providerId"),version,title,bytes,text(payload));
 }
 static String text(Map<String,Object> payload){if(!"ama.controlled-knowledge-document.v1".equals(payload.get("schemaVersion"))||!(payload.get("minutes") instanceof Map<?,?> minutes)||!(minutes.get("body") instanceof String body)||!(payload.get("sections") instanceof List<?> sections))throw new IllegalArgumentException("Unsupported knowledge document schema");var text=new StringBuilder(ControlledKnowledgeClient.string(payload,"title")).append('\n').append(body);for(var value:sections){if(!(value instanceof Map<?,?> section)||!(section.get("items") instanceof List<?> items))throw new IllegalArgumentException("Invalid knowledge sections");for(var item:items){if(!(item instanceof Map<?,?> row)||!(row.get("body") instanceof String paragraph))throw new IllegalArgumentException("Invalid knowledge paragraph");if(text.length()+paragraph.length()+1>2*1024*1024)throw new IllegalArgumentException("Knowledge text budget");text.append('\n').append(paragraph);}}return text.toString();}
 @SuppressWarnings("unchecked") private static Map<String,Object> cast(Map<?,?> raw){return (Map<String,Object>)raw;}
 static String id(Object value){if(!(value instanceof String s)||!s.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("Bounded controlled material identity required");return s;}
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
}
