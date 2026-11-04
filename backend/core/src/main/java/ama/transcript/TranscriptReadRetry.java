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
  Object before=version.get();
  for(int attempt=0;;attempt++){
   try{return acquisition.get();}
   catch(Failure failure){
    if(failure.code()!=PROVENANCE_UNAVAILABLE||attempt>=2)throw failure;
    Object after=version.get();if(Objects.equals(before,after))throw failure;before=after;
   }
  }
 }
}
