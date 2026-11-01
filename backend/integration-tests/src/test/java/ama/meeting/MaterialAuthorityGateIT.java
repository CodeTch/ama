package ama.meeting;

import ama.access.MeetingAccess.*;
import ama.contract.IdentityContracts.*;
import ama.db.*;
import ama.task.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Local ACL preparation is not a content gate and cannot make external requests for outsiders. */
class MaterialAuthorityGateIT {
 JdbcTemplate j;JdbcMeetingAccess access;TransactionRunner tx;Actor owner,outsider;String meeting;
 @BeforeEach void fixture(){
  var source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);
  org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
  tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);
  owner=actor();outsider=actor();meeting=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,new JdbcOutbox(source,new MeetingEventReferences(source).validators()),new ama.audit.JdbcAuditAdapter(source),x->{})).create(owner,UUID.randomUUID().toString(),"material-gate").id();
 }
 Actor actor(){String id="material-gate-"+UUID.randomUUID();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);return new Actor(id,Status.ACTIVE,Set.of(Role.USER));}
 @Test void preparationVerifiesLocalIdentityAndAclOutsideTheExternalCallback(){
  var calls=new AtomicInteger();access.installExternalPreparation((actor,id)->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(owner,actor);assertEquals(meeting,id);calls.incrementAndGet();});
  access.prepareExternalRead(owner,meeting);assertEquals(1,calls.get());
  assertThrows(AccessDeniedException.class,()->access.prepareExternalRead(outsider,meeting));assertEquals(1,calls.get());
  assertThrows(IllegalStateException.class,()->tx.required(()->{access.prepareExternalRead(owner,meeting);return null;}));
  j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner.enterpriseId());assertThrows(AccessDeniedException.class,()->access.prepareExternalRead(owner,meeting));assertEquals(1,calls.get());
 }
 @Test void missingExternalProofDoesNotLeakAvailabilityToAnOutsiderOrGrantContentFromPreparation(){
  access.installPublicationValidity();j.update("UPDATE meeting SET ever_published=1 WHERE id=?",meeting);
  var calls=new AtomicInteger();access.installExternalPreparation((actor,id)->calls.incrementAndGet());
  access.prepareExternalRead(owner,meeting);assertEquals(1,calls.get());
  assertThrows(AuthorityUnavailableException.class,()->access.require(owner,meeting,Action.READ));
  assertThrows(AccessDeniedException.class,()->access.require(outsider,meeting,Action.READ));
  j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",meeting);
  assertThrows(AccessDeniedException.class,()->access.prepareExternalRead(owner,meeting));assertEquals(1,calls.get());
 }
 String knowledge(){String ref=UUID.randomUUID().toString();j.update("INSERT INTO material_reference(id,meeting_id,kind,provider_id,external_id,state) VALUES (?,?,'KNOWLEDGE','provider','document','ACTIVE')",ref,meeting);j.update("INSERT INTO material_knowledge_location(reference_id,library_id) VALUES (?,'library')",ref);revision(ref,0,"a".repeat(64));return ref;}
 void revision(String ref,int no,String version){j.update("INSERT INTO material_revision(id,reference_id,revision,external_version,content_hash,parse_state,created_at) VALUES (?,?,?,?,?,'READY',UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),ref,no,version,version);}
 void cache(String actor,String pins,String state,int seconds){String hash=MaterialAuthorityDependencies.capture(j,meeting).fingerprint();j.update("INSERT INTO material_authority_cache(meeting_id,actor_id,dependency_hash,pins_hash,proof_hash,state,verified_until,demand_until,next_check_at) VALUES (?,?,?,?,?,?,TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE dependency_hash=VALUES(dependency_hash),pins_hash=VALUES(pins_hash),state=VALUES(state),verified_until=VALUES(verified_until)",meeting,actor,hash,pins,"c".repeat(64),state,seconds);}
 @Test void exactProofCacheIsRequiredForActorPinsAndAllRetainedVersions(){
  String ref=knowledge(),pins="d".repeat(64);access.installMaterialValidity(pins);
  assertThrows(AuthorityUnavailableException.class,()->access.require(owner,meeting,Action.READ));
  cache(outsider.enterpriseId(),pins,"ACTIVE",5);assertThrows(AuthorityUnavailableException.class,()->access.require(owner,meeting,Action.READ));
  cache(owner.enterpriseId(),"e".repeat(64),"ACTIVE",5);assertThrows(AuthorityUnavailableException.class,()->access.require(owner,meeting,Action.READ));
  cache(owner.enterpriseId(),pins,"ACTIVE",5);access.require(owner,meeting,Action.READ);
  tx.required(()->{var gate=access.lock(owner,meeting);var until=access.readAuthorityDeadline(gate).orElseThrow();assertTrue(until.isAfter(java.time.Instant.now()));assertTrue(until.isBefore(java.time.Instant.now().plusSeconds(6)));return null;});
  revision(ref,1,"b".repeat(64));assertThrows(AuthorityUnavailableException.class,()->access.require(owner,meeting,Action.READ));
  j.update("UPDATE material_reference SET state='REMOVED' WHERE id=?",ref);assertThrows(AuthorityUnavailableException.class,()->access.require(owner,meeting,Action.READ));
  cache(owner.enterpriseId(),pins,"ACTIVE",5);access.require(owner,meeting,Action.READ);
 }
 @Test void expiryIsRetryableButAnExplicitDenialIsNot(){
  knowledge();String pins="d".repeat(64);access.installMaterialValidity(pins);cache(owner.enterpriseId(),pins,"ACTIVE",-1);
  assertThrows(AuthorityUnavailableException.class,()->access.require(owner,meeting,Action.READ));
  cache(owner.enterpriseId(),pins,"DENIED",-1);assertThrows(AccessDeniedException.class,()->access.require(owner,meeting,Action.READ));
 }
 @Test void completeListCountsRequireExactCurrentExternalProofAndNeverProbeOutsiders(){
  String reference=knowledge(),pins="d".repeat(64);access.installMaterialValidity(pins);var calls=new AtomicInteger();access.installExternalPreparation((actor,id)->calls.incrementAndGet());
  var reader=new JdbcWebMeetingProjectionReader(j.getDataSource(),tx,access,new WorkspaceCursor(),WorkspaceReadBudget.DEFAULT);
  assertEquals(0,reader.list(outsider,"owned",10,null).total());assertEquals(0,calls.get());
  assertEquals(0,reader.list(owner,"owned",10,null,new MeetingListFilter("definitely-not-matching",Set.of(),Set.of(),null,null)).total());assertEquals(0,calls.get());
  assertThrows(AuthorityUnavailableException.class,()->reader.list(owner,"owned",10,null));
  cache(owner.enterpriseId(),pins,"ACTIVE",5);var active=reader.list(owner,"owned",10,null);assertEquals(1,active.total());assertEquals(List.of(meeting),active.items().stream().map(ama.contract.WorkspaceContracts.MeetingItem::id).toList());
  revision(reference,1,"b".repeat(64));assertThrows(AuthorityUnavailableException.class,()->reader.list(owner,"owned",10,null));
  cache(owner.enterpriseId(),pins,"DENIED",-1);var denied=reader.list(owner,"owned",10,null);assertEquals(0,denied.total());assertTrue(denied.items().isEmpty());assertTrue(denied.topicGroups().isEmpty());assertTrue(denied.stageCounts().values().stream().allMatch(n->n==0));
 }

}
