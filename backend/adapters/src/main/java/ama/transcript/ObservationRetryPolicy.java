package ama.transcript;
import ama.contract.MinutesContracts;
/** Cache lifetime only. A fresh committed original-receipt GET is required after finite acquisition fails. */
final class ObservationRetryPolicy {
 private ObservationRetryPolicy(){}
 static boolean retain(RuntimeException failure){return !(failure instanceof MinutesContracts.Failure denied&&(denied.code()==MinutesContracts.FailureCode.BUDGET_EXCEEDED||denied.code()==MinutesContracts.FailureCode.CANCELLED));}
}
