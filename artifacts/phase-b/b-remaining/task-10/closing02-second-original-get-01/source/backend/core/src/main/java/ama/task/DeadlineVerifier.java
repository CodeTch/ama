package ama.task;
import ama.contract.ExternalContracts.OperationToken;
import java.time.Instant;
/** Domain adapter verifies server-enforced absolute cutoff/fencing evidence. No network I/O here. */
@FunctionalInterface public interface DeadlineVerifier { void verify(OperationToken token,String model,Instant deadline); }
