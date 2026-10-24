package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.minutes.contract.StoredSourceGraphTest.*;
import static org.junit.jupiter.api.Assertions.*;

class HashProfilesTest {
 static StoredProvenanceFacts transcriptFacts(){
  return new StoredProvenanceFacts(base(),List.of(new ContentFile(id(3),H,9)),List.of(),List.of(),100000L);
 }
 static StoredProvenanceFacts targetFacts(){
  var rows=new ArrayList<>(base());rows.add(input(8,null,null));
  rows.add(row(Table.MINUTES_ELEMENT,id(12),id(11),"CONCLUSION",1L,"A😀B","PARTIAL"));
  rows.add(row(Table.SOURCE_LOCATOR,id(14),id(1),"TRANSCRIPT",id(5),id(6),null,null,null,null,null,null,null));
  var a=OutputValidation.anchor("A😀B",0,2,()->false);
  rows.add(row(Table.EVIDENCE_LINK,id(15),id(14),id(12),null,"PARTIAL",OutputValidation.anchorText(a)));
  return new StoredProvenanceFacts(rows,List.of(new ContentFile(id(3),H,9)),List.of(new GenerationConfiguration(id(8),artifact("prompt"),artifact("model"),profiles(),id(9),List.of())),List.of(new EvidenceAnchor(id(15),a)),100000L);
 }
 static ReviewFacts review(StoredProvenanceFacts facts,long start,long end,List<String> selected){
  return new ReviewFacts(id(1),ReviewKind.SOURCE_SUPPORT,id(12),null,OutputValidation.anchor("A😀B",start,end,()->false),selected,facts);
 }
 static String vector(String name,String suffix)throws Exception{
  try(var stream=HashProfilesTest.class.getResourceAsStream("/ama/minutes/vectors/"+name+suffix)){assertNotNull(stream);return new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).strip();}
 }
 static void vector(String name,CanonicalValue data,String profile,String kind)throws Exception{
  var codec=new CanonicalValueCodec();var envelope=ContractValues.envelope(profile,kind,data);
  assertEquals(vector(name,".hex"),HexFormat.of().formatHex(codec.encode(envelope,BUDGET.canonical(),()->false)));
  assertEquals(vector(name,".sha256"),codec.sha256(envelope,BUDGET.canonical(),()->false));
 }
 @Test void completeIndependentConfirmationVectorAndFourMarkSetOrder()throws Exception {
  var helper=new TranscriptConfirmationHash();var facts=transcriptFacts();
  vector("confirmation",helper.data(new ConfirmationFacts(id(7),facts),BUDGET,()->false),"ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION");
  var rows=new ArrayList<>(facts.rows());
  for(var mark:List.of("GENERATION_FOCUS","DISPUTE","KEY","CONCLUSION"))rows.add(row(Table.TRANSCRIPT_SEGMENT_MARK,id(5),id(6),mark));
  var all=new StoredProvenanceFacts(rows,facts.files(),List.of(),List.of(),100000L);
  vector("confirmation-marks",helper.data(new ConfirmationFacts(id(7),all),BUDGET,()->false),"ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION");
  Collections.reverse(rows);assertEquals(helper.data(new ConfirmationFacts(id(7),all),BUDGET,()->false),helper.data(new ConfirmationFacts(id(7),new StoredProvenanceFacts(rows,facts.files(),List.of(),List.of(),100000L)),BUDGET,()->false));
  rows.add(row(Table.TRANSCRIPT_SEGMENT_MARK,id(5),id(6),"KEY"));
  fails(SOURCE_MEMBERSHIP_MISMATCH,()->helper.data(new ConfirmationFacts(id(7),new StoredProvenanceFacts(rows,facts.files(),List.of(),List.of(),100000L)),BUDGET,()->false));
 }
 @Test void completeIndependentReviewTargetAndSourcesVectors()throws Exception{
  var facts=review(targetFacts(),0,3,List.of(id(15)));var helper=new ResultHashProfiles();
  vector("review-target",helper.targetData(facts,BUDGET,()->false),"ama.result-review.v2","REVIEW_TARGET");
  vector("review-sources",helper.sourcesData(facts,BUDGET,()->false),"ama.result-review.v2","REVIEW_SOURCES");
  fails(PROFILE_UNAVAILABLE,()->helper.target(facts,artifact("ama.result-review.v2"),BUDGET,()->false));
 }
 @Test void reviewSourcesRejectsSupportiveSubsetAndFullContentChangesTargetHash(){
  var helper=new ResultHashProfiles();var facts=targetFacts();
  fails(SOURCE_MEMBERSHIP_MISMATCH,()->helper.sourcesData(review(facts,0,3,List.of()),BUDGET,()->false));
  var first=helper.targetData(review(facts,0,1,List.of(id(15))),BUDGET,()->false);
  var rows=new ArrayList<>(facts.rows());rows.removeIf(r->r.table()==Table.MINUTES_ELEMENT);rows.add(row(Table.MINUTES_ELEMENT,id(12),id(11),"CONCLUSION",1L,"A😀C","PARTIAL"));
  var changed=new StoredProvenanceFacts(rows,facts.files(),facts.configurations(),List.of(),100000L);
  var second=helper.targetData(new ReviewFacts(id(1),ReviewKind.SOURCE_SUPPORT,id(12),null,OutputValidation.anchor("A😀C",0,1,()->false),List.of(),changed),BUDGET,()->false);
  assertNotEquals(first,second);
 }
 @Test void confirmationCannotClaimRawProfileChainWithoutConfiguredArchiveReader(){
  fails(PROFILE_UNAVAILABLE,()->new TranscriptConfirmationHash().hash(new ConfirmationFacts(id(7),transcriptFacts()),artifact("ama.transcript-confirmation.v1"),BUDGET,()->false));
 }

 @Test void materialReviewCannotBypassOwningRawArchiveChain(){
  var f=materials();var rows=new ArrayList<>(f.rows());rows.add(row(Table.MINUTES_ELEMENT,id(12),id(11),"ACTION",1L,"x","PARTIAL"));
  rows.add(row(Table.ACTION_ITEM,id(12),null,null,null));rows.add(row(Table.ACTION_SUGGESTION,id(12),id(8),"Alice",null));
  var a=OutputValidation.anchor("x",0,1,()->false);rows.add(row(Table.EVIDENCE_LINK,id(15),id(14),id(12),null,"PARTIAL",OutputValidation.anchorText(a)));
  var facts=new StoredProvenanceFacts(rows,f.files(),f.configurations(),List.of(new EvidenceAnchor(id(15),a)),100000L);
  var review=new ReviewFacts(id(1),ReviewKind.SOURCE_SUPPORT,id(12),null,a,List.of(id(15)),facts);
  fails(PROFILE_UNAVAILABLE,()->new ResultHashProfiles().sources(review,artifact("ama.result-review.v2"),BUDGET,()->false));
 }

 @Test void eachSavedMarkAdditionAndRemovalChangesWholeConfirmationDomain(){
  var f=transcriptFacts();var helper=new TranscriptConfirmationHash();var codec=new CanonicalValueCodec();
  String empty=codec.sha256(ContractValues.envelope("ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",helper.data(new ConfirmationFacts(id(7),f),BUDGET,()->false)),BUDGET.canonical(),()->false);
  var allRows=new ArrayList<>(f.rows());for(var mark:Mark.values())allRows.add(row(Table.TRANSCRIPT_SEGMENT_MARK,id(5),id(6),mark.name()));
  var all=new StoredProvenanceFacts(allRows,f.files(),List.of(),List.of(),100000L);
  String complete=codec.sha256(ContractValues.envelope("ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",helper.data(new ConfirmationFacts(id(7),all),BUDGET,()->false)),BUDGET.canonical(),()->false);
  for(var mark:Mark.values()){
   var oneRows=new ArrayList<>(f.rows());oneRows.add(row(Table.TRANSCRIPT_SEGMENT_MARK,id(5),id(6),mark.name()));
   var one=new StoredProvenanceFacts(oneRows,f.files(),List.of(),List.of(),100000L);
   assertNotEquals(empty,codec.sha256(ContractValues.envelope("ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",helper.data(new ConfirmationFacts(id(7),one),BUDGET,()->false)),BUDGET.canonical(),()->false));
   var removed=new ArrayList<>(allRows);removed.removeIf(r->r.table()==Table.TRANSCRIPT_SEGMENT_MARK&&r.string("mark").equals(mark.name()));
   var fewer=new StoredProvenanceFacts(removed,f.files(),List.of(),List.of(),100000L);
   assertNotEquals(complete,codec.sha256(ContractValues.envelope("ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",helper.data(new ConfirmationFacts(id(7),fewer),BUDGET,()->false)),BUDGET.canonical(),()->false));
  }
 }
}
