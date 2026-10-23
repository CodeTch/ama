from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java');s=p.read_text();s=s.replace(' private static final String UPLOAD=', ''' private record InputProofs(Map<String,ama.contract.TaskContracts.TaskSpec> specs,boolean valid) {}
 private static final ama.contract.MinutesContracts.CanonicalBudget PRODUCER=new ama.contract.MinutesContracts.CanonicalBudget(65536,8,128,1024,4096);
 private static final String UPLOAD=''')
s=s.replace(''' public Page inputs(Actor actor,String meeting,int limit,String after) {
  page(limit,50);cursor(after);
  return owner''',''' public Page inputs(Actor actor,String meeting,int limit,String after) {
  page(limit,50);cursor(after);
  var proofs=proofs(meeting,j.queryForList("SELECT id FROM transcription_input WHERE meeting_id=? AND id>? ORDER BY id LIMIT ?",String.class,meeting,after==null?"":after,limit));
  return owner''')
s=s.replace('return page(rows,limit,row->inputSummary(row),"id");','return page(rows,limit,row->{verifyProof(row,proofs);return inputSummary(row);},"id");')
s=s.replace('return owner(actor,meeting,(gate,root)->inputDetail(meeting,input));','var proofs=proofs(meeting,List.of(input));\n  return owner(actor,meeting,(gate,root)->inputDetail(meeting,input,proofs));')
s=s.replace('private Map<String,Object> inputDetail(String meeting,String input) {','private Map<String,Object> inputDetail(String meeting,String input,InputProofs proofs) {')
s=s.replace('if(tasks.size()!=1)throw unavailable();verifyTask(tasks.getFirst(),row);','if(tasks.size()!=1)throw unavailable();verifyTask(tasks.getFirst(),row);verifyProof(row,proofs);')
s=s.replace('''page(limit,50);cursor(after);if(!Set.of("active","all").contains(scope))throw new IllegalArgumentException("Invalid task scope");
  return owner''','''page(limit,50);cursor(after);if(!Set.of("active","all").contains(scope))throw new IllegalArgumentException("Invalid task scope");
  var proofs=proofs(meeting,j.queryForList("SELECT input_id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1' AND id>?"+(scope.equals("active")?" AND state IN ('READY','RUNNING')":"")+" ORDER BY id LIMIT ?",String.class,meeting,after==null?"":after,limit));
  return owner''')
s=s.replace('taskSummary(meeting,row),"id")','taskSummary(meeting,row,proofs),"id")')
s=s.replace(''' public Map<String,Object> task(Actor actor,String meeting,String task) {
  ama.contract.OutboxContracts.uuid(task);
  return owner''',''' public Map<String,Object> task(Actor actor,String meeting,String task) {
  ama.contract.OutboxContracts.uuid(task);
  var proofs=proofs(meeting,j.queryForList("SELECT input_id FROM durable_task WHERE id=? AND subject_kind='MEETING' AND subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1' LIMIT 2",String.class,task,meeting));
  return owner''')
s=s.replace('return taskSummary(meeting,rows.getFirst());','return taskSummary(meeting,rows.getFirst(),proofs);').replace('private Map<String,Object> taskSummary(String meeting,Map<String,Object> row) {','private Map<String,Object> taskSummary(String meeting,Map<String,Object> row,InputProofs proofs) {')
s=s.replace('var input=inputs.getFirst();verifyTask(row,input);','var input=inputs.getFirst();verifyTask(row,input);verifyProof(input,proofs);')
needle=' private void verifyTask(Map<String,Object> task,Map<String,Object> input) {'
insert=''' private InputProofs proofs(String meeting,List<String> inputs) {
  independent();var result=new HashMap<String,ama.contract.TaskContracts.TaskSpec>();
  try {
   for(String input:new LinkedHashSet<>(inputs)) {
    var rows=reader().select("durable_task",BoundedTranscriptMetadata.TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,input);
    if(rows.size()!=1)return new InputProofs(Map.of(),false);var spec=TranscriptReadGraph.spec(rows.getFirst());
    if(!meeting.equals(spec.subject().id())||!input.equals(spec.input().id())||!TranscriptImportProducer.compute(meeting,input,spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch(),PRODUCER,()->false).equals(spec.producerKey()))return new InputProofs(Map.of(),false);
    result.put(input,spec);
   }
   return new InputProofs(Map.copyOf(result),true);
  }catch(ama.contract.MinutesContracts.Failure|IllegalArgumentException corrupt){return new InputProofs(Map.of(),false);}
 }
 private void verifyProof(Map<String,Object> input,InputProofs proofs) {
  var spec=proofs.specs().get(text(input,"id"));if(!proofs.valid()||spec==null)throw unavailable();
  var actual=reader().select("durable_task",BoundedTranscriptMetadata.TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,input.get("id"));
  if(actual.size()!=1||!TranscriptReadGraph.spec(actual.getFirst()).equals(spec))throw unavailable();verifyTask(actual.getFirst(),input);
 }
'''
assert needle in s;s=s.replace(needle,insert+needle)
s=s.replace('var result=new HashMap<String,String>();if(files.isEmpty())return result;','var result=new HashMap<String,String>();if(files.isEmpty())return result;\n  if(registry!=null&&files.size()>((Number)budgets.getOrDefault("maxFiles",0)).intValue())throw new Failure(503,"PROCESSING_READ_BUDGET_EXCEEDED");')
p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java');s=p.read_text();needle='    public String historicalStatus(String id) { return requireHistoricalMetadata(id).status(); }';s=s.replace(needle,needle+'''
    /** Metadata observation over explicitly installed historical routes, independent of a write default. */
    public boolean hasHistoricalRoute() {
        for(String id:backends.keySet())try{requireHistoricalMetadata(id);return true;}catch(Unavailable ignored){}
        return false;
    }
''');p.write_text(s)
p=Path('backend/api/src/main/java/ama/api/ProcessingConfiguration.java');s=p.read_text().replace('snapshot.loadClosure(List.of(ref),budget,0,()->false);backends.defaultWrite();','snapshot.loadClosure(List.of(ref),budget,0,()->false);if(!backends.hasHistoricalRoute())return false;');p.write_text(s)
