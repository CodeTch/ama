package ama.storage;
import org.junit.jupiter.api.Test;
/** Exactly one overridden test; both consumed zero-wire windows remain immutable. */
class Task11B2RejectedTransportManifestContinuationIT extends Task11B2RejectedTransportIT {
 @Override @Test void genuineRejectedReceiptTransportIsBoundedAndReadOnly()throws Exception {
  runMatrix(Task11B2RuntimeFixture.inspectRejectedTransportManifestInputs());
 }
}
