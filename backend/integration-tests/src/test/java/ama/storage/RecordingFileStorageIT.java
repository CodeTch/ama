package ama.storage;

import ama.contract.RecordingContracts;
import ama.contract.StorageContracts.*;
import ama.db.MysqlDataSources;
import ama.recording.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh local MySQL plus real mounted-volume file service over mTLS; no old evidence windows. */
class RecordingFileStorageIT {
    @Test void historicalAcknowledgedDatabasePcmStillArchivesThroughItsOriginalRoute()throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("legacy-sequence"))){
            var storage=fixture.startServer();var catalog=catalog(fixture,storage);var legacy=new RecordingService(new JdbcRecordingPersistence(source));var session=legacy.open(UUID.randomUUID().toString(),catalog.defaultWrite().id());legacy.accept(session.sessionId(),1,chunk(session));legacy.finish(session.sessionId(),1,Map.of(session.partId(),1L));
            var prepared=new RecordingArchivePreparation(new JdbcRecordingPersistence(source,catalog),catalog).prepare(session.sessionId(),()->false);
            try(var input=storage.read(prepared.blob().key(),0,prepared.blob().size())){assertArrayEquals(chunk(session).pcm(),Arrays.copyOfRange(input.readAllBytes(),44,48));}
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=? AND pcm IS NOT NULL AND pcm_storage_key IS NULL",Integer.class,session.sessionId()));
        }
    }
    @Test void archiveUsesOneVerifiedBoundedStreamForItsFrozenChunkSet()throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("sequence"))){
            var actual=fixture.startServer();var sequences=new java.util.concurrent.atomic.AtomicInteger();var individual=new java.util.concurrent.atomic.AtomicInteger();
            var observed=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{FileStoragePort.class},(proxy,method,args)->{
                if(method.getName().equals("readSequence"))sequences.incrementAndGet();if(method.getName().equals("read"))individual.incrementAndGet();
                try{return method.invoke(actual,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
            });
            var catalog=catalog(fixture,observed);var persistence=new JdbcRecordingPersistence(source,catalog);var recordings=new RecordingService(persistence);var session=recordings.open(UUID.randomUUID().toString(),catalog.defaultWrite().id());
            recordings.accept(session.sessionId(),1,new RecordingContracts.Chunk(session.partId(),1,1,0,2,new byte[]{1,2,3,4}));recordings.accept(session.sessionId(),1,new RecordingContracts.Chunk(session.partId(),1,2,2,2,new byte[]{5,6,7,8}));recordings.finish(session.sessionId(),1,Map.of(session.partId(),2L));
            var result=new RecordingArchivePreparation(persistence,catalog).prepare(session.sessionId(),()->false);
            assertEquals(1,sequences.get(),"one authenticated response must carry the exact frozen ordered chunk manifest");assertEquals(0,individual.get());assertEquals(4,result.sampleCount());
            try(var input=actual.read(result.blob().key(),0,result.blob().size())){assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8},Arrays.copyOfRange(input.readAllBytes(),44,52));}
        }
    }
    @TempDir Path root;
    static javax.sql.DataSource source;static JdbcTemplate jdbc;
    @BeforeAll static void schema() {
        String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");
        if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Fresh owned schema required");
        source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }
    FileBackendCatalog catalog(ManagedLocalFixture fixture,FileStoragePort storage) {
        var catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(fixture.binding.id(),"local",1,"WRITABLE",storage,fixture.binding)),fixture.binding.id());
        catalog.configure();return catalog;
    }
    RecordingContracts.Chunk chunk(RecordingContracts.Session s){return new RecordingContracts.Chunk(s.partId(),s.writerEpoch(),1,0,2,new byte[]{1,2,3,4});}
    @Test void ackPinsImmutableFileBytesAndRecoversWithoutDatabasePcm() throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("files"))) {
            var storage=fixture.startServer();var catalog=catalog(fixture,storage);
            var persistence=new JdbcRecordingPersistence(source,catalog);var service=new RecordingService(persistence);
            var s=service.open(UUID.randomUUID().toString(),catalog.defaultWrite().id());
            assertEquals(1,service.accept(s.sessionId(),1,chunk(s)).highestContiguousSeq());
            assertNull(jdbc.queryForObject("SELECT pcm FROM recording_chunk WHERE session_id=?",byte[].class,s.sessionId()));
            var recovered=new JdbcRecordingPersistence(source,catalog);
            assertEquals(1,new RecordingService(recovered).ack(s.sessionId(),s.partId()).highestContiguousSeq());
            try(var in=recovered.readChunk(s.sessionId(),s.partId(),1)){assertArrayEquals(chunk(s).pcm(),in.readAllBytes());}
            assertEquals(1,new RecordingService(recovered).accept(s.sessionId(),1,chunk(s)).highestContiguousSeq());
            assertEquals(1,storage.list(s.backendId(),"recordings",s.sessionId()+"/",null,10).items().size());
        }
    }
    @Test void fileWriteFailureCannotCommitManifestOrAck() throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("files"))) {
            var storage=fixture.startServer();
            var failed=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{FileStoragePort.class},(proxy,method,args)->{
                if(method.getName().equals("put"))throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);
                try{return method.invoke(storage,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
            });
            var service=new RecordingService(new JdbcRecordingPersistence(source,catalog(fixture,failed)));
            var s=service.open(UUID.randomUUID().toString(),fixture.binding.id());
            assertThrows(StorageException.class,()->service.accept(s.sessionId(),1,chunk(s)));
            assertEquals(0,service.ack(s.sessionId(),s.partId()).highestContiguousSeq());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,s.sessionId()));
        }
    }
    @Test void databaseRollbackPreservesTheSameFileForExactRetry() throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("files"))) {
            var storage=fixture.startServer();var catalog=catalog(fixture,storage);
            var service=new RecordingService(new JdbcRecordingPersistence(source,catalog));
            var s=service.open(UUID.randomUUID().toString(),catalog.defaultWrite().id());
            assertThrows(IllegalStateException.class,()->new ama.db.TransactionRunner(source).required(()->{
                service.accept(s.sessionId(),1,chunk(s));throw new IllegalStateException("controlled after-file rollback");
            }));
            assertEquals(0,service.ack(s.sessionId(),s.partId()).highestContiguousSeq());
            assertEquals(1,storage.list(s.backendId(),"recordings",s.sessionId()+"/",null,10).items().size());
            assertThrows(RecordingService.HashConflict.class,()->service.accept(s.sessionId(),1,new RecordingContracts.Chunk(s.partId(),1,0,2,new byte[]{9,9,9,9})));
            assertEquals(1,new RecordingService(new JdbcRecordingPersistence(source,catalog)).accept(s.sessionId(),1,chunk(s)).highestContiguousSeq());
            assertEquals(1,storage.list(s.backendId(),"recordings",s.sessionId()+"/",null,10).items().size());
        }
    }
    @Test void acknowledgedFileSurvivesOwnedNativeFileProcessCrash() throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("files"))) {
            var storage=fixture.startProcess();var catalog=catalog(fixture,storage);
            var service=new RecordingService(new JdbcRecordingPersistence(source,catalog));
            var s=service.open(UUID.randomUUID().toString(),catalog.defaultWrite().id());
            assertEquals(1,service.accept(s.sessionId(),1,chunk(s)).highestContiguousSeq());
            fixture.process.destroyForcibly();assertTrue(fixture.process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));
            var restarted=fixture.startProcess();var recovered=new JdbcRecordingPersistence(source,catalog(fixture,restarted));
            assertEquals(1,new RecordingService(recovered).ack(s.sessionId(),s.partId()).highestContiguousSeq());
            try(var input=recovered.readChunk(s.sessionId(),s.partId(),1)){assertArrayEquals(chunk(s).pcm(),input.readAllBytes());}
            assertEquals(1,new RecordingService(recovered).accept(s.sessionId(),1,chunk(s)).highestContiguousSeq());
        }
    }
    @Test void archiveStreamsEveryPartIntoVerifiedWavAndReplaysTheSameObject() throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("files"))) {
            var storage=fixture.startServer();var catalog=catalog(fixture,storage);var persistence=new JdbcRecordingPersistence(source,catalog);
            var service=new RecordingService(persistence);var first=service.open(UUID.randomUUID().toString(),catalog.defaultWrite().id());
            service.accept(first.sessionId(),1,chunk(first));service.pause(first.sessionId(),1);var second=service.nextPart(first.sessionId(),1);
            service.accept(second.sessionId(),2,chunk(second));
            var archives=new RecordingArchivePreparation(persistence,catalog);
            assertThrows(IllegalStateException.class,()->archives.prepare(first.sessionId(),()->false));
            service.finish(first.sessionId(),2,Map.of(first.partId(),1L,second.partId(),1L));
            var result=archives.prepare(first.sessionId(),()->false);assertEquals(4,result.sampleCount());assertEquals(52,result.blob().size());
            assertEquals(List.of(first.partId(),second.partId()),result.parts().stream().map(RecordingArchivePreparation.PartRange::partId).toList());
            assertEquals(0,result.parts().getFirst().startSample());assertEquals(2,result.parts().getLast().startSample());
            byte[] wav;try(var in=storage.read(result.blob().key(),0,result.blob().size())){wav=in.readAllBytes();}
            assertEquals("RIFF",new String(wav,0,4,java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals(16000,java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(24));
            assertArrayEquals(new byte[]{1,2,3,4,1,2,3,4},Arrays.copyOfRange(wav,44,wav.length));
            assertEquals(result.blob(),archives.prepare(first.sessionId(),()->false).blob());
            assertThrows(java.util.concurrent.CancellationException.class,()->archives.prepare(first.sessionId(),()->true));
        }
    }
    @Test void corruptedChunkCannotPublishAnArchive() throws Exception {
        try(var fixture=new ManagedLocalFixture(root.resolve("files"))) {
            var storage=fixture.startServer();var catalog=catalog(fixture,storage);var persistence=new JdbcRecordingPersistence(source,catalog);
            var service=new RecordingService(persistence);var s=service.open(UUID.randomUUID().toString(),catalog.defaultWrite().id());
            service.accept(s.sessionId(),1,chunk(s));service.finish(s.sessionId(),1,Map.of(s.partId(),1L));
            jdbc.update("UPDATE recording_chunk SET sha256=? WHERE session_id=?","0".repeat(64),s.sessionId());
            assertThrows(IllegalStateException.class,()->new RecordingArchivePreparation(persistence,catalog).prepare(s.sessionId(),()->false));
            assertTrue(storage.list(s.backendId(),"recordings",s.sessionId()+"/archive-v1/",null,10).items().isEmpty());
        }
    }
    @Test void archiveRegistrationUploadPinsOriginalBackendDespiteChangedDefault() throws Exception {
        try(var original=new ManagedLocalFixture(root.resolve("original"));var replacement=new ManagedLocalFixture(root.resolve("replacement"))) {
            var a=original.startServer();var b=replacement.startServer();
            var catalog=new FileBackendCatalog(source,List.of(
                    new FileBackendCatalog.Backend(original.binding.id(),"local",1,"WRITABLE",a,original.binding),
                    new FileBackendCatalog.Backend(replacement.binding.id(),"local",1,"WRITABLE",b,replacement.binding)),replacement.binding.id());catalog.configure();
            var tx=new ama.db.TransactionRunner(source);var fences=new ama.task.JdbcSubjectFences(source,Set.of("MEETING"));
            var access=new ama.meeting.JdbcMeetingAccess(source,tx,fences);var outbox=new ama.task.JdbcOutbox(source,new ama.meeting.MeetingEventReferences(source).validators());
            String owner="recording-register-"+UUID.randomUUID();
            jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);
            var actor=new ama.contract.IdentityContracts.Actor(owner,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
            var meetings=new ama.meeting.MeetingService(new ama.meeting.JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),id->{}));
            String meeting=meetings.create(actor,UUID.randomUUID().toString(),"owned-recording-register").id();
            FilePurposePolicy purposes=(person,id,purpose)->{if(!purpose.equals("RECORDING_ARCHIVE"))throw new FilePurposePolicy.Unavailable();};
            var components=ama.transcript.JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of(),ama.minutes.ControlledMinutesAdmin.READ,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,4194304,250000));
            FileUploads uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,new ama.audit.JdbcAuditAdapter(source),outbox,1024,components.authority().publicationWriteGuard());
            byte[] bytes={1,2,3,4};String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            var request=new ama.contract.FileContracts.Begin("RECORDING_ARCHIVE","录音.wav",bytes.length,hash);String key=UUID.randomUUID().toString();
            var accepted=uploads.beginPinned(actor,meeting,key,request,"owned-recording-register",original.binding.id());
            uploads.part(actor,accepted.uploadId(),1,new ByteArrayInputStream(bytes),bytes.length,hash,"owned-recording-register");
            var completed=uploads.complete(actor,accepted.uploadId(),new ama.contract.FileContracts.Completion(List.of(new ama.contract.FileContracts.ClientPart(1,hash)),hash),"owned-recording-register");
            assertEquals(original.binding.id(),jdbc.queryForObject("SELECT backend_id FROM file_object WHERE id=?",String.class,completed.fileId()));
            assertEquals(accepted,uploads.beginPinned(actor,meeting,key,request,"owned-recording-register",original.binding.id()));
            assertThrows(ama.contract.FileContracts.Conflict.class,()->uploads.beginPinned(actor,meeting,key,request,"owned-recording-register",replacement.binding.id()));
            assertTrue(b.list(replacement.binding.id(),"files","",null,10).items().isEmpty());
        }
    }
}
