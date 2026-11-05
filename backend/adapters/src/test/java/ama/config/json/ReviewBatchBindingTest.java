package ama.config.json;
import ama.contract.MinutesContracts.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.minutes.contract.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
import static ama.contract.MinutesContracts.FailureCode.*;
class ReviewBatchBindingTest {
 static List<RawArchive> raw()throws Exception{return full().stream().filter(a->Set.of(ArtifactType.CANONICAL_CODEC,ArtifactType.REVIEW_HASH,ArtifactType.CONFIRMATION_HASH,ArtifactType.PROCESSING_CONFIG,ArtifactType.PARSER_CONFIG).contains(a.claimedArtifactType())).toList();}
 static StoredProvenanceFacts facts(boolean corrupt,boolean missingEvidence)throws Exception{
  var p=finalStored();var rows=new ArrayList<>(p.rows());var anchor=OutputValidation.anchor("A",0,1,()->false);var scopes=new ArrayList<ReviewScopeFact>();
  for(int i=1;i<=3;i++){String id=uid(60+i);rows.add(row(Table.RESULT_REVIEW,id,MEETING,(long)i,"SOURCE_SUPPORT",ELEMENT,null,OutputValidation.anchorText(anchor),resource("full-review-target.sha256"),corrupt&&i==2?"0".repeat(64):resource("full-review-sources.sha256"),"ama.result-review.v2","PARTIAL","human",TIME));if(!missingEvidence||i!=2)rows.add(row(Table.RESULT_REVIEW_EVIDENCE,id,EV));scopes.add(new ReviewScopeFact(id,anchor));}
  return new StoredProvenanceFacts(rows,p.files(),p.configurations(),p.evidenceAnchors(),p.taskSpecs(),scopes,100000L);
 }
 static List<FullRow> reviews(StoredProvenanceFacts facts){return facts.rows().stream().filter(r->r.table()==Table.RESULT_REVIEW).toList();}
 @Test void sharedClosureVerifiesEveryReviewWithinOneExactArchiveBudget()throws Exception{
  var f=facts(false,false);var raw=raw();long exact=raw.stream().mapToLong(a->a.bytes().length).sum()+fixture("manifest").length;
  assertDoesNotThrow(()->new ResultHashProfiles(service(),raw,totalBudget(exact)).verifyReviews(f,reviews(f),GRAPH,()->false));
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new ResultHashProfiles(service(),raw,totalBudget(exact-1)).verifyReviews(f,reviews(f),GRAPH,()->false)).code());
 }
 @Test void anyChangedDigestOrMissingMembershipRejectsBatch()throws Exception{
  var f=facts(true,false);assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->new ResultHashProfiles(service(),raw(),B).verifyReviews(f,reviews(f),GRAPH,()->false)).code());
  var missing=facts(false,true);assertEquals(SOURCE_MEMBERSHIP_MISMATCH,assertThrows(Failure.class,()->new ResultHashProfiles(service(),raw(),B).verifyReviews(missing,reviews(missing),GRAPH,()->false)).code());
 }
 @Test void duplicateAndCancellationCannotCreateValidation()throws Exception{
  var f=facts(false,false);var one=reviews(f).getFirst();assertEquals(TARGET_INVALID,assertThrows(Failure.class,()->new ResultHashProfiles(service(),raw(),B).verifyReviews(f,List.of(one,one),GRAPH,()->false)).code());
  assertEquals(CANCELLED,assertThrows(Failure.class,()->new ResultHashProfiles(service(),raw(),B).verifyReviews(f,reviews(f),GRAPH,()->true)).code());
 }
 @Test void changedOriginalArchiveOrUnusedClosureCannotValidate()throws Exception{
  var f=facts(false,false);var raw=new ArrayList<>(raw());var first=raw.getFirst();first.bytes()[0]='x';assertThrows(Failure.class,()->new ResultHashProfiles(service(),raw,B).verifyReviews(f,reviews(f),GRAPH,()->false));
  assertThrows(Failure.class,()->new ResultHashProfiles(service(),full(),B).verifyReviews(f,reviews(f),GRAPH,()->false));
 }
}
