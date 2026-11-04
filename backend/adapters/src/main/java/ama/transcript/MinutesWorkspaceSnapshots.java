package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;
final class MinutesWorkspaceSnapshots {
 static final String COLUMNS="id meeting_id revision minutes_revision_id element_set_id template_revision_id exclusion_revision_id transcript_confirmation_id material_snapshot_id needs_generation generation_fingerprint created_by created_at";
 static MinutesSavedGraphPlan copy(String body,String set,List<FullRow> rows,String parent,String actor,TranscriptAcquisitionBudget.Ledger budget){return MinutesSavedGraphPlan.copy(body,set,rows,parent,actor,budget);}
 static boolean sameSources(Map<String,Object> saved,Map<String,Object> current){return List.of("transcript_confirmation_id","material_snapshot_id").stream().allMatch(key->Objects.equals(saved.get(key),current.get(key)));}
 /** Called only under the existing authenticated meeting root transaction. */
 static void capture(JdbcTemplate j,String meeting,String actor){
  j.update("INSERT INTO minutes_workspace_snapshot("+COLUMNS.replace(' ', ',')+") SELECT ?,m.id,m.revision,m.minutes_revision_id,m.element_set_id,m.template_revision_id,m.exclusion_revision_id,m.transcript_confirmation_id,m.material_snapshot_id,m.needs_generation,m.generation_fingerprint,?,m.updated_at FROM meeting m WHERE m.id=? AND m.minutes_revision_id IS NOT NULL AND m.element_set_id IS NOT NULL AND m.template_revision_id IS NOT NULL AND m.exclusion_revision_id IS NOT NULL AND m.transcript_confirmation_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM minutes_workspace_snapshot s WHERE s.meeting_id=m.id AND s.revision=m.revision)",UUID.randomUUID().toString(),actor,meeting);
 }
 private MinutesWorkspaceSnapshots(){}
}
