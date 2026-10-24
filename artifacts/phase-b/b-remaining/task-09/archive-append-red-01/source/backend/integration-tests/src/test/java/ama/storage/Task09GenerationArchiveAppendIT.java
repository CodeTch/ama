package ama.storage;
import ama.transcript.*;import ama.model.*;import ama.task.*;import java.util.*;import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
/** Appended controlled defaults cannot reinterpret an already accepted original request. */
class Task09GenerationArchiveAppendIT extends Task09GenerationApplicationRestartIT {
 @Override void replayAfterRestart(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations)throws Exception{
  String oldPin=pin,oldPrompt=prompt.version(),oldModel=model.version();var root=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);install();assertNotEquals(oldPin,pin);assertNotEquals(oldPrompt,prompt.version());assertNotEquals(oldModel,model.version());assertEquals(root,j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));Files.writeString(receipt.resolve("actual-archive-default-append.json"),json.writeValueAsString(Map.of("originalIndex",oldPin,"appendedIndex",pin,"oldPrompt",oldPrompt,"newPrompt",prompt.version(),"oldModel",oldModel,"newModel",model.version(),"meetingRootUnchanged",true)));super.replayAfterRestart(candidate,preparation,client,operations);
 }
}
