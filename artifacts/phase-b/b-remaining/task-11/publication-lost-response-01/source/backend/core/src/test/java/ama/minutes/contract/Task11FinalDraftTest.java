package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.minutes.contract.StoredSourceGraphTest.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

/** Retains the exact existing final golden and exercises prospective identity without fake rows. */
class Task11FinalDraftTest extends FinalAndCoverageTest {
 static FinalDraft draft(StoredProvenanceFacts facts,List<String> reviews){return new FinalDraft(id(20),id(1),1,id(21),id(22),id(7),id(13),null,id(11),id(9),id(10),H,"human",T,reviews,facts);}
 @Test void draftWithoutFinalRowsEqualsOriginalPersistedGoldenProjection(){
  var stored=finalFacts();var rows=stored.rows().stream().filter(r->r.table()!=Table.FINAL_CONFIRMATION&&r.table()!=Table.FINAL_RESULT_REVIEW).toList();
  var actual=new StoredProvenanceFacts(rows,stored.files(),stored.configurations(),stored.evidenceAnchors(),100000L);
  assertTrue(actual.rows().stream().noneMatch(r->r.table()==Table.FINAL_CONFIRMATION));
  var profiles=new ResultHashProfiles();assertEquals(profiles.finalData(new FinalFacts(id(20),stored),BUDGET,()->false),profiles.finalData(draft(actual,List.of()),BUDGET,()->false));
 }
 @Test void prospectiveMembershipCannotInventOrDuplicateStoredReview(){
  var actual=finalFacts();fails(SOURCE_MEMBERSHIP_MISMATCH,()->draft(actual,List.of(id(40),id(40))));
  fails(PROVENANCE_UNAVAILABLE,()->new ResultHashProfiles().finalData(draft(actual,List.of(id(40))),BUDGET,()->false));
 }
}
