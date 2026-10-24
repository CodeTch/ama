package ama.transcript;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** One immutable publication identity validator, used before any producer or receipt capability. */
final class PublicationManifest {
 private static final Set<String> KEYS=Set.of("schema","providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId","publicationId","confirmationId","requesterId","businessKey","libraryId","folderId","logicalRequestHash","executionRequestHash","payloadSha256","archiveIndexSha256","renderArtifact","meetingId","businessVersion","cancelEpoch","executionNo","submissionNo","priorProofs");
 static void validate(Map<String,Object> m){
  var keys=new HashSet<>(m.keySet());keys.remove("bindingTransition");require(keys.equals(KEYS)&&"ama.knowledge-publication-manifest.v1".equals(m.get("schema")),PROVENANCE_UNAVAILABLE);
  for(String k:List.of("publicationId","confirmationId","meetingId"))uuid(text(m,k,36));
  for(String k:List.of("providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId","requesterId","libraryId","folderId"))text(m,k,255);
  for(String k:List.of("logicalRequestHash","executionRequestHash","payloadSha256","archiveIndexSha256"))hash(text(m,k,64));
  require((m.get("publicationId")+":"+m.get("confirmationId")).equals(m.get("businessKey")),PROVENANCE_UNAVAILABLE);
  number(m.get("businessVersion"),0);number(m.get("cancelEpoch"),0);long execution=number(m.get("executionNo"),1),submission=number(m.get("submissionNo"),1);require(execution<=submission,PROVENANCE_UNAVAILABLE);
  require(m.get("renderArtifact") instanceof Map<?,?>,PROVENANCE_UNAVAILABLE);var render=(Map<?,?>)m.get("renderArtifact");require(render.keySet().equals(Set.of("version","sha256")),PROVENANCE_UNAVAILABLE);text(render,"version",255);hash(text(render,"sha256",64));
  require(m.get("priorProofs") instanceof List<?>,PROVENANCE_UNAVAILABLE);var proofs=(List<?>)m.get("priorProofs");require(proofs.size()<submission&&proofs.size()<=256,PROVENANCE_UNAVAILABLE);String previous="";
  for(var item:proofs){require(item instanceof Map<?,?>,PROVENANCE_UNAVAILABLE);var proof=(Map<?,?>)item;require(proof.keySet().equals(Set.of("executionId","executionRequestHash","receiptId","proofDigest")),PROVENANCE_UNAVAILABLE);String id=text(proof,"executionId",36);uuid(id);require(previous.compareTo(id)<0,PROVENANCE_UNAVAILABLE);previous=id;hash(text(proof,"executionRequestHash",64));hash(text(proof,"proofDigest",64));require(text(proof,"receiptId",40).matches("receipt-[0-9a-f]{32}"),PROVENANCE_UNAVAILABLE);}
  Object value=m.get("bindingTransition");if(proofs.isEmpty())require(value==null,PROVENANCE_UNAVAILABLE);else{
   require(value instanceof Map<?,?>,PROVENANCE_UNAVAILABLE);var transition=(Map<?,?>)value;require(transition.keySet().equals(Set.of("schema","expectedGeneration","priorProofs","newBusinessKey","newLogicalRequestHash"))&&"ama.controlled-kb-binding-transition.v1".equals(transition.get("schema")),PROVENANCE_UNAVAILABLE);
   // The pinned provider starts at generation 1 and increments once per accepted durable
   // receipt. Full authenticated predecessor membership is checked separately against history.
   require(number(transition.get("expectedGeneration"),1)==proofs.size()&&proofs.equals(transition.get("priorProofs"))&&m.get("businessKey").equals(transition.get("newBusinessKey"))&&m.get("logicalRequestHash").equals(transition.get("newLogicalRequestHash")),PROVENANCE_UNAVAILABLE);
  }
  require(ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(Map.of("schema","ama.knowledge-publish-execution.v1","logicalRequestHash",m.get("logicalRequestHash"),"executionNo",m.get("executionNo"),"priorProofs",proofs))).equals(m.get("executionRequestHash")),FINGERPRINT_MISMATCH);
 }
 static void logical(Map<String,Object> m,String source,String fingerprint){validate(m);var logical=new TreeMap<String,Object>();logical.put("schema","ama.knowledge-publish.v1");logical.put("domain","KNOWLEDGE_PUBLISH");for(String k:List.of("providerId","publicationId","confirmationId","businessKey","libraryId","folderId","renderArtifact","payloadSha256"))logical.put(k,m.get(k));logical.put("sourceManifestHash",source);logical.put("inputFingerprint",fingerprint);require(ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(logical)).equals(m.get("logicalRequestHash")),FINGERPRINT_MISMATCH);}
 private static String text(Map<?,?> m,String key,int max){Object x=m.get(key);require(x instanceof String,PROVENANCE_UNAVAILABLE);String s=(String)x;require(!s.isBlank()&&s.codePointCount(0,s.length())<=max,PROVENANCE_UNAVAILABLE);return s;}
 private static long number(Object n,long min){require(n instanceof Integer||n instanceof Long,PROVENANCE_UNAVAILABLE);long value=((Number)n).longValue();require(value>=min&&value<Long.MAX_VALUE-1,PROVENANCE_UNAVAILABLE);return value;}
}
