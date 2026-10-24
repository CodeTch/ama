package ama.storage;
import ama.transcript.*;import ama.model.*;import ama.task.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class Task09MinutesResponseBudgetIT extends Task09MinutesHttpIT {
 @Override void minutesBounds(Map<String,Object> values){super.minutesBounds(values);values.put("minutes.bounds.response-bytes",1);}
 @Override void afterExternal(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations)throws Exception{restartApi();var response=call("GET","/meetings/"+meeting+"/minutes/templates","",null);assertEquals(503,response.statusCode(),response.body());assertTrue(response.body().contains("RESPONSE_BUDGET_EXCEEDED"));assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));}
}
