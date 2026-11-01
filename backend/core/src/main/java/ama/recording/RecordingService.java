package ama.recording;

import ama.contract.RecordingContracts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Single-writer protocol. Persistent deployments reconstruct proofs from committed storage. */
public final class RecordingService {
    public interface Persistence {
        default String backend(String requested) { return requested; }
        default void open(RecordingContracts.Session session) {}
        default RecordingContracts.Session openOrCurrent(RecordingContracts.Session session) { open(session);return session; }
        void persist(RecordingContracts.Session session, RecordingContracts.Chunk chunk);
        default void pause(RecordingContracts.Session session, boolean paused) {}
        default void nextPart(RecordingContracts.Session previous, RecordingContracts.Session next) {}
        default void finish(RecordingContracts.Session session, Map<String, Long> finalSequences) {}
        default boolean recoverable() { return false; }
        default Snapshot load(String sessionId) { throw new IllegalStateException("Recording recovery unavailable"); }
        /** Same committed facts as load, projected for one append under the session lock. */
        default AppendSnapshot loadForAppend(String sessionId,RecordingContracts.Chunk chunk) {
            var full=load(sessionId);var active=new HashMap<Long,Proof>();
            for(var proof:full.chunks()) {
                if(!full.parts().contains(proof.partId()))throw new IllegalStateException("Unissued recording proof");
                if(proof.partId().equals(full.session().partId())&&active.put(proof.seq(),proof)!=null)throw new IllegalStateException("Duplicate recording proof");
            }
            long highest=0;while(active.containsKey(highest+1))highest++;
            long prefix=highest,seq=chunk.seq();
            var relevant=active.values().stream().filter(p->p.seq()>prefix||p.seq()==seq||p.seq()==seq-1||seq<Long.MAX_VALUE&&p.seq()==seq+1).toList();
            return new AppendSnapshot(full.session(),full.paused(),full.finished(),full.parts(),full.chunks().size(),
                    full.chunks().stream().mapToLong(Proof::sampleCount).sum(),highest,active.keySet().stream().mapToLong(Long::longValue).max().orElse(0),relevant);
        }
        /** Routing only; it conveys no permission or PCM integrity claim. */
        default String meetingId(String sessionId) {return load(sessionId).session().meetingId();}
        /** Caller already holds its meeting gate; bind the actual session before doing any work. */
        default <T> T withinMeeting(String sessionId,String expectedMeeting,Supplier<T> work) {
            return locked(sessionId,()->{if(!expectedMeeting.equals(meetingId(sessionId)))throw new StaleWriter();return work.get();});
        }
        /** JDBC holds a session row in the caller transaction, memory fixtures serialize here. */
        default <T> T locked(String sessionId, Supplier<T> work) { synchronized (this) { return work.get(); } }
    }
    public record Proof(String partId, long seq, long sampleStart, long sampleCount, String sha256) {
        public static Proof of(RecordingContracts.Chunk c) { return new Proof(c.partId(),c.seq(),c.sampleStart(),c.sampleCount(),c.sha256()); }
    }
    public record Snapshot(RecordingContracts.Session session, boolean paused, boolean finished, List<Proof> chunks, List<String> parts) {
        public Snapshot { Objects.requireNonNull(session); chunks=List.copyOf(chunks);parts=List.copyOf(parts);if(!parts.contains(session.partId())||new java.util.HashSet<>(parts).size()!=parts.size())throw new IllegalStateException("Invalid recording parts"); }
        public Snapshot(RecordingContracts.Session session,boolean paused,boolean finished,List<Proof> chunks){this(session,paused,finished,chunks,List.of(session.partId()));}
    }
    /** Persistence verifies the complete totals under its current manifest fence; the collector cannot supply them. */
    public record AppendSnapshot(RecordingContracts.Session session,boolean paused,boolean finished,List<String> parts,
                                 long totalChunks,long totalSamples,long highestContiguousSeq,long highestReceivedSeq,List<Proof> chunks) {
        public AppendSnapshot {
            Objects.requireNonNull(session);parts=List.copyOf(parts);chunks=List.copyOf(chunks);
            if(parts.size()>16||!parts.contains(session.partId())||new java.util.HashSet<>(parts).size()!=parts.size()
                    ||totalChunks<0||totalChunks>RecordingContracts.MAX_SESSION_CHUNKS||totalSamples<0||totalSamples>RecordingContracts.MAX_SESSION_SAMPLES
                    ||highestContiguousSeq<0||highestContiguousSeq>totalChunks||highestReceivedSeq<highestContiguousSeq
                    ||highestReceivedSeq-highestContiguousSeq>512||chunks.size()>515)throw new IllegalStateException("Invalid append manifest");
            var sequences=new java.util.HashSet<Long>();
            for(var proof:chunks)if(!proof.partId().equals(session.partId())||proof.seq()<1||proof.seq()>highestReceivedSeq||!sequences.add(proof.seq()))throw new IllegalStateException("Invalid append proof");
        }
    }
    public static final class StaleWriter extends RuntimeException { public StaleWriter() { super("Stale recording writer"); } }
    public static final class HashConflict extends RuntimeException { public HashConflict() { super("Recording sequence or sample conflict"); } }
    public static final class Gap extends RuntimeException { public Gap() { super("Recording has unpersisted sequence gaps"); } }
    public static final class Paused extends RuntimeException { public Paused() { super("Recording is paused"); } }
    public static final class Closed extends IllegalStateException { public Closed() { super("Recording already finished"); } }
    public record Archive(String sessionId, long finalSequence) {}
    private final Map<String, State> sessions = new HashMap<>();
    private final Persistence persistence;
    public RecordingService() { this((session, chunk) -> {}); }
    public RecordingService(Persistence persistence) { this.persistence = Objects.requireNonNull(persistence); }
    public RecordingContracts.Session open(String meetingId, String backendId) {
        if (meetingId == null || meetingId.isBlank() || backendId == null || backendId.isBlank()) throw new IllegalArgumentException("Invalid recording session");
        var session = new RecordingContracts.Session(UUID.randomUUID().toString(), meetingId, UUID.randomUUID().toString(), 1, persistence.backend(backendId));
        return persistence.locked(null, () -> {
            var actual=persistence.openOrCurrent(session);
            if (!persistence.recoverable()) sessions.put(actual.sessionId(), new State(actual));
            return actual;
        });
    }
    public RecordingContracts.Session session(String sessionId) { return persistence.locked(sessionId, () -> state(sessionId).session); }
    public String meetingId(String sessionId) {return persistence.locked(sessionId,()->persistence.recoverable()?persistence.meetingId(sessionId):state(sessionId).session.meetingId());}
    /** Scope binding only. All permissions remain the transport's current meeting gate. */
    public <T> T withinMeeting(String sessionId,String expectedMeeting,Supplier<T> work) {
        Objects.requireNonNull(expectedMeeting);Objects.requireNonNull(work);
        if(persistence.recoverable())return persistence.withinMeeting(sessionId,expectedMeeting,work);
        return persistence.locked(sessionId,()->{if(!expectedMeeting.equals(state(sessionId).session.meetingId()))throw new StaleWriter();return work.get();});
    }
    public record PartManifest(String partId,long highestContiguousSeq,long highestReceivedSeq,long sampleCount) {}
    public record Manifest(String sessionId,boolean paused,boolean finished,List<PartManifest> parts) { public Manifest{parts=List.copyOf(parts);} }
    public Manifest manifest(String sessionId) { return persistence.locked(sessionId,()->{var s=state(sessionId);var parts=new java.util.ArrayList<PartManifest>();for(var entry:s.parts.entrySet()){var chunks=entry.getValue();parts.add(new PartManifest(entry.getKey(),ack(chunks).highestContiguousSeq(),chunks.keySet().stream().mapToLong(Long::longValue).max().orElse(0),chunks.values().stream().mapToLong(Proof::sampleCount).sum()));}return new Manifest(sessionId,s.paused,s.finished,parts);}); }
    public boolean finished(String sessionId) { return persistence.locked(sessionId, () -> state(sessionId).finished); }
    public void pause(String id, long epoch) { setPaused(id,epoch,true); }
    public void resume(String id, long epoch) { setPaused(id,epoch,false); }
    private void setPaused(String id,long epoch,boolean paused) {
        persistence.locked(id, () -> { var s=state(id,epoch); if(s.finished)throw new Closed(); persistence.pause(s.session,paused);s.paused=paused;return null; });
    }
    public RecordingContracts.Ack accept(String id, long epoch, RecordingContracts.Chunk chunk) {
        Objects.requireNonNull(chunk);
        return persistence.locked(id, () -> {
            if(persistence.recoverable())return acceptRecovered(id,epoch,chunk);
            var s=state(id,epoch); requirePart(s,chunk.partId());
            if(!s.session.partId().equals(chunk.partId()))throw new StaleWriter();
            var chunks=s.parts.get(chunk.partId());
            if(chunk.seq()>ack(chunks).highestContiguousSeq()+512)throw new IllegalArgumentException("Recording sequence outside retry window");
            if (chunk.writerEpoch()!=0 && chunk.writerEpoch()!=epoch) throw new StaleWriter();
            if (s.paused) throw new Paused();
            if (s.finished) throw new Closed();
            var proof=Proof.of(chunk);var old=chunks.get(chunk.seq());
            if (old!=null && !old.equals(proof)) throw new HashConflict();
            if (old==null) {
                long total=s.parts.values().stream().flatMap(p->p.values().stream()).mapToLong(Proof::sampleCount).sum();
                long count=s.parts.values().stream().mapToLong(Map::size).sum();
                if(count>=RecordingContracts.MAX_SESSION_CHUNKS||chunk.sampleCount()>RecordingContracts.MAX_SESSION_SAMPLES-total)
                    throw new IllegalArgumentException("Recording session limit reached");
                var before=chunks.get(chunk.seq()-1);var after=chunks.get(chunk.seq()+1);
                if (chunk.seq()==1 && chunk.sampleStart()!=0
                        || before!=null && before.sampleStart()+before.sampleCount()!=chunk.sampleStart()
                        || after!=null && chunk.sampleStart()+chunk.sampleCount()!=after.sampleStart()) throw new HashConflict();
                persistence.persist(s.session,chunk); chunks.put(chunk.seq(),proof);
            }
            return ack(chunks);
        });
    }
    private RecordingContracts.Ack acceptRecovered(String id,long epoch,RecordingContracts.Chunk chunk) {
        var s=persistence.loadForAppend(id,chunk);
        if(s.session().writerEpoch()!=epoch)throw new StaleWriter();
        if(!s.parts().contains(chunk.partId()))throw new IllegalArgumentException("Recording part was not issued");
        if(!s.session().partId().equals(chunk.partId()))throw new StaleWriter();
        if(chunk.seq()>s.highestContiguousSeq()+512)throw new IllegalArgumentException("Recording sequence outside retry window");
        if(chunk.writerEpoch()!=0&&chunk.writerEpoch()!=epoch)throw new StaleWriter();
        if(s.paused())throw new Paused();if(s.finished())throw new Closed();
        var chunks=new HashMap<Long,Proof>();for(var proof:s.chunks())chunks.put(proof.seq(),proof);
        var proof=Proof.of(chunk);var old=chunks.get(chunk.seq());
        if(old!=null&&!old.equals(proof))throw new HashConflict();
        if(old==null) {
            if(s.totalChunks()>=RecordingContracts.MAX_SESSION_CHUNKS||chunk.sampleCount()>RecordingContracts.MAX_SESSION_SAMPLES-s.totalSamples())throw new IllegalArgumentException("Recording session limit reached");
            var before=chunks.get(chunk.seq()-1);var after=chunk.seq()==Long.MAX_VALUE?null:chunks.get(chunk.seq()+1);
            if(chunk.seq()==1&&chunk.sampleStart()!=0||before!=null&&before.sampleStart()+before.sampleCount()!=chunk.sampleStart()
                    ||after!=null&&chunk.sampleStart()+chunk.sampleCount()!=after.sampleStart())throw new HashConflict();
            persistence.persist(s.session(),chunk);chunks.put(chunk.seq(),proof);
        }
        long highest=s.highestContiguousSeq();while(chunks.containsKey(highest+1))highest++;
        long max=Math.max(s.highestReceivedSeq(),chunk.seq());var missing=new java.util.ArrayList<Long>();
        for(long seq=highest+1;seq<max;seq++)if(!chunks.containsKey(seq))missing.add(seq);
        return new RecordingContracts.Ack(highest,missing);
    }
    public RecordingContracts.Ack ack(String id,String partId) {
        return persistence.locked(id, () -> {var s=state(id);requirePart(s,partId);return ack(s.parts.get(partId));});
    }
    private RecordingContracts.Ack ack(Map<Long,Proof> chunks) {
        long highest=0;while(chunks.containsKey(highest+1))highest++;
        long max=chunks.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
        var missing=new java.util.ArrayList<Long>();
        for(long seq=highest+1;seq<max;seq++)if(!chunks.containsKey(seq))missing.add(seq);
        return new RecordingContracts.Ack(highest,missing);
    }
    public RecordingContracts.Session nextPart(String id,long epoch) {
        return persistence.locked(id,() -> {
            var s=state(id,epoch);if(s.finished)throw new Closed();if(!s.paused)throw new StaleWriter();
            var chunks=s.parts.get(s.session.partId());
            if(ack(chunks).highestContiguousSeq()!=chunks.keySet().stream().mapToLong(Long::longValue).max().orElse(0))throw new Gap();
            if(s.parts.size()>=16)throw new IllegalArgumentException("Recording part limit reached");
            var next=new RecordingContracts.Session(id,s.session.meetingId(),UUID.randomUUID().toString(),Math.addExact(epoch,1),s.session.backendId());
            persistence.nextPart(s.session,next);s.session=next;s.parts.put(next.partId(),new HashMap<>());s.paused=false;return next;
        });
    }
    public Archive finish(String id,long epoch,Map<String,Long> finalSequences) {
        return persistence.locked(id, () -> {
            var s=state(id,epoch);
            if(finalSequences==null||!finalSequences.keySet().equals(s.parts.keySet()))throw new Gap();
            long maximum=0;
            for(var entry:s.parts.entrySet()) {
                Long last=finalSequences.get(entry.getKey());var chunks=entry.getValue();
                long max=chunks.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
                if(last==null||last<0||last!=max||ack(chunks).highestContiguousSeq()!=last)throw new Gap();
                maximum=Math.max(maximum,last);
            }
            if(maximum==0)throw new Gap();
            persistence.finish(s.session,finalSequences);s.finished=true;return new Archive(id,maximum);
        });
    }
    private static void requirePart(State s,String partId) {if(!s.parts.containsKey(partId))throw new IllegalArgumentException("Unissued recording part");}
    private State state(String id,long epoch) {var s=state(id);if(s.session.writerEpoch()!=epoch)throw new StaleWriter();return s;}
    private State state(String id) {
        if(persistence.recoverable()) {
            var snapshot=persistence.load(id);var s=new State(snapshot.session());s.paused=snapshot.paused();s.finished=snapshot.finished();
            for(var part:snapshot.parts())s.parts.putIfAbsent(part,new HashMap<>());
            for(var proof:snapshot.chunks()) {requirePart(s,proof.partId());if(s.parts.get(proof.partId()).put(proof.seq(),proof)!=null)throw new IllegalStateException("Duplicate recording proof");}
            return s;
        }
        var s=sessions.get(id);if(s==null)throw new IllegalArgumentException("Unknown recording session");return s;
    }
    private static final class State {
        RecordingContracts.Session session;final Map<String,Map<Long,Proof>> parts=new java.util.LinkedHashMap<>();boolean finished;boolean paused;
        State(RecordingContracts.Session session){this.session=session;parts.put(session.partId(),new HashMap<>());}
    }
}
