package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.minutes.contract.StoredSourceGraphTest.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class FinalAndCoverageTest {
 static StoredProvenanceFacts finalFacts(){
  var f=HashProfilesTest.targetFacts();var rows=new ArrayList<>(f.rows());
  rows.add(row(Table.MINUTES_REVISION,id(13),id(1),1L,null,"title","AI","body",id(8),"human",T));
  rows.add(row(Table.FINAL_CONFIRMATION,id(20),id(1),1L,id(21),id(22),id(7),id(13),null,id(11),id(9),id(10),H,H,"human",T));
  return new StoredProvenanceFacts(rows,f.files(),f.configurations(),f.evidenceAnchors(),100000L);
 }
 static FullRow review(int id,long no,String outcome,Anchor anchor,String target,String sources){
  return row(Table.RESULT_REVIEW,id(id),id(1),no,"SOURCE_SUPPORT",id(12),null,OutputValidation.anchorText(anchor),target,sources,"ama.result-review.v2",outcome,"human",T);
 }
 @Test void independentFullFinalProjectionAndFrozenMembershipIgnoresLaterReview()throws Exception {
  var helper=new ResultHashProfiles();var f=finalFacts();var data=assertDoesNotThrow(()->helper.finalData(new FinalFacts(id(20),f),BUDGET,()->false));
  HashProfilesTest.vector("final",data,"ama.final-sources.v2","FINAL_SOURCES");
  var rows=new ArrayList<>(f.rows());rows.add(review(40,1,"UNSUPPORTED",OutputValidation.anchor("A😀B",0,3,()->false),H,H));
  rows.add(row(Table.RESULT_REVIEW_EVIDENCE,id(40),id(15)));
  var later=new StoredProvenanceFacts(rows,f.files(),f.configurations(),f.evidenceAnchors(),100000L);
  assertEquals(data,helper.finalData(new FinalFacts(id(20),later),BUDGET,()->false));
 }
 @Test void latestContraryExactScopeWinsAndPartialReviewsNeverGiveWholeCredit(){
  var facts=HashProfilesTest.targetFacts();var full=HashProfilesTest.review(facts,0,3,List.of(id(15)));var helper=new ReviewCoverageEvaluator();
  var a=full.anchor();var early=review(40,1,"SUPPORTED",a,H,H);var later=review(41,2,"PARTIAL",a,H,H);
  var result=helper.evaluate(full,H,H,List.of(later,early),()->false);
  assertEquals(id(41),result.reviewId());assertEquals("PARTIAL",result.outcome());assertFalse(result.wholeTargetSupported());
  assertTrue(helper.evaluate(full,H,H,List.of(early),()->false).wholeTargetSupported());
  var partial=HashProfilesTest.review(facts,0,1,List.of(id(15)));
  var span=review(42,3,"SUPPORTED",partial.anchor(),H,H);assertFalse(helper.evaluate(partial,H,H,List.of(span),()->false).wholeTargetSupported());
  assertNull(helper.evaluate(full,H,H,List.of(span),()->false).reviewId());
 }
 @Test void newTargetOrHashInvalidatesReviewAndMissingFinalInputFails(){
  var f=finalFacts();var helper=new ResultHashProfiles();var rows=new ArrayList<>(f.rows());rows.removeIf(r->r.table()==Table.MINUTES_REVISION);rows.add(row(Table.MINUTES_REVISION,id(13),id(1),1L,null,"title","HUMAN","body",null,"human",T));
  fails(PROFILE_UNAVAILABLE,()->helper.finalData(new FinalFacts(id(20),new StoredProvenanceFacts(rows,f.files(),f.configurations(),f.evidenceAnchors(),100000L)),BUDGET,()->false));
  var scope=HashProfilesTest.review(HashProfilesTest.targetFacts(),0,3,List.of(id(15)));
  var review=review(40,1,"SUPPORTED",scope.anchor(),H,H);
  assertNull(new ReviewCoverageEvaluator().evaluate(scope,"1".repeat(64),H,List.of(review),()->false).reviewId());
 }
}
