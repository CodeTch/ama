from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();s=s.replace('try {j.update("UPDATE durable_task SET producer_key=? WHERE id=?","e".repeat(64),task);assertEquals(503,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"producer").statusCode());}', '''try {j.update("UPDATE durable_task SET producer_key=? WHERE id=?","e".repeat(64),task);assertEquals(503,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"producer").statusCode());
            assertEquals(503,call("GET","/meetings/"+meeting+"/processing-tasks/"+task,"",null).statusCode());
            assertEquals(503,call("GET","/meetings/"+meeting+"/processing-inputs/"+body(accepted).get("inputId").asText(),"",null).statusCode());
        }''')
at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void recoveryRacesNeverAdoptDifferentRevokedUncommittedOrAbsentResults() throws Exception {
        for(String mode:List.of("DIFFERENT","ROLLBACK","REVOKED","UNCOMMITTED","LATE","DB_FAILURE","FINAL_CLAIM")) {
            preparationDecorator=java.util.function.UnaryOperator.identity();
            meeting=body(call("POST","/meetings","{}",UUID.randomUUID().toString())).get("id").asText();
            String file=upload("race "+mode+"\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
            String other=mode.equals("DIFFERENT")?json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(upload("other\n")))):payload;
            var reached=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
            preparationDecorator=actual->(a,m,r)->{
                if(!first.compareAndSet(true,false))return actual.prepare(a,m,r);
                var prepared=mode.equals("FINAL_CLAIM")?actual.prepare(a,m,r):null;
                reached.countDown();await(release);if(prepared!=null)return prepared;throw ama.contract.ProcessingContracts.conflict();
            };
            try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var a=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",payload,"race"));assertTrue(reached.await(10,java.util.concurrent.TimeUnit.SECONDS));
                if(mode.equals("LATE")) {
                    release.countDown();assertEquals(409,a.get(10,java.util.concurrent.TimeUnit.SECONDS).statusCode());
                    var b=call("POST","/meetings/"+meeting+"/transcript-imports",other,"race");assertEquals(202,b.statusCode(),b.body());ownedTasks.add(body(b).get("taskId").asText());continue;
                }
                if(mode.equals("UNCOMMITTED")) {
                    var inserted=new java.util.concurrent.CountDownLatch(1);var finish=new java.util.concurrent.CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean(true);
                    source.after=sql->{if(sql.startsWith("INSERT INTO idempotency_record")&&once.compareAndSet(true,false)){inserted.countDown();await(finish);}};
                    try {
                        var b=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",other,"race"));assertTrue(inserted.await(10,java.util.concurrent.TimeUnit.SECONDS));release.countDown();
                        var failed=a.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(503,failed.statusCode(),failed.body());finish.countDown();var winner=b.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(202,winner.statusCode(),winner.body());ownedTasks.add(body(winner).get("taskId").asText());
                    }finally{finish.countDown();source.after=sql->{};}
                    continue;
                }
                if(mode.equals("ROLLBACK"))source.rollbackOn.set("INSERT INTO durable_task");
                var b=call("POST","/meetings/"+meeting+"/transcript-imports",other,"race");
                assertEquals(mode.equals("ROLLBACK")?503:202,b.statusCode(),mode+" "+b.body());if(b.statusCode()==202)ownedTasks.add(body(b).get("taskId").asText());
                if(mode.equals("REVOKED"))j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
                if(mode.equals("DB_FAILURE"))source.failStatement.set("SELECT actor_id,resource_id");
                release.countDown();var recovered=a.get(10,java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(mode.equals("FINAL_CLAIM")?202:mode.equals("REVOKED")?403:mode.equals("DB_FAILURE")?503:409,recovered.statusCode(),mode+" "+recovered.body());
                if(mode.equals("FINAL_CLAIM"))assertEquals(body(b),body(recovered));
            }finally{release.countDown();j.update("UPDATE enterprise_identity SET status='ACTIVE' WHERE enterprise_id=?",owner);source.after=sql->{};}
            assertEquals(mode.equals("ROLLBACK")?0:1,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
        }
    }
    @Test void malformedRequestsAndOversizedNamesFailBeforeEffectsOrTextTransfer() throws Exception {
        String file=upload("limits\n");
        for(String revision:List.of("\"expectedRevisionToken\":null","\"expectedRevisionToken\":\"01\"","\"expectedRevisionToken\":\"+1\"","\"expectedRevisionToken\":\"１\"","\"expectedRevisionToken\":\" 1\"","\"expectedRevisionToken\":\"9223372036854775807\"","\"expectedRevision\":1.0","\"expectedRevision\":1e0","\"expectedRevision\":-1","\"expectedRevision\":9223372036854775807","\"expectedRevision\":0,\"expectedRevisionToken\":\"1\"")) {
            var response=call("POST","/meetings/"+meeting+"/transcript-imports","{"+revision+",\"orderedFileIds\":[\""+file+"\"]}",UUID.randomUUID().toString());assertEquals(400,response.statusCode(),response.body());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        }
        assertEquals(400,call("POST","/meetings/"+meeting+"/transcript-imports","{\"expectedRevision\":0,\"expectedRevision\":0,\"orderedFileIds\":[\""+file+"\"]}","duplicate").statusCode());
        assertEquals(413,call("POST","/meetings/"+meeting+"/transcript-imports"," ".repeat(65537),"oversized").statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=?",Integer.class,meeting));
        j.update("UPDATE file_object SET display_name=? WHERE id=?","😀".repeat(32769),file);source.statements.clear();
        var response=call("GET","/meetings/"+meeting+"/completed-files","",null);assertEquals(503,response.statusCode());assertEquals("PROCESSING_READ_BUDGET_EXCEEDED",body(response).get("error").get("code").asText());
        assertTrue(source.statements.stream().anyMatch(sql->sql.contains("CASE WHEN")&&sql.contains("OCTET_LENGTH(CONVERT(display_name USING utf16))")));
    }
'''
s=s.replace(at,new+at);p.write_text(s)
