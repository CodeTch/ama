package ama.config;
import ama.contract.ConfigArchiveContracts.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** A single operation's cumulative bootstrap accounting, shared by recursive inputs. */
public final class ArchiveReadState {
 public final ArchiveReadBudget budget;public final BooleanSupplier cancelled;private long bytes,tokens,scalars,dependencies,rows,edges;
 public ArchiveReadState(ArchiveReadBudget budget,BooleanSupplier cancelled){require(budget!=null&&cancelled!=null,INVALID_ARGUMENT);this.budget=budget;this.cancelled=cancelled;check(cancelled);}
 public ArchiveBytes capture(byte[] raw,boolean manifest){check(cancelled);require(raw!=null,INVALID_ARGUMENT);var value=ArchiveBytes.capture(raw,manifest?budget.maxManifestBytes():budget.maxArtifactBytes(),budget.maxTotalBytes()-bytes,cancelled);bytes+=raw.length;return value;}
 public void token(){check(cancelled);require(tokens<budget.maxTokens(),BUDGET_EXCEEDED);tokens++;}
 public void scalars(long count){check(cancelled);require(count<=budget.maxAggregateScalars()-scalars,BUDGET_EXCEEDED);scalars+=count;}
 public void dependencies(long count){check(cancelled);require(count<=budget.maxDependencies()-dependencies,BUDGET_EXCEEDED);dependencies+=count;}
 public void rows(long count){check(cancelled);require(count<=budget.maxFactRows()-rows,BUDGET_EXCEEDED);rows+=count;}
 public void edges(long count){check(cancelled);require(count<=budget.maxFactEdges()-edges,BUDGET_EXCEEDED);edges+=count;}
}
