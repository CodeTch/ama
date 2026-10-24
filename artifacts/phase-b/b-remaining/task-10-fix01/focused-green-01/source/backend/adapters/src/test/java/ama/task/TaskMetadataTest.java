package ama.task;

import ama.contract.TaskContracts.SubjectRef;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskMetadataTest {
    @Test void unsafeProtocolCodesAreRejectedBeforePersistence() {
        for (var kind : new String[]{"MEETING\nBODY", "会议", "MEETING BODY", "MEETING/ROOT"}) {
            assertThrows(IllegalArgumentException.class, () -> new SubjectRef(kind, "provider-id"), kind);
        }
    }

    @Test void vendorIdentifiersKeepTheirBinaryIdentity() {
        var first = new SubjectRef("MEETING", "provider-id");
        var second = new SubjectRef("MEETING", "provider-id ");
        var third = new SubjectRef("MEETING", "Provider-id");
        assertNotEquals(first, second);
        assertNotEquals(first, third);
    }
}
