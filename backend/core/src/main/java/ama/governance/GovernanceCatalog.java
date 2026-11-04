package ama.governance;
import ama.contract.GovernanceContracts.ModelConfig;
import java.util.*;
/** Installed identities are distinct; a fixture vector is never relabelled as another model. */
public final class GovernanceCatalog {
 public static final Set<String> MODELS=Set.of("harness-fixture-embedding-v1");
 public static final Map<String,String> PROMPTS=Map.of(
  "summary-v1","总结会议内容，保留结论与行动项，直接返回纪要正文。",
  "summary-v2","仅根据会议原文生成纪要。分别列出讨论事实、已确认结论、行动项及其负责人和期限，并为每项保留原文证据；缺失信息标记为待确认，不得补造。");
 public static void require(ModelConfig config){if(!MODELS.contains(config.modelVersion())||!PROMPTS.containsKey(config.promptVersion()))throw new IllegalArgumentException("Installed model and prompt versions required");}
 public static String promptArchiveVersion(String baseSha,String version){if(!PROMPTS.containsKey(version))throw new IllegalArgumentException("Installed prompt required");return "governance."+version+"."+baseSha.substring(0,16);}
 private GovernanceCatalog(){}
}
