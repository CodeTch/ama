package ama.transcript;
import java.util.Objects;
import java.util.function.Supplier;
import ama.contract.MinutesContracts.Failure;
import static ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE;

/** A concurrent commit may invalidate a read's two snapshots. Reacquire at most
 * twice, only when a root change is observed. Callers retain one operation budget. */
public final class TranscriptReadRetry {
 private TranscriptReadRetry(){}
 public static <T> T read(Supplier<?> version,Supplier<T> acquisition){
  return read(version,acquisition,error->error instanceof Failure f&&f.code()==PROVENANCE_UNAVAILABLE);
 }
 public static <T> T read(Supplier<?> version,Supplier<T> acquisition,java.util.function.Predicate<RuntimeException> changedMetadata){
  Object before=version.get();
  for(int attempt=0;;attempt++){
   try{return acquisition.get();}
   catch(RuntimeException failure){
    if(!changedMetadata.test(failure)||attempt>=2)throw failure;
    Object after=version.get();if(Objects.equals(before,after))throw failure;before=after;
   }
  }
 }
}
