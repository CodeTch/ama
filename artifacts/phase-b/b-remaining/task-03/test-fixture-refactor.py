from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java');s=p.read_text()
a=s.index('   var raw=new java.util.ArrayList<ama.contract.ConfigArchiveContracts.RawArchive>();')
b=s.index('   f.tx.required(()->{',a)
block=s[a:b]
# Move actual archive/preparation setup into reusable owned fixture method, preserving first bind test.
f=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityFixture.java');t=f.read_text();pos=t.index(' public void close()')
method=''' ama.transcript.JdbcTranscriptAuthority.Components components(){return ama.transcript.JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of(),B,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,2097152,250000));}
 ama.transcript.JdbcTranscriptAuthority.PreparedIntake prepare(ama.transcript.JdbcTranscriptAuthority.Components c,List<Uploaded> uploaded)throws Exception {
  var draft=c.intake().readDraft(actor,meeting,uploaded.stream().map(Uploaded::file).toList(),()->false);
'''+block+'''  return prepared;
 }
 void bind(ama.transcript.JdbcTranscriptAuthority.Components c,ama.transcript.JdbcTranscriptAuthority.PreparedIntake p,List<Uploaded> uploaded){
  tx.required(()->{
   var gate=c.registry().lock(actor,meeting,MeetingAccess.Action.EDIT);var reservation=c.authority().reserveIntake(gate,p);
   var bindings=new ArrayList<FileRegistry.Binding>();
   for(var u:uploaded)bindings.add(new FileRegistry.Binding(c.registry().prelock(gate,new FileRegistry.Reference("INPUT_BATCH_FILE",p.batchId(),"TRANSCRIPT_INPUT"),u.file(),reservation),0,u.upload()));
   var receipt=c.registry().prelockSources(gate,uploaded.stream().map(Uploaded::file).toList(),false).orElseThrow();
   var held=c.authority().insertAndReadNew(reservation,receipt);c.registry().bindAll(receipt,bindings);c.authority().requireHeld(held,gate,p.spec());return null;
  });
 }
'''
method=method.replace('   String batch=UUID.randomUUID().toString(),input=UUID.randomUUID().toString();','   String batch=UUID.randomUUID().toString(),input=UUID.randomUUID().toString();')
t=t[:pos]+method+t[pos:];t=t.replace('import static ama.transcript.ArchiveProcessingFixture.*;','import static ama.transcript.ArchiveProcessingFixture.*;\nimport static org.junit.jupiter.api.Assertions.*;');f.write_text(t)
s=s[:a]+'''   var prepared=f.prepare(c,List.of(a));String batch=prepared.batchId(),input=prepared.inputId();
'''+s[b:];p.write_text(s)
