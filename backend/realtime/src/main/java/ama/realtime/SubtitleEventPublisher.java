package ama.realtime;

import ama.transcript.SubtitleReconciler;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class SubtitleEventPublisher {
    private final Consumer<SubtitleReconciler.Event> sink;
    private final List<SubtitleReconciler.Event> events = new ArrayList<>();
    private final Map<String, SubtitleReconciler.Event> accepted = new HashMap<>();
    public SubtitleEventPublisher() { this(event -> {}); }
    public SubtitleEventPublisher(Consumer<SubtitleReconciler.Event> sink) { this.sink = Objects.requireNonNull(sink); }
    public synchronized void publish(SubtitleReconciler.Event event) {
        Objects.requireNonNull(event);
        var prior=accepted.get(event.eventId());
        if(prior!=null){if(!prior.equals(event))throw new IllegalArgumentException("Conflicting subtitle event");return;}
        // A rejected delivery must never become a locally replayable success.
        sink.accept(event);
        events.add(event);accepted.put(event.eventId(),event);
    }
    public synchronized List<SubtitleReconciler.Event> after(String eventId) {
        if(eventId==null)return List.copyOf(events);
        int index=-1;for(int i=0;i<events.size();i++)if(events.get(i).eventId().equals(eventId)){index=i;break;}
        if(index<0)throw new IllegalArgumentException("Unknown subtitle cursor");
        return List.copyOf(events.subList(index+1,events.size()));
    }
}
