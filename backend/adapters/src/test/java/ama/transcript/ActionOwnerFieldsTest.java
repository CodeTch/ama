package ama.transcript;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ActionOwnerFieldsTest {
 @Test void permitsAnIdentityTupleForSubsequentGenuineGateValidation(){var fields=new JdbcMinutesSavedCommands.ActionFields("dev-member","开发参会人","2026-11-01");assertEquals("dev-member",fields.ownerEnterpriseId());}
 @Test void keepsDisplayOnlyAndUnspecifiedFieldsAvailable(){assertDoesNotThrow(()->new JdbcMinutesSavedCommands.ActionFields(null,"外部负责人",null));assertDoesNotThrow(()->new JdbcMinutesSavedCommands.ActionFields(null,null,null));}
 @Test void rejectsMalformedOrIncompleteIdentityTuple(){for(String id:new String[]{""," ","x".repeat(256),"\ud800"})assertThrows(RuntimeException.class,()->new JdbcMinutesSavedCommands.ActionFields(id,"name",null));assertThrows(RuntimeException.class,()->new JdbcMinutesSavedCommands.ActionFields("id",null,null));}
 @Test void retainsExactCalendarDateAndUnicodeChecks(){assertThrows(RuntimeException.class,()->new JdbcMinutesSavedCommands.ActionFields(null,"\ud800",null));assertThrows(RuntimeException.class,()->new JdbcMinutesSavedCommands.ActionFields(null,"name","2026-02-30"));}
}
