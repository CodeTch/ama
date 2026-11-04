package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TranscriptContracts.Failure;
import ama.task.PreparationCancellation;
import java.util.*;
import java.io.IOException;
import static ama.contract.MinutesContracts.uuid;

/** A meeting-scoped optimistic draft. Accepted submissions carry their own immutable copy. */
final class JdbcPublicationConfigurations {
 private final JdbcPublicationService service;
 JdbcPublicationConfigurations(JdbcPublicationService service){this.service=service;}
 private Map<String,Object> row(String meeting){
  var rows=service.j.queryForList("SELECT version,IF(OCTET_LENGTH(configuration_json)<=65536,configuration_json,NULL) configuration_json FROM publication_configuration WHERE meeting_id=?",meeting);
  if(rows.isEmpty())return null;
  try{if(rows.size()!=1||!(rows.getFirst().get("configuration_json") instanceof String text))throw new IllegalArgumentException("Unavailable stored configuration");var value=PublicationConfigurationData.validate(ControlledKnowledgeClient.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),65536));if(!rows.getFirst().get("version").toString().equals(value.get("configurationVersionToken")))throw new IllegalArgumentException("Configuration version mismatch");return value;}
  catch(IllegalArgumentException malformed){throw new Failure(503,"PUBLICATION_CONFIGURATION_UNAVAILABLE");}
 }
 private void owner(Actor actor,String meeting){service.authority.savedAccess().require(service.authority.savedAccess().lock(actor,meeting),MeetingAccess.Action.EDIT);}
 Map<String,Object> read(Actor actor,String meeting,PreparationCancellation cancellation)throws IOException{
  uuid(meeting);var budget=service.limits.ledger(service.authority.readBudget(),service.authority.canonicalBudget(),cancellation::cancelled);
  var call=service.client.authorized(service.authorize(actor,meeting,"DESTINATION_READ",budget),"DESTINATION_READ","GET","/v1/knowledge/libraries/publication-rules",null);
  var raw=service.client.exchange(call,65536,cancellation);PublicationDestinationPage.exact(raw,Set.of("providerId","storeInstanceId","tagRules"));var rules=PublicationConfigurationData.rules(raw.get("tagRules"));
  return service.authority.transactionRunner().required(()->{owner(actor,meeting);return response(meeting,row(meeting),rules);});
 }
 Map<String,Object> save(Actor actor,String meeting,Map<String,Object> request,PreparationCancellation cancellation)throws IOException{
  uuid(meeting);PublicationDestinationPage.exact(request,Set.of("expectedConfigurationVersionToken","providerId","libraryId","folderId","tags"));
  String version=token(request.get("expectedConfigurationVersionToken")),provider=PublicationDestinationPage.text(request.get("providerId"),64),library=PublicationDestinationPage.id(request.get("libraryId")),folder=PublicationDestinationPage.id(request.get("folderId"));
  if(!provider.equals(service.client.identities().get("providerId")))throw new IllegalArgumentException("Unknown provider");
  var budget=service.limits.ledger(service.authority.readBudget(),service.authority.canonicalBudget(),cancellation::cancelled);
  var call=service.client.authorized(service.authorize(actor,meeting,"DESTINATION_READ",budget),"DESTINATION_READ","GET","/v1/knowledge/libraries/"+PublicationDestinationPage.encode(library)+"/configuration?folderId="+PublicationDestinationPage.encode(folder),null);
  final Map<String,Object> location;
  try{location=PublicationConfigurationData.location(service.client.exchange(call,65536,cancellation),service.client.identities(),library,folder);}
  catch(ControlledKnowledgeClient.ProviderFailure denied){if(Set.of(403,404,409).contains(denied.status))throw new Failure(409,"PUBLICATION_DESTINATION_UNAVAILABLE");throw denied;}
  var metadata=PublicationConfigurationData.metadata(location,Long.toString(Long.parseLong(version)+1),request.get("tags"));
  String wire=new String(ControlledKnowledgeClient.canonical(metadata),java.nio.charset.StandardCharsets.UTF_8);if(wire.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Configuration too large");
  return service.authority.transactionRunner().required(()->{
   owner(actor,meeting);mutable(meeting);var old=row(meeting);String actual=old==null?"0":(String)old.get("configurationVersionToken");if(!version.equals(actual))throw new Failure(409,"PUBLICATION_CONFIGURATION_CONFLICT");
   if(old==null)service.j.update("INSERT INTO publication_configuration(meeting_id,version,configuration_json,updated_by,updated_at) VALUES (?,1,?,?,UTC_TIMESTAMP(6))",meeting,wire,actor.enterpriseId());
   else service.j.update("UPDATE publication_configuration SET version=version+1,configuration_json=?,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE meeting_id=? AND version=?",wire,actor.enterpriseId(),meeting,Long.parseLong(version));
   return response(meeting,metadata,PublicationConfigurationData.rules(location.get("tagRules")));
  });
 }
 private void mutable(String meeting){
  var root=service.j.queryForMap("SELECT ever_published,lifecycle,governance_state FROM meeting WHERE id=?",meeting);
  if(((Number)root.get("ever_published")).intValue()!=0||!"ACTIVE".equals(root.get("lifecycle"))||!"ACTIVE".equals(root.get("governance_state"))||service.j.queryForObject("SELECT COUNT(*) FROM publication WHERE meeting_id=? AND state<>'FAILED'",Long.class,meeting)!=0)throw new Failure(409,"PUBLICATION_CONFIGURATION_FROZEN");
 }
 /** Caller already holds the meeting root lock; configuration saves take the same lock. */
 Map<String,Object> capture(String meeting,String expected,String provider,String library,String folder){
  var saved=row(meeting);if(expected==null){if(saved!=null)throw new Failure(409,"PUBLICATION_CONFIGURATION_REQUIRED");return null;}
  token(expected);if(saved==null||!expected.equals(saved.get("configurationVersionToken")))throw new Failure(409,"PUBLICATION_CONFIGURATION_CONFLICT");
  if(!provider.equals(saved.get("providerId"))||!library.equals(saved.get("libraryId"))||!folder.equals(saved.get("folderId")))throw new Failure(409,"PUBLICATION_CONFIGURATION_CONFLICT");return PublicationConfigurationData.validate(saved);
 }
 private static Map<String,Object> response(String meeting,Map<String,Object> metadata,Map<String,Object> rules){var response=new LinkedHashMap<String,Object>();response.put("meetingId",meeting);response.put("configurationVersionToken",metadata==null?"0":metadata.get("configurationVersionToken"));response.put("configuration",metadata);response.put("tagRules",rules);return Collections.unmodifiableMap(response);}
 static String token(Object value){if(!(value instanceof String s)||!s.matches("0|[1-9][0-9]{0,18}"))throw new IllegalArgumentException("Configuration version required");try{if(Long.parseLong(s)>=Long.MAX_VALUE-1)throw new IllegalArgumentException("Configuration version exhausted");}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid configuration version");}return s;}
}
