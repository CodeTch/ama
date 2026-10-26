package ama.transcript;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic immutable history checks only; neither these rows nor this verifier issue authority. */
final class Task11ClosedHistoryFixture {
 static final String MEETING="12345678-1234-4234-8234-123456789ac1",PUB="12345678-1234-4234-8234-123456789abc",FINAL="12345678-1234-4234-8234-123456789abd",TASK="12345678-1234-4234-8234-123456789abe",OP="12345678-1234-4234-8234-123456789abf",EXEC="12345678-1234-4234-8234-123456789ac0",STORE="12345678-1234-4234-8234-123456789ac2";
 static Map<String,Object> row(Object... pairs){var out=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
 static final class Fixture {
  final Map<String,Object> root=row("id",MEETING,"revision",15L,"ever_published",0L,"stage","PUBLISH_FAILED","lifecycle","ACTIVE","governance_state","ACTIVE");
  final List<Map<String,Object>> pubs=new ArrayList<>(),finals=new ArrayList<>();
  final Map<String,List<Map<String,Object>>> rows=new LinkedHashMap<>();
  final Map<String,String> originals=new TreeMap<>();
  final Map<String,PublicationWriteHistory.ExecutionOriginal> executions=new TreeMap<>();
  final Map<String,Object> manifest=new ManifestSeed().manifest();
  Fixture(){for(String name:List.of("publication_submission","external_document_mapping","publication_attempt","durable_task","task_attempt","external_operation","external_execution"))rows.put(name,new ArrayList<>());}
  Fixture local(){
   manifest.put("publicationId",PUB);manifest.put("confirmationId",FINAL);manifest.put("meetingId",MEETING);manifest.put("businessKey",PUB+":"+FINAL);manifest.put("storeInstanceId",STORE);manifest.put("requesterId","owner");
   var logical=new TreeMap<String,Object>();logical.put("schema","ama.knowledge-publish.v1");logical.put("domain","KNOWLEDGE_PUBLISH");for(String key:List.of("providerId","publicationId","confirmationId","businessKey","libraryId","folderId","renderArtifact","payloadSha256"))logical.put(key,manifest.get(key));logical.put("sourceManifestHash","e".repeat(64));logical.put("inputFingerprint","f".repeat(64));manifest.put("logicalRequestHash",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(logical)));manifest.put("executionRequestHash",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(Map.of("schema","ama.knowledge-publish-execution.v1","logicalRequestHash",manifest.get("logicalRequestHash"),"executionNo",1L,"priorProofs",List.of()))));
   String original=new String(ControlledKnowledgeClient.canonical(manifest),StandardCharsets.UTF_8);originals.put(TASK,original);var spec=JdbcPublicationService.spec(manifest,new ManifestSeed().budget());
   pubs.add(row("id",PUB,"meeting_id",MEETING,"confirmation_id",FINAL,"business_key",PUB+":"+FINAL,"provider_id",manifest.get("providerId"),"library_id",manifest.get("libraryId"),"folder_id",manifest.get("folderId"),"state","FAILED","revision",2L));
   finals.add(row("id",FINAL,"meeting_id",MEETING,"meeting_revision",13L,"source_manifest_hash","e".repeat(64),"input_fingerprint","f".repeat(64)));
   rows.get("publication_submission").add(row("task_id",TASK,"publication_id",PUB,"submission_no",1L,"confirmation_id",FINAL,"provider_id",manifest.get("providerId"),"business_key",PUB+":"+FINAL,"library_id",manifest.get("libraryId"),"folder_id",manifest.get("folderId"),"requester_id","owner","logical_request_hash",manifest.get("logicalRequestHash"),"execution_request_hash",manifest.get("executionRequestHash"),"local_state","NEVER_MARKED","local_decided_at","done","local_decided_by","owner","local_reason","TERMINAL_UNMARKED_FAILURE","local_attempt_no",0L,"local_execution_id",null));
   rows.get("durable_task").add(row("id",TASK,"task_type",spec.type(),"pool",spec.pool(),"subject_kind","MEETING","subject_id",MEETING,"producer_key",spec.producerKey(),"input_kind",spec.input().kind(),"input_id",spec.input().id(),"input_fingerprint",spec.inputFingerprint(),"business_version",spec.businessVersion(),"priority",0L,"cancel_epoch",spec.cancelEpoch(),"max_attempts",3L,"attempt",0L,"state","FAILED","output_kind",null,"output_id",null));return this;
  }
  Fixture remote(){local();var s=rows.get("publication_submission").getFirst();s.put("local_state","UNDECIDED");for(String field:List.of("local_decided_at","local_decided_by","local_reason","local_attempt_no","local_execution_id"))s.put(field,null);rows.get("durable_task").getFirst().put("attempt",1L);rows.get("task_attempt").add(row("task_id",TASK,"attempt",1L,"ended_at","done","outcome","FAILED"));rows.get("external_operation").add(row("id",OP,"operation_kind","KNOWLEDGE_PUBLISH","business_key",PUB+":"+FINAL,"subject_kind","MEETING","subject_id",MEETING,"state","FAILED","request_hash",manifest.get("logicalRequestHash"),"current_execution_no",1L,"external_result_present",0L));rows.get("external_execution").add(row("id",EXEC,"operation_id",OP,"execution_no",1L,"task_id",TASK,"request_hash",manifest.get("executionRequestHash"),"state","FAILED","marked_at","marked","task_attempt",1L,"finished_at","done","no_effect_basis","DEFINITIVE_REJECTION","no_effect_verified_at","verified","provider_proof_reference_shape",1L,"no_effect_evidence_present",1L,"proof_store",STORE,"proof_receipt","receipt-"+"a".repeat(32),"proof_digest","b".repeat(64)));rows.get("publication_attempt").add(row("id",EXEC,"publication_id",PUB,"confirmation_id",FINAL,"operation_id",OP,"execution_id",EXEC,"kind","PUBLISH","outcome","FAILED","finished_at","done"));executions.put(EXEC,new PublicationWriteHistory.ExecutionOriginal(originals.get(TASK),"knowledge:"+STORE+":receipt-"+"a".repeat(32)+":"+"b".repeat(64)));return this;}
  void verify(){PublicationWriteHistory.verify(root,pubs,finals,new PublicationGraph.Plan(MEETING,List.of(),List.of(),rows,new ManifestSeed().budget()),originals,executions);}
 }
 static final class ManifestSeed {
 Map<String,Object> manifest(){var m=new TreeMap<String,Object>();m.put("schema","ama.knowledge-publication-manifest.v1");for(String k:List.of("publicationId","confirmationId","meetingId"))m.put(k,"12345678-1234-4234-8234-123456789abc");for(String k:List.of("providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId","requesterId","libraryId","folderId"))m.put(k,k);for(String k:List.of("logicalRequestHash","payloadSha256","archiveIndexSha256"))m.put(k,"a".repeat(64));m.put("businessKey",m.get("publicationId")+":"+m.get("confirmationId"));m.put("businessVersion",13L);m.put("cancelEpoch",0L);m.put("executionNo",1L);m.put("submissionNo",1L);m.put("priorProofs",List.of());m.put("renderArtifact",Map.of("version","render-v1","sha256","b".repeat(64)));m.put("executionRequestHash",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(Map.of("schema","ama.knowledge-publish-execution.v1","logicalRequestHash",m.get("logicalRequestHash"),"executionNo",1L,"priorProofs",List.of()),budget())));return m;}
 TranscriptAcquisitionBudget.Ledger budget(){return budget(false);}
 TranscriptAcquisitionBudget.Ledger budget(boolean cancelled){return new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256).ledger(new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,16777216,64,128,128,2000000,128,255,4194304,4194304,16777216,65536,64,100000,500000),new ama.contract.MinutesContracts.CanonicalBudget(67108864,128,4000000,4194304,16777216),()->cancelled);}

 }
}
