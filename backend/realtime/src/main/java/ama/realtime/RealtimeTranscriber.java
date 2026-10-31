package ama.realtime;

import ama.contract.SpeechContracts;
import ama.transcript.SubtitleReconciler;
import java.util.Objects;

/** Short-window realtime facade; persistence remains the subtitle reconciler's source of truth. */
public final class RealtimeTranscriber {
    private final SubtitleReconciler reconciler;
    public RealtimeTranscriber(SubtitleReconciler reconciler) { this.reconciler = Objects.requireNonNull(reconciler); }
    public boolean submit(String sessionId, String partId, long seq, SpeechContracts.WavClip clip, String text) {
        var eventId = sessionId + ":" + partId + ":" + seq;
        return reconciler.apply(new SubtitleReconciler.Event(eventId, sessionId, partId, seq, "temporary", text, clip.startSample(), clip.endSampleExclusive()));
    }
}
