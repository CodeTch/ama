package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.minutes.contract.StoredSourceGraphTest.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class GenerationFingerprintTest {
 static StoredProvenanceFacts region(String requestMeeting,String baseSet,String instructions,boolean modern){
  var original=facts();var rows=new ArrayList<>(original.rows());rows.removeIf(r->r.table()==Table.GENERATION_INPUT);
  rows.add(row(Table.GENERATION_INPUT,id(8),id(1),id(7),null,id(9),id(10),id(11),"prompt","model",1L,H,"REGION",null));
  rows.add(row(Table.GENERATION_REGION_REQUEST,id(8),requestMeeting,baseSet,"OVERVIEW",instructions));
  var definitions=new EnumMap<ProfileKind,Artifact>(profiles().definitions());if(modern)definitions.put(ProfileKind.GENERATION_HASH,artifact("ama.generation-input.v3"));
  return new StoredProvenanceFacts(rows,original.files(),List.of(new GenerationConfiguration(id(8),artifact("prompt"),artifact("model"),new Profiles(definitions),id(9),List.of())),List.of(),100000L);
 }
 @Test void regionManifestPinsExactRequestAndNeverReusesLegacyProfile(){
  var helper=new GenerationFingerprint();var first=helper.data(new GenerationFacts(id(8),region(id(1),id(11),"保留决定",true)),new Fence(1,2,3),BUDGET,()->false);
  var changed=helper.data(new GenerationFacts(id(8),region(id(1),id(11),"按时间排序",true)),new Fence(1,2,3),BUDGET,()->false);assertNotEquals(first,changed);
  fails(PROFILE_UNAVAILABLE,()->helper.data(new GenerationFacts(id(8),region(id(1),id(11),"保留决定",false)),new Fence(1,2,3),BUDGET,()->false));
  fails(SOURCE_MEMBERSHIP_MISMATCH,()->helper.data(new GenerationFacts(id(8),region(id(2),id(11),"保留决定",true)),new Fence(1,2,3),BUDGET,()->false));
  fails(INPUT_INVALID,()->helper.data(new GenerationFacts(id(8),region(id(1),id(12),"保留决定",true)),new Fence(1,2,3),BUDGET,()->false));
 }
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
