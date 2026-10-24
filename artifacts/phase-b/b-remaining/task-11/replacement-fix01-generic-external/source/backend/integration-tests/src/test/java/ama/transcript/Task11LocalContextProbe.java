package ama.transcript;
import ama.task.*;import ama.contract.TaskContracts.TaskSpec;import static org.junit.jupiter.api.Assertions.*;
/** Negative authority tests using the actual installed issuer/preparation, with no selected writer. */
public final class Task11LocalContextProbe {
 public static int verify(JdbcTranscriptAuthority authority,TaskSpec spec){var policy=authority.publicationDomain();var fake=new PublicationLocalTerminationPolicy.LocalTerminalContext(){};var held=authority.transactionRunner().required(()->{var actual=policy.prepare(java.util.List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,false).orElseThrow();assertThrows(IllegalArgumentException.class,()->policy.validateLocalTerminal(actual,fake));assertThrows(IllegalArgumentException.class,()->policy.sealLocalTerminal(actual,fake));return actual;});authority.transactionRunner().required(()->{assertThrows(IllegalArgumentException.class,()->policy.validateLocalTerminal(held,fake));return null;});return 3;}
}
