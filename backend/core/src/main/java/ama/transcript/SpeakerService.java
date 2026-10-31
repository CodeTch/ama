package ama.transcript;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Speaker labels and ACL projection; merging labels never removes access grants. */
public final class SpeakerService {
    private final Map<String, Set<String>> access = new HashMap<>();
    public synchronized void add(String speakerId, Set<String> enterpriseIds) {
        if (speakerId == null || speakerId.isBlank() || enterpriseIds == null) throw new IllegalArgumentException("Invalid speaker");
        access.putIfAbsent(speakerId, new HashSet<>(enterpriseIds));
    }
    public synchronized void merge(String sourceSpeakerId, String targetSpeakerId) {
        if (!access.containsKey(sourceSpeakerId) || !access.containsKey(targetSpeakerId)) throw new IllegalArgumentException("Unknown speaker");
        access.get(targetSpeakerId).addAll(access.get(sourceSpeakerId));
        access.remove(sourceSpeakerId);
    }
    public synchronized Set<String> access(String speakerId) { return Set.copyOf(access.getOrDefault(speakerId, Set.of())); }
}
