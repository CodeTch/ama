package ama.config;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import java.util.List;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
public final class ProcessingManifestRefs {
 public ProcessingManifestRefs(ProcessingManifestReader reader){}
 public List<Ref> roots(byte[] original,ArchiveReadBudget b,BooleanSupplier c){throw fail(PROFILE_UNAVAILABLE);}
}
