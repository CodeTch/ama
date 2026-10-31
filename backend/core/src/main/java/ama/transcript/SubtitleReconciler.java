package ama.transcript;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies live subtitle revisions only while the meeting is live and ignores duplicate event ids. */
public final class SubtitleReconciler {
    public record Event(String eventId, String meetingId, String segmentId, long revision, String status, String text, long startMs, long endMs) {
        public Event { if (eventId == null || meetingId == null || segmentId == null || revision < 1 || text == null || startMs < 0 || endMs < startMs || !Set.of("temporary", "stable").contains(status)) throw new IllegalArgumentException("Invalid subtitle event"); }
    }
    private final Set<String> live = new HashSet<>();
    private final Set<String> events = new HashSet<>();
    private final Map<String, Map<String, Event>> segments = new HashMap<>();
    private final Map<String, Integer> writes = new HashMap<>();
    public synchronized void open(String meetingId) { live.add(meetingId); segments.putIfAbsent(meetingId, new HashMap<>()); }
    public synchronized void delete(String meetingId) { live.remove(meetingId); }
    public synchronized boolean apply(Event event) {
        if (!live.contains(event.meetingId()) || !events.add(event.eventId())) return false;
        var current = segments.computeIfAbsent(event.meetingId(), ignored -> new HashMap<>()).get(event.segmentId());
        if (current != null && current.revision() >= event.revision()) return false;
        segments.get(event.meetingId()).put(event.segmentId(), event);
        writes.merge(event.meetingId(), 1, Integer::sum);
        return true;
    }
    public synchronized int writes(String meetingId) { return writes.getOrDefault(meetingId, 0); }
}
