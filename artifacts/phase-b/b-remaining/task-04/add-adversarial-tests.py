from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void rollbackEveryWriteStageRestoresSourcesFencesDebtAndIdempotency() throws Exception {
        String file=upload("rollback\n");String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var before=j.queryForMap("SELECT revision,cancel_epoch,needs_generation,transcript_revision_id,transcript_confirmation_id,final_confirmation_id FROM meeting WHERE id=?",meeting);
        var references=j.queryForList("SELECT owner_kind,owner_id,purpose FROM file_reference WHERE file_id=? ORDER BY owner_kind,owner_id,purpose",file);
        var counters=j.queryForMap("SELECT reference_epoch,location_revision FROM file_object WHERE id=?",file);
        for(String stage:List.of("INSERT INTO input_batch(","INSERT INTO input_batch_fingerprint_profile","INSERT INTO input_batch_file","INSERT INTO transcription_input","INSERT INTO file_reference","UPDATE meeting SET revision","UPDATE task_subject SET","INSERT INTO durable_task","INSERT INTO audit_event","INSERT INTO task_outbox")) {
            source.failStatement.set(stage);
            var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"rollback");
            assertEquals(503,response.statusCode(),stage+" "+response.body());assertNull(source.failStatement.get(),"Fault must actually execute: "+stage);
            assertEquals(before,j.queryForMap("SELECT revision,cancel_epoch,needs_generation,transcript_revision_id,transcript_confirmation_id,final_confirmation_id FROM meeting WHERE id=?",meeting));
            assertEquals(references,j.queryForList("SELECT owner_kind,owner_id,purpose FROM file_reference WHERE file_id=? ORDER BY owner_kind,owner_id,purpose",file));assertEquals(counters,j.queryForMap("SELECT reference_epoch,location_revision FROM file_object WHERE id=?",file));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM input_batch WHERE meeting_id=?",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcription_input WHERE meeting_id=?",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='PROCESSING_IMPORT'",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",Long.class,meeting));
        }
        source.rollbackOn.set("INSERT INTO durable_task");assertEquals(503,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"rollback").statusCode());
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"rollback");assertEquals(202,response.statusCode(),response.body());ownedTasks.add(body(response).get("taskId").asText());
    }
    @Test void acceptedReplayPrecedesArchiveSourcePublicationAndUnavailableBranch() throws Exception {
        String file=upload("historical\n");String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"historical");assertEquals(202,accepted.statusCode(),accepted.body());ownedTasks.add(body(accepted).get("taskId").asText());
        j.update("UPDATE meeting SET revision=7,cancel_epoch=3,ever_published=1,stage='PUBLISHED' WHERE id=?",meeting);j.update("UPDATE task_subject SET business_version=7,cancel_epoch=3 WHERE subject_id=?",meeting);
        ((org.springframework.core.env.ConfigurableEnvironment)app.getEnvironment()).getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("task04-unavailable",Map.of("processing.archive-root",archive.resolve("absent").toString())));
        source.statements.clear();var replay=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"historical");assertEquals(body(accepted),body(replay));
        assertTrue(source.statements.stream().noneMatch(sql->sql.contains("upload_session")||sql.contains("file_object")||sql.startsWith("INSERT")),source.statements.toString());
        app.close();
        app=new SpringApplicationBuilder(ApiApplication.class).initializers(context->{
            var c=(GenericApplicationContext)context;c.registerBean("task04DataSource",javax.sql.DataSource.class,()->source);
            c.registerBean("task04Backends",FileBackendDefinitions.class,()->new FileBackendDefinitions(List.of(storage.backend(storage.storage)),storage.binding.id()));
        }).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true");
        var afterRestart=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"historical");assertEquals(202,afterRestart.statusCode(),afterRestart.body());assertEquals(body(accepted),body(afterRestart));
    }
    @Test void failedPreparationDoesExactlyOneFreshLookupForCommittedConcurrentWinner() throws Exception {
        String file=upload("concurrent\n");String payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var reached=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        preparationDecorator=actual->(a,m,r)->{if(first.compareAndSet(true,false)){reached.countDown();await(release);throw ama.contract.ProcessingContracts.conflict();}return actual.prepare(a,m,r);};
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",payload,"concurrent"));assertTrue(reached.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var b=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"concurrent");assertEquals(202,b.statusCode(),b.body());ownedTasks.add(body(b).get("taskId").asText());release.countDown();
            var recovered=a.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(202,recovered.statusCode(),recovered.body());assertEquals(body(b),body(recovered));
        }finally{release.countDown();}
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
    }
    @Test void ownerMemberOutsiderAdminSessionAndCsrfGuardsAreFresh() throws Exception {
        String file=upload("security\n"),originalToken=token;
        String member="task04-member-"+UUID.randomUUID(),memberToken=identity(member);
        var saved=call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken","0","rosterDraftIds",List.of(member))),"roster");assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(200,call("POST","/meetings/"+meeting+"/roster-confirmations","{\"expectedRevisionToken\":\"1\"}","confirm").statusCode());
        String outsider="task04-outsider-"+UUID.randomUUID(),outsiderToken=identity(outsider),admin="task04-admin-"+UUID.randomUUID(),adminToken=identity(admin);j.update("INSERT INTO role_assignment VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))",admin);
        for(String denied:List.of(memberToken,outsiderToken,adminToken)) {
            token=denied;
            for(String path:List.of("uploads","completed-files","processing-inputs","processing-tasks")) {var response=call("GET","/meetings/"+meeting+"/"+path,"",null);assertEquals(403,response.statusCode(),response.body());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());}
            assertEquals(403,call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","2","orderedFileIds",List.of(file))),"denied").statusCode());
        }
        token=memberToken;var capabilities=call("GET","/meetings/"+meeting+"/processing-capabilities","",null);assertEquals(200,capabilities.statusCode(),capabilities.body());assertFalse(body(capabilities).get("canSubmit").asBoolean());
        token=originalToken;j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
        assertEquals(401,call("GET","/meetings/"+meeting+"/uploads","",null).statusCode());
    }
    @Test void actualQueueClaimAndRenewNeedNoFakeTranscriptOrPreleaseWorkerGrant() throws Exception {
        String file=upload("queue\n");var response=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),"queue");assertEquals(202,response.statusCode(),response.body());String task=body(response).get("taskId").asText();ownedTasks.add(task);
        var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=queue.claim("TRANSCRIPT_IMPORT","TASK04_CONTROLLED_WORKER").orElseThrow();assertEquals(task,lease.taskId());assertTrue(queue.renew(lease));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,meeting));
        assertThrows(RuntimeException.class,()->queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.SUCCEEDED,new ama.contract.TaskContracts.TypedRef("TRANSCRIPT_REVISION",UUID.randomUUID().toString()),null)));
        assertTrue(queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"PROCESSING_INPUT_INVALID")));
    }
    private static void await(java.util.concurrent.CountDownLatch latch) {try{if(!latch.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Task4 barrier timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}}
'''
assert at in s;s=s.replace(at,new+at);p.write_text(s)
