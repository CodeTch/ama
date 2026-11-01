package ama.recording;

import ama.contract.RecordingContracts;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL metadata hot path. Seeded file routes are fixtures, not evidence of stored audio. */
class RecordingAppendIT extends RecordingProtocolIT {
    @Test void lateExactRetryDoesNotTransferTheWholeCommittedHistory() {
        var s = seeded(3600, 2);
        var measured = new Counted(source);
        var service = new RecordingService(new JdbcRecordingPersistence(measured));
        long start = System.nanoTime();
        assertEquals(3600, service.accept(s.sessionId(), 1, chunk(s, 2)).highestContiguousSeq());
        System.out.println("APPEND_HISTORY_PROBE " + measured.rows + " chunk rows / " + (System.nanoTime()-start)/1_000_000 + " ms");
        assertTrue(measured.rows.get() <= 516, "Append transferred " + measured.rows + " historical chunk rows");
        var changed = new RecordingContracts.Chunk(s.partId(), 1, 2, 2, 2, new byte[]{9,9,9,9});
        assertThrows(RecordingService.HashConflict.class, () -> service.accept(s.sessionId(), 1, changed));
    }

    @Test void corruptionOutsideRetryNeighbourhoodStillBlocksAnAck() {
        var s = seeded(2400, 2);
        jdbc.update("UPDATE recording_chunk SET pcm_storage_key=NULL WHERE session_id=? AND seq=1",s.sessionId());
        assertThrows(IllegalStateException.class, () -> service().accept(s.sessionId(),1,chunk(s,2401)));
        assertEquals(2400,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,s.sessionId()));
    }

    @Test void caseChangedUnissuedPartFarOutsideRetryNeighbourhoodCannotHideInSqlCollation() {
        var s=seeded(2400,2);
        jdbc.update("UPDATE recording_chunk SET part_id=UPPER(part_id),pcm_storage_key=CONCAT(session_id,'/',UPPER(part_id),'/',seq) WHERE session_id=? AND seq=1",s.sessionId());
        assertThrows(IllegalStateException.class,()->service().accept(s.sessionId(),1,chunk(s,2401)));
        assertEquals(2400,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,s.sessionId()));
    }

    @Test void globalChunkAndSampleBoundsStillIncludeEarlierHistory() {
        var full = seeded(4096,2);
        assertThrows(IllegalArgumentException.class, () -> service().accept(full.sessionId(),1,chunk(full,4097)));
        assertEquals(4096,service().accept(full.sessionId(),1,chunk(full,1)).highestContiguousSeq());
        var duration = seeded(3600,32000);
        var next = new RecordingContracts.Chunk(duration.partId(),1,3601,115200000,2,new byte[]{1,2,3,4});
        assertThrows(IllegalArgumentException.class, () -> service().accept(duration.sessionId(),1,next));
    }

    @Test void outOfOrderTailAndNeighbourConflictsKeepTheOriginalAckSemantics() {
        var s=seeded(2000,2);
        var service=service();
        assertEquals(List.of(2001L,2002L),service.accept(s.sessionId(),1,chunk(s,2003)).missingSeqs());
        assertEquals(List.of(2002L),service.accept(s.sessionId(),1,chunk(s,2001)).missingSeqs());
        var wrong=new RecordingContracts.Chunk(s.partId(),1,2002,9000,2,new byte[]{1,2,3,4});
        assertThrows(RecordingService.HashConflict.class,()->service.accept(s.sessionId(),1,wrong));
        assertEquals(2003,service.accept(s.sessionId(),1,chunk(s,2002)).highestContiguousSeq());
    }

    @Test void earlierPartSamplesAndMalformedRoutesAreIncludedInTheAppendGate() {
        var first=seeded(3600,32000);service().pause(first.sessionId(),1);
        var second=service().nextPart(first.sessionId(),1);
        assertThrows(IllegalArgumentException.class,()->service().accept(second.sessionId(),2,chunk(second,1)));
        jdbc.update("UPDATE recording_chunk SET pcm_storage_key=UPPER(pcm_storage_key) WHERE session_id=? AND seq=1",first.sessionId());
        assertThrows(IllegalStateException.class,()->service().accept(second.sessionId(),2,chunk(second,1)));
    }

    @Test void largestRetryTailKeepsEveryMissingSequenceAndCanFillItsFirstGap() {
        var s=seeded(2400,2);var service=service();
        assertEquals(511,service.accept(s.sessionId(),1,chunk(s,2912)).missingSeqs().size());
        assertThrows(IllegalArgumentException.class,()->service.accept(s.sessionId(),1,chunk(s,2913)));
        var ack=service.accept(s.sessionId(),1,chunk(s,2401));assertEquals(2401,ack.highestContiguousSeq());
        assertEquals(510,ack.missingSeqs().size());assertEquals(2402L,ack.missingSeqs().getFirst());
        assertEquals(2911L,ack.missingSeqs().getLast());
    }

    @Test void pooledLateProjectionMeasuresTheSameCommittedFactsWithBoundedWireRows()throws Exception {
        var s=seeded(3600,2);
        try(var connection=source.getConnection()){
            var single=new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true);
            var measured=new Counted(single);var persistence=new JdbcRecordingPersistence(measured);
            for(int i=0;i<3;i++){persistence.locked(s.sessionId(),()->persistence.load(s.sessionId()));persistence.locked(s.sessionId(),()->persistence.loadForAppend(s.sessionId(),chunk(s,3601)));}
            long[] full=new long[15],bounded=new long[15];int fullRows=0,boundedRows=0;
            for(int i=0;i<15;i++){
                measured.rows.set(0);long start=System.nanoTime();var snapshot=persistence.locked(s.sessionId(),()->persistence.load(s.sessionId()));full[i]=System.nanoTime()-start;fullRows+=measured.rows.get();
                measured.rows.set(0);start=System.nanoTime();var projection=persistence.locked(s.sessionId(),()->persistence.loadForAppend(s.sessionId(),chunk(s,3601)));bounded[i]=System.nanoTime()-start;boundedRows+=measured.rows.get();
                assertEquals(snapshot.chunks().size(),projection.totalChunks());assertEquals(snapshot.chunks().stream().mapToLong(RecordingService.Proof::sampleCount).sum(),projection.totalSamples());
                assertEquals(snapshot.session(),projection.session());assertEquals(snapshot.parts(),projection.parts());
            }
            Arrays.sort(full);Arrays.sort(bounded);
            System.out.println("APPEND_POOLED_PROBE fullMedianMs="+full[7]/1_000_000.0+" boundedMedianMs="+bounded[7]/1_000_000.0+" fullRows="+fullRows+" boundedRows="+boundedRows+" operations=15 history=3600");
            assertEquals(3600*15,fullRows);assertTrue(boundedRows<=15*516);
        }
    }

    private RecordingContracts.Session seeded(int count,int samples) {
        var s=service().open(UUID.randomUUID().toString(),"local");
        var rows=new ArrayList<Object[]>();
        for(int i=1;i<=count;i++)rows.add(new Object[]{s.sessionId(),s.partId(),i,(long)(i-1)*samples,samples,chunk(s,i).sha256(),samples*2,s.sessionId()+"/"+s.partId()+"/"+i});
        new ama.db.TransactionRunner(source).required(()->{
            jdbc.batchUpdate("INSERT INTO recording_chunk(session_id,part_id,seq,sample_start,sample_count,sha256,bytes,pcm_storage_key,persisted_at) VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",rows);
            return null;
        });
        return s;
    }

    private static final class Counted extends AbstractDataSource {
        final DataSource source;final AtomicInteger rows=new AtomicInteger();
        Counted(DataSource source){this.source=source;}
        @Override public Connection getConnection()throws SQLException{return connection(source.getConnection());}
        @Override public Connection getConnection(String user,String password)throws SQLException{return connection(source.getConnection(user,password));}
        private Connection connection(Connection original){return proxy(Connection.class,original,(method,args,result)->{
            if(method.getName().equals("prepareStatement")&&args[0] instanceof String sql&&sql.toLowerCase(Locale.ROOT).contains("recording_chunk"))
                return proxy(PreparedStatement.class,(PreparedStatement)result,(called,parameters,value)->value instanceof ResultSet rs?proxy(ResultSet.class,rs,(next,ignored,more)->{if(next.getName().equals("next")&&Boolean.TRUE.equals(more))rows.incrementAndGet();return more;}):value);
            return result;
        });}
        interface After {Object apply(Method method,Object[] args,Object result);}
        private static <T>T proxy(Class<T> type,T original,After after){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(self,method,args)->{
            try{return after.apply(method,args,method.invoke(original,args));}catch(InvocationTargetException failure){throw failure.getCause();}
        }));}
    }
}
