package ama.transcript;
import ama.config.ConfigArchiveSource;import ama.contract.MinutesContracts.*;import ama.storage.FileRegistry;import java.util.*;import org.junit.jupiter.api.Test;import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
/** Actual initial command dispatch: bounded discovery must happen before any held root or archive access. */
class Task11InitialSettingsAdmissionTest {
 @Test void initialCommandUsesSelectedAcquisitionBoundsAtDiscovery()throws Exception{
  var f=new Task11PublicationWriteGuardBoundaryTest.Fixture();var registry=mock(FileRegistry.class);when(f.authority.fileRegistry()).thenReturn(registry);when(f.authority.transactionRunner()).thenReturn(f.tx);var archives=mock(java.util.function.Supplier.class);String m=Task11PublicationWriteGuardBoundaryTest.MEETING;
  try(var receipts=mockConstruction(JdbcMinutesSettingsReceipts.class);var discovery=mockStatic(MinutesGraphDiscovery.class,call->{if(call.getMethod().getName().equals("discover"))throw ama.contract.MinutesContracts.fail(FailureCode.BUDGET_EXCEEDED);return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);})){var command=new JdbcMinutesInitialSettings(f.authority,archives,Task11CombinedInstallationTest.PROMPT,Task11CombinedInstallationTest.MODEL,Task11CombinedInstallationTest.LIMITS,mock(ama.task.SubjectFencePort.class),mock(ama.task.OutboxPort.class),mock(ama.audit.AuditPort.class));assertThrows(Failure.class,()->command.initialize(Task11PublicationWriteGuardBoundaryTest.ACTOR,m,"initial-key",1,m,m));discovery.verify(()->MinutesGraphDiscovery.discover(f.ds,m,"INITIAL_SETTINGS",m,Task11CombinedInstallationTest.LIMITS,f.authority.readBudget()));verifyNoInteractions(archives);assertTrue(f.updates.isEmpty());}
 }
}
