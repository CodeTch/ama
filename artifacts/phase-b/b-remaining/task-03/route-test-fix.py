from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java');s=p.read_text();a=s.index('   f.j.update("UPDATE storage_backend SET status=\'READ_ONLY\'',s.index('@Test void duplicateOriginal'));b=s.index('\n  }\n }',a)
s=s[:a]+'''   f.j.update("UPDATE upload_session SET purpose='OTHER_INPUT' WHERE id=?",a.upload());var c=f.components();assertThrows(RuntimeException.class,()->c.intake().readDraft(f.actor,f.meeting,List.of(a.file()),()->false));f.j.update("UPDATE upload_session SET purpose='TRANSCRIPT_INPUT' WHERE id=?",a.upload());
   var readonly=new FileBackendCatalog(f.source,List.of(new FileBackendCatalog.Backend(f.storage.binding.id(),f.storage.binding.type(),2,"READ_ONLY",f.storage.storage,f.storage.binding)),f.storage.binding.id());readonly.configure();
   var roRegistry=new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,readonly,Map.of(),ama.transcript.ArchiveProcessingFixture.B);
   assertDoesNotThrow(()->f.tx.required(()->{var gate=roRegistry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);roRegistry.prelockSources(gate,List.of(a.file()),false).orElseThrow();return null;}));
   var disabled=new FileBackendCatalog(f.source,List.of(new FileBackendCatalog.Backend(f.storage.binding.id(),f.storage.binding.type(),3,"DISABLED",f.storage.storage,f.storage.binding)),f.storage.binding.id());disabled.configure();
   var offRegistry=new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,disabled,Map.of(),ama.transcript.ArchiveProcessingFixture.B);
   assertThrows(RuntimeException.class,()->f.tx.required(()->{var gate=offRegistry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);offRegistry.prelockSources(gate,List.of(a.file()),false);return null;}));assertEquals(0,f.reads.get());
''' + s[b:];p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java');s=p.read_text();i=s.rfind('}');p.write_text(s[:i]+Path('artifacts/phase-b/b-remaining/task-03/identity-extra-test.txt').read_text()+s[i:])
