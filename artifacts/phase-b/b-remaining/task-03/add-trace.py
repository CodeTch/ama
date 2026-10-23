from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityFixture.java');s=p.read_text().replace(' final RegistryProbeSource source=new RegistryProbeSource(MysqlTestDatabase.dataSource());',' final Task03TraceSource trace=new Task03TraceSource(MysqlTestDatabase.dataSource());\n final RegistryProbeSource source=new RegistryProbeSource(trace);');p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java');s=p.read_text();a=s.index('  var reader=new BoundedTranscriptMetadata(j,budget);var task=reader.one("durable_task"',s.index(' FileRegistry.Download worker('));b=s.index('  return registry.read(actor,meeting,',a)
s=s[:a]+'''  var tasks=j.queryForList("SELECT subject_kind,subject_id FROM durable_task WHERE id=?",lease.taskId());
  if(tasks.size()!=1||!"MEETING".equals(tasks.getFirst().get("subject_kind")))throw new ama.contract.FileContracts.NotFound();
  String meeting=(String)tasks.getFirst().get("subject_id");uuid(meeting);
  var owners=j.queryForList("SELECT owner_id FROM meeting WHERE id=?",String.class,meeting);if(owners.size()!=1)throw new ama.contract.FileContracts.NotFound();
  var actor=new Actor(owners.getFirst(),ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
''' +s[b:];p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java');s=p.read_text().replace('"object_key","expected_sha256"),skip,state,3)','"object_key","expected_sha256","state","completed_file_id"),skip,state,3)');p.write_text(s)
