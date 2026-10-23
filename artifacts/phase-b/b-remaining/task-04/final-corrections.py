from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcProcessingIntake.java');s=p.read_text().replace('if(failure.status()!=409&&failure.status()!=503)throw failure;','if(failure.status()!=409&&failure.status()!=503&&failure.status()!=413)throw failure;').replace('}catch(IllegalArgumentException|ama.contract.MinutesContracts.Failure corrupt){return null;}','}catch(ama.contract.MinutesContracts.Failure failure){if(failure.code()==ama.contract.MinutesContracts.FailureCode.CANCELLED)throw failure;return null;}catch(IllegalArgumentException corrupt){return null;}')
s=s.replace('ProcessingContractsHelper::unavailable','ama.contract.ProcessingContracts::unavailable').replace(' private static final class ProcessingContractsHelper {static Failure unavailable(){return ama.contract.ProcessingContracts.unavailable();}}\n','');p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java');s=p.read_text();a=s.index(' private Map<String,String> eligibility(');b=s.index(' private Map<String,Object> completed(',a)
replacement=''' private Map<String,String> eligibility(Actor actor,FileRegistry.LockedMeeting gate,Map<String,Object> root,List<Map<String,Object>> files) {
  var result=new HashMap<String,String>();if(files.isEmpty())return result;
  var candidates=new ArrayList<String>();var read=reader();
  for(var file:files) {
   String id=text(file,"id");result.put(id,"SOURCE_PROVENANCE");
   if(!"AVAILABLE".equals(file.get("state"))){result.put(id,"SOURCE_STATE");continue;}
   var originals=read.select("upload_session","id meeting_id purpose cancel_epoch# backend_id expected_size# expected_sha256","completed_file_id=? AND state='COMPLETED'","id",2,id);
   if(originals.size()!=1)continue;var original=originals.getFirst();
   if(!Objects.equals(root.get("id"),original.get("meeting_id"))||number(file,"size_bytes")!=number(original,"expected_size")||!Objects.equals(file.get("sha256"),original.get("expected_sha256")))continue;
   if(!"TRANSCRIPT_INPUT".equals(original.get("purpose"))){result.put(id,"SOURCE_PURPOSE");continue;}
   if(number(original,"cancel_epoch")!=number(root,"cancel_epoch")){result.put(id,"SOURCE_EPOCH");continue;}
   try{purposes.require(actor,text(root,"id"),text(original,"purpose"));}catch(FilePurposePolicy.Unavailable denied){result.put(id,"SOURCE_PURPOSE");continue;}
   try{backends.historicalStatus(text(original,"backend_id"));}catch(ama.contract.FileContracts.Unavailable missing){result.put(id,"SOURCE_ROUTE");continue;}
   candidates.add(id);
  }
  if(registry==null||candidates.isEmpty())return result;
  if(candidates.size()>((Number)budgets.getOrDefault("maxFiles",0)).intValue())throw new Failure(503,"PROCESSING_READ_BUDGET_EXCEEDED");
  try {
   var receipt=registry.prelockSources(gate,List.copyOf(candidates),false).orElseThrow();
   var sources=registry.sources(gate,receipt,List.copyOf(candidates));
   for(var source:sources)result.put(source.file().fileId(),!writable(root)?"READ_ONLY":!"TRANSCRIPT_INPUT".equals(source.originalPurpose())?"SOURCE_PURPOSE":source.uploadCancelEpoch()!=number(root,"cancel_epoch")?"SOURCE_EPOCH":null);
  }catch(ama.contract.FileContracts.Conflict|ama.contract.FileContracts.NotFound|ama.contract.FileContracts.Unavailable changed){
   // The complete selected candidate union changed. Unknown eligibility is unavailable, never a partial grant.
  }
  return result;
 }
'''
s=s[:a]+replacement+s[b:];s=s.replace('reason.equals("SOURCE_ROUTE")?"UNAVAILABLE":"INELIGIBLE"','Set.of("SOURCE_ROUTE","SOURCE_PROVENANCE").contains(reason)?"UNAVAILABLE":"INELIGIBLE"')
s=s.replace('catch(ama.contract.MinutesContracts.Failure|IllegalArgumentException corrupt){return new InputProofs(Map.of(),false);}', 'catch(ama.contract.MinutesContracts.Failure failure){if(failure.code()==ama.contract.MinutesContracts.FailureCode.CANCELLED)throw failure;return new InputProofs(Map.of(),false);}catch(IllegalArgumentException corrupt){return new InputProofs(Map.of(),false);}')
s=s.replace('throw new Failure(503,"PROCESSING_READ_BUDGET_EXCEEDED");}\n }','throw new Failure(503,failure.code()==ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED?"PROCESSING_READ_BUDGET_EXCEEDED":"PROCESSING_UNAVAILABLE");}\n }')
s=s.replace('"meeting_id=? AND id>?","id",limit+1,meeting,after==null?"":after);\n   var selected=rows.subList', '"meeting_id=? AND id>? AND EXISTS(SELECT 1 FROM upload_session u WHERE u.completed_file_id=file_object.id AND u.state=\'COMPLETED\')","id",limit+1,meeting,after==null?"":after);\n   var selected=rows.subList');p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();s=s.replace('var counters=j.queryForMap("SELECT reference_epoch,location_revision FROM file_object WHERE id=?",file);','''var counters=j.queryForMap("SELECT reference_epoch,location_revision FROM file_object WHERE id=?",file);
        long audits=j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Long.class,meeting),events=j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Long.class,meeting);''')
s=s.replace('assertNull(source.failStatement.get(),"Fault must actually execute: "+stage);','''assertNull(source.failStatement.get(),"Fault must actually execute: "+stage);
            assertEquals(audits,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Long.class,meeting));assertEquals(events,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Long.class,meeting));''');p.write_text(s)
p=Path('artifacts/phase-b/b-remaining/task-04/run.py');s=p.read_text();s=s.replace("info=json.loads(subprocess.check_output(['docker','inspect','ama-test-b01-ad9f1af29eff']))[0]",'''assert not (e/(name+'.command.json')).exists() and not (e/(name+'.sources.json')).exists(), 'Evidence label must be new'
format='{"Id":{{json .Id}},"State":{"Running":{{json .State.Running}}},"NetworkSettings":{"Ports":{{json .NetworkSettings.Ports}}},"Labels":{{json .Config.Labels}}}'
info=json.loads(subprocess.check_output(['docker','inspect','--format',format,'ama-test-b01-ad9f1af29eff']))
assert info['Labels']['ama.remaining.owner']=='remaining-20261023' ''')
needle="files=sorted(p for p in (root/'backend').rglob('*') if p.is_file() and '/src/' in str(p))";s=s.replace(needle,'''if name.startswith('config-restart-final'):
 env['JAVA_TOOL_OPTIONS']='-Xmx64m -XX:ActiveProcessorCount=2'
 import uuid
 restart=(root/'backend/integration-tests/target'/('task04-config-restart-'+str(uuid.uuid4()))).resolve()
 assert restart.parent==(root/'backend/integration-tests/target').resolve() and not restart.exists()
 restart.mkdir();env['AMA_B06_RESTART_EVIDENCE']=str(restart)
(e/(name+'.guard.json')).write_text(json.dumps(info,indent=2)+'\\n')
'''+needle)
p.write_text(s)
