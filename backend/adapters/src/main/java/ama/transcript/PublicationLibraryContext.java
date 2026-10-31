package ama.transcript;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Metadata-only location from the current viewer's provider READ, bound to the verified frozen publication. */
final class PublicationLibraryContext {
 static Map<String,Object> validate(Map<String,Object> raw,Map<String,Object> manifest,String viewer,String document,String result,TranscriptAcquisitionBudget.Ledger budget){
  budget.check();require(raw.keySet().equals(Set.of("providerId","storeInstanceId","viewerId","documentId","resultId","publicationId","payloadSha256","libraryId","libraryName","libraryDescription","folderId","folderPath","readGrantVersionToken","locationVersion")),PROVENANCE_UNAVAILABLE);
  for(String key:List.of("providerId","storeInstanceId","publicationId","payloadSha256","libraryId","folderId"))require(Objects.equals(manifest.get(key),raw.get(key)),PROVENANCE_UNAVAILABLE);
  require(viewer.equals(raw.get("viewerId"))&&document.equals(raw.get("documentId"))&&result.equals(raw.get("resultId")),PROVENANCE_UNAVAILABLE);
  String name=text(raw.get("libraryName"),1024,false),description=text(raw.get("libraryDescription"),4096,true);require(raw.get("folderPath") instanceof List<?>,PROVENANCE_UNAVAILABLE);var path=(List<?>)raw.get("folderPath");require(!path.isEmpty()&&path.size()<=32,BUDGET_EXCEEDED);for(Object node:path){budget.check();text(node,1024,false);}
  require(raw.get("readGrantVersionToken") instanceof String,PROVENANCE_UNAVAILABLE);String grant=(String)raw.get("readGrantVersionToken");require(grant.matches("[1-9][0-9]{0,18}")&&(grant.length()<19||grant.compareTo("9223372036854775807")<=0),PROVENANCE_UNAVAILABLE);
  require(raw.get("locationVersion") instanceof String,PROVENANCE_UNAVAILABLE);String version=(String)raw.get("locationVersion");require(version.matches("[0-9a-f]{64}"),PROVENANCE_UNAVAILABLE);var unsigned=new TreeMap<>(raw);unsigned.remove("locationVersion");require(ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(unsigned,budget)).equals(version),FINGERPRINT_MISMATCH);
  var view=new TreeMap<String,Object>();for(String key:List.of("providerId","storeInstanceId","libraryId","folderId","documentId","payloadSha256"))view.put(key,raw.get(key));view.put("libraryName",name);view.put("libraryDescription",description);view.put("folderPath",List.copyOf(path));view.put("versionToken",version);budget.check();return Collections.unmodifiableMap(view);
 }
 private static String text(Object value,int max,boolean empty){require(value instanceof String,PROVENANCE_UNAVAILABLE);String text=(String)value;scalars(text);require(text.length()<=max&&(empty||!text.isBlank())&&text.codePoints().noneMatch(Character::isISOControl),BUDGET_EXCEEDED);return text;}
}
