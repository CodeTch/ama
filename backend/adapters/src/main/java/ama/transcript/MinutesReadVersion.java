package ama.transcript;
import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Editor intents, detached history and asynchronous results can precede a meeting revision.
 * This is a bounded retry hint only; every acquisition repeats the full authority/provenance checks. */
final class MinutesReadVersion {
 private MinutesReadVersion(){}
 static Object capture(JdbcTemplate jdbc,String meeting,String selector,String selected,BiConsumer<Integer,Long> charge){
  charge.accept(12,2048L);
  var root=jdbc.queryForList("SELECT m.revision,m.access_revision,m.cancel_epoch,g.intent_epoch,(SELECT id FROM minutes_revision FORCE INDEX(minutes_history) WHERE meeting_id=m.id ORDER BY revision DESC LIMIT 1) body_history,(SELECT id FROM element_set FORCE INDEX(element_set_revision) WHERE meeting_id=m.id ORDER BY revision DESC LIMIT 1) set_history,(SELECT id FROM exclusion_revision FORCE INDEX(exclusion_version) WHERE meeting_id=m.id ORDER BY revision DESC LIMIT 1) exclusion_history,(SELECT id FROM result_review FORCE INDEX(review_sequence) WHERE meeting_id=m.id ORDER BY review_no DESC LIMIT 1) review_history,p.id publication_id,p.state publication_state,p.revision publication_revision FROM meeting m LEFT JOIN meeting_minutes_editor_guard g ON g.meeting_id=m.id LEFT JOIN publication p ON p.meeting_id=m.id WHERE m.id=? LIMIT 1",meeting);
  boolean storedInput=selector.equals("STORED_INPUT")||selector.equals("STORED_INPUT_READ");
  if(!storedInput&&!Set.of("CANDIDATE","MEETING_TASKS").contains(selector))return root;
  boolean all=selector.equals("MEETING_TASKS");
  String selection=all?"1=1":storedInput?"t.input_id=?":"c.id=?";
  var task=jdbc.queryForList("SELECT t.id,t.state,t.attempt,t.output_id,t.last_error,t.lease_owner,t.expires_at,t.available_at,c.id candidate_id,c.state candidate_state,e.id execution_id,e.state execution_state,e.task_attempt,e.marked_at,e.finished_at,e.no_effect_verified_at,o.state operation_state,o.next_check_at,o.reconcile_epoch,o.reconcile_owner,o.reconcile_expires_at,o.reconcile_scan_seq,OCTET_LENGTH(o.external_result_id) operation_result_bytes,SHA2(o.external_result_id,256) operation_result_hash,o.last_error operation_error,a.ended_at,a.outcome,a.error_code FROM durable_task t LEFT JOIN generation_candidate c ON c.task_id=t.id LEFT JOIN external_execution e ON e.task_id=t.id LEFT JOIN external_operation o ON o.id=e.operation_id LEFT JOIN task_attempt a ON a.task_id=t.id AND a.attempt=t.attempt WHERE t.subject_kind='MEETING' AND t.subject_id=? AND "+selection+" ORDER BY t.id,e.id LIMIT 257",all?new Object[]{meeting}:new Object[]{meeting,selected});
  charge.accept(task.size()*29,task.size()*2048L);require(task.size()<=256,BUDGET_EXCEEDED);
  return List.of(root,task);
 }
}
