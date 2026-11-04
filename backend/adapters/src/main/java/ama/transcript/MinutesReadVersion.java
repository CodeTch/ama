package ama.transcript;
import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Changes in a selected generation's asynchronous result can precede its root adoption.
 * This is a bounded retry hint only; every acquisition repeats the full authority/provenance checks. */
final class MinutesReadVersion {
 private MinutesReadVersion(){}
 static Object capture(JdbcTemplate jdbc,String meeting,String selector,String selected,BiConsumer<Integer,Long> charge){
  charge.accept(3,64L);
  var root=jdbc.queryForList("SELECT revision,access_revision,cancel_epoch FROM meeting WHERE id=? LIMIT 1",meeting);
  if(!Set.of("STORED_INPUT","CANDIDATE").contains(selector))return root;
  String selection=selector.equals("STORED_INPUT")?"t.input_id=?":"c.id=?";
  var task=jdbc.queryForList("SELECT t.id,t.state,t.attempt,t.output_id,t.last_error,c.id candidate_id,c.state candidate_state,e.id execution_id,e.state execution_state FROM durable_task t LEFT JOIN generation_candidate c ON c.task_id=t.id LEFT JOIN external_execution e ON e.task_id=t.id WHERE t.subject_kind='MEETING' AND t.subject_id=? AND t.task_type='MINUTES_GENERATE_V1' AND "+selection+" ORDER BY t.id,e.id LIMIT 257",meeting,selected);
  charge.accept(task.size()*9,task.size()*512L);require(task.size()<=256,BUDGET_EXCEEDED);
  return List.of(root,task);
 }
}
