package ama.transcript;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class MinutesPendingAdaptiveReadTest {
 @Test void completeMetadataFitsAsBoundedPagesWithoutLargerAuthorityBudget(){var fixture=new PublicationGraphAdaptiveReadTest();var j=new PublicationGraphAdaptiveReadTest.MetadataJdbc(25);var budget=fixture.budget(8192);var q=new MinutesPendingDiscovery.Query("durable_task",List.of("id","value"),List.of("id"),"meeting_id=?",List.of("owned"));var result=MinutesPendingDiscovery.read(j,q,true,budget);assertEquals(25,result.size());for(int n=0;n<25;n++)assertEquals(fixture.id(n+1),result.get(n).get("id"));assertTrue(j.fetched.size()>1);assertTrue(j.fetched.stream().allMatch(n->n<=3));assertTrue(budget.usage()[0]<=8192);}
 @Test void evenOneWorstCaseRowMustFitBeforePayloadTransfer(){var fixture=new PublicationGraphAdaptiveReadTest();var j=new PublicationGraphAdaptiveReadTest.MetadataJdbc(1);var q=new MinutesPendingDiscovery.Query("durable_task",List.of("id","value"),List.of("id"),"meeting_id=?",List.of("owned"));var error=assertThrows(Failure.class,()->MinutesPendingDiscovery.read(j,q,false,fixture.budget(2048)));assertEquals(FailureCode.BUDGET_EXCEEDED,error.code());assertTrue(j.fetched.isEmpty());}
 @Test void PagingNeverTruncatesTheBoundedCompleteTaskUnion(){var fixture=new PublicationGraphAdaptiveReadTest();var j=new PublicationGraphAdaptiveReadTest.MetadataJdbc(33);var q=new MinutesPendingDiscovery.Query("durable_task",List.of("id","value"),List.of("id"),"meeting_id=?",List.of("owned"));var error=assertThrows(Failure.class,()->MinutesPendingDiscovery.read(j,q,false,fixture.budget(8192)));assertEquals(FailureCode.BUDGET_EXCEEDED,error.code());}
}
