package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Selection policy applied only to source versions obtained from complete stored graph proofs. */
final class MinutesExclusionSelection {
 static void requireSelected(Map<String,Object> root,List<FullRow> rows,Map<String,List<SourceVersion>> eligible,List<JdbcMinutesSettingsCommands.Exclusion> selected){
  for(var item:selected){var versions=eligible.get(item.locatorId()+"/"+item.category());if(versions==null)throw new ama.contract.TranscriptContracts.Failure(422,"CURRENT_DETECTION_SOURCE_REQUIRED");var exact=versions.stream().distinct().toList();require(exact.size()==1,SOURCE_CONTEXT_AMBIGUOUS);MinutesEditSources.requireCurrent(root,rows,exact);}
 }
 private MinutesExclusionSelection(){}
}
