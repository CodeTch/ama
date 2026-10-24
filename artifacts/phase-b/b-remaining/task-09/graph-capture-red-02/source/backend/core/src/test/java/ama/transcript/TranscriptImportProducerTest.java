package ama.transcript;
import ama.contract.MinutesContracts.CanonicalBudget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TranscriptImportProducerTest {
    @Test void independentProducerGoldenPreservesLongBitsAndEverySemanticField() {
        var budget=new CanonicalBudget(65536,8,128,1024,4096);
        String actual=TranscriptImportProducer.compute("00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002","a".repeat(64),9007199254740993L,7,budget,()->false);
        assertEquals("4db2492f3b4cacfdad3a96d9dc4bab453b54ea2cf49591d529a81793dbfc19c6",actual);
        assertNotEquals(actual,TranscriptImportProducer.compute("00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002","a".repeat(64),9007199254740992L,7,budget,()->false));
        assertThrows(ama.contract.MinutesContracts.Failure.class,()->TranscriptImportProducer.compute("00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002","a".repeat(64),9007199254740993L,7,budget,()->true));
    }
}
