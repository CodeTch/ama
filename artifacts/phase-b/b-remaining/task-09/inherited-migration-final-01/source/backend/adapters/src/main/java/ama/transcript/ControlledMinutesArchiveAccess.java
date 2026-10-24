package ama.transcript;
import ama.config.*;import ama.config.file.ControlledConfigArchiveSource;import ama.contract.ConfigArchiveContracts.*;import java.nio.file.Path;import java.util.Objects;import java.util.function.Supplier;
/** Immutable trusted deployment root; historical pins never select a caller supplied path or latest release. */
public final class ControlledMinutesArchiveAccess implements Supplier<ConfigArchiveSource.Snapshot> {
 private final Path root;private final String configured;private final ConfigArchiveIndex.IndexBudget index;private final ArchiveReadBudget read;
 public ControlledMinutesArchiveAccess(Path root,String configured,ConfigArchiveIndex.IndexBudget index,ArchiveReadBudget read){this.root=Objects.requireNonNull(root);ama.contract.MinutesContracts.hash(configured);this.configured=configured;this.index=Objects.requireNonNull(index);this.read=Objects.requireNonNull(read);}
 public ConfigArchiveSource.Snapshot get(){return original(configured);}
 public ConfigArchiveSource.Snapshot original(String exactIndex){ama.contract.MinutesContracts.hash(exactIndex);return new ControlledConfigArchiveSource().open(root,exactIndex,index,read,()->false);}
}
