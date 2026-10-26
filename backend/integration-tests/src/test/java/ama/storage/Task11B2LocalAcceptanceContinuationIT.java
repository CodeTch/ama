package ama.storage;
import org.junit.jupiter.api.Test;
/** Dedicated successor: existing initialized store/grant and same saved final; never initial fixture entry. */
class Task11B2LocalAcceptanceContinuationIT extends Task11B2LocalAcceptanceIT {
 @Override @Test void sameInitialReadyPublicationClosesLocallyAndOriginalReceiptsReplayInFreshJvm()throws Exception {runLocal(true);}
}
