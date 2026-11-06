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
 // New inputs pin the rule-bearing archive. Previously issued inputs keep their original hash.
 public static final String MINUTES_RULES = """
   仅依据当前已确认的转写和锁定材料生成结果。概述应归纳议题和范围，跳过寒暄及测试开场。
   关键内容按议题归纳事实与讨论，保留建议、预计和不确定语气。结论只列明确决定及附带条件。
   逐项提取明确的后续任务、交付物、被提及的负责人和期限，未明确的字段留空，人员和期限仅作建议。
   分开提取待核实问题、明确分歧和已提出的风险。没有相关内容时保留空集合。
   每个事实关联实际证据。背景材料只作背景，不能转成会议发言、决定或行动。
   标注内部核心经营数据、政治敏感信息及来源。正文按照选定模板成段撰写，应用排除范围，避免逐句抄写和跨章节重复。
   局部任务遵守传入章节和生成要求，输出必须符合当前协议及锚点约束。
   """;
 public static String rulesArchiveVersion(String baseSha,String version){
  return promptArchiveVersion(baseSha,version)+".minutes-rules-1";
 }
 public static String installedPromptVersion(String baseSha,String version,Set<String> installed){
  String revised=rulesArchiveVersion(baseSha,version);
  return installed.contains(revised)?revised:promptArchiveVersion(baseSha,version);
 }
 private GovernanceCatalog(){}
}
