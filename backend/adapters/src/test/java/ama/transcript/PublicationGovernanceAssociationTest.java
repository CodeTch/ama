package ama.transcript;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure immutable-success metadata; deliberately grants no viewer/content authorization. */
class PublicationGovernanceAssociationTest {
 @Test void originalPublishedBindingCanBeReconciledAfterRevocationWithoutRevivingRead(){
  for(String state:List.of("WITHDRAW_PENDING","WITHDRAWN","OBSOLETE_PENDING","OBSOLETE","UNKNOWN")){
   var f=new Task11DocumentAssociationTest.Fixture();f.root.put("governance_state",state);f.root.put("cancel_epoch",1L);f.rows.get("external_document_mapping").getFirst().put("authority_state","UNKNOWN");
   assertDoesNotThrow(()->PublicationDocumentRead.recordedGovernance(f.root,f.pub,f.confirmation,f.rows));assertThrows(RuntimeException.class,f::select);assertNull(f.projection().get("officialDocumentUrl"));
  }
 }
 @Test void reconciliationStillRequiresTheActualSucceededPublicationAndExactFrozenSource(){
  for(var change:List.of(new String[]{"external_document_mapping","provider_id","other"},new String[]{"publication_attempt","outcome","UNKNOWN"},new String[]{"external_execution","state","UNKNOWN"},new String[]{"external_operation","state","FAILED"},new String[]{"durable_task","output_id","other"},new String[]{"task_attempt","outcome","FAILED"})){
   var f=new Task11DocumentAssociationTest.Fixture();f.rows.get(change[0]).getFirst().put(change[1],change[2]);assertThrows(RuntimeException.class,()->PublicationDocumentRead.recordedGovernance(f.root,f.pub,f.confirmation,f.rows));
  }
  var moved=new Task11DocumentAssociationTest.Fixture();moved.root.put("minutes_revision_id","other");assertThrows(RuntimeException.class,()->PublicationDocumentRead.recordedGovernance(moved.root,moved.pub,moved.confirmation,moved.rows));
  var future=new Task11DocumentAssociationTest.Fixture();future.rows.get("durable_task").getFirst().put("cancel_epoch",1L);assertThrows(RuntimeException.class,()->PublicationDocumentRead.recordedGovernance(future.root,future.pub,future.confirmation,future.rows));
 }
}
