package ama.config.json;
import ama.config.*;
import ama.config.ConfigArchiveIndex.IndexBudget;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
public final class StrictConfigIndexReader {
 public ConfigArchiveIndex read(byte[] raw,IndexBudget b,BooleanSupplier c){throw fail(PROFILE_UNAVAILABLE);}
}
