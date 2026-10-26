package ama.transcript;
import java.util.*;
/** Configuration compatibility only; the JDBC service must separately verify its actual shared authority installation. */
public final class SavedTranscriptTransitions {
 public interface CandidateProvider {}public interface PublicationProvider {}
 private final String candidate,publication,version;private final List<String> candidates,publications;
 public SavedTranscriptTransitions(String candidate,String publication,String version,Collection<String> candidates,Collection<String> publications){this.candidate=candidate;this.publication=publication;this.version=version;this.candidates=List.copyOf(candidates);this.publications=List.copyOf(publications);}
 /** Configuration cannot grant candidate authority: each actual write separately consumes its issuer-bound contribution. */
 public void requireControlledPublication(){
  boolean legacy="1".equals(version)&&"DISABLED_NOT_INSTALLED".equals(candidate)&&"DISABLED_NOT_INSTALLED".equals(publication);
  boolean controlled="2".equals(version)&&"AUTHORITY_CONTROLLED".equals(candidate)&&"AUTHORITY_CONTROLLED".equals(publication);
  if(!(legacy||controlled)||!candidates.isEmpty()||!publications.isEmpty())throw ama.contract.MinutesContracts.fail(ama.contract.MinutesContracts.FailureCode.CAPABILITY_UNAVAILABLE);
 }
 public void requireAbsent(){if(!"1".equals(version)||!"DISABLED_NOT_INSTALLED".equals(candidate)||!"DISABLED_NOT_INSTALLED".equals(publication)||!candidates.isEmpty()||!publications.isEmpty())throw ama.contract.MinutesContracts.fail(ama.contract.MinutesContracts.FailureCode.CAPABILITY_UNAVAILABLE);}

 public Map<String,Object> observation(){return Map.of("candidateInstallation",candidate==null?"UNKNOWN":candidate,"publicationInstallation",publication==null?"UNKNOWN":publication,"declarationVersion",version==null?"UNKNOWN":version,"candidateProviders",candidates,"publicationProviders",publications);}
}
