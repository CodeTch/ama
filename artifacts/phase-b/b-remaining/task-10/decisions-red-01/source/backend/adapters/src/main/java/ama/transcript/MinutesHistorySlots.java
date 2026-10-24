package ama.transcript;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** One allocator over independently held actual history indexes; no meeting-r substitution. */
final class MinutesHistorySlots {
 static long next(MinutesGraphCapture.Graph graph,String table,int slot){require(java.util.Set.of("minutes_revision","element_set","exclusion_revision").contains(table)&&slot>0,INVALID_ARGUMENT);var rows=graph.auxiliary().get(table+"_history_max");require(rows!=null&&rows.size()<=1,PROVENANCE_UNAVAILABLE);long old=rows.isEmpty()?0:((Number)rows.getFirst().get("revision")).longValue();require(old>=0&&old<=Long.MAX_VALUE-1-slot,BUDGET_EXCEEDED);return old+slot;}
}
