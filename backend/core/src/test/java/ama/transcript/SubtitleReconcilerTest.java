package ama.transcript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubtitleReconcilerTest {
    @Test
    void lateTranscriptCannotReviveDeletedMeeting() {
        var reconciler = new SubtitleReconciler();
        reconciler.open("m");
        assertTrue(reconciler.apply(new SubtitleReconciler.Event("e1", "m", "s", 1, "temporary", "非常", 0, 100)));
        assertFalse(reconciler.apply(new SubtitleReconciler.Event("e1", "m", "s", 1, "temporary", "非常", 0, 100)));
        reconciler.delete("m");
        assertFalse(reconciler.apply(new SubtitleReconciler.Event("e2", "m", "s", 2, "stable", "重要", 0, 100)));
        assertEquals(1, reconciler.writes("m"));
    }
}
