package ama.transcript;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Immutable boot composition. No PENDING count is inferred from absent storage. */
public final class CorrectionInstallation {
 public interface Provider {}
 public record Declaration(String mode,String version,String qualificationId) {}
 public record Projection(String mode,String readiness,String qualificationId) {}
 private final Object bootIdentity=new Object();
 private final Declaration selected;private final List<String> providers;
 private CorrectionInstallation(Declaration selected,Collection<String> inventory){this.selected=selected;providers=List.copyOf(inventory);}
 public static CorrectionInstallation compose(Declaration selected,Collection<String> actualProviderInventory){return new CorrectionInstallation(selected,actualProviderInventory);}
 public Projection projection(){
  boolean valid=selected!=null&&"DISABLED_NOT_INSTALLED".equals(selected.mode())&&"1".equals(selected.version())&&selected.qualificationId()!=null&&selected.qualificationId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")&&providers.isEmpty();
  return valid?new Projection("DISABLED_NOT_INSTALLED","NOT_APPLICABLE",selected.qualificationId()):new Projection("UNAVAILABLE","UNAVAILABLE",null);
 }
 public void requireReady(){if(!projection().readiness().equals("NOT_APPLICABLE"))throw fail(CAPABILITY_UNAVAILABLE);}
 public void requireSame(CorrectionInstallation expected){if(expected!=this||expected.bootIdentity!=bootIdentity)throw fail(CAPABILITY_UNAVAILABLE);}
 public List<String> providerInventory(){return providers;}
}
