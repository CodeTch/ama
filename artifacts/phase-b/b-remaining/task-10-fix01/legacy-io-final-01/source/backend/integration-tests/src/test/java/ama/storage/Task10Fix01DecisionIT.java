package ama.storage;
import java.util.*;import ama.transcript.*;
class Task10Fix01DecisionIT extends Task10Fix01ReplayIT {
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  var a=ama.api.Task09RuntimeProbe.components(app).authority();String id=(String)candidate.get("id");
  race(()->new JdbcMinutesDecisions(a).reject(actor(),meeting,"fix01-decision",4,id,UUID.randomUUID().toString()),new IllegalArgumentException("Decision preparation programming failure"),false);
 }
 @Override void afterExternal(Map<String,Object> candidate,MinutesExternalPreparation preparation,ama.model.ControlledMinutesClient client,ama.task.JdbcExternalOperations operations){}
}
