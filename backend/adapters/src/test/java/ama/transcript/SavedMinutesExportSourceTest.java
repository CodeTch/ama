package ama.transcript;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Selection guards only. Full saved provenance still comes from JdbcMinutesFactsReader. */
class SavedMinutesExportSourceTest {
 private final String body=UUID.randomUUID().toString(),finalId=UUID.randomUUID().toString();
 private Map<String,Object> root(boolean published){
  var root=new HashMap<String,Object>();root.put("minutes_revision_id",body);root.put("revision",8L);root.put("lifecycle","ACTIVE");root.put("governance_state","ACTIVE");root.put("ever_published",published?1L:0L);root.put("stage",published?"PUBLISHED":"MINUTES_PENDING");root.put("final_confirmation_id",published?finalId:null);
  for(String field:List.of("info_revision_id","roster_revision_id","transcript_confirmation_id","material_snapshot_id","element_set_id","template_revision_id","exclusion_revision_id"))root.put(field,UUID.randomUUID().toString());return root;
 }
 private Map<String,Object> frozen(Map<String,Object> root){var fixed=new HashMap<>(root);fixed.put("id",finalId);fixed.put("meeting_revision",root.get("revision"));return fixed;}
 @Test void currentSavedSourceAndExactPublishedSnapshotAreEligible(){
  assertDoesNotThrow(()->SavedMinutesExportSource.checkSelection(root(false),body,false,null));var root=root(true);assertDoesNotThrow(()->SavedMinutesExportSource.checkSelection(root,body,false,frozen(root)));
 }
 @Test void historyAndUnadoptedCandidateCannotBeRequestedAsCurrent(){
  assertThrows(ama.contract.TranscriptContracts.Failure.class,()->SavedMinutesExportSource.checkSelection(root(false),UUID.randomUUID().toString(),false,null));
  assertThrows(ama.contract.TranscriptContracts.Failure.class,()->SavedMinutesExportSource.checkSelection(root(false),body,true,null));
 }
 @Test void publishedSourceCannotDriftFromAnyFrozenVersion(){
  for(String field:List.of("minutes_revision_id","info_revision_id","roster_revision_id","transcript_confirmation_id","material_snapshot_id","element_set_id","template_revision_id","exclusion_revision_id")){
   var root=root(true);var frozen=frozen(root);frozen.put(field,UUID.randomUUID().toString());assertThrows(ama.contract.TranscriptContracts.Failure.class,()->SavedMinutesExportSource.checkSelection(root,body,false,frozen),field);
  }
  var root=root(true);var frozen=frozen(root);frozen.put("meeting_revision",7L);assertThrows(ama.contract.TranscriptContracts.Failure.class,()->SavedMinutesExportSource.checkSelection(root,body,false,frozen));
  assertThrows(ama.contract.TranscriptContracts.Failure.class,()->SavedMinutesExportSource.checkSelection(root,body,false,null));
 }
 @Test void cleaningAndAllNonActiveGovernanceStatesDenySelection(){
  for(String state:List.of("UNKNOWN","WITHDRAW_PENDING","WITHDRAWN","OBSOLETE_PENDING","OBSOLETE")){var root=root(false);root.put("governance_state",state);assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->SavedMinutesExportSource.checkSelection(root,body,false,null));}
  var root=root(false);root.put("lifecycle","CLEANING");assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->SavedMinutesExportSource.checkSelection(root,body,false,null));
 }
}
