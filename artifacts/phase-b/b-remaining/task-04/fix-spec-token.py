from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java');s=p.read_text();a=s.index('  return new CanonicalValueCodec().sha256(obj("profile","ama.transcript-import-producer.v1"');b=s.index('\n }',a);s=s[:a]+'  return TranscriptImportProducer.compute(meeting,input,fingerprint,version,epoch,canonical,cancelled);'+s[b:];p.write_text(s)
p=Path('backend/contracts/src/main/java/ama/contract/FileContracts.java');s=p.read_text().replace('public record Accepted(String uploadId, long partSize) {}','public record Accepted(String uploadId, long partSize) {public String getPartSizeToken(){return Long.toString(partSize);}}');p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcProcessingIntake.java');s=p.read_text();s=s.replace(' private record Replay(String scope,String task,int status,boolean matches) {}',''' private record Replay(String scope,String task,int status,boolean matches) {}
 private record ReplayProof(ama.contract.TaskContracts.TaskSpec spec,String producer) {}
 private static final ama.contract.MinutesContracts.CanonicalBudget REPLAY_PRODUCER_BUDGET=new ama.contract.MinutesContracts.CanonicalBudget(65536,8,128,1024,4096);''')
s=s.replace('access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);match(claimed);return restore(meeting,claimed);','var proof=observe(claimed);access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);match(claimed);return restore(meeting,claimed,proof);')
s=s.replace('var existing=record(actor,meeting,scope,keyHash,hash);access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);','var existing=record(actor,meeting,scope,keyHash,hash);var proof=existing.isPresent()?observe(existing.orElseThrow()):null;access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);')
s=s.replace('return Optional.of(restore(meeting,replay));','return Optional.of(restore(meeting,replay,proof));')
s=s.replace(' private ImportAccepted restore(String meeting,Replay replay) {',''' private ReplayProof observe(Replay replay) {
  if(!replay.matches()||replay.task()==null)return null;
  try {
   var specs=taskSpec(replay.task());if(specs.size()!=1)return null;var spec=specs.getFirst();
   String producer=TranscriptImportProducer.compute(spec.subject().id(),spec.input().id(),spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch(),REPLAY_PRODUCER_BUDGET,()->false);
   return new ReplayProof(spec,producer);
  }catch(IllegalArgumentException|ama.contract.MinutesContracts.Failure corrupt){return null;}
 }
 private List<ama.contract.TaskContracts.TaskSpec> taskSpec(String task) {
  return j.query("SELECT task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,priority,cancel_epoch,max_attempts FROM durable_task WHERE id=?",(r,n)->new ama.contract.TaskContracts.TaskSpec(r.getString(1),r.getString(2),new ama.contract.TaskContracts.SubjectRef(r.getString(3),r.getString(4)),r.getString(5),new ama.contract.TaskContracts.TypedRef(r.getString(6),r.getString(7)),r.getString(8),r.getLong(9),r.getInt(10),r.getLong(11),r.getInt(12)),task);
 }
 private ImportAccepted restore(String meeting,Replay replay,ReplayProof proof) {
  if(proof==null||!proof.producer().equals(proof.spec().producerKey())||!taskSpec(replay.task()).equals(List.of(proof.spec())))throw unavailable();''')
p.write_text(s)
p=Path('backend/api/src/main/java/ama/api/ProcessingController.java');s=p.read_text();needle='  JsonNode body;try{body=json.readTree(bytes);}';s=s.replace(needle,'''  try(var parser=json.getFactory().createParser(bytes)) {int tokens=0;while(parser.nextToken()!=null)if(++tokens>4096)throw new Failure(413,"REQUEST_TOO_LARGE");}
  JsonNode body;try{body=json.readTree(bytes);}''');p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcProcessingIntake.java');s=p.read_text().replace('if(Thread.currentThread().isInterrupted()||failure.code()==ama.contract.MinutesContracts.FailureCode.CANCELLED)throw failure;','if(Thread.currentThread().isInterrupted()||!Set.of(ama.contract.MinutesContracts.FailureCode.PROFILE_UNAVAILABLE,ama.contract.MinutesContracts.FailureCode.CAPABILITY_UNAVAILABLE,ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,ama.contract.MinutesContracts.FailureCode.READ_FAILED,ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE).contains(failure.code()))throw failure;');p.write_text(s)
p=Path('backend/api/src/main/java/ama/api/ProcessingErrors.java');s=p.read_text().replace(' @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void provenance(ama.contract.MinutesContracts.Failure failure,HttpServletResponse response)throws IOException {BusinessErrors.write(response,503,"PROCESSING_UNAVAILABLE","Processing is unavailable");response.setHeader("Cache-Control","private, no-store");}', ''' @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void provenance(ama.contract.MinutesContracts.Failure failure,HttpServletResponse response)throws IOException {
  boolean conflict=java.util.Set.of(ama.contract.MinutesContracts.FailureCode.FINGERPRINT_MISMATCH,ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE).contains(failure.code());
  BusinessErrors.write(response,conflict?409:503,conflict?"PROCESSING_CONFLICT":"PROCESSING_UNAVAILABLE","Processing request could not be completed");response.setHeader("Cache-Control","private, no-store");
 }
 @ExceptionHandler(ama.contract.FileContracts.Conflict.class) void conflict(HttpServletResponse r)throws IOException {BusinessErrors.write(r,409,"PROCESSING_CONFLICT","Processing request conflicts");}
 @ExceptionHandler(ama.contract.FileContracts.NotFound.class) void source(HttpServletResponse r)throws IOException {BusinessErrors.write(r,409,"PROCESSING_CONFLICT","Processing source changed");}
 @ExceptionHandler(ama.contract.FileContracts.Unavailable.class) void route(HttpServletResponse r)throws IOException {unavailable(r);}
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse r)throws IOException {BusinessErrors.write(r,403,"MEETING_FORBIDDEN","Meeting access denied");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse r)throws IOException {BusinessErrors.write(r,400,"INVALID_ARGUMENT","Invalid request");}
 @ExceptionHandler(Exception.class) void unexpected(HttpServletResponse r)throws IOException {unavailable(r);}''');p.write_text(s)
import json
p=Path('contracts/openapi/files.yaml');d=json.loads(p.read_text());d['Accepted']['properties']['partSizeToken']={'$ref':'./meetings.yaml#/ExpectedRevisionToken'};d['Accepted']['required'].append('partSizeToken');p.write_text(json.dumps(d,indent=2)+'\n')
p=Path('contracts/openapi/processing.yaml');d=json.loads(p.read_text());
for name,base in [('UploadReceipt','UploadSummary'),('InputDetail','InputSummary')]:
 extra=d[name]['allOf'][1];d[name].pop('allOf');d[name].update(type='object',additionalProperties=False,required=d[base]['required']+extra['required'],properties=dict(d[base]['properties'],**extra['properties']))
p.write_text(json.dumps(d,indent=2)+'\n')
