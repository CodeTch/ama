package ama.storage;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix02WorkerPrefixTest {
 Map<String,Object> row(String sql,int holder,int connection){return Map.of("sql",sql,"transaction",holder>0,"holder",holder,"physical",connection);}
 List<Map<String,Object>> normal(){return List.of(row("DISCOVER",0,1),row("CAPTURE1 FOR UPDATE",2,3),row("CAPTURE2 FOR UPDATE",4,5),row(Task11Fix02WorkerPrefix.BODY,4,5),row("AFTER",0,6));}
 List<Map<String,Object>> denied(){return List.of(row("DISCOVER",0,11),row("CAPTURE1 FOR UPDATE",12,13),row("CAPTURE2 FOR UPDATE",14,15));}
 @Test void fullPrefixAndRenumberedActualConnectionsQualify(){assertEquals(0,Task11Fix02WorkerPrefix.verify(normal(),denied()).get("deniedBodyQueries"));}
 @Test void earlierDenialDoesNotQualify(){assertThrows(AssertionError.class,()->Task11Fix02WorkerPrefix.verify(normal(),denied().subList(0,2)));}
 @Test void differentSqlOrTransactionCannotQualify(){var wrong=new ArrayList<>(denied());wrong.set(2,row("OTHER FOR UPDATE",14,15));assertThrows(AssertionError.class,()->Task11Fix02WorkerPrefix.verify(normal(),wrong));wrong.set(2,row("CAPTURE2 FOR UPDATE",0,15));assertThrows(AssertionError.class,()->Task11Fix02WorkerPrefix.verify(normal(),wrong));}
 @Test void missingNormalBodyOrSecondCaptureCannotQualify(){assertThrows(AssertionError.class,()->Task11Fix02WorkerPrefix.verify(normal().subList(0,3),denied()));var n=new ArrayList<>(normal());n.set(2,row("CAPTURE2 FOR UPDATE",2,3));n.set(3,row(Task11Fix02WorkerPrefix.BODY,2,3));var d=new ArrayList<>(denied());d.set(2,row("CAPTURE2 FOR UPDATE",12,13));assertThrows(AssertionError.class,()->Task11Fix02WorkerPrefix.verify(n,d));}
}
