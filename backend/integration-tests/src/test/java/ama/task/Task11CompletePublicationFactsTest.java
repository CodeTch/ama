package ama.task;
import ama.transcript.*;
import ama.contract.TaskContracts.*;
import ama.contract.IdentityContracts.*;
import ama.access.MeetingAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Genuine guard/transaction plus real Task7 publication-fact carrier; JDBC and ACL are substituted. */
class Task11CompletePublicationFactsTest {
 static final String M="12345678-1234-4234-8234-123456789ac1";
 static final Actor A=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 static final TaskSpec SPEC=new TaskSpec("MINUTES_GENERATE_V1","MINUTES_GENERATE",new SubjectRef("MEETING",M),"a".repeat(64),new TypedRef("GENERATION_INPUT",M),"b".repeat(64),1,0,0,3);
 @Test void completeFactsRequireAuthenticTaskRankAndRetainTheActualHolder()throws Exception{
  var f=Task11PublicationWriteGuardBoundaryTest.retentionFixture();var issuer=new JdbcTaskPreparations(f.source());
  var stored=f.tx().required(()->{var domain=issuer.issue(List.of(SPEC),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,new Object(),true);var h=f.guard().controls(f.prepared(),A,M,f.gate());
   assertThrows(IllegalArgumentException.class,()->JdbcGenerationRetention.completePublication(f.source(),domain,M,f.guard(),h,f.gate()));f.guard().tasks(h,f.gate());
   var facts=JdbcGenerationRetention.completePublication(f.source(),domain,M,f.guard(),h,f.gate());assertFalse(JdbcGenerationRetention.revisitPublication(f.source(),domain,M,facts).frozen(f.source(),domain,M));
   var other=issuer.issue(List.of(SPEC),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,new Object(),true);assertThrows(IllegalArgumentException.class,()->JdbcGenerationRetention.revisitPublication(f.source(),other,M,facts));
   assertThrows(IllegalArgumentException.class,()->JdbcGenerationRetention.completePublication(f.source(),domain,M,f.guard(),h,mock(MeetingAccess.Locked.class)));
   return new Object[]{domain,facts};});
  assertThrows(IllegalArgumentException.class,()->f.tx().required(()->JdbcGenerationRetention.revisitPublication(f.source(),(TaskDomainPolicy.PreparedDomain)stored[0],M,(GenerationRetentionPolicy.PublicationPreparation)stored[1])));
 }
 @Test void currentRootFreezeSuppressesRetentionThroughActualFrameworkRevisit()throws Exception{
  var f=Task11PublicationWriteGuardBoundaryTest.retentionFixture();var issuer=new JdbcTaskPreparations(f.source());f.tx().required(()->{var domain=issuer.issue(List.of(SPEC),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,new Object(),true);var h=f.guard().controls(f.prepared(),A,M,f.gate());f.guard().tasks(h,f.gate());var facts=JdbcGenerationRetention.completePublication(f.source(),domain,M,f.guard(),h,f.gate());f.frozen().set(true);assertTrue(JdbcGenerationRetention.revisitPublication(f.source(),domain,M,facts).frozen(f.source(),domain,M));return null;});
 }
}
