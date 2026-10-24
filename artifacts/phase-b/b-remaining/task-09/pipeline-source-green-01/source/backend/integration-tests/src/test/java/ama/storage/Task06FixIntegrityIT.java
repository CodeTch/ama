package ama.storage;
import ama.contract.TranscriptContracts.*;import ama.contract.MinutesContracts.Failure;import org.junit.jupiter.api.Test;import java.util.*;import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
class Task06FixIntegrityIT extends Task06FixFixture {
 @Test void everyAppendReservesProspectiveDepthAndNodeAtBoundary()throws Exception {
  var outcomes=new ArrayList<Object>();for(Operation operation:List.of(Operation.SAVE,Operation.MARKS,Operation.RESTORE))for(boolean depth:List.of(true,false)){
   newMeeting();String initial=saved(raw());var service=bounded(depth?3:10,depth?10:3);var cmd=new Command(operation,2,initial,operation==Operation.RESTORE?List.of():edits(initial,operation));String next=resultId(service.mutate(actor(),meeting,"boundary",cmd,UUID.randomUUID().toString()),"revisionId");assertNotNull(service.readCurrent(actor(),meeting).get("selected"));
   var before=atomicState();var overflow=new Command(operation,3,next,operation==Operation.RESTORE?List.of():edits(next,operation));assertThrows(Failure.class,()->service.mutate(actor(),meeting,"overflow",overflow,UUID.randomUUID().toString()));assertEquals(before,atomicState());var capabilities=(Map<?,?>)service.readOwner(actor(),meeting,next).get("capabilities");assertEquals(false,capabilities.get("canSave"));assertEquals(false,capabilities.get("canRestore"));assertEquals(true,capabilities.get("canConfirm"));outcomes.add(Map.of("operation",operation,"ceiling",depth?"depth":"nodes","meeting",meeting,"readable",next,"overflowAtomic",true));
  }Files.writeString(receipt.resolve("prospective-ancestry-boundaries.json"),json.writeValueAsString(outcomes));
 }
 @Test void currentPointersMustBeExactForOwnerAndMemberWhileOldHistoryRemainsValid()throws Exception {
  String first=saved(raw()),oldConfirmation=confirm(first,2);String second=resultId(isolated().mutate(actor(),meeting,"next",new Command(Operation.MARKS,3,first,edits(first,Operation.MARKS)),UUID.randomUUID().toString()),"revisionId");String currentConfirmation=confirm(second,4);String selectedMeeting=meeting;
  String member="task06-fix-member-"+UUID.randomUUID();identity(member);assertEquals(200,call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken","5","rosterDraftIds",List.of(member))),"info").statusCode());assertEquals(200,call("POST","/meetings/"+meeting+"/roster-confirmations","{\"expectedRevisionToken\":\"6\"}","roster").statusCode());var memberActor=new ama.contract.IdentityContracts.Actor(member,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  assertNotNull(isolated().readOwner(actor(),meeting,first).get("selectedConfirmation"));newMeeting();String foreignWorking=saved(raw()),foreignConfirmation=confirm(foreignWorking,2);meeting=selectedMeeting;var service=isolated();
  for(var combination:List.of(Arrays.asList(second,oldConfirmation),Arrays.asList(second,foreignConfirmation),Arrays.asList((String)null,currentConfirmation))){
   rootMutation(()->j.update("UPDATE meeting SET transcript_revision_id=?,transcript_confirmation_id=? WHERE id=?",combination.get(0),combination.get(1),meeting));
   try{assertThrows(Failure.class,()->service.readCurrent(actor(),meeting));assertThrows(Failure.class,()->service.readCurrent(memberActor,meeting));}
   finally{rootMutation(()->j.update("UPDATE meeting SET transcript_revision_id=?,transcript_confirmation_id=? WHERE id=?",second,currentConfirmation,meeting));}
  }assertNotNull(service.readCurrent(actor(),meeting).get("confirmation"));assertNotNull(service.readCurrent(memberActor,meeting).get("confirmation"));
 }
}
