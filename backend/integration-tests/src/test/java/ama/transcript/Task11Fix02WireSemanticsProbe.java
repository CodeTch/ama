package ama.transcript;
import java.util.*;import java.nio.charset.StandardCharsets;import static org.junit.jupiter.api.Assertions.*;
/** Detached production canonical/semantic controls, without relying on disabled Failure stacks. */
public final class Task11Fix02WireSemanticsProbe {
 static TranscriptAcquisitionBudget.Ledger ledger(){var read=new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,1000000,100,100,16,10000,16,128,65536,65536,1000000,65536,100,1000,10000);var canonical=new ama.contract.MinutesContracts.CanonicalBudget(1000000,32,100000,65536,10000);return new TranscriptAcquisitionBudget(1000000,1000000,1000000,10000000,10000000,1000000,16,256,256).ledger(read,canonical,()->false);}
 public static String canonical(String json){return new String(ControlledKnowledgeClient.canonical(ControlledKnowledgeClient.parse(json.getBytes(StandardCharsets.UTF_8),65536),ledger()),StandardCharsets.UTF_8);}
 public static void verify(String original,String bad,boolean semantic){
  var good=PublicationManifest.read(original,ledger());var changed=ControlledKnowledgeClient.parse(bad.getBytes(StandardCharsets.UTF_8),65536);assertEquals(original.length(),bad.length());assertEquals(original.getBytes(StandardCharsets.UTF_8).length,bad.getBytes(StandardCharsets.UTF_8).length);assertEquals(original.codePointCount(0,original.length()),bad.codePointCount(0,bad.length()));
  if(semantic){assertEquals(bad,canonical(bad),"Semantic wire is strictly canonical, so padding cannot fake rejection");assertEquals(ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE,assertThrows(ama.contract.MinutesContracts.Failure.class,()->PublicationManifest.validate(changed,ledger())).code());}
  else{assertEquals(good,changed);assertNotEquals(bad,canonical(bad));assertDoesNotThrow(()->PublicationManifest.validate(changed,ledger()));}
  var failure=assertThrows(ama.contract.MinutesContracts.Failure.class,()->PublicationManifest.read(bad,ledger()));assertEquals(ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE,failure.code());assertEquals(0,failure.getStackTrace().length,"Production Failure intentionally disables stacks");
 }
}
