package ama.transcript;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class MinutesReviewPresenceTest {
 static final String ELEMENT="00000000-0000-4000-8000-000000000001",OTHER="00000000-0000-4000-8000-000000000002";
 FullRow review(String element,String body,ReviewKind kind){Object[] values={OTHER,OTHER,1L,kind.name(),element,body,null,"0".repeat(64),"0".repeat(64),"ama.result-review.v1",kind==ReviewKind.ACTION_FIELDS?"CONFIRMED":"SUPPORTED","owner","2026-11-05T00:00:00.000000Z"};return new FullRow(Table.RESULT_REVIEW,Arrays.stream(values).map(v->v==null?(Cell)new NullCell():v instanceof Long n?cell(n):cell((String)v)).toList());}
 @Test void newElementHasNoCreditFromOtherTargetsOrReviewKinds(){assertFalse(JdbcMinutesSavedQueries.hasReviewFor(List.of(),ELEMENT,ReviewKind.SOURCE_SUPPORT));assertFalse(JdbcMinutesSavedQueries.hasReviewFor(List.of(review(OTHER,null,ReviewKind.SOURCE_SUPPORT)),ELEMENT,ReviewKind.SOURCE_SUPPORT));assertFalse(JdbcMinutesSavedQueries.hasReviewFor(List.of(review(null,ELEMENT,ReviewKind.SOURCE_SUPPORT)),ELEMENT,ReviewKind.SOURCE_SUPPORT));assertFalse(JdbcMinutesSavedQueries.hasReviewFor(List.of(review(ELEMENT,null,ReviewKind.ACTION_FIELDS)),ELEMENT,ReviewKind.SOURCE_SUPPORT));}
 @Test void existingMatchingReviewStillRequiresFullHashAndCoverageEvaluation(){for(var kind:ReviewKind.values())assertTrue(JdbcMinutesSavedQueries.hasReviewFor(List.of(review(ELEMENT,null,kind)),ELEMENT,kind));}
}
