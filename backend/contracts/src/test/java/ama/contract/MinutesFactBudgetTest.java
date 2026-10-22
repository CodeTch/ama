package ama.contract;
import java.util.*;import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;import static org.junit.jupiter.api.Assertions.*;
class MinutesFactBudgetTest {
 @Test void artifactCaptureRefusesSmallMaximumAndRemainingBeforeOwnedBytesExist(){
  byte[] raw={1,2};
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->ConfigArchiveContracts.ArchiveBytes.capture(raw,1,2,()->false)).code());
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->ConfigArchiveContracts.ArchiveBytes.capture(raw,2,1,()->false)).code());
 }
 @Test void artifactReusesBoundedIsolatedCaptureWithoutExposingMutableArray(){
  byte[] raw={1,2};var owned=ConfigArchiveContracts.ArchiveBytes.capture(raw,2,2,()->false);
  var artifact=new Artifact("synthetic",owned.sha256(()->false),owned);raw[0]=9;
  assertSame(owned,artifact.bytes());assertEquals(2,artifact.bytes().size());
  assertEquals("a12871fee210fb8619291eaea194581cbd2531e4b23759d225f6806923f63222",artifact.bytes().sha256(()->false));
  assertTrue(artifact.bytes().sameBytes(new byte[]{1,2}));
  assertEquals(CANCELLED,assertThrows(Failure.class,()->ConfigArchiveContracts.ArchiveBytes.capture(raw,2,2,()->true)).code());
 }
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
