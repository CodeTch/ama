package ama.transcript;
import ama.contract.MinutesContracts;import ama.task.ObservationRetryRequiredException;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ObservationRetryPolicyTest {
 @Test void finiteAcquisitionFailureCannotKeepAnExpiredPreparedObjectAlive(){assertFalse(ObservationRetryPolicy.retain(MinutesContracts.fail(MinutesContracts.FailureCode.BUDGET_EXCEEDED)));assertFalse(ObservationRetryPolicy.retain(MinutesContracts.fail(MinutesContracts.FailureCode.CANCELLED)));}
 @Test void transactionAndMembershipFailuresKeepTheSameAuthoritativeReceiptUntilItsOwnBudgetFails(){assertTrue(ObservationRetryPolicy.retain(new ObservationRetryRequiredException()));assertTrue(ObservationRetryPolicy.retain(MinutesContracts.fail(MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE)));assertTrue(ObservationRetryPolicy.retain(MinutesContracts.fail(MinutesContracts.FailureCode.READ_FAILED)));assertTrue(ObservationRetryPolicy.retain(new IllegalStateException("database unavailable")));}
}
