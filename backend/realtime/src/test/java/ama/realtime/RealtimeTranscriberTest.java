package ama.realtime;

import ama.contract.SpeechContracts;
import ama.transcript.SubtitleReconciler;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RealtimeTranscriberTest {
    @Test void failedSubtitleDeliveryNeverAppearsInReplayAndOriginalEventCanRetry(){
        var fail=new java.util.concurrent.atomic.AtomicBoolean(true);
        var events=new SubtitleEventPublisher(event->{if(fail.get())throw new IllegalStateException("controlled delivery failure");});
        var event=new SubtitleReconciler.Event("e1","m","s1",1,"temporary","original",0,1000);
        assertThrows(IllegalStateException.class,()->events.publish(event));assertTrue(events.after(null).isEmpty());
        fail.set(false);events.publish(event);assertEquals(List.of(event),events.after(null));
    }
    @Test void exactEventReplayIsIdempotentAndChangedDuplicateCannotReplaceOriginal(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();var events=new SubtitleEventPublisher(event->calls.incrementAndGet());
        var event=new SubtitleReconciler.Event("e1","m","s1",1,"temporary","original",0,1000);
        events.publish(event);events.publish(event);assertEquals(1,calls.get());assertEquals(List.of(event),events.after(null));
        var changed=new SubtitleReconciler.Event("e1","foreign","s1",1,"stable","different",0,1000);
        assertThrows(IllegalArgumentException.class,()->events.publish(changed));assertEquals(1,calls.get());assertEquals(List.of(event),events.after(null));
        assertThrows(IllegalArgumentException.class,()->events.after("unknown-cursor"));
    }
    @Test
    void duplicateSubmissionPublishesOnce() {
        var sink = new SubtitleReconciler(); sink.open("m");
        var transcriber = new RealtimeTranscriber(sink);
        var clip = new SpeechContracts.WavClip("f", 0, 10);
        assertTrue(transcriber.submit("m", "p", 1, clip, "hello"));
        assertFalse(transcriber.submit("m", "p", 1, clip, "hello"));
        assertEquals(1, sink.writes("m"));
    }

    @Test
    void eventPublisherReplaysAfterCursorWithoutDuplicatingTheCursor() {
        var events = new SubtitleEventPublisher();
        var first = new SubtitleReconciler.Event("e1", "m", "s1", 1, "temporary", "a", 0, 1);
        var second = new SubtitleReconciler.Event("e2", "m", "s2", 1, "stable", "b", 1, 2);
        events.publish(first); events.publish(second);
        assertEquals(java.util.List.of(second), events.after("e1"));
    }
}
