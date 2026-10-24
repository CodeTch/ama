package ama.config;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.config.ConfigArchiveIndex.IndexBudget;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Explicit historical acquisition, never a default/latest resolver or business validation token. */
public interface ConfigArchiveSource {
 Snapshot open(Path root, String exactIndexSha, IndexBudget indexBudget, ArchiveReadBudget validationBudget, BooleanSupplier cancel);
 interface Snapshot {
  String indexSha256();
  ConfigArchiveIndex index();
  List<RawArchive> loadClosure(List<Ref> roots, ArchiveReadBudget budget, long reservedManifestBytes, BooleanSupplier cancel);
 }
}
