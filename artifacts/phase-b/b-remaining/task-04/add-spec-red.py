from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void acceptedResultWithCorruptFullProducerSpecIsUnavailable() throws Exception {
        String file=upload("producer\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"producer");assertEquals(202,accepted.statusCode(),accepted.body());String task=body(accepted).get("taskId").asText();ownedTasks.add(task);
        String original=j.queryForObject("SELECT producer_key FROM durable_task WHERE id=?",String.class,task);
        try {j.update("UPDATE durable_task SET producer_key=? WHERE id=?","e".repeat(64),task);assertEquals(503,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"producer").statusCode());}
        finally {j.update("UPDATE durable_task SET producer_key=? WHERE id=?",original,task);}
    }
    @Test void originalUploadBeginEmitsOpaquePartSizeToken() throws Exception {
        var begin=call("POST","/meetings/"+meeting+"/uploads",json.writeValueAsString(Map.of("purpose","TRANSCRIPT_INPUT","name","token.txt","size",1,"sha256",hash("x"))),UUID.randomUUID().toString());assertEquals(201,begin.statusCode(),begin.body());
        assertTrue(body(begin).has("partSizeToken"),begin.body());assertEquals(body(begin).get("partSize").asText(),body(begin).get("partSizeToken").asText());
    }
'''
s=s.replace(at,new+at);p.write_text(s)
