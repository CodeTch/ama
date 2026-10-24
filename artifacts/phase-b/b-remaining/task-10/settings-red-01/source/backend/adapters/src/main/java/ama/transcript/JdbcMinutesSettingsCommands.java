package ama.transcript;
import ama.config.ConfigArchiveSource;import ama.contract.ConfigArchiveContracts.Ref;import ama.contract.IdentityContracts.Actor;import java.util.*;import java.util.function.Supplier;
public final class JdbcMinutesSettingsCommands {
 public JdbcMinutesSettingsCommands(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,Ref prompt,Ref model,TranscriptAcquisitionBudget limits){}
 public record Exclusion(String locatorId,String category,String reason){}
 public Map<String,Object> replaceExclusions(Actor actor,String meeting,String key,long expected,String source,List<Exclusion> items,String trace){throw new ama.contract.TranscriptContracts.Failure(503,"SETTINGS_NOT_INSTALLED");}
 public Map<String,Object> selectTemplate(Actor actor,String meeting,String key,long expected,String revision,String trace){throw new ama.contract.TranscriptContracts.Failure(503,"SETTINGS_NOT_INSTALLED");}
}
