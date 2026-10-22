package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.minutes.contract.StoredSourceGraphTest.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class GenerationFingerprintTest {
 static final String CH="cb9ff149976f0075e7215487dedbc15267d3fa9d79a0f99956ff087b3924c182";
 static StoredProvenanceFacts facts(){
  var rows=new ArrayList<>(base());rows.removeIf(r->r.table()==Table.TRANSCRIPT_CONFIRMATION);rows.add(row(Table.TRANSCRIPT_CONFIRMATION,id(7),id(1),id(5),CH,"human",T));rows.add(input(8,null,null));
  return new StoredProvenanceFacts(rows,List.of(new ContentFile(id(3),H,9)),List.of(new GenerationConfiguration(id(8),artifact("prompt"),artifact("model"),profiles(),id(9),List.of())),List.of(),100000L);
 }
 @Test void completeIndependentEmptyGenerationVector()throws Exception {
  HashProfilesTest.vector("generation",new GenerationFingerprint().data(new GenerationFacts(id(8),facts()),new Fence(1,2,3),BUDGET,()->false),"ama.generation-input.v2","GENERATION_INPUT");
 }
 @Test void fixedFenceChangesDigestAndRowBaseMustMatch(){
  var helper=new GenerationFingerprint();var first=helper.data(new GenerationFacts(id(8),facts()),new Fence(1,2,3),BUDGET,()->false);
  var changed=helper.data(new GenerationFacts(id(8),facts()),new Fence(1,2,4),BUDGET,()->false);assertNotEquals(first,changed);
  fails(INPUT_INVALID,()->helper.data(new GenerationFacts(id(8),facts()),new Fence(2,2,3),BUDGET,()->false));
 }
 @Test void computeCannotPretendUninterpretedArchiveFactsAreValidated(){
  var index=new StoredFactsIndex(facts(),BUDGET,()->false);
  fails(PROFILE_UNAVAILABLE,()->new GenerationFingerprint().recomputeStored(index.one(Table.GENERATION_INPUT,id(8),PROVENANCE_UNAVAILABLE),new Fence(1,2,3),profiles(),index));
 }
 @Test void taskVerificationRejectsCrossInputAndMissingOrConflictingHistoricalFences(){
  var helper=new GenerationFingerprint();
  var wrong=new TaskSpec("MINUTES","MINUTES",new SubjectRef("MEETING",id(1)),H,new TypedRef("GENERATION_INPUT",id(18)),H,2,0,3,1);
  fails(INPUT_INVALID,()->helper.verify(new GenerationFacts(id(8),facts()),wrong,profiles(),BUDGET,()->false));
  var correct=new TaskSpec("MINUTES","MINUTES",new SubjectRef("MEETING",id(1)),H,new TypedRef("GENERATION_INPUT",id(8)),H,2,0,3,1);
  fails(PROFILE_UNAVAILABLE,()->helper.verify(new GenerationFacts(id(8),facts()),correct,profiles(),BUDGET,()->false));
 }
}
