package ama.storage;
import ama.transcript.*;import ama.model.*;import ama.task.*;import ama.contract.MinutesContracts.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class Task09LimitsOutputIT extends Task09GenerationExternalIT {
 @Override ValidationBudget outputBudget(){return new ValidationBudget(1,32,400000,128,4194304,4194304,4194304,16777216,1000,1000,1000,10000,10000,20000);}
 @Override boolean candidateExpected(){return false;} @Override String expectedTaskState(){return "FAILED";}
 @Override void afterWorker(String task,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations,JdbcModelPermitPool permits,Throwable failure)throws Exception{assertNull(failure);assertEquals("INVALID_MODEL_OUTPUT",j.queryForObject("SELECT last_error FROM durable_task WHERE id=?",String.class,task));assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM external_execution WHERE task_id=?",String.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM model_reservation r JOIN external_execution e ON r.execution_id=e.id WHERE e.task_id=? AND r.settled_at IS NULL",Integer.class,task));}
}
