from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text().replace('ConfigurableApplicationContext app; ManagedRegistryFixture storage; Task04ProbeSource source;','ConfigurableApplicationContext app; ManagedRegistryFixture storage; Task04ProbeSource source; RegistryObservedStorage observed;\n    Map<String,Object> deploymentBounds;volatile boolean purposeEnabled=true;')
s=s.replace('        storage=new ManagedRegistryFixture(root);','        storage=new ManagedRegistryFixture(root);observed=new RegistryObservedStorage(storage.storage);')
s=s.replace('        app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds)', '        deploymentBounds=Map.copyOf(bounds);\n        app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds)')
s=s.replace('c.registerBean("task04Purpose",FilePurposePolicy.class,TranscriptFilePurposePolicy::new);','c.registerBean("task04Purpose",FilePurposePolicy.class,()->(a,m,p)->{if(!purposeEnabled)throw new FilePurposePolicy.Unavailable();new TranscriptFilePurposePolicy().require(a,m,p);});')
s=s.replace('storage.backend(storage.storage)', 'storage.backend(observed)')
a=s.index('    String upload(String value) throws Exception {');b=s.index('    @Test void nativeUpload',a)
new=r'''    String open(String value) throws Exception {
        String checksum=hash(value);
        var begin=call("POST","/meetings/"+meeting+"/uploads",json.writeValueAsString(Map.of("purpose","TRANSCRIPT_INPUT","name","original😀.txt","size",value.getBytes(StandardCharsets.UTF_8).length,"sha256",checksum)),UUID.randomUUID().toString());assertEquals(201,begin.statusCode(),begin.body());String upload=body(begin).get("uploadId").asText();
        var part=part(upload,value);assertEquals(204,part.statusCode(),part.body());return upload;
    }
    HttpResponse<String> part(String upload,String value) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1/uploads/"+upload+"/parts/1")).header("Cookie","__Host-AMA_SESSION="+token).header("Origin",origin).header("X-CSRF-Token",csrf).header("Content-Type","application/octet-stream").header("X-Checksum-SHA256",hash(value)).PUT(HttpRequest.BodyPublishers.ofString(value)).build();
        return HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> complete(String upload,String value) throws Exception {
        return call("POST","/uploads/"+upload+"/completion",json.writeValueAsString(Map.of("parts",List.of(Map.of("part",1,"sha256",hash(value))),"sha256",hash(value))),null);
    }
    String upload(String value) throws Exception {
        var completed=complete(open(value),value);assertEquals(200,completed.statusCode(),completed.body());return body(completed).get("fileId").asText();
    }
    void restartRoute(long revision,String status,boolean defaultEnabled) {
        app.close();
        app=new SpringApplicationBuilder(ApiApplication.class).properties(deploymentBounds).initializers(context->{
            var c=(GenericApplicationContext)context;c.registerBean("task04DataSource",javax.sql.DataSource.class,()->source);
            c.registerBean("task04Purpose",FilePurposePolicy.class,()->(a,m,p)->{if(!purposeEnabled)throw new FilePurposePolicy.Unavailable();new TranscriptFilePurposePolicy().require(a,m,p);});
            c.registerBean("task04Backends",FileBackendDefinitions.class,()->new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(storage.binding.id(),storage.binding.type(),revision,status,observed,storage.binding)),defaultEnabled?storage.binding.id():null));
        }).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());
    }
'''
s=s[:a]+new+s[b:]
at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void historicalRouteActionsAndTerminalRetriesMatchActualB04Operations() throws Exception {
        String value="matrix\n",open=open(value),finalizing=open(value),disabledFinalizing=open(value),aborted=open(value);
        assertEquals(204,call("DELETE","/uploads/"+aborted,"",null).statusCode());String file=upload(value);
        String completed=j.queryForObject("SELECT id FROM upload_session WHERE completed_file_id=?",String.class,file);
        for(String upload:List.of(finalizing,disabledFinalizing)) {
            observed.beforeComplete.set(()->{throw new ama.contract.StorageContracts.StorageException(ama.contract.StorageContracts.ErrorCode.STORAGE_UNAVAILABLE);});
            assertEquals(503,complete(upload,value).statusCode());assertEquals("FINALIZING",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,upload));
        }
        restartRoute(2,"READ_ONLY",false);
        var receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+open,"",null));assertEquals(List.of("ABORT"),json.convertValue(receipt.get("allowedActions"),List.class));
        assertEquals(503,part(open,value).statusCode());assertEquals(503,complete(open,value).statusCode());
        j.update("UPDATE storage_backend SET config_revision=99 WHERE id=?",storage.binding.id());
        assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+open,"",null)).get("allowedActions").size());assertEquals(503,call("DELETE","/uploads/"+open,"",null).statusCode());j.update("UPDATE storage_backend SET config_revision=2 WHERE id=?",storage.binding.id());
        assertEquals(204,call("DELETE","/uploads/"+open,"",null).statusCode());assertEquals(204,call("DELETE","/uploads/"+aborted,"",null).statusCode());
        receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+finalizing,"",null));assertEquals(List.of("COMPLETE"),json.convertValue(receipt.get("allowedActions"),List.class));
        assertEquals(409,part(finalizing,value).statusCode());assertEquals(409,call("DELETE","/uploads/"+finalizing,"",null).statusCode());assertEquals(200,complete(finalizing,value).statusCode());
        var capability=call("GET","/meetings/"+meeting+"/processing-capabilities","",null);assertEquals("AVAILABLE",body(capability).get("intake").asText());
        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),"readonly");assertEquals(202,accepted.statusCode(),accepted.body());ownedTasks.add(body(accepted).get("taskId").asText());
        restartRoute(3,"DISABLED",false);
        receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+disabledFinalizing,"",null));assertEquals(0,receipt.get("allowedActions").size());assertEquals(0,receipt.get("idempotentRetries").size());assertEquals(503,complete(disabledFinalizing,value).statusCode());
        assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+aborted,"",null)).get("idempotentRetries").size());assertEquals(503,call("DELETE","/uploads/"+aborted,"",null).statusCode());
        j.update("UPDATE meeting SET ever_published=1,stage='PUBLISHED' WHERE id=?",meeting);
        receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+completed,"",null));assertEquals(0,receipt.get("allowedActions").size());assertEquals(2,receipt.get("idempotentRetries").size());assertEquals(200,complete(completed,value).statusCode());assertEquals(204,call("DELETE","/uploads/"+completed,"",null).statusCode());
        assertEquals(400,complete(completed,"wrong").statusCode());
        purposeEnabled=false;assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+completed,"",null)).get("idempotentRetries").size());assertEquals(503,complete(completed,value).statusCode());purposeEnabled=true;
        j.update("UPDATE meeting SET cancel_epoch=1 WHERE id=?",meeting);j.update("UPDATE task_subject SET cancel_epoch=1 WHERE subject_id=?",meeting);
        assertEquals(0,body(call("GET","/meetings/"+meeting+"/uploads/"+completed,"",null)).get("idempotentRetries").size());assertEquals(409,complete(completed,value).statusCode());
    }
    @Test void unsafeRequestsFreshRevocationAndGovernanceAreNeverClientPermissions() throws Exception {
        String file=upload("csrf\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        String url="http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1/meetings/"+meeting+"/transcript-imports";
        for(String mode:List.of("COOKIE","CSRF","ORIGIN","DUPLICATE")) {
            var request=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json").header("Idempotency-Key","unsafe");
            if(!mode.equals("COOKIE"))request.header("Cookie","__Host-AMA_SESSION="+token);
            if(!mode.equals("CSRF"))request.header("X-CSRF-Token",csrf);
            request.header("Origin",mode.equals("ORIGIN")?"https://foreign.example.invalid":origin);if(mode.equals("DUPLICATE"))request.header("Origin",origin);
            var response=HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(mode.equals("COOKIE")?401:403,response.statusCode(),response.body());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        }
        j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",meeting);assertEquals(403,call("GET","/meetings/"+meeting+"/uploads","",null).statusCode());
        j.update("UPDATE meeting SET governance_state='ACTIVE',lifecycle='DELETING' WHERE id=?",meeting);assertEquals(403,call("GET","/meetings/"+meeting+"/processing-capabilities","",null).statusCode());
        j.update("UPDATE meeting SET lifecycle='ACTIVE' WHERE id=?",meeting);j.update("UPDATE identity_session SET revoked_at=UTC_TIMESTAMP(6) WHERE enterprise_id=?",owner);assertEquals(401,call("GET","/meetings/"+meeting+"/uploads","",null).statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
    }
'''
s=s.replace(at,new+at);p.write_text(s)
