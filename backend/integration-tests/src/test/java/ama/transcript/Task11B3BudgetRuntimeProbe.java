package ama.transcript;
import java.util.*;import ama.contract.IdentityContracts.Actor;import static org.junit.jupiter.api.Assertions.*;
/** Read-only effective configuration projection and exact real service invocation; no alternate policy. */
public final class Task11B3BudgetRuntimeProbe {
 public static Map<String,Object> settings(JdbcPublicationService service){return Map.of("acquisition",service.limits,"archive",service.authority.readBudget(),"canonical",service.authority.canonicalBudget(),"archiveIndexSha256",service.pin);}
 public static void resubmit(JdbcPublicationService service,Actor actor,String meeting,String publication,String key,long revision,long publicationRevision,String confirmation,String provider,String library,String folder)throws Exception {service.resubmit(actor,meeting,publication,key,revision,publicationRevision,confirmation,provider,library,folder,UUID.randomUUID().toString());fail("Diagnostic must never return committed acceptance");}
}