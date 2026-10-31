package ama.recording;

import ama.contract.RecordingContracts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecordingServiceTest {
    @Test void recoveryManifestKeepsOrderedPartsAndDistinguishesGapsFromPersistedSamples(){
        var service=new RecordingService();var first=service.open("meeting","backend");
        service.accept(first.sessionId(),1,new ama.contract.RecordingContracts.Chunk(first.partId(),1,2,2,2,new byte[]{1,2,3,4}));
        var gap=service.manifest(first.sessionId());assertFalse(gap.finished());assertEquals(0,gap.parts().getFirst().highestContiguousSeq());assertEquals(2,gap.parts().getFirst().highestReceivedSeq());assertEquals(2,gap.parts().getFirst().sampleCount());
        service.accept(first.sessionId(),1,new ama.contract.RecordingContracts.Chunk(first.partId(),1,1,0,2,new byte[]{1,2,3,4}));service.pause(first.sessionId(),1);assertTrue(service.manifest(first.sessionId()).paused());
        var second=service.nextPart(first.sessionId(),1);var complete=service.manifest(first.sessionId());assertEquals(java.util.List.of(first.partId(),second.partId()),complete.parts().stream().map(RecordingService.PartManifest::partId).toList());assertEquals(2,complete.parts().getFirst().highestContiguousSeq());assertEquals(0,complete.parts().getLast().sampleCount());
    }
    @Test
    void totalChunkCountAndTwoHourSamplesAreBoundedAcrossTheSession() {
        var service=new RecordingService();var small=service.open("small-meeting","backend");
        for(int seq=1;seq<=4096;seq++)service.accept(small.sessionId(),1,new RecordingContracts.Chunk(small.partId(),seq,seq-1,1,new byte[2]));
        assertThrows(IllegalArgumentException.class,()->service.accept(small.sessionId(),1,new RecordingContracts.Chunk(small.partId(),4097,4096,1,new byte[2])));
        var longSession=service.open("long-meeting","backend");
        for(int seq=1;seq<=3600;seq++)service.accept(longSession.sessionId(),1,new RecordingContracts.Chunk(longSession.partId(),seq,(seq-1)*32000L,32000,new byte[64000]));
        assertThrows(IllegalArgumentException.class,()->service.accept(longSession.sessionId(),1,new RecordingContracts.Chunk(longSession.partId(),3601,115200000,1,new byte[2])));
    }
    @Test
    void deviceReopenIssuesNewPartAndEpochAndFinishRequiresEveryPart() {
        var service=new RecordingService();var first=service.open("meeting","backend");
        service.accept(first.sessionId(),1,new RecordingContracts.Chunk(first.partId(),1,0,2,new byte[4]));
        service.pause(first.sessionId(),1);
        var second=service.nextPart(first.sessionId(),1);
        assertNotEquals(first.partId(),second.partId());assertEquals(2,second.writerEpoch());
        assertEquals(first.backendId(),second.backendId());
        assertThrows(RecordingService.StaleWriter.class,() -> service.accept(first.sessionId(),1,new RecordingContracts.Chunk(first.partId(),2,2,2,new byte[4])));
        service.accept(first.sessionId(),2,new RecordingContracts.Chunk(second.partId(),1,0,2,new byte[4]));
        assertEquals(1,service.ack(first.sessionId(),first.partId()).highestContiguousSeq());
        assertThrows(RecordingService.Gap.class,() -> service.finish(first.sessionId(),2,java.util.Map.of(second.partId(),1L)));
        assertEquals(first.sessionId(),service.finish(first.sessionId(),2,java.util.Map.of(first.partId(),1L,second.partId(),1L)).sessionId());
    }
    @Test
    void ackReportsTheAbsentSequenceRatherThanAnAlreadyStoredSequence() {
        var service = new RecordingService(); var session = service.open("meeting", "backend-a");
        var ack = service.accept(session.sessionId(), 1, new RecordingContracts.Chunk(session.partId(), 2, 2, 2, new byte[4]));
        assertEquals(java.util.List.of(1L), ack.missingSeqs());
    }
    @Test
    void unissuedPartAndConflictingSampleMetadataAreRejected() {
        var service = new RecordingService(); var session = service.open("meeting", "backend-a");
        assertThrows(IllegalArgumentException.class, () -> service.accept(session.sessionId(), 1, new RecordingContracts.Chunk("other", 1, 0, 2, new byte[4])));
        service.accept(session.sessionId(), 1, new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[4]));
        assertThrows(RecordingService.HashConflict.class, () -> service.accept(session.sessionId(), 1, new RecordingContracts.Chunk(session.partId(), 1, 1, 2, new byte[4])));
    }
    @Test
    void contiguousSequencesMustAlsoHaveContiguousSamples() {
        var service = new RecordingService(); var session = service.open("meeting", "backend-a");
        service.accept(session.sessionId(), 1, new RecordingContracts.Chunk(session.partId(), 2, 3, 2, new byte[4]));
        assertThrows(RecordingService.HashConflict.class, () -> service.accept(session.sessionId(), 1, new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[4])));
        assertEquals(0, service.ack(session.sessionId(), session.partId()).highestContiguousSeq());
    }
    @Test
    void staleWriterAndHashConflictAreRejectedAndAckOnlyAdvancesContiguously() {
        var service = new RecordingService();
        var session = service.open("meeting", "backend-a");
        var first = new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[] {1, 0, 0, 0});
        var second = new RecordingContracts.Chunk(session.partId(), 2, 2, 2, new byte[] {2, 0, 0, 0});
        assertEquals(0, service.accept(session.sessionId(), session.writerEpoch(), second).highestContiguousSeq());
        assertEquals(2, service.accept(session.sessionId(), session.writerEpoch(), first).highestContiguousSeq());
        assertThrows(RecordingService.HashConflict.class, () -> service.accept(session.sessionId(), session.writerEpoch(), new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[] {9, 0, 0, 0})));
        assertThrows(RecordingService.StaleWriter.class, () -> service.accept(session.sessionId(), session.writerEpoch() - 1, second));
    }

    @Test
    void finishCannotArchiveWithMissingFinalSequence() {
        var service = new RecordingService();
        var session = service.open("meeting", "backend-a");
        service.accept(session.sessionId(), session.writerEpoch(), new RecordingContracts.Chunk(session.partId(), 2, 2, 2, new byte[] {2, 0, 0, 0}));
        assertThrows(RecordingService.Gap.class, () -> service.finish(session.sessionId(), session.writerEpoch(), java.util.Map.of(session.partId(), 2L)));
        service.accept(session.sessionId(), session.writerEpoch(), new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[] {1, 0, 0, 0}));
        assertEquals(session.sessionId(), service.finish(session.sessionId(), session.writerEpoch(), java.util.Map.of(session.partId(), 2L)).sessionId());
    }

    @Test
    void pauseAndResumeUseTheSameWriterEpochAndExposeSessionForReconnect() {
        var service = new RecordingService();
        var session = service.open("meeting", "backend-a");
        assertEquals(session.sessionId(), service.session(session.sessionId()).sessionId());
        service.pause(session.sessionId(), session.writerEpoch());
        assertThrows(RecordingService.Paused.class, () -> service.accept(session.sessionId(), session.writerEpoch(), new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[] {1, 0, 0, 0})));
        service.resume(session.sessionId(), session.writerEpoch());
        assertEquals(1, service.accept(session.sessionId(), session.writerEpoch(), new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[] {1, 0, 0, 0})).highestContiguousSeq());
    }

    @Test
    void persistenceFailureDoesNotAdvanceAck() {
        var service = new RecordingService((session, chunk) -> { throw new IllegalStateException("db down"); });
        var session = service.open("meeting", "backend-a");
        assertThrows(IllegalStateException.class, () -> service.accept(session.sessionId(), session.writerEpoch(), new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[] {1, 0, 0, 0})));
        assertEquals(0, service.ack(session.sessionId(), session.partId()).highestContiguousSeq());
    }
}
