package ama.storage;
import org.junit.jupiter.api.Test;
class Task06FixHandlesIT extends Task06FixFixture {
 @Test void completeSavedHandleCannotCrossIssuerGateHolderOrEnd(){try{String working=saved(raw());ama.transcript.Task06SavedHandleProbe.verify(ama.api.Task05RuntimeProbe.components(app).authority(),ama.api.Task05RuntimeProbe.components(app).authority(),actor(),meeting,working);}catch(Exception e){throw new RuntimeException(e);}}
}
