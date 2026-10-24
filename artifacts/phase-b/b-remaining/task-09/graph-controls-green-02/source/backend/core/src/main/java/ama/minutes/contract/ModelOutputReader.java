package ama.minutes.contract;
import java.io.InputStream;
import java.util.function.BooleanSupplier;
import ama.contract.MinutesContracts.*;
public interface ModelOutputReader {
 ValidatedOutput read(InputStream source,ValidationContext context,ValidationBudget budget,BooleanSupplier cancelled);
 default ValidatedOutput read(InputStream source,ValidationContext context,ama.config.ConfigBindings.ValidatedGenerationBinding binding,BooleanSupplier cancelled){
  ama.contract.MinutesContracts.require(binding!=null&&context!=null,FailureCode.INVALID_ARGUMENT);
  ama.contract.MinutesContracts.require(binding.fingerprint().equals(context.inputFingerprint())&&binding.fingerprint().equals(context.taskFingerprint()),FailureCode.FINGERPRINT_MISMATCH);
  return read(source,context,binding.outputBudget(),cancelled);
 }
}
