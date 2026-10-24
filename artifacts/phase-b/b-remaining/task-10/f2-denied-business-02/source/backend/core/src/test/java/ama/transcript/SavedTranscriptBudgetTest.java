package ama.transcript;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.MinutesContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.FailureCode.*;
class SavedTranscriptBudgetTest {
 static final ArchiveReadBudget A=new ArchiveReadBudget(65536,1000,4,20,8,10000,32,255,1000,1000,1000,65536,4,100,1000);
 static final CanonicalBudget C=new CanonicalBudget(10000,32,10000,1000,1000);
 @Test void supplementaryUtf16AccumulatesAcrossAcquisitionsBeforeTransfer(){
  var l=new TranscriptAcquisitionBudget(100,100,3,100,100,100,8,8,4).ledger(A,C,()->false);
  assertDoesNotThrow(()->l.text(4,1,2,1,false));
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->l.text(4,1,2,1,false)).code());
 }
 @Test void copyAndEscapedResponseReservationsPrecedeAllocation(){
  var l=new TranscriptAcquisitionBudget(100,100,100,7,100,100,8,8,4).ledger(A,C,()->false);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->l.text(4,1,2,2,false)).code());
  var r=new TranscriptAcquisitionBudget(100,100,100,100,100,11,8,8,4).ledger(A,C,()->false);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->r.text(4,1,2,1,true)).code());
 }
 @Test void explicitDisabledDeclarationIsNotAnInstalledZeroPendingReader(){
  var d=new CorrectionInstallation.Declaration("DISABLED_NOT_INSTALLED","1","local-b-task6-v1");
  var installed=CorrectionInstallation.compose(d,List.of());
  assertEquals(new CorrectionInstallation.Projection("DISABLED_NOT_INSTALLED","NOT_APPLICABLE","local-b-task6-v1"),installed.projection());
  assertDoesNotThrow(installed::requireReady);
  for(var denied:List.of(CorrectionInstallation.compose(null,List.of()),CorrectionInstallation.compose(d,List.of("actual-provider")),CorrectionInstallation.compose(new CorrectionInstallation.Declaration("ENABLED","1","x"),List.of())))
   assertEquals(CAPABILITY_UNAVAILABLE,assertThrows(Failure.class,denied::requireReady).code());
  assertThrows(Failure.class,()->installed.requireSame(CorrectionInstallation.compose(d,List.of())));
 }
 @Test void canonicalEncodedNodesAndContainersRejectBeforeRowOrTextAllocation(){
  var limits=new TranscriptAcquisitionBudget(1000,1000,1000,10000,10000,10000,8,8,4);
  assertThrows(Failure.class,()->limits.ledger(A,new CanonicalBudget(10,32,10000,1000,1000),()->false).text(11,11,11,1,false));
  assertThrows(Failure.class,()->limits.ledger(A,new CanonicalBudget(10000,32,3,1000,1000),()->false).row(2));
  assertThrows(Failure.class,()->limits.ledger(A,new CanonicalBudget(10000,32,10000,1000,1),()->false).row(2));
 }
 @Test void futureDomainsCannotBecomeInstalledThroughAnEmptyNoOp(){
  var disabled=new SavedTranscriptTransitions("DISABLED_NOT_INSTALLED","DISABLED_NOT_INSTALLED","1",List.of(),List.of());assertDoesNotThrow(disabled::requireAbsent);
  for(var blocked:List.of(new SavedTranscriptTransitions(null,null,null,List.of(),List.of()),new SavedTranscriptTransitions("ENABLED","DISABLED_NOT_INSTALLED","1",List.of(),List.of()),new SavedTranscriptTransitions("DISABLED_NOT_INSTALLED","DISABLED_NOT_INSTALLED","1",List.of("real-candidate-provider"),List.of()),new SavedTranscriptTransitions("DISABLED_NOT_INSTALLED","DISABLED_NOT_INSTALLED","1",List.of(),List.of("real-publication-provider"))))assertThrows(Failure.class,blocked::requireAbsent);
 }
}
