from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java')
s=p.read_text();marker=' @Test void realCurrentMemberHistoryOwnerAndMalformedProducerProfilesRemainDistinct()'
assert marker in s
s=s.replace(marker,''' @Test void danglingCurrentPointerIsFatalAfterAuthorizationEvenWithValidLegacyReference() throws Exception {
  try(var f=new TranscriptAuthorityFixture()){
   var uploaded=f.upload("dangling");var canonical=new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,2097152,250000);
   var c=ama.transcript.JdbcTranscriptAuthority.create(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of("TASK03_LEGACY",legacy()),ama.transcript.ArchiveProcessingFixture.B,canonical);
   var p=f.prepare(c,List.of(uploaded));f.bind(c,p,List.of(uploaded));
   f.j.update("INSERT INTO file_reference VALUES (?,'TASK03_LEGACY',?,'TRANSCRIPT_INPUT')",uploaded.file(),UUID.randomUUID().toString());
   f.current(UUID.randomUUID().toString(),3);var outsider=f.outsider();
   assertThrows(MeetingAccess.AccessDeniedException.class,()->c.registry().download(outsider,uploaded.file(),null));
   f.source.statements.clear();assertThrows(ama.contract.FileContracts.Unavailable.class,()->c.registry().download(f.actor,uploaded.file(),null));
   assertNoCompleteLocks(f);assertEquals(0,f.reads.get());assertEquals(1,f.source.statements.stream().filter(q->q.startsWith("SELECT enterprise_id,status")).count());
  }
 }
'''+marker)
p.write_text(s)
