package ama.storage;
import java.nio.file.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix02MetadataGrowthIT {
 @Test void actualCountSelectGrowthStaysWithinAdmission()throws Exception {assertEquals("task11-fix02-isolated",System.getenv("AMA_TASK11_FIXTURE"));var result=ama.transcript.Task11Fix02MetadataGrowthProbe.verify(ama.publication.Task11Source.open());Files.writeString(Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"),"actual-count-select-growth.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));}
}
