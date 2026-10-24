package ama.task;
import ama.contract.TaskContracts.TaskSpec;import ama.contract.ExternalContracts.ExternalRequest;import java.util.*;
/** Fixed registered publication transition; DTOs cannot authenticate a prepared replacement. */
public interface PublicationReplacementPolicy extends PublicationLocalTerminationPolicy {
 record StoredPublicationExecution(String manifest,String evidence){}
 StoredPublicationExecution readPublicationExecution(PreparedDomain held,TaskSpec spec,String task,String operation,String execution,long number);
 interface PreparedPublicationReplacement {TaskSpec spec();}
 interface ReplacementContext {}
 record ReplacementBinding(TaskSpec next,String taskId,List<TaskSpec> specs,Map<String,TaskSpec> oldTasks,PreparedDomain prepared,String operationId,long expectedExecutionNo,String previousLogicalHash,String executionId,ExternalRequest request){
  public ReplacementBinding{specs=List.copyOf(specs);oldTasks=Map.copyOf(oldTasks);}
 }
 record ReplacementAccepted(String taskId,String operationId,String executionId,Long executionNo){}
 ReplacementBinding requireReplacement(PreparedPublicationReplacement prepared);
 void lockReplacementRemainder(PreparedDomain domain,ReplacementContext context,PreparedPublicationReplacement prepared);
}
