package ama.transcript;
import java.util.*;
import ama.minutes.contract.OutputValidation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class MinutesValidatedReviewScopeTest {
 final String meeting="00000000-0000-4000-8000-000000000001",element="00000000-0000-4000-8000-000000000002",review="00000000-0000-4000-8000-000000000003",evidence="00000000-0000-4000-8000-000000000004",other="00000000-0000-4000-8000-000000000005";
 final Anchor anchor=OutputValidation.anchor("中文😀",0,3,()->false);
 FullRow row(Table table,Object... values){return new FullRow(table,Arrays.stream(values).map(v->v==null?(Cell)new NullCell():v instanceof Long n?cell(n):cell((String)v)).toList());}
 FullRow review(String id,long number,String outcome){return row(Table.RESULT_REVIEW,id,meeting,number,"SOURCE_SUPPORT",element,null,OutputValidation.anchorText(anchor),"0".repeat(64),"1".repeat(64),"ama.result-review.v2",outcome,"owner","2026-11-05T00:00:00.000000Z");}
 ReviewFacts scope(String target,Anchor selected,List<String> ids,boolean later){var rows=new ArrayList<FullRow>(List.of(review(review,1,"SUPPORTED"),row(Table.RESULT_REVIEW_EVIDENCE,review,evidence)));if(later){rows.add(review(other,2,"UNSUPPORTED"));rows.add(row(Table.RESULT_REVIEW_EVIDENCE,other,evidence));}return new ReviewFacts(meeting,ReviewKind.SOURCE_SUPPORT,target,null,selected,ids,new StoredProvenanceFacts(rows,List.of(),List.of(),List.of(),List.of(),100L));}
 @Test void exactWholeScopeUsesLatestSavedOutcomeEvenWhenNotSupported(){var s=scope(element,anchor,List.of(evidence),true);assertEquals(other,JdbcMinutesSavedQueries.matchingValidatedReview(s,"ama.result-review.v2").orElseThrow().string("id"));assertEquals("UNSUPPORTED",JdbcMinutesSavedQueries.matchingValidatedReview(s,"ama.result-review.v2").orElseThrow().string("outcome"));}
 @Test void newImmutableTargetAndDifferentProfileHaveNoReusableValidation(){assertTrue(JdbcMinutesSavedQueries.matchingValidatedReview(scope(other,anchor,List.of(evidence),false),"ama.result-review.v2").isEmpty());assertTrue(JdbcMinutesSavedQueries.matchingValidatedReview(scope(element,anchor,List.of(evidence),false),"ama.result-review.v3").isEmpty());}
 @Test void partialChangedContentAndEvidenceMembershipDoNotMatch(){assertTrue(JdbcMinutesSavedQueries.matchingValidatedReview(scope(element,OutputValidation.anchor("中文😀",0,2,()->false),List.of(evidence),false),"ama.result-review.v2").isEmpty());assertTrue(JdbcMinutesSavedQueries.matchingValidatedReview(scope(element,OutputValidation.anchor("文字😀",0,3,()->false),List.of(evidence),false),"ama.result-review.v2").isEmpty());for(var ids:List.of(List.<String>of(),List.of(other),List.of(evidence,other)))assertTrue(JdbcMinutesSavedQueries.matchingValidatedReview(scope(element,anchor,ids,false),"ama.result-review.v2").isEmpty());}
 @Test void sourceAndActionReviewsAreSeparateScopes(){var s=scope(element,anchor,List.of(evidence),false);var action=new ReviewFacts(meeting,ReviewKind.ACTION_FIELDS,element,null,null,List.of(evidence),s.stored());assertTrue(JdbcMinutesSavedQueries.matchingValidatedReview(action,"ama.result-review.v2").isEmpty());}
}
