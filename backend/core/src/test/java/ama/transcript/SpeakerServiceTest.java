package ama.transcript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeakerServiceTest {
    @Test
    void mergePreservesAccessList() {
        var service = new SpeakerService();
        service.add("a", java.util.Set.of("u1", "u2"));
        service.add("b", java.util.Set.of("u2"));
        service.merge("b", "a");
        assertEquals(java.util.Set.of("u1", "u2"), service.access("a"));
    }
}
