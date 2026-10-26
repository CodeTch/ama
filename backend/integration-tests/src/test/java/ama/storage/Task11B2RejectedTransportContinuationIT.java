package ama.storage;

import org.junit.jupiter.api.Test;

/** One overridden test: retained zero-GET setup-error continuation, never inherited double execution. */
class Task11B2RejectedTransportContinuationIT extends Task11B2RejectedTransportIT {
 @Override @Test void genuineRejectedReceiptTransportIsBoundedAndReadOnly()throws Exception {
  runMatrix(Task11B2RuntimeFixture.inspectRejectedTransportContinuationInputs());
 }
}
