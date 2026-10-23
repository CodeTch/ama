package ama.storage;
import ama.api.ApiApplication;
import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.support.MysqlTestDatabase;
import ama.transcript.TranscriptFilePurposePolicy;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ama.transcript.ArchiveProcessingFixture.B;
class ProcessingIntakeHttpIT {
    ConfigurableApplicationContext app;
    ManagedRegistryFixture storage;
    Task04ProbeSource source;
    RegistryObservedStorage observed;
    Map<String,Object> deploymentBounds;
    volatile boolean purposeEnabled=true;
    JdbcTemplate j;
    final ObjectMapper json=new ObjectMapper();
    String owner,token,meeting;
    final String csrf="T".repeat(43),origin="https://task04.example.invalid";
    final List<String> ownedTasks=new ArrayList<>();
    final Map<String,Map<String,Object>> acceptedSpecs=new LinkedHashMap<>();
    static final String SPEC_COLUMNS="id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts";
    void track(String task) {
        var spec=j.queryForMap("SELECT "+SPEC_COLUMNS+" FROM durable_task WHERE id=?",task);
        assertEquals("TRANSCRIPT_IMPORT_V1",spec.get("task_type"));
        assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
        assertFalse(acceptedSpecs.containsKey(task));
        acceptedSpecs.put(task,Map.copyOf(spec));
        ownedTasks.add(task);
    }
    volatile java.util.function.UnaryOperator<ama.transcript.JdbcProcessingIntake.NewIntake> preparationDecorator=java.util.function.UnaryOperator.identity();
    Path archive;
    String pin;
    Ref processing;
    @BeforeEach void start(TestInfo test) throws Exception {
        source=new Task04ProbeSource(MysqlTestDatabase.dataSource());
        j=new JdbcTemplate(source);
        Path parent=Path.of("target/registry-fixtures").toAbsolutePath().normalize();
        Files.createDirectories(parent);
        Path root=parent.resolve("task04-"+UUID.randomUUID());
        assertTrue(root.startsWith(parent));
        assertFalse(Files.exists(root));
        Files.createDirectory(root);
        Task04Pki.create();
        storage=new ManagedRegistryFixture(root);
        observed=new RegistryObservedStorage(storage.storage);
        archive=Path.of("target").toAbsolutePath().normalize().resolve("task04-archives-"+UUID.randomUUID());
        assertFalse(Files.exists(archive));
        Files.createDirectory(archive);
        var raw=new ArrayList<RawArchive>();
        for(String name:List.of("codec","confirmation","parser","processing1")) {
            var type=switch(name) {
                case "codec"->ArtifactType.CANONICAL_CODEC;
                case "confirmation"->ArtifactType.CONFIRMATION_HASH;
                case "parser"->ArtifactType.PARSER_CONFIG;
                default->ArtifactType.PROCESSING_CONFIG;
            };
            String version=switch(name) {
                case "codec"->"ama.canonical-value.v1";
                case "confirmation"->"ama.transcript-confirmation.v1";
                case "parser"->"ama.strict-utf8-lines.v1";
                default->"b06.controlled.import-txt.1";
            };
            try(var in=getClass().getResourceAsStream("/ama/transcript/restart/"+name+".json")) {
                raw.add(new RawArchive(type,version,in.readAllBytes()));
            }
        }
        var ib=new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128);
        pin=new ControlledArchiveReleaseWriter().publish(archive,null,raw,ib,B,()->false);
        var snapshot=new ControlledConfigArchiveSource().open(archive,pin,ib,B,()->false);
        processing=snapshot.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();
        var bounds=new HashMap<String,Object>();
        String[] names= {
            "artifact-bytes","archive-total-bytes","artifacts","dependencies","graph-depth","json-tokens","json-depth","name-utf16","string-utf16","string-utf8","aggregate-scalars","manifest-bytes","batch-files","fact-rows","fact-edges","canonical-bytes","canonical-depth","canonical-nodes","canonical-string-utf8","canonical-scalars","index-bytes","index-entries","release-bytes","index-generations","total-source-bytes"
        };
        long[] values= {
            65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000,16777216,32,1000000,2097152,250000,4194304,4096,67108864,128,1048576
        };
        for(int n=0;n<names.length;n++)bounds.put("processing.bounds."+names[n],values[n]);
        deploymentBounds=Map.copyOf(bounds);
        app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds).initializers(context-> {
            var c=(GenericApplicationContext)context;
            c.registerBean("task04Preparation",ama.transcript.JdbcProcessingIntake.NewIntake.class,()-> {
                var actual=(ama.transcript.JdbcProcessingIntake.NewIntake)c.getBean("newIntakeBranch");
                return (a,m,r)->preparationDecorator.apply(actual).prepare(a,m,r);
            },definition->definition.setPrimary(true));
            c.registerBean("task04DataSource",javax.sql.DataSource.class,()->source);
            c.registerBean("task04Purpose",FilePurposePolicy.class,()->(a,m,p)-> {
                if(!purposeEnabled)throw new FilePurposePolicy.Unavailable();
                new TranscriptFilePurposePolicy().require(a,m,p);
            });
            c.registerBean("task04Backends",FileBackendDefinitions.class,()->new FileBackendDefinitions(List.of(storage.backend(observed)),storage.binding.id()));
        }).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR", "--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin, "--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256(),"--processing.max-total-source-bytes=1048576");
        owner="task04-"+UUID.randomUUID();
        token=identity(owner,Set.of("redirectedAcceptedResultCannotAcknowledgeAnotherRequest","changedAcceptedBatchSequenceOrFingerprintIsUnavailable","corruptFixedImportColumnsAreUnavailable","finalSerializedReplayRejectsCorruptAcceptedGraph").contains(test.getTestMethod().orElseThrow().getName()));
        var created=call("POST","/meetings","{}","create");
        assertEquals(201,created.statusCode(),created.body());
        meeting=body(created).get("id").asText();
    }
    String identity(String id) throws Exception {
        return identity(id,false);
    }
    String identity(String id,boolean alreadyIssued) throws Exception {
        String value=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));
        j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);
        // Only the new positive integrity fixtures use a recent already-issued session after the observed backward clock step.
        String issued=alreadyIssued?"UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE":"UTC_TIMESTAMP(6)";
        j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,0,?,"+issued+",UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",hash(value),id,csrf);
        return value;
    }
    static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    JsonNode body(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body());
    }
    HttpResponse<String> call(String method,String path,String payload,String key) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1"+path)).header("Cookie","__Host-AMA_SESSION="+token).header("Origin",origin).header("X-CSRF-Token",csrf).header("Content-Type","application/json");
        if(key!=null)request.header("Idempotency-Key",key);
        var built=request.method(method,HttpRequest.BodyPublishers.ofString(payload)).build();
        var response=HttpClient.newHttpClient().send(built,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401)authDiagnostic(method+" "+path,built);
        return response;
    }
    void authDiagnostic(String operation,HttpRequest request) throws Exception {
        var session=j.queryForMap("SELECT i.status,i.session_generation identity_generation,s.session_generation session_generation,s.issued_at,s.expires_at,s.revoked_at,UTC_TIMESTAMP(6) database_now FROM identity_session s JOIN enterprise_identity i ON i.enterprise_id=s.enterprise_id WHERE s.token_hash=?",hash(token));
        Path diagnostic=Path.of("target/task04-custody");Files.createDirectories(diagnostic);
        Files.writeString(diagnostic.resolve(meeting+"-auth-diagnostic.txt"),"owner="+owner+" meeting="+meeting+" operation="+operation+" session="+session+" tokenCanonical="+token.matches("[A-Za-z0-9_-]{43}")+" cookieCount="+request.headers().allValues("Cookie").size()+" cookieExact="+request.headers().firstValue("Cookie").orElse("").equals("__Host-AMA_SESSION="+token)+" freshResolution="+app.getBean(ama.identity.SessionPort.class).resolve(token).isPresent()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    String open(String value) throws Exception {
        String checksum=hash(value);
        var begin=call("POST","/meetings/"+meeting+"/uploads",json.writeValueAsString(Map.of("purpose","TRANSCRIPT_INPUT","name","original😀.txt","size",value.getBytes(StandardCharsets.UTF_8).length,"sha256",checksum)),UUID.randomUUID().toString());
        assertEquals(201,begin.statusCode(),begin.body());
        String upload=body(begin).get("uploadId").asText();
        var part=part(upload,value);
        assertEquals(204,part.statusCode(),part.body());
        return upload;
    }
    HttpResponse<String> part(String upload,String value) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1/uploads/"+upload+"/parts/1")).header("Cookie","__Host-AMA_SESSION="+token).header("Origin",origin).header("X-CSRF-Token",csrf).header("Content-Type","application/octet-stream").header("X-Checksum-SHA256",hash(value)).PUT(HttpRequest.BodyPublishers.ofString(value)).build();
        var response=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401)authDiagnostic("PART "+upload,req);
        return response;
    }
    HttpResponse<String> complete(String upload,String value) throws Exception {
        return call("POST","/uploads/"+upload+"/completion",json.writeValueAsString(Map.of("parts",List.of(Map.of("part",1,"sha256",hash(value))),"sha256",hash(value))),null);
    }
    String upload(String value) throws Exception {
        var completed=complete(open(value),value);
        assertEquals(200,completed.statusCode(),completed.body());
        return body(completed).get("fileId").asText();
    }
    void restartRoute(long revision,String status,boolean defaultEnabled) {
        app.close();
        app=new SpringApplicationBuilder(ApiApplication.class).properties(deploymentBounds).initializers(context-> {
            var c=(GenericApplicationContext)context;
            c.registerBean("task04DataSource",javax.sql.DataSource.class,()->source);
            c.registerBean("task04Purpose",FilePurposePolicy.class,()->(a,m,p)-> {
                if(!purposeEnabled)throw new FilePurposePolicy.Unavailable();
                new TranscriptFilePurposePolicy().require(a,m,p);
            });
            c.registerBean("task04Backends",FileBackendDefinitions.class,()->new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(storage.binding.id(),storage.binding.type(),revision,status,observed,storage.binding)),defaultEnabled?storage.binding.id():null));
        }).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());
    }
    @Test void nativeUploadToActualProductionQueueAndHistoricalReplay() throws Exception {
        String file=upload("first line\nsecond line\n");
        String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"import");
        assertEquals(202,response.statusCode(),response.body());
        var accepted=body(response);
        String task=accepted.get("taskId").asText();
        track(task);
        assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
        assertEquals("TRANSCRIPT_IMPORT_V1",j.queryForObject("SELECT task_type FROM durable_task WHERE id=?",String.class,task));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,meeting));
        assertEquals(accepted,body(call("POST","/meetings/"+meeting+"/transcript-imports",payload,"import")));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
    }
    @Test void discoveryRecoversActualReceiptsInputsAndTasksWithoutClientMemory() throws Exception {
        var capabilities=call("GET","/meetings/"+meeting+"/processing-capabilities","",null);
        assertEquals(200,capabilities.statusCode(),capabilities.body());
        assertEquals("NOT_INSTALLED",body(capabilities).get("execution").asText());
        String file=upload("discovery original\n");
        var uploads=call("GET","/meetings/"+meeting+"/uploads?limit=1","",null);
        assertEquals(200,uploads.statusCode(),uploads.body());
        String upload=body(uploads).get("items").get(0).get("uploadId").asText();
        var receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+upload,"",null));
        assertEquals("COMPLETED",receipt.get("state").asText());
        assertEquals(0,receipt.get("allowedActions").size());
        assertEquals(2,receipt.get("idempotentRetries").size());
        assertEquals("1",body(call("GET","/meetings/"+meeting+"/uploads/"+upload+"/parts","",null)).get("items").get(0).get("part").asText());
        var completed=body(call("GET","/meetings/"+meeting+"/completed-files","",null));
        assertEquals(file,completed.get("items").get(0).get("fileId").asText());
        var accepted=body(call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),"discover"));
        track(accepted.get("taskId").asText());
        var input=call("GET","/meetings/"+meeting+"/processing-inputs/"+accepted.get("inputId").asText(),"",null);
        assertEquals(200,input.statusCode(),input.body());
        assertEquals(file,body(input).get("files").get(0).get("fileId").asText());
        var tasks=call("GET","/meetings/"+meeting+"/processing-tasks","",null);
        assertEquals(200,tasks.statusCode(),tasks.body());
        assertEquals("READY",body(tasks).get("items").get(0).get("state").asText());
        assertEquals(400,call("GET","/meetings/"+meeting+"/uploads?limit=51","",null).statusCode());
        assertEquals(404,call("GET","/meetings/"+meeting+"/uploads/"+UUID.randomUUID(),"",null).statusCode());
    }
    @Test void httpRevisionBoundariesAndExhaustionPreserveReplayForEveryMutation() throws Exception {
        for(String route:List.of("info","roster-confirmations","transcript-imports"))for(long revision:new long[] {
            9007199254740991L,9007199254740992L,9007199254740993L,9223372036854775805L,9223372036854775806L
        }) {
            var created=call("POST","/meetings","{}",UUID.randomUUID().toString());
            meeting=body(created).get("id").asText();
            j.update("UPDATE meeting SET revision=? WHERE id=?",revision,meeting);
            j.update("UPDATE task_subject SET business_version=? WHERE subject_kind='MEETING' AND subject_id=?",revision,meeting);
            j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",revision,meeting);
            if(route.equals("transcript-imports")&&revision>=9223372036854775805L) {
                var cap=body(call("GET","/meetings/"+meeting+"/processing-capabilities","",null));
                assertEquals(revision<9223372036854775806L,cap.get("canSubmit").asBoolean(),cap.toString());
                if(revision==9223372036854775806L)assertEquals("REVISION_EXHAUSTED",cap.get("readOnlyReason").asText());
            }
            var request=new LinkedHashMap<String,Object>();
            request.put("expectedRevisionToken",Long.toString(revision));
            if(route.equals("info"))request.put("rosterDraftIds",List.of());
            if(route.equals("transcript-imports"))request.put("orderedFileIds",List.of(upload("boundary\n")));
            String payload=json.writeValueAsString(request),key=UUID.randomUUID().toString(),path="/meetings/"+meeting+"/"+route,method=route.equals("info")?"PUT":"POST";
            var first=call(method,path,payload,key);
            if(revision==9223372036854775806L) {
                assertEquals(409,first.statusCode(),route+" "+first.body());
                continue;
            }
            assertEquals(route.equals("transcript-imports")?202:200,first.statusCode(),route+" "+first.body());
            assertEquals(Long.toString(revision+1),body(first).get("revisionToken").asText());
            if(route.equals("transcript-imports")) {
                track(body(first).get("taskId").asText());
                if(revision==9223372036854775805L) {
                    var cap=body(call("GET","/meetings/"+meeting+"/processing-capabilities","",null));
                    assertFalse(cap.get("canSubmit").asBoolean(),cap.toString());
                    assertEquals("REVISION_EXHAUSTED",cap.get("readOnlyReason").asText());
                }
            }
            assertEquals(body(first),body(call(method,path,payload,key)));
            request.remove("expectedRevisionToken");
            request.put("expectedRevision",revision);
            assertEquals(body(first),body(call(method,path,json.writeValueAsString(request),key)));
            request.put("expectedRevisionToken",Long.toString(revision));
            assertEquals(body(first),body(call(method,path,json.writeValueAsString(request),key)));
            assertEquals(409,call(method,path,payload,UUID.randomUUID().toString()).statusCode());
        }
    }
    @Test void rollbackEveryWriteStageRestoresSourcesFencesDebtAndIdempotency() throws Exception {
        String file=upload("rollback\n");
        String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var before=j.queryForMap("SELECT revision,cancel_epoch,needs_generation,transcript_revision_id,transcript_confirmation_id,final_confirmation_id FROM meeting WHERE id=?",meeting);
        var references=j.queryForList("SELECT owner_kind,owner_id,purpose FROM file_reference WHERE file_id=? ORDER BY owner_kind,owner_id,purpose",file);
        var counters=j.queryForMap("SELECT reference_epoch,location_revision FROM file_object WHERE id=?",file);
        long audits=j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Long.class,meeting),events=j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Long.class,meeting);
        for(String stage:List.of("INSERT INTO input_batch(","INSERT INTO input_batch_fingerprint_profile","INSERT INTO input_batch_file","INSERT INTO transcription_input","INSERT INTO file_reference","UPDATE meeting SET revision","UPDATE task_subject SET","INSERT INTO durable_task","INSERT INTO audit_event","INSERT INTO task_outbox")) {
            source.failStatement.set(stage);
            var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"rollback");
            assertEquals(503,response.statusCode(),stage+" "+response.body());
            assertNull(source.failStatement.get(),"Fault must actually execute: "+stage);
            assertEquals(audits,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Long.class,meeting));
            assertEquals(events,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Long.class,meeting));
            assertEquals(before,j.queryForMap("SELECT revision,cancel_epoch,needs_generation,transcript_revision_id,transcript_confirmation_id,final_confirmation_id FROM meeting WHERE id=?",meeting));
            assertEquals(references,j.queryForList("SELECT owner_kind,owner_id,purpose FROM file_reference WHERE file_id=? ORDER BY owner_kind,owner_id,purpose",file));
            assertEquals(counters,j.queryForMap("SELECT reference_epoch,location_revision FROM file_object WHERE id=?",file));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM input_batch WHERE meeting_id=?",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcription_input WHERE meeting_id=?",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='PROCESSING_IMPORT'",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",Long.class,meeting));
        }
        source.rollbackOn.set("INSERT INTO durable_task");
        assertEquals(503,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"rollback").statusCode());
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"rollback");
        assertEquals(202,response.statusCode(),response.body());
        track(body(response).get("taskId").asText());
    }
    @Test void acceptedReplayPrecedesArchiveSourcePublicationAndUnavailableBranch() throws Exception {
        String file=upload("historical\n");
        String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"historical");
        assertEquals(202,accepted.statusCode(),accepted.body());
        track(body(accepted).get("taskId").asText());
        j.update("UPDATE meeting SET revision=7,cancel_epoch=3,ever_published=1,stage='PUBLISHED' WHERE id=?",meeting);
        j.update("UPDATE task_subject SET business_version=7,cancel_epoch=3 WHERE subject_id=?",meeting);
        ((org.springframework.core.env.ConfigurableEnvironment)app.getEnvironment()).getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("task04-unavailable",Map.of("processing.archive-root",archive.resolve("absent").toString())));
        source.statements.clear();
        var replay=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"historical");
        assertEquals(body(accepted),body(replay));
        assertTrue(source.statements.stream().noneMatch(sql->sql.contains("upload_session")||sql.contains("file_object")||sql.startsWith("INSERT")),source.statements.toString());
        app.close();
        app=new SpringApplicationBuilder(ApiApplication.class).initializers(context-> {
            var c=(GenericApplicationContext)context;
            c.registerBean("task04DataSource",javax.sql.DataSource.class,()->source);
            c.registerBean("task04Backends",FileBackendDefinitions.class,()->new FileBackendDefinitions(List.of(storage.backend(observed)),storage.binding.id()));
        }).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true");
        var afterRestart=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"historical");
        assertEquals(202,afterRestart.statusCode(),afterRestart.body());
        assertEquals(body(accepted),body(afterRestart));
    }
    @Test void failedPreparationDoesExactlyOneFreshLookupForCommittedConcurrentWinner() throws Exception {
        String file=upload("concurrent\n");
        String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var reached=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        preparationDecorator=actual->(a,m,r)-> {
            if(first.compareAndSet(true,false)) {
                reached.countDown();
                await(release);
                throw ama.contract.ProcessingContracts.conflict();
            }
            return actual.prepare(a,m,r);
        };
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",payload,"concurrent"));
            assertTrue(reached.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var b=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"concurrent");
            assertEquals(202,b.statusCode(),b.body());
            track(body(b).get("taskId").asText());
            release.countDown();
            var recovered=a.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(202,recovered.statusCode(),recovered.body());
            assertEquals(body(b),body(recovered));
        }
        finally {
            release.countDown();
        }
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
    }
    @Test void ownerMemberOutsiderAdminSessionAndCsrfGuardsAreFresh() throws Exception {
        String file=upload("security\n"),originalToken=token;
        String member="task04-member-"+UUID.randomUUID(),memberToken=identity(member);
        var saved=call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken","0","rosterDraftIds",List.of(member))),"roster");
        assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(200,call("POST","/meetings/"+meeting+"/roster-confirmations","{\"expectedRevisionToken\":\"1\"}","confirm").statusCode());
        String outsider="task04-outsider-"+UUID.randomUUID(),outsiderToken=identity(outsider),admin="task04-admin-"+UUID.randomUUID(),adminToken=identity(admin);
        j.update("INSERT INTO role_assignment VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))",admin);
        for(String denied:List.of(memberToken,outsiderToken,adminToken)) {
            token=denied;
            for(String path:List.of("uploads","completed-files","processing-inputs","processing-tasks")) {
                var response=call("GET","/meetings/"+meeting+"/"+path,"",null);
                assertEquals(403,response.statusCode(),response.body());
                assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());
            }
            assertEquals(403,call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","2","orderedFileIds",List.of(file))),"denied").statusCode());
        }
        token=memberToken;
        var capabilities=call("GET","/meetings/"+meeting+"/processing-capabilities","",null);
        assertEquals(200,capabilities.statusCode(),capabilities.body());
        assertFalse(body(capabilities).get("canSubmit").asBoolean());
        token=originalToken;
        j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
        assertEquals(401,call("GET","/meetings/"+meeting+"/uploads","",null).statusCode());
    }
    @Test void actualQueueClaimAndRenewNeedNoFakeTranscriptOrPreleaseWorkerGrant() throws Exception {
        String file=upload("queue\n");
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),"queue");
        assertEquals(202,response.statusCode(),response.body());
        String task=body(response).get("taskId").asText();
        track(task);
        var queue=app.getBean(ama.task.JdbcTaskQueue.class);
        var lease=queue.claim("TRANSCRIPT_IMPORT","TASK04_CONTROLLED_WORKER").orElseThrow();
        assertEquals(task,lease.taskId());
        assertTrue(queue.renew(lease));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,meeting));
        assertThrows(RuntimeException.class,()->queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.SUCCEEDED,new ama.contract.TaskContracts.TypedRef("TRANSCRIPT_REVISION",UUID.randomUUID().toString()),null)));
        assertTrue(queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"PROCESSING_INPUT_INVALID")));
    }
    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            if(!latch.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Task4 barrier timeout");
        }
        catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
    @Test void acceptedResultWithCorruptFullProducerSpecIsUnavailable() throws Exception {
        String file=upload("producer\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"producer");
        assertEquals(202,accepted.statusCode(),accepted.body());
        String task=body(accepted).get("taskId").asText();
        track(task);
        String original=j.queryForObject("SELECT producer_key FROM durable_task WHERE id=?",String.class,task);
        try {
            j.update("UPDATE durable_task SET producer_key=? WHERE id=?","e".repeat(64),task);
            assertEquals(503,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"producer").statusCode());
            assertEquals(503,call("GET","/meetings/"+meeting+"/processing-tasks/"+task,"",null).statusCode());
            assertEquals(503,call("GET","/meetings/"+meeting+"/processing-inputs/"+body(accepted).get("inputId").asText(),"",null).statusCode());
        }
        finally {
            j.update("UPDATE durable_task SET producer_key=? WHERE id=?",original,task);
        }
    }
    @Test void redirectedAcceptedResultCannotAcknowledgeAnotherRequest() throws Exception {
        String file=upload("redirect first\n");
        String firstPayload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var first=call("POST","/meetings/"+meeting+"/transcript-imports",firstPayload,"redirect-first");
        assertEquals(202,first.statusCode(),first.body());
        String firstTask=body(first).get("taskId").asText();track(firstTask);
        String secondFile=upload("redirect second\n");
        var second=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","1","orderedFileIds",List.of(secondFile))),"redirect-second");
        assertEquals(202,second.statusCode(),second.body());
        String secondTask=body(second).get("taskId").asText();track(secondTask);
        String scope=j.queryForObject("SELECT scope_hash FROM idempotency_record WHERE operation='PROCESSING_IMPORT' AND result_id=?",String.class,firstTask);
        var before=replayCounts();
        try {
            assertEquals(1,j.update("UPDATE idempotency_record SET result_id=? WHERE scope_hash=?",secondTask,scope));
            assertUnavailableReplay(firstPayload,"redirect-first",before);
        } finally {assertEquals(1,j.update("UPDATE idempotency_record SET result_id=? WHERE scope_hash=?",firstTask,scope));}
        assertEquals(body(first),body(call("POST","/meetings/"+meeting+"/transcript-imports",firstPayload,"redirect-first")));
    }
    @Test void changedAcceptedBatchSequenceOrFingerprintIsUnavailable() throws Exception {
        String first=upload("sequence first\n"),second=upload("sequence second\n"),other=upload("sequence other\n");
        String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(first,second)));
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"sequence");
        assertEquals(202,response.statusCode(),response.body());var accepted=body(response);track(accepted.get("taskId").asText());
        String batch=accepted.get("batchId").asText();var before=replayCounts();
        String originalHash=j.queryForObject("SELECT sha256 FROM input_batch_file WHERE batch_id=? AND ordinal=1",String.class,batch);
        try {
            j.update("UPDATE input_batch_file SET file_id=?,sha256=? WHERE batch_id=? AND ordinal=1",other,hash("sequence other\n"),batch);
            assertUnavailableReplay(payload,"sequence",before);
        } finally {j.update("UPDATE input_batch_file SET file_id=?,sha256=? WHERE batch_id=? AND ordinal=1",first,originalHash,batch);}
        try {
            j.update("UPDATE input_batch_file SET ordinal=3 WHERE batch_id=? AND ordinal=1",batch);
            j.update("UPDATE input_batch_file SET ordinal=1 WHERE batch_id=? AND ordinal=2",batch);
            j.update("UPDATE input_batch_file SET ordinal=2 WHERE batch_id=? AND ordinal=3",batch);
            assertUnavailableReplay(payload,"sequence",before);
        } finally {
            // Two-step temporary ordinals avoid primary-key collisions and preserve each exact member.
            j.update("UPDATE input_batch_file SET ordinal=ordinal+2 WHERE batch_id=?",batch);
            j.update("UPDATE input_batch_file SET ordinal=5-ordinal WHERE batch_id=?",batch);
        }
        String profile=j.queryForObject("SELECT profile FROM input_batch_fingerprint_profile WHERE batch_id=?",String.class,batch);
        try {
            j.update("DELETE FROM input_batch_fingerprint_profile WHERE batch_id=?",batch);
            assertUnavailableReplay(payload,"sequence",before);
        } finally {j.update("INSERT INTO input_batch_fingerprint_profile(batch_id,profile) VALUES (?,?)",batch,profile);}
        String fingerprint=j.queryForObject("SELECT input_fingerprint FROM input_batch WHERE id=?",String.class,batch);
        try {j.update("UPDATE input_batch SET input_fingerprint=? WHERE id=?","e".repeat(64),batch);assertUnavailableReplay(payload,"sequence",before);}
        finally {j.update("UPDATE input_batch SET input_fingerprint=? WHERE id=?",fingerprint,batch);}
        assertEquals(accepted,body(call("POST","/meetings/"+meeting+"/transcript-imports",payload,"sequence")));
    }
    @Test void corruptFixedImportColumnsAreUnavailable() throws Exception {
        String file=upload("shape\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"shape");
        assertEquals(202,response.statusCode(),response.body());var accepted=body(response);track(accepted.get("taskId").asText());
        String input=accepted.get("inputId").asText();var before=replayCounts();
        for(String column:List.of("model_config_version","prompt_version","manifest_schema_version","material_mode","parser_version","processing_config_version","config_manifest")) {
            var original=j.queryForMap("SELECT "+column+",material_snapshot_id FROM transcription_input WHERE id=?",input);
            try {
                if(column.equals("material_mode"))j.update("UPDATE transcription_input SET material_mode='PINNED',material_snapshot_id=? WHERE id=?",UUID.randomUUID().toString(),input);
                else j.update("UPDATE transcription_input SET "+column+"=? WHERE id=?",column.equals("manifest_schema_version")?2:"unexpected",input);
                assertUnavailableReplay(payload,"shape",before);
            } finally {j.update("UPDATE transcription_input SET "+column+"=?,material_snapshot_id=? WHERE id=?",original.get(column),original.get("material_snapshot_id"),input);}
        }
        assertEquals(accepted,body(call("POST","/meetings/"+meeting+"/transcript-imports",payload,"shape")));
    }
    @Test void finalSerializedReplayRejectsCorruptAcceptedGraph() throws Exception {
        String file=upload("final replay\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        var winner=new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        var before=new java.util.concurrent.atomic.AtomicReference<Map<String,Object>>();
        preparationDecorator=actual->(a,m,r)-> {
            var prepared=actual.prepare(a,m,r);
            if(first.getAndSet(false))try {
                var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"final-corrupt");
                assertEquals(202,accepted.statusCode(),accepted.body());var result=body(accepted);winner.set(result);track(result.get("taskId").asText());
                before.set(replayCounts());
                j.update("UPDATE transcription_input SET model_config_version='unexpected' WHERE id=?",result.get("inputId").asText());
            } catch(Exception failure) {throw new IllegalStateException(failure);}
            return prepared;
        };
        try {
            var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"final-corrupt");
            assertNotNull(winner.get());assertEquals(503,response.statusCode(),response.body());
            assertEquals(before.get(),replayCounts());
        }
        finally {
            if(winner.get()!=null)j.update("UPDATE transcription_input SET model_config_version=NULL WHERE id=?",winner.get().get("inputId").asText());
            preparationDecorator=java.util.function.UnaryOperator.identity();
        }
        assertEquals(winner.get(),body(call("POST","/meetings/"+meeting+"/transcript-imports",payload,"final-corrupt")));
    }
    Map<String,Object> replayCounts() {
        return j.queryForMap("SELECT (SELECT COUNT(*) FROM input_batch WHERE meeting_id=?) batches,(SELECT COUNT(*) FROM transcription_input WHERE meeting_id=?) inputs,(SELECT COUNT(*) FROM durable_task WHERE subject_id=?) tasks,(SELECT COUNT(*) FROM idempotency_record WHERE resource_id=?) idempotency,(SELECT COUNT(*) FROM audit_event WHERE resource_id=?) audits,(SELECT COUNT(*) FROM task_outbox WHERE subject_id=?) events",meeting,meeting,meeting,meeting,meeting,meeting);
    }
    void assertUnavailableReplay(String payload,String key,Map<String,Object> before) throws Exception {
        var result=call("POST","/meetings/"+meeting+"/transcript-imports",payload,key);
        assertEquals(503,result.statusCode(),result.body());
        assertEquals("PROCESSING_UNAVAILABLE",body(result).get("error").get("code").asText());
        assertEquals(before,replayCounts());
    }
    @Test void originalUploadBeginEmitsOpaquePartSizeToken() throws Exception {
        var begin=call("POST","/meetings/"+meeting+"/uploads",json.writeValueAsString(Map.of("purpose","TRANSCRIPT_INPUT","name","token.txt","size",1,"sha256",hash("x"))),UUID.randomUUID().toString());
        assertEquals(201,begin.statusCode(),begin.body());
        assertTrue(body(begin).has("partSizeToken"),begin.body());
        assertEquals(body(begin).get("partSize").asText(),body(begin).get("partSizeToken").asText());
    }
    @Test void recoveryRacesNeverAdoptDifferentRevokedUncommittedOrAbsentResults() throws Exception {
        for(String mode:List.of("DIFFERENT","ROLLBACK","REVOKED","UNCOMMITTED","LATE","DB_FAILURE","FINAL_CLAIM")) {
            preparationDecorator=java.util.function.UnaryOperator.identity();
            meeting=body(call("POST","/meetings","{}",UUID.randomUUID().toString())).get("id").asText();
            String file=upload("race "+mode+"\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
            String other=mode.equals("DIFFERENT")?json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(upload("other\n")))):payload;
            var reached=new java.util.concurrent.CountDownLatch(1);
            var release=new java.util.concurrent.CountDownLatch(1);
            var first=new java.util.concurrent.atomic.AtomicBoolean(true);
            preparationDecorator=actual->(a,m,r)-> {
                if(!first.compareAndSet(true,false))return actual.prepare(a,m,r);
                var prepared=mode.equals("FINAL_CLAIM")?actual.prepare(a,m,r):null;
                reached.countDown();
                await(release);
                if(prepared!=null)return prepared;
                throw ama.contract.ProcessingContracts.conflict();
            };
            try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var a=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",payload,"race"));
                assertTrue(reached.await(10,java.util.concurrent.TimeUnit.SECONDS));
                if(mode.equals("LATE")) {
                    release.countDown();
                    assertEquals(409,a.get(10,java.util.concurrent.TimeUnit.SECONDS).statusCode());
                    var b=call("POST","/meetings/"+meeting+"/transcript-imports",other,"race");
                    assertEquals(202,b.statusCode(),b.body());
                    track(body(b).get("taskId").asText());
                    continue;
                }
                if(mode.equals("UNCOMMITTED")) {
                    var inserted=new java.util.concurrent.CountDownLatch(1);
                    var finish=new java.util.concurrent.CountDownLatch(1);
                    var once=new java.util.concurrent.atomic.AtomicBoolean(true);
                    source.after=sql-> {
                        if(sql.startsWith("INSERT INTO idempotency_record")&&once.compareAndSet(true,false)) {
                            inserted.countDown();
                            await(finish);
                        }
                    };
                    try {
                        var b=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",other,"race"));
                        assertTrue(inserted.await(10,java.util.concurrent.TimeUnit.SECONDS));
                        release.countDown();
                        var failed=a.get(10,java.util.concurrent.TimeUnit.SECONDS);
                        assertEquals(503,failed.statusCode(),failed.body());
                        finish.countDown();
                        var winner=b.get(10,java.util.concurrent.TimeUnit.SECONDS);
                        assertEquals(202,winner.statusCode(),winner.body());
                        track(body(winner).get("taskId").asText());
                    }
                    finally {
                        finish.countDown();
                        source.after=sql-> {
                        };
                    }
                    continue;
                }
                if(mode.equals("ROLLBACK"))source.rollbackOn.set("INSERT INTO durable_task");
                var b=call("POST","/meetings/"+meeting+"/transcript-imports",other,"race");
                assertEquals(mode.equals("ROLLBACK")?503:202,b.statusCode(),mode+" "+b.body());
                if(b.statusCode()==202)track(body(b).get("taskId").asText());
                if(mode.equals("REVOKED"))j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
                if(mode.equals("DB_FAILURE"))source.failStatement.set("SELECT actor_id,resource_id");
                release.countDown();
                var recovered=a.get(10,java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(mode.equals("FINAL_CLAIM")?202:mode.equals("REVOKED")?403:mode.equals("DB_FAILURE")?503:409,recovered.statusCode(),mode+" "+recovered.body());
                if(mode.equals("FINAL_CLAIM"))assertEquals(body(b),body(recovered));
            }
            finally {
                release.countDown();
                j.update("UPDATE enterprise_identity SET status='ACTIVE' WHERE enterprise_id=?",owner);
                source.after=sql-> {
                };
            }
            assertEquals(mode.equals("ROLLBACK")?0:1,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
        }
    }
    @Test void malformedRequestsAndOversizedNamesFailBeforeEffectsOrTextTransfer() throws Exception {
        String file=upload("limits\n");
        for(String revision:List.of("\"expectedRevisionToken\":null","\"expectedRevisionToken\":\"01\"","\"expectedRevisionToken\":\"+1\"","\"expectedRevisionToken\":\"１\"","\"expectedRevisionToken\":\" 1\"","\"expectedRevisionToken\":\"9223372036854775807\"","\"expectedRevision\":1.0","\"expectedRevision\":1e0","\"expectedRevision\":-1","\"expectedRevision\":9223372036854775807","\"expectedRevision\":0,\"expectedRevisionToken\":\"1\"")) {
            var response=call("POST","/meetings/"+meeting+"/transcript-imports","{"+revision+",\"orderedFileIds\":[\""+file+"\"]}",UUID.randomUUID().toString());
            assertEquals(400,response.statusCode(),response.body());
            assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        }
        assertEquals(400,call("POST","/meetings/"+meeting+"/transcript-imports","{\"expectedRevision\":0,\"expectedRevision\":0,\"orderedFileIds\":[\""+file+"\"]}","duplicate").statusCode());
        assertEquals(413,call("POST","/meetings/"+meeting+"/transcript-imports"," ".repeat(65537),"oversized").statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
        j.update("UPDATE file_object SET display_name=? WHERE id=?","😀".repeat(32769),file);
        source.statements.clear();
        var response=call("GET","/meetings/"+meeting+"/completed-files","",null);
        assertEquals(503,response.statusCode());
        assertEquals("PROCESSING_READ_BUDGET_EXCEEDED",body(response).get("error").get("code").asText());
        assertTrue(source.statements.stream().anyMatch(sql->sql.contains("CASE WHEN")&&sql.contains("OCTET_LENGTH(CONVERT(display_name USING utf16))")));
    }
    @Test void staleQueueTaskCanTerminateWithoutAcquiringObsoleteSourcePermission() throws Exception {
        String file=upload("stale\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"stale");
        assertEquals(202,response.statusCode(),response.body());
        String task=body(response).get("taskId").asText();
        track(task);
        j.update("UPDATE meeting SET revision=2,cancel_epoch=1 WHERE id=?",meeting);
        j.update("UPDATE task_subject SET business_version=2,cancel_epoch=1 WHERE subject_id=?",meeting);
        j.update("UPDATE storage_backend SET status='DISABLED',config_revision=2 WHERE id=?",storage.binding.id());
        var queue=app.getBean(ama.task.JdbcTaskQueue.class);
        assertDoesNotThrow(()->assertTrue(queue.claim("TRANSCRIPT_IMPORT","TASK04_STALE_WORKER").isEmpty()));
        assertEquals("CANCELLED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
    }
    @Test void historicalRouteActionsAndTerminalRetriesMatchActualB04Operations() throws Exception {
        String value="matrix\n",open=open(value),finalizing=open(value),disabledFinalizing=open(value),aborted=open(value);
        assertEquals(204,call("DELETE","/uploads/"+aborted,"",null).statusCode());
        String file=upload(value);
        String completed=j.queryForObject("SELECT id FROM upload_session WHERE completed_file_id=?",String.class,file);
        for(String upload:List.of(finalizing,disabledFinalizing)) {
            observed.beforeComplete.set(()-> {
                throw new ama.contract.StorageContracts.StorageException(ama.contract.StorageContracts.ErrorCode.STORAGE_UNAVAILABLE);
            });
            assertEquals(503,complete(upload,value).statusCode());
            assertEquals("FINALIZING",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,upload));
        }
        restartRoute(2,"READ_ONLY",false);
        var receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+open,"",null));
        assertEquals(List.of("ABORT"),json.convertValue(receipt.get("allowedActions"),List.class));
        assertEquals(503,part(open,value).statusCode());
        assertEquals(503,complete(open,value).statusCode());
        j.update("UPDATE storage_backend SET config_revision=99 WHERE id=?",storage.binding.id());
        assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+open,"",null)).get("allowedActions").size());
        assertEquals(503,call("DELETE","/uploads/"+open,"",null).statusCode());
        j.update("UPDATE storage_backend SET config_revision=2 WHERE id=?",storage.binding.id());
        assertEquals(204,call("DELETE","/uploads/"+open,"",null).statusCode());
        assertEquals(204,call("DELETE","/uploads/"+aborted,"",null).statusCode());
        receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+finalizing,"",null));
        assertEquals(List.of("COMPLETE"),json.convertValue(receipt.get("allowedActions"),List.class));
        assertEquals(409,part(finalizing,value).statusCode());
        assertEquals(409,call("DELETE","/uploads/"+finalizing,"",null).statusCode());
        assertEquals(200,complete(finalizing,value).statusCode());
        var capability=call("GET","/meetings/"+meeting+"/processing-capabilities","",null);
        assertEquals("AVAILABLE",body(capability).get("intake").asText());
        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),"readonly");
        assertEquals(202,accepted.statusCode(),accepted.body());
        track(body(accepted).get("taskId").asText());
        restartRoute(3,"DISABLED",false);
        receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+disabledFinalizing,"",null));
        assertEquals(0,receipt.get("allowedActions").size());
        assertEquals(0,receipt.get("idempotentRetries").size());
        assertEquals(503,complete(disabledFinalizing,value).statusCode());
        assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+aborted,"",null)).get("idempotentRetries").size());
        assertEquals(503,call("DELETE","/uploads/"+aborted,"",null).statusCode());
        j.update("UPDATE meeting SET ever_published=1,stage='PUBLISHED' WHERE id=?",meeting);
        receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+completed,"",null));
        assertEquals(0,receipt.get("allowedActions").size());
        assertEquals(2,receipt.get("idempotentRetries").size());
        assertEquals(200,complete(completed,value).statusCode());
        assertEquals(204,call("DELETE","/uploads/"+completed,"",null).statusCode());
        assertEquals(409,complete(completed,"wrong").statusCode());
        purposeEnabled=false;
        assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+completed,"",null)).get("idempotentRetries").size());
        assertEquals(503,complete(completed,value).statusCode());
        purposeEnabled=true;
        j.update("UPDATE meeting SET cancel_epoch=1 WHERE id=?",meeting);
        j.update("UPDATE task_subject SET cancel_epoch=1 WHERE subject_id=?",meeting);
        assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+completed,"",null)).get("idempotentRetries").size());
        assertEquals(409,complete(completed,value).statusCode());
    }
    @Test void unsafeRequestsFreshRevocationAndGovernanceAreNeverClientPermissions() throws Exception {
        String file=upload("csrf\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        String url="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1/meetings/"+meeting+"/transcript-imports";
        for(String mode:List.of("COOKIE","CSRF","ORIGIN","DUPLICATE")) {
            var request=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json").header("Idempotency-Key","unsafe");
            if(!mode.equals("COOKIE"))request.header("Cookie","__Host-AMA_SESSION="+token);
            if(!mode.equals("CSRF"))request.header("X-CSRF-Token",csrf);
            request.header("Origin",mode.equals("ORIGIN")?"https://foreign.example.invalid":origin);
            if(mode.equals("DUPLICATE"))request.header("Origin",origin);
            var response=HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(mode.equals("COOKIE")?401:403,response.statusCode(),response.body());
            assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        }
        j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",meeting);
        assertEquals(403,call("GET","/meetings/"+meeting+"/uploads","",null).statusCode());
        j.update("UPDATE meeting SET governance_state='ACTIVE',lifecycle='DELETING' WHERE id=?",meeting);
        assertEquals(403,call("GET","/meetings/"+meeting+"/processing-capabilities","",null).statusCode());
        j.update("UPDATE meeting SET lifecycle='ACTIVE' WHERE id=?",meeting);
        j.update("UPDATE identity_session SET revoked_at=UTC_TIMESTAMP(6) WHERE enterprise_id=?",owner);
        assertEquals(401,call("GET","/meetings/"+meeting+"/uploads","",null).statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
    }
    @Test void actualCanonicalAndManagedHashesStayOutsideMeetingRoots() throws Exception {
        String file=upload("observed\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        String provider="TASK04_"+UUID.randomUUID();
        Task04HashObserver.inside.set(0);
        Task04HashObserver.outside.set(0);
        Task04HashObserver.held=()->source.rootsHeld.contains(Thread.currentThread());
        java.security.Security.insertProviderAt(new Task04HashObserver(provider),1);
        try {
            var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"observe");
            assertEquals(202,accepted.statusCode(),accepted.body());
            String task=body(accepted).get("taskId").asText();
            track(task);
            assertEquals(202,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"observe").statusCode());
            assertEquals(200,call("GET","/meetings/"+meeting+"/processing-inputs/"+body(accepted).get("inputId").asText(),"",null).statusCode());
            assertEquals(200,call("GET","/meetings/"+meeting+"/processing-tasks","",null).statusCode());
            var queue=app.getBean(ama.task.JdbcTaskQueue.class);
            var lease=queue.claim("TRANSCRIPT_IMPORT","TASK04_OBSERVED_WORKER").orElseThrow();
            assertEquals(task,lease.taskId());
            assertTrue(queue.renew(lease));
            assertTrue(queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"PROCESSING_INPUT_INVALID")));
            assertTrue(Task04HashObserver.outside.get()>0);
            assertEquals(0,Task04HashObserver.inside.get());
        }
        finally {
            java.security.Security.removeProvider(provider);
            Task04HashObserver.held=()->false;
        }
    }
    @Test void ordinaryQueueSkipsLockedInputRootsWithoutWaitingOrLeasing() throws Exception {
        String file=upload("skip\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"skip");
        assertEquals(202,response.statusCode(),response.body());
        var accepted=body(response);
        String task=accepted.get("taskId").asText();
        track(task);
        var held=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var blocker=pool.submit(()->new ama.db.TransactionRunner(source).required(()-> {
                j.queryForObject("SELECT id FROM transcription_input WHERE id=? FOR UPDATE",String.class,accepted.get("inputId").asText());
                held.countDown();
                await(release);
                return null;
            }));
            assertTrue(held.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var claim=pool.submit(()->app.getBean(ama.task.JdbcTaskQueue.class).claim("TRANSCRIPT_IMPORT","TASK04_SKIP_WORKER"));
            try {
                assertTrue(assertDoesNotThrow(()->claim.get(2,java.util.concurrent.TimeUnit.SECONDS)).isEmpty());
            }
            finally {
                release.countDown();
                blocker.get(10,java.util.concurrent.TimeUnit.SECONDS);
                claim.get(10,java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        finally {
            release.countDown();
        }
        assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
    }
    @Test void operationalBudgetFailureStillDiscoversAnActualCommittedWinner() throws Exception {
        String file=upload("budget race\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var reached=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        preparationDecorator=actual->(a,m,r)-> {
            if(first.compareAndSet(true,false)) {
                reached.countDown();
                await(release);
                throw new ama.contract.ProcessingContracts.Failure(413,"REQUEST_TOO_LARGE");
            }
            return actual.prepare(a,m,r);
        };
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",payload,"budget"));
            assertTrue(reached.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var b=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"budget");
            assertEquals(202,b.statusCode(),b.body());
            track(body(b).get("taskId").asText());
            release.countDown();
            var result=a.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(202,result.statusCode(),result.body());
            assertEquals(body(b),body(result));
        }
        finally {
            release.countDown();
        }
    }
    @Test void ineligibleCompletedNeighborDoesNotMislabelAValidSource() throws Exception {
        String valid=upload("valid\n"),invalid=upload("cleanup\n");
        j.update("UPDATE file_object SET state='DELETE_PENDING' WHERE id=?",invalid);
        var response=call("GET","/meetings/"+meeting+"/completed-files","",null);
        assertEquals(200,response.statusCode(),response.body());
        var byId=new HashMap<String,JsonNode>();
        for(var item:body(response).get("items"))byId.put(item.get("fileId").asText(),item);
        assertEquals("ELIGIBLE",byId.get(valid).get("intakeEligibility").get("state").asText());
        assertEquals("SOURCE_STATE",byId.get(invalid).get("intakeEligibility").get("reason").asText());
        var receipts=new ArrayList<JsonNode>();
        for(String file:List.of(valid,invalid)) {
            var receipt=call("GET","/meetings/"+meeting+"/uploads/"+byId.get(file).get("uploadId").asText(),"",null);
            assertEquals(200,receipt.statusCode(),receipt.body());receipts.add(body(receipt));
        }
        Path responseEvidence=Path.of("target/task04-custody");Files.createDirectories(responseEvidence);
        Files.writeString(responseEvidence.resolve(meeting+"-schema-http.json"),json.writeValueAsString(Map.of("completedFiles",body(response),"receipts",receipts)));

        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(valid))),"valid-neighbor");
        assertEquals(202,accepted.statusCode(),accepted.body());
        track(body(accepted).get("taskId").asText());
        assertEquals(409,call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","1","orderedFileIds",List.of(invalid))),"invalid-neighbor").statusCode());
    }
    @Test void lostCommitAcknowledgmentRecoversTheActualCommittedAcceptance() throws Exception {
        String file=upload("lost acknowledgment\n");
        String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        source.lostAckOn.set("INSERT INTO durable_task");
        var uncertain=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"lost-ack");
        assertEquals(503,uncertain.statusCode(),uncertain.body());
        assertNull(source.lostAckOn.get());
        var tasks=j.queryForList("SELECT id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1'",String.class,meeting);
        assertEquals(1,tasks.size());
        String task=tasks.getFirst();
        track(task);
        var recovered=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"lost-ack");
        assertEquals(202,recovered.statusCode(),recovered.body());
        assertEquals(task,body(recovered).get("taskId").asText());
        assertEquals(body(recovered),body(call("POST","/meetings/"+meeting+"/transcript-imports",payload,"lost-ack")));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcription_input WHERE meeting_id=?",Integer.class,meeting));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,meeting));
    }
    @AfterEach void close() throws Exception {
        if(j!=null)for(String task:ownedTasks) {
            var rows=j.queryForList("SELECT state,input_id FROM durable_task WHERE id=? AND task_type='TRANSCRIPT_IMPORT_V1'",task);
            assertEquals(1,rows.size());
            assertTrue(Set.of("READY","RUNNING","FAILED","CANCELLED").contains(rows.getFirst().get("state")));
            var tx=new ama.db.TransactionRunner(source);
            tx.required(()-> {
                var locked=j.queryForMap("SELECT "+SPEC_COLUMNS+",state FROM durable_task WHERE id=? FOR UPDATE",task);
                var current=(String)locked.remove("state");
                assertEquals(acceptedSpecs.get(task),locked,"accepted immutable spec must remain exact before deny-only fixture teardown");
                if(Set.of("READY","RUNNING").contains(current)) {
                    j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='CANCELLED',error_code='TASK04_FIXTURE_CLOSED' WHERE task_id=? AND ended_at IS NULL",task);
                    assertEquals(1,j.update("UPDATE durable_task SET state='CANCELLED',lease_owner=NULL,expires_at=NULL WHERE id=? AND state=?",task,current));
                }
                return null;
            });
        }
        Path receipts=Path.of("target/task04-custody");
        Files.createDirectories(receipts);
        if(meeting!=null)Files.writeString(receipts.resolve(meeting+".txt"),"meeting="+meeting+" owner="+owner+" tasks="+ownedTasks+" native="+(storage==null?null:storage.local.base.root)+" archive="+archive+" pki="+LocalFixture.pki+" javaMaxMemory="+Runtime.getRuntime().maxMemory()+" processors="+Runtime.getRuntime().availableProcessors()+"\n");
        if(meeting!=null)Files.writeString(receipts.resolve(meeting+"-accepted-specs.json"),json.writeValueAsString(acceptedSpecs));
        if(meeting!=null&&source!=null) {
            Files.writeString(receipts.resolve(meeting+"-locks.json"),json.writeValueAsString(source.lockTrace));
            Files.writeString(receipts.resolve(meeting+"-hashes.json"),json.writeValueAsString(Map.of("inside",Task04HashObserver.inside.get(),"outside",Task04HashObserver.outside.get())));
        }
        if(app!=null)app.close();
        if(storage!=null)storage.close();
    }
}
