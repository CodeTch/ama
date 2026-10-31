package ama.contract;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecordingContractsTest {
    @Test
    void pcm16LengthAndTwoSecondLimitAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new RecordingContracts.Chunk("p", 1, 0, 2, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> new RecordingContracts.Chunk("p", 1, 0, 32001, new byte[64002]));
        assertThrows(IllegalArgumentException.class, () -> new RecordingContracts.Chunk("p", 1, Long.MAX_VALUE, 1, new byte[2]));
        assertEquals(64000, new RecordingContracts.Chunk("p", 1, 0, 32000, new byte[64000]).pcm().length);
    }
    @Test
    void sequenceStartsAtOneAndPayloadHashIsBound() {
        var chunk = new RecordingContracts.Chunk("p", 1, 0, 1, new byte[] {1, 2});
        assertEquals(1, chunk.seq());
        assertThrows(IllegalArgumentException.class, () -> new RecordingContracts.Chunk("p", 0, 0, 1, new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> new RecordingContracts.Chunk("p", 2, -1, 1, new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> new RecordingContracts.Chunk("p", 1, 2, 0, 1, "0".repeat(64), new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> new RecordingContracts.Session("", "m", "p", 0, "b"));
    }
}
