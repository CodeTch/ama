package ama.governance;
import ama.contract.GovernanceContracts.*;
import java.util.*;

/** A failed kind cannot be diluted by another kind. Rules belong to the queued run. */
public final class EvaluationQualification {
 public record Assessment(boolean eligible,String mode,Map<String,Map<String,Object>> kinds,List<String> blockers){public Assessment{kinds=Map.copyOf(kinds);blockers=List.copyOf(blockers);}}
 public static Assessment assess(List<Sample> samples,List<SampleResult> results,QualificationRules rules,boolean voiceEnabled,Map<String,Map<String,Double>> human){
  var blockers=new ArrayList<String>();var groups=new LinkedHashMap<String,Map<String,Object>>();
  if(rules==null)blockers.add("未在运行前冻结判定规则，本次仅作观察");
  var byId=new HashMap<String,SampleResult>();for(var result:results)if(byId.put(result.id(),result)!=null)throw new IllegalArgumentException("Duplicate sample result");
  for(Kind kind:Kind.values()){
   var selected=samples.stream().filter(s->s.kind()==kind).toList();if(selected.isEmpty())continue;
   int failed=0,pending=0;double sum=0;boolean sufficient=true;String metric=switch(kind){case ASR->"cer";case DIARIZATION->"der";case ENTITY->"f1";case EVIDENCE->"coverage";case MINUTES->"rubric";case VOICE_SAME->"frr";case VOICE_DIFFERENT->"far";};
   for(var sample:selected){var result=byId.get(sample.id());if(result==null){pending++;continue;}if(!result.succeeded()){failed++;continue;}
    Double value=null;
    if(kind==Kind.MINUTES){var rubric=human.get(sample.id());if(rubric!=null&&rubric.keySet().containsAll(Set.of("factuality","actions","evidence")))value=Math.min(rubric.get("factuality"),Math.min(rubric.get("actions"),rubric.get("evidence")));}
    else if(kind==Kind.VOICE_SAME||kind==Kind.VOICE_DIFFERENT){var accepted=result.measures().get("accepted");if(accepted!=null)value=kind==Kind.VOICE_SAME?1-accepted:accepted;}
    else value=result.measures().get(metric);
    if(value==null||result.actual()==null){sufficient=false;continue;}sum+=value;
    if(kind==Kind.EVIDENCE&&(rules==null||result.measures().getOrDefault("correctness",-1d)<rules.minEvidenceCorrectness()))sufficient=false;
   }
   double value=sum/selected.size();boolean lowIsGood=Set.of(Kind.ASR,Kind.DIARIZATION,Kind.VOICE_SAME,Kind.VOICE_DIFFERENT).contains(kind);
   double bound=rules==null?0:switch(kind){case ASR->rules.maxCer();case DIARIZATION->rules.maxDer();case ENTITY->rules.minEntityF1();case EVIDENCE->rules.minEvidenceCoverage();case MINUTES->rules.minMinutesRubric();case VOICE_SAME->rules.maxFrr();case VOICE_DIFFERENT->rules.maxFar();};
   boolean passed=rules!=null&&failed==0&&pending==0&&sufficient&&selected.size()>=rules.minimumPerKind()&&(lowIsGood?value<=bound:value>=bound);
   groups.put(kind.name(),Map.of("sampleCount",selected.size(),"failed",failed,"pending",pending,"metric",metric,"value",value,"limit",bound,"passed",passed,"sufficient",sufficient));
   if(!passed)blockers.add(kind.name()+"："+(pending>0?"样本尚未完成":failed>0?"含失败样本":!sufficient?"缺少有效标注或人工评分":"未满足冻结规则"));
  }
  if(samples.isEmpty())blockers.add("缺少评测样本");
  if(voiceEnabled&&(!groups.containsKey("VOICE_SAME")||!groups.containsKey("VOICE_DIFFERENT")))blockers.add("启用声纹匹配必须同时具有同人和异人样本");
  return new Assessment(blockers.isEmpty(),rules==null?"OBSERVATION":"QUALIFICATION",groups,blockers);
 }
}
