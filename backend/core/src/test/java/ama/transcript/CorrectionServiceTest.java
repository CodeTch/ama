package ama.transcript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorrectionServiceTest {
    @Test
    void staleUndoConflictsAndRejectedCorrectionLeavesNoPending() {
        var service = new CorrectionService("hello world");
        var op = service.replace("world", "世界", 0);
        assertEquals(1, service.pendingCorrections());
        service.decide(op.operationId(), true, 1);
        assertEquals("hello 世界", service.text());
        assertEquals(0, service.pendingCorrections());
        assertThrows(CorrectionService.Conflict.class, () -> service.undo(op.operationId(), 1));
    }
}
