from pathlib import Path
p=Path('backend/api/src/main/java/ama/api/ProcessingController.java');s=p.read_text().replace('try(var parser=json.getFactory().createParser(bytes)) {int tokens=0;while(parser.nextToken()!=null)if(++tokens>4096)throw new Failure(413,"REQUEST_TOO_LARGE");}', 'try(var parser=json.getFactory().createParser(bytes)) {int tokens=0;while(parser.nextToken()!=null)if(++tokens>4096)throw new Failure(413,"REQUEST_TOO_LARGE");}catch(IOException invalid){throw new IllegalArgumentException("Invalid JSON");}');p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void staleQueueTaskCanTerminateWithoutAcquiringObsoleteSourcePermission() throws Exception {
        String file=upload("stale\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var response=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"stale");assertEquals(202,response.statusCode(),response.body());String task=body(response).get("taskId").asText();ownedTasks.add(task);
        j.update("UPDATE meeting SET revision=2,cancel_epoch=1 WHERE id=?",meeting);j.update("UPDATE task_subject SET business_version=2,cancel_epoch=1 WHERE subject_id=?",meeting);
        j.update("UPDATE storage_backend SET status='DISABLED',config_revision=2 WHERE id=?",storage.binding.id());
        var queue=app.getBean(ama.task.JdbcTaskQueue.class);assertDoesNotThrow(()->assertTrue(queue.claim("TRANSCRIPT_IMPORT","TASK04_STALE_WORKER").isEmpty()));
        assertEquals("CANCELLED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
    }
'''
s=s.replace(at,new+at);p.write_text(s)
