package ama.config.file;
import ama.config.*;
import ama.config.ConfigArchiveIndex.IndexBudget;
import ama.contract.ConfigArchiveContracts.*;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class ControlledConfigArchiveSource implements ConfigArchiveSource {
 public Snapshot open(Path root,String sha,IndexBudget ib,ArchiveReadBudget b,BooleanSupplier c){throw fail(PROFILE_UNAVAILABLE);}
}
