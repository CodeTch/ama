from pathlib import Path
root=Path('.')
p=root/'backend/core/src/main/java/ama/storage/FileRegistry.java'
s=p.read_text().replace('interface ReadRequest { String referenceKind(); }','interface ReadRequest { String referenceKind(); }\n    record CurrentReadRequest(String referenceKind, String fileId) implements ReadRequest {}')
p.write_text(s)
p=root/'backend/core/src/main/java/ama/storage/CompleteReadPolicy.java'
s=p.read_text().replace('import java.util.List;','import java.util.List;\nimport ama.contract.IdentityContracts.Actor;')
s=s.replace('    ReadPlan prelockRead(FileRegistry.LockedMeeting gate, String meetingId, FileRegistry.ReadRequest request);','''    interface ReadPreparation {}
    ReadPreparation prepareRead(Actor actor, String meetingId, FileRegistry.ReadRequest request);
    void authorizeRead(FileRegistry.LockedMeeting gate, String meetingId, FileRegistry.ReadRequest request);
    Object prelockCurrent(FileRegistry.LockedMeeting gate, String meetingId,
                          FileRegistry.Reference reference, String fileId, ReadPreparation prepared);
    ReadPlan prelockRead(FileRegistry.LockedMeeting gate, String meetingId,
                         FileRegistry.ReadRequest request, ReadPreparation prepared);''')
p.write_text(s)
p=root/'backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java'
s=p.read_text();start=s.rfind('}')
s=s[:start]+(root/'artifacts/phase-b/b-remaining/task-03/read-authority-fragment.txt').read_text()+s[start:]
s=s.replace('  if(!(context instanceof ReservedIntake reserved))throw new ama.contract.FileContracts.NotFound();','  if(context instanceof JdbcTranscriptAuthority.ReadScope)return prelockReadMember(gate,meeting,reference,file,context);\n  if(!(context instanceof ReservedIntake reserved))throw new ama.contract.FileContracts.NotFound();')
s=s.replace('  if(!(token instanceof ReservedIntake reserved))throw new ama.contract.FileContracts.NotFound();','  if(token instanceof JdbcTranscriptAuthority.ReadScope){validateRead(token,meeting,reference,file,originalPurpose);return;}\n  if(!(token instanceof ReservedIntake reserved))throw new ama.contract.FileContracts.NotFound();')
p.write_text(s)
p=root/'backend/adapters/src/main/java/ama/transcript/InputBatchFileReferencePolicy.java'
s=p.read_text().replace(' public ReadPlan prelockRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request){throw new ama.contract.FileContracts.Unavailable();}', ''' public ReadPreparation prepareRead(ama.contract.IdentityContracts.Actor actor,String meeting,FileRegistry.ReadRequest request){return authority.prepareRead(actor,meeting,request);}
 public void authorizeRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request){authority.authorizeRead(gate,meeting,request);}
 public Object prelockCurrent(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference ref,String file,ReadPreparation prepared){return authority.prelockCurrent(gate,meeting,ref,file,prepared);}
 public ReadPlan prelockRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request,ReadPreparation prepared){return authority.prelockRead(gate,meeting,request,prepared);}''')
s=s.replace('public List<ReadMember> readMembers(Object domain,FileRegistry.Reference ref,String file){throw new ama.contract.FileContracts.Unavailable();}', 'public List<ReadMember> readMembers(Object domain,FileRegistry.Reference ref,String file){return authority.readMembers(domain,ref,file);}')
s=s.replace('public void prelockReadTasks(List<Object> scopes){throw new ama.contract.FileContracts.Unavailable();}', 'public void prelockReadTasks(List<Object> scopes){authority.prelockReadTasks(scopes);}')
s=s.replace('public void finishRead(Object domain,List<FileRegistry.CompletedSource> sources){throw new ama.contract.FileContracts.Unavailable();}', 'public void finishRead(Object domain,List<FileRegistry.CompletedSource> sources){authority.finishRead(domain,sources);}')
p.write_text(s)
p=root/'backend/adapters/src/main/java/ama/transcript/JdbcProcessingSourceReader.java'
s=p.read_text().replace('throw new ama.contract.FileContracts.Unavailable();','return authority.worker(lease,input,file);',1).replace('throw new ama.contract.FileContracts.Unavailable();','return authority.history(actor,meeting,revision,file,range);',1);p.write_text(s)
p=root/'backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java'
s=p.read_text().replace('public ReadPlan prelockRead(FileRegistry.LockedMeeting g,String m,FileRegistry.ReadRequest r){','''public ReadPreparation prepareRead(Actor a,String m,FileRegistry.ReadRequest r){return new ReadPreparation(){};}
    public void authorizeRead(FileRegistry.LockedMeeting g,String m,FileRegistry.ReadRequest r){}
    public Object prelockCurrent(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String id,ReadPreparation p){return new Scope();}
    public ReadPlan prelockRead(FileRegistry.LockedMeeting g,String m,FileRegistry.ReadRequest r,ReadPreparation p){''')
s=s.replace('FileRegistry.ReadRequest request=()->ref.kind();','FileRegistry.ReadRequest request=new FileRegistry.CurrentReadRequest(ref.kind(),a.file());');p.write_text(s)
p=root/'backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityFixture.java'
s=p.read_text().replace(' final JdbcFileUploads uploads;', ' final JdbcFileUploads uploads;\n final List<String> ownedTasks=new ArrayList<>();')
s=s.replace('var spec=p.spec();String task=id();', 'var spec=p.spec();String task=id();ownedTasks.add(task);')
s=s.replace(' public void close() throws Exception {storage.close();}', ''' public void close() throws Exception {
  try {
   for(String task:ownedTasks){
    tx.required(()->{
     var state=j.queryForObject("SELECT state FROM durable_task WHERE id=? FOR UPDATE",String.class,task);
     if(!Set.of("SUCCEEDED","FAILED").contains(state)){
      j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='FAILED',error_code='TASK03_FIXTURE_CLOSED' WHERE task_id=? AND ended_at IS NULL",task);
      j.update("UPDATE durable_task SET state='FAILED',lease_owner=NULL,expires_at=NULL WHERE id=?",task);
     }
     return null;
    });
   }
   Path receipt=Path.of("target/task03-custody");Files.createDirectories(receipt);
   var rows=new ArrayList<String>();rows.add("owner="+owner+" meeting="+meeting+" nativeRoot="+storage.root);
   for(String task:ownedTasks)rows.add("synthetic-read-fixture task="+task+" state="+j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
   Files.write(receipt.resolve(meeting+".txt"),rows);
  } finally {storage.close();}
 }''')
# root path is not exposed by ManagedRegistryFixture; record only public fixture identity.
s=s.replace('+" nativeRoot="+storage.root','')
p.write_text(s)
