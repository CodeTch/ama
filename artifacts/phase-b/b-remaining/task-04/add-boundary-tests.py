from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new='''    @Test void httpRevisionBoundariesAndExhaustionPreserveReplayForEveryMutation() throws Exception {
        for(String route:List.of("info","roster-confirmations","transcript-imports"))for(long revision:new long[]{9007199254740991L,9007199254740992L,9007199254740993L,9223372036854775805L,9223372036854775806L}) {
            var created=call("POST","/meetings","{}",UUID.randomUUID().toString());meeting=body(created).get("id").asText();
            j.update("UPDATE meeting SET revision=? WHERE id=?",revision,meeting);j.update("UPDATE task_subject SET business_version=? WHERE subject_kind='MEETING' AND subject_id=?",revision,meeting);
            j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",revision,meeting);
            var request=new LinkedHashMap<String,Object>();request.put("expectedRevisionToken",Long.toString(revision));
            if(route.equals("info"))request.put("rosterDraftIds",List.of());
            if(route.equals("transcript-imports"))request.put("orderedFileIds",List.of(upload("boundary\\n")));
            String payload=json.writeValueAsString(request),key=UUID.randomUUID().toString(),path="/meetings/"+meeting+"/"+route,method=route.equals("info")?"PUT":"POST";
            var first=call(method,path,payload,key);
            if(revision==9223372036854775806L){assertEquals(409,first.statusCode(),route+" "+first.body());continue;}
            assertEquals(route.equals("transcript-imports")?202:200,first.statusCode(),route+" "+first.body());
            assertEquals(Long.toString(revision+1),body(first).get("revisionToken").asText());
            if(route.equals("transcript-imports"))ownedTasks.add(body(first).get("taskId").asText());
            assertEquals(body(first),body(call(method,path,payload,key)));
            request.remove("expectedRevisionToken");request.put("expectedRevision",revision);
            assertEquals(body(first),body(call(method,path,json.writeValueAsString(request),key)));
            request.put("expectedRevisionToken",Long.toString(revision));assertEquals(body(first),body(call(method,path,json.writeValueAsString(request),key)));
            assertEquals(409,call(method,path,payload,UUID.randomUUID().toString()).statusCode());
        }
    }
'''
s=s.replace(at,new+at)
s=s.replace('WHERE id=? AND subject_id=? AND task_type=\'TRANSCRIPT_IMPORT_V1\'",task,meeting','WHERE id=? AND task_type=\'TRANSCRIPT_IMPORT_V1\'",task')
p.write_text(s)
