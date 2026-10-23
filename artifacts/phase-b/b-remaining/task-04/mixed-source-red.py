from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void ineligibleCompletedNeighborDoesNotMislabelAValidSource() throws Exception {
        String valid=upload("valid\n"),invalid=upload("cleanup\n");j.update("UPDATE file_object SET state='DELETE_PENDING' WHERE id=?",invalid);
        var response=call("GET","/meetings/"+meeting+"/completed-files","",null);assertEquals(200,response.statusCode(),response.body());
        var byId=new HashMap<String,JsonNode>();for(var item:body(response).get("items"))byId.put(item.get("fileId").asText(),item);
        assertEquals("ELIGIBLE",byId.get(valid).get("intakeEligibility").get("state").asText());assertEquals("SOURCE_STATE",byId.get(invalid).get("intakeEligibility").get("reason").asText());
        var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(valid))),"valid-neighbor");assertEquals(202,accepted.statusCode(),accepted.body());ownedTasks.add(body(accepted).get("taskId").asText());
        assertEquals(409,call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","1","orderedFileIds",List.of(invalid))),"invalid-neighbor").statusCode());
    }
'''
s=s.replace(at,new+at);p.write_text(s)
