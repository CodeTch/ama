package ama.config.json;
import ama.contract.ConfigArchiveContracts.Ref;import ama.config.*;import ama.contract.ConfigArchiveContracts.*;import ama.contract.MinutesContracts.*;import ama.transcript.*;
import java.util.*;import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;import static ama.config.json.ConfigBindingsTest.*;
class SavedTranscriptAdmissionTest {
 private ConfigArchiveSource.Snapshot snapshot()throws Exception {
  var raw=archives();var entries=new ArrayList<ConfigArchiveIndex.Entry>();for(var a:raw)entries.add(new ConfigArchiveIndex.Entry(new Ref(a.claimedArtifactType(),a.claimedVersion(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(a.bytes()))),a.bytes().length));
  return new ConfigArchiveSource.Snapshot(){public String indexSha256(){return "a".repeat(64);}public ConfigArchiveIndex index(){return new ConfigArchiveIndex(1,null,entries);}public List<RawArchive> loadClosure(List<Ref> roots,ArchiveReadBudget budget,long reserved,java.util.function.BooleanSupplier cancelled){return raw;}};
 }
 @Test void originalCanonicalMinimaConstrainLedgerBeforeTransfer()throws Exception {
  var admitted=SavedTranscriptAdmission.capture(snapshot(),B,new CanonicalBudget(1000000,64,100000,99999,99999));
  assertEquals(new CanonicalBudget(65536,32,4096,4096,4096),admitted.canonical());
  var ledger=new TranscriptAcquisitionBudget(1000000,1000000,1000000,8000000,8000000,8000000,8,8,8).ledger(B,admitted.canonical(),()->false);
  assertDoesNotThrow(()->ledger.text(4096,4096,4096,1,false));assertThrows(Failure.class,()->ledger.text(4097,4097,4097,1,false));assertEquals(4,admitted.originals().size());
 }
 @Test void smallerRuntimeStillRestrictsOriginal()throws Exception {
  var runtime=new CanonicalBudget(32000,12,1000,2000,500);assertEquals(runtime,SavedTranscriptAdmission.capture(snapshot(),B,runtime).canonical());
 }
}
