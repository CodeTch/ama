package ama.contract;
import java.util.*;import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;import static org.junit.jupiter.api.Assertions.*;
class MinutesFactBudgetTest {
 @Test void factBagChecksCallerLimitBeforeAnyLargeDefensiveCopy(){
  List<FullRow> huge=new AbstractList<>(){
   @Override public int size(){return Integer.MAX_VALUE;}
   @Override public FullRow get(int index){throw new AssertionError("copy happened before budget");}
   @Override public Object[] toArray(){throw new AssertionError("copy happened before budget");}
  };
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new StoredProvenanceFacts(huge,List.of(),List.of(),List.of(),1)).code());
 }

 @Test void catalogSnapshotAlsoChecksItsBoundBeforeDefensiveCopy(){
  List<CatalogFact> huge=new AbstractList<>(){
   @Override public int size(){return Integer.MAX_VALUE;}
   @Override public CatalogFact get(int index){throw new AssertionError("catalog copy before bound");}
   @Override public Object[] toArray(){throw new AssertionError("catalog copy before bound");}
  };
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new CatalogFacts(huge,1)).code());
 }
}
