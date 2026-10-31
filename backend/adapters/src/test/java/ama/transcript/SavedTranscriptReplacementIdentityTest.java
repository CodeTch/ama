package ama.transcript;
import ama.contract.TranscriptContracts.*;
import ama.contract.IdentityContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SavedTranscriptReplacementIdentityTest {
 static final String REV="00000000-0000-4000-8000-000000000001",SEG="00000000-0000-4000-8000-000000000002",MEETING="00000000-0000-4000-8000-000000000003";
 static final Actor OWNER=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 @Test void differentReplacementBodiesAndSelectedOccurrencesCannotReplayTheSameRequest(){
  var one=new Command(Operation.REPLACE,7,REV,List.of(),new Replacement("甲","乙",List.of("a".repeat(64))));
  var text=new Command(Operation.REPLACE,7,REV,List.of(),new Replacement("甲","丙",List.of("a".repeat(64))));
  var occurrence=new Command(Operation.REPLACE,7,REV,List.of(),new Replacement("甲","乙",List.of("b".repeat(64))));
  var original=JdbcSavedTranscriptReceipts.identity(OWNER,MEETING,"same-key",one);
  var changed=JdbcSavedTranscriptReceipts.identity(OWNER,MEETING,"same-key",text);
  assertEquals(original.scope(),changed.scope());assertNotEquals(original.request(),changed.request());assertNotEquals(original.request(),JdbcSavedTranscriptReceipts.identity(OWNER,MEETING,"same-key",occurrence).request());
 }
 @Test void originalSaveRequestHashRemainsExactlyVersionOne(){
  var command=new Command(Operation.SAVE,7,REV,List.of(new Edit(SEG,"人工原文",List.of())));
  assertEquals("f9a2f1e449127391226a72e7168dd60aa5091da1dac9795d5b819eaea76167bf",JdbcSavedTranscriptReceipts.identity(OWNER,MEETING,"same-key",command).request());
 }
}
