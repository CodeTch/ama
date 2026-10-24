package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Original permanent-rejection fact; acceptance is tested before any next publication POST. */
class Task11ReplacementIT extends Task11PublicationResumeIT {
 @Override String previousWindow(){return "publication-zero-effect-01";}
 @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  String publication="368767a5-c477-40fc-8e27-13705667c5a0",execution="0a360254-6710-442e-8c54-a07a913e2362";var before=j.queryForMap("SELECT p.state,p.revision,m.revision meeting_revision,m.final_confirmation_id,m.cancel_epoch FROM publication p JOIN meeting m ON m.id=p.meeting_id WHERE p.id=?",publication);assertEquals("FAILED",before.get("state"));assertEquals("DEFINITIVE_REJECTION",j.queryForObject("SELECT no_effect_basis FROM external_execution WHERE id=?",String.class,execution));
  token=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR FROM enterprise_identity WHERE enterprise_id=?",hash(token),csrf,owner);
  String path="/meetings/"+meeting+"/publications/"+publication+"/resubmissions";String request=json.writeValueAsString(Map.of("expectedRevisionToken",before.get("meeting_revision").toString(),"expectedPublicationRevisionToken",before.get("revision").toString(),"confirmationId",before.get("final_confirmation_id"),"providerId","CONTROLLED_KNOWLEDGE_V1","libraryId","controlled-library","folderId","controlled-root"));
  Files.writeString(receipt.resolve("replacement-original-before.json"),json.writeValueAsString(Map.of("publication",publication,"execution",execution,"before",before,"request",request,"nextPublicationPostAuthorizedByTest",false)));
  var response=call("POST",path,request,"task11-original-rejection-replacement");Files.writeString(receipt.resolve("replacement-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));assertEquals(202,response.statusCode(),response.body());
  var accepted=body(response);assertEquals(publication,accepted.get("publicationId").asText());assertNotEquals("302565ef-16e7-49df-b5c2-76caa2a13e33",accepted.get("taskId").asText());assertEquals("2",accepted.get("submissionNoToken").asText());
 }
}
