package ama.storage;
import ama.api.Task06RuntimeProbe;
import ama.transcript.*;
import ama.contract.TranscriptContracts.*;
import ama.contract.MinutesContracts.Failure;
import java.util.*;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task06CompositionIT extends Task06Fixture {
 @Test void noninstalledActualCompositionAndPretransferBudgetConsumption()throws Exception {
  String raw=raw();assertFalse(Task06RuntimeProbe.installed(app));var composition=Task06RuntimeProbe.compose(app);var replacement=Task06RuntimeProbe.compose(app);
  assertEquals("NOT_APPLICABLE",composition.correction().projection().readiness());assertTrue(composition.correction().providerInventory().isEmpty());assertNotEquals(composition.correction().projection().qualificationId(),replacement.correction().projection().qualificationId());assertThrows(Failure.class,()->composition.correction().requireSame(replacement.correction()));
  Files.writeString(receipt.resolve("noninstalled-composition.json"),json.writeValueAsString(composition.qualification()));
  var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);
  var saved=composition.service().mutate(actor(),meeting,"large-saved",new Command(Operation.SAVE,1,raw,List.of(new Edit(ids.get(0),"X".repeat(10000),List.of()),new Edit(ids.get(1),"Beta",List.of()))),UUID.randomUUID().toString());
  String working=(String)((Map<?,?>)saved.get("result")).get("revisionId");
  var outcomes=new ArrayList<Map<String,Object>>();
  for(int dimension=0;dimension<6;dimension++){
   long[] v={16777216,16777216,16777216,67108864,67108864,33554432};v[dimension]=dimension==3||dimension==4?40000:5000;
   var bounded=new JdbcSavedTranscripts(ama.api.Task05RuntimeProbe.components(app).authority(),settings().archives(),new TranscriptAcquisitionBudget(v[0],v[1],v[2],v[3],v[4],v[5],128,256,64),composition.correction());
   probe.statements.clear();var failure=assertThrows(Failure.class,()->bounded.readOwner(actor(),meeting,working));assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,failure.code());
   assertTrue(probe.statements.stream().anyMatch(sql->sql.contains("OCTET_LENGTH(body)")&&sql.contains("FOR UPDATE")),"Bound must reach retained body measurement");
   assertFalse(probe.statements.stream().anyMatch(sql->sql.startsWith("SELECT revision_id,segment_id,ordinal,body,")),"No body content query after rejected preflight");
   outcomes.add(Map.of("dimension",dimension,"configured",v[dimension],"code",failure.code().name(),"bodyContentTransfers",0));
  }
  Files.writeString(receipt.resolve("pretransfer-boundaries.json"),json.writeValueAsString(outcomes));assertFalse(Task06RuntimeProbe.installed(app));
 }
}
