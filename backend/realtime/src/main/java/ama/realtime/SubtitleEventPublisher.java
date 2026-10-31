package ama.realtime;

import ama.transcript.SubtitleReconciler;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SubtitleEventPublisher {
    private final Consumer<SubtitleReconciler.Event> sink;
    private final List<SubtitleReconciler.Event> events = new ArrayList<>();
    public SubtitleEventPublisher() { this(event -> {}); }
    public SubtitleEventPublisher(Consumer<SubtitleReconciler.Event> sink) { this.sink = Objects.requireNonNull(sink); }
    public synchronized void publish(SubtitleReconciler.Event event) { event=Objects.requireNonNull(event);events.add(event);sink.accept(event); }
    public synchronized List<SubtitleReconciler.Event> after(String eventId) { if(eventId==null)return List.copyOf(events);int index=-1;for(int i=0;i<events.size();i++)if(events.get(i).eventId().equals(eventId)){index=i;break;}return index<0?List.of():List.copyOf(events.subList(index+1,events.size())); }
}
