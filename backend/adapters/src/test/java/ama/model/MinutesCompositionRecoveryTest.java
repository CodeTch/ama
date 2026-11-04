package ama.model;
import java.io.IOException;import java.util.*;import java.util.concurrent.atomic.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MinutesCompositionRecoveryTest {
 @Test void resumesOnlyAlreadyReservedExecutionAfterTemporaryFailure(){var pending=new ArrayList<>(List.of("reserved"));var attempted=new ArrayList<String>();var attempts=new AtomicInteger();var recovery=new MinutesCompositionRecovery(()->List.copyOf(pending),id->{attempted.add(id);if(attempts.getAndIncrement()==0)throw new IOException("offline");pending.remove(id);});recovery.runOnce();assertEquals(List.of("reserved"),pending);recovery.runOnce();assertTrue(pending.isEmpty());recovery.runOnce();assertEquals(List.of("reserved","reserved"),attempted);}
}
