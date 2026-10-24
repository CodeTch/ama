package ama.config.json;
import ama.contract.ConfigArchiveContracts.*;import ama.contract.MinutesContracts.*;import java.util.*;import java.util.stream.*;import org.junit.jupiter.api.*;
import static ama.config.json.ConfigBindingsTest.*;import static ama.contract.MinutesContracts.FailureCode.*;import static org.junit.jupiter.api.Assertions.*;
class ArchiveBudgetTest {
 static final long[] EXACT={987,3618,4,4,3,295,5,22,64,64,2949,1763,1,3,3};
 static ArchiveReadBudget budget(long[] a){return new ArchiveReadBudget(a[0],a[1],(int)a[2],a[3],(int)a[4],a[5],(int)a[6],(int)a[7],(int)a[8],a[9],a[10],a[11],(int)a[12],a[13],a[14]);}
 @Test void independentExactCumulativeBudgetAcceptsAllFiveRawDocuments()throws Exception{
  var manifest=fixture("manifest");var raw=archives();assertDoesNotThrow(()->service().computeProcessing(manifest,prerequisites(),raw,budget(EXACT),()->false));
 }
 @TestFactory Stream<DynamicTest> eachCumulativeBoundRejectsOneMoreThanItsLimit(){
  return IntStream.range(0,EXACT.length).filter(i->EXACT[i]>1).mapToObj(i->DynamicTest.dynamicTest("budget-dimension-"+i,()->{
   var limits=EXACT.clone();limits[i]--;var manifest=fixture("manifest");var raw=archives();
   assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->service().computeProcessing(manifest,prerequisites(),raw,budget(limits),()->false)).code());
  }));
 }
}
