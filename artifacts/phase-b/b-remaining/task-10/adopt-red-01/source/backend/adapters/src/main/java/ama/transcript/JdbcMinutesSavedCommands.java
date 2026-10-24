package ama.transcript;
import ama.config.ConfigArchiveSource;import ama.contract.IdentityContracts.Actor;import java.util.*;import java.util.function.Supplier;
/** Controlled actual composition; formal new effects require the later combined publication installation. */
public final class JdbcMinutesSavedCommands {
 private final JdbcTranscriptAuthority authority;private final Supplier<ConfigArchiveSource.Snapshot> archives;private final String pin;private final TranscriptAcquisitionBudget limits;
 public JdbcMinutesSavedCommands(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits){this.authority=Objects.requireNonNull(authority);this.archives=Objects.requireNonNull(archives);this.pin=pin;this.limits=Objects.requireNonNull(limits);}
 public Map<String,Object> adopt(Actor actor,String meeting,String key,long expected,String candidate,String input,String fingerprint,String trace){throw new ama.contract.TranscriptContracts.Failure(503,"MINUTES_ADOPTION_UNAVAILABLE");}
}
