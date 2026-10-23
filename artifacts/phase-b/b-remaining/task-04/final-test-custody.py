from pathlib import Path
import hashlib,json
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();old=hashlib.sha256(p.read_bytes()).hexdigest()
s=s.replace('final List<String> ownedTasks=new ArrayList<>();','''final List<String> ownedTasks=new ArrayList<>();
    final Map<String,Map<String,Object>> acceptedSpecs=new LinkedHashMap<>();
    static final String SPEC_COLUMNS="id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts";
    void track(String task) {
        var spec=j.queryForMap("SELECT "+SPEC_COLUMNS+" FROM durable_task WHERE id=?",task);
        assertEquals("TRANSCRIPT_IMPORT_V1",spec.get("task_type"));
        assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
        assertFalse(acceptedSpecs.containsKey(task));
        acceptedSpecs.put(task,Map.copyOf(spec));
        ownedTasks.add(task);
    }''')
s=s.replace('ownedTasks.add(', 'track(').replace('        track(task);\n    }','        ownedTasks.add(task);\n    }',1)
insert='''    @Test void lostCommitAcknowledgmentRecoversTheActualCommittedAcceptance() throws Exception {
        String file=upload("lost acknowledgment\\n");
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
'''
s=s.replace('    @AfterEach void close()',insert+'    @AfterEach void close()')
s=s.replace('var current=j.queryForObject("SELECT state FROM durable_task WHERE id=? FOR UPDATE",String.class,task);','''var locked=j.queryForMap("SELECT "+SPEC_COLUMNS+",state FROM durable_task WHERE id=? FOR UPDATE",task);
                var current=(String)locked.remove("state");
                assertEquals(acceptedSpecs.get(task),locked,"accepted immutable spec must remain exact before deny-only fixture teardown");''')
s=s.replace('        if(meeting!=null&&source!=null) {','''        if(meeting!=null)Files.writeString(receipts.resolve(meeting+"-accepted-specs.json"),json.writeValueAsString(acceptedSpecs));
        if(meeting!=null&&source!=null) {''')
p.write_text(s,newline='\n')
Path('artifacts/phase-b/b-remaining/task-04/final-test-custody-change.json').write_text(json.dumps({'path':str(p),'before_sha256':old,'after_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'reason':'Explicit real lost commit ACK test and accepted immutable fullspec READY custody/transactional teardown guard; production bytes unchanged'},indent=2)+'\n')
