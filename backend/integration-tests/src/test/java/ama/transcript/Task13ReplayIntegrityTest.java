package ama.transcript;
import ama.contract.MinutesContracts.*;
import ama.contract.ProcessingContracts.ImportRequest;
import ama.access.MeetingAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Actual intake catch/lookup path and production classifiers; JDBC rows/authorization are substitutes. */
class Task13ReplayIntegrityTest {
 @Test void mixedProfileAndProvenanceAreNotOrdinaryWinnerRecovery()throws Exception {
  var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
  for(var entry:Map.of(JdbcSavedTranscripts.class,"ordinaryPreparationFailure",JdbcMinutesInitialSettings.class,"ordinary",JdbcMinutesGenerationCommands.class,"ordinary").entrySet()){
   var method=entry.getKey().getDeclaredMethod(entry.getValue(),RuntimeException.class);method.setAccessible(true);
   for(var code:List.of(FailureCode.PROFILE_UNAVAILABLE,FailureCode.PROVENANCE_UNAVAILABLE,FailureCode.FINGERPRINT_MISMATCH,FailureCode.CANCELLED))checks.add(()->assertEquals(false,method.invoke(null,new Failure(code)),entry.getKey().getSimpleName()+" "+code));
   for(var code:List.of(FailureCode.ARCHIVE_IO_UNAVAILABLE,FailureCode.BUDGET_EXCEEDED))checks.add(()->assertEquals(true,method.invoke(null,new Failure(code)),entry.getKey().getSimpleName()+" "+code));
  }
  assertAll(checks);
 }
 static final class Case {
  final Task11PublicationWriteGuardBoundaryTest.Fixture f=new Task11PublicationWriteGuardBoundaryTest.Fixture();
  final JdbcProcessingIntake intake;final RuntimeException failure;
  Case(RuntimeException failure)throws Exception {this.failure=failure;intake=new JdbcProcessingIntake(f.ds,f.tx,f.access,null,null,null,null,null,(a,m,r)->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());throw failure;});}
  void accept(){intake.accept(Task11PublicationWriteGuardBoundaryTest.ACTOR,Task11PublicationWriteGuardBoundaryTest.MEETING,"integrity-boundary",new ImportRequest(0,List.of("12345678-1234-4234-8234-123456789ab2")),"12345678-1234-4234-8234-123456789ab3");}
  long lookups(){return f.sql.stream().filter(q->q.startsWith("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record")).count();}
 }
 @Test void intakeIntegrityFailureCannotPerformASecondReceiptLookup()throws Exception {
  var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
  for(var code:List.of(FailureCode.PROFILE_UNAVAILABLE,FailureCode.PROVENANCE_UNAVAILABLE,FailureCode.FINGERPRINT_MISMATCH,FailureCode.CANCELLED))checks.add(()->{var error=new Failure(code);var c=new Case(error);var actual=assertThrows(RuntimeException.class,c::accept);assertAll(()->assertSame(error,actual),()->assertEquals(1,c.lookups()),()->assertTrue(c.f.updates.isEmpty()));});
  assertAll(checks);
 }
 @Test void ordinaryArchiveIoStillRechecksWinnerAndFreshAuthorization()throws Exception {
  var c=new Case(new Failure(FailureCode.ARCHIVE_IO_UNAVAILABLE));var error=assertThrows(ama.contract.ProcessingContracts.Failure.class,c::accept);assertEquals("PROCESSING_UNAVAILABLE",error.code());assertEquals(2,c.lookups());verify(c.f.access,times(2)).require(c.f.root,MeetingAccess.Action.EDIT);assertTrue(c.f.updates.isEmpty());
 }
 @Test void deniedFreshAuthorizationCannotYieldAReceipt()throws Exception {
  var c=new Case(new Failure(FailureCode.ARCHIVE_IO_UNAVAILABLE));doNothing().doThrow(new MeetingAccess.AccessDeniedException()).when(c.f.access).require(c.f.root,MeetingAccess.Action.EDIT);assertThrows(MeetingAccess.AccessDeniedException.class,c::accept);assertEquals(2,c.lookups());assertTrue(c.f.updates.isEmpty());
 }
 @Test void interruptedPreparationCannotEnterWinnerRecovery()throws Exception {
  var error=new Failure(FailureCode.ARCHIVE_IO_UNAVAILABLE);var c=new Case(error);var intake=new JdbcProcessingIntake(c.f.ds,c.f.tx,c.f.access,null,null,null,null,null,(a,m,r)->{Thread.currentThread().interrupt();throw error;});
  try{assertSame(error,assertThrows(Failure.class,()->intake.accept(Task11PublicationWriteGuardBoundaryTest.ACTOR,Task11PublicationWriteGuardBoundaryTest.MEETING,"interrupted",new ImportRequest(0,List.of("12345678-1234-4234-8234-123456789ab2")),"12345678-1234-4234-8234-123456789ab3")));assertTrue(Thread.currentThread().isInterrupted());assertEquals(1,c.lookups());}finally{Thread.interrupted();}
 }
}
