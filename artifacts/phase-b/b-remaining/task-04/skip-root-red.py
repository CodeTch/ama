from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void ordinaryQueueSkipsLockedInputRootsWithoutWaitingOrLeasing() throws Exception {
        String file=upload("skip\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"skip");assertEquals(202,response.statusCode(),response.body());var accepted=body(response);String task=accepted.get("taskId").asText();ownedTasks.add(task);
        var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var blocker=pool.submit(()->new ama.db.TransactionRunner(source).required(()->{j.queryForObject("SELECT id FROM transcription_input WHERE id=? FOR UPDATE",String.class,accepted.get("inputId").asText());held.countDown();await(release);return null;}));assertTrue(held.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var claim=pool.submit(()->app.getBean(ama.task.JdbcTaskQueue.class).claim("TRANSCRIPT_IMPORT","TASK04_SKIP_WORKER"));
            try {assertTrue(assertDoesNotThrow(()->claim.get(2,java.util.concurrent.TimeUnit.SECONDS)).isEmpty());}
            finally {release.countDown();blocker.get(10,java.util.concurrent.TimeUnit.SECONDS);claim.get(10,java.util.concurrent.TimeUnit.SECONDS);}
        }finally{release.countDown();}
        assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
    }
'''
s=s.replace(at,new+at);p.write_text(s)
