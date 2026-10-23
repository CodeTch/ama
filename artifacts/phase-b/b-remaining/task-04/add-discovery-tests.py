from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new='''    @Test void discoveryRecoversActualReceiptsInputsAndTasksWithoutClientMemory() throws Exception {
        var capabilities=call("GET","/meetings/"+meeting+"/processing-capabilities","",null);
        assertEquals(200,capabilities.statusCode(),capabilities.body());assertEquals("NOT_INSTALLED",body(capabilities).get("execution").asText());
        String file=upload("discovery original\\n");
        var uploads=call("GET","/meetings/"+meeting+"/uploads?limit=1","",null);assertEquals(200,uploads.statusCode(),uploads.body());
        String upload=body(uploads).get("items").get(0).get("uploadId").asText();
        var receipt=body(call("GET","/meetings/"+meeting+"/uploads/"+upload,"",null));
        assertEquals("COMPLETED",receipt.get("state").asText());assertEquals(0,receipt.get("allowedActions").size());assertEquals(2,receipt.get("idempotentRetries").size());
        assertEquals("1",body(call("GET","/meetings/"+meeting+"/uploads/"+upload+"/parts","",null)).get("items").get(0).get("part").asText());
        var completed=body(call("GET","/meetings/"+meeting+"/completed-files","",null));assertEquals(file,completed.get("items").get(0).get("fileId").asText());
        var accepted=body(call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file))),"discover"));ownedTasks.add(accepted.get("taskId").asText());
        var input=call("GET","/meetings/"+meeting+"/processing-inputs/"+accepted.get("inputId").asText(),"",null);assertEquals(200,input.statusCode(),input.body());assertEquals(file,body(input).get("files").get(0).get("fileId").asText());
        var tasks=call("GET","/meetings/"+meeting+"/processing-tasks","",null);assertEquals(200,tasks.statusCode(),tasks.body());assertEquals("READY",body(tasks).get("items").get(0).get("state").asText());
        assertEquals(400,call("GET","/meetings/"+meeting+"/uploads?limit=51","",null).statusCode());
        assertEquals(404,call("GET","/meetings/"+meeting+"/uploads/"+UUID.randomUUID(),"",null).statusCode());
    }
'''
assert at in s;s=s.replace(at,new+at);p.write_text(s)
