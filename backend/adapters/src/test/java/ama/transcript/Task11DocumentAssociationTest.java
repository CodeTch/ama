package ama.transcript;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task11DocumentAssociationTest {
 static final String M="12345678-1234-4234-8234-123456789abc",P="12345678-1234-4234-8234-123456789abd",F="12345678-1234-4234-8234-123456789abe";
 static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 static class Fixture {
  final Map<String,Object> root=map("id",M,"owner_id","owner","revision",13L,"cancel_epoch",0L,"lifecycle","ACTIVE","governance_state","ACTIVE","stage","PUBLISHED","ever_published",1L,"final_confirmation_id",F);
  final Map<String,Object> pub=map("id",P,"meeting_id",M,"confirmation_id",F,"business_key",P+":"+F,"library_id","lib","folder_id","folder","state","SUCCEEDED","revision",2L);
  final Map<String,Object> confirmation=map("id",F,"meeting_id",M,"meeting_revision",13L);
  final Map<String,List<Map<String,Object>>> rows=new LinkedHashMap<>();
  Fixture(){
   for(String key:List.of("info_revision_id","roster_revision_id","transcript_confirmation_id","minutes_revision_id","material_snapshot_id","element_set_id","template_revision_id","exclusion_revision_id")){root.put(key,key);confirmation.put(key,key);}
   rows.put("final_confirmation",List.of(confirmation));
   rows.put("external_document_mapping",List.of(map("publication_id",P,"provider_id","kb","external_document_id","doc-"+"a".repeat(32),"authority_state","ACTIVE")));
   rows.put("publication_attempt",List.of(map("id","attempt","publication_id",P,"confirmation_id",F,"operation_id","op","execution_id","exec","kind","PUBLISH","outcome","SUCCESS","finished_at","done")));
   rows.put("external_execution",List.of(map("id","exec","operation_id","op","task_id","task","execution_no",1L,"state","SUCCEEDED","marked_at","marked","finished_at","done","task_attempt",1L,"request_hash","exec-hash")));
   rows.put("external_operation",List.of(map("id","op","current_execution_no",1L,"state","SUCCEEDED","operation_kind","KNOWLEDGE_PUBLISH","subject_kind","MEETING","subject_id",M,"business_key",P+":"+F,"request_hash","logical-hash")));
   rows.put("publication_submission",List.of(map("task_id","task","publication_id",P,"confirmation_id",F,"provider_id","kb","local_state","UNDECIDED","business_key",P+":"+F,"library_id","lib","folder_id","folder","logical_request_hash","logical-hash","execution_request_hash","exec-hash","submission_no",1L)));
   rows.put("durable_task",List.of(map("id","task","state","SUCCEEDED","task_type","KNOWLEDGE_PUBLISH_V1","pool","KNOWLEDGE_PUBLISH","subject_kind","MEETING","subject_id",M,"input_kind","PUBLICATION","input_id",P,"output_kind","PUBLICATION","output_id",P,"business_version",13L,"cancel_epoch",0L,"attempt",1L,"input_fingerprint","logical-hash")));
   rows.put("task_attempt",List.of(map("task_id","task","attempt",1L,"ended_at","done","outcome","SUCCEEDED")));
  }
  Object select(){return PublicationDocumentRead.select(root,pub,confirmation,rows);}
  Map<String,Object> projection(){return PublicationReadProjection.status(root,pub,rows,false);}
 }
 @Test void completeSuccessAssociationExposesOnlyExactSameOriginEntry(){var f=new Fixture();assertDoesNotThrow(f::select);assertEquals("/api/v1/meetings/"+M+"/publications/"+P+"/document",f.projection().get("officialDocumentUrl"));assertEquals("READY",f.projection().get("documentViewAvailability"));}
 @Test void everySuccessAssociationIsNecessaryForDocumentAvailability(){
  for(var change:List.of(new String[]{"external_document_mapping","authority_state","WITHDRAWN"},new String[]{"external_document_mapping","provider_id","other"},new String[]{"publication_attempt","outcome","PENDING"},new String[]{"publication_attempt","confirmation_id","other"},new String[]{"external_execution","state","UNKNOWN"},new String[]{"external_execution","operation_id","other"},new String[]{"external_operation","state","MANUAL_REQUIRED"},new String[]{"publication_submission","folder_id","other"},new String[]{"durable_task","output_id","other"},new String[]{"task_attempt","outcome","FAILED"})){
   var f=new Fixture();f.rows.get(change[0]).getFirst().put(change[1],change[2]);assertThrows(RuntimeException.class,f::select,Arrays.toString(change));assertNull(f.projection().get("officialDocumentUrl"));
  }
 }
 @Test void unrelatedFinalPointersOrUnfinishedAttemptsCannotBorrowSuccess(){var pointer=new Fixture();pointer.root.put("minutes_revision_id","other");assertThrows(RuntimeException.class,pointer::select);assertNull(pointer.projection().get("officialDocumentUrl"));var open=new Fixture();open.rows.get("task_attempt").getFirst().put("ended_at",null);assertThrows(RuntimeException.class,open::select);assertNull(open.projection().get("officialDocumentUrl"));var duplicate=new Fixture();var attempt=duplicate.rows.get("publication_attempt").getFirst();duplicate.rows.put("publication_attempt",List.of(attempt,new LinkedHashMap<>(attempt)));assertThrows(RuntimeException.class,duplicate::select);}
}
