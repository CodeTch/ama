package ama.storage;
import org.junit.jupiter.api.Test;
/** One exact Host continuation; all three consumed windows remain immutable. */
class Task11B2RejectedTransportHostContinuationIT extends Task11B2RejectedTransportIT {
 @Override @Test void genuineRejectedReceiptTransportIsBoundedAndReadOnly()throws Exception {
  runMatrix(Task11B2RuntimeFixture.inspectRejectedTransportHostInputs());
 }
}
