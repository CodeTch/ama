package ama.storage;
import ama.transcript.*;
import ama.config.*;
import ama.config.file.*;
import ama.contract.IdentityContracts.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static ama.transcript.ArchiveProcessingFixture.B;
class Task09FinalInheritedSavedGraphIT extends Task09PipelineFixture {
 @Test void noninstalledCompleteRawInspectionPreservesActualBodyAndExplicitEmptyMarks()throws Exception {
  String raw=raw();var result=assertDoesNotThrow(()->isolated().readOwner(actor(),meeting,raw),"Complete retained RAW body must be inspectable with original successful producer");
  var detail=(Map<?,?>)result.get("selected");assertEquals(raw,detail.get("id"));assertEquals("RAW",detail.get("kind"));
  var segments=(List<Map<String,Object>>)detail.get("segments");assertEquals(List.of("Alpha😀","Beta"),segments.stream().map(s->s.get("body")).toList());
  assertTrue(segments.stream().allMatch(s->s.get("marks").equals(List.of())));
  Files.writeString(receipt.resolve("isolated-raw-inspection.json"),json.writeValueAsString(result));
 }
}
