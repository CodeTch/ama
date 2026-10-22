import pathlib
p=pathlib.Path("backend/integration-tests/src/test/java/ama/task/B06QueueSeamsIT.java")
s=p.read_text().replace("SET stage='TRANSCRIPT'","SET stage='TRANSCRIPT_PENDING'")
needle="    static int index(List<String> sql,String part)"
method="""    @Test void currentThreeArgumentCompletionValidatesOutputBeforeEffects(){
        String task=queue.enqueue(spec);
        TaskLease lease=queue.claim(spec.pool(),"SEAM_OUTPUT_WORKER").orElseThrow();
        String output=UUID.randomUUID().toString();
        var effects=new AtomicInteger();
        var outputPolicy=new TaskDomainPolicy(){
            public boolean lockRoot(TaskSpec s,boolean skip){return policy.lockRoot(s,skip);}
            public void validateInput(TaskSpec s){policy.validateInput(s);}
            public void validateOutput(TaskSpec s,TaskResult r){
                var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",r.output().id());
                if(!r.output().kind().equals("TEST_INFO")||rows.size()!=1
                        ||!rows.getFirst().get("meeting_id").equals(meeting)
                        ||((Number)rows.getFirst().get("revision")).longValue()!=1)
                    throw new IllegalArgumentException("Output row not present before effects");
            }
            public void validateExternalRequest(TaskSpec s,ExternalRequest r){policy.validateExternalRequest(s,r);}
        };
        var outputQueue=new JdbcTaskQueue(source,fences,outbox,Map.of("B06_SEAM_TEST",outputPolicy));
        var failure=assertThrows(IllegalArgumentException.class,()->outputQueue.completeWithEffects(lease,
                new TaskResult(Outcome.SUCCEEDED,new TypedRef("TEST_INFO",output),null),()->{
                    effects.incrementAndGet();
                    j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,1,'output',?,UTC_TIMESTAMP(6))",output,meeting,owner);
                }));
        assertEquals("Output row not present before effects",failure.getMessage());
        assertEquals(0,effects.get());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?",Integer.class,output));
        assertEquals("RUNNING",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
        System.out.println("B06 existing three-argument output ordering control: validation rejected absent real output before effects; effects=0; output rows=0; task=RUNNING. This is existing API behavior, not a production defect.");
    }
"""
assert needle in s
p.write_text(s.replace(needle,method+needle))
