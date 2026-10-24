package ama.transcript;
import java.util.*;
/** Installation boundary for future Task10 disposition and Task11 publication freeze. */
public final class SavedTranscriptTransitions {
 public interface CandidateProvider {}public interface PublicationProvider {}
 private final String candidate,publication,version;private final List<String> candidates,publications;
 public SavedTranscriptTransitions(String candidate,String publication,String version,Collection<String> candidates,Collection<String> publications){this.candidate=candidate;this.publication=publication;this.version=version;this.candidates=List.copyOf(candidates);this.publications=List.copyOf(publications);}
 /** Configuration cannot grant candidate authority: each actual write separately consumes its issuer-bound contribution. */
 public void requireControlledPublication(){if(!"1".equals(version)||!"DISABLED_NOT_INSTALLED".equals(candidate)||!"DISABLED_NOT_INSTALLED".equals(publication)||!candidates.isEmpty()||!publications.isEmpty())throw ama.contract.MinutesContracts.fail(ama.contract.MinutesContracts.FailureCode.CAPABILITY_UNAVAILABLE);}
 public void requireAbsent(){requireControlledPublication();}
 public Map<String,Object> observation(){return Map.of("candidateInstallation",candidate==null?"UNKNOWN":candidate,"publicationInstallation",publication==null?"UNKNOWN":publication,"declarationVersion",version==null?"UNKNOWN":version,"candidateProviders",candidates,"publicationProviders",publications);}
}
