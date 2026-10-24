package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

final class DomainHash {
 static void profile(Artifact artifact,String version,CanonicalBudget budget,BooleanSupplier cancelled){
  require(artifact!=null&&artifact.version().equals(version),PROFILE_UNAVAILABLE);check(cancelled);var bytes=artifact.bytes();
  require(bytes.size()<=budget.maxEncodedBytes(),BUDGET_EXCEEDED);
  require(bytes.sha256(cancelled).equals(artifact.sha256()),PROFILE_UNAVAILABLE);check(cancelled);
 }
 static String hash(Artifact artifact,String version,String kind,CanonicalValue data,CanonicalBudget budget,BooleanSupplier cancelled){
  profile(artifact,version,budget,cancelled);return new CanonicalValueCodec().sha256(ContractValues.envelope(version,kind,data),budget,cancelled);
 }
}
