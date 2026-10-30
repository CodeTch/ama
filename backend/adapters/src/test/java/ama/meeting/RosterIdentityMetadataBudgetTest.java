package ama.meeting;
import ama.contract.IdentityContracts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RosterIdentityMetadataBudgetTest {
 @Test void schemaWidthAdmitsEveryLegalUnicodeIdentityAndItsOwnedCopies(){
  String supplementary="🧪".repeat(255),cjk="界".repeat(255);IdentityContracts.stableId(supplementary);IdentityContracts.stableId(cjk);
  var budget=new WorkspaceReadBudget();JdbcMeetingRepository.reserveRosterIdentityIds(budget,1);var usage=budget.usage();
  assertAll(()->assertEquals(128+4L*supplementary.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,usage.bytes()),()->assertEquals(4L*255,usage.scalars()),()->assertEquals(4L*supplementary.length(),usage.units()),()->assertTrue(usage.bytes()>=128+4L*cjk.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
 }
 @Test void aByteOrUtf16UnitBelowSchemaReservationRejectsBeforeIdAcquisition(){
  var d=WorkspaceReadBudget.DEFAULT;
  var bytes=new WorkspaceReadBudget(new WorkspaceReadBudget.Limits(4207,d.responseBytes(),d.scalars(),d.units(),d.rows(),d.edges(),d.timeout()),()->false);
  var units=new WorkspaceReadBudget(new WorkspaceReadBudget.Limits(d.bytes(),d.responseBytes(),d.scalars(),2039,d.rows(),d.edges(),d.timeout()),()->false);
  assertAll(()->assertThrows(WorkspaceReadBudget.Unavailable.class,()->JdbcMeetingRepository.reserveRosterIdentityIds(bytes,1)),()->assertThrows(WorkspaceReadBudget.Unavailable.class,()->JdbcMeetingRepository.reserveRosterIdentityIds(units,1)));
  assertEquals(0,bytes.usage().bytes());assertEquals(0,units.usage().units());
 }
}
