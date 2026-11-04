package ama.recording;

import ama.contract.RecordingContracts;
import ama.db.TransactionRunner;
import ama.storage.FileBackendCatalog;
import ama.contract.StorageContracts.StorageKey;
import java.io.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Durable PCM precedes the committed manifest; current database fences validate reused metadata. */
public final class JdbcRecordingPersistence implements RecordingService.Persistence {
    private final JdbcTemplate jdbc;
    private final TransactionRunner transactions;
    private final FileBackendCatalog backends;
    private record Verified(RecordingContracts.Session session,long generation,long chunks,long samples) {}
    // A process-local verification is never sufficient: every reuse also locks and reads the
    // database generation, which every chunk mutation advances in the same transaction.
    private final Map<String,Verified> verified=java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Verified> entry){return size()>1024;}
    });
    private final class VerificationTransaction implements TransactionSynchronization {
        final JdbcRecordingPersistence owner=JdbcRecordingPersistence.this;
        final Map<String,Verified> values=new java.util.HashMap<>();
        @Override public void afterCommit(){verified.putAll(values);}
    }
    private VerificationTransaction verificationTransaction(boolean create){
        if(!TransactionSynchronizationManager.isSynchronizationActive())return null;
        for(var synchronization:TransactionSynchronizationManager.getSynchronizations())
            if(synchronization instanceof JdbcRecordingPersistence.VerificationTransaction candidate&&candidate.owner==this)return candidate;
        if(!create)return null;var value=new VerificationTransaction();TransactionSynchronizationManager.registerSynchronization(value);return value;
    }
    /** Explicit database-PCM fixture/legacy mode; deployed configuration supplies the file catalog. */
    public JdbcRecordingPersistence(DataSource source) { this(source,null); }
    public JdbcRecordingPersistence(DataSource source,FileBackendCatalog backends) {
        jdbc=new JdbcTemplate(Objects.requireNonNull(source));jdbc.setQueryTimeout(5);transactions=new TransactionRunner(source,10);this.backends=backends;
    }
    @Override public String backend(String requested){return backends==null?requested:backends.defaultWrite().id();}
    @Override public boolean recoverable(){return true;}
    @Override public String meetingId(String id) {
        var rows=jdbc.queryForList("SELECT meeting_id FROM recording_session WHERE session_id=?",String.class,id);
        if(rows.size()!=1)throw new IllegalArgumentException("Unknown recording session");return rows.getFirst();
    }
    @Override public <T> T withinMeeting(String id,String expectedMeeting,Supplier<T> work) {
        return transactions.required(()->{
            // Preserve admission-before-session ordering used by new sessions and writer leases.
            jdbc.queryForList("SELECT meeting_id FROM recording_meeting_capture WHERE meeting_id=? FOR UPDATE",String.class,expectedMeeting);
            return RecordingService.Persistence.super.withinMeeting(id,expectedMeeting,work);
        });
    }
    @Override public <T> T locked(String id,Supplier<T> work) {
        return transactions.required(() -> {
            if(id!=null)jdbc.query("SELECT session_id FROM recording_session WHERE session_id=? FOR UPDATE",(r,n)->r.getString(1),id);
            return work.get();
        });
    }
    @Override public void open(RecordingContracts.Session s) {
        jdbc.update("INSERT INTO recording_session(session_id,meeting_id,part_id,writer_epoch,backend_id,state,created_at) VALUES (?,?,?,?,?,'OPEN',UTC_TIMESTAMP(6))",
                s.sessionId(),s.meetingId(),s.partId(),s.writerEpoch(),s.backendId());
        jdbc.update("INSERT INTO recording_part(session_id,part_id,part_no,created_at) VALUES (?,?,1,UTC_TIMESTAMP(6))",s.sessionId(),s.partId());
    }
    @Override public RecordingContracts.Session openOrCurrent(RecordingContracts.Session proposed) {
        jdbc.update("INSERT INTO recording_meeting_capture(meeting_id) VALUES (?) ON DUPLICATE KEY UPDATE meeting_id=meeting_id",proposed.meetingId());
        var pointer=jdbc.queryForObject("SELECT session_id FROM recording_meeting_capture WHERE meeting_id=? FOR UPDATE",String.class,proposed.meetingId());
        if(pointer!=null){var current=load(pointer);if(!current.session().meetingId().equals(proposed.meetingId()))throw new IllegalStateException("Recording admission provenance");if(!current.finished())return current.session();}
        else {
            var active=jdbc.queryForList("SELECT session_id FROM recording_session WHERE meeting_id=? AND state IN ('OPEN','PAUSED') ORDER BY session_id LIMIT 2",String.class,proposed.meetingId());
            if(active.size()>1)throw new RecordingService.StaleWriter();
            if(active.size()==1){var current=load(active.getFirst());jdbc.update("UPDATE recording_meeting_capture SET session_id=? WHERE meeting_id=?",current.session().sessionId(),proposed.meetingId());return current.session();}
        }
        open(proposed);jdbc.update("UPDATE recording_meeting_capture SET session_id=? WHERE meeting_id=?",proposed.sessionId(),proposed.meetingId());return proposed;
    }
    @Override public String currentSessionId(String meeting) {
        var pointers=jdbc.queryForList("SELECT session_id FROM recording_meeting_capture WHERE meeting_id=?",String.class,meeting);
        if(!pointers.isEmpty()&&pointers.getFirst()!=null)return pointers.getFirst();
        var legacy=jdbc.queryForList("SELECT session_id FROM recording_session WHERE meeting_id=? ORDER BY created_at DESC LIMIT 2",String.class,meeting);
        if(legacy.size()>1)throw new RecordingService.StaleWriter();return legacy.isEmpty()?null:legacy.getFirst();
    }
    @Override public RecordingService.Snapshot load(String id) {
        var sessions=jdbc.query("SELECT session_id,meeting_id,part_id,writer_epoch,backend_id,state FROM recording_session WHERE session_id=?",
                (r,n)->new RecordingService.Snapshot(new RecordingContracts.Session(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5)),r.getString(6).equals("PAUSED"),r.getString(6).equals("FINISHED"),java.util.List.of()),id);
        if(sessions.size()!=1)throw new IllegalArgumentException("Unknown recording session");
        var s=sessions.getFirst();
        var proofs=jdbc.query("SELECT part_id,seq,sample_start,sample_count,sha256,bytes,OCTET_LENGTH(pcm),pcm_storage_key FROM recording_chunk WHERE session_id=? ORDER BY seq LIMIT 4097",(r,n)->{
            long count=r.getLong(4),length=r.getLong(7);boolean missing=r.wasNull();String key=r.getString(8);
            if(count<1||count>RecordingContracts.MAX_PCM_BYTES/2||r.getLong(6)!=count*2
                    ||missing&&(key==null||!key.equals(objectKey(id,r.getString(1),r.getLong(2))))
                    ||!missing&&(length!=count*2||key!=null))throw new IllegalStateException("Recording PCM missing or invalid");
            return new RecordingService.Proof(r.getString(1),r.getLong(2),r.getLong(3),count,r.getString(5));
        },id);
        var parts=jdbc.query("SELECT part_id FROM recording_part WHERE session_id=? ORDER BY part_no LIMIT 17",(r,n)->r.getString(1),id);
        if(proofs.size()>RecordingContracts.MAX_SESSION_CHUNKS||parts.size()>16||proofs.stream().mapToLong(RecordingService.Proof::sampleCount).sum()>RecordingContracts.MAX_SESSION_SAMPLES)throw new IllegalStateException("Recording manifest exceeds session limits");
        return new RecordingService.Snapshot(s.session(),s.paused(),s.finished(),proofs,parts);
    }
    /** Detached routing facts only. They prove neither audio completeness nor write permission. */
    public record SessionMetadata(RecordingContracts.Session session,String state) {
        public SessionMetadata {Objects.requireNonNull(session);if(!java.util.Set.of("OPEN","PAUSED","FINISHED").contains(state))throw new IllegalStateException("Invalid recording state");}
    }
    public SessionMetadata sessionMetadata(String id) {
        return sessionRow(id,false).metadata();
    }
    private record SessionRow(SessionMetadata metadata,long generation) {}
    private SessionRow sessionRow(String id,boolean currentAppend) {
        // Routing happens before identity/meeting acquisition. Only the append path has
        // already acquired the session in the correct order and may force a current read.
        var rows=jdbc.query("SELECT session_id,meeting_id,part_id,writer_epoch,backend_id,state,manifest_generation FROM recording_session WHERE session_id=?"+(currentAppend?" FOR SHARE":""),(r,n)->new SessionRow(new SessionMetadata(new RecordingContracts.Session(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5)),r.getString(6)),r.getLong(7)),id);
        if(rows.size()!=1)throw new IllegalArgumentException("Unknown recording session");return rows.getFirst();
    }
    @Override public RecordingService.AppendSnapshot loadForAppend(String id,RecordingContracts.Chunk chunk) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                ||!org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("Append projection requires the locked session transaction");
        var row=sessionRow(id,true);var metadata=row.metadata();
        var parts=jdbc.queryForList("SELECT part_id FROM recording_part WHERE session_id=? ORDER BY part_no LIMIT 17 FOR SHARE",String.class,id);
        if(parts.size()>16||!parts.contains(metadata.session().partId()))throw new IllegalStateException("Invalid recording parts");
        long generation=row.generation();var previous=verified.get(id);
        boolean reusable=parts.size()==1&&previous!=null&&previous.generation()==generation&&previous.session().equals(metadata.session());
        var parameters=new java.util.ArrayList<Object>(parts);parameters.add(id);
        // Establish a complete current-read proof after restart/eviction or ANY chunk mutation.
        // The generation lock excludes mutations while a matching proof is used. In particular,
        // an RR snapshot from an earlier permission read cannot be paired with a newer generation.
        var totals=reusable?new long[]{previous.chunks(),previous.samples(),0,previous.chunks()==0?0:1,previous.chunks()}:jdbc.queryForObject("""
                SELECT COUNT(*),COALESCE(SUM(c.sample_count),0),COALESCE(SUM(CASE WHEN
                    c.sample_count<1 OR c.sample_count>32000 OR c.bytes<>c.sample_count*2
                    OR c.seq<1 OR c.seq>4608 OR c.sample_start<0 OR c.sample_start>9223372036854775807-c.sample_count
                    OR (c.pcm_length IS NULL AND (c.pcm_storage_key IS NULL OR BINARY c.pcm_storage_key<>BINARY CONCAT(c.session_id,'/',c.part_id,'/',c.seq)))
                    OR (c.pcm_length IS NOT NULL AND (c.pcm_length<>c.sample_count*2 OR c.pcm_storage_key IS NOT NULL))
                    OR BINARY c.part_id NOT IN (%s)
                    THEN 1 ELSE 0 END),0),COALESCE(MIN(c.seq),0),COALESCE(MAX(c.seq),0)
                FROM (SELECT session_id,part_id,seq,sample_start,sample_count,bytes,OCTET_LENGTH(pcm) AS pcm_length,pcm_storage_key
                      FROM recording_chunk WHERE session_id=? LIMIT 4097 FOR SHARE) c
                """.formatted(String.join(",",java.util.Collections.nCopies(parts.size(),"BINARY ?"))),
                (r,n)->new long[]{r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getLong(5)},parameters.toArray());
        if(totals==null||totals[0]>RecordingContracts.MAX_SESSION_CHUNKS||totals[1]>RecordingContracts.MAX_SESSION_SAMPLES||totals[2]!=0)throw new IllegalStateException("Recording PCM missing or invalid");
        // With one issued part, the integrity scan above proves every row belongs to it.
        // Unique positive sequences with min=1 and count=max prove a complete prefix;
        // reuse that proof instead of scanning/sorting the same history a second time.
        // Gaps and multipart recordings still require the complete current-part frontier.
        boolean contiguousSinglePart=parts.size()==1&&(totals[0]==0||totals[3]==1&&totals[0]==totals[4]);
        var frontier=contiguousSinglePart?new long[]{totals[4],totals[4]}:jdbc.queryForObject("""
                SELECT COALESCE(MAX(seq),0),COALESCE(MIN(CASE WHEN seq<>position THEN position-1 END),MAX(seq),0)
                FROM (SELECT seq,ROW_NUMBER() OVER(ORDER BY seq) AS position
                      FROM recording_chunk WHERE session_id=? AND part_id=? LIMIT 4097 FOR SHARE) c
                """,(r,n)->new long[]{r.getLong(1),r.getLong(2)},id,metadata.session().partId());
        if(frontier==null)throw new IllegalStateException("Missing recording frontier");
        long highest=frontier[1],maximum=frontier[0];
        if(maximum<highest||maximum-highest>512)throw new IllegalStateException("Recording retry tail exceeds bound");
        long upper=chunk.seq()==Long.MAX_VALUE?Long.MAX_VALUE:chunk.seq()+1;
        var proofs=jdbc.query("""
                SELECT part_id,seq,sample_start,sample_count,sha256 FROM recording_chunk
                WHERE session_id=? AND part_id=? AND (seq>? OR seq BETWEEN ? AND ?) ORDER BY seq LIMIT 516 FOR SHARE
                """,(r,n)->new RecordingService.Proof(r.getString(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getString(5)),id,metadata.session().partId(),highest,chunk.seq()-1,upper);
        var transaction=verificationTransaction(true);
        if(transaction!=null){if(contiguousSinglePart)transaction.values.put(id,new Verified(metadata.session(),generation,totals[0],totals[1]));else transaction.values.remove(id);}
        return new RecordingService.AppendSnapshot(metadata.session(),metadata.state().equals("PAUSED"),metadata.state().equals("FINISHED"),parts,totals[0],totals[1],highest,maximum,proofs);
    }
    @Override public void persist(RecordingContracts.Session s,RecordingContracts.Chunk c) {
        byte[] pcm=c.pcm();String key=null;
        if(backends!=null) {
            key=objectKey(s.sessionId(),c.partId(),c.seq());var storageKey=new StorageKey(s.backendId(),"recordings",key);
            ama.contract.StorageContracts.StoredBlob stored;
            try{stored=backends.require(s.backendId(),true).storage().put(storageKey,new ByteArrayInputStream(pcm),pcm.length,c.sha256());}
            catch(ama.contract.StorageContracts.StorageException failure){
                if(failure.code()==ama.contract.StorageContracts.ErrorCode.CONFLICT)throw new RecordingService.HashConflict();
                throw failure;
            }
            if(!stored.key().equals(storageKey)||stored.size()!=pcm.length||!stored.sha256().equals(c.sha256()))throw new IllegalStateException("Recording storage proof mismatch");
        }
        jdbc.update("INSERT INTO recording_chunk(session_id,part_id,seq,sample_start,sample_count,sha256,bytes,pcm,pcm_storage_key,persisted_at) VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
                s.sessionId(),c.partId(),c.seq(),c.sampleStart(),c.sampleCount(),c.sha256(),pcm.length,backends==null?pcm:null,key);
        var transaction=verificationTransaction(false);var before=transaction==null?null:transaction.values.remove(s.sessionId());
        if(before!=null&&before.session().equals(s)&&c.partId().equals(s.partId())&&c.seq()==before.chunks()+1&&c.sampleStart()==before.samples()){
            // The one-row INSERT advances the database trigger exactly once. Any other
            // DML would advance it further, making this candidate fail the next current
            // generation comparison. Publish only after commit; no second query is needed.
            transaction.values.put(s.sessionId(),new Verified(s,Math.addExact(before.generation(),1),before.chunks()+1,Math.addExact(before.samples(),c.sampleCount())));
        }
    }
    private static String objectKey(String session,String part,long seq){return session+"/"+part+"/"+seq;}
    /** Frozen metadata must still match; storage/legacy routing always comes from the actual DB. */
    public InputStream readFrozenChunks(RecordingService.Snapshot frozen,java.util.List<RecordingService.Proof> ordered){
        var actual=load(frozen.session().sessionId());
        if(!actual.finished()||!actual.session().equals(frozen.session())||!actual.parts().equals(frozen.parts())||!new java.util.HashSet<>(actual.chunks()).equals(new java.util.HashSet<>(frozen.chunks()))||ordered.size()!=actual.chunks().size()||!new java.util.HashSet<>(ordered).equals(new java.util.HashSet<>(actual.chunks())))throw new IllegalStateException("Frozen recording manifest changed");
        var selected=new java.util.LinkedHashMap<StorageKey,RecordingService.Proof>();var chunks=new java.util.ArrayList<ama.contract.StorageContracts.ReadChunk>();
        for(var proof:ordered){var key=new StorageKey(frozen.session().backendId(),"recordings",objectKey(frozen.session().sessionId(),proof.partId(),proof.seq()));if(selected.put(key,proof)!=null)throw new IllegalArgumentException("Duplicate frozen chunk");chunks.add(new ama.contract.StorageContracts.ReadChunk(key,proof.sampleCount()*2,proof.sha256()));}
        var sequence=new ama.contract.StorageContracts.ReadSequence(chunks);
        long legacy=jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=? AND pcm_storage_key IS NULL",Long.class,frozen.session().sessionId());
        if(legacy==0){if(backends==null)throw new IllegalStateException("Recording file route unavailable");return backends.require(frozen.session().backendId(),false).storage().readSequence(sequence);}
        // Historical ACKed DB PCM, including a resumed mixed session, retains its original route.
        return ama.storage.ChunkSequenceStream.open(sequence,chunk->{var proof=selected.get(chunk.key());return readChunk(frozen.session().sessionId(),proof.partId(),proof.seq());});
    }
    /** Internal frozen-chunk reader; public callers must hold current recording/meeting authorization. */
    public InputStream readChunk(String session,String part,long seq) {
        var rows=jdbc.queryForList("SELECT s.backend_id,c.pcm_storage_key,c.bytes,c.sha256,OCTET_LENGTH(c.pcm) AS pcm_length FROM recording_chunk c JOIN recording_session s ON s.session_id=c.session_id WHERE c.session_id=? AND c.part_id=? AND c.seq=?",session,part,seq);
        if(rows.size()!=1)throw new IllegalArgumentException("Unknown recording chunk");var row=rows.getFirst();
        long size=((Number)row.get("bytes")).longValue();if(size<2||size>RecordingContracts.MAX_PCM_BYTES)throw new IllegalStateException("Invalid recording PCM size");
        String expected=row.get("sha256").toString(),key=(String)row.get("pcm_storage_key");InputStream input;
        if(key!=null) {
            if(backends==null||row.get("pcm_length")!=null||!key.equals(objectKey(session,part,seq)))throw new IllegalStateException("Recording file route unavailable");
            input=backends.require(row.get("backend_id").toString(),false).storage().read(new StorageKey(row.get("backend_id").toString(),"recordings",key),0,size);
        }else {
            if(row.get("pcm_length")==null||((Number)row.get("pcm_length")).longValue()!=size)throw new IllegalStateException("Recording PCM missing");
            byte[] bytes=jdbc.queryForObject("SELECT pcm FROM recording_chunk WHERE session_id=? AND part_id=? AND seq=? AND OCTET_LENGTH(pcm)<=?",byte[].class,session,part,seq,RecordingContracts.MAX_PCM_BYTES);
            if(bytes==null)throw new IllegalStateException("Recording PCM missing");input=new ByteArrayInputStream(bytes);
        }
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            return new FilterInputStream(input){long received;boolean verified;
                @Override public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:Byte.toUnsignedInt(one[0]);}
                @Override public int read(byte[] bytes,int off,int len)throws IOException{
                    int n=in.read(bytes,off,len);if(n>0){received+=n;if(received>size)throw new IOException("Recording PCM exceeds manifest");digest.update(bytes,off,n);}
                    if(n<0&&!verified){verified=true;if(received!=size||!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new IOException("Recording PCM integrity mismatch");}return n;
                }
            };
        }catch(java.security.NoSuchAlgorithmException impossible){try{input.close();}catch(IOException ignored){}throw new IllegalStateException(impossible);}
    }
    @Override public void pause(RecordingContracts.Session s,boolean paused) {
        jdbc.update("UPDATE recording_session SET state=? WHERE session_id=? AND writer_epoch=? AND state IN ('OPEN','PAUSED')",paused?"PAUSED":"OPEN",s.sessionId(),s.writerEpoch());
    }
    @Override public void nextPart(RecordingContracts.Session previous,RecordingContracts.Session next) {
        int ordinal=jdbc.queryForObject("SELECT COALESCE(MAX(part_no),0)+1 FROM recording_part WHERE session_id=?",Integer.class,previous.sessionId());
        jdbc.update("INSERT INTO recording_part(session_id,part_id,part_no,created_at) VALUES (?,?,?,UTC_TIMESTAMP(6))",next.sessionId(),next.partId(),ordinal);
        int changed=jdbc.update("UPDATE recording_session SET part_id=?,writer_epoch=?,state='OPEN',writer_connection_id=NULL WHERE session_id=? AND writer_epoch=? AND state='PAUSED'",next.partId(),next.writerEpoch(),next.sessionId(),previous.writerEpoch());
        if(changed!=1)throw new RecordingService.StaleWriter();
    }
    @Override public void finish(RecordingContracts.Session s,Map<String,Long> finalSequences) {
        jdbc.update("UPDATE recording_session SET state='FINISHED' WHERE session_id=? AND writer_epoch=? AND state IN ('OPEN','PAUSED')",s.sessionId(),s.writerEpoch());
    }
}
