package ama.task;

import ama.contract.TaskContracts.TaskSpec;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.task.TaskDomainPolicy.*;

/** Per-call capabilities only: no transaction cache, ThreadLocal or extra synchronization. */
public final class JdbcTaskPreparations {
 public void requireExternalRead(TaskDomainPolicy.PreparedDomain held,ama.contract.TaskContracts.TaskSpec spec){var h=held(held);if(!h.specs.contains(spec))throw new IllegalArgumentException("Undeclared publication execution");}
    private final DataSource source;
    private record Held(JdbcTaskPreparations issuer, DataSource source, Object transaction,
                        Set<TaskSpec> specs, Acquisition purpose, Object value,
                        boolean effectsAllowed) implements PreparedDomain {}
    public JdbcTaskPreparations(DataSource source) { this.source=Objects.requireNonNull(source); }
    /** Trusted registered policy issues only AFTER complete identity acquisition or authentic adoption. */
    public PreparedDomain issue(List<TaskSpec> specs, Acquisition purpose, Object value, boolean effectsAllowed) {
        var set=specs(specs);
        return new Held(this,source,transaction(),set,Objects.requireNonNull(purpose),value,effectsAllowed);
    }
    public void require(List<TaskSpec> specs, Acquisition purpose, PreparedDomain prepared) {
        var held=held(prepared);
        if(held.purpose()!=purpose || !held.specs().equals(specs(specs)))
            throw new IllegalArgumentException("Preparation purpose or complete specs mismatch");
    }
    public Object value(PreparedDomain prepared) { return held(prepared).value(); }
    public boolean permitsEffects(PreparedDomain prepared) { return held(prepared).effectsAllowed(); }
    /** Validated immutable execution facts; this view is data, not a transferable capability. */
    public record ExternalFacts(String taskId, TaskSpec spec, String operationId, String executionId,
            long executionNo, String requestHash, String requestManifest) {}
    public ExternalFacts requireExternal(PreparedDomain prepared, ama.task.ExternalOperationPort.ExecutionContext execution) {
        held(prepared);
        return JdbcExternalOperations.requireContext(source, prepared, execution);
    }
    /** Actual context resolves its own held preparation; issuer validation still applies. */
    public ExternalFacts requireExternal(ama.task.ExternalOperationPort.ExecutionContext execution) {
        var prepared=JdbcExternalOperations.contextPreparation(source,execution);held(prepared);return requireExternal(prepared,execution);
    }
    public record LocalTerminalFacts(String taskId,TaskSpec spec,String previousState,long attempt,String previousOwner,java.time.Instant previousExpiry,String resultingState,String reason,boolean attemptAlreadyEnded) {}
    public LocalTerminalFacts requireLocalTerminal(PreparedDomain prepared,PublicationLocalTerminationPolicy.LocalTerminalContext context,PublicationLocalTerminationPolicy.Phase phase){held(prepared);return JdbcTaskQueue.requireLocalContext(source,prepared,context,phase);}
    /** JDBC queue consumption independently enforces its own source/actual-holder boundary. */
    static void requireTransaction(DataSource expected, PreparedDomain prepared) {
        if(!(prepared instanceof Held held)||held.source()!=expected
                ||!TransactionSynchronizationManager.isActualTransactionActive()
                ||held.transaction()!=TransactionSynchronizationManager.getResource(expected))
            throw new IllegalArgumentException("Preparation belongs to another queue DataSource transaction");
    }
    private Held held(PreparedDomain prepared) {
        if(!(prepared instanceof Held held) || held.issuer()!=this || held.source()!=source
                || held.transaction()!=transaction())
            throw new IllegalArgumentException("Expired or foreign domain preparation");
        return held;
    }
    private static Set<TaskSpec> specs(List<TaskSpec> specs) {
        if(specs==null || specs.isEmpty())throw new IllegalArgumentException("Complete task specs required");
        return Set.copyOf(specs);
    }
    private Object transaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source))
            throw new IllegalStateException("Preparation requires its actual DataSource transaction");
        return TransactionSynchronizationManager.getResource(source);
    }
 public void requirePublicationReplacement(PreparedDomain prepared,PublicationReplacementPolicy.ReplacementContext context,PublicationReplacementPolicy.PreparedPublicationReplacement carrier){held(prepared);JdbcExternalOperations.requirePublicationReplacement(source,prepared,context,carrier);}

}
