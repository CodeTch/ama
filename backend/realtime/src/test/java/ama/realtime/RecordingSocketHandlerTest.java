package ama.realtime;

import ama.contract.RecordingContracts;
import ama.recording.RecordingService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecordingSocketHandlerTest {
    @Test
    void handlerReturnsDurableAck() {
        var service = new RecordingService(); var session = service.open("m", "b");
        var handler = new RecordingSocketHandler(service);
        assertEquals(1, handler.accept(session, new RecordingContracts.Chunk(session.partId(), 1, 0, 2, new byte[] {1,0,0,0})).highestContiguousSeq());
    }
}
