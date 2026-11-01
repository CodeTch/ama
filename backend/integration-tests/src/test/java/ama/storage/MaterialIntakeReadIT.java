package ama.storage;
import ama.contract.IdentityContracts.*;
import ama.contract.FileContracts.*;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.db.*;
import ama.meeting.*;
import ama.task.*;
import ama.transcript.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Actual completed unbound upload -> owner-only intake -> parse -> reference. Public downloads stay closed. */
class MaterialIntakeReadIT {
 @TempDir Path root;static javax.sql.DataSource source;static JdbcTemplate j;
 static String id(){return UUID.randomUUID().toString();}
 @BeforeAll static void schema(){source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
 class Fixture implements AutoCloseable{
  final ManagedLocalFixture files;final JdbcTranscriptAuthority authority;final JdbcMeetingAccess access;final FileRegistry registry;final FileUploads uploads;final JdbcMaterials materials;final MeetingService meetings;final Actor owner,outsider;final String meeting;
  Fixture()throws Exception{
   files=new ManagedLocalFixture(root.resolve(id()));var storage=files.startServer();var catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(files.binding.id(),"local",1,"WRITABLE",storage,files.binding)),files.binding.id());catalog.configure();var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);var outbox=new JdbcOutbox(source,new MeetingEventReferences(source).validators());var audit=new ama.audit.JdbcAuditAdapter(source);meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,audit,x->{}));owner=actor();outsider=actor();meeting=meetings.create(owner,id(),id()).id();
   var budget=new ArchiveReadBudget(65536,1048576,64,128,16,200000,32,255,65536,262144,1048576,65536,32,250000,500000);var purposes=new TranscriptFilePurposePolicy();var components=JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of(),budget,new CanonicalBudget(16777216,32,1000000,4194304,250000));authority=components.authority();registry=components.registry();uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,audit,outbox,1024*1024,components.authority().publicationWriteGuard());materials=new JdbcMaterials(components.authority(),uploads,null,new ama.material.MaterialParser.Budget(1024*1024,1024*1024,100));
  }
  Actor actor(){String who="material-intake-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",who,who);return new Actor(who,Status.ACTIVE,Set.of(Role.USER));}
  String upload(String purpose){byte[] bytes="第一段\n\n第二段".getBytes(java.nio.charset.StandardCharsets.UTF_8);String sha=ama.material.MaterialDocumentParser.sha(bytes);var accepted=uploads.begin(owner,meeting,id(),new Begin(purpose,"material.txt",bytes.length,sha),id());uploads.part(owner,accepted.uploadId(),1,new ByteArrayInputStream(bytes),bytes.length,sha,id());return uploads.complete(owner,accepted.uploadId(),new Completion(List.of(new ClientPart(1,sha)),sha),id()).fileId();}
  public void close()throws Exception{files.close();}
 }
 @Test void completedUnboundMaterialHasNoPublicReadButCanBeParsedAndBoundByItsMeetingOwner()throws Exception{
  try(var f=new Fixture()){String file=f.upload("MATERIAL_SOURCE");assertThrows(NotFound.class,()->f.registry.download(f.owner,file,null));
   var result=f.materials.add(f.owner,f.meeting,"original",new JdbcMaterials.Selection(file,null,null,null),id());assertEquals("READY",result.state());assertEquals(file,result.fileId());assertEquals(result,f.materials.add(f.owner,f.meeting,"original",new JdbcMaterials.Selection(file,null,null,null),id()));
   assertEquals(1,f.materials.list(f.owner,f.meeting).items().size());try(var download=f.registry.download(f.owner,file,null)){assertEquals("第一段\n\n第二段",new String(download.body().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.registry.download(f.outsider,file,null));
  }
 }
 @Test void anUnboundUploadCannotCrossMeetingOwnerOrOriginalPurpose()throws Exception{
  try(var f=new Fixture()){String material=f.upload("MATERIAL_SOURCE"),transcript=f.upload("TRANSCRIPT_INPUT"),other=f.meetings.create(f.owner,id(),id()).id();
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.materials.add(f.outsider,f.meeting,id(),new JdbcMaterials.Selection(material,null,null,null),id()));
   assertThrows(NotFound.class,()->f.materials.add(f.owner,other,id(),new JdbcMaterials.Selection(material,null,null,null),id()));
   assertThrows(NotFound.class,()->f.materials.add(f.owner,f.meeting,id(),new JdbcMaterials.Selection(transcript,null,null,null),id()));
   assertTrue(f.materials.list(f.owner,f.meeting).items().isEmpty());assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,f.meeting));
  }
 }
 @Test void aConcurrentRefreshWaitsOutsideTheTransactionForTheSameFreshProof()throws Exception{
  try(var f=new Fixture();var client=org.mockito.Mockito.mock(ControlledKnowledgeClient.class)){
   var pins=Map.of("providerId","provider","storeInstanceId","store","clientId","client","delegationKeyId","delegation","assertionKeyId","assertion");org.mockito.Mockito.when(client.identities()).thenReturn(pins);
   try(var renewal=new JdbcMaterialAuthority(f.authority,client,null)){
    leasedProof(f,pins);var entered=new java.util.concurrent.CountDownLatch(1);
    try(var thread=java.util.concurrent.Executors.newSingleThreadExecutor()){
     var waiting=thread.submit(()->{entered.countDown();f.access.prepareExternalRead(f.owner,f.meeting);f.access.require(f.owner,f.meeting,ama.access.MeetingAccess.Action.READ);});
     assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));Thread.sleep(200);
     // This update needs the same cache row. It can finish only if waiting releases its DB locks.
     j.update("UPDATE material_authority_cache SET state='ACTIVE',verified_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 SECOND),lease_owner=NULL,lease_until=NULL WHERE meeting_id=? AND actor_id=?",f.meeting,f.owner.enterpriseId());
     assertDoesNotThrow(()->waiting.get(3,java.util.concurrent.TimeUnit.SECONDS));
    }
   }
  }
 }
 @Test void aStalledRefreshCannotAuthorizeAnExpiredProofAndWaitIsBounded()throws Exception{
  try(var f=new Fixture();var client=org.mockito.Mockito.mock(ControlledKnowledgeClient.class)){
   var pins=Map.of("providerId","provider","storeInstanceId","store","clientId","client","delegationKeyId","delegation","assertionKeyId","assertion");org.mockito.Mockito.when(client.identities()).thenReturn(pins);
   try(var renewal=new JdbcMaterialAuthority(f.authority,client,null)){
    leasedProof(f,pins);long started=System.nanoTime();
    assertThrows(ama.access.MeetingAccess.AuthorityUnavailableException.class,()->f.access.prepareExternalRead(f.owner,f.meeting));
    assertTrue(System.nanoTime()-started<java.util.concurrent.TimeUnit.SECONDS.toNanos(4));
    assertThrows(ama.access.MeetingAccess.AuthorityUnavailableException.class,()->f.access.require(f.owner,f.meeting,ama.access.MeetingAccess.Action.READ));
   }
  }
 }
 void leasedProof(Fixture f,Map<String,String> pins)throws Exception{
  String ref=id();j.update("INSERT INTO material_reference(id,meeting_id,kind,provider_id,external_id,state) VALUES (?,?,'KNOWLEDGE','provider','document','ACTIVE')",ref,f.meeting);j.update("INSERT INTO material_knowledge_location(reference_id,library_id) VALUES (?,'library')",ref);j.update("INSERT INTO material_revision(id,reference_id,revision,external_version,content_hash,parse_state,created_at) VALUES (?,?,0,?,?,'READY',UTC_TIMESTAMP(6))",id(),ref,"a".repeat(64),"a".repeat(64));
  String hash=MaterialAuthorityDependencies.capture(j,f.meeting).fingerprint(),pinsHash=ama.material.MaterialDocumentParser.sha(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(new TreeMap<>(pins)));
  j.update("INSERT INTO material_authority_cache(meeting_id,actor_id,dependency_hash,pins_hash,proof_hash,state,verified_until,demand_until,next_check_at,lease_owner,lease_until) VALUES (?,?,?,?,?,'ACTIVE',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 SECOND))",f.meeting,f.owner.enterpriseId(),hash,pinsHash,"c".repeat(64),id());
 }

}
