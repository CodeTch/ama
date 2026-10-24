package ama.transcript;
import java.util.*;import static org.junit.jupiter.api.Assertions.*;
public final class Task11PublicationBudgetProbe {
 public static Map<String,Object> verify(JdbcPublicationService service,String meeting){var a=service.authority;var normal=service.limits;var budget=normal.ledger(a.readBudget(),a.canonicalBudget(),()->false);var plan=PublicationGraph.discover(a.dataSource(),meeting,budget);long[] first=budget.usage();PublicationGraph.discover(a.dataSource(),meeting,budget);long[] twice=budget.usage();assertTrue(twice[0]>first[0]&&twice[3]>first[3]&&twice[5]>first[5]);
  var tiny=new TranscriptAcquisitionBudget(128,128,128,512,512,128,8,8,1);assertThrows(ama.contract.MinutesContracts.Failure.class,()->PublicationGraph.discover(a.dataSource(),meeting,tiny.ledger(a.readBudget(),a.canonicalBudget(),()->false)));
  var limits=new TranscriptAcquisitionBudget(2000,2000,2000,10000,10000,1024,8,8,64);var cumulative=limits.ledger(a.readBudget(),a.canonicalBudget(),()->false);ControlledKnowledgeClient.canonical(Map.of("proof","x".repeat(100)),cumulative);ControlledKnowledgeClient.canonical(Map.of("proof","x".repeat(100)),cumulative);assertThrows(ama.contract.MinutesContracts.Failure.class,()->ControlledKnowledgeClient.canonical(Map.of("proof","x".repeat(100)),cumulative));return Map.of("firstUsage",first,"twiceUsage",twice,"tinyHistoryRejected",true,"cumulativeCanonicalRejected",true,"tasks",plan.rows().get("durable_task").size());
 }
}
