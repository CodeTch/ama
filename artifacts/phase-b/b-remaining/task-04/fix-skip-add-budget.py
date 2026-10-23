from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/BoundedTranscriptMetadata.java');s=p.read_text().replace('private final boolean locked;', 'private final boolean locked,skipLocked;')
s=s.replace('BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget,boolean locked){this.j=j;this.budget=budget;this.locked=locked;}', 'BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget,boolean locked){this(j,budget,locked,false);}\n BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget,boolean locked,boolean skipLocked){this.j=j;this.budget=budget;this.locked=locked;this.skipLocked=skipLocked;}')
s=s.replace('(locked?" FOR UPDATE":"")','(locked?" FOR UPDATE"+(skipLocked?" SKIP LOCKED":""):"")');p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/transcript/ImportTaskDomainPolicy.java');s=p.read_text().replace(' public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain prepared) {',''' public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain prepared) {
  try{return lockAll(specs,skip,prepared);}
  catch(ama.contract.FileContracts.NotFound missing){if(skip)return false;throw missing;}
  catch(ama.contract.MinutesContracts.Failure raced){if(skip&&(raced.code()==PROVENANCE_UNAVAILABLE||raced.code()==PROFILE_UNAVAILABLE))return false;throw raced;}
 }
 private boolean lockAll(List<TaskSpec> specs,boolean skip,PreparedDomain prepared) {''').replace('new BoundedTranscriptMetadata(j,authority.readBudget(),true);','new BoundedTranscriptMetadata(j,authority.readBudget(),true,skip);');p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void operationalBudgetFailureStillDiscoversAnActualCommittedWinner() throws Exception {
        String file=upload("budget race\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        var reached=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        preparationDecorator=actual->(a,m,r)->{if(first.compareAndSet(true,false)){reached.countDown();await(release);throw new ama.contract.ProcessingContracts.Failure(413,"REQUEST_TOO_LARGE");}return actual.prepare(a,m,r);};
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->call("POST","/meetings/"+meeting+"/transcript-imports",payload,"budget"));assertTrue(reached.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var b=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"budget");assertEquals(202,b.statusCode(),b.body());ownedTasks.add(body(b).get("taskId").asText());release.countDown();
            var result=a.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(202,result.statusCode(),result.body());assertEquals(body(b),body(result));
        }finally{release.countDown();}
    }
'''
s=s.replace(at,new+at);p.write_text(s)
