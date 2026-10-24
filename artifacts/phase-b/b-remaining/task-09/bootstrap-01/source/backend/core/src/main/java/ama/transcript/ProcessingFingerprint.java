package ama.transcript;
import ama.config.ConfigBindings;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import java.util.List;
import java.util.function.BooleanSupplier;
public final class ProcessingFingerprint {
 private final ConfigBindings bindings;
 public ProcessingFingerprint(ConfigBindings bindings){ama.contract.MinutesContracts.require(bindings!=null,FailureCode.INVALID_ARGUMENT);this.bindings=bindings;}
 public ConfigBindings.ValidatedProcessingBinding compute(byte[] rawManifest,StoredProvenanceFacts prerequisites,List<RawArchive> archives,ArchiveReadBudget budget,BooleanSupplier cancelled){return bindings.computeProcessing(rawManifest,prerequisites,archives,budget,cancelled);}
 public ConfigBindings.ValidatedProcessingBinding verify(FullRow input,StoredProvenanceFacts facts,List<RawArchive> archives,ArchiveReadBudget budget,BooleanSupplier cancelled){return bindings.verifyProcessing(input,facts,archives,budget,cancelled);}
}
