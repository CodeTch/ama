# Task11 replacement R1 frozen source review package
BASE ba48f35e7ef97c1e162a2d373453bd1e9689529c
HEAD: uncommitted frozen snapshot /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-replacement-review-source-r1-20261025
Manifest SHA256 b8012cc81f0e8de43f74e3cf6c4107da8f01029f8863c3cda52e02180af47109
This is a concrete frozen source gate, not a committed full-task review. Files not changed from BASE are listed as contextual dependencies; read their snapshot only for a named concrete risk.

## backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java
--- a/backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java
+++ b/backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java
@@ -341,30 +341,49 @@
   // Locking reads remain current under RR and select the OLD task, never the successor's result.
   var stored=j.queryForMap("SELECT state,last_error,output_kind,output_id FROM durable_task WHERE id=? FOR UPDATE",oldTask.id());
   var kind=(String)stored.get("output_kind");
   var result=new PreparedExternalPolicy.StoredResult((String)stored.get("state"),(String)stored.get("last_error"),kind==null?null:new TypedRef(kind,(String)stored.get("output_id")));
   if(!result.state().equals("FAILED")&&!result.state().equals("STALE"))return ObservationResult.STALE_TOKEN;
   // The execution's exact marked attempt must be the old terminal task's attempt and already
   // ended with that same local result. Nothing here ends an attempt or rewrites a durable fact.
   var attempts=j.query("SELECT a.owner,a.ended_at,a.outcome,a.error_code,e.finished_at FROM external_execution e JOIN task_attempt a ON a.task_id=e.task_id AND a.attempt=e.task_attempt WHERE e.id=? AND a.task_id=? AND a.attempt=? FOR UPDATE",
       (r,n)->r.getString("owner")!=null&&r.getObject("ended_at")!=null&&r.getObject("finished_at")!=null
         &&result.state().equals(r.getString("outcome"))&&Objects.equals(result.errorCode(),r.getString("error_code")),
       oldExecution.id(),oldTask.id(),oldTask.attempt());
   if(attempts.size()!=1||!attempts.getFirst())return ObservationResult.STALE_TOKEN;
   if(!preparedPolicy(oldTask.spec()).verifyReplay(scope.prepared(),context,prepared,result))return ObservationResult.STALE_TOKEN;
   context.require();return result.state().equals("STALE")?ObservationResult.RECORDED_FENCED:ObservationResult.APPLIED;
  }
+ private static final class PublicationReplacementContext implements PublicationReplacementPolicy.ReplacementContext {
+  final JdbcExternalOperations issuer;final Object holder;final TaskDomainPolicy.PreparedDomain domain;final PublicationReplacementPolicy.PreparedPublicationReplacement carrier;final PublicationReplacementPolicy policy;boolean closed;
+  private PublicationReplacementContext(JdbcExternalOperations issuer,TaskDomainPolicy.PreparedDomain domain,PublicationReplacementPolicy.PreparedPublicationReplacement carrier,PublicationReplacementPolicy policy){this.issuer=issuer;this.domain=domain;this.carrier=carrier;this.policy=policy;holder=org.springframework.transaction.support.TransactionSynchronizationManager.getResource(issuer.q.source);}
+  void require(javax.sql.DataSource source,TaskDomainPolicy.PreparedDomain domain,PublicationReplacementPolicy.PreparedPublicationReplacement carrier){if(closed||issuer.q.source!=source||this.domain!=domain||this.carrier!=carrier||holder==null||holder!=org.springframework.transaction.support.TransactionSynchronizationManager.getResource(source)||!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()||issuer.q.policy(carrier.spec())!=policy)throw new IllegalArgumentException("Foreign or expired replacement context");}
+ }
+ static void requirePublicationReplacement(javax.sql.DataSource source,TaskDomainPolicy.PreparedDomain domain,PublicationReplacementPolicy.ReplacementContext context,PublicationReplacementPolicy.PreparedPublicationReplacement carrier){if(!(context instanceof PublicationReplacementContext actual))throw new IllegalArgumentException("Genuine engine replacement context required");actual.require(source,domain,carrier);}
+ /** One atomic acceptance only. No marker or send authority is returned by this method. */
+ public PublicationReplacementPolicy.ReplacementAccepted replaceZeroEffectPublication(PublicationReplacementPolicy.PreparedPublicationReplacement carrier){return q.work(()->{
+  var next=carrier.spec();if(!next.type().equals("KNOWLEDGE_PUBLISH_V1")||!(q.policy(next) instanceof PublicationReplacementPolicy policy))throw new IllegalArgumentException("Fixed publication replacement required");var binding=policy.requireReplacement(carrier);if(!binding.next().equals(next)||!binding.specs().contains(next)||binding.oldTasks().containsKey(binding.taskId()))throw new IllegalArgumentException("Invalid full replacement declaration");
+  var scope=q.prepareDomain(binding.specs(),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,binding.prepared()).orElseThrow();var locked=q.lockDomain(scope,next,false).orElseThrow();if(!q.current(locked,next))throw new IllegalStateException("Replacement current fence denied");
+  var ids=new TreeSet<>(binding.oldTasks().keySet());ids.add(binding.taskId());for(String id:ids){if(id.equals(binding.taskId()))q.insertNew(next,id);else{var actual=q.row(id,true,false).orElseThrow();if(!actual.spec().equals(binding.oldTasks().get(id)))throw new IllegalStateException("Replacement task inventory changed");}}
+  var context=new PublicationReplacementContext(this,binding.prepared(),carrier,policy);try{policy.lockReplacementRemainder(binding.prepared(),context,carrier);}finally{context.closed=true;}
+  var request=binding.request();if(!request.operationKind().equals("KNOWLEDGE_PUBLISH")||!request.logicalRequestHash().equals(next.inputFingerprint()))throw new IllegalArgumentException("Replacement request mismatch");
+  if(binding.operationId()==null){if(binding.executionId()!=null||binding.expectedExecutionNo()!=0)throw new IllegalArgumentException("New key has no premature operation");return new PublicationReplacementPolicy.ReplacementAccepted(binding.taskId(),null,null,null);}
+  if(binding.expectedExecutionNo()<1||binding.expectedExecutionNo()>=Long.MAX_VALUE-1||binding.executionId()==null)throw new IllegalArgumentException("Invalid next execution");long number=binding.expectedExecutionNo()+1;
+  if(j.update("UPDATE external_operation SET request_hash=?,state='RETRY_QUEUED',current_execution_no=?,reconcile_epoch=reconcile_epoch+1,reconcile_owner=NULL,reconcile_expires_at=NULL,last_error=NULL WHERE id=? AND operation_kind='KNOWLEDGE_PUBLISH' AND business_key=? AND subject_kind='MEETING' AND subject_id=? AND request_hash=? AND current_execution_no=? AND state='FAILED' AND reconcile_epoch<9223372036854775806",request.logicalRequestHash(),number,binding.operationId(),request.businessKey(),next.subject().id(),binding.previousLogicalHash(),binding.expectedExecutionNo())!=1)throw new IllegalStateException("Replacement operation CAS failed");
+  if(j.update("INSERT INTO external_execution(id,operation_id,execution_no,task_id,state,request_hash,request_manifest,created_at) VALUES (?,?,?,?,'PREPARED',?,?,UTC_TIMESTAMP(6))",binding.executionId(),binding.operationId(),number,binding.taskId(),request.executionRequestHash(),request.requestManifest())!=1)throw new IllegalStateException("Replacement execution insert failed");
+  return new PublicationReplacementPolicy.ReplacementAccepted(binding.taskId(),binding.operationId(),binding.executionId(),number);
+ });}
  @Override public String retryAfterZeroEffect(String id,long expected,TaskSpec next,ExternalRequest request){return q.work(()->{
   // Discover the COMPLETE immutable old/new spec set before identities, subject, roots or tasks.
   var seen=operation(id,false,false).orElseThrow();
   var ex=execution(id,expected,false,false).orElseThrow();var old=q.row(ex.task(),false,false).orElseThrow();
   var scope=q.prepareDomain(List.of(next,old.spec()),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,null)
     .orElseThrow(DomainPreparationUnavailableException::new);
   q.lockDomain(scope,next,false).orElseThrow();q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
   // A locking read is current even when the preliminary discovery established an RR snapshot.
   var replay=j.query("SELECT * FROM durable_task WHERE producer_key=? FOR UPDATE",JdbcTaskQueue::read,next.producerKey());
   if(!replay.isEmpty()){
    var task=replay.getFirst();if(!task.spec().equals(next))throw new IllegalArgumentException("Retry producer conflicts with immutable spec");
    q.lockDomain(scope,next,false).orElseThrow();q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
    q.row(task.id(),true,false).orElseThrow();var o=operation(id,true,false).orElseThrow();
    var e=execution(id,expected+1,true,false).orElseThrow(()->new IllegalArgumentException("Retry producer belongs to another execution"));
    if(!e.task().equals(task.id())||!e.hash().equals(request.executionRequestHash())||!e.manifest().equals(request.requestManifest())||!o.subject().equals(next.subject())||!o.kind().equals(request.operationKind())||!o.key().equals(request.businessKey())||!o.hash().equals(request.logicalRequestHash()))throw new IllegalArgumentException("Retry replay conflicts with original acceptance");

Changed members:
[
  {
    "path": "backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java",
    "beforeBytes": 40419,
    "afterBytes": 45568
  },
  {
    "path": "backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java",
    "beforeBytes": 3909,
    "afterBytes": 4700
  },
  {
    "path": "backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java",
    "beforeBytes": 22099,
    "afterBytes": 30812
  },
  {
    "path": "backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java",
    "beforeBytes": 14143,
    "afterBytes": 14213
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/ConfiguredPublication.java",
    "beforeBytes": 0,
    "afterBytes": 1905
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/ControlledKnowledgeClient.java",
    "beforeBytes": 0,
    "afterBytes": 14825
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/JdbcFinalConfirmations.java",
    "beforeBytes": 0,
    "afterBytes": 13036
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/JdbcMinutesCommandReceipts.java",
    "beforeBytes": 7670,
    "afterBytes": 7718
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/JdbcMinutesSavedQueries.java",
    "beforeBytes": 24684,
    "afterBytes": 26486
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/JdbcPublicationLocalRecovery.java",
    "beforeBytes": 0,
    "afterBytes": 13502
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/JdbcPublicationReplacement.java",
    "beforeBytes": 0,
    "afterBytes": 20683
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/JdbcPublicationService.java",
    "beforeBytes": 0,
    "afterBytes": 34949
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java",
    "beforeBytes": 62567,
    "afterBytes": 62993
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/MinutesEventReferences.java",
    "beforeBytes": 7227,
    "afterBytes": 9459
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/MinutesGraphCapture.java",
    "beforeBytes": 10352,
    "afterBytes": 12313
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/MinutesGraphDiscovery.java",
    "beforeBytes": 24216,
    "afterBytes": 25274
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/PublicationGraph.java",
    "beforeBytes": 0,
    "afterBytes": 5967
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/PublicationLocalDecision.java",
    "beforeBytes": 0,
    "afterBytes": 7444
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/PublicationRecordedLookup.java",
    "beforeBytes": 0,
    "afterBytes": 13130
  },
  {
    "path": "backend/adapters/src/main/java/ama/transcript/PublicationTaskDomainPolicy.java",
    "beforeBytes": 0,
    "afterBytes": 25584
  },
  {
    "path": "backend/adapters/src/test/java/ama/transcript/Task11KnowledgeCanonicalTest.java",
    "beforeBytes": 0,
    "afterBytes": 3499
  },
  {
    "path": "backend/adapters/src/test/java/ama/transcript/Task11KnowledgeProofTest.java",
    "beforeBytes": 0,
    "afterBytes": 1955
  },
  {
    "path": "backend/api/src/main/java/ama/api/FinalConfirmationController.java",
    "beforeBytes": 0,
    "afterBytes": 4665
  },
  {
    "path": "backend/api/src/main/java/ama/api/KnowledgeConfiguration.java",
    "beforeBytes": 0,
    "afterBytes": 723
  },
  {
    "path": "backend/api/src/main/java/ama/api/MinutesConfiguration.java",
    "beforeBytes": 3574,
    "afterBytes": 4223
  },
  {
    "path": "backend/api/src/main/java/ama/api/PublicationController.java",
    "beforeBytes": 0,
    "afterBytes": 8447
  },
  {
    "path": "backend/api/src/main/java/ama/api/PublicationErrors.java",
    "beforeBytes": 0,
    "afterBytes": 1620
  },
  {
    "path": "backend/api/src/test/java/ama/api/Task11PublicationErrorsTest.java",
    "beforeBytes": 0,
    "afterBytes": 2249
  },
  {
    "path": "backend/contracts/src/main/java/ama/contract/MinutesContracts.java",
    "beforeBytes": 37192,
    "afterBytes": 38462
  },
  {
    "path": "backend/core/src/main/java/ama/knowledge/ControlledKnowledgeDocumentRenderer.java",
    "beforeBytes": 0,
    "afterBytes": 5186
  },
  {
    "path": "backend/core/src/main/java/ama/minutes/contract/GenerationArchiveRefs.java",
    "beforeBytes": 5637,
    "afterBytes": 6314
  },
  {
    "path": "backend/core/src/main/java/ama/minutes/contract/ResultHashProfiles.java",
    "beforeBytes": 16170,
    "afterBytes": 18465
  },
  {
    "path": "backend/core/src/main/java/ama/task/ExternalOperationPort.java",
    "beforeBytes": 2771,
    "afterBytes": 3012
  },
  {
    "path": "backend/core/src/main/java/ama/task/PublicationLocalRecoveryUnavailable.java",
    "beforeBytes": 0,
    "afterBytes": 267
  },
  {
    "path": "backend/core/src/main/java/ama/task/PublicationLocalTerminationPolicy.java",
    "beforeBytes": 0,
    "afterBytes": 792
  },
  {
    "path": "backend/core/src/main/java/ama/task/PublicationReplacementPolicy.java",
    "beforeBytes": 0,
    "afterBytes": 1055
  },
  {
    "path": "backend/core/src/test/java/ama/minutes/contract/Task11FinalDraftTest.java",
    "beforeBytes": 0,
    "afterBytes": 1567
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/api/Task11RuntimeProbe.java",
    "beforeBytes": 0,
    "afterBytes": 448
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/publication/Task11BootstrapIT.java",
    "beforeBytes": 0,
    "afterBytes": 4828
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/publication/Task11Source.java",
    "beforeBytes": 0,
    "afterBytes": 450
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11FinalPublicationIT.java",
    "beforeBytes": 0,
    "afterBytes": 13828
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11KnowledgeFixture.java",
    "beforeBytes": 0,
    "afterBytes": 9364
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11LocalMaintenanceIT.java",
    "beforeBytes": 0,
    "afterBytes": 1922
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoveryIT.java",
    "beforeBytes": 0,
    "afterBytes": 3890
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoveryResumeIT.java",
    "beforeBytes": 0,
    "afterBytes": 5136
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoverySecurityIT.java",
    "beforeBytes": 0,
    "afterBytes": 3330
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11LocalTerminalIT.java",
    "beforeBytes": 0,
    "afterBytes": 4822
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11LostResponseIT.java",
    "beforeBytes": 0,
    "afterBytes": 6038
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11PublicationOriginalLookupIT.java",
    "beforeBytes": 0,
    "afterBytes": 2277
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11PublicationResumeIT.java",
    "beforeBytes": 0,
    "afterBytes": 6673
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11PublicationWaiter.java",
    "beforeBytes": 0,
    "afterBytes": 3222
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11PublishedReceiptIT.java",
    "beforeBytes": 0,
    "afterBytes": 2393
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11RejectedReceiptIT.java",
    "beforeBytes": 0,
    "afterBytes": 2451
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11ReplacementBoundaryIT.java",
    "beforeBytes": 0,
    "afterBytes": 3485
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11ReplacementIT.java",
    "beforeBytes": 0,
    "afterBytes": 5711
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11ReplacementRollbackIT.java",
    "beforeBytes": 0,
    "afterBytes": 5111
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11ResponseLossFixture.java",
    "beforeBytes": 0,
    "afterBytes": 3644
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/storage/Task11ZeroEffectIT.java",
    "beforeBytes": 0,
    "afterBytes": 3033
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/transcript/Task11LocalContextProbe.java",
    "beforeBytes": 0,
    "afterBytes": 995
  },
  {
    "path": "backend/integration-tests/src/test/java/ama/transcript/Task11ReplacementBoundaryProbe.java",
    "beforeBytes": 0,
    "afterBytes": 2058
  },
  {
    "path": "backend/worker/src/main/java/ama/worker/KnowledgePublicationHandler.java",
    "beforeBytes": 0,
    "afterBytes": 1078
  },
  {
    "path": "backend/worker/src/main/java/ama/worker/KnowledgeReconciliationHandler.java",
    "beforeBytes": 0,
    "afterBytes": 1163
  },
  {
    "path": "backend/worker/src/main/java/ama/worker/ReconciliationLoop.java",
    "beforeBytes": 6525,
    "afterBytes": 7376
  },
  {
    "path": "backend/worker/src/main/java/ama/worker/TranscriptWorkerMain.java",
    "beforeBytes": 2701,
    "afterBytes": 2845
  },
  {
    "path": "backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java",
    "beforeBytes": 5615,
    "afterBytes": 9289
  },
  {
    "path": "tools/providers/controlled_knowledge.py",
    "beforeBytes": 0,
    "afterBytes": 27409
  },
  {
    "path": "tools/providers/knowledge_response_loss.py",
    "beforeBytes": 0,
    "afterBytes": 4079
  },
  {
    "path": "tools/providers/test_knowledge_binding.py",
    "beforeBytes": 0,
    "afterBytes": 2533
  },
  {
    "path": "tools/providers/test_knowledge_transition.py",
    "beforeBytes": 0,
    "afterBytes": 3970
  },
  {
    "path": "tools/task11_window.py",
    "beforeBytes": 0,
    "afterBytes": 8188
  }
]
Unchanged context members:
[
  "backend/adapters/src/main/java/ama/db/TransactionRunner.java",
  "backend/adapters/src/main/java/ama/task/JdbcOutbox.java",
  "backend/adapters/src/main/java/ama/task/TaskTransactionContext.java",
  "backend/core/src/main/java/ama/task/TaskDomainPolicy.java"
]

## backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java
--- a/backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java
+++ b/backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java
@@ -24,39 +24,43 @@
             throw new IllegalArgumentException("Preparation purpose or complete specs mismatch");
     }
     public Object value(PreparedDomain prepared) { return held(prepared).value(); }
     public boolean permitsEffects(PreparedDomain prepared) { return held(prepared).effectsAllowed(); }
     /** Validated immutable execution facts; this view is data, not a transferable capability. */
     public record ExternalFacts(String taskId, TaskSpec spec, String operationId, String executionId,
             long executionNo, String requestHash, String requestManifest) {}
     public ExternalFacts requireExternal(PreparedDomain prepared, ama.task.ExternalOperationPort.ExecutionContext execution) {
         held(prepared);
         return JdbcExternalOperations.requireContext(source, prepared, execution);
     }
     /** Actual context resolves its own held preparation; issuer validation still applies. */
     public ExternalFacts requireExternal(ama.task.ExternalOperationPort.ExecutionContext execution) {
         var prepared=JdbcExternalOperations.contextPreparation(source,execution);held(prepared);return requireExternal(prepared,execution);
     }
+    public record LocalTerminalFacts(String taskId,TaskSpec spec,String previousState,long attempt,String previousOwner,java.time.Instant previousExpiry,String resultingState,String reason,boolean attemptAlreadyEnded) {}
+    public LocalTerminalFacts requireLocalTerminal(PreparedDomain prepared,PublicationLocalTerminationPolicy.LocalTerminalContext context,PublicationLocalTerminationPolicy.Phase phase){held(prepared);return JdbcTaskQueue.requireLocalContext(source,prepared,context,phase);}
     /** JDBC queue consumption independently enforces its own source/actual-holder boundary. */
     static void requireTransaction(DataSource expected, PreparedDomain prepared) {
         if(!(prepared instanceof Held held)||held.source()!=expected
                 ||!TransactionSynchronizationManager.isActualTransactionActive()
                 ||held.transaction()!=TransactionSynchronizationManager.getResource(expected))
             throw new IllegalArgumentException("Preparation belongs to another queue DataSource transaction");
     }
     private Held held(PreparedDomain prepared) {
         if(!(prepared instanceof Held held) || held.issuer()!=this || held.source()!=source
                 || held.transaction()!=transaction())
             throw new IllegalArgumentException("Expired or foreign domain preparation");
         return held;
     }
     private static Set<TaskSpec> specs(List<TaskSpec> specs) {
         if(specs==null || specs.isEmpty())throw new IllegalArgumentException("Complete task specs required");
         return Set.copyOf(specs);
     }
     private Object transaction() {
         if(!TransactionSynchronizationManager.isActualTransactionActive()
                 || !TransactionSynchronizationManager.hasResource(source))
             throw new IllegalStateException("Preparation requires its actual DataSource transaction");
         return TransactionSynchronizationManager.getResource(source);
     }
+ public void requirePublicationReplacement(PreparedDomain prepared,PublicationReplacementPolicy.ReplacementContext context,PublicationReplacementPolicy.PreparedPublicationReplacement carrier){held(prepared);JdbcExternalOperations.requirePublicationReplacement(source,prepared,context,carrier);}
+
 }

## backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java
--- a/backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java
+++ b/backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java
@@ -98,39 +98,39 @@
  @Override public Optional<TaskLease> claim(String pool,String worker){
   independent();protocolCode(pool);protocolCode(worker);
   // Four bounded pages prevent one locked head batch from blocking all later candidates.
   for(int page=0;page<4;page++){
    var candidates=new ArrayList<Row>();
    candidates.addAll(jdbc.query("SELECT * FROM durable_task WHERE pool=? AND state='READY' AND available_at<=UTC_TIMESTAMP(6) ORDER BY available_at,id LIMIT 64 OFFSET ?",JdbcTaskQueue::read,pool,page*64));
    candidates.addAll(jdbc.query("SELECT * FROM durable_task WHERE pool=? AND state='RUNNING' AND expires_at<=UTC_TIMESTAMP(6) ORDER BY expires_at,id LIMIT 64 OFFSET ?",JdbcTaskQueue::read,pool,page*64));
    if(candidates.isEmpty())break;
    Instant now=now();candidates.sort(Comparator.<Row>comparingLong(r->-(r.spec().priority()+Math.max(0,Duration.between(r.available(),now).getSeconds())/5)).thenComparing(Row::id));
    for(var candidate:candidates){var lease=work(()->claimOne(candidate,worker));if(lease.isPresent())return lease;}
   }
   return Optional.empty();
  }
  private Optional<TaskLease> claimOne(Row candidate,String worker){
   if(!policies.containsKey(candidate.spec().type()))return Optional.empty();
-  var domain=lockDomain(candidate.spec(),true);if(domain.isEmpty())return Optional.empty();
+  var preparation=prepareDomain(List.of(candidate.spec()),TaskDomainPolicy.Acquisition.TASK_MUTATION,true,null);if(preparation.isEmpty())return Optional.empty();var scope=preparation.orElseThrow();var domain=lockDomain(scope,candidate.spec(),true);if(domain.isEmpty())return Optional.empty();
   var locked=row(candidate.id(),true,true);if(locked.isEmpty())return Optional.empty();var r=locked.orElseThrow();Instant now=now();
   if(!r.spec().equals(candidate.spec()))return Optional.empty();
   if(!(r.state().equals("READY")&&!r.available().isAfter(now)||r.state().equals("RUNNING")&&!r.expiry().isAfter(now)))return Optional.empty();
   if(r.state().equals("RUNNING")&&unfinishedAttempt(r).isEmpty())return Optional.empty();
-  if(!current(domain.orElseThrow(),r.spec())){finish(r,domain.orElseThrow().deleted()||domain.orElseThrow().cancelEpoch()!=r.spec().cancelEpoch()?"CANCELLED":"STALE","SUBJECT_CHANGED",null);return Optional.empty();}
+  if(!current(domain.orElseThrow(),r.spec())){finishLocal(scope,r,domain.orElseThrow().deleted()||domain.orElseThrow().cancelEpoch()!=r.spec().cancelEpoch()?"CANCELLED":"STALE","SUBJECT_CHANGED",null,true,false);return Optional.empty();}
   policy(r.spec()).validateInput(r.spec());
   if(r.state().equals("RUNNING"))endAttempt(r,"EXPIRED","LEASE_EXPIRED");
-  if(r.attempt()>=r.spec().maxAttempts()){terminateUnmarkedExecution(r);finish(r,"FAILED","ATTEMPT_LIMIT",null,false);return Optional.empty();}
+  if(r.attempt()>=r.spec().maxAttempts()){finishLocal(scope,r,"FAILED","ATTEMPT_LIMIT",null,false,true);return Optional.empty();}
   now=now();long attempt=r.attempt()+1;Instant expiry=now.plusSeconds(20);
   jdbc.update("UPDATE durable_task SET state='RUNNING',attempt=?,lease_owner=?,expires_at=? WHERE id=?",attempt,worker,sqlTime(expiry),r.id());
   jdbc.update("INSERT INTO task_attempt(task_id,attempt,owner,started_at) VALUES (?,?,?,?)",r.id(),attempt,worker,sqlTime(now));
   return Optional.of(new TaskLease(r.id(),attempt,worker,expiry,r.spec()));
  }
  record LockedAttempt(long attempt,String owner) {}
  record ValidatedLease(DomainScope domain,Row task,LockedAttempt attempt) {}
  private Optional<LockedAttempt> unfinishedAttempt(Row task){
   var rows=jdbc.query("SELECT attempt,owner,ended_at FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE",(r,n)->r.getObject("ended_at")==null&&Objects.equals(task.owner(),r.getString("owner"))?new LockedAttempt(r.getLong("attempt"),r.getString("owner")):null,task.id(),task.attempt());
   return rows.size()==1?Optional.ofNullable(rows.getFirst()):Optional.empty();
  }
  Optional<ValidatedLease> validatedLease(TaskLease lease){
   var prepared=prepareDomain(List.of(lease.spec()),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,null);
   if(prepared.isEmpty())return Optional.empty();var scope=prepared.orElseThrow();
   var domain=lockDomain(scope,lease.spec(),false);if(domain.isEmpty())return Optional.empty();
@@ -154,62 +154,99 @@
      ||valid.domain.locked==null||!valid.task.spec().equals(lease.spec())||!valid.task.id().equals(lease.taskId())||valid.attempt.attempt()!=lease.attempt()||!valid.attempt.owner().equals(lease.leaseOwner())||phase==CompletionPhase.CLOSED)
     throw new IllegalArgumentException("Foreign or expired after-task scope");
    JdbcTaskPreparations.requireTransaction(queue.source,valid.domain.prepared());policy.requirePrepared(valid.domain.specs(),valid.domain.purpose(),valid.domain.prepared());
   }
   TaskDomainPolicy.PreparedDomain preparedFor(JdbcTaskQueue expected,ConfinedTaskCompletionPolicy registered,List<TaskSpec> specs){
    require(expected,registered);if(phase!=CompletionPhase.BINDING||!valid.domain.specs().equals(specs))throw new IllegalStateException("After-task binding phase required");return valid.domain.prepared();
   }
   void requireInvocation(JdbcTaskQueue expected,ConfinedTaskCompletionPolicy registered,CompletionInvocation exact,Supplier<TaskResult> payload,TaskLease wanted,CompletionPhase expectedPhase){
    require(expected,registered);if(invocation!=exact||carrier!=payload||!lease.equals(wanted)||phase!=expectedPhase)throw new IllegalStateException("After-task invocation mismatch");
   }
   void requireResult(TaskResult value){if(phase!=CompletionPhase.VALIDATING||result!=value)throw new IllegalStateException("After-task result mismatch");}
  }
  @Override public boolean renew(TaskLease lease){return work(()->{if(validLease(lease).isEmpty())return false;jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND WHERE id=?",lease.taskId());return true;});}
  @Override public boolean complete(TaskLease lease,TaskResult result){return completeWithEffects(lease,result,()->{});}
  @Override public boolean completeWithEffects(TaskLease lease,TaskResult result,Runnable effects){return work(()->{
-  var valid=validLease(lease);if(valid.isEmpty())return false;var r=valid.orElseThrow();if(policy(r.spec()) instanceof ConfinedTaskCompletionPolicy)throw new IllegalStateException("Confined carrier required");policy(r.spec()).validateOutput(r.spec(),result);
+  var valid=validatedLease(lease);if(valid.isEmpty())return false;var validated=valid.orElseThrow();var r=validated.task();if(policy(r.spec()) instanceof ConfinedTaskCompletionPolicy)throw new IllegalStateException("Confined carrier required");policy(r.spec()).validateOutput(r.spec(),result);
   effects.run();
-  completeValidated(r,result);return true;
+  completeValidated(validated,result);return true;
  });}
  @Override public boolean completeWithEffects(TaskLease lease,Supplier<TaskResult> effects){
   Objects.requireNonNull(lease,"lease");Objects.requireNonNull(effects,"effects");
   return work(()->{
    var valid=validatedLease(lease);if(valid.isEmpty())return false;var validated=valid.orElseThrow();var r=validated.task();
    if(policy(r.spec()) instanceof ConfinedTaskCompletionPolicy confined){
     var envelope=effects instanceof PreparedCompletionEnvelope e?e:null;
     var payload=envelope==null?effects:Objects.requireNonNull(envelope.payload(),"Completion payload");
     if(payload instanceof PreparedCompletionEnvelope||payload==envelope)throw new IllegalArgumentException("Recursive completion envelope");
     var scope=new AfterTask(this,validated,lease,confined,payload,envelope);
     try {
      scope.invocation=Objects.requireNonNull(confined.bindCompletion(scope,payload));scope.phase=CompletionPhase.BOUND;
      if(envelope!=null)envelope.admit();
      scope.phase=CompletionPhase.WRITING;scope.result=Objects.requireNonNull(scope.invocation.write(),"Prepared completion result");scope.phase=CompletionPhase.WRITTEN;
      scope.phase=CompletionPhase.VALIDATING;scope.invocation.validateActual(scope.result);scope.phase=CompletionPhase.VALIDATED;
-     completeValidated(r,scope.result);scope.phase=CompletionPhase.SETTLED;return true;
+     completeValidated(validated,scope.result);scope.phase=CompletionPhase.SETTLED;return true;
     } finally {scope.phase=CompletionPhase.CLOSED;}
    }
    if(effects instanceof PreparedCompletionEnvelope)throw new IllegalArgumentException("Confined completion not installed");
    var result=Objects.requireNonNull(effects.get(),"Prepared completion result");
    policy(r.spec()).validateOutput(r.spec(),result);
-   completeValidated(r,result);return true;
+   completeValidated(validated,result);return true;
   });
  }
- private void completeValidated(Row r,TaskResult result){
+ private void completeValidated(ValidatedLease validated,TaskResult result){
+  var r=validated.task();
   if(result.outcome()==Outcome.RETRYABLE && r.attempt()<r.spec().maxAttempts()){
    long delay=Math.min(40,5L << Math.min(3,r.attempt()-1));long millis=ThreadLocalRandom.current().nextLong(1001);
    jdbc.update("UPDATE durable_task SET state='READY',lease_owner=NULL,expires_at=NULL,available_at=?,last_error=? WHERE id=?",sqlTime(now().plusMillis(delay*1000+millis)),result.errorCode(),r.id());endAttempt(r,"RETRYABLE",result.errorCode());event("TASK_RETRY",r);
-  }else finish(r,result.outcome()==Outcome.RETRYABLE?"FAILED":result.outcome().name(),result.errorCode(),result.output());
+  }else finishLocal(validated.domain(),r,result.outcome()==Outcome.RETRYABLE?"FAILED":result.outcome().name(),result.errorCode(),result.output(),true,false);
+ }
+ private static final class LocalTerminal implements PublicationLocalTerminationPolicy.LocalTerminalContext {
+  final JdbcTaskQueue queue;final DomainScope scope;final Row row;final Object holder;final String state,reason;final boolean ended;PublicationLocalTerminationPolicy.Phase phase=PublicationLocalTerminationPolicy.Phase.VALIDATING;boolean closed;
+  private LocalTerminal(JdbcTaskQueue queue,DomainScope scope,Row row,String state,String reason,boolean ended){this.queue=queue;this.scope=scope;this.row=row;this.state=state;this.reason=reason;this.ended=ended;holder=TransactionSynchronizationManager.getResource(queue.source);}
+  void require(DataSource source,TaskDomainPolicy.PreparedDomain prepared,PublicationLocalTerminationPolicy.Phase wanted){
+   if(closed||phase!=wanted||queue.source!=source||holder==null||holder!=TransactionSynchronizationManager.getResource(source)||!TransactionSynchronizationManager.isActualTransactionActive()||scope.prepared()!=prepared||scope.locked==null||scope.purpose()!=TaskDomainPolicy.Acquisition.TASK_MUTATION||scope.policy()!=queue.policy(row.spec())||!(scope.policy() instanceof PublicationLocalTerminationPolicy)||!row.spec().type().equals("KNOWLEDGE_PUBLISH_V1")||!row.spec().input().kind().equals("PUBLICATION")||!scope.specs().contains(row.spec()))throw new IllegalArgumentException("Foreign or expired local termination context");
+   JdbcTaskPreparations.requireTransaction(source,prepared);scope.policy().requirePrepared(scope.specs(),scope.purpose(),prepared);
+  }
+ }
+ static JdbcTaskPreparations.LocalTerminalFacts requireLocalContext(DataSource source,TaskDomainPolicy.PreparedDomain prepared,PublicationLocalTerminationPolicy.LocalTerminalContext value,PublicationLocalTerminationPolicy.Phase phase){
+  if(!(value instanceof LocalTerminal c))throw new IllegalArgumentException("Genuine queue local termination context required");c.require(source,prepared,phase);return new JdbcTaskPreparations.LocalTerminalFacts(c.row.id(),c.row.spec(),c.row.state(),c.row.attempt(),c.row.owner(),c.row.expiry(),c.state,c.reason,c.ended);
+ }
+ private void finishLocal(DomainScope scope,Row row,String state,String code,TypedRef output,boolean endCurrent,boolean terminatePrepared){finishLocal(scope,row,state,code,output,endCurrent,terminatePrepared,false,false);}
+ private void finishLocal(DomainScope scope,Row row,String state,String code,TypedRef output,boolean endCurrent,boolean terminatePrepared,boolean requireEligible,boolean preserveTerminal){
+  if(!(scope.policy() instanceof PublicationLocalTerminationPolicy policy)||!row.spec().type().equals("KNOWLEDGE_PUBLISH_V1")||!Set.of("FAILED","CANCELLED","STALE").contains(state)||output!=null){if(requireEligible)throw new IllegalArgumentException("Fixed publication recovery required");if(terminatePrepared)terminateUnmarkedExecution(row);finish(row,state,code,output,endCurrent);return;}
+  var context=new LocalTerminal(this,scope,row,state,code,!endCurrent&&row.state().equals("RUNNING"));
+  try{context.require(source,scope.prepared(),PublicationLocalTerminationPolicy.Phase.VALIDATING);boolean eligible=policy.validateLocalTerminal(scope.prepared(),context);if(requireEligible&&!eligible)throw new PublicationLocalRecoveryUnavailable("Publication local recovery ineligible");
+   if(eligible)terminateLocalPrepared(context);else if(terminatePrepared)terminateUnmarkedExecution(row);
+   if(!preserveTerminal){boolean end=endCurrent;if(eligible&&row.state().equals("RECONCILING")){endLocalReconciliationAttempt(context);end=false;}finish(row,state,code,output,end);}
+   if(eligible){context.phase=PublicationLocalTerminationPolicy.Phase.SEALING;policy.sealLocalTerminal(scope.prepared(),context);context.require(source,scope.prepared(),PublicationLocalTerminationPolicy.Phase.SEALING);}
+  }finally{context.closed=true;}
+ }
+ /** Private purpose carrier only; a lease, ordinary completion token or caller callback is insufficient. */
+ public String recoverPublicationLocal(PublicationLocalTerminationPolicy.PreparedLocalRecovery recovery){Objects.requireNonNull(recovery);return work(()->{
+  var spec=recovery.spec();if(!spec.type().equals("KNOWLEDGE_PUBLISH_V1")||!(policy(spec) instanceof PublicationLocalTerminationPolicy p))throw new IllegalArgumentException("Fixed publication recovery required");var binding=p.requireLocalRecovery(recovery);if(!binding.spec().equals(spec))throw new IllegalArgumentException("Recovery spec mismatch");var prepared=prepareDomain(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,binding.prepared()).orElseThrow();lockDomain(prepared,spec,false).orElseThrow();var row=row(binding.taskId(),true,false).orElseThrow();if(!row.spec().equals(spec))throw new IllegalArgumentException("Actual recovery task mismatch");policy(spec).validateInput(spec);
+  boolean terminal=Set.of("FAILED","CANCELLED","STALE").contains(row.state());if(!terminal&&!Set.of("READY","RUNNING","RECONCILING").contains(row.state()))throw new PublicationLocalRecoveryUnavailable("Task cannot recover locally");if((row.state().equals("RUNNING")||row.state().equals("RECONCILING"))&&row.owner()!=null&&(row.expiry()==null||row.expiry().isAfter(now())))throw new PublicationLocalRecoveryUnavailable("Live ordinary owner excludes recovery");
+  finishLocal(prepared,row,terminal?row.state():"FAILED",terminal?"TERMINAL_UNMARKED_FAILURE":"EXPIRED_UNMARKED_ABANDONED",null,true,false,true,terminal);return row.id();
+ });}
+ private void endLocalReconciliationAttempt(LocalTerminal context){context.require(source,context.scope.prepared(),PublicationLocalTerminationPolicy.Phase.VALIDATING);var r=context.row;if(!r.state().equals("RECONCILING"))throw new IllegalStateException("Actual reconciliation state required");var unfinished=jdbc.queryForList("SELECT attempt,owner FROM task_attempt WHERE task_id=? AND ended_at IS NULL ORDER BY attempt FOR UPDATE",r.id());if(unfinished.isEmpty())return;if(unfinished.size()!=1||((Number)unfinished.getFirst().get("attempt")).longValue()!=r.attempt()||!Objects.equals(unfinished.getFirst().get("owner"),r.owner()))throw new IllegalStateException("Exact actual unfinished attempt required");if(jdbc.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='FAILED',error_code='LOCAL_NEVER_MARKED' WHERE task_id=? AND attempt=? AND owner=? AND ended_at IS NULL",r.id(),r.attempt(),r.owner())!=1)throw new IllegalStateException("Local attempt must end exactly once");}
+ private void terminateLocalPrepared(LocalTerminal context){
+  context.require(source,context.scope.prepared(),PublicationLocalTerminationPolicy.Phase.VALIDATING);var task=context.row;
+  var rows=jdbc.queryForList("SELECT id,operation_id,execution_no,state,marked_at,task_attempt,no_effect_basis,no_effect_verified_at FROM external_execution WHERE task_id=? ORDER BY operation_id,execution_no FOR UPDATE",task.id());if(rows.isEmpty())return;if(rows.size()!=1)throw new IllegalStateException("Local closure requires exact execution set");var e=rows.getFirst();if(e.get("marked_at")!=null||e.get("task_attempt")!=null)throw new IllegalStateException("Committed marker excludes local closure");
+  if(e.get("state").equals("FAILED")&&"NOT_SENT".equals(e.get("no_effect_basis"))&&e.get("no_effect_verified_at")!=null)return;
+  var op=jdbc.queryForMap("SELECT state,current_execution_no FROM external_operation WHERE id=? FOR UPDATE",e.get("operation_id"));if(!e.get("state").equals("PREPARED")||!op.get("state").equals("RETRY_QUEUED")||((Number)op.get("current_execution_no")).longValue()!=((Number)e.get("execution_no")).longValue())throw new IllegalStateException("Exact unmarked prepared execution required");
+  if(jdbc.update("UPDATE external_execution SET state='FAILED',finished_at=UTC_TIMESTAMP(6),no_effect_basis='NOT_SENT',no_effect_evidence=?,no_effect_verified_at=UTC_TIMESTAMP(6) WHERE id=? AND state='PREPARED' AND marked_at IS NULL AND task_attempt IS NULL","publication-local:"+task.id(),e.get("id"))!=1)throw new IllegalStateException("Local prepared transition lost");
+  if(jdbc.update("UPDATE external_operation SET state='FAILED',last_error='LOCAL_NEVER_MARKED',reconcile_epoch=reconcile_epoch+1,reconcile_owner=NULL,reconcile_expires_at=NULL WHERE id=? AND state='RETRY_QUEUED' AND current_execution_no=?",e.get("operation_id"),e.get("execution_no"))!=1)throw new IllegalStateException("Local operation transition lost");
  }
  void endAttempt(Row r,String outcome,String code){
   if(r.attempt()>0&&"RUNNING".equals(r.state())){
    int changed=jdbc.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome=?,error_code=? WHERE task_id=? AND attempt=? AND owner=? AND ended_at IS NULL",outcome,code,r.id(),r.attempt(),r.owner());
    if(changed!=1)throw new IllegalStateException("Current attempt transition must affect exactly one row");
   }
  }
  void finish(Row r,String state,String code,TypedRef output){finish(r,state,code,output,true);}
  private void finish(Row r,String state,String code,TypedRef output,boolean endCurrent){
   jdbc.update("UPDATE durable_task SET state=?,lease_owner=NULL,expires_at=NULL,last_error=?,output_kind=?,output_id=? WHERE id=?",state,code,output==null?null:output.kind(),output==null?null:output.id(),r.id());
   if(endCurrent)endAttempt(r,state,code);event("TASK_"+state,r);
  }
  /** A PREPARED execution has never crossed the committed marker boundary. Task lock is already held. */
  private void terminateUnmarkedExecution(Row task){
   var candidates=jdbc.queryForList("SELECT operation_id,execution_no FROM external_execution WHERE task_id=? AND state='PREPARED'",task.id());

## backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java
--- a/backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java
+++ b/backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java
@@ -20,31 +20,31 @@
 import static ama.contract.MinutesContracts.*;
 import static ama.contract.MinutesContracts.FailureCode.*;
 
 /** Shared per-process installation: one existing transcript issuer, one fixed confined registration. */
 public final class TranscriptImportRuntime {
  private TranscriptImportRuntime() {}
  public record Settings(Supplier<ConfigArchiveSource.Snapshot> archives,ArchiveReadBudget archiveBudget,
                         TranscriptDocumentParser.ParseBudget aggregate,Duration batchDeadline) {
   public Settings {
    Objects.requireNonNull(archives);Objects.requireNonNull(archiveBudget);Objects.requireNonNull(aggregate);Objects.requireNonNull(batchDeadline);
    if(aggregate.maxSourceBytes()<=0||aggregate.maxUtf16CodeUnits()<=0||aggregate.maxSegments()<=0||batchDeadline.isZero()||batchDeadline.isNegative()||batchDeadline.compareTo(Duration.ofHours(1))>0)throw new IllegalArgumentException("Finite import bounds required");
   }
  }
  public static JdbcTaskQueue install(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,JdbcTranscriptAuthority.Components components) {
   components.authority().requireImportSource(source);
-  return JdbcTaskQueue.installConfined(source,fences,outbox,q->Map.of("TRANSCRIPT_IMPORT_V1",new ImportPolicy(q,components),"MINUTES_GENERATE_V1",components.authority().minutesTaskPolicy()));
+  return JdbcTaskQueue.installConfined(source,fences,outbox,q->Map.of("TRANSCRIPT_IMPORT_V1",new ImportPolicy(q,components),"MINUTES_GENERATE_V1",components.authority().minutesTaskPolicy(),"KNOWLEDGE_PUBLISH_V1",components.authority().publicationTaskPolicy()));
  }
  public static Supplier<TaskResult> prepare(JdbcTaskQueue queue,TaskLease lease,Settings settings,PreparationCancellation cancellation) throws Exception {
   if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Import preparation requires independent reads");
   if(!(queue.policy(lease.spec()) instanceof ImportPolicy policy)||policy.queue!=queue)throw new IllegalArgumentException("Production import not installed");
   return policy.prepareRaw(lease,settings,cancellation);
  }
  private record Raw(TaskLease lease,String id,String owner,String batch,Instant time,List<TranscriptDocumentParser.Segment> segments,TaskResult failure) {
   Raw {segments=List.copyOf(segments);}
  }
  private static final class ImportPolicy implements ConfinedTaskCompletionPolicy {
   final JdbcTaskQueue queue;final JdbcTranscriptAuthority.Components components;final TaskDomainPolicy domain;
   ImportPolicy(JdbcTaskQueue queue,JdbcTranscriptAuthority.Components components){this.queue=queue;this.components=components;domain=components.authority().taskPolicy();}
   public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){return domain.prepare(specs,purpose,skip);}
   public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){domain.requirePrepared(specs,purpose,held);}
   public boolean permitsDomainEffects(PreparedDomain held){return domain.permitsDomainEffects(held);}

## backend/adapters/src/main/java/ama/transcript/ConfiguredPublication.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/ConfiguredPublication.java
@@ -0,0 +1,8 @@
+package ama.transcript;
+import ama.config.*;import ama.config.file.*;import ama.contract.ConfigArchiveContracts.*;import java.nio.file.Path;import java.net.URI;import java.util.function.Supplier;import org.springframework.core.env.PropertyResolver;
+/** Explicit fixed controlled installation; no database effects or provider startup during configuration. */
+public record ConfiguredPublication(Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget bounds,Path applicationIdentities,URI origin,String mode,int capacity,int reconciliationCapacity) {
+ public ConfiguredPublication {if(capacity<1||capacity>64||reconciliationCapacity<1||reconciliationCapacity>64||!"ISOLATED_LOOPBACK_TEST".equals(mode))throw new IllegalArgumentException("Bounded explicit controlled publication installation required");}
+ public static ConfiguredPublication read(PropertyResolver env,ArchiveReadBudget read,ConfigArchiveIndex.IndexBudget index){if(!env.getRequiredProperty("publication.enabled",Boolean.class))throw new IllegalArgumentException("Publication installation required");String pin=env.getRequiredProperty("minutes.index-sha");Path archive=Path.of(env.getRequiredProperty("minutes.archive-root"));return new ConfiguredPublication(new ControlledMinutesArchiveAccess(archive,pin,index,read),pin,ConfiguredMinutes.acquisition(env),Path.of(env.getRequiredProperty("publication.client-identities")),URI.create(env.getRequiredProperty("publication.origin")),env.getRequiredProperty("publication.mode"),env.getRequiredProperty("publication.capacity",Integer.class),env.getRequiredProperty("publication.reconciliation-capacity",Integer.class));}
+ public ControlledKnowledgeClient client(){try{return new ControlledKnowledgeClient(applicationIdentities,origin,mode);}catch(Exception unavailable){throw new IllegalStateException("Controlled publication client unavailable",unavailable);}}
+}

## backend/adapters/src/main/java/ama/transcript/ControlledKnowledgeClient.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/ControlledKnowledgeClient.java
@@ -0,0 +1,71 @@
+package ama.transcript;
+
+import com.fasterxml.jackson.core.*;
+import com.fasterxml.jackson.databind.*;
+import ama.task.PreparationCancellation;
+import java.io.*;
+import java.net.*;
+import java.nio.charset.StandardCharsets;
+import java.nio.file.*;
+import java.security.*;
+import java.security.spec.*;
+import java.time.Instant;
+import java.util.*;
+import java.util.concurrent.*;
+
+/** One-shot pinned loopback-test transport. Signing is package-confined to the registered publication bridge. */
+public final class ControlledKnowledgeClient implements AutoCloseable {
+ static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(8388608).maxNumberLength(19).maxNameLength(128).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
+ private final java.util.concurrent.atomic.AtomicLong getCalls=new java.util.concurrent.atomic.AtomicLong(),postCalls=new java.util.concurrent.atomic.AtomicLong();
+ public Map<String,Long> traffic(){return Map.of("getWriteAttempts",getCalls.get(),"postWriteAttempts",postCalls.get());}
+ private final Map<String,String> identities;private final String bearer;private final PrivateKey signing;private final PublicKey assertion;private final InetSocketAddress endpoint;
+ private final ScheduledExecutorService watchdog=Executors.newScheduledThreadPool(1,r->{var t=new Thread(r,"knowledge-transport-deadline");t.setDaemon(true);return t;});private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();private volatile boolean closed;
+ public ControlledKnowledgeClient(Path applicationIdentities,URI origin,String mode)throws Exception {
+  if(!"ISOLATED_LOOPBACK_TEST".equals(mode)||!"http".equals(origin.getScheme())||!"127.0.0.1".equals(origin.getHost())||origin.getPort()<1||origin.getPort()>65535||origin.getUserInfo()!=null||origin.getRawQuery()!=null||origin.getFragment()!=null||!Set.of("","/").contains(origin.getPath()))throw new IllegalArgumentException("Explicit pinned isolated loopback-test origin required");
+  endpoint=new InetSocketAddress("127.0.0.1",origin.getPort());var config=parse(privateBytes(applicationIdentities,8192),8192);if(!"application".equals(config.get("role")))throw new IllegalArgumentException("Application credential role required");var ids=new TreeMap<String,String>();for(String key:List.of("providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId"))ids.put(key,string(config,key));identities=Map.copyOf(ids);
+  var paths=(Map<?,?>)config.get("paths");bearer=new String(privateBytes(Path.of((String)paths.get("serviceBearer")),128),StandardCharsets.US_ASCII);if(!bearer.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Controlled bearer encoding");var keys=KeyFactory.getInstance("Ed25519");signing=keys.generatePrivate(new PKCS8EncodedKeySpec(privateBytes(Path.of((String)paths.get("delegationPrivateKey")),8192)));assertion=keys.generatePublic(new X509EncodedKeySpec(privateBytes(Path.of((String)paths.get("providerAssertionPublicKey")),8192)));
+ }
+ public Map<String,String> identities(){return identities;}public int port(){return endpoint.getPort();}
+ static byte[] privateBytes(Path path,int limit)throws IOException {if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!Files.getPosixFilePermissions(path).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))||Files.size(path)>limit)throw new IllegalArgumentException("Restricted controlled credential required");return Files.readAllBytes(path);}
+ static byte[] canonical(Object value){try{byte[] intermediate=JSON.writeValueAsBytes(value);return ama.config.CanonicalJson.encode(ama.model.BoundedModelJson.parse(intermediate,8454144),8454144,()->false);}catch(IOException failure){throw new IllegalArgumentException("Controlled JSON encoding");}}
+ static Map<String,Object> parse(byte[] bytes,int limit){if(bytes.length>limit)throw new IllegalArgumentException("Controlled response budget");try{var node=JSON.readTree(bytes);if(!node.isObject())throw new IllegalArgumentException("Controlled object required");return JSON.convertValue(node,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(IOException malformed){throw new IllegalArgumentException("Invalid controlled JSON");}}
+ static String string(Map<String,?> map,String key){var value=map.get(key);if(!(value instanceof String s)||s.isEmpty())throw new IllegalArgumentException("Missing controlled field");return s;}
+ static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(GeneralSecurityException impossible){throw new IllegalStateException(impossible);}}
+ static String base64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
+ static byte[] unbase64(String text){if(!text.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid signed encoding");var bytes=Base64.getUrlDecoder().decode(text);if(!base64(bytes).equals(text))throw new IllegalArgumentException("Noncanonical signed encoding");return bytes;}
+ /** Only the genuine service/worker bridge may mint calls; an interface implementation is never accepted. */
+ static final class Call {
+  final ControlledKnowledgeClient issuer;final String actor,purpose,method,path;final byte[] body;
+  private Call(ControlledKnowledgeClient issuer,String actor,String purpose,String method,String path,byte[] body){this.issuer=issuer;this.actor=actor;this.purpose=purpose;this.method=method;this.path=path;this.body=body;}
+ }
+ Call authorized(JdbcPublicationService.Authorization authorization,String purpose,String method,String path,Object body){
+  authorization.require(this,purpose);String actor=authorization.actor();if(!path.matches("/v1/knowledge/[A-Za-z0-9_:/?=&%-]+")||path.length()>2048)throw new IllegalArgumentException("Bounded fixed controlled resource required");return new Call(this,actor,purpose,method,path,body==null?new byte[0]:canonical(body));
+ }
+ private String delegation(Call call){
+  if(call.issuer!=this)throw new IllegalArgumentException("Foreign controlled call");long now=Instant.now().getEpochSecond();var claims=new TreeMap<String,Object>();claims.put("schema","ama.controlled-kb-delegation.v1");claims.put("issuer",identities.get("clientId"));claims.put("keyId",identities.get("delegationKeyId"));claims.put("audience",identities.get("providerId"));claims.put("storeInstanceId",identities.get("storeInstanceId"));claims.put("actorId",call.actor);claims.put("purpose",call.purpose);claims.put("method",call.method);claims.put("resource",call.path);claims.put("issuedAt",now);claims.put("expiresAt",now+30);claims.put("nonce",UUID.randomUUID().toString().replace("-",""));claims.put("requestDigest",digest(call.method.equals("POST")?call.body:canonical(Map.of("method",call.method,"resource",call.path))));byte[] raw=canonical(claims);
+  try{var signature=Signature.getInstance("Ed25519");signature.initSign(signing);signature.update(raw);String result=base64(raw)+"."+base64(signature.sign());if(result.length()>4096)throw new IllegalArgumentException("Delegation budget");return result;}catch(GeneralSecurityException failure){throw new IllegalStateException("Controlled signer unavailable");}
+ }
+ Map<String,Object> exchange(Call call,int limit,PreparationCancellation cancellation)throws IOException {
+  if(call.issuer!=this||closed||cancellation.cancelled())throw new IOException("PROVIDER_UNAVAILABLE");if(call.body.length>8454144||limit>8454144)throw new IllegalArgumentException("Controlled wire budget");var socket=new Socket();sockets.add(socket);var deadline=watchdog.schedule(()->abort(socket),15,TimeUnit.SECONDS);
+  try(var registered=cancellation.register(()->abort(socket));socket){
+   if(closed||cancellation.cancelled())throw new IOException("PROVIDER_UNAVAILABLE");socket.connect(endpoint,3000);socket.setSoTimeout(5000);var out=socket.getOutputStream();String headers=call.method+" "+call.path+" HTTP/1.1\r\nHost: 127.0.0.1:"+endpoint.getPort()+"\r\nAuthorization: Bearer "+bearer+"\r\nX-Controlled-Delegation: "+delegation(call)+"\r\nContent-Type: application/json\r\nContent-Length: "+call.body.length+"\r\nConnection: close\r\n\r\n";if(call.method.equals("GET"))getCalls.incrementAndGet();else if(call.method.equals("POST"))postCalls.incrementAndGet();out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(call.body);out.flush();var in=socket.getInputStream();int[] used={0};String status=line(in,used,2048);if(!status.matches("HTTP/1\\.[01] [0-9]{3}.*"))throw new IOException("INVALID_PROVIDER_RECEIPT");int code=Integer.parseInt(status.substring(9,12));var response=new HashMap<String,String>();
+   for(int i=0;;i++){String h=line(in,used,4096);if(h.isEmpty())break;if(i>=32)throw new IOException("INVALID_PROVIDER_RECEIPT");int colon=h.indexOf(':');if(colon<=0)throw new IOException("INVALID_PROVIDER_RECEIPT");String key=h.substring(0,colon).toLowerCase(Locale.ROOT),value=h.substring(colon+1).strip();if(response.putIfAbsent(key,value)!=null)throw new IOException("INVALID_PROVIDER_RECEIPT");}
+   if(response.containsKey("transfer-encoding")||response.containsKey("content-encoding")||!"application/json".equals(response.get("content-type")))throw new IOException("INVALID_PROVIDER_RECEIPT");String length=response.get("content-length");if(length==null||!length.matches("0|[1-9][0-9]{0,8}"))throw new IOException("INVALID_PROVIDER_RECEIPT");int count=Integer.parseInt(length);if(count>(code==200?limit:65536))throw new IOException("INVALID_PROVIDER_RECEIPT");byte[] bytes=in.readNBytes(count);if(bytes.length!=count)throw new IOException("UNKNOWN_OUTCOME");if(code!=200)throw new ProviderFailure(code,code==401||code==403?"DESTINATION_DENIED":code==409?"PROVIDER_CONFLICT":"PROVIDER_UNAVAILABLE");var value=parse(bytes,limit);if(!Objects.equals(value.get("providerId"),identities.get("providerId"))||!Objects.equals(value.get("storeInstanceId"),identities.get("storeInstanceId")))throw new IOException("INVALID_PROVIDER_RECEIPT");return value;
+  }catch(IOException failure){throw failure;}catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw new IOException("PROVIDER_UNAVAILABLE");}finally{deadline.cancel(false);sockets.remove(socket);abort(socket);}
+ }
+ Map<String,Object> assertion(String token){
+  if(token==null||token.length()>4096||token.chars().filter(c->c=='.').count()!=1)throw new IllegalArgumentException("Invalid destination assertion");String[] parts=token.split("\\.",-1);byte[] bytes=unbase64(parts[0]),signature=unbase64(parts[1]);try{var verifier=Signature.getInstance("Ed25519");verifier.initVerify(assertion);verifier.update(bytes);if(signature.length!=64||!verifier.verify(signature))throw new IllegalArgumentException("Invalid destination signature");}catch(GeneralSecurityException failure){throw new IllegalArgumentException("Invalid destination signature");}var claims=parse(bytes,3072);if(!Arrays.equals(bytes,canonical(claims))||!"ama.controlled-kb-destination.v1".equals(claims.get("schema")))throw new IllegalArgumentException("Invalid destination assertion");for(var entry:identities.entrySet())if(!entry.getValue().equals(claims.get(entry.getKey())))throw new IllegalArgumentException("Pinned destination identity mismatch");long now=Instant.now().getEpochSecond(),issued=((Number)claims.get("issuedAt")).longValue(),expires=((Number)claims.get("expiresAt")).longValue();if(issued>now+2||expires<now||expires<issued||expires>issued+30)throw new IllegalArgumentException("Expired destination assertion");return claims;
+ }
+ /** Permanent, signed exact rejection. Unlike destination assertions this immutable fact does not expire. */
+ Map<String,Object> permanentRejection(String token){return verifyPermanentRejection(assertion,identities,token);}
+ static Map<String,Object> verifyPermanentRejection(PublicKey assertion,Map<String,String> identities,String token){
+  if(token==null||token.length()>8192||token.chars().filter(c->c=='.').count()!=1)throw new IllegalArgumentException("Invalid permanent rejection");String[] parts=token.split("\\.",-1);byte[] bytes=unbase64(parts[0]),signature=unbase64(parts[1]);
+  try{var verifier=Signature.getInstance("Ed25519");verifier.initVerify(assertion);verifier.update(bytes);if(signature.length!=64||!verifier.verify(signature))throw new IllegalArgumentException("Invalid rejection signature");}catch(GeneralSecurityException failure){throw new IllegalArgumentException("Invalid rejection signature");}
+  var claims=parse(bytes,4096);if(!Arrays.equals(bytes,canonical(claims))||!"ama.controlled-kb-permanent-rejection.v1".equals(claims.get("schema")))throw new IllegalArgumentException("Invalid permanent rejection");for(var entry:identities.entrySet())if(!entry.getValue().equals(claims.get(entry.getKey())))throw new IllegalArgumentException("Pinned rejection identity mismatch");
+  var exact=new HashSet<String>(identities.keySet());exact.addAll(Set.of("schema","publicationId","confirmationId","businessKey","logicalRequestHash","executionRequestHash","executionId","executionNo","actorId","libraryId","folderId","payloadSha256","receiptId","verifiedAt","slotGeneration","noEffectBasis","errorCode"));if(!claims.keySet().equals(exact)||!"DEFINITIVE_REJECTION".equals(claims.get("noEffectBasis"))||!"DESTINATION_DENIED".equals(claims.get("errorCode"))||!(claims.get("verifiedAt") instanceof Number n)||n.longValue()<1||n.longValue()>Instant.now().getEpochSecond()+2||!(claims.get("slotGeneration") instanceof Number g)||g.longValue()<1)throw new IllegalArgumentException("Invalid permanent rejection binding");return claims;
+ }
+ static final class ProviderFailure extends IOException {final int status;ProviderFailure(int status,String code){super(code);this.status=status;}}
+ private static String line(InputStream in,int[] used,int max)throws IOException {var line=new ByteArrayOutputStream();boolean cr=false;for(;;){int b=in.read();if(b<0||++used[0]>8192)throw new IOException("INVALID_PROVIDER_RECEIPT");if(cr){if(b!='\n')throw new IOException("INVALID_PROVIDER_RECEIPT");return line.toString(StandardCharsets.US_ASCII);}if(b=='\r'){cr=true;continue;}if(b<32||b>126||line.size()>=max)throw new IOException("INVALID_PROVIDER_RECEIPT");line.write(b);}}
+ private static void abort(Socket socket){try{socket.close();}catch(IOException ignored){}}
+ public void close(){closed=true;for(var socket:sockets)abort(socket);watchdog.shutdownNow();try{watchdog.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
+}

## backend/adapters/src/main/java/ama/transcript/JdbcFinalConfirmations.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/JdbcFinalConfirmations.java
@@ -0,0 +1,73 @@
+package ama.transcript;
+
+import ama.config.*;
+import ama.config.json.*;
+import ama.contract.IdentityContracts.Actor;
+import ama.contract.MinutesContracts.*;
+import ama.contract.TaskContracts.SubjectRef;
+import ama.minutes.contract.*;
+import java.util.*;
+import java.util.function.Supplier;
+import org.springframework.jdbc.core.JdbcTemplate;
+import static ama.contract.MinutesContracts.*;
+import static ama.contract.MinutesContracts.FailureCode.*;
+
+/** Final confirmation owns S/A/F acquisition and an issuer-bound atomic write. */
+public final class JdbcFinalConfirmations {
+ private final JdbcTranscriptAuthority authority;private final Supplier<ConfigArchiveSource.Snapshot> archives;private final String pin;
+ private final TranscriptAcquisitionBudget limits;private final JdbcSavedTranscripts transcripts;private final JdbcTemplate j;
+ private final JdbcMinutesCommandReceipts receipts;private final JdbcMinutesFactsReader reader;private final JdbcMinutesSavedQueries queries;
+ private record Plan(MinutesGraphCapture.Graph graph,FinalDraft draft,String hash,CorrectionInstallation correction){}
+ public JdbcFinalConfirmations(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits,JdbcSavedTranscripts transcripts){
+  this.authority=Objects.requireNonNull(authority);this.archives=Objects.requireNonNull(archives);hash(pin);this.pin=pin;this.limits=Objects.requireNonNull(limits);this.transcripts=Objects.requireNonNull(transcripts);transcripts.correctionFor(authority);
+  j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);receipts=new JdbcMinutesCommandReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());reader=new JdbcMinutesFactsReader(authority,archives,pin,limits);queries=new JdbcMinutesSavedQueries(authority,archives,pin,limits,transcripts);
+ }
+ public Map<String,Object> readiness(Actor actor,String meeting){var snapshot=reader.readOwner(actor,meeting,"READINESS",null);var result=queries.readiness(snapshot,true);var correction=transcripts.correctionFor(authority);reader.recheck(actor,meeting,snapshot,transcripts,correction,"NOT_APPLICABLE".equals(correction.projection().readiness()));return result;}
+ public Map<String,Object> confirm(Actor actor,String meeting,String key,long expected,List<String> optionalReviewIds,String trace){
+  JdbcMinutesFactsReader.independent();uuid(trace);var optional=new ArrayList<>(optionalReviewIds);for(String id:optional)uuid(id);optional.sort(String::compareTo);require(new HashSet<>(optional).size()==optional.size()&&optional.size()<=256,INVALID_ARGUMENT);
+  var identity=JdbcMinutesCommandReceipts.identity(actor,meeting,"FINAL_CONFIRM",key,expected,optional.toArray(String[]::new));var replay=receipts.lookup(actor,meeting,identity);if(replay.isPresent())return replay.orElseThrow();
+  var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);MinutesGraphDiscovery.Discovery declaration=null;Plan planned=null;RuntimeException error=null;
+  try{
+   declaration=MinutesGraphDiscovery.withPending(authority.dataSource(),MinutesGraphDiscovery.discover(authority.dataSource(),meeting,"READINESS",null,limits,authority.readBudget()),limits.tasks());JdbcMinutesFactsReader.charge(declaration,budget);var declared=declaration;
+   var graph=authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declared);var cap=authority.minutesScope(gate,actor,meeting);var captured=MinutesGraphCapture.captureCurrent(authority,cap,gate,actor,declared,budget);writable(captured,expected);return captured;});
+   var snapshot=reader.prepare(graph,budget);var readiness=queries.readiness(snapshot,true);if(!Boolean.TRUE.equals(readiness.get("ready")))throw new ama.contract.TranscriptContracts.Failure(409,"FINAL_NOT_READY");
+   var eligible=(List<?>)readiness.get("optionalReviewIds");if(!eligible.containsAll(optional))throw new ama.contract.TranscriptContracts.Failure(422,"INVALID_OPTIONAL_REVIEW");var members=new TreeSet<String>();for(Object id:(List<?>)readiness.get("requiredReviewIds"))members.add((String)id);members.addAll(optional);
+   var root=graph.discovery().root();var body=JdbcMinutesFactsReader.one(graph,Table.MINUTES_REVISION,(String)root.get("minutes_revision_id"));var input=JdbcMinutesFactsReader.one(graph,Table.GENERATION_INPUT,body.string("generation_input_id"));
+   String now=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").format(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
+   var draft=new FinalDraft(UUID.randomUUID().toString(),meeting,expected+1,(String)root.get("info_revision_id"),(String)root.get("roster_revision_id"),(String)root.get("transcript_confirmation_id"),body.string("id"),(String)root.get("material_snapshot_id"),(String)root.get("element_set_id"),(String)root.get("template_revision_id"),(String)root.get("exclusion_revision_id"),input.string("input_fingerprint"),actor.enterpriseId(),now,List.copyOf(members),snapshot.facts());
+   var archive=archives.get();require(pin.equals(archive.indexSha256()),PROFILE_UNAVAILABLE);var gb=new GraphBudget(authority.canonicalBudget(),limits.revisionDepth(),limits.revisionNodes(),authority.readBudget().maxFactRows());var refs=new GenerationArchiveRefs(snapshot.facts(),archive.index(),new StrictProcessingManifestReader(),authority.readBudget(),gb,()->false).finalSources(draft);var raw=MinutesStoredReviews.load(archive,refs,authority.readBudget(),budget);
+   var configurations=snapshot.facts().configurations().stream().filter(c->c.inputId().equals(input.string("id"))).toList();require(configurations.size()==1,PROFILE_UNAVAILABLE);
+   String computed=new ResultHashProfiles(new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader()),raw,authority.readBudget()).finalSources(draft,configurations.getFirst().profiles().definition(ProfileKind.FINAL_SOURCES),gb,()->false);
+   planned=new Plan(graph,draft,computed,transcripts.correctionFor(authority));
+  }catch(RuntimeException failure){if(Thread.currentThread().isInterrupted()||failure instanceof Failure f&&f.code()==CANCELLED)throw failure;error=failure;}
+  final var selected=declaration;final var plan=planned;final var failed=error;
+  return authority.transactionRunner().required(()->{
+   var stored=receipts.claim(actor,meeting,identity);var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,selected);JdbcMinutesCommandReceipts.match(stored);MinutesDeferredPreparation.beforeReplay(failed);if(stored.result()!=null)return receipts.restore(meeting,identity,stored,true);JdbcMinutesCommandReceipts.requireRevision(j,meeting,expected);if(failed!=null)throw failed;
+   var cap=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.captureCurrent(authority,cap,gate,actor,selected,budget);require(fresh.equals(plan.graph()),PROVENANCE_UNAVAILABLE);writable(fresh,expected);transcripts.requireCorrection(authority,plan.correction(),true);
+   authority.claimMinutesWrite(cap,gate,actor,fresh,JdbcTranscriptAuthority.MinutesWritePurpose.FINAL_CONFIRM,plan);authority.requireMinutesInvocation(cap,gate,actor,fresh,JdbcTranscriptAuthority.MinutesWritePurpose.FINAL_CONFIRM,plan);
+   var d=plan.draft();require(j.update("INSERT INTO final_confirmation(id,meeting_id,meeting_revision,info_revision_id,roster_revision_id,transcript_confirmation_id,minutes_revision_id,material_snapshot_id,element_set_id,template_revision_id,exclusion_revision_id,source_manifest_hash,input_fingerprint,confirmed_by,confirmed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",d.confirmationId(),meeting,d.meetingRevision(),d.infoRevisionId(),d.rosterRevisionId(),d.transcriptConfirmationId(),d.minutesRevisionId(),d.materialSnapshotId(),d.elementSetId(),d.templateRevisionId(),d.exclusionRevisionId(),plan.hash(),d.inputFingerprint(),d.confirmedBy(),java.time.LocalDateTime.ofInstant(java.time.Instant.parse(d.confirmedAt()),java.time.ZoneOffset.UTC))==1,PROVENANCE_UNAVAILABLE);
+   for(String review:d.reviewIds())require(j.update("INSERT INTO final_result_review(confirmation_id,review_id) VALUES (?,?)",d.confirmationId(),review)==1,PROVENANCE_UNAVAILABLE);
+   var actual=new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes()).rows(Table.FINAL_CONFIRMATION,"id=?","id",1,true,d.confirmationId());require(actual.size()==1,PROVENANCE_UNAVAILABLE);var row=actual.getFirst();
+   var values=new LinkedHashMap<String,Object>();values.put("id",d.confirmationId());values.put("meeting_id",meeting);values.put("meeting_revision",d.meetingRevision());values.put("info_revision_id",d.infoRevisionId());values.put("roster_revision_id",d.rosterRevisionId());values.put("transcript_confirmation_id",d.transcriptConfirmationId());values.put("minutes_revision_id",d.minutesRevisionId());values.put("material_snapshot_id",d.materialSnapshotId());values.put("element_set_id",d.elementSetId());values.put("template_revision_id",d.templateRevisionId());values.put("exclusion_revision_id",d.exclusionRevisionId());values.put("source_manifest_hash",plan.hash());values.put("input_fingerprint",d.inputFingerprint());values.put("confirmed_by",d.confirmedBy());values.put("confirmed_at",d.confirmedAt());
+   for(var entry:values.entrySet()){var v=row.value(entry.getKey());Object actualValue=v instanceof NullCell?null:v instanceof UIntCell n?n.value():((TextCell)v).value();require(Objects.equals(actualValue,entry.getValue()),PROVENANCE_UNAVAILABLE);}
+   require(j.queryForList("SELECT review_id FROM final_result_review WHERE confirmation_id=? ORDER BY review_id",String.class,d.confirmationId()).equals(d.reviewIds()),PROVENANCE_UNAVAILABLE);
+   var fences=new ama.task.JdbcSubjectFences(authority.dataSource(),Set.of("MEETING"));var subject=fences.lock(new SubjectRef("MEETING",meeting),false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));require(subject.businessVersion()==expected,PROVENANCE_UNAVAILABLE);
+   require(j.update("UPDATE meeting SET revision=?,final_confirmation_id=?,stage='READY_TO_PUBLISH',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=?",expected+1,d.confirmationId(),meeting,expected,subject.cancelEpoch())==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",expected+1,meeting)==1,PROVENANCE_UNAVAILABLE);fences.advance(subject,expected+1,subject.cancelEpoch(),false);authority.finishMinutesGraphTransition(cap,gate,actor,fresh,expected+1);
+   receipts.accepted(identity,d.confirmationId());new ama.audit.JdbcAuditAdapter(authority.dataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),"FINAL_CONFIRMED",meeting,expected+1,"SUCCEEDED",trace,java.time.Instant.now()));
+   var outbox=new ama.task.JdbcOutbox(authority.dataSource(),new MinutesEventReferences(authority.dataSource()).validators());outbox.append(new ama.contract.OutboxContracts.EventDraft("FINAL_CONFIRMED",null,new SubjectRef("MEETING",meeting),expected+1,new ama.contract.OutboxContracts.Reference("FINAL_CONFIRMATION",d.confirmationId())));outbox.flush();
+   return receipts.restore(meeting,identity,new JdbcMinutesCommandReceipts.Stored(d.confirmationId(),200,true),false);
+  });
+ }
+ public Map<String,Object> current(Actor actor,String meeting){
+  String id=authority.transactionRunner().required(()->{authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),ama.access.MeetingAccess.Action.READ);return j.queryForObject("SELECT final_confirmation_id FROM meeting WHERE id=?",String.class,meeting);});
+  if(id==null)return JdbcMinutesSavedQueries.map("confirmation",null);return detail(actor,meeting,id);
+ }
+ public Map<String,Object> detail(Actor actor,String meeting,String id){
+  uuid(id);var snapshot=reader.readOwner(actor,meeting,"FINAL",id);var row=JdbcMinutesFactsReader.one(snapshot.graph(),Table.FINAL_CONFIRMATION,id);
+  return JdbcMinutesSavedQueries.map("confirmation",JdbcMinutesSavedQueries.project(row),"body",JdbcMinutesSavedQueries.project(JdbcMinutesFactsReader.one(snapshot.graph(),Table.MINUTES_REVISION,row.string("minutes_revision_id"))),"reviewIds",snapshot.facts().rows().stream().filter(r->r.table()==Table.FINAL_RESULT_REVIEW&&id.equals(r.string("confirmation_id"))).map(r->r.string("review_id")).sorted().toList(),"provenanceVerified",true,"observation",JdbcMinutesSettingsReceipts.observation(snapshot.observation()));
+ }
+ private static void writable(MinutesGraphCapture.Graph graph,long expected){
+  var root=graph.discovery().root();if(((Number)root.get("revision")).longValue()!=expected)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");if(expected>=Long.MAX_VALUE-1)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_EXHAUSTED");
+  require("ACTIVE".equals(root.get("lifecycle"))&&"ACTIVE".equals(root.get("governance_state"))&&((Number)root.get("ever_published")).intValue()==0,CAPABILITY_UNAVAILABLE);
+ }
+}

## backend/adapters/src/main/java/ama/transcript/JdbcMinutesCommandReceipts.java
--- a/backend/adapters/src/main/java/ama/transcript/JdbcMinutesCommandReceipts.java
+++ b/backend/adapters/src/main/java/ama/transcript/JdbcMinutesCommandReceipts.java
@@ -1,20 +1,20 @@
 package ama.transcript;
 import ama.access.MeetingAccess;import ama.contract.IdentityContracts.Actor;import ama.contract.TranscriptContracts.Failure;import ama.db.TransactionRunner;import javax.sql.DataSource;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;
 /** Independently constructible authenticated receipts: no current config/readiness/CAS on true replay. */
 public final class JdbcMinutesCommandReceipts {
- private static final Map<String,String> KINDS=Map.ofEntries(Map.entry("MINUTES_ADOPT","GENERATION_CANDIDATE"),Map.entry("MINUTES_REJECT","GENERATION_CANDIDATE"),Map.entry("MINUTES_DISPOSE","GENERATION_CANDIDATE"),Map.entry("MINUTES_BODY_SAVE","MINUTES_REVISION"),Map.entry("MINUTES_BODY_RESTORE","MINUTES_REVISION"),Map.entry("MINUTES_ELEMENT_EDIT","ELEMENT_SET"),Map.entry("MINUTES_ACTION_FIELDS","ELEMENT_SET"),Map.entry("MINUTES_OPEN_QUESTION","ELEMENT_SET"),Map.entry("MINUTES_SET_RESTORE","ELEMENT_SET"),Map.entry("MINUTES_TEMPLATE","TEMPLATE_REVISION"),Map.entry("MINUTES_EXCLUSIONS","EXCLUSION_REVISION"),Map.entry("MINUTES_REVIEW","RESULT_REVIEW"));
+ private static final Map<String,String> KINDS=Map.ofEntries(Map.entry("MINUTES_ADOPT","GENERATION_CANDIDATE"),Map.entry("MINUTES_REJECT","GENERATION_CANDIDATE"),Map.entry("MINUTES_DISPOSE","GENERATION_CANDIDATE"),Map.entry("MINUTES_BODY_SAVE","MINUTES_REVISION"),Map.entry("MINUTES_BODY_RESTORE","MINUTES_REVISION"),Map.entry("MINUTES_ELEMENT_EDIT","ELEMENT_SET"),Map.entry("MINUTES_ACTION_FIELDS","ELEMENT_SET"),Map.entry("MINUTES_OPEN_QUESTION","ELEMENT_SET"),Map.entry("MINUTES_SET_RESTORE","ELEMENT_SET"),Map.entry("MINUTES_TEMPLATE","TEMPLATE_REVISION"),Map.entry("MINUTES_EXCLUSIONS","EXCLUSION_REVISION"),Map.entry("MINUTES_REVIEW","RESULT_REVIEW"),Map.entry("FINAL_CONFIRM","FINAL_CONFIRMATION"));
  private final JdbcTemplate j;private final TransactionRunner tx;private final MeetingAccess access;
  public JdbcMinutesCommandReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){j=new JdbcTemplate(source);j.setQueryTimeout(2);this.tx=tx;this.access=access;}
  record Identity(String operation,String scope,String key,String request){}record Stored(String result,int status,boolean matches){}
  static Identity identity(Actor actor,String meeting,String operation,String key,long expected,String... semantics){JdbcProcessingIntake.key(key);ama.contract.MinutesContracts.uuid(meeting);if(!KINDS.containsKey(operation)||expected<0||expected>Long.MAX_VALUE-1)throw new IllegalArgumentException("Invalid minutes command");var fields=new ArrayList<String>();fields.add("minutes-command-v1");fields.add(operation);fields.add(Long.toString(expected));for(String value:semantics){fields.add(value==null?"NULL":"VALUE");if(value!=null){ama.contract.MinutesContracts.scalars(value);fields.add(value);}}return new Identity(operation,JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,operation,key),JdbcProcessingIntake.digest(key),JdbcProcessingIntake.digest(fields.toArray(String[]::new)));}
  public Optional<Map<String,Object>> lookup(Actor actor,String meeting,String operation,String key,long expected,String... semantics){return lookup(actor,meeting,identity(actor,meeting,operation,key,expected,semantics));}
  Optional<Map<String,Object>> lookup(Actor actor,String meeting,Identity id){return tx.required(()->{var record=record(actor,meeting,id);access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);if(record.isEmpty())return Optional.empty();match(record.orElseThrow());return Optional.of(restore(meeting,id,record.orElseThrow(),true));});}
  public Map<String,Object> discover(Actor actor,String meeting,String operation,String key){JdbcProcessingIntake.key(key);ama.contract.MinutesContracts.uuid(meeting);if(!KINDS.containsKey(operation))throw new IllegalArgumentException("Unknown command");String scope=JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,operation,key);return tx.required(()->{var records=j.queryForList("SELECT actor_id,resource_id,operation,key_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);if(records.isEmpty())return Map.of("status","NOT_FOUND","operation",operation,"observation",observation(j,meeting));if(records.size()!=1)throw unavailable();var record=records.getFirst();if(!actor.enterpriseId().equals(record.get("actor_id"))||!meeting.equals(record.get("resource_id"))||!operation.equals(record.get("operation"))||!JdbcProcessingIntake.digest(key).equals(record.get("key_hash"))||!"SUCCEEDED".equals(record.get("state")))throw unavailable();return restore(meeting,new Identity(operation,scope,(String)record.get("key_hash"),""),new Stored((String)record.get("result_id"),((Number)record.get("http_status")).intValue(),true),true);});}
  private Optional<Stored> record(Actor actor,String meeting,Identity id){return j.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",(r,n)->new Stored(r.getString(6),r.getInt(7),actor.enterpriseId().equals(r.getString(1))&&meeting.equals(r.getString(2))&&id.operation().equals(r.getString(3))&&id.key().equals(r.getString(4))&&id.request().equals(r.getString(5))&&"SUCCEEDED".equals(r.getString(8))),id.scope()).stream().findFirst();}
  Stored claim(Actor actor,String meeting,Identity id){j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,?,?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",id.scope(),actor.enterpriseId(),meeting,id.operation(),id.key(),id.request());return record(actor,meeting,id).orElseThrow(JdbcMinutesCommandReceipts::unavailable);}
  static void requireRevision(JdbcTemplate j,String meeting,long expected){if(j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting)!=expected)throw new Failure(409,"REVISION_CONFLICT");}
  static void match(Stored value){if(!value.matches())throw new Failure(409,"IDEMPOTENCY_CONFLICT");}
  void accepted(Identity id,String result){ama.contract.MinutesContracts.uuid(result);if(j.update("UPDATE idempotency_record SET result_id=?,http_status=200 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",result,id.scope())!=1)throw unavailable();}
  Map<String,Object> restore(String meeting,Identity id,Stored stored,boolean replay){if(stored.result()==null||stored.status()!=200)throw unavailable();String kind=KINDS.get(id.operation());String table=kind.toLowerCase(Locale.ROOT);if(kind.equals("TEMPLATE_REVISION")){if(j.queryForObject("SELECT COUNT(*) FROM template_revision WHERE id=?",Long.class,stored.result())!=1)throw unavailable();}else if(j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND meeting_id=?",Long.class,stored.result(),meeting)!=1)throw unavailable();
   return Map.of("operation",id.operation(),"resultKind",kind,"resultId",stored.result(),"httpStatus",200,"replayed",replay,"observation",observation(j,meeting));}
  static Map<String,Object> observation(JdbcTemplate j,String meeting){var root=j.queryForMap("SELECT revision,access_revision,lifecycle,ever_published,minutes_revision_id,element_set_id,transcript_confirmation_id,template_revision_id,exclusion_revision_id,needs_generation FROM meeting WHERE id=?",meeting);var result=new LinkedHashMap<String,Object>();for(String key:List.of("revision","access_revision")){long value=((Number)root.get(key)).longValue();String name=key.equals("revision")?"revision":"accessRevision";result.put(name,value);result.put(name+"Token",Long.toString(value));}result.put("lifecycle",root.get("lifecycle"));result.put("currentBodyId",root.get("minutes_revision_id"));result.put("currentSetId",root.get("element_set_id"));result.put("currentConfirmationId",root.get("transcript_confirmation_id"));result.put("templateRevisionId",root.get("template_revision_id"));result.put("exclusionRevisionId",root.get("exclusion_revision_id"));result.put("needsGeneration",((Number)root.get("needs_generation")).intValue()!=0);result.put("readOnlyReason",((Number)root.get("ever_published")).intValue()!=0?"PUBLISHED":null);return Collections.unmodifiableMap(result);}

## backend/adapters/src/main/java/ama/transcript/JdbcMinutesSavedQueries.java
--- a/backend/adapters/src/main/java/ama/transcript/JdbcMinutesSavedQueries.java
+++ b/backend/adapters/src/main/java/ama/transcript/JdbcMinutesSavedQueries.java
@@ -1,33 +1,36 @@
 package ama.transcript;
 import ama.access.MeetingAccess;import ama.config.*;import ama.config.json.*;import ama.contract.IdentityContracts.Actor;import ama.contract.MinutesContracts.*;import ama.minutes.contract.*;import java.util.*;import java.util.function.Supplier;import org.springframework.jdbc.core.JdbcTemplate;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
 /** Distinct current READ projection and owner immutable history/readiness. Never infers a historical body/set pair. */
 public final class JdbcMinutesSavedQueries {
  private final JdbcTranscriptAuthority authority;private final JdbcMinutesFactsReader reader;private final Supplier<ConfigArchiveSource.Snapshot> archives;private final String pin;private final TranscriptAcquisitionBudget limits;private final JdbcTemplate j;private final JdbcSavedTranscripts transcripts;
  public JdbcMinutesSavedQueries(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits){this(authority,archives,pin,limits,null);}
  public JdbcMinutesSavedQueries(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits,JdbcSavedTranscripts transcripts){this.transcripts=transcripts;if(transcripts!=null)transcripts.correctionFor(authority);this.authority=authority;this.archives=archives;this.pin=pin;this.limits=limits;reader=new JdbcMinutesFactsReader(authority,archives,pin,limits);j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);}
  public Map<String,Object> current(Actor actor,String meeting){var snapshot=reader.readCurrent(actor,meeting);String body=(String)snapshot.observation().get("minutes_revision_id"),set=(String)snapshot.observation().get("element_set_id");if(body!=null)saved(snapshot,body,true);if(set!=null)saved(snapshot,set,false);return map("body",body==null?null:bodyProjection(one(snapshot,Table.MINUTES_REVISION,body),false),"setId",set,"elements",elements(snapshot,set,false),"evidence",effectiveEvidence(snapshot,body,set).stream().map(JdbcMinutesSavedQueries::project).toList(),"sensitive",directories(snapshot,set,true),"background",directories(snapshot,set,false),"observation",observation(snapshot));}
  public Map<String,Object> history(Actor actor,String meeting,boolean body,int limit,String before){if(limit<1||limit>50)throw new IllegalArgumentException("Page limit out of range");Long cursor=before==null?null:counter(before);return authority.transactionRunner().required(()->{authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),MeetingAccess.Action.EDIT);String table=body?"minutes_revision":"element_set",field=body?"minutes_revision_id":"element_set_id";var args=new ArrayList<Object>();args.add(meeting);if(cursor!=null)args.add(cursor);args.add(limit+1);var rows=j.queryForList("SELECT r.id,r.revision,r.created_at"+(body?",r.title_origin":"")+" FROM "+table+" r WHERE r.meeting_id=?"+(cursor==null?"":" AND r.revision<?")+" AND NOT EXISTS(SELECT 1 FROM generation_candidate c WHERE c."+field+"=r.id) ORDER BY r.revision DESC LIMIT ?",args.toArray());boolean more=rows.size()>limit;if(more)rows=rows.subList(0,limit);var items=rows.stream().map(row->map("id",row.get("id"),"revisionToken",row.get("revision").toString(),"createdAt",row.get("created_at").toString(),"titleOrigin",row.get("title_origin"))).toList();return map("items",items,"nextBeforeRevisionToken",more?rows.getLast().get("revision").toString():null,"hasMore",more,"observation",JdbcMinutesCommandReceipts.observation(j,meeting));});}
  public Map<String,Object> detail(Actor actor,String meeting,String id,boolean body){uuid(id);var snapshot=reader.readOwner(actor,meeting,body?"OWNER_REVISION":"OWNER_SET",id);saved(snapshot,id,body);return map(body?"body":"set",body?bodyProjection(one(snapshot,Table.MINUTES_REVISION,id),true):project(one(snapshot,Table.ELEMENT_SET,id)),"elements",body?null:elements(snapshot,id,true),"evidence",targetEvidence(snapshot,body?"BODY":"SET",id).stream().map(JdbcMinutesSavedQueries::project).toList(),"sensitive",body?List.of():directories(snapshot,id,true),"background",body?List.of():directories(snapshot,id,false),"observation",observation(snapshot));}
  public Map<String,Object> settings(Actor actor,String meeting){var snapshot=reader.readOwner(actor,meeting,"SETTINGS_READ",null);String template=(String)snapshot.observation().get("template_revision_id"),exclusion=(String)snapshot.observation().get("exclusion_revision_id");return map("template",template==null?null:project(one(snapshot,Table.TEMPLATE_REVISION,template)),"exclusionRevision",exclusion==null?null:project(one(snapshot,Table.EXCLUSION_REVISION,exclusion)),"items",rows(snapshot,Table.SENSITIVE_EXCLUSION).stream().filter(r->exclusion!=null&&exclusion.equals(r.string("revision_id"))).map(JdbcMinutesSavedQueries::project).toList(),"observation",observation(snapshot));}
  public Map<String,Object> review(Actor actor,String meeting,String id){uuid(id);var snapshot=reader.readOwner(actor,meeting,"REVIEW_ID",id);var review=one(snapshot,Table.RESULT_REVIEW,id);saved(snapshot,review.nullableString("element_id")==null?review.string("minutes_revision_id"):one(snapshot,Table.MINUTES_ELEMENT,review.string("element_id")).string("set_id"),review.nullableString("element_id")==null);var memberships=rows(snapshot,Table.RESULT_REVIEW_EVIDENCE).stream().filter(row->id.equals(row.string("review_id"))).map(row->row.string("evidence_id")).toList();return map("review",project(review),"evidence",rows(snapshot,Table.EVIDENCE_LINK).stream().filter(row->memberships.contains(row.string("id"))).map(JdbcMinutesSavedQueries::project).toList(),"provenanceVerified",true,"observation",observation(snapshot));}
  public Map<String,Object> reviews(Actor actor,String meeting,String kind,String target,int limit,String before){uuid(target);if(!Set.of("BODY","ELEMENT").contains(kind)||limit<1||limit>50)throw new IllegalArgumentException("Invalid review page");Long cursor=before==null?null:counter(before);var snapshot=reader.readOwner(actor,meeting,kind.equals("BODY")?"REVIEW_BODY":"REVIEW_ELEMENT",target);saved(snapshot,kind.equals("BODY")?target:one(snapshot,Table.MINUTES_ELEMENT,target).string("set_id"),kind.equals("BODY"));String column=kind.equals("BODY")?"minutes_revision_id":"element_id";var selected=rows(snapshot,Table.RESULT_REVIEW).stream().filter(row->target.equals(row.nullableString(column))&&(cursor==null||row.number("review_no")<cursor)).sorted(Comparator.comparingLong((FullRow row)->row.number("review_no")).reversed()).toList();boolean more=selected.size()>limit;if(more)selected=selected.subList(0,limit);return map("items",selected.stream().map(JdbcMinutesSavedQueries::project).toList(),"nextBeforeReviewNoToken",more?Long.toString(selected.getLast().number("review_no")):null,"hasMore",more,"observation",observation(snapshot));}
  public Map<String,Object> source(Actor actor,String meeting,String locator,String kind,String target,String historicalBody,String historicalSet){uuid(locator);boolean history=historicalBody!=null||historicalSet!=null;JdbcMinutesFactsReader.Snapshot snapshot;if(history){String selected=historicalBody!=null?historicalBody:historicalSet;uuid(selected);snapshot=reader.readOwner(actor,meeting,historicalBody!=null?"OWNER_REVISION":"OWNER_SET",selected);saved(snapshot,selected,historicalBody!=null);kind=historicalBody!=null?"BODY":"SET";target=selected;}else{if(!Set.of("BODY","ELEMENT").contains(kind))throw new IllegalArgumentException("Target required");uuid(target);snapshot=reader.readCurrent(actor,meeting);if(kind.equals("BODY")){if(!target.equals(snapshot.observation().get("minutes_revision_id")))throw absent();}else if(!one(snapshot,Table.MINUTES_ELEMENT,target).string("set_id").equals(snapshot.observation().get("element_set_id")))throw absent();}if(targetEvidence(snapshot,kind,target).stream().noneMatch(row->locator.equals(row.string("locator_id")))&&!(kind.equals("SET")&&directoryLocators(snapshot,target).contains(locator)))throw absent();var selected=one(snapshot,Table.SOURCE_LOCATOR,locator);require("TRANSCRIPT".equals(selected.string("kind")),CAPABILITY_UNAVAILABLE);var segments=rows(snapshot,Table.TRANSCRIPT_SEGMENT).stream().filter(row->selected.string("transcript_revision_id").equals(row.string("revision_id"))&&selected.string("segment_id").equals(row.string("segment_id"))).toList();require(segments.size()==1,PROVENANCE_UNAVAILABLE);var segment=segments.getFirst();var marks=rows(snapshot,Table.TRANSCRIPT_SEGMENT_MARK).stream().filter(row->selected.string("transcript_revision_id").equals(row.string("revision_id"))&&selected.string("segment_id").equals(row.string("segment_id"))).map(row->row.string("mark")).sorted().toList();String route="/api/v1/meetings/"+meeting+"/transcript/"+(history?"revisions/"+selected.string("transcript_revision_id")+"/":"")+"sources/"+segment.string("source_file_id");return map("locator",project(selected),"text",segment.string("body"),"marks",marks,"sourceVersion",selected.string("transcript_revision_id"),"historical",history,"originalBytes",map("route",route,"eligibility","RECHECK_REQUIRED"),"observation",observation(snapshot));}
- public Map<String,Object> readiness(Actor actor,String meeting){var snapshot=reader.readOwner(actor,meeting,"READINESS",null);var root=snapshot.observation();var blockers=new LinkedHashSet<String>();var installation=transcripts==null?null:transcripts.correctionFor(authority);var correction=installation==null?new CorrectionInstallation.Projection("UNAVAILABLE","UNAVAILABLE",null):installation.projection();boolean correctionReady="NOT_APPLICABLE".equals(correction.readiness());if(!correctionReady)blockers.add("CORRECTION_UNAVAILABLE");String body=(String)root.get("minutes_revision_id"),set=(String)root.get("element_set_id");if(body==null)blockers.add("BODY_REQUIRED");if(set==null)blockers.add("ELEMENT_SET_REQUIRED");if(((Number)root.get("needs_generation")).intValue()!=0)blockers.add("GENERATION_REQUIRED");if(root.get("material_snapshot_id")!=null||((Number)root.get("materials_pending")).intValue()!=0)blockers.add("MATERIALS_UNAVAILABLE");if(root.get("transcript_confirmation_id")==null)blockers.add("TRANSCRIPT_CONFIRMATION_REQUIRED");if(root.get("template_revision_id")==null||root.get("exclusion_revision_id")==null)blockers.add("SETTINGS_REQUIRED");if(((Number)root.get("ever_published")).intValue()!=0)blockers.add("PUBLISHED");if(!snapshot.graph().auxiliary().getOrDefault("publication",List.of()).isEmpty())blockers.add("PUBLICATION_BUSY");var infos=snapshot.graph().auxiliary().getOrDefault("meeting_info_revision",List.of()).stream().filter(r->Objects.equals(r.get("id"),root.get("info_revision_id"))).toList();if(infos.size()!=1||!(infos.getFirst().get("name") instanceof String name)||name.isBlank()||infos.getFirst().get("category_id")==null)blockers.add("MEETING_INFO_REQUIRED");var roster=snapshot.graph().auxiliary().getOrDefault("roster_revision",List.of()).stream().filter(r->Objects.equals(r.get("id"),root.get("roster_revision_id"))).toList();if(roster.size()!=1||!"CONFIRMED".equals(roster.getFirst().get("state")))blockers.add("ROSTER_CONFIRMATION_REQUIRED");
+ public Map<String,Object> readiness(Actor actor,String meeting){var snapshot=reader.readOwner(actor,meeting,"READINESS",null);var result=readiness(snapshot,false);var installation=transcripts==null?null:transcripts.correctionFor(authority);reader.recheck(actor,meeting,snapshot,transcripts,installation,installation!=null&&"NOT_APPLICABLE".equals(installation.projection().readiness()));return result;}
+ Map<String,Object> readiness(JdbcMinutesFactsReader.Snapshot snapshot,boolean finalInstalled){return readiness(snapshot,finalInstalled,null);}
+ Map<String,Object> replacementReadiness(JdbcMinutesFactsReader.Snapshot snapshot,String publication){var existing=snapshot.graph().auxiliary().getOrDefault("publication",List.of());require(existing.size()==1&&publication.equals(existing.getFirst().get("id"))&&"FAILED".equals(existing.getFirst().get("state")),PROVENANCE_UNAVAILABLE);return readiness(snapshot,true,publication);}
+ private Map<String,Object> readiness(JdbcMinutesFactsReader.Snapshot snapshot,boolean finalInstalled,String replacementPublication){var root=snapshot.observation();var blockers=new LinkedHashSet<String>();var installation=transcripts==null?null:transcripts.correctionFor(authority);var correction=installation==null?new CorrectionInstallation.Projection("UNAVAILABLE","UNAVAILABLE",null):installation.projection();boolean correctionReady="NOT_APPLICABLE".equals(correction.readiness());if(!correctionReady)blockers.add("CORRECTION_UNAVAILABLE");String body=(String)root.get("minutes_revision_id"),set=(String)root.get("element_set_id");if(body==null)blockers.add("BODY_REQUIRED");if(set==null)blockers.add("ELEMENT_SET_REQUIRED");if(((Number)root.get("needs_generation")).intValue()!=0)blockers.add("GENERATION_REQUIRED");if(root.get("material_snapshot_id")!=null||((Number)root.get("materials_pending")).intValue()!=0)blockers.add("MATERIALS_UNAVAILABLE");if(root.get("transcript_confirmation_id")==null)blockers.add("TRANSCRIPT_CONFIRMATION_REQUIRED");if(root.get("template_revision_id")==null||root.get("exclusion_revision_id")==null)blockers.add("SETTINGS_REQUIRED");if(((Number)root.get("ever_published")).intValue()!=0)blockers.add("PUBLISHED");if(replacementPublication==null&&!snapshot.graph().auxiliary().getOrDefault("publication",List.of()).isEmpty())blockers.add("PUBLICATION_BUSY");var infos=snapshot.graph().auxiliary().getOrDefault("meeting_info_revision",List.of()).stream().filter(r->Objects.equals(r.get("id"),root.get("info_revision_id"))).toList();if(infos.size()!=1||!(infos.getFirst().get("name") instanceof String name)||name.isBlank()||infos.getFirst().get("category_id")==null)blockers.add("MEETING_INFO_REQUIRED");var roster=snapshot.graph().auxiliary().getOrDefault("roster_revision",List.of()).stream().filter(r->Objects.equals(r.get("id"),root.get("roster_revision_id"))).toList();if(roster.size()!=1||!"CONFIRMED".equals(roster.getFirst().get("state")))blockers.add("ROSTER_CONFIRMATION_REQUIRED");
   if(body!=null){saved(snapshot,body,true);var input=one(snapshot,Table.GENERATION_INPUT,one(snapshot,Table.MINUTES_REVISION,body).string("generation_input_id"));for(String field:List.of("transcript_confirmation_id","template_revision_id","exclusion_revision_id"))if(!Objects.equals(input.nullableString(field),root.get(field)))blockers.add("SAVED_SOURCE_OR_SETTINGS_STALE");}
-  var required=new ArrayList<Map<String,Object>>();var ids=new ArrayList<String>();if(set!=null){saved(snapshot,set,false);for(var element:rows(snapshot,Table.MINUTES_ELEMENT).stream().filter(r->set.equals(r.string("set_id"))&&Set.of("CONCLUSION","ACTION").contains(r.string("category"))).sorted(Comparator.comparing((FullRow row)->Category.valueOf(row.string("category")).ordinal()).thenComparingLong(row->row.number("ordinal")).thenComparing(row->row.string("id"))).toList()){var kinds=element.string("category").equals("ACTION")?List.of(ReviewKind.SOURCE_SUPPORT,ReviewKind.ACTION_FIELDS):List.of(ReviewKind.SOURCE_SUPPORT);for(var kind:kinds){var coverage=coverage(snapshot,element,kind);required.add(coverage);if(Boolean.TRUE.equals(coverage.get("satisfied")))ids.add((String)coverage.get("reviewId"));else blockers.add("REVIEW_REQUIRED");}}var exclusion=(String)root.get("exclusion_revision_id");var excluded=new HashSet<List<Object>>();for(var row:rows(snapshot,Table.SENSITIVE_EXCLUSION))if(exclusion!=null&&exclusion.equals(row.string("revision_id")))excluded.add(physicalExclusion(snapshot,row.string("locator_id"),row.string("category")));for(var detection:rows(snapshot,Table.SENSITIVE_DETECTION))if(set.equals(detection.string("set_id")))for(var member:rows(snapshot,Table.SENSITIVE_DETECTION_SOURCE))if(detection.string("id").equals(member.string("detection_id"))&&!excluded.contains(physicalExclusion(snapshot,member.string("locator_id"),detection.string("category"))))blockers.add("SENSITIVITY_UNRESOLVED");}
-  var pending=snapshot.graph().discovery().pending();require(pending!=null,PROVENANCE_UNAVAILABLE);var candidates=pending.rows().get("generation_candidate");if(!candidates.isEmpty())blockers.add("PENDING_CANDIDATE");var outstanding=pending.rows().get("durable_task").stream().filter(row->Set.of("READY","RUNNING","RECONCILING").contains(row.get("state"))).map(row->row.get("id")).toList();if(!outstanding.isEmpty())blockers.add("OUTSTANDING_GENERATION");if(pending.rows().get("external_operation").stream().anyMatch(row->!Set.of("SUCCEEDED","FAILED","STALE").contains(row.get("state"))))blockers.add("OUTSTANDING_GENERATION");var latestOptional=new TreeMap<String,FullRow>();var currentElements=rows(snapshot,Table.MINUTES_ELEMENT).stream().filter(row->set!=null&&set.equals(row.string("set_id"))).map(row->row.string("id")).toList();for(var review:rows(snapshot,Table.RESULT_REVIEW)){if(!(body!=null&&body.equals(review.nullableString("minutes_revision_id"))||currentElements.contains(review.nullableString("element_id"))))continue;String group=review.string("review_kind")+"/"+review.nullableString("minutes_revision_id")+"/"+review.nullableString("element_id")+"/"+review.nullableString("target_anchor");var previous=latestOptional.get(group);if(previous==null||previous.number("review_no")<review.number("review_no"))latestOptional.put(group,review);}var optional=latestOptional.values().stream().map(row->row.string("id")).filter(id->!ids.contains(id)).toList();reader.recheck(actor,meeting,snapshot,transcripts,installation,correctionReady);boolean businessReady=blockers.isEmpty();blockers.add("TASK11_NOT_INSTALLED");return map("ready",false,"businessReady",businessReady,"correction",map("mode",correction.mode(),"readiness",correction.readiness(),"qualificationId",correction.qualificationId()),"required",required,"requiredReviewIds",ids,"optionalReviewIds",optional,"blockers",List.copyOf(blockers),"pendingCandidateIds",candidates.stream().map(row->row.get("id")).toList(),"outstandingTaskIds",outstanding,"observation",observation(snapshot));}
+  var required=new ArrayList<Map<String,Object>>();var ids=new ArrayList<String>();if(set!=null){saved(snapshot,set,false);for(var element:rows(snapshot,Table.MINUTES_ELEMENT).stream().filter(r->set.equals(r.string("set_id"))&&Set.of("CONCLUSION","ACTION").contains(r.string("category"))).sorted(Comparator.comparing((FullRow row)->Category.valueOf(row.string("category")).ordinal()).thenComparingLong(row->row.number("ordinal")).thenComparing(row->row.string("id"))).toList()){var kinds=element.string("category").equals("ACTION")?List.of(ReviewKind.SOURCE_SUPPORT,ReviewKind.ACTION_FIELDS):List.of(ReviewKind.SOURCE_SUPPORT);for(var kind:kinds){var coverage=coverage(snapshot,element,kind);required.add(coverage);if(Boolean.TRUE.equals(coverage.get("satisfied")))ids.add((String)coverage.get("reviewId"));else blockers.add("REVIEW_REQUIRED");}}var exclusion=(String)root.get("exclusion_revision_id");var excluded=new HashSet<List<Object>>();for(var row:rows(snapshot,Table.SENSITIVE_EXCLUSION))if(exclusion!=null&&exclusion.equals(row.string("revision_id")))excluded.add(physicalExclusion(snapshot,row.string("locator_id"),row.string("category")));for(var detection:rows(snapshot,Table.SENSITIVE_DETECTION))if(set.equals(detection.string("set_id")))for(var member:rows(snapshot,Table.SENSITIVE_DETECTION_SOURCE))if(detection.string("id").equals(member.string("detection_id"))&&!finalInstalled&&!excluded.contains(physicalExclusion(snapshot,member.string("locator_id"),detection.string("category"))))blockers.add("SENSITIVITY_UNRESOLVED");if(finalInstalled){var used=new HashSet<List<Object>>();for(var e:effectiveEvidence(snapshot,body,set))used.add(physicalExclusion(snapshot,e.string("locator_id"),""));var notes=rows(snapshot,Table.BACKGROUND_NOTE).stream().filter(n->set.equals(n.string("set_id"))).map(n->n.string("id")).toList();for(var source:rows(snapshot,Table.BACKGROUND_NOTE_SOURCE))if(notes.contains(source.string("note_id")))used.add(physicalExclusion(snapshot,source.string("locator_id"),""));for(var e:rows(snapshot,Table.SENSITIVE_EXCLUSION))if(exclusion!=null&&exclusion.equals(e.string("revision_id"))&&used.contains(physicalExclusion(snapshot,e.string("locator_id"),"")))blockers.add("EXCLUDED_SOURCE_USED");}}
+  var pending=snapshot.graph().discovery().pending();require(pending!=null,PROVENANCE_UNAVAILABLE);var candidates=pending.rows().get("generation_candidate");if(!candidates.isEmpty())blockers.add("PENDING_CANDIDATE");var outstanding=pending.rows().get("durable_task").stream().filter(row->Set.of("READY","RUNNING","RECONCILING").contains(row.get("state"))).map(row->row.get("id")).toList();if(!outstanding.isEmpty())blockers.add("OUTSTANDING_GENERATION");if(pending.rows().get("external_operation").stream().anyMatch(row->!Set.of("SUCCEEDED","FAILED","STALE").contains(row.get("state"))))blockers.add("OUTSTANDING_GENERATION");var latestOptional=new TreeMap<String,FullRow>();var currentElements=rows(snapshot,Table.MINUTES_ELEMENT).stream().filter(row->set!=null&&set.equals(row.string("set_id"))).map(row->row.string("id")).toList();for(var review:rows(snapshot,Table.RESULT_REVIEW)){if(!(body!=null&&body.equals(review.nullableString("minutes_revision_id"))||currentElements.contains(review.nullableString("element_id"))))continue;String group=review.string("review_kind")+"/"+review.nullableString("minutes_revision_id")+"/"+review.nullableString("element_id")+"/"+review.nullableString("target_anchor");var previous=latestOptional.get(group);if(previous==null||previous.number("review_no")<review.number("review_no"))latestOptional.put(group,review);}var optional=latestOptional.values().stream().filter(row->!finalInstalled||("SOURCE_SUPPORT".equals(row.string("review_kind"))?"SUPPORTED".equals(row.string("outcome")):"CONFIRMED".equals(row.string("outcome")))).map(row->row.string("id")).filter(id->!ids.contains(id)).toList();boolean businessReady=blockers.isEmpty();if(!finalInstalled)blockers.add("TASK11_NOT_INSTALLED");return map("ready",finalInstalled&&businessReady,"businessReady",businessReady,"correction",map("mode",correction.mode(),"readiness",correction.readiness(),"qualificationId",correction.qualificationId()),"required",required,"requiredReviewIds",ids,"optionalReviewIds",optional,"blockers",List.copyOf(blockers),"pendingCandidateIds",candidates.stream().map(row->row.get("id")).toList(),"outstandingTaskIds",outstanding,"observation",observation(snapshot));}
  private Map<String,Object> coverage(JdbcMinutesFactsReader.Snapshot snapshot,FullRow target,ReviewKind kind){var facts=snapshot.facts();String id=target.string("id");Anchor anchor=kind==ReviewKind.ACTION_FIELDS?null:OutputValidation.anchor(target.string("body"),0,OutputValidation.scalarCount(target.string("body"),()->false),()->false);var evidence=rows(snapshot,Table.EVIDENCE_LINK).stream().filter(row->id.equals(row.nullableString("element_id"))).map(row->row.string("id")).sorted().toList();var scope=new ReviewFacts(snapshot.graph().discovery().meeting(),kind,id,null,anchor,evidence,facts);var profiles=facts.configurations().stream().map(c->c.profiles().definition(ProfileKind.REVIEW_HASH)).distinct().toList();require(profiles.size()==1,PROFILE_UNAVAILABLE);var profile=profiles.getFirst();var archive=archives.get();require(pin.equals(archive.indexSha256()),PROFILE_UNAVAILABLE);var gb=new GraphBudget(authority.canonicalBudget(),limits.revisionDepth(),limits.revisionNodes(),authority.readBudget().maxFactRows());var refs=new GenerationArchiveRefs(facts,archive.index(),new StrictProcessingManifestReader(),authority.readBudget(),gb,()->false).review(scope,profile.version());var raw=MinutesStoredReviews.load(archive,refs,authority.readBudget(),snapshot.budget());var targetRaw=MinutesStoredReviews.load(archive,refs.stream().filter(ref->ref.artifactType()==ama.contract.ConfigArchiveContracts.ArtifactType.REVIEW_HASH).toList(),authority.readBudget(),snapshot.budget());var bindings=new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader());String th=new ResultHashProfiles(bindings,targetRaw,authority.readBudget()).target(scope,profile,gb,()->false),sh=new ResultHashProfiles(bindings,raw,authority.readBudget()).sources(scope,profile,gb,()->false);var credit=new ReviewCoverageEvaluator().evaluate(scope,th,sh,rows(snapshot,Table.RESULT_REVIEW),()->false);boolean meetingEvidence=rows(snapshot,Table.EVIDENCE_LINK).stream().filter(row->evidence.contains(row.string("id"))).anyMatch(row->"TRANSCRIPT".equals(one(snapshot,Table.SOURCE_LOCATOR,row.string("locator_id")).string("kind")));boolean satisfied=kind==ReviewKind.ACTION_FIELDS?"CONFIRMED".equals(credit.outcome()):credit.wholeTargetSupported()&&!evidence.isEmpty()&&meetingEvidence;return map("targetKind","ELEMENT","targetId",id,"reviewKind",kind.name(),"scope",kind==ReviewKind.SOURCE_SUPPORT?"WHOLE":null,"reviewId",credit.reviewId(),"outcome",credit.outcome(),"satisfied",satisfied);}
  private static List<Object> physicalExclusion(JdbcMinutesFactsReader.Snapshot snapshot,String locator,String category){var row=one(snapshot,Table.SOURCE_LOCATOR,locator);var value=new ArrayList<Object>();value.add(category);for(var column:Table.SOURCE_LOCATOR.columns())if(!Set.of("id","meeting_id").contains(column.name()))value.add(row.value(column.name()));return List.copyOf(value);}
  private static List<FullRow> effectiveEvidence(JdbcMinutesFactsReader.Snapshot snapshot,String body,String set){var values=new TreeMap<String,FullRow>();if(body!=null)for(var row:targetEvidence(snapshot,"BODY",body))values.put(row.string("id"),row);if(set!=null)for(var row:targetEvidence(snapshot,"SET",set))values.put(row.string("id"),row);return List.copyOf(values.values());}
  private static Set<String> directoryLocators(JdbcMinutesFactsReader.Snapshot snapshot,String set){var ids=new HashSet<String>();for(boolean sensitive:List.of(true,false)){var parents=rows(snapshot,sensitive?Table.SENSITIVE_DETECTION:Table.BACKGROUND_NOTE).stream().filter(row->set!=null&&set.equals(row.string("set_id"))).map(row->row.string("id")).toList();for(var member:rows(snapshot,sensitive?Table.SENSITIVE_DETECTION_SOURCE:Table.BACKGROUND_NOTE_SOURCE))if(parents.contains(member.string(sensitive?"detection_id":"note_id")))ids.add(member.string("locator_id"));}return Set.copyOf(ids);}
  private static List<Map<String,Object>> directories(JdbcMinutesFactsReader.Snapshot snapshot,String set,boolean sensitive){var result=new ArrayList<Map<String,Object>>();for(var row:rows(snapshot,sensitive?Table.SENSITIVE_DETECTION:Table.BACKGROUND_NOTE).stream().filter(value->set!=null&&set.equals(value.string("set_id"))).sorted(Comparator.comparing((FullRow value)->sensitive?value.string("category"):"").thenComparingLong(value->value.number("ordinal")).thenComparing(value->value.string("id"))).toList()){var locators=rows(snapshot,sensitive?Table.SENSITIVE_DETECTION_SOURCE:Table.BACKGROUND_NOTE_SOURCE).stream().filter(value->row.string("id").equals(value.string(sensitive?"detection_id":"note_id"))).map(value->value.string("locator_id")).sorted().toList();var value=new LinkedHashMap<String,Object>();value.put("id",row.string("id"));value.put("ordinalToken",Long.toString(row.number("ordinal")));value.put(sensitive?"category":"body",row.string(sensitive?"category":"body"));value.put("locatorIds",locators);result.add(Collections.unmodifiableMap(value));}return List.copyOf(result);}
  private static List<FullRow> targetEvidence(JdbcMinutesFactsReader.Snapshot s,String kind,String id){var elements=kind.equals("SET")?rows(s,Table.MINUTES_ELEMENT).stream().filter(r->id.equals(r.string("set_id"))).map(r->r.string("id")).toList():List.of();return rows(s,Table.EVIDENCE_LINK).stream().filter(r->kind.equals("BODY")?id.equals(r.nullableString("minutes_revision_id")):kind.equals("ELEMENT")?id.equals(r.nullableString("element_id")):elements.contains(r.nullableString("element_id"))).toList();}
  private static Map<String,Object> elements(JdbcMinutesFactsReader.Snapshot snapshot,String set,boolean owner){var result=new LinkedHashMap<String,Object>();for(Category category:Category.values()){var items=new ArrayList<Object>();for(var row:rows(snapshot,Table.MINUTES_ELEMENT).stream().filter(r->set!=null&&set.equals(r.string("set_id"))&&category.name().equals(r.string("category"))).sorted(Comparator.comparingLong(r->r.number("ordinal"))).toList()){var accepted=rows(snapshot,Table.ACTION_ITEM).stream().filter(r->row.string("id").equals(r.string("element_id"))).map(JdbcMinutesSavedQueries::project).toList();var suggestion=owner?rows(snapshot,Table.ACTION_SUGGESTION).stream().filter(r->row.string("id").equals(r.string("element_id"))).map(JdbcMinutesSavedQueries::project).toList():List.of();items.add(map("id",row.string("id"),"body",row.string("body"),"ordinalToken",Long.toString(row.number("ordinal")),"action",accepted.isEmpty()?null:accepted.getFirst(),"suggestion",suggestion.isEmpty()?null:suggestion.getFirst()));}result.put(category.name(),items);}return Collections.unmodifiableMap(result);}
  private static Map<String,Object> bodyProjection(FullRow row,boolean owner){var value=new LinkedHashMap<String,Object>();value.put("id",row.string("id"));value.put("title",row.string("title"));value.put("body",row.string("body"));value.put("titleOrigin",row.string("title_origin"));value.put("revisionToken",Long.toString(row.number("revision")));if(owner){value.put("generationInputId",row.nullableString("generation_input_id"));value.put("parentId",row.nullableString("parent_id"));}return Collections.unmodifiableMap(value);}
  private static void saved(JdbcMinutesFactsReader.Snapshot s,String id,boolean body){if(s.graph().auxiliary().getOrDefault("generation_candidate",List.of()).stream().anyMatch(r->id.equals(r.get(body?"minutes_revision_id":"element_set_id"))))throw absent();}
  private static ama.contract.TranscriptContracts.Failure absent(){return new ama.contract.TranscriptContracts.Failure(404,"SAVED_TARGET_NOT_FOUND");}
  private static List<FullRow> rows(JdbcMinutesFactsReader.Snapshot s,Table table){return s.facts().rows().stream().filter(r->r.table()==table).toList();}private static FullRow one(JdbcMinutesFactsReader.Snapshot s,Table table,String id){return JdbcMinutesFactsReader.one(s.graph(),table,id);}
  static long counter(String value){if(value==null||!value.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Canonical counter required");try{long n=Long.parseLong(value);if(n<0||n>Long.MAX_VALUE-1)throw new IllegalArgumentException("Counter out of range");return n;}catch(NumberFormatException e){throw new IllegalArgumentException("Counter out of range");}}
  static Map<String,Object> project(FullRow row){var result=new LinkedHashMap<String,Object>();for(var column:row.table().columns()){var value=row.value(column.name());result.put(column.kind()==CellKind.UINT?column.name()+"Token":column.name(),value instanceof NullCell?null:value instanceof UIntCell n?Long.toString(n.value()):((TextCell)value).value());}return Collections.unmodifiableMap(result);}
  private static Map<String,Object> observation(JdbcMinutesFactsReader.Snapshot s){var value=new LinkedHashMap<>(JdbcMinutesSettingsReceipts.observation(s.observation()));var root=s.observation();value.put("currentBodyId",root.get("minutes_revision_id"));value.put("currentSetId",root.get("element_set_id"));value.put("lifecycle",root.get("lifecycle"));value.put("templateRevisionId",root.get("template_revision_id"));value.put("exclusionRevisionId",root.get("exclusion_revision_id"));value.put("needsGeneration",((Number)root.get("needs_generation")).intValue()!=0);value.put("readOnlyReason",((Number)root.get("ever_published")).intValue()!=0?"PUBLISHED":null);return Collections.unmodifiableMap(value);}static Map<String,Object> map(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int n=0;n<pairs.length;n+=2)result.put((String)pairs[n],pairs[n+1]);return Collections.unmodifiableMap(result);}
 }

## backend/adapters/src/main/java/ama/transcript/JdbcPublicationLocalRecovery.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/JdbcPublicationLocalRecovery.java
@@ -0,0 +1,46 @@
+package ama.transcript;
+import ama.access.MeetingAccess;import ama.contract.IdentityContracts.*;import ama.contract.TaskContracts.*;import ama.storage.FileRegistry;import ama.task.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.support.TransactionSynchronizationManager;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
+/** DB-only owner local recovery. Construction and replay do not need a provider client or archive. */
+public final class JdbcPublicationLocalRecovery {
+ final JdbcTranscriptAuthority authority;final TranscriptAcquisitionBudget limits;final JdbcTaskQueue queue;final JdbcOutbox outbox;final JdbcTemplate j;
+ public JdbcPublicationLocalRecovery(JdbcTranscriptAuthority authority,TranscriptAcquisitionBudget limits,JdbcTaskQueue queue,JdbcOutbox outbox){this.authority=Objects.requireNonNull(authority);this.limits=Objects.requireNonNull(limits);this.queue=Objects.requireNonNull(queue);this.outbox=Objects.requireNonNull(outbox);j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);authority.publicationDomain().installRecovery(this);}
+ record Plan(Map<String,Object> root,List<Map<String,Object>> publications,List<Map<String,Object>> finals,List<Map<String,Object>> members,PublicationGraph.Plan graph,Set<String> identities){}
+ static final class Prepared implements PublicationLocalTerminationPolicy.PreparedLocalRecovery {
+  final JdbcPublicationLocalRecovery issuer;final Actor actor;final String meeting,task;final TaskSpec spec;final Plan plan;final Object holder,scope;final FileRegistry.LockedMeeting gate;TaskDomainPolicy.PreparedDomain held;boolean admitted,closed;
+  private Prepared(JdbcPublicationLocalRecovery issuer,Actor actor,String meeting,String task,TaskSpec spec,Plan plan,Object scope,FileRegistry.LockedMeeting gate){this.issuer=issuer;this.actor=actor;this.meeting=meeting;this.task=task;this.spec=spec;this.plan=plan;this.scope=scope;this.gate=gate;holder=TransactionSynchronizationManager.getResource(issuer.authority.dataSource());}
+  public TaskSpec spec(){return spec;}
+ }
+ Prepared requireCarrier(PublicationLocalTerminationPolicy.PreparedLocalRecovery value){if(!(value instanceof Prepared p)||p.issuer!=this||p.closed||p.holder==null||!TransactionSynchronizationManager.isActualTransactionActive()||p.holder!=TransactionSynchronizationManager.getResource(authority.dataSource()))throw new IllegalArgumentException("Foreign/expired explicit local recovery purpose");authority.requireMinutesScope(p.scope,p.gate,p.actor,p.meeting);return p;}
+ Plan discover(String meeting){var root=j.queryForMap("SELECT id,owner_id,revision,cancel_epoch,stage,lifecycle,governance_state,ever_published,final_confirmation_id FROM meeting WHERE id=?",meeting);var publications=publications(meeting,false);var finals=finals(meeting,false);var members=members(meeting,false);var graph=PublicationGraph.discover(authority.dataSource(),meeting);require(graph.rows().get("durable_task").size()<=limits.tasks(),BUDGET_EXCEEDED);var ids=new TreeSet<String>();ids.add((String)root.get("owner_id"));for(var row:finals)ids.add((String)row.get("confirmed_by"));for(var row:graph.rows().get("publication_submission")){ids.add((String)row.get("requester_id"));if(row.get("local_decided_by") instanceof String actor)ids.add(actor);}return new Plan(Collections.unmodifiableMap(root),publications,finals,members,graph,Set.copyOf(ids));}
+ private List<Map<String,Object>> publications(String meeting,boolean lock){var rows=j.queryForList("SELECT id,meeting_id,confirmation_id,business_key,library_id,folder_id,state,revision FROM publication WHERE meeting_id=? ORDER BY id LIMIT 2"+(lock?" FOR UPDATE":""),meeting);require(rows.size()<=1,PROVENANCE_UNAVAILABLE);return rows;}
+ private List<Map<String,Object>> finals(String meeting,boolean lock){var rows=j.queryForList("SELECT id,meeting_id,source_manifest_hash,input_fingerprint,confirmed_by FROM final_confirmation WHERE meeting_id=? ORDER BY id LIMIT 257"+(lock?" FOR UPDATE":""),meeting);require(rows.size()<=256,BUDGET_EXCEEDED);return rows;}
+ private List<Map<String,Object>> members(String meeting,boolean lock){var rows=j.queryForList("SELECT confirmation_id,review_id FROM final_result_review WHERE confirmation_id IN (SELECT id FROM final_confirmation WHERE meeting_id=?) ORDER BY confirmation_id,review_id LIMIT 257"+(lock?" FOR UPDATE":""),meeting);require(rows.size()<=256,BUDGET_EXCEEDED);return rows;}
+ public Map<String,Object> recover(Actor actor,String meeting,String publication,String task,String key,long expected,long expectedPublication,String trace){return recover(actor,meeting,publication,task,key,expected,expectedPublication,trace,false);}
+ private Map<String,Object> recover(Actor actor,String meeting,String publication,String task,String key,long expected,long expectedPublication,String trace,boolean terminalOnly){
+  JdbcMinutesFactsReader.independent();uuid(meeting);uuid(publication);uuid(task);uuid(trace);JdbcProcessingIntake.key(key);String scope=JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,"PUBLICATION_LOCAL_RECOVERY",key),hash=JdbcProcessingIntake.digest(publication,task,Long.toString(expected),Long.toString(expectedPublication));var replay=receipt(actor,meeting,scope,hash);if(replay!=null)return replay;var plan=discover(meeting);
+  try{return authority.transactionRunner().required(()->{
+   j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'PUBLICATION_LOCAL_RECOVERY',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",scope,actor.enterpriseId(),meeting,JdbcProcessingIntake.digest(key),hash);var command=j.queryForMap("SELECT actor_id,resource_id,request_hash,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);
+   var access=(ama.meeting.JdbcMeetingAccess)authority.meetingAccess();var ids=access.tryLockHistoricalIdentities(actor,plan.identities(),false).orElseThrow();new JdbcSubjectFences(authority.dataSource(),Set.of("MEETING")).lock(new SubjectRef("MEETING",meeting),false).orElseThrow();var root=access.lockPrepared(ids,meeting,false).orElseThrow();access.requirePrepared(root,actor,meeting,plan.identities(),MeetingAccess.Action.EDIT);var gate=authority.fileRegistry().adopt(actor,meeting,root,MeetingAccess.Action.EDIT);var owned=authority.minutesScope(gate,actor,meeting);
+   if(!actor.enterpriseId().equals(command.get("actor_id"))||!meeting.equals(command.get("resource_id"))||!hash.equals(command.get("request_hash")))throw new ama.contract.TranscriptContracts.Failure(409,"IDEMPOTENCY_CONFLICT");if(command.get("result_id")!=null)return acknowledgement(meeting,(String)command.get("result_id"),true);
+   var actual=j.queryForMap("SELECT id,owner_id,revision,cancel_epoch,stage,lifecycle,governance_state,ever_published,final_confirmation_id FROM meeting WHERE id=?",meeting);require(actual.equals(plan.root()),PROVENANCE_UNAVAILABLE);require(publications(meeting,true).equals(plan.publications())&&finals(meeting,true).equals(plan.finals())&&members(meeting,true).equals(plan.members()),PROVENANCE_UNAVAILABLE);PublicationGraph.lock(authority.dataSource(),plan.graph(),true);PublicationGraph.lock(authority.dataSource(),plan.graph(),false);
+   var submissions=plan.graph().rows().get("publication_submission").stream().filter(s->task.equals(s.get("task_id"))&&publication.equals(s.get("publication_id"))).toList();require(submissions.size()==1,PROVENANCE_UNAVAILABLE);var rows=plan.graph().rows().get("durable_task").stream().filter(t->task.equals(t.get("id"))).toList();require(rows.size()==1,PROVENANCE_UNAVAILABLE);if(terminalOnly&&!Set.of("FAILED","CANCELLED","STALE").contains(rows.getFirst().get("state")))throw new PublicationLocalRecoveryUnavailable("Maintenance requires actual terminal task");var spec=TranscriptReadGraph.spec(rows.getFirst());PublicationTaskDomainPolicy.shape(spec);require(spec.input().id().equals(publication)&&spec.subject().id().equals(meeting),PROVENANCE_UNAVAILABLE);
+   if(!"NEVER_MARKED".equals(submissions.getFirst().get("local_state"))){if(((Number)actual.get("revision")).longValue()!=expected||plan.publications().size()!=1||((Number)plan.publications().getFirst().get("revision")).longValue()!=expectedPublication)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");require("ACTIVE".equals(actual.get("lifecycle"))&&"ACTIVE".equals(actual.get("governance_state"))&&((Number)actual.get("ever_published")).intValue()==0,CAPABILITY_UNAVAILABLE);var carrier=new Prepared(this,actor,meeting,task,spec,plan,owned,gate);try{carrier.held=authority.publicationDomain().adoptLocalRecovery(carrier);require(queue.recoverPublicationLocal(carrier).equals(task),PROVENANCE_UNAVAILABLE);}finally{carrier.closed=true;}outbox.append(new ama.contract.OutboxContracts.EventDraft("PUBLICATION_LOCAL_RECOVERED",task,spec.subject(),spec.businessVersion(),new ama.contract.OutboxContracts.Reference("PUBLICATION",publication)));}
+   require(j.update("UPDATE idempotency_record SET result_id=?,http_status=200 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",task,scope)==1,PROVENANCE_UNAVAILABLE);outbox.flush();return acknowledgement(meeting,task,false);
+  });}catch(PublicationLocalRecoveryUnavailable unavailable){throw new ama.contract.TranscriptContracts.Failure(409,"PUBLICATION_LOCAL_RECOVERY_UNAVAILABLE");}
+ }
+ private String maintenanceAfterTime,maintenanceAfterTask;private long maintenanceNextNanos;
+ public record MaintenanceResult(int scanned,int attempted,int recovered,boolean endOfPass){}
+ /** Existing reconciliation cadence only; discovery grants no authority and never abandons live work. */
+ public synchronized MaintenanceResult maintainLocalFailures(){
+  JdbcMinutesFactsReader.independent();long now=System.nanoTime();if(now<maintenanceNextNanos)return new MaintenanceResult(0,0,0,false);maintenanceNextNanos=now+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
+  String cursor=maintenanceAfterTime==null?"":" AND (s.created_at>? OR (s.created_at=? AND s.task_id>?))";Object[] args=maintenanceAfterTime==null?new Object[0]:new Object[]{maintenanceAfterTime,maintenanceAfterTime,maintenanceAfterTask};
+  var candidates=j.queryForList("SELECT s.task_id,s.publication_id,CAST(s.created_at AS CHAR) created_at,t.state,m.id meeting_id,m.owner_id,m.revision,p.revision publication_revision,i.status FROM publication_submission s JOIN durable_task t ON t.id=s.task_id JOIN publication p ON p.id=s.publication_id JOIN meeting m ON m.id=p.meeting_id JOIN enterprise_identity i ON i.enterprise_id=m.owner_id WHERE s.local_state='UNDECIDED'"+cursor+" ORDER BY s.created_at,s.task_id LIMIT 64",args);
+  int scanned=0,attempted=0,recovered=0;for(var candidate:candidates){if(attempted==16)break;scanned++;maintenanceAfterTime=(String)candidate.get("created_at");maintenanceAfterTask=(String)candidate.get("task_id");if(!Set.of("FAILED","CANCELLED","STALE").contains(candidate.get("state"))||!"ACTIVE".equals(candidate.get("status")))continue;attempted++;
+   try{var actor=new Actor((String)candidate.get("owner_id"),Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));recover(actor,(String)candidate.get("meeting_id"),(String)candidate.get("publication_id"),(String)candidate.get("task_id"),"local-maintenance:"+candidate.get("task_id"),((Number)candidate.get("revision")).longValue(),((Number)candidate.get("publication_revision")).longValue(),UUID.randomUUID().toString(),true);recovered++;}
+   catch(RuntimeException unavailable){/* Full fresh acquisition rolled back; next candidate advances fairly. */}
+  }
+  boolean end=scanned==candidates.size()&&candidates.size()<64;if(end){maintenanceAfterTime=null;maintenanceAfterTask=null;}return new MaintenanceResult(scanned,attempted,recovered,end);
+ }
+ private Map<String,Object> receipt(Actor actor,String meeting,String scope,String hash){return authority.transactionRunner().required(()->{var rows=j.queryForList("SELECT actor_id,resource_id,request_hash,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),MeetingAccess.Action.EDIT);if(rows.isEmpty())return null;var r=rows.getFirst();if(!actor.enterpriseId().equals(r.get("actor_id"))||!meeting.equals(r.get("resource_id"))||!hash.equals(r.get("request_hash")))throw new ama.contract.TranscriptContracts.Failure(409,"IDEMPOTENCY_CONFLICT");require(r.get("result_id")!=null&&((Number)r.get("http_status")).intValue()==200,PROVENANCE_UNAVAILABLE);return acknowledgement(meeting,(String)r.get("result_id"),true);});}
+ private Map<String,Object> acknowledgement(String meeting,String task,boolean replay){var rows=j.queryForList("SELECT s.task_id,s.publication_id,s.submission_no,s.confirmation_id,s.provider_id,s.library_id,s.folder_id,s.local_state,s.local_reason,s.local_decided_by,s.local_attempt_no,s.local_execution_id,CAST(s.local_decided_at AS CHAR) local_decided_at FROM publication_submission s JOIN publication p ON p.id=s.publication_id WHERE s.task_id=? AND p.meeting_id=? AND s.local_state='NEVER_MARKED'",task,meeting);require(rows.size()==1,PROVENANCE_UNAVAILABLE);return JdbcMinutesSavedQueries.map("taskId",task,"decision",rows.getFirst(),"httpStatus",200,"replayed",replay,"observation",JdbcMinutesCommandReceipts.observation(j,meeting));}
+}

## backend/adapters/src/main/java/ama/transcript/JdbcPublicationReplacement.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/JdbcPublicationReplacement.java
@@ -0,0 +1,43 @@
+package ama.transcript;
+import ama.access.MeetingAccess;import ama.contract.IdentityContracts.Actor;import ama.contract.TaskContracts.*;import ama.contract.ExternalContracts.*;import ama.task.*;import ama.storage.FileRegistry;import java.util.*;import java.time.Instant;import org.springframework.transaction.support.TransactionSynchronizationManager;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
+/** Registered assembler for complete closed-history publication replacement. No external send here. */
+final class JdbcPublicationReplacement {
+ final JdbcPublicationService service;final JdbcTranscriptAuthority authority;
+ JdbcPublicationReplacement(JdbcPublicationService service){this.service=service;authority=service.authority;authority.publicationDomain().installReplacement(this);}
+ record Proof(String execution,String operation,long number,String manifest,NoEffectProof admitted,Map<String,Object> reference,long generation){}
+ record Assembly(JdbcPublicationService.Prepared plan,Map<String,Object> publication,Map<String,String> manifests,List<Proof> proofs,Map<String,Object> transition,String task,String execution,String operation,long previousNo,String previousLogical,Map<String,TaskSpec> oldTasks,List<TaskSpec> specs,TranscriptAcquisitionBudget.Ledger budget){}
+ static final class Prepared implements PublicationReplacementPolicy.PreparedPublicationReplacement {
+  final JdbcPublicationReplacement issuer;final Assembly assembly;final Object holder,scope;final FileRegistry.LockedMeeting gate;TaskDomainPolicy.PreparedDomain domain;boolean taskPhase,admitted,closed;
+  private Prepared(JdbcPublicationReplacement issuer,Assembly assembly,Object scope,FileRegistry.LockedMeeting gate){this.issuer=issuer;this.assembly=assembly;this.scope=scope;this.gate=gate;holder=TransactionSynchronizationManager.getResource(issuer.authority.dataSource());}
+  public TaskSpec spec(){return assembly.plan().spec();}
+ }
+ Prepared requireCarrier(PublicationReplacementPolicy.PreparedPublicationReplacement value){if(!(value instanceof Prepared p)||p.issuer!=this||p.closed||p.holder==null||p.holder!=TransactionSynchronizationManager.getResource(authority.dataSource())||!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalArgumentException("Foreign or expired publication replacement");authority.requireMinutesScope(p.scope,p.gate,p.assembly.plan().actor(),p.spec().subject().id());return p;}
+ void checkRemainder(Prepared carrier){requireCarrier(carrier);var a=carrier.assembly;PublicationGraph.lockReplacement(authority.dataSource(),a.plan().publicationGraph(),a.task());PublicationLocalDecision.requireClosed(a.plan().publicationGraph(),a.plan().publication());require(manifests(a.plan().publicationGraph(),a.budget()).equals(a.manifests()),PROVENANCE_UNAVAILABLE);exactProofs(a.proofs());}
+ private Map<String,Object> publication(String meeting,String id,boolean lock){var rows=service.j.queryForList("SELECT id,meeting_id,confirmation_id,business_key,library_id,folder_id,state,revision FROM publication WHERE meeting_id=? AND id=?"+(lock?" FOR UPDATE":""),meeting,id);require(rows.size()==1,PROVENANCE_UNAVAILABLE);return Collections.unmodifiableMap(rows.getFirst());}
+ private Map<String,String> manifests(PublicationGraph.Plan graph,TranscriptAcquisitionBudget.Ledger budget){var result=new TreeMap<String,String>();for(var row:graph.rows().get("publication_submission")){long b=((Number)row.get("request_manifest_bytes")).longValue(),p=((Number)row.get("request_manifest_scalars")).longValue(),u=((Number)row.get("request_manifest_utf16")).longValue();require(b>0&&b<=Math.min(65536,authority.readBudget().maxManifestBytes()),BUDGET_EXCEEDED);budget.text(b,p,u,3,false);String text=service.j.queryForObject("SELECT request_manifest FROM publication_submission WHERE task_id=? AND OCTET_LENGTH(request_manifest)=? AND CHAR_LENGTH(request_manifest)=? AND OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2=?",String.class,row.get("task_id"),b,p,u);require(text!=null,PROVENANCE_UNAVAILABLE);result.put((String)row.get("task_id"),text);}return Map.copyOf(result);}
+ private Map<String,Object> parseManifest(String text,TranscriptAcquisitionBudget.Ledger budget){long b=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,p=text.codePointCount(0,text.length()),u=text.length();budget.capture(b,p,u,8);budget.admitCapture(u*6,p,u*6,2);var fields=ControlledKnowledgeClient.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),65536);byte[] canonical=ControlledKnowledgeClient.canonical(fields);budget.capture(canonical.length,canonical.length,canonical.length,2);require(Arrays.equals(canonical,text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),PROVENANCE_UNAVAILABLE);return fields;}
+ private void validateManifests(PublicationGraph.Plan graph,Map<String,String> manifests,TranscriptAcquisitionBudget.Ledger budget){for(var row:graph.rows().get("publication_submission")){String id=(String)row.get("task_id");var fields=parseManifest(manifests.get(id),budget);var tasks=graph.rows().get("durable_task").stream().filter(t->id.equals(t.get("id"))).toList();require(tasks.size()==1&&JdbcPublicationService.spec(fields).equals(TranscriptReadGraph.spec(tasks.getFirst())),PROVENANCE_UNAVAILABLE);for(var pair:Map.of("publicationId","publication_id","confirmationId","confirmation_id","providerId","provider_id","businessKey","business_key","libraryId","library_id","folderId","folder_id","requesterId","requester_id","logicalRequestHash","logical_request_hash","executionRequestHash","execution_request_hash").entrySet())require(Objects.equals(fields.get(pair.getKey()),row.get(pair.getValue())),PROVENANCE_UNAVAILABLE);require(((Number)fields.get("submissionNo")).longValue()==((Number)row.get("submission_no")).longValue(),PROVENANCE_UNAVAILABLE);for(var pin:service.client.identities().entrySet())require(pin.getValue().equals(fields.get(pin.getKey())),PROVENANCE_UNAVAILABLE);}}
+ private void exactProofs(List<Proof> proofs){for(var proof:proofs)require(service.j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE id=? AND operation_id=? AND execution_no=? AND state='FAILED' AND no_effect_basis=? AND no_effect_evidence=? AND no_effect_verified_at=?",Long.class,proof.execution(),proof.operation(),proof.number(),proof.admitted().basis().name(),proof.admitted().safeEvidenceReference(),java.time.LocalDateTime.ofInstant(proof.admitted().verifiedAt(),java.time.ZoneOffset.UTC))==1,PROVENANCE_UNAVAILABLE);}
+ Map<String,Object> accept(Actor actor,String meeting,String publication,String key,long expected,long expectedPublication,String confirmation,String provider,String library,String folder,String trace)throws java.io.IOException {
+  final String[] phase={"INPUT"};try{
+  JdbcMinutesFactsReader.independent();uuid(meeting);uuid(publication);uuid(confirmation);uuid(trace);JdbcProcessingIntake.key(key);if(service.transcripts==null)throw new IllegalStateException("Replacement command runtime unavailable");require(service.client.identities().get("providerId").equals(provider)&&library!=null&&folder!=null&&!library.isBlank()&&!folder.isBlank()&&library.codePointCount(0,library.length())<=255&&folder.codePointCount(0,folder.length())<=255,INVALID_ARGUMENT);
+  String scope=JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,"PUBLICATION_RESUBMIT",key),request=JdbcProcessingIntake.digest(publication,Long.toString(expected),Long.toString(expectedPublication),confirmation,provider,library,folder);var replay=receipt(actor,meeting,scope,request);if(replay!=null)return replay;
+  phase[0]="DISCOVERY";var budget=service.limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);var graph=PublicationGraph.discover(authority.dataSource(),meeting);PublicationGraph.reserve(graph.rows(),true);PublicationLocalDecision.requireClosed(graph,publication);var header=publication(meeting,publication,false);require("FAILED".equals(header.get("state")),CAPABILITY_UNAVAILABLE);if(((Number)header.get("revision")).longValue()!=expectedPublication)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");
+  var identities=new TreeSet<String>();for(var row:graph.rows().get("publication_submission")){identities.add((String)row.get("requester_id"));if(row.get("local_decided_by") instanceof String id)identities.add(id);}var declaration=MinutesGraphDiscovery.withPending(authority.dataSource(),MinutesGraphDiscovery.identities(MinutesGraphDiscovery.publicationHistory(authority.dataSource(),meeting,confirmation,service.limits,authority.readBudget()),identities),service.limits.tasks());JdbcMinutesFactsReader.charge(declaration,budget);var acceptedManifests=new java.util.concurrent.atomic.AtomicReference<Map<String,String>>();
+  phase[0]="CAPTURE";var captured=authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);var cap=authority.minutesScope(gate,actor,meeting);require(publication(meeting,publication,true).equals(header),PROVENANCE_UNAVAILABLE);var actual=MinutesGraphCapture.captureReplacement(authority,cap,gate,actor,declaration,budget,publication,()->PublicationGraph.lock(authority.dataSource(),graph,true),()->PublicationGraph.lock(authority.dataSource(),graph,false));JdbcPublicationService.current(actual,expected,confirmation);PublicationLocalDecision.requireClosed(graph,publication);acceptedManifests.set(manifests(graph,budget));return actual;});
+  phase[0]="MANIFESTS";validateManifests(graph,acceptedManifests.get(),budget);phase[0]="PROVENANCE";var facts=service.reader.prepare(captured,budget);phase[0]="READINESS";require(Boolean.TRUE.equals(service.queries.replacementReadiness(facts,publication).get("ready")),CAPABILITY_UNAVAILABLE);phase[0]="RENDER";var payload=service.render(facts,confirmation);String payloadHash=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(payload));var finalRow=JdbcMinutesFactsReader.one(captured,ama.contract.MinutesContracts.Table.FINAL_CONFIRMATION,confirmation);String business=publication+":"+confirmation;
+  phase[0]="PROOFS";var proofs=new ArrayList<Proof>();for(var e:graph.rows().get("external_execution")){String task=(String)e.get("task_id");if(!acceptedManifests.get().containsKey(task)||e.get("marked_at")==null)continue;var reference=new PublicationRecordedLookup.Reference((String)e.get("operation_id"),(String)e.get("id"),((Number)e.get("execution_no")).longValue(),new SubjectRef("MEETING",meeting));var original=PublicationRecordedLookup.captureReplacement(service,reference,actor,publication,budget).identity();require(original.manifest().equals(acceptedManifests.get().get(task)),PROVENANCE_UNAVAILABLE);var receipt=service.replacementProof(original,reference.executionId(),reference.executionNo());var admitted=service.validateRejection(original,reference.executionId(),reference.executionNo(),receipt);var proofReference=Map.<String,Object>of("executionId",reference.executionId(),"executionRequestHash",original.executionHash(),"receiptId",receipt.get("receiptId"),"proofDigest",ControlledKnowledgeClient.digest(((String)receipt.get("permanentRejection")).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));proofs.add(new Proof(reference.executionId(),reference.operationId(),reference.executionNo(),original.manifest(),admitted,proofReference,((Number)receipt.get("slotGeneration")).longValue()));}
+  proofs.sort(Comparator.comparing(Proof::execution));exactProofs(proofs);var prior=proofs.stream().map(Proof::reference).toList();var matching=graph.rows().get("external_operation").stream().filter(o->"KNOWLEDGE_PUBLISH".equals(o.get("operation_kind"))&&business.equals(o.get("business_key"))).toList();require(matching.size()<=1,PROVENANCE_UNAVAILABLE);String operation=matching.isEmpty()?null:(String)matching.getFirst().get("id");long previousNo=matching.isEmpty()?0:((Number)matching.getFirst().get("current_execution_no")).longValue();require(previousNo<Long.MAX_VALUE-1,BUDGET_EXCEEDED);long number=previousNo+1,submission=graph.rows().get("publication_submission").size()+1L;require(submission<=Math.min(256,service.limits.tasks()),BUDGET_EXCEEDED);
+  var logical=new TreeMap<String,Object>();logical.put("schema","ama.knowledge-publish.v1");logical.put("domain","KNOWLEDGE_PUBLISH");logical.put("providerId",provider);logical.put("publicationId",publication);logical.put("confirmationId",confirmation);logical.put("businessKey",business);logical.put("libraryId",library);logical.put("folderId",folder);logical.put("sourceManifestHash",finalRow.string("source_manifest_hash"));logical.put("inputFingerprint",finalRow.string("input_fingerprint"));logical.put("renderArtifact",((Map<?,?>)payload.get("provenance")).get("renderArtifact"));logical.put("payloadSha256",payloadHash);String logicalHash=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(logical));String executionHash=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(Map.of("schema","ama.knowledge-publish-execution.v1","logicalRequestHash",logicalHash,"executionNo",number,"priorProofs",prior)));
+  Map<String,Object> transition=proofs.isEmpty()?null:Map.of("schema","ama.controlled-kb-binding-transition.v1","expectedGeneration",proofs.stream().mapToLong(Proof::generation).max().orElseThrow(),"priorProofs",prior,"newBusinessKey",business,"newLogicalRequestHash",logicalHash);var manifest=new TreeMap<String,Object>();manifest.put("schema","ama.knowledge-publication-manifest.v1");manifest.putAll(service.client.identities());manifest.put("publicationId",publication);manifest.put("confirmationId",confirmation);manifest.put("requesterId",actor.enterpriseId());manifest.put("businessKey",business);manifest.put("libraryId",library);manifest.put("folderId",folder);manifest.put("logicalRequestHash",logicalHash);manifest.put("executionRequestHash",executionHash);manifest.put("payloadSha256",payloadHash);manifest.put("archiveIndexSha256",service.pin);manifest.put("renderArtifact",logical.get("renderArtifact"));manifest.put("meetingId",meeting);manifest.put("businessVersion",expected);manifest.put("cancelEpoch",((Number)captured.discovery().root().get("cancel_epoch")).longValue());manifest.put("executionNo",number);manifest.put("submissionNo",submission);manifest.put("priorProofs",prior);manifest.put("bindingTransition",transition);byte[] manifestBytes=ControlledKnowledgeClient.canonical(manifest);require(manifestBytes.length<=Math.min(65536,authority.readBudget().maxManifestBytes()),BUDGET_EXCEEDED);budget.capture(manifestBytes.length,manifestBytes.length,manifestBytes.length,4);String wire=new String(manifestBytes,java.nio.charset.StandardCharsets.UTF_8);var next=JdbcPublicationService.spec(manifest);
+  phase[0]="DESTINATION";var destination=service.destination(service.authorize(actor,meeting,"DESTINATION_AUTHORIZE"),publication,provider,library,folder);var prepared=new JdbcPublicationService.Prepared(actor,declaration,captured,facts,graph,next,publication,confirmation,provider,library,folder,business,logicalHash,executionHash,wire,payload,payloadHash,destination.token(),number,submission);var allTasks=new TreeMap<String,TaskSpec>();for(var task:graph.rows().get("durable_task"))allTasks.put((String)task.get("id"),TranscriptReadGraph.spec(task));var specs=new ArrayList<TaskSpec>();for(var spec:allTasks.values())if(spec.type().equals("KNOWLEDGE_PUBLISH_V1"))specs.add(spec);specs.add(next);String newTask=UUID.randomUUID().toString(),newExecution=operation==null?null:UUID.randomUUID().toString();var assembly=new Assembly(prepared,header,acceptedManifests.get(),List.copyOf(proofs),transition,newTask,newExecution,operation,previousNo,operation==null?null:(String)matching.getFirst().get("request_hash"),Map.copyOf(allTasks),List.copyOf(specs),budget);
+  phase[0]="FINAL_ACQUISITION";return authority.transactionRunner().required(()->{
+   service.j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'PUBLICATION_RESUBMIT',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",scope,actor.enterpriseId(),meeting,JdbcProcessingIntake.digest(key),request);var command=service.j.queryForMap("SELECT actor_id,resource_id,request_hash,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);require(actor.enterpriseId().equals(command.get("actor_id"))&&meeting.equals(command.get("resource_id"))&&request.equals(command.get("request_hash")),PROVENANCE_UNAVAILABLE);if(command.get("result_id")!=null)return service.acknowledgement(meeting,(String)command.get("result_id"),true);var cap=authority.minutesScope(gate,actor,meeting);require(publication(meeting,publication,true).equals(header),PROVENANCE_UNAVAILABLE);var carrier=new Prepared(this,assembly,cap,gate);var accepted=new java.util.concurrent.atomic.AtomicReference<PublicationReplacementPolicy.ReplacementAccepted>();
+   try{var fresh=MinutesGraphCapture.captureReplacement(authority,cap,gate,actor,declaration,budget,publication,()->PublicationGraph.lock(authority.dataSource(),graph,true),()->{phase[0]="ENGINE_ACCEPTANCE";carrier.taskPhase=true;carrier.domain=authority.publicationDomain().adoptReplacement(carrier);accepted.set(new JdbcExternalOperations(service.queue).replaceZeroEffectPublication(carrier));carrier.taskPhase=false;});phase[0]="FINAL_COMPARE";require(fresh.equals(captured),PROVENANCE_UNAVAILABLE);JdbcPublicationService.current(fresh,expected,confirmation);require(destination.expires()>=Instant.now().getEpochSecond(),CAPABILITY_UNAVAILABLE);service.transcripts.requireCorrection(authority,service.transcripts.correctionFor(authority),true);require(manifests(graph,budget).equals(assembly.manifests()),PROVENANCE_UNAVAILABLE);exactProofs(proofs);
+    phase[0]="FINAL_WRITE";require(service.j.update("INSERT INTO publication_submission(task_id,publication_id,submission_no,confirmation_id,provider_id,business_key,library_id,folder_id,requester_id,logical_request_hash,execution_request_hash,request_manifest,created_at,local_state) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),'UNDECIDED')",newTask,publication,submission,confirmation,provider,business,library,folder,actor.enterpriseId(),logicalHash,executionHash,wire)==1,PROVENANCE_UNAVAILABLE);require(service.j.update("UPDATE publication SET confirmation_id=?,business_key=?,library_id=?,folder_id=?,state='READY',revision=revision+1 WHERE id=? AND revision=? AND state='FAILED' AND revision<9223372036854775806",confirmation,business,library,folder,publication,expectedPublication)==1,PROVENANCE_UNAVAILABLE);require(service.j.update("UPDATE idempotency_record SET result_id=?,http_status=202 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",newTask,scope)==1,PROVENANCE_UNAVAILABLE);new ama.audit.JdbcAuditAdapter(authority.dataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),"PUBLICATION_RESUBMITTED",meeting,expected,"SUCCEEDED",trace,Instant.now()));service.outbox.append(new ama.contract.OutboxContracts.EventDraft("PUBLICATION_ACCEPTED",newTask,next.subject(),expected,new ama.contract.OutboxContracts.Reference("PUBLICATION",publication)));service.outbox.flush();return service.acknowledgement(meeting,accepted.get().taskId(),false);
+   }finally{carrier.closed=true;}
+  });
+  }catch(RuntimeException failure){String code=failure instanceof ama.contract.MinutesContracts.Failure f?f.code().name():failure.getClass().getSimpleName();System.getLogger(JdbcPublicationReplacement.class.getName()).log(System.Logger.Level.WARNING,"Publication replacement unavailable at "+phase[0]+": "+code);throw failure;}
+ }
+ private Map<String,Object> receipt(Actor actor,String meeting,String scope,String request){return authority.transactionRunner().required(()->{var rows=service.j.queryForList("SELECT actor_id,resource_id,request_hash,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),MeetingAccess.Action.EDIT);if(rows.isEmpty())return null;var row=rows.getFirst();if(!actor.enterpriseId().equals(row.get("actor_id"))||!meeting.equals(row.get("resource_id"))||!request.equals(row.get("request_hash")))throw new ama.contract.TranscriptContracts.Failure(409,"IDEMPOTENCY_CONFLICT");require(row.get("result_id")!=null&&((Number)row.get("http_status")).intValue()==202,PROVENANCE_UNAVAILABLE);return service.acknowledgement(meeting,(String)row.get("result_id"),true);});}
+}

## backend/adapters/src/main/java/ama/transcript/JdbcPublicationService.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/JdbcPublicationService.java
@@ -0,0 +1,125 @@
+package ama.transcript;
+
+import ama.access.MeetingAccess;
+import ama.config.*;
+import ama.config.json.*;
+import ama.contract.IdentityContracts.*;
+import ama.contract.MinutesContracts.*;
+import ama.contract.TaskContracts.*;
+import ama.contract.ExternalContracts.*;
+import ama.knowledge.ControlledKnowledgeDocumentRenderer;
+import ama.task.*;
+import java.util.*;
+import java.util.function.Supplier;
+import java.time.Instant;
+import org.springframework.jdbc.core.JdbcTemplate;
+import static ama.contract.MinutesContracts.*;
+import static ama.contract.MinutesContracts.FailureCode.*;
+
+/** Registered publication assembler. Untrusted DTOs never mint calls or task-domain capabilities. */
+public final class JdbcPublicationService {
+ final JdbcTranscriptAuthority authority;final ControlledKnowledgeClient client;final TranscriptAcquisitionBudget limits;final JdbcTemplate j;
+ final Supplier<ConfigArchiveSource.Snapshot> archives;final String pin;final JdbcSavedTranscripts transcripts;final JdbcTaskQueue queue;final JdbcOutbox outbox;private final JdbcPublicationReplacement replacement;
+ final JdbcMinutesFactsReader reader;final JdbcMinutesSavedQueries queries;
+ static final PreparationCancellation NEVER=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
+ public JdbcPublicationService(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits,JdbcSavedTranscripts transcripts,JdbcTaskQueue queue,JdbcOutbox outbox,ControlledKnowledgeClient client){this.authority=authority;this.archives=archives;this.pin=pin;this.limits=limits;this.transcripts=transcripts;this.queue=queue;this.outbox=outbox;this.client=client;j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);reader=new JdbcMinutesFactsReader(authority,archives,pin,limits);queries=new JdbcMinutesSavedQueries(authority,archives,pin,limits,transcripts);authority.publicationDomain().install(this);replacement=new JdbcPublicationReplacement(this);}
+ public static final class Authorization {
+  private final JdbcPublicationService issuer;private final String actor,purpose;private final long expires;
+  private Authorization(JdbcPublicationService issuer,String actor,String purpose){this.issuer=issuer;this.actor=actor;this.purpose=purpose;expires=Instant.now().getEpochSecond()+30;}
+  void require(ControlledKnowledgeClient client,String purpose){if(issuer.client!=client||!this.purpose.equals(purpose)||expires<Instant.now().getEpochSecond())throw new IllegalArgumentException("Foreign or expired knowledge authorization");}
+  String actor(){return actor;}
+ }
+ record Prepared(Actor actor,MinutesGraphDiscovery.Discovery declaration,MinutesGraphCapture.Graph graph,JdbcMinutesFactsReader.Snapshot facts,PublicationGraph.Plan publicationGraph,TaskSpec spec,String publication,String confirmation,String provider,String library,String folder,String businessKey,String logicalHash,String executionHash,String manifest,Map<String,Object> payload,String payloadHash,String assertion,long executionNo,long submissionNo){}
+ Authorization authorize(Actor actor,String meeting,String purpose){return authority.transactionRunner().required(()->{var gate=authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);authority.minutesScope(gate,actor,meeting);return new Authorization(this,actor.enterpriseId(),purpose);});}
+ record DestinationAssertion(String token,long expires){}
+ DestinationAssertion destination(Authorization authorized,String publication,String provider,String library,String folder)throws java.io.IOException {var body=Map.of("actorId",authorized.actor(),"providerId",provider,"libraryId",library,"folderId",folder,"context",publication);var response=client.exchange(client.authorized(authorized,"DESTINATION_AUTHORIZE","POST","/v1/knowledge/destination-authorizations",body),65536,NEVER);String assertion=ControlledKnowledgeClient.string(response,"assertion");var fields=client.assertion(assertion);require(authorized.actor().equals(fields.get("actorId"))&&library.equals(fields.get("libraryId"))&&folder.equals(fields.get("folderId"))&&publication.equals(fields.get("context")),PROVENANCE_UNAVAILABLE);return new DestinationAssertion(assertion,((Number)fields.get("expiresAt")).longValue());}
+ public Map<String,Object> submit(Actor actor,String meeting,String key,long expected,String confirmation,String provider,String library,String folder,String trace)throws java.io.IOException {
+  JdbcMinutesFactsReader.independent();if(transcripts==null)throw new IllegalStateException("Worker receipt runtime has no command installation");uuid(meeting);uuid(confirmation);uuid(trace);JdbcProcessingIntake.key(key);if(!client.identities().get("providerId").equals(provider)||library==null||folder==null||library.codePointCount(0,library.length())>255||folder.codePointCount(0,folder.length())>255||library.isBlank()||folder.isBlank())throw new IllegalArgumentException("Exact controlled destination required");
+  String scope=JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,"PUBLICATION_SUBMIT",key),request=JdbcProcessingIntake.digest(Long.toString(expected),confirmation,provider,library,folder);var replay=receipt(actor,meeting,scope,request);if(replay!=null)return replay;
+  String publication=UUID.randomUUID().toString();var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);var declaration=MinutesGraphDiscovery.withPending(authority.dataSource(),MinutesGraphDiscovery.discover(authority.dataSource(),meeting,"FINAL",confirmation,limits,authority.readBudget()),limits.tasks());JdbcMinutesFactsReader.charge(declaration,budget);var pub=PublicationGraph.discover(authority.dataSource(),meeting);PublicationGraph.reserve(pub.rows(),true);
+  var captured=authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);var cap=authority.minutesScope(gate,actor,meeting);var graph=MinutesGraphCapture.capturePublication(authority,cap,gate,actor,declaration,budget,()->PublicationGraph.lock(authority.dataSource(),pub,true),()->PublicationGraph.lock(authority.dataSource(),pub,false));current(graph,expected,confirmation);require(graph.auxiliary().getOrDefault("publication",List.of()).isEmpty(),CAPABILITY_UNAVAILABLE);return graph;});
+  var facts=reader.prepare(captured,budget);var readiness=queries.readiness(facts,true);if(!Boolean.TRUE.equals(readiness.get("ready")))throw new ama.contract.TranscriptContracts.Failure(409,"PUBLICATION_NOT_READY");var payload=render(facts,confirmation);String payloadHash=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(payload));var finalRow=JdbcMinutesFactsReader.one(captured,Table.FINAL_CONFIRMATION,confirmation);String businessKey=publication+":"+confirmation;
+  var logical=new TreeMap<String,Object>();logical.put("schema","ama.knowledge-publish.v1");logical.put("domain","KNOWLEDGE_PUBLISH");logical.put("providerId",provider);logical.put("publicationId",publication);logical.put("confirmationId",confirmation);logical.put("businessKey",businessKey);logical.put("libraryId",library);logical.put("folderId",folder);logical.put("sourceManifestHash",finalRow.string("source_manifest_hash"));logical.put("inputFingerprint",finalRow.string("input_fingerprint"));logical.put("renderArtifact",((Map<?,?>)payload.get("provenance")).get("renderArtifact"));logical.put("payloadSha256",payloadHash);String logicalHash=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(logical));String executionHash=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(Map.of("schema","ama.knowledge-publish-execution.v1","logicalRequestHash",logicalHash,"executionNo",1,"priorProofs",List.of())));
+  var manifest=new TreeMap<String,Object>();manifest.put("schema","ama.knowledge-publication-manifest.v1");manifest.putAll(client.identities());manifest.put("publicationId",publication);manifest.put("confirmationId",confirmation);manifest.put("requesterId",actor.enterpriseId());manifest.put("businessKey",businessKey);manifest.put("libraryId",library);manifest.put("folderId",folder);manifest.put("logicalRequestHash",logicalHash);manifest.put("executionRequestHash",executionHash);manifest.put("payloadSha256",payloadHash);manifest.put("archiveIndexSha256",pin);manifest.put("renderArtifact",logical.get("renderArtifact"));manifest.put("meetingId",meeting);manifest.put("businessVersion",expected);manifest.put("cancelEpoch",((Number)captured.discovery().root().get("cancel_epoch")).longValue());manifest.put("executionNo",1);manifest.put("submissionNo",1);manifest.put("priorProofs",List.of());String wireManifest=new String(ControlledKnowledgeClient.canonical(manifest),java.nio.charset.StandardCharsets.UTF_8);require(wireManifest.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=65536,BUDGET_EXCEEDED);
+  var spec=spec(manifest);var destinationProof=destination(authorize(actor,meeting,"DESTINATION_AUTHORIZE"),publication,provider,library,folder);String assertion=destinationProof.token();var prepared=new Prepared(actor,declaration,captured,facts,pub,spec,publication,confirmation,provider,library,folder,businessKey,logicalHash,executionHash,wireManifest,payload,payloadHash,assertion,1,1);
+  return authority.transactionRunner().required(()->{
+   j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'PUBLICATION_SUBMIT',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",scope,actor.enterpriseId(),meeting,JdbcProcessingIntake.digest(key),request);var record=j.queryForMap("SELECT actor_id,resource_id,request_hash,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);if(!actor.enterpriseId().equals(record.get("actor_id"))||!meeting.equals(record.get("resource_id"))||!request.equals(record.get("request_hash")))throw new ama.contract.TranscriptContracts.Failure(409,"IDEMPOTENCY_CONFLICT");if(record.get("result_id")!=null)return acknowledgement(meeting,(String)record.get("result_id"),true);
+   var cap=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.capturePublication(authority,cap,gate,actor,declaration,budget,()->PublicationGraph.lock(authority.dataSource(),pub,true),()->PublicationGraph.lock(authority.dataSource(),pub,false));require(fresh.equals(captured),PROVENANCE_UNAVAILABLE);current(fresh,expected,confirmation);transcripts.requireCorrection(authority,transcripts.correctionFor(authority),true);require(destinationProof.expires()>=Instant.now().getEpochSecond(),CAPABILITY_UNAVAILABLE);authority.claimMinutesWrite(cap,gate,actor,fresh,JdbcTranscriptAuthority.MinutesWritePurpose.PUBLICATION_SUBMIT,prepared);
+   require(j.update("INSERT INTO publication(id,meeting_id,confirmation_id,business_key,library_id,folder_id,state,revision,created_at) VALUES (?,?,?,?,?,?,'READY',1,UTC_TIMESTAMP(6))",publication,meeting,confirmation,businessKey,library,folder)==1,PROVENANCE_UNAVAILABLE);
+   String task=queue.enqueue(spec,authority.publicationDomain().adopt(cap,gate,fresh,prepared));require(j.update("INSERT INTO publication_submission(task_id,publication_id,submission_no,confirmation_id,provider_id,business_key,library_id,folder_id,requester_id,logical_request_hash,execution_request_hash,request_manifest,created_at,local_state) VALUES (?,?,1,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),'UNDECIDED')",task,publication,confirmation,provider,businessKey,library,folder,actor.enterpriseId(),logicalHash,executionHash,wireManifest)==1,PROVENANCE_UNAVAILABLE);
+   require(j.update("UPDATE idempotency_record SET result_id=?,http_status=202 WHERE scope_hash=? AND result_id IS NULL AND http_status=0",task,scope)==1,PROVENANCE_UNAVAILABLE);new ama.audit.JdbcAuditAdapter(authority.dataSource()).append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),"PUBLICATION_ACCEPTED",meeting,expected,"SUCCEEDED",trace,Instant.now()));outbox.append(new ama.contract.OutboxContracts.EventDraft("PUBLICATION_ACCEPTED",task,spec.subject(),expected,new ama.contract.OutboxContracts.Reference("PUBLICATION",publication)));outbox.flush();return acknowledgement(meeting,task,false);
+  });
+ }
+ private Map<String,Object> receipt(Actor actor,String meeting,String scope,String request){return authority.transactionRunner().required(()->{var found=j.queryForList("SELECT actor_id,resource_id,request_hash,result_id,http_status FROM idempotency_record WHERE scope_hash=? FOR UPDATE",scope);authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),MeetingAccess.Action.EDIT);if(found.isEmpty())return null;var row=found.getFirst();if(!actor.enterpriseId().equals(row.get("actor_id"))||!meeting.equals(row.get("resource_id"))||!request.equals(row.get("request_hash")))throw new ama.contract.TranscriptContracts.Failure(409,"IDEMPOTENCY_CONFLICT");require(row.get("result_id")!=null&&((Number)row.get("http_status")).intValue()==202,PROVENANCE_UNAVAILABLE);return acknowledgement(meeting,(String)row.get("result_id"),true);});}
+ Map<String,Object> acknowledgement(String meeting,String task,boolean replay){var found=j.queryForList("SELECT s.task_id,s.publication_id,s.submission_no,s.confirmation_id,s.provider_id,s.library_id,s.folder_id,s.requester_id,s.local_state FROM publication_submission s JOIN durable_task t ON t.id=s.task_id WHERE s.task_id=? AND t.subject_kind='MEETING' AND t.subject_id=? AND t.task_type='KNOWLEDGE_PUBLISH_V1' AND t.input_kind='PUBLICATION' AND t.input_id=s.publication_id AND t.input_fingerprint=s.logical_request_hash",task,meeting);require(found.size()==1,PROVENANCE_UNAVAILABLE);var row=found.getFirst();return JdbcMinutesSavedQueries.map("publicationId",row.get("publication_id"),"taskId",task,"confirmationId",row.get("confirmation_id"),"providerId",row.get("provider_id"),"libraryId",row.get("library_id"),"folderId",row.get("folder_id"),"submissionNoToken",row.get("submission_no").toString(),"localState",row.get("local_state"),"httpStatus",202,"replayed",replay,"observation",JdbcMinutesCommandReceipts.observation(j,meeting));}
+ static TaskSpec spec(Map<String,Object> manifest){String meeting=(String)manifest.get("meetingId"),publication=(String)manifest.get("publicationId"),logical=(String)manifest.get("logicalRequestHash");long r=((Number)manifest.get("businessVersion")).longValue(),e=((Number)manifest.get("cancelEpoch")).longValue();var values=new TreeMap<String,Object>();values.put("schema","ama.knowledge-publish-producer.v1");values.put("domain","TASK_PRODUCER");for(String key:List.of("meetingId","publicationId","confirmationId","logicalRequestHash","executionNo","businessVersion","cancelEpoch","businessKey","submissionNo"))values.put(key,manifest.get(key));values.put("type","KNOWLEDGE_PUBLISH_V1");return new TaskSpec("KNOWLEDGE_PUBLISH_V1","KNOWLEDGE_PUBLISH",new SubjectRef("MEETING",meeting),ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(values)),new TypedRef("PUBLICATION",publication),logical,r,0,e,3);}
+ Map<String,Object> render(JdbcMinutesFactsReader.Snapshot snapshot,String confirmation){
+  var finalRow=JdbcMinutesFactsReader.one(snapshot.graph(),Table.FINAL_CONFIRMATION,confirmation);var body=JdbcMinutesFactsReader.one(snapshot.graph(),Table.MINUTES_REVISION,finalRow.string("minutes_revision_id"));var configs=snapshot.facts().configurations().stream().filter(c->c.inputId().equals(body.string("generation_input_id"))).toList();require(configs.size()==1,PROFILE_UNAVAILABLE);var render=configs.getFirst().profiles().definition(ProfileKind.RENDER);var read=new ArchiveReadState(authority.readBudget(),()->false);var parsed=new StrictConfigArchiveReader().read(render.bytes(),new ama.contract.ConfigArchiveContracts.Identity(ama.contract.ConfigArchiveContracts.ArtifactType.RENDER,render.version()),read);var envelope=ama.config.WireValues.fields(parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");var definition=ama.config.WireValues.fields(envelope.get("definition"),"contract","rendererVersion","defaultTemplateRevisionId");
+  var info=snapshot.graph().auxiliary().getOrDefault("meeting_info_revision",List.of()).stream().filter(row->finalRow.string("info_revision_id").equals(row.get("id"))).toList();require(info.size()==1,PROVENANCE_UNAVAILABLE);var members=snapshot.graph().auxiliary().getOrDefault("roster_member",List.of()).stream().filter(row->finalRow.string("roster_revision_id").equals(row.get("roster_id"))).sorted(Comparator.comparing(row->(String)row.get("member_id"))).map(row->new ControlledKnowledgeDocumentRenderer.Member((String)row.get("member_kind"),(String)row.get("display_name"))).toList();var meta=info.getFirst();var meeting=new ControlledKnowledgeDocumentRenderer.Meeting((String)meta.get("name"),(String)meta.get("category_id"),meta.get("actual_meeting_date")==null?null:meta.get("actual_meeting_date").toString(),members);byte[] payload=new ControlledKnowledgeDocumentRenderer().render(new FinalFacts(confirmation,snapshot.facts()),meeting,ama.config.WireValues.text(definition.get("rendererVersion")),ama.config.WireValues.text(definition.get("defaultTemplateRevisionId")),render,Math.min(8388608,limits.responseBytes()),()->false);snapshot.budget().capture(payload.length,payload.length,payload.length,4);return ControlledKnowledgeClient.parse(payload,8388608);
+ }
+ public static final class Request implements ExternalOperationPort.PreparedRequest {
+  final JdbcPublicationService issuer;final String task;final Prepared prepared;final ExternalRequest external;
+  private Request(JdbcPublicationService issuer,String task,Prepared prepared){this.issuer=issuer;this.task=task;this.prepared=prepared;external=new ExternalRequest("KNOWLEDGE_PUBLISH",prepared.businessKey(),prepared.logicalHash(),prepared.executionHash(),prepared.manifest());}
+ }
+ record ReceiptIdentity(Actor actor,TaskSpec spec,String task,String publication,String confirmation,String provider,String library,String folder,String businessKey,String logicalHash,String executionHash,String manifest,String payloadHash,long executionNo) {
+  static ReceiptIdentity from(Request request){var p=request.prepared;return new ReceiptIdentity(p.actor(),p.spec(),request.task,p.publication(),p.confirmation(),p.provider(),p.library(),p.folder(),p.businessKey(),p.logicalHash(),p.executionHash(),p.manifest(),p.payloadHash(),p.executionNo());}
+ }
+ static final class Observation implements ExternalOperationPort.PreparedObservation {
+  final JdbcPublicationService issuer;final Request request;final ReceiptIdentity identity;final OperationToken token;final Map<String,Object> receipt;final ExternalObservation fact;
+  private Observation(JdbcPublicationService issuer,Request request,ReceiptIdentity identity,OperationToken token,Map<String,Object> receipt,ExternalObservation fact){this.issuer=issuer;this.request=request;this.identity=identity;this.token=token;this.receipt=Map.copyOf(receipt);this.fact=fact;}
+  private Observation(JdbcPublicationService issuer,Request request,OperationToken token,Map<String,Object> receipt,ExternalObservation fact){this(issuer,request,ReceiptIdentity.from(request),token,receipt,fact);}
+ }
+ public Request prepare(TaskLease lease)throws java.io.IOException {return prepareRecorded(lease.taskId(),lease.spec(),lease);}
+ private Request prepareRecorded(String task,TaskSpec spec,TaskLease lease)throws java.io.IOException {return prepareRecorded(task,spec,lease,limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false));}
+ private Request prepareRecorded(String task,TaskSpec spec,TaskLease lease,TranscriptAcquisitionBudget.Ledger budget)throws java.io.IOException {
+  JdbcMinutesFactsReader.independent();PublicationTaskDomainPolicy.shape(spec);var selected=j.queryForList("SELECT publication_id,confirmation_id,requester_id FROM publication_submission WHERE task_id=?",task);require(selected.size()==1,PROVENANCE_UNAVAILABLE);var binding=selected.getFirst();String meeting=spec.subject().id(),confirmation=(String)binding.get("confirmation_id");var actor=new Actor((String)binding.get("requester_id"),Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
+  var declaration=MinutesGraphDiscovery.discover(authority.dataSource(),meeting,"FINAL",confirmation,limits,authority.readBudget());var pub=PublicationGraph.discover(authority.dataSource(),meeting);JdbcMinutesFactsReader.charge(declaration,budget);
+  var captured=authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);var cap=authority.minutesScope(gate,actor,meeting);var graph=MinutesGraphCapture.capturePublication(authority,cap,gate,actor,declaration,budget,()->PublicationGraph.lock(authority.dataSource(),pub,true),()->PublicationGraph.lock(authority.dataSource(),pub,false));current(graph,spec.businessVersion(),confirmation);if(lease!=null){var actual=pub.rows().get("durable_task").stream().filter(r->task.equals(r.get("id"))).toList();require(actual.size()==1&&"RUNNING".equals(actual.getFirst().get("state"))&&lease.leaseOwner().equals(actual.getFirst().get("lease_owner"))&&lease.attempt()==((Number)actual.getFirst().get("attempt")).longValue(),PROVENANCE_UNAVAILABLE);}return graph;});
+  String manifest=authority.transactionRunner().required(()->{var gate=JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,declaration);var cap=authority.minutesScope(gate,actor,meeting);var fresh=MinutesGraphCapture.capturePublication(authority,cap,gate,actor,declaration,budget,()->PublicationGraph.lock(authority.dataSource(),pub,true),()->PublicationGraph.lock(authority.dataSource(),pub,false));require(fresh.equals(captured),PROVENANCE_UNAVAILABLE);var rows=pub.rows().get("publication_submission").stream().filter(r->task.equals(r.get("task_id"))).toList();require(rows.size()==1,PROVENANCE_UNAVAILABLE);var row=rows.getFirst();long bytes=((Number)row.get("request_manifest_bytes")).longValue(),scalars=((Number)row.get("request_manifest_scalars")).longValue(),units=((Number)row.get("request_manifest_utf16")).longValue();require(bytes<=65536,BUDGET_EXCEEDED);budget.capture(bytes,scalars,units,4);return j.queryForObject("SELECT request_manifest FROM publication_submission WHERE task_id=?",String.class,task);});
+  var fields=ControlledKnowledgeClient.parse(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8),65536);require(spec(fields).equals(spec)&&client.identities().entrySet().stream().allMatch(e->e.getValue().equals(fields.get(e.getKey())))&&actor.enterpriseId().equals(fields.get("requesterId"))&&confirmation.equals(fields.get("confirmationId")),PROVENANCE_UNAVAILABLE);var facts=reader.prepare(captured,budget);var payload=render(facts,confirmation);String hash=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(payload));require(hash.equals(fields.get("payloadSha256")),FINGERPRINT_MISMATCH);
+  String publication=spec.input().id(),provider=(String)fields.get("providerId"),library=(String)fields.get("libraryId"),folder=(String)fields.get("folderId");String assertion=lease==null?null:destination(new Authorization(this,actor.enterpriseId(),"DESTINATION_AUTHORIZE"),publication,provider,library,folder).token();var prepared=new Prepared(actor,declaration,captured,facts,pub,spec,publication,confirmation,provider,library,folder,(String)fields.get("businessKey"),(String)fields.get("logicalRequestHash"),(String)fields.get("executionRequestHash"),manifest,payload,hash,assertion,((Number)fields.get("executionNo")).longValue(),((Number)fields.get("submissionNo")).longValue());return new Request(this,task,prepared);
+ }
+ public ExternalOperationPort.PreparedObservation execute(Request request,OperationToken token,PreparationCancellation cancellation)throws java.io.IOException {
+  requireRequest(request);require(token.subject().equals(request.prepared.spec().subject())&&token.executionNo()==request.prepared.executionNo(),PROVENANCE_UNAVAILABLE);var p=request.prepared;
+  String assertion=destination(authorize(p.actor(),p.spec().subject().id(),"DESTINATION_AUTHORIZE"),p.publication(),p.provider(),p.library(),p.folder()).token();var body=new TreeMap<String,Object>();body.put("protocol","ama.controlled-knowledge-publish.v1");body.put("providerId",p.provider());body.put("publicationId",p.publication());body.put("confirmationId",p.confirmation());body.put("businessKey",p.businessKey());body.put("logicalRequestHash",p.logicalHash());body.put("executionRequestHash",p.executionHash());body.put("executionId",token.executionId());body.put("executionNo",token.executionNo());body.put("actorId",p.actor().enterpriseId());body.put("libraryId",p.library());body.put("folderId",p.folder());body.put("destinationAuthorization",assertion);body.put("payloadSha256",p.payloadHash());body.put("payload",p.payload());body.put("bindingTransition",ControlledKnowledgeClient.parse(p.manifest().getBytes(java.nio.charset.StandardCharsets.UTF_8),65536).get("bindingTransition"));
+  var authorization=authorize(p.actor(),p.spec().subject().id(),"PUBLISH");try{return accept(request,token,client.exchange(client.authorized(authorization,"PUBLISH","POST","/v1/knowledge/publications",body),65536,cancellation));}catch(java.io.IOException unavailable){return new Observation(this,request,token,Map.of("state","UNKNOWN"),new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"UNKNOWN_OUTCOME",null));}
+ }
+ public static final class LookupRequest {
+  private final JdbcPublicationService issuer;private final JdbcExternalOperations.RecordedLookup capability;private final Request request;private final ReceiptIdentity identity;private final OperationToken token;
+  private LookupRequest(JdbcPublicationService issuer,JdbcExternalOperations.RecordedLookup capability,Request request,ReceiptIdentity identity,OperationToken token){this.issuer=issuer;this.capability=capability;this.request=request;this.identity=identity;this.token=token;}
+ }
+ public LookupRequest prepareLookup(JdbcExternalOperations.RecordedLookup capability,OperationToken token)throws java.io.IOException {
+  var original=PublicationRecordedLookup.capture(this,capability,token);Request request=original.businessAllowed()?prepareRecorded(original.identity().task(),original.identity().spec(),null,original.budget()):null;
+  if(request!=null)require(ReceiptIdentity.from(request).equals(original.identity()),PROVENANCE_UNAVAILABLE);capability.require(authority.dataSource(),token);return new LookupRequest(this,capability,request,original.identity(),token);
+ }
+ public ExternalOperationPort.PreparedObservation lookup(LookupRequest lookup,PreparationCancellation cancellation)throws java.io.IOException {
+  if(lookup.issuer!=this)throw new IllegalArgumentException("Foreign recorded publication lookup");lookup.capability.require(authority.dataSource(),lookup.token);var token=lookup.token;var p=lookup.identity;String path="/v1/knowledge/publications/"+p.publication()+"/executions/"+token.executionId();return accept(lookup.request,p,token,client.exchange(client.authorized(new Authorization(this,p.actor().enterpriseId(),"RECEIPT_LOOKUP"),"RECEIPT_LOOKUP","GET",path,null),65536,cancellation));
+ }
+ public Map<String,Object> readPublishedReceipt(Actor actor,String meeting,String publication)throws java.io.IOException {
+  var read=PublicationRecordedLookup.terminal(this,actor,meeting,publication);var identity=read.capture.identity();var token=read.reference;read.requireOwner();String path="/v1/knowledge/publications/"+publication+"/executions/"+token.executionId();var raw=client.exchange(client.authorized(new Authorization(this,identity.actor().enterpriseId(),"RECEIPT_LOOKUP"),"RECEIPT_LOOKUP","GET",path,null),65536,NEVER);validateSuccess(identity,token.executionId(),token.executionNo(),raw);read.requireOwner();return Map.copyOf(raw);
+ }
+ private Observation accept(Request request,OperationToken token,Map<String,Object> receipt){return accept(request,ReceiptIdentity.from(request),token,receipt);}
+ private Observation accept(Request request,ReceiptIdentity p,OperationToken token,Map<String,Object> receipt){String state=(String)receipt.get("state");if("UNKNOWN".equals(state)){require(p.publication().equals(receipt.get("publicationId"))&&token.executionId().equals(receipt.get("executionId")),PROVENANCE_UNAVAILABLE);return new Observation(this,request,p,token,receipt,new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"UNKNOWN_OUTCOME",null));}
+  if("FAILED".equals(state)){var verified=validateRejection(p,token.executionId(),token.executionNo(),receipt);return new Observation(this,request,p,token,receipt,new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"DESTINATION_DENIED",verified));}
+  validateSuccess(p,token.executionId(),token.executionNo(),receipt);return new Observation(this,request,p,token,receipt,new ExternalObservation(ExternalOutcome.SUCCEEDED,(String)receipt.get("resultId"),null,null,null));
+ }
+ public Map<String,Object> readRejectedReceipt(Actor actor,String meeting,String publication)throws java.io.IOException {
+  var read=PublicationRecordedLookup.terminal(this,actor,meeting,publication,"FAILED");var p=read.capture.identity();read.requireOwner();String path="/v1/knowledge/publications/"+publication+"/executions/"+read.reference.executionId();var receipt=client.exchange(client.authorized(new Authorization(this,p.actor().enterpriseId(),"RECEIPT_LOOKUP"),"RECEIPT_LOOKUP","GET",path,null),65536,NEVER);require("FAILED".equals(receipt.get("state")),PROVENANCE_UNAVAILABLE);var verified=validateRejection(p,read.reference.executionId(),read.reference.executionNo(),receipt);require(j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE id=? AND state='FAILED' AND no_effect_basis=? AND no_effect_evidence=? AND no_effect_verified_at=?",Long.class,read.reference.executionId(),verified.basis().name(),verified.safeEvidenceReference(),java.time.LocalDateTime.ofInstant(verified.verifiedAt(),java.time.ZoneOffset.UTC))==1,PROVENANCE_UNAVAILABLE);read.requireOwner();return Map.copyOf(receipt);
+ }
+ NoEffectProof validateRejection(ReceiptIdentity p,String executionId,long executionNo,Map<String,Object> receipt){
+   validateReceiptIdentity(p,executionId,executionNo,receipt);String signed=ControlledKnowledgeClient.string(receipt,"permanentRejection");var proof=client.permanentRejection(signed);for(String key:List.of("publicationId","confirmationId","businessKey","logicalRequestHash","executionRequestHash","executionId","executionNo","actorId","libraryId","folderId","payloadSha256","receiptId","verifiedAt","slotGeneration","noEffectBasis","errorCode"))require(Objects.equals(proof.get(key),receipt.get(key)),PROVENANCE_UNAVAILABLE);String receiptId=ControlledKnowledgeClient.string(proof,"receiptId");require(receiptId.matches("receipt-[0-9a-f]{32}"),PROVENANCE_UNAVAILABLE);String evidence="knowledge:"+client.identities().get("storeInstanceId")+":"+receiptId+":"+ControlledKnowledgeClient.digest(signed.getBytes(java.nio.charset.StandardCharsets.US_ASCII));var verified=new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,evidence,Instant.ofEpochSecond(((Number)proof.get("verifiedAt")).longValue()));return verified;
+ }
+ private void validateSuccess(ReceiptIdentity p,String executionId,long executionNo,Map<String,Object> receipt){
+  require("SUCCESS".equals(receipt.get("state")),PROVENANCE_UNAVAILABLE);validateReceiptIdentity(p,executionId,executionNo,receipt);String result=ControlledKnowledgeClient.string(receipt,"resultId"),document=ControlledKnowledgeClient.string(receipt,"documentId");require(result.matches("result-[0-9a-f]{32}")&&document.matches("doc-[0-9a-f]{32}"),PROVENANCE_UNAVAILABLE);
+ }
+ private void validateReceiptIdentity(ReceiptIdentity p,String executionId,long executionNo,Map<String,Object> receipt){
+  require("ama.controlled-knowledge-receipt.v1".equals(receipt.get("protocol"))&&client.identities().get("providerId").equals(receipt.get("providerId"))&&client.identities().get("storeInstanceId").equals(receipt.get("storeInstanceId"))&&p.publication().equals(receipt.get("publicationId"))&&p.confirmation().equals(receipt.get("confirmationId"))&&p.businessKey().equals(receipt.get("businessKey"))&&p.logicalHash().equals(receipt.get("logicalRequestHash"))&&p.executionHash().equals(receipt.get("executionRequestHash"))&&executionId.equals(receipt.get("executionId"))&&executionNo==((Number)receipt.get("executionNo")).longValue()&&p.actor().enterpriseId().equals(receipt.get("actorId"))&&p.library().equals(receipt.get("libraryId"))&&p.folder().equals(receipt.get("folderId"))&&p.payloadHash().equals(receipt.get("payloadSha256"))&&client.identities().get("clientId").equals(receipt.get("clientId")),PROVENANCE_UNAVAILABLE);
+ }
+ Request requireRequest(ExternalOperationPort.PreparedRequest value){if(!(value instanceof Request r)||r.issuer!=this)throw new IllegalArgumentException("Foreign publication request");return r;}
+ Observation requireObservation(ExternalOperationPort.PreparedObservation value){if(!(value instanceof Observation o)||o.issuer!=this)throw new IllegalArgumentException("Foreign publication receipt");if(o.request!=null)requireRequest(o.request);return o;}
+ public Map<String,Object> resubmit(Actor actor,String meeting,String publication,String key,long expected,long expectedPublication,String confirmation,String provider,String library,String folder,String trace)throws java.io.IOException{return replacement.accept(actor,meeting,publication,key,expected,expectedPublication,confirmation,provider,library,folder,trace);}
+ Map<String,Object> replacementProof(ReceiptIdentity identity,String execution,long number)throws java.io.IOException{String path="/v1/knowledge/publications/"+identity.publication()+"/executions/"+execution;var receipt=client.exchange(client.authorized(new Authorization(this,identity.actor().enterpriseId(),"RECEIPT_LOOKUP"),"RECEIPT_LOOKUP","GET",path,null),65536,NEVER);require("FAILED".equals(receipt.get("state")),PROVENANCE_UNAVAILABLE);validateRejection(identity,execution,number,receipt);return receipt;}
+ public Map<String,Long> traffic(){return client.traffic();}
+ public void requireClient(ControlledKnowledgeClient value){if(value!=client)throw new IllegalArgumentException("Foreign publication client");}
+ static void current(MinutesGraphCapture.Graph graph,long expected,String confirmation){var root=graph.discovery().root();if(((Number)root.get("revision")).longValue()!=expected)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");require(confirmation.equals(root.get("final_confirmation_id"))&&"ACTIVE".equals(root.get("lifecycle"))&&"ACTIVE".equals(root.get("governance_state"))&&((Number)root.get("ever_published")).intValue()==0,CAPABILITY_UNAVAILABLE);}
+}

## backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java
--- a/backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java
+++ b/backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java
@@ -19,59 +19,59 @@
 import java.time.format.DateTimeFormatter;
 import java.util.*;
 import java.util.function.BooleanSupplier;
 import static ama.canonical.CanonicalValue.*;
 import static ama.contract.MinutesContracts.*;
 import static ama.contract.MinutesContracts.FailureCode.*;
 
 /** One private issuer for first bind and source provenance. No detached value grants authority. */
 public final class JdbcTranscriptAuthority {
  public static final String REFERENCE="INPUT_BATCH_FILE",PURPOSE="TRANSCRIPT_INPUT";
  private final DataSource source;private final JdbcTemplate j;private final TransactionRunner tx;
  private final MeetingAccess access;private final ArchiveReadBudget budget;private final CanonicalBudget canonical;
  private FileRegistry registry;
  private FileBackendCatalog savedBackends;
  private FilePurposePolicy purposes;
- private final ImportTaskDomainPolicy taskPolicy;private final MinutesTaskDomainPolicy minutesTaskPolicy;
+ private final ImportTaskDomainPolicy taskPolicy;private final MinutesTaskDomainPolicy minutesTaskPolicy;private final PublicationTaskDomainPolicy publicationTaskPolicy;
  private final InputBatchFileReferencePolicy policy;
  private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
  public interface ReservedIntake extends FileRegistry.ReferenceContext {}
  public interface HeldProcessingFacts {}
  private enum Phase { RESERVED, INSERTED, BOUND, FAILED }
  private final class Reservation implements ReservedIntake {
   final Object holder=transaction();final FileRegistry.LockedMeeting gate;final PreparedIntake prepared;
   Phase phase=Phase.RESERVED;Held held;
   Reservation(FileRegistry.LockedMeeting gate,PreparedIntake prepared){this.gate=gate;this.prepared=prepared;}
   JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
  }
  private final class Held implements HeldProcessingFacts {
   final Reservation reserved;final StoredProvenanceFacts facts;
   Held(Reservation reserved,StoredProvenanceFacts facts){this.reserved=reserved;this.facts=facts;}
   JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
  }
  public static final class PreparedIntake {
   private final JdbcTranscriptAuthority issuer;private final ProcessingIntakeFacts.Draft draft;
   private final String batch,input,manifest,batchHash,processingVersion,parserVersion,time;private final TaskSpec spec;
   private final StoredProvenanceFacts expected;
   private PreparedIntake(JdbcTranscriptAuthority issuer,ProcessingIntakeFacts.Draft draft,String batch,String input,String manifest,String batchHash,String processingVersion,String parserVersion,String time,TaskSpec spec,StoredProvenanceFacts expected){
    this.issuer=issuer;this.draft=draft;this.batch=batch;this.input=input;this.manifest=manifest;this.batchHash=batchHash;this.processingVersion=processingVersion;this.parserVersion=parserVersion;this.time=time;this.spec=spec;this.expected=expected;
   }
   public String batchId(){return batch;}public String inputId(){return input;}public TaskSpec spec(){return spec;}
  }
  public record Components(FileRegistry registry,JdbcTranscriptAuthority authority,ProcessingIntakeFacts intake,ProcessingSourceReader sources) {}
  private JdbcTranscriptAuthority(DataSource source,TransactionRunner tx,MeetingAccess access,ArchiveReadBudget budget,CanonicalBudget canonical){
-  this.source=Objects.requireNonNull(source);j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=Objects.requireNonNull(tx);this.access=Objects.requireNonNull(access);this.budget=Objects.requireNonNull(budget);this.canonical=Objects.requireNonNull(canonical);policy=new InputBatchFileReferencePolicy(this);taskPolicy=new ImportTaskDomainPolicy(this,source);minutesTaskPolicy=new MinutesTaskDomainPolicy(this,source);
+  this.source=Objects.requireNonNull(source);j=new JdbcTemplate(source);j.setQueryTimeout(5);this.tx=Objects.requireNonNull(tx);this.access=Objects.requireNonNull(access);this.budget=Objects.requireNonNull(budget);this.canonical=Objects.requireNonNull(canonical);policy=new InputBatchFileReferencePolicy(this);taskPolicy=new ImportTaskDomainPolicy(this,source);minutesTaskPolicy=new MinutesTaskDomainPolicy(this,source);publicationTaskPolicy=new PublicationTaskDomainPolicy(this,source);
  }
  MeetingAccess savedAccess(){return access;}
  public static Components create(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,
          FileBackendCatalog backends,Map<String,FileReferencePolicy> legacy,ArchiveReadBudget budget,CanonicalBudget canonical){
   if(legacy.containsKey(REFERENCE))throw new IllegalArgumentException("Production transcript authority owns its complete policy registration");
   var authority=new JdbcTranscriptAuthority(source,tx,access,budget,canonical);authority.purposes=Objects.requireNonNull(purposes);authority.savedBackends=Objects.requireNonNull(backends);var policies=new HashMap<>(legacy);policies.put(REFERENCE,authority.policy);
   var registry=new JdbcFileRegistry(source,tx,access,purposes,backends,policies,budget);authority.attach(registry);
   return new Components(registry,authority,new JdbcProcessingIntakeFacts(authority),new JdbcProcessingSourceReader(authority));
  }
  private void attach(FileRegistry registry){if(this.registry!=null)throw new IllegalStateException("Duplicate authority attachment");this.registry=Objects.requireNonNull(registry);}
  ProcessingIntakeFacts.Draft readDraft(Actor actor,String meeting,List<String> files,BooleanSupplier cancelled){
   independent();ids(files);check(cancelled);
   var draft=tx.required(()->{
    var gate=registry.lock(actor,meeting,MeetingAccess.Action.EDIT);var root=root(meeting);writable(root);
    var held=registry.prelockSources(gate,files,false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));
@@ -176,30 +176,32 @@
  DataSource dataSource(){return source;}TransactionRunner transactionRunner(){return tx;}
  /** Deny-only verification of the exact retained complete graph; no acquisition or authority issuance. */
  public void requireImportCompletion(List<TaskSpec> specs,ama.task.TaskDomainPolicy.PreparedDomain prepared){taskPolicy.requireCompletion(specs,prepared);}
  public void requireImportSource(DataSource expected){if(source!=expected)throw new IllegalArgumentException("Foreign import source");}
  public StoredProvenanceFacts readWorkerFacts(TaskLease lease,BooleanSupplier cancelled){
   independent();Objects.requireNonNull(lease);check(cancelled);
   var actual=j.queryForList("SELECT owner_id FROM meeting WHERE id=?",String.class,lease.spec().subject().id());
   require(actual.size()==1,PROVENANCE_UNAVAILABLE);
   var actor=new Actor(actual.getFirst(),ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
   var facts=new JdbcProcessingFactsReader(this).readWorker(actor,lease,budget,cancelled);
   require(facts.taskFacts().size()==1&&facts.taskFacts().getFirst().taskId().equals(lease.taskId())&&facts.taskFacts().getFirst().spec().equals(lease.spec()),PROVENANCE_UNAVAILABLE);
   return facts;
  }
  public ama.task.TaskDomainPolicy taskPolicy(){return taskPolicy;}
  public ama.task.TaskDomainPolicy minutesTaskPolicy(){return minutesTaskPolicy;}
+ public ama.task.TaskDomainPolicy publicationTaskPolicy(){return publicationTaskPolicy;}
+ PublicationTaskDomainPolicy publicationDomain(){return publicationTaskPolicy;}
  MinutesTaskDomainPolicy minutesDomain(){return minutesTaskPolicy;}
  public ama.task.TaskDomainPolicy.PreparedDomain adoptCreated(FileRegistry.LockedMeeting gate,HeldProcessingFacts held,TaskSpec spec){return taskPolicy.adopt(gate,held,spec);}
  public List<FileRegistry.CompletedSource> originals(PreparedIntake prepared){
   if(prepared==null||prepared.issuer!=this)throw new IllegalArgumentException("Foreign preparation");return prepared.draft.orderedSources();
  }
  void requireCreatedTransition(FileRegistry.LockedMeeting gate,HeldProcessingFacts value,TaskSpec spec){
   requireHeld(value,gate,spec);var h=held(value);var prepared=h.reserved.prepared;var draft=prepared.draft;
   var root=root(draft.meetingId());writable(root);
   access.requirePrepared(registry.access(gate),draft.actor(),draft.meetingId(),List.of(draft.actor().enterpriseId()),MeetingAccess.Action.EDIT);
   require(number(root,"revision")==spec.businessVersion()&&number(root,"cancel_epoch")==spec.cancelEpoch()&&draft.revision()+1==spec.businessVersion()&&Objects.equals(root.get("owner_id"),draft.actor().enterpriseId()),FINGERPRINT_MISMATCH);
   require(j.queryForObject("SELECT COUNT(*) FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? AND business_version=? AND cancel_epoch=? AND deleted=0",Long.class,draft.meetingId(),spec.businessVersion(),spec.cancelEpoch())==1,FINGERPRINT_MISMATCH);
  }
 
  public interface PreparedMinutesTransition {}
  public interface HeldMinutesTransition {}
@@ -278,55 +280,55 @@
   var root=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);if(!actor.enterpriseId().equals(root.get("owner_id")))throw new ama.access.MeetingAccess.AccessDeniedException();
   var guard=j.queryForList("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=? FOR UPDATE",Long.class,meeting);
   var controls=MinutesIntentControl.read(j,meeting,true);if(!controls.equals(plan.controls))throw new ama.contract.TranscriptContracts.Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");MinutesIntentControl.requireControlled(controls,root);
   if(((Number)root.get("revision")).longValue()!=expected)throw new JdbcMinutesIntentReceipts.StaleIntent(new JdbcMinutesIntentReceipts(source,tx,access).projection(meeting,false));
   if(guard.size()>1||!guard.isEmpty()&&guard.getFirst()<0)throw new ama.contract.TranscriptContracts.Failure(503,"PROVENANCE_UNAVAILABLE");
   if(!guard.isEmpty()&&guard.getFirst()>=Long.MAX_VALUE-1)throw new ama.contract.TranscriptContracts.Failure(409,"INTENT_EXHAUSTED");
   return new IntentScope(gate,plan,guard.isEmpty()?null:guard.getFirst());
  }
  void writeMinutesIntent(Object value,Object prepared,FileRegistry.LockedMeeting gate){
   if(!(value instanceof JdbcTranscriptAuthority.IntentScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||scope.plan!=prepared||scope.purpose!=EditorPurpose.EDIT_INTENT||scope.consumed)throw new IllegalArgumentException("Foreign, expired or consumed intent write");
   registry.access(gate);scope.consumed=true;long next=scope.before==null?1:scope.before+1;int changed=scope.before==null?j.update("INSERT INTO meeting_minutes_editor_guard(meeting_id,intent_epoch) VALUES (?,1)",scope.plan.meeting):j.update("UPDATE meeting_minutes_editor_guard SET intent_epoch=? WHERE meeting_id=? AND intent_epoch=?",next,scope.plan.meeting,scope.before);
   if(changed!=1||!j.queryForList("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Long.class,scope.plan.meeting).equals(List.of(next)))throw new ama.contract.TranscriptContracts.Failure(503,"PROVENANCE_UNAVAILABLE");
  }
 
  /** Minutes capture shares this exact issuer, DataSource holder and genuine B03/B04 gate. */
- enum MinutesWritePurpose { INITIAL_SETTINGS, GENERATION_INPUT, GENERATION_COMPLETION, STALE_RETENTION, ADOPT, REJECT, DISPOSE, EDIT, REVIEW, SETTINGS }
+ enum MinutesWritePurpose { INITIAL_SETTINGS, GENERATION_INPUT, GENERATION_COMPLETION, STALE_RETENTION, ADOPT, REJECT, DISPOSE, EDIT, REVIEW, SETTINGS, FINAL_CONFIRM, PUBLICATION_SUBMIT }
  private final class MinutesScope {
   final Object holder=transaction();final FileRegistry.LockedMeeting gate;final Actor actor;final String meeting;final MeetingAccess.Action action;
   MinutesGraphCapture.Graph graph;TransitionScope transition;MinutesWritePurpose writePurpose;Object preparedWrite;String decidedCandidate;boolean writing;
   MinutesScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting){this(gate,actor,meeting,MeetingAccess.Action.EDIT);}
   MinutesScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting,MeetingAccess.Action action){this.gate=gate;this.actor=actor;this.meeting=meeting;this.action=action;}
   JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
  }
  Object minutesScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting){
   access.requirePrepared(registry.access(gate),actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
   require(actor.enterpriseId().equals(j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,meeting)),PROVENANCE_UNAVAILABLE);
   return new MinutesScope(gate,actor,meeting);
  }
  Object minutesReadScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting){access.requirePrepared(registry.access(gate),actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.READ);return new MinutesScope(gate,actor,meeting,MeetingAccess.Action.READ);}
  void requireMinutesScope(Object value,FileRegistry.LockedMeeting gate,Actor actor,String meeting){
   if(!(value instanceof JdbcTranscriptAuthority.MinutesScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||!scope.actor.equals(actor)||!scope.meeting.equals(meeting)||scope.graph!=null)throw new IllegalArgumentException("Foreign, expired or consumed minutes capture scope");
   registry.access(gate); // Identity/root authorization was established before domain locks.
  }
  void sealMinutesCapture(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph){
   requireMinutesScope(value,gate,actor,graph.discovery().meeting());var scope=(MinutesScope)value;scope.graph=graph;
   if(graph.discovery().pending()!=null){var pending=graph.discovery().pending();long before=((Number)graph.discovery().root().get("revision")).longValue();MinutesPendingDiscovery.validate(pending,before);scope.transition=new TransitionScope(new TransitionPlan(actor,graph.discovery().meeting(),pending),gate,before);scope.transition.phase=TransitionPhase.TASKS;}
  }
  void claimMinutesWrite(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,MinutesWritePurpose purpose,Object prepared){claimMinutesWrite(value,gate,actor,graph,purpose,prepared,null);}
  void claimMinutesWrite(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,MinutesWritePurpose purpose,Object prepared,String selected){
-  if(!(value instanceof JdbcTranscriptAuthority.MinutesScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||!scope.actor.equals(actor)||!scope.meeting.equals(graph.discovery().meeting())||scope.graph!=graph||scope.writing||scope.action!=MeetingAccess.Action.EDIT)throw new IllegalArgumentException("Foreign, expired or reused minutes write invocation");registry.access(gate);Objects.requireNonNull(purpose);Objects.requireNonNull(prepared);String kind=graph.discovery().kind();boolean valid=switch(purpose){case INITIAL_SETTINGS->kind.equals("INITIAL_SETTINGS");case GENERATION_INPUT->kind.equals("CURRENT_GENERATION");case GENERATION_COMPLETION,STALE_RETENTION->kind.equals("STORED_INPUT");case ADOPT->kind.equals("ADOPT");case SETTINGS->Set.of("SETTINGS_TEMPLATE","SETTINGS_EXCLUSIONS").contains(kind);case REVIEW->Set.of("REVIEW_BODY","REVIEW_ELEMENT").contains(kind);case EDIT->Set.of("BODY_EDIT","BODY_RESTORE","SET_EDIT","SET_RESTORE").contains(kind);default->false;};if(!valid)throw new IllegalArgumentException("Wrong minutes invocation purpose");if(selected!=null&&!Set.of(MinutesWritePurpose.ADOPT,MinutesWritePurpose.REJECT,MinutesWritePurpose.DISPOSE).contains(purpose))throw new IllegalArgumentException("Unexpected candidate decision");if(selected!=null&&graph.auxiliary().getOrDefault("generation_candidate",List.of()).stream().filter(row->selected.equals(row.get("id"))).count()!=1)throw new IllegalArgumentException("Undeclared candidate decision");scope.writePurpose=purpose;scope.preparedWrite=prepared;scope.decidedCandidate=selected;scope.writing=true;
+  if(!(value instanceof JdbcTranscriptAuthority.MinutesScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||!scope.actor.equals(actor)||!scope.meeting.equals(graph.discovery().meeting())||scope.graph!=graph||scope.writing||scope.action!=MeetingAccess.Action.EDIT)throw new IllegalArgumentException("Foreign, expired or reused minutes write invocation");registry.access(gate);Objects.requireNonNull(purpose);Objects.requireNonNull(prepared);String kind=graph.discovery().kind();boolean valid=switch(purpose){case INITIAL_SETTINGS->kind.equals("INITIAL_SETTINGS");case GENERATION_INPUT->kind.equals("CURRENT_GENERATION");case GENERATION_COMPLETION,STALE_RETENTION->kind.equals("STORED_INPUT");case ADOPT->kind.equals("ADOPT");case SETTINGS->Set.of("SETTINGS_TEMPLATE","SETTINGS_EXCLUSIONS").contains(kind);case REVIEW->Set.of("REVIEW_BODY","REVIEW_ELEMENT").contains(kind);case FINAL_CONFIRM->kind.equals("READINESS");case PUBLICATION_SUBMIT->kind.equals("FINAL");case EDIT->Set.of("BODY_EDIT","BODY_RESTORE","SET_EDIT","SET_RESTORE").contains(kind);default->false;};if(!valid)throw new IllegalArgumentException("Wrong minutes invocation purpose");if(selected!=null&&!Set.of(MinutesWritePurpose.ADOPT,MinutesWritePurpose.REJECT,MinutesWritePurpose.DISPOSE).contains(purpose))throw new IllegalArgumentException("Unexpected candidate decision");if(selected!=null&&graph.auxiliary().getOrDefault("generation_candidate",List.of()).stream().filter(row->selected.equals(row.get("id"))).count()!=1)throw new IllegalArgumentException("Undeclared candidate decision");scope.writePurpose=purpose;scope.preparedWrite=prepared;scope.decidedCandidate=selected;scope.writing=true;
  }
  void requireMinutesWrite(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph){
   if(!(value instanceof JdbcTranscriptAuthority.MinutesScope scope)||scope.issuer()!=this||scope.holder!=transaction()||scope.gate!=gate||!scope.actor.equals(actor)||scope.graph!=graph||!scope.writing)throw new IllegalArgumentException("Foreign or inactive minutes write scope");registry.access(gate);
  }
  void requireMinutesInvocation(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,MinutesWritePurpose purpose,Object prepared){requireMinutesWrite(value,gate,actor,graph);var scope=(MinutesScope)value;if(scope.writePurpose!=purpose||scope.preparedWrite!=prepared)throw new IllegalArgumentException("Foreign minutes purpose or prepared plan");}
  void finishMinutesGraphTransition(Object value,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,long revision){requireMinutesWrite(value,gate,actor,graph);var scope=(MinutesScope)value;if(scope.transition==null)throw MinutesPendingDiscovery.unavailable();finishMinutesTransition(scope.transition,revision,scope.decidedCandidate);}
  enum SavedPurpose { OWNER_INSPECTION, CURRENT, MUTATION }
  public interface HeldSavedTranscript {}
  private final class SavedScope implements HeldSavedTranscript {
   SavedTranscriptGraph complete;
   final Object holder=transaction();final FileRegistry.LockedMeeting gate;final Actor actor;final String meeting;final SavedPurpose purpose;
   SavedScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting,SavedPurpose purpose){this.gate=gate;this.actor=actor;this.meeting=meeting;this.purpose=purpose;}
   JdbcTranscriptAuthority issuer(){return JdbcTranscriptAuthority.this;}
  }
  Object savedScope(FileRegistry.LockedMeeting gate,Actor actor,String meeting,SavedPurpose purpose){

## backend/adapters/src/main/java/ama/transcript/MinutesEventReferences.java
--- a/backend/adapters/src/main/java/ama/transcript/MinutesEventReferences.java
+++ b/backend/adapters/src/main/java/ama/transcript/MinutesEventReferences.java
@@ -1,22 +1,30 @@
 package ama.transcript;
 import ama.task.OutboxReferenceValidator;
 import javax.sql.DataSource;
 import org.springframework.jdbc.core.JdbcTemplate;
 import java.util.Map;import java.util.Set;
 /** Actual DB reference validation, before the shared final outbox flush. */
 public final class MinutesEventReferences {
  private final JdbcTemplate j;
  public MinutesEventReferences(DataSource source){j=new JdbcTemplate(source);j.setQueryTimeout(2);}
- public Map<String,OutboxReferenceValidator> validators(){return Map.of("GENERATION_CANDIDATE",this::decision,"TEMPLATE_REVISION",this::template,"ELEMENT_SET",this::elements,"RESULT_REVIEW",this::review,"MINUTES_REVISION",this::saved,"GENERATION_INPUT",this::generation,"EXCLUSION_REVISION",event->{if("MINUTES_EXCLUSIONS_SAVED".equals(event.eventType())){exclusion(event);return;}if(event.taskId()!=null||!"MINUTES_SETTINGS_INITIALIZED".equals(event.eventType())||!"MEETING".equals(event.subject().kind()))throw new IllegalArgumentException("Invalid initial minutes event");var found=j.queryForList("SELECT x.id FROM exclusion_revision x JOIN meeting m ON m.id=x.meeting_id JOIN template_revision t ON t.id=m.template_revision_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE x.id=? AND m.id=? AND m.revision=? AND m.exclusion_revision_id=x.id AND x.revision=1 AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0 AND m.needs_generation=1 AND t.owner_id IS NULL AND NOT EXISTS(SELECT 1 FROM sensitive_exclusion e WHERE e.revision_id=x.id)",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid initial minutes reference");});}
+ public Map<String,OutboxReferenceValidator> validators(){return Map.of("PUBLICATION",this::publication,"FINAL_CONFIRMATION",this::finalConfirmation,"GENERATION_CANDIDATE",this::decision,"TEMPLATE_REVISION",this::template,"ELEMENT_SET",this::elements,"RESULT_REVIEW",this::review,"MINUTES_REVISION",this::saved,"GENERATION_INPUT",this::generation,"EXCLUSION_REVISION",event->{if("MINUTES_EXCLUSIONS_SAVED".equals(event.eventType())){exclusion(event);return;}if(event.taskId()!=null||!"MINUTES_SETTINGS_INITIALIZED".equals(event.eventType())||!"MEETING".equals(event.subject().kind()))throw new IllegalArgumentException("Invalid initial minutes event");var found=j.queryForList("SELECT x.id FROM exclusion_revision x JOIN meeting m ON m.id=x.meeting_id JOIN template_revision t ON t.id=m.template_revision_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE x.id=? AND m.id=? AND m.revision=? AND m.exclusion_revision_id=x.id AND x.revision=1 AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0 AND m.needs_generation=1 AND t.owner_id IS NULL AND NOT EXISTS(SELECT 1 FROM sensitive_exclusion e WHERE e.revision_id=x.id)",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid initial minutes reference");});}
+ private void publication(ama.contract.OutboxContracts.EventDraft event){
+  if(event.taskId()==null||!"MEETING".equals(event.subject().kind())||!Set.of("TASK_ENQUEUED","PUBLICATION_ACCEPTED","PUBLICATION_LOCAL_RECOVERED","TASK_RETRY","TASK_SUCCEEDED","TASK_FAILED","TASK_CANCELLED","TASK_STALE","TASK_RECONCILING","EXTERNAL_MARKED","EXTERNAL_UNKNOWN","EXTERNAL_CONTRADICTION").contains(event.eventType()))throw new IllegalArgumentException("Invalid publication event");
+  var found=j.queryForList("SELECT t.id FROM durable_task t JOIN publication p ON p.id=t.input_id WHERE t.id=? AND p.id=? AND p.meeting_id=? AND t.subject_kind='MEETING' AND t.subject_id=p.meeting_id AND t.task_type='KNOWLEDGE_PUBLISH_V1' AND t.pool='KNOWLEDGE_PUBLISH' AND t.input_kind='PUBLICATION' AND t.business_version=? AND t.priority=0 AND t.max_attempts=3",String.class,event.taskId(),event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid publication reference");if("PUBLICATION_LOCAL_RECOVERED".equals(event.eventType())&&j.queryForObject("SELECT COUNT(*) FROM publication_submission WHERE task_id=? AND publication_id=? AND local_state='NEVER_MARKED' AND local_decided_at IS NOT NULL",Long.class,event.taskId(),event.reference().id())!=1)throw new IllegalArgumentException("Actual local decision required");
+ }
+ private void finalConfirmation(ama.contract.OutboxContracts.EventDraft event){
+  if(event.taskId()!=null||!"MEETING".equals(event.subject().kind())||!"FINAL_CONFIRMED".equals(event.eventType()))throw new IllegalArgumentException("Invalid final event");
+  var found=j.queryForList("SELECT f.id FROM final_confirmation f JOIN meeting m ON m.id=f.meeting_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE f.id=? AND m.id=? AND m.revision=? AND m.final_confirmation_id=f.id AND f.meeting_revision=m.revision AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid final reference");
+ }
  private void decision(ama.contract.OutboxContracts.EventDraft event){if(event.taskId()!=null||!"MEETING".equals(event.subject().kind())||!Set.of("MINUTES_CANDIDATE_REJECTED","MINUTES_CANDIDATE_DISPOSED").contains(event.eventType()))throw new IllegalArgumentException("Invalid candidate decision event");var found=j.queryForList("SELECT c.id FROM generation_candidate c JOIN meeting m ON m.id=c.meeting_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE c.id=? AND m.id=? AND m.revision=? AND c.state='REJECTED' AND c.decided_at IS NOT NULL AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid candidate decision reference");}
  private void template(ama.contract.OutboxContracts.EventDraft event){settings(event,"MINUTES_TEMPLATE_SELECTED","template_revision_id");}
  private void exclusion(ama.contract.OutboxContracts.EventDraft event){settings(event,"MINUTES_EXCLUSIONS_SAVED","exclusion_revision_id");}
  private void settings(ama.contract.OutboxContracts.EventDraft event,String kind,String pointer){if(event.taskId()!=null||!"MEETING".equals(event.subject().kind())||!kind.equals(event.eventType()))throw new IllegalArgumentException("Invalid settings event");var found=j.queryForList("SELECT m.id FROM meeting m JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE m.id=? AND m.revision=? AND m."+pointer+"=? AND m.needs_generation=1 AND m.final_confirmation_id IS NULL AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0",String.class,event.subject().id(),event.subjectRevision(),event.reference().id());if(found.size()!=1)throw new IllegalArgumentException("Invalid settings reference");}
  private void elements(ama.contract.OutboxContracts.EventDraft event){if(event.taskId()!=null||!"MEETING".equals(event.subject().kind())||!Set.of("MINUTES_ELEMENT_EDITED","MINUTES_ACTION_FIELDS_SAVED","MINUTES_OPEN_QUESTION","MINUTES_SET_RESTORED").contains(event.eventType()))throw new IllegalArgumentException("Invalid element event");var found=j.queryForList("SELECT r.id FROM element_set r JOIN meeting m ON m.id=r.meeting_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE r.id=? AND m.id=? AND m.revision=? AND m.element_set_id=r.id AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid element reference");}
  private void review(ama.contract.OutboxContracts.EventDraft event){if(event.taskId()!=null||!"MEETING".equals(event.subject().kind())||!"MINUTES_REVIEWED".equals(event.eventType()))throw new IllegalArgumentException("Invalid review event");var found=j.queryForList("SELECT r.id FROM result_review r JOIN meeting m ON m.id=r.meeting_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE r.id=? AND m.id=? AND m.revision=? AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0 AND (r.minutes_revision_id=m.minutes_revision_id OR r.element_id IN(SELECT id FROM minutes_element WHERE set_id=m.element_set_id))",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid review reference");}
  private void saved(ama.contract.OutboxContracts.EventDraft event){if(event.taskId()!=null||!"MEETING".equals(event.subject().kind())||!Set.of("MINUTES_FIRST_SAVED","MINUTES_ADOPTED","MINUTES_SAVED","MINUTES_RESTORED").contains(event.eventType()))throw new IllegalArgumentException("Invalid saved minutes event");var found=j.queryForList("SELECT r.id FROM minutes_revision r JOIN meeting m ON m.id=r.meeting_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE r.id=? AND m.id=? AND m.revision=? AND m.minutes_revision_id=r.id AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid saved minutes reference");}
  private void generation(ama.contract.OutboxContracts.EventDraft event){
   if(event.taskId()==null||!"MEETING".equals(event.subject().kind())||!java.util.Set.of("MINUTES_GENERATION_ACCEPTED","TASK_ENQUEUED","TASK_RETRY","TASK_SUCCEEDED","TASK_FAILED","TASK_CANCELLED","TASK_STALE","TASK_RECONCILING","EXTERNAL_MARKED","EXTERNAL_UNKNOWN","EXTERNAL_CONTRADICTION").contains(event.eventType()))throw new IllegalArgumentException("Invalid generation event");
   var rows=j.queryForList("SELECT t.id FROM durable_task t JOIN generation_input g ON g.id=t.input_id WHERE t.id=? AND g.id=? AND g.meeting_id=? AND t.subject_kind='MEETING' AND t.subject_id=g.meeting_id AND t.task_type='MINUTES_GENERATE_V1' AND t.pool='MINUTES_GENERATE' AND t.input_kind='GENERATION_INPUT' AND t.business_version=? AND g.base_revision=t.business_version AND g.input_fingerprint=t.input_fingerprint AND t.priority=0 AND t.max_attempts=3 AND g.scope='FULL' AND g.target_element_id IS NULL AND g.material_snapshot_id IS NULL",String.class,event.taskId(),event.reference().id(),event.subject().id(),event.subjectRevision());if(rows.size()!=1)throw new IllegalArgumentException("Invalid generation event provenance");
  }
 }

## backend/adapters/src/main/java/ama/transcript/MinutesGraphCapture.java
--- a/backend/adapters/src/main/java/ama/transcript/MinutesGraphCapture.java
+++ b/backend/adapters/src/main/java/ama/transcript/MinutesGraphCapture.java
@@ -10,66 +10,72 @@
 import static ama.contract.MinutesContracts.FailureCode.*;
 import static ama.transcript.SavedTranscriptRows.*;
 
 /** Retained root/parent/row locks precede every complete bounded content transfer.
  * Captured graph is detached data; only the private authority scope records successful acquisition. */
 final class MinutesGraphCapture {
  record Graph(MinutesGraphDiscovery.Discovery discovery,List<FullRow> rows,Map<String,List<Map<String,Object>>> auxiliary) {}
  static final List<String> DOMAIN_RANK=List.of("transcript_revision","transcript_segment","transcript_segment_mark","transcript_confirmation","transcription_input","input_batch","input_batch_fingerprint_profile","input_batch_file","minutes_template","template_revision","exclusion_revision","sensitive_exclusion","generation_input","minutes_revision","element_set","minutes_element","action_item","action_suggestion","sensitive_detection","background_note","source_locator","sensitive_detection_source","background_note_source","evidence_link","result_review","result_review_evidence","final_result_review","generation_candidate");
  static final Shape TEMPLATE=new Shape("minutes_template",List.of("id"),List.of("id","owner_id","name","name_normalized","name_hash","current_revision_id","revision","created_at"),Set.of("revision"),Set.of("created_at"));
  static final Shape CANDIDATE=new Shape("generation_candidate",List.of("id"),List.of("id","meeting_id","input_id","task_id","minutes_revision_id","element_set_id","base_revision","input_fingerprint","state","created_at","decided_at"),Set.of("base_revision"),Set.of("created_at","decided_at"));
  static Graph capture(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget){
   return captureMode(authority,scope,gate,actor,expected,budget,false,null);
  }
  static Graph captureCurrent(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget){return captureMode(authority,scope,gate,actor,expected,budget,true,null);}
  static Graph captureTask(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,boolean currentSources,Runnable publication){return captureMode(authority,scope,gate,actor,expected,budget,currentSources,publication);}
- private static Graph captureMode(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,boolean currentSources,Runnable publication){
+ static Graph capturePublication(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,Runnable controls,Runnable tasks){return captureMode(authority,scope,gate,actor,expected,budget,false,null,controls,tasks);}
+ private static Graph captureMode(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,boolean currentSources,Runnable publication){return captureMode(authority,scope,gate,actor,expected,budget,currentSources,publication,null,null);}
+ static Graph captureReplacement(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,String publication,Runnable controls,Runnable tasks){return captureMode(authority,scope,gate,actor,expected,budget,true,null,controls,tasks,publication);}
+ private static Graph captureMode(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,boolean currentSources,Runnable publication,Runnable publicationControls,Runnable publicationTasks){return captureMode(authority,scope,gate,actor,expected,budget,currentSources,publication,publicationControls,publicationTasks,null);}
+ private static Graph captureMode(JdbcTranscriptAuthority authority,Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphDiscovery.Discovery expected,TranscriptAcquisitionBudget.Ledger budget,boolean currentSources,Runnable publication,Runnable publicationControls,Runnable publicationTasks,String replacementPublication){
   authority.requireMinutesScope(scope,gate,actor,expected.meeting());budget.check();
   MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,"meeting",budget);
   var j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);var read=new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes());
   var rows=new ArrayList<FullRow>();var auxiliary=new TreeMap<String,List<Map<String,Object>>>();var transfers=new ArrayList<Supplier<List<FullRow>>>();var auxiliaryTransfers=new LinkedHashMap<String,List<Supplier<List<Map<String,Object>>>>>();
   // Control rows precede all domain locks. Missing editor guard remains an explicit empty set.
   auxiliary.put("meeting_minutes_editor_guard",read.read(metadata("meeting_minutes_editor_guard","meeting_id","meeting_id intent_epoch#"),"meeting_id=?","meeting_id",1,false,expected.meeting()));
   for(String name:List.of("meeting_info_revision","roster_revision","roster_member")){
    MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,name,budget);
    Shape control=switch(name){case "meeting_info_revision"->new Shape(name,List.of("id"),List.of("id","meeting_id","revision","name","category_id","summary","actual_meeting_date","created_by","created_at"),Set.of("revision"),Set.of("created_at"));case "roster_revision"->new Shape(name,List.of("id"),List.of("id","meeting_id","revision","state","confirmed_at","confirmed_by"),Set.of("revision"),Set.of("confirmed_at"));default->metadata(name,"roster_id,member_id","roster_id member_id enterprise_id display_name member_kind");};
    for(var row:expected.tables().getOrDefault(name,List.of()))auxiliaryTransfers.computeIfAbsent(name,k->new ArrayList<>()).add(read.deferRead(control,String.join(" AND ",control.keys().stream().map(k->k+"=?").toList()),String.join(",",control.keys()),1,false,control.keys().stream().map(row::get).toArray()));
   }
   auxiliary.put("publication",read.read(metadata("publication","id","id meeting_id confirmation_id state revision#"),"meeting_id=?","id",1,false,expected.meeting()));
-  if(expected.pending()!=null){require(auxiliary.get("publication").equals(expected.pending().rows().get("publication")),PROVENANCE_UNAVAILABLE);if(!expected.kind().equals("READINESS"))require(auxiliary.get("publication").isEmpty(),CAPABILITY_UNAVAILABLE);}
+  if(expected.pending()!=null){require(auxiliary.get("publication").equals(expected.pending().rows().get("publication")),PROVENANCE_UNAVAILABLE);if(replacementPublication!=null){var existing=auxiliary.get("publication");require(existing.size()==1&&replacementPublication.equals(existing.getFirst().get("id"))&&"FAILED".equals(existing.getFirst().get("state")),CAPABILITY_UNAVAILABLE);}else if(!expected.kind().equals("READINESS"))require(auxiliary.get("publication").isEmpty(),CAPABILITY_UNAVAILABLE);}
   if(publication!=null)publication.run();
   var finalIds=new TreeSet<String>();if(expected.root().get("final_confirmation_id") instanceof String id)finalIds.add(id);for(var row:expected.tables().getOrDefault("final_confirmation",List.of()))finalIds.add((String)row.get("id"));
   MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,"final_confirmation",budget);
   for(String id:finalIds){if(expected.kind().equals("FINAL")&&id.equals(expected.selected()))transfers.add(read.deferRows(Table.FINAL_CONFIRMATION,"id=?","id",1,false,id));else auxiliaryTransfers.computeIfAbsent("final_control",k->new ArrayList<>()).add(read.deferRead(shape(Table.FINAL_CONFIRMATION),"id=?","id",1,false,id));}
+  if(publicationControls!=null)publicationControls.run();
   var byName=new HashMap<String,Table>();for(Table t:Table.values())byName.put(t.sqlName(),t);
   for(String name:DOMAIN_RANK){
    if(name.equals("minutes_revision")||name.equals("element_set")||name.equals("exclusion_revision")){
     budget.row(2);budget.capture(36,36,36,4);String index=name.equals("minutes_revision")?"minutes_history":name.equals("element_set")?"element_set_revision":"exclusion_version";
     var highest=j.queryForList("SELECT id,revision FROM "+name+" FORCE INDEX ("+index+") WHERE meeting_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",expected.meeting());
     auxiliary.put(name+"_history_max",List.copyOf(highest));
    }
    if(name.equals("result_review")){budget.row(2);budget.capture(36,36,36,4);auxiliary.put("result_review_history_max",List.copyOf(j.queryForList("SELECT id,review_no FROM result_review FORCE INDEX (review_sequence) WHERE meeting_id=? ORDER BY review_no DESC LIMIT 1 FOR UPDATE",expected.meeting())));}
    MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,name,budget);
    var declared=expected.tables().getOrDefault(name,List.of());if(declared.isEmpty())continue;
    Table table=byName.get(name);Shape shape=table!=null?shape(table):name.equals("minutes_template")?TEMPLATE:name.equals("generation_candidate")?CANDIDATE:metadata("input_batch_fingerprint_profile","batch_id","batch_id profile");
    for(var row:declared){
     budget.check();String where=String.join(" AND ",shape.keys().stream().map(k->k+"=?").toList());Object[] keys=shape.keys().stream().map(row::get).toArray();String order=String.join(",",shape.keys());
     if(table!=null)transfers.add(read.deferRows(table,where,order,1,true,keys));
     else auxiliaryTransfers.computeIfAbsent(name,k->new ArrayList<>()).add(read.deferRead(shape,where,order,1,false,keys));
    }
   }
   for(String name:MinutesSourceRows.RANK){
+   if(name.equals("durable_task")&&publicationTasks!=null)publicationTasks.run();
    MinutesGraphDiscovery.lockAndCompare(authority.dataSource(),expected,name,budget);
    Shape shape=MinutesSourceRows.SHAPES.get(name);var declared=new ArrayList<>(expected.tables().getOrDefault(name,List.of()));
    if(name.equals("external_execution"))declared.sort(Comparator.comparing((Map<String,Object> r)->(String)r.get("operation_id")).thenComparingLong(r->(Long)r.get("execution_no")));
    for(var row:declared)auxiliaryTransfers.computeIfAbsent(name,k->new ArrayList<>()).add(read.deferRead(shape,String.join(" AND ",shape.keys().stream().map(k->k+"=?").toList()),String.join(",",shape.keys()),1,false,shape.keys().stream().map(row::get).toArray()));
    if(currentSources&&name.equals("file_reference")){
    var ids=expected.tables().getOrDefault("file_object",List.of()).stream().map(r->(String)r.get("id")).sorted().toList();if(ids.isEmpty()&&Set.of("SAVED_EFFECTIVE","READINESS").contains(expected.kind())&&expected.root().get("minutes_revision_id")==null&&expected.root().get("element_set_id")==null)continue;require(!ids.isEmpty(),PROVENANCE_UNAVAILABLE);authority.requireTaskPurpose(actor,expected.meeting());
    var locked=authority.fileRegistry().prelockSources(gate,ids,false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));var originals=authority.fileRegistry().sources(gate,locked,ids);require(originals.size()==ids.size(),PROVENANCE_UNAVAILABLE);
    for(var source:originals){var row=expected.tables().get("file_object").stream().filter(r->source.file().fileId().equals(r.get("id"))).findFirst().orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));require(JdbcTranscriptAuthority.PURPOSE.equals(source.originalPurpose())&&source.file().sha256().equals(row.get("sha256"))&&source.file().size()==((Number)row.get("size_bytes")).longValue(),PROVENANCE_UNAVAILABLE);}
   }
   }
   // No existing-row lock is discovered by a deferred content SELECT; exact protected PKs only.
   for(var transfer:transfers){var found=transfer.get();require(found.size()==1,PROVENANCE_UNAVAILABLE);rows.addAll(found);}
   for(var group:auxiliaryTransfers.entrySet()){var found=new ArrayList<Map<String,Object>>();for(var transfer:group.getValue()){var one=transfer.get();require(one.size()==1,PROVENANCE_UNAVAILABLE);found.addAll(one);}auxiliary.put(group.getKey(),List.copyOf(found));}
   var graph=new Graph(expected,List.copyOf(rows),Collections.unmodifiableMap(auxiliary));authority.sealMinutesCapture(scope,gate,actor,graph);return graph;
  }

## backend/adapters/src/main/java/ama/transcript/MinutesGraphDiscovery.java
--- a/backend/adapters/src/main/java/ama/transcript/MinutesGraphDiscovery.java
+++ b/backend/adapters/src/main/java/ama/transcript/MinutesGraphDiscovery.java
@@ -13,60 +13,63 @@
  record Discovery(String meeting,String kind,String selected,Map<String,Object> root,
     Map<String,List<Map<String,Object>>> tables,Map<String,List<List<Object>>> memberships,Set<String> identities,List<Selector> selectors,MinutesPendingDiscovery.Plan pending) {
   Discovery(String meeting,String kind,String selected,Map<String,Object> root,Map<String,List<Map<String,Object>>> tables,Map<String,List<List<Object>>> memberships,Set<String> identities,List<Selector> selectors){this(meeting,kind,selected,root,tables,memberships,identities,selectors,null);}
  }
  record Selector(String table,String column,Object value,String qualifier) {}
  private record Definition(String name,List<String> keys,List<String> columns) {}
  private record Query(String table,String column,Object value,int depth,boolean required,String qualifier) {Query(String table,String column,Object value,int depth,boolean required){this(table,column,value,depth,required,"");}}
  private static final Set<String> CONTENT=Set.of("body","title","name","themes","reason","target_anchor","section_path","owner_display","owner_mention","config_manifest");
  private static final Map<String,Definition> DEFINITIONS=definitions();
  private final JdbcTemplate j;private final String meeting,kind,selected;private final int maximum;private int taskMaximum=256,fileMaximum=64,revisionMaximum=256,depthMaximum=128;private long byteMaximum=16777216;
  private final ArrayDeque<Query> pending=new ArrayDeque<>();
  private final Map<String,Map<List<Object>,Map<String,Object>>> tables=new TreeMap<>();
  private final Map<String,List<List<Object>>> memberships=new TreeMap<>();
  private final Map<String,Selector> selectors=new TreeMap<>();
  private final Set<String> queried=new HashSet<>(),identities=new TreeSet<>();
- private long scanned,bytes;private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
+ private boolean publicationHistory;private long scanned,bytes;private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
  private MinutesGraphDiscovery(DataSource source,String meeting,String kind,String selected,int maximum){
   uuid(meeting);require(maximum>0&&maximum<=100000,INVALID_ARGUMENT);this.j=new JdbcTemplate(source);j.setQueryTimeout(2);this.meeting=meeting;this.kind=kind;this.selected=selected;this.maximum=maximum;
  }
  static Discovery discover(DataSource source,String meeting,String kind,String selected,int maximum){return new MinutesGraphDiscovery(source,meeting,kind,selected,maximum).run();}
  static Discovery discover(DataSource source,String meeting,String kind,String selected,TranscriptAcquisitionBudget limits,ama.contract.ConfigArchiveContracts.ArchiveReadBudget read){var value=new MinutesGraphDiscovery(source,meeting,kind,selected,(int)Math.min(100000,read.maxFactRows()));value.taskMaximum=Math.min(256,limits.tasks());value.fileMaximum=Math.min(64,read.maxBatchFiles());value.revisionMaximum=Math.min(256,limits.revisionNodes());value.depthMaximum=Math.min(128,limits.revisionDepth());value.byteMaximum=Math.min(16777216,Math.min(limits.copyBytes(),Math.min(limits.utf8(),read.maxTotalBytes())));return value.run();}
+ static Discovery publicationHistory(DataSource source,String meeting,String confirmation,TranscriptAcquisitionBudget limits,ama.contract.ConfigArchiveContracts.ArchiveReadBudget read){var value=new MinutesGraphDiscovery(source,meeting,"FINAL",confirmation,(int)Math.min(100000,read.maxFactRows()));value.taskMaximum=Math.min(256,limits.tasks());value.fileMaximum=Math.min(64,read.maxBatchFiles());value.revisionMaximum=Math.min(256,limits.revisionNodes());value.depthMaximum=Math.min(128,limits.revisionDepth());value.byteMaximum=Math.min(16777216,Math.min(limits.copyBytes(),Math.min(limits.utf8(),read.maxTotalBytes())));value.publicationHistory=true;return value.run();}
+ static Discovery identities(Discovery d,Collection<String> additional){var ids=new TreeSet<>(d.identities());ids.addAll(additional);return new Discovery(d.meeting(),d.kind(),d.selected(),d.root(),d.tables(),d.memberships(),Set.copyOf(ids),d.selectors(),d.pending());}
  static Discovery withPending(DataSource source,Discovery d,int taskLimit){var pending=MinutesPendingDiscovery.discover(source,d.meeting(),d.tables().getOrDefault("durable_task",List.of()).stream().map(r->(String)r.get("id")).toList());MinutesDomainCapacity.reserve(MinutesDomainCapacity.merge(d.tables(),pending.rows()),taskLimit,256,null);return new Discovery(d.meeting(),d.kind(),d.selected(),d.root(),d.tables(),d.memberships(),d.identities(),d.selectors(),pending);}
  static List<String> metadataColumns(String table){return DEFINITIONS.get(table).columns();}
  private Discovery run(){
   var roots=select(new Query("meeting","id",meeting,0,true));require(roots.size()==1,PROVENANCE_UNAVAILABLE);var root=roots.getFirst();identities.add(text(root,"owner_id"));
   parent("meeting_info_revision",root.get("info_revision_id"),1);parent("roster_revision",root.get("roster_revision_id"),1);parent("final_confirmation",root.get("final_confirmation_id"),1);
   switch(kind){
    case "INITIAL_SETTINGS"->parent("template_revision",requiredSelection(),1);
    case "CURRENT_GENERATION"->{for(String col:List.of("transcript_confirmation_id","template_revision_id","exclusion_revision_id","element_set_id","minutes_revision_id"))parent(tableFor(col),root.get(col),1);unsupported(root,"material_snapshot_id");}
    case "SAVED_EFFECTIVE","READINESS","SETTINGS_READ"->{parent("minutes_revision",root.get("minutes_revision_id"),1);parent("element_set",root.get("element_set_id"),1);parent("exclusion_revision",root.get("exclusion_revision_id"),1);parent("template_revision",root.get("template_revision_id"),1);parent("transcript_confirmation",root.get("transcript_confirmation_id"),1);}
    case "OWNER_SET"->parent("element_set",requiredSelection(),1);
    case "REVIEW_ID"->parent("result_review",requiredSelection(),1);
    case "BODY_EDIT","BODY_RESTORE"->{parent("minutes_revision",requiredSelection(),1);parent("transcript_confirmation",root.get("transcript_confirmation_id"),1);}
    case "SET_EDIT","SET_RESTORE"->{parent("element_set",requiredSelection(),1);parent("transcript_confirmation",root.get("transcript_confirmation_id"),1);}
    case "OWNER_REVISION"->parent("minutes_revision",requiredSelection(),1);
    case "STORED_INPUT"->{parent("generation_input",requiredSelection(),1);if(j.queryForList("SELECT id FROM generation_candidate WHERE input_id=? ORDER BY id LIMIT 2",String.class,selected).isEmpty())parent("minutes_revision",root.get("minutes_revision_id"),1);}
    case "REUSE_SOURCES"->{require(selected!=null&&selected.length()==73,INVALID_ARGUMENT);var ids=selected.split("/",-1);require(ids.length==2,INVALID_ARGUMENT);uuid(ids[0]);uuid(ids[1]);parent("generation_candidate",ids[0],1);parent("exclusion_revision",ids[1],1);parent("transcript_confirmation",root.get("transcript_confirmation_id"),1);parent("template_revision",root.get("template_revision_id"),1);}
    case "SETTINGS_TEMPLATE","SETTINGS_EXCLUSIONS"->{for(String col:List.of("minutes_revision_id","element_set_id","transcript_confirmation_id","template_revision_id","exclusion_revision_id"))parent(tableFor(col),root.get(col),1);if(kind.equals("SETTINGS_TEMPLATE"))parent("template_revision",requiredSelection(),1);}
    case "ADOPT"->{parent("generation_candidate",requiredSelection(),1);parent("minutes_revision",root.get("minutes_revision_id"),1);}
    case "CANDIDATE"->parent("generation_candidate",requiredSelection(),1);
    case "REVIEW_BODY"->parent("minutes_revision",requiredSelection(),1);
    case "REVIEW_ELEMENT"->parent("minutes_element",requiredSelection(),1);
    case "FINAL"->parent("final_confirmation",requiredSelection(),1);
    default->throw fail(INVALID_ARGUMENT);
   }
+  if(publicationHistory)children("final_confirmation","meeting_id",meeting,1);
   while(!pending.isEmpty()){
    check();Query query=pending.removeFirst();require(query.depth()<=depthMaximum,BUDGET_EXCEEDED);String queryKey=query.table()+"|"+query.column()+"|"+query.value()+query.qualifier();
    if(!queried.add(queryKey)){if(query.required())require(!memberships.get(queryKey).isEmpty(),PROVENANCE_UNAVAILABLE);continue;}
    var found=select(query);if(query.required())require(found.size()==1,PROVENANCE_UNAVAILABLE);
    var keys=new ArrayList<List<Object>>();var definition=DEFINITIONS.get(query.table());var group=tables.computeIfAbsent(query.table(),k->new HashMap<>());
    for(var row:found){
     if(row.containsKey("meeting_id"))require(meeting.equals(row.get("meeting_id")),PROVENANCE_UNAVAILABLE);
     List<Object> key=definition.keys().stream().map(row::get).toList();keys.add(key);Map<String,Object> old=group.putIfAbsent(key,row);
     if(old==null){require(group.size()<=switch(query.table()){case "durable_task"->taskMaximum;case "file_object"->fileMaximum;case "generation_input","transcription_input","transcript_revision","minutes_revision"->revisionMaximum;default->maximum;},BUDGET_EXCEEDED);expand(query.table(),row,query.depth()+1);}else require(old.equals(row),PROVENANCE_UNAVAILABLE);
    }
    memberships.put(queryKey,List.copyOf(keys));
   }
   var complete=new TreeMap<String,List<Map<String,Object>>>();
   for(var e:tables.entrySet()){var definition=DEFINITIONS.get(e.getKey());var rows=new ArrayList<>(e.getValue().values());rows.sort((a,b)->compare(definition.keys().stream().map(a::get).toList(),definition.keys().stream().map(b::get).toList()));complete.put(e.getKey(),List.copyOf(rows));}
   return new Discovery(meeting,kind,selected,root,Collections.unmodifiableMap(complete),Collections.unmodifiableMap(new TreeMap<>(memberships)),Set.copyOf(identities),List.copyOf(selectors.values()));

## backend/adapters/src/main/java/ama/transcript/PublicationGraph.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/PublicationGraph.java
@@ -0,0 +1,25 @@
+package ama.transcript;
+import javax.sql.DataSource;
+import org.springframework.jdbc.core.JdbcTemplate;
+import java.util.*;
+import static ama.contract.MinutesContracts.*;
+import static ama.contract.MinutesContracts.FailureCode.*;
+
+/** Bounded publication controls and complete shared task union, declared before root acquisition. */
+final class PublicationGraph {
+ record Plan(String meeting,List<MinutesPendingDiscovery.Query> controls,List<MinutesPendingDiscovery.Query> tasks,Map<String,List<Map<String,Object>>> rows){}
+ static Plan discover(DataSource source,String meeting){var j=new JdbcTemplate(source);j.setQueryTimeout(2);var controls=new ArrayList<MinutesPendingDiscovery.Query>();var tasks=new ArrayList<MinutesPendingDiscovery.Query>();var rows=new LinkedHashMap<String,List<Map<String,Object>>>();
+  controls.add(q("publication_submission","task_id publication_id submission_no confirmation_id provider_id business_key library_id folder_id requester_id logical_request_hash execution_request_hash created_at local_state local_decided_at local_reason local_decided_by local_attempt_no local_execution_id","task_id","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)",meeting,List.of("OCTET_LENGTH(request_manifest) AS request_manifest_bytes","CHAR_LENGTH(request_manifest) AS request_manifest_scalars","OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2 AS request_manifest_utf16")));
+  controls.add(q("external_document_mapping","publication_id provider_id external_document_id authority_state verified_at","publication_id","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)",meeting,List.of("OCTET_LENGTH(document_url) AS document_url_bytes","CHAR_LENGTH(document_url) AS document_url_scalars","OCTET_LENGTH(CONVERT(document_url USING utf16))/2 AS document_url_utf16")));
+  controls.add(q("publication_attempt","id publication_id confirmation_id operation_id kind outcome created_at finished_at execution_id","id","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)",meeting,List.of()));
+  for(String table:List.of("durable_task","task_attempt","external_operation","external_execution")){
+   var shape=MinutesSourceRows.SHAPES.get(table);var cols=new ArrayList<>(shape.columns().stream().filter(c->!MinutesSourceRows.CONTENT.contains(c)).toList());String where=switch(table){case "durable_task"->"subject_kind='MEETING' AND subject_id=?";case "task_attempt"->"task_id IN (SELECT id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=?)";case "external_operation"->"subject_kind='MEETING' AND subject_id=?";default->"operation_id IN (SELECT id FROM external_operation WHERE subject_kind='MEETING' AND subject_id=?)";};
+   if(table.equals("external_execution")){cols.add("OCTET_LENGTH(request_manifest) AS request_manifest_bytes");cols.add("CASE WHEN no_effect_evidence IS NOT NULL AND OCTET_LENGTH(no_effect_evidence)>0 THEN 1 ELSE 0 END AS no_effect_evidence_present");cols.add("CASE WHEN no_effect_evidence REGEXP '^knowledge:[0-9a-f-]{36}:receipt-[0-9a-f]{32}:[0-9a-f]{64}$' THEN 1 ELSE 0 END AS provider_proof_reference_shape");}if(table.equals("external_operation"))cols.add("CASE WHEN external_result_id IS NOT NULL THEN 1 ELSE 0 END AS external_result_present");tasks.add(new MinutesPendingDiscovery.Query(table,cols,table.equals("external_execution")?List.of("operation_id","execution_no"):shape.keys(),where,List.of(meeting)));
+  }
+  for(var q:controls)rows.put(q.table(),MinutesPendingDiscovery.read(j,q,false));for(var q:tasks)rows.put(q.table(),MinutesPendingDiscovery.read(j,q,false));reserve(rows,false);return new Plan(meeting,List.copyOf(controls),List.copyOf(tasks),Map.copyOf(rows));
+ }
+ private static MinutesPendingDiscovery.Query q(String table,String columns,String key,String where,String meeting,List<String> extra){var cols=new ArrayList<>(List.of(columns.split(" ")));cols.addAll(extra);return new MinutesPendingDiscovery.Query(table,List.copyOf(cols),List.of(key),where,List.of(meeting));}
+ static void lock(DataSource source,Plan plan,boolean controls){var j=new JdbcTemplate(source);j.setQueryTimeout(2);for(var q:controls?plan.controls():plan.tasks())require(MinutesPendingDiscovery.read(j,q,true).equals(plan.rows().get(q.table())),PROVENANCE_UNAVAILABLE);}
+ static void lockReplacement(DataSource source,Plan plan,String insertedTask){var j=new JdbcTemplate(source);j.setQueryTimeout(2);for(var query:plan.tasks()){var q=query;if(q.table().equals("durable_task")){var args=new ArrayList<>(q.args());args.add(insertedTask);q=new MinutesPendingDiscovery.Query(q.table(),q.columns(),q.keys(),"("+q.where()+") AND id<>?",args);}require(MinutesPendingDiscovery.read(j,q,true).equals(plan.rows().get(q.table())),PROVENANCE_UNAVAILABLE);}}
+ static void reserve(Map<String,List<Map<String,Object>>> rows,boolean prospective){long tasks=rows.get("durable_task").size()+(prospective?1:0),attempts=rows.get("task_attempt").size()+(prospective?3:0),operations=rows.get("external_operation").size()+(prospective?1:0),executions=rows.get("external_execution").size()+(prospective?1:0),pubAttempts=rows.get("publication_attempt").size()+(prospective?1:0);for(var task:rows.get("durable_task")){String type=(String)task.get("task_type");require(Set.of("TRANSCRIPT_IMPORT_V1","MINUTES_GENERATE_V1","KNOWLEDGE_PUBLISH_V1").contains(type),CAPABILITY_UNAVAILABLE);if(Set.of("READY","RUNNING").contains(task.get("state")))attempts+=((Number)task.get("max_attempts")).longValue()-((Number)task.get("attempt")).longValue();if(!"TRANSCRIPT_IMPORT_V1".equals(type)&&Set.of("READY","RUNNING","RECONCILING").contains(task.get("state"))&&rows.get("external_execution").stream().noneMatch(e->task.get("id").equals(e.get("task_id")))){operations++;executions++;if("KNOWLEDGE_PUBLISH_V1".equals(type))pubAttempts++;}}require(tasks<=256&&attempts<=256&&operations<=256&&executions<=256&&pubAttempts<=256&&rows.get("publication_submission").size()+(prospective?1:0)<=256,BUDGET_EXCEEDED);}
+}

## backend/adapters/src/main/java/ama/transcript/PublicationLocalDecision.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/PublicationLocalDecision.java
@@ -0,0 +1,31 @@
+package ama.transcript;
+import java.util.*;import ama.contract.TaskContracts.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
+/** Complete predeclared metadata eligibility. No text transfer, parsing, archive or provider calls. */
+final class PublicationLocalDecision {
+ record Eligible(String publication,long submissionNo,String task,String execution){}
+ static Eligible inspect(PublicationGraph.Plan plan,TaskSpec spec,String selected){return inspect(plan,spec,selected,false);}
+ static void requireClosed(PublicationGraph.Plan plan,String publication){var tasks=plan.rows().get("durable_task").stream().filter(t->"PUBLICATION".equals(t.get("input_kind"))&&publication.equals(t.get("input_id"))).toList();require(!tasks.isEmpty(),PROVENANCE_UNAVAILABLE);require(inspect(plan,TranscriptReadGraph.spec(tasks.getFirst()),"",true)!=null,CAPABILITY_UNAVAILABLE);for(var operation:plan.rows().get("external_operation"))if(((String)operation.get("business_key")).startsWith(publication+":"))require("FAILED".equals(operation.get("state")),PROVENANCE_UNAVAILABLE);}
+ private static Eligible inspect(PublicationGraph.Plan plan,TaskSpec spec,String selected,boolean closed){
+  String publication=spec.input().id();var r=plan.rows();var submissions=r.get("publication_submission").stream().filter(s->publication.equals(s.get("publication_id"))).sorted(Comparator.comparingLong(s->number(s,"submission_no"))).toList();var tasks=r.get("durable_task").stream().filter(t->"PUBLICATION".equals(t.get("input_kind"))&&publication.equals(t.get("input_id"))).toList();require(!submissions.isEmpty()&&submissions.size()==tasks.size(),PROVENANCE_UNAVAILABLE);if(!r.get("external_document_mapping").stream().filter(m->publication.equals(m.get("publication_id"))).toList().isEmpty())return null;
+  var selectedRows=tasks.stream().filter(t->selected.equals(t.get("id"))).toList();require(closed||selectedRows.size()==1&&TranscriptReadGraph.spec(selectedRows.getFirst()).equals(spec),PROVENANCE_UNAVAILABLE);var taskIds=new HashSet<String>();Eligible answer=null;long no=0;
+  for(var s:submissions){require(number(s,"submission_no")==++no,PROVENANCE_UNAVAILABLE);String id=(String)s.get("task_id");require(taskIds.add(id),PROVENANCE_UNAVAILABLE);var matches=tasks.stream().filter(t->id.equals(t.get("id"))).toList();require(matches.size()==1,PROVENANCE_UNAVAILABLE);var t=matches.getFirst();require("KNOWLEDGE_PUBLISH_V1".equals(t.get("task_type"))&&"KNOWLEDGE_PUBLISH".equals(t.get("pool"))&&"MEETING".equals(t.get("subject_kind"))&&plan.meeting().equals(t.get("subject_id"))&&s.get("logical_request_hash").equals(t.get("input_fingerprint"))&&(publication+":"+s.get("confirmation_id")).equals(s.get("business_key")),PROVENANCE_UNAVAILABLE);if(t.get("output_kind")!=null||t.get("output_id")!=null)return null;
+   var ordinary=r.get("task_attempt").stream().filter(a->id.equals(a.get("task_id"))).toList();require(ordinary.size()==number(t,"attempt"),PROVENANCE_UNAVAILABLE);var numbers=new HashSet<Long>();for(var a:ordinary)require(number(a,"attempt")>0&&number(a,"attempt")<=number(t,"attempt")&&numbers.add(number(a,"attempt")),PROVENANCE_UNAVAILABLE);
+   var executions=r.get("external_execution").stream().filter(e->id.equals(e.get("task_id"))).toList();require(executions.size()<=1,PROVENANCE_UNAVAILABLE);var e=executions.isEmpty()?null:executions.getFirst();var attempts=r.get("publication_attempt").stream().filter(a->e!=null&&e.get("id").equals(a.get("execution_id"))).toList();Map<String,Object> operation=null;
+   if(e!=null){var ops=r.get("external_operation").stream().filter(o->e.get("operation_id").equals(o.get("id"))).toList();require(ops.size()==1,PROVENANCE_UNAVAILABLE);operation=ops.getFirst();require("KNOWLEDGE_PUBLISH".equals(operation.get("operation_kind"))&&plan.meeting().equals(operation.get("subject_id"))&&"MEETING".equals(operation.get("subject_kind"))&&s.get("business_key").equals(operation.get("business_key"))&&s.get("execution_request_hash").equals(e.get("request_hash"))&&number(operation,"current_execution_no")>=number(e,"execution_no"),PROVENANCE_UNAVAILABLE);if(number(operation,"current_execution_no")==number(e,"execution_no"))require(operation.get("request_hash").equals(s.get("logical_request_hash")),PROVENANCE_UNAVAILABLE);if(number(operation,"external_result_present")!=0)return null;}
+   if(id.equals(selected)){
+    if(!"UNDECIDED".equals(s.get("local_state"))||!Set.of("READY","RUNNING","RECONCILING","FAILED","CANCELLED","STALE").contains(t.get("state"))||!attempts.isEmpty())return null;
+    if(e!=null){if(e.get("marked_at")!=null||e.get("task_attempt")!=null||number(operation,"current_execution_no")!=number(e,"execution_no"))return null;boolean prepared="PREPARED".equals(e.get("state"))&&"RETRY_QUEUED".equals(operation.get("state"))&&e.get("finished_at")==null&&e.get("no_effect_basis")==null;boolean failed="FAILED".equals(e.get("state"))&&"FAILED".equals(operation.get("state"))&&"NOT_SENT".equals(e.get("no_effect_basis"))&&e.get("no_effect_verified_at")!=null&&number(e,"no_effect_evidence_present")==1;if(!prepared&&!failed)return null;}
+    answer=new Eligible(publication,no,id,e==null?null:(String)e.get("id"));
+   }else {
+    if(!Set.of("FAILED","CANCELLED","STALE").contains(t.get("state"))||ordinary.stream().anyMatch(a->a.get("ended_at")==null))return null;
+    if("NEVER_MARKED".equals(s.get("local_state"))){if(s.get("local_decided_at")==null||s.get("local_decided_by")==null||s.get("local_reason")==null||number(s,"local_attempt_no")!=number(t,"attempt")||!attempts.isEmpty()||!Objects.equals(s.get("local_execution_id"),e==null?null:e.get("id")))return null;if(e!=null&&(!"FAILED".equals(e.get("state"))||!"NOT_SENT".equals(e.get("no_effect_basis"))||e.get("marked_at")!=null||e.get("task_attempt")!=null||e.get("no_effect_verified_at")==null||number(e,"no_effect_evidence_present")!=1))return null;
+    }else {if(e==null||!"FAILED".equals(t.get("state"))||!"FAILED".equals(e.get("state"))||!"DEFINITIVE_REJECTION".equals(e.get("no_effect_basis"))||e.get("marked_at")==null||e.get("task_attempt")==null||e.get("no_effect_verified_at")==null||number(e,"provider_proof_reference_shape")!=1||attempts.size()!=1)return null;var a=attempts.getFirst();require(publication.equals(a.get("publication_id"))&&s.get("confirmation_id").equals(a.get("confirmation_id"))&&e.get("operation_id").equals(a.get("operation_id")),PROVENANCE_UNAVAILABLE);if(!"FAILED".equals(a.get("outcome"))||a.get("finished_at")==null)return null;}
+   }
+  }
+  if(closed){var last=submissions.getLast();answer=new Eligible(publication,number(last,"submission_no"),(String)last.get("task_id"),(String)last.get("local_execution_id"));}require(answer!=null,PROVENANCE_UNAVAILABLE);if(answer.submissionNo()!=submissions.size())return null;
+  for(var a:r.get("publication_attempt"))if(publication.equals(a.get("publication_id")))require(r.get("external_execution").stream().anyMatch(e->a.get("execution_id").equals(e.get("id"))&&taskIds.contains(e.get("task_id"))),PROVENANCE_UNAVAILABLE);
+  for(var o:r.get("external_operation"))if(((String)o.get("business_key")).startsWith(publication+":"))require(r.get("external_execution").stream().anyMatch(e->o.get("id").equals(e.get("operation_id"))&&taskIds.contains(e.get("task_id"))),PROVENANCE_UNAVAILABLE);
+  return answer;
+ }
+ static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
+}

## backend/adapters/src/main/java/ama/transcript/PublicationRecordedLookup.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/PublicationRecordedLookup.java
@@ -0,0 +1,43 @@
+package ama.transcript;
+import ama.task.*;import ama.contract.ExternalContracts.*;import ama.contract.IdentityContracts.*;import java.util.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
+/** Original immutable receipt binding. No saved-content graph, POST request or business capability. */
+final class PublicationRecordedLookup {
+ record Capture(JdbcPublicationService.ReceiptIdentity identity,boolean businessAllowed,TranscriptAcquisitionBudget.Ledger budget){}
+ static Capture capture(JdbcPublicationService service,JdbcExternalOperations.RecordedLookup capability,OperationToken token){return captureAuthorized(service,new Reference(token.operationId(),token.executionId(),token.executionNo(),token.subject()),()->capability.require(service.authority.dataSource(),token));}
+ record Reference(String operationId,String executionId,long executionNo,ama.contract.TaskContracts.SubjectRef subject){}
+ private static Capture captureAuthorized(JdbcPublicationService service,Reference token,Runnable authorization){return captureAuthorized(service,token,authorization,service.limits.ledger(service.authority.readBudget(),service.authority.canonicalBudget(),()->false));}
+ static Capture captureReplacement(JdbcPublicationService service,Reference token,Actor actor,String publication,TranscriptAcquisitionBudget.Ledger budget){return captureAuthorized(service,token,()->service.authority.transactionRunner().required(()->{service.authority.savedAccess().require(service.authority.savedAccess().lock(actor,token.subject().id()),ama.access.MeetingAccess.Action.EDIT);require(service.j.queryForObject("SELECT COUNT(*) FROM publication p JOIN publication_submission s ON s.publication_id=p.id JOIN external_execution e ON e.task_id=s.task_id WHERE p.id=? AND p.meeting_id=? AND e.id=? AND e.operation_id=? AND e.execution_no=? AND e.state='FAILED'",Long.class,publication,token.subject().id(),token.executionId(),token.operationId(),token.executionNo())==1,PROVENANCE_UNAVAILABLE);return null;}),budget);}
+ private static Capture captureAuthorized(JdbcPublicationService service,Reference token,Runnable authorization,TranscriptAcquisitionBudget.Ledger budget){
+  var authority=service.authority;JdbcMinutesFactsReader.independent();authorization.run();var j=service.j;
+  budget.row(7);var metadata=j.queryForList("SELECT e.task_id,OCTET_LENGTH(e.request_manifest) execution_bytes,CHAR_LENGTH(e.request_manifest) execution_scalars,OCTET_LENGTH(CONVERT(e.request_manifest USING utf16))/2 execution_utf16,OCTET_LENGTH(s.request_manifest) submission_bytes,CHAR_LENGTH(s.request_manifest) submission_scalars,OCTET_LENGTH(CONVERT(s.request_manifest USING utf16))/2 submission_utf16 FROM external_execution e JOIN publication_submission s ON s.task_id=e.task_id WHERE e.id=? AND e.operation_id=? AND e.execution_no=?",token.executionId(),token.operationId(),token.executionNo());require(metadata.size()==1,PROVENANCE_UNAVAILABLE);for(String key:List.of("execution_bytes","execution_scalars","submission_bytes","submission_scalars","execution_utf16","submission_utf16"))require(metadata.getFirst().get(key) instanceof Number&&((Number)metadata.getFirst().get(key)).longValue()>0&&((Number)metadata.getFirst().get(key)).longValue()<=65536,BUDGET_EXCEEDED);String task=(String)metadata.getFirst().get("task_id");
+  var sizes=metadata.getFirst();long eb=((Number)sizes.get("execution_bytes")).longValue(),es=((Number)sizes.get("execution_scalars")).longValue(),eu=((Number)sizes.get("execution_utf16")).longValue(),sb=((Number)sizes.get("submission_bytes")).longValue(),ss=((Number)sizes.get("submission_scalars")).longValue(),su=((Number)sizes.get("submission_utf16")).longValue();require(eb<=authority.readBudget().maxManifestBytes()&&sb<=authority.readBudget().maxManifestBytes(),BUDGET_EXCEEDED);
+  // Reserve both retained JDBC strings and their owned UTF8 copies before either LONGTEXT transfer.
+  budget.text(eb,es,eu,2,false);budget.text(sb,ss,su,2,false);budget.row(25);
+
+  var rows=j.queryForList("SELECT e.request_manifest execution_manifest,e.request_hash execution_hash,s.request_manifest submission_manifest,s.publication_id,s.confirmation_id,s.provider_id,s.business_key,s.library_id,s.folder_id,s.requester_id,s.logical_request_hash,s.execution_request_hash,s.submission_no,o.operation_kind,o.business_key operation_key,o.subject_kind operation_subject_kind,o.subject_id operation_subject_id,o.request_hash current_logical_hash,o.current_execution_no,f.source_manifest_hash,f.input_fingerprint final_fingerprint,f.meeting_id final_meeting,p.meeting_id publication_meeting FROM external_execution e JOIN external_operation o ON o.id=e.operation_id JOIN publication_submission s ON s.task_id=e.task_id JOIN publication p ON p.id=s.publication_id JOIN final_confirmation f ON f.id=s.confirmation_id WHERE e.id=? AND e.operation_id=? AND e.execution_no=? AND e.task_id=? AND OCTET_LENGTH(e.request_manifest)=? AND CHAR_LENGTH(e.request_manifest)=? AND OCTET_LENGTH(CONVERT(e.request_manifest USING utf16))/2=? AND OCTET_LENGTH(s.request_manifest)=? AND CHAR_LENGTH(s.request_manifest)=? AND OCTET_LENGTH(CONVERT(s.request_manifest USING utf16))/2=?",token.executionId(),token.operationId(),token.executionNo(),task,eb,es,eu,sb,ss,su);require(rows.size()==1,PROVENANCE_UNAVAILABLE);var row=rows.getFirst();String manifest=(String)row.get("execution_manifest");require(manifest.equals(row.get("submission_manifest")),PROVENANCE_UNAVAILABLE);require(manifest.length()==eu&&manifest.codePointCount(0,manifest.length())==es,PROVENANCE_UNAVAILABLE);budget.capture(eb,es,eu,8);budget.admitCapture(Math.multiplyExact(eu,6),es,Math.multiplyExact(eu,6),2);byte[] bytes=manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8);require(bytes.length==eb,PROVENANCE_UNAVAILABLE);var m=ControlledKnowledgeClient.parse(bytes,65536);
+  var manifestKeys=new HashSet<>(m.keySet());manifestKeys.remove("bindingTransition");require(manifestKeys.equals(Set.of("schema","providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId","publicationId","confirmationId","requesterId","businessKey","libraryId","folderId","logicalRequestHash","executionRequestHash","payloadSha256","archiveIndexSha256","renderArtifact","meetingId","businessVersion","cancelEpoch","executionNo","submissionNo","priorProofs"))&&"ama.knowledge-publication-manifest.v1".equals(m.get("schema")),PROVENANCE_UNAVAILABLE);
+  byte[] canonical=ControlledKnowledgeClient.canonical(m);budget.capture(canonical.length,canonical.length,canonical.length,2);require(Arrays.equals(bytes,canonical),PROVENANCE_UNAVAILABLE);
+  for(var identity:service.client.identities().entrySet())require(identity.getValue().equals(m.get(identity.getKey())),PROVENANCE_UNAVAILABLE);
+  budget.row(12);var tasks=j.queryForList("SELECT task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,priority,cancel_epoch,max_attempts FROM durable_task WHERE id=?",task);require(tasks.size()==1,PROVENANCE_UNAVAILABLE);var spec=TranscriptReadGraph.spec(tasks.getFirst());PublicationTaskDomainPolicy.shape(spec);require(JdbcPublicationService.spec(m).equals(spec)&&spec.subject().equals(token.subject()),PROVENANCE_UNAVAILABLE);
+  for(var pair:Map.of("publicationId","publication_id","confirmationId","confirmation_id","requesterId","requester_id","providerId","provider_id","businessKey","business_key","libraryId","library_id","folderId","folder_id","logicalRequestHash","logical_request_hash","executionRequestHash","execution_request_hash").entrySet())require(Objects.equals(m.get(pair.getKey()),row.get(pair.getValue())),PROVENANCE_UNAVAILABLE);
+  require(spec.subject().id().equals(row.get("final_meeting"))&&spec.subject().id().equals(row.get("publication_meeting"))&&spec.subject().id().equals(row.get("operation_subject_id"))&&spec.subject().kind().equals(row.get("operation_subject_kind"))&&"KNOWLEDGE_PUBLISH".equals(row.get("operation_kind"))&&m.get("businessKey").equals(row.get("operation_key"))&&m.get("executionRequestHash").equals(row.get("execution_hash"))&&((Number)m.get("executionNo")).longValue()==token.executionNo()&&((Number)m.get("submissionNo")).longValue()==((Number)row.get("submission_no")).longValue(),PROVENANCE_UNAVAILABLE);
+  if(((Number)row.get("current_execution_no")).longValue()==token.executionNo())require(m.get("logicalRequestHash").equals(row.get("current_logical_hash")),PROVENANCE_UNAVAILABLE);
+  budget.capture(eb,es,eu,8);var logical=new TreeMap<String,Object>();logical.put("schema","ama.knowledge-publish.v1");logical.put("domain","KNOWLEDGE_PUBLISH");for(String key:List.of("providerId","publicationId","confirmationId","businessKey","libraryId","folderId","renderArtifact","payloadSha256"))logical.put(key,m.get(key));logical.put("sourceManifestHash",row.get("source_manifest_hash"));logical.put("inputFingerprint",row.get("final_fingerprint"));require(ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(logical)).equals(m.get("logicalRequestHash")),FINGERPRINT_MISMATCH);var execution=Map.of("schema","ama.knowledge-publish-execution.v1","logicalRequestHash",m.get("logicalRequestHash"),"executionNo",m.get("executionNo"),"priorProofs",m.get("priorProofs"));require(ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(execution)).equals(m.get("executionRequestHash")),FINGERPRINT_MISMATCH);
+  hash((String)m.get("payloadSha256"));hash((String)m.get("archiveIndexSha256"));var actor=new Actor((String)m.get("requesterId"),Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));var binding=new JdbcPublicationService.ReceiptIdentity(actor,spec,task,(String)m.get("publicationId"),(String)m.get("confirmationId"),(String)m.get("providerId"),(String)m.get("libraryId"),(String)m.get("folderId"),(String)m.get("businessKey"),(String)m.get("logicalRequestHash"),(String)m.get("executionRequestHash"),manifest,(String)m.get("payloadSha256"),token.executionNo());
+  boolean allowed=authority.transactionRunner().required(()->{var access=(ama.meeting.JdbcMeetingAccess)authority.meetingAccess();var identities=access.tryLockFactIdentities(actor,Set.of(actor.enterpriseId()),false).orElseThrow();var root=access.lockFactRoot(identities,spec.subject().id(),false).orElseThrow();var current=j.queryForMap("SELECT final_confirmation_id,ever_published FROM meeting WHERE id=?",spec.subject().id());return access.factActorActive(identities)&&access.factAllowsBusiness(root,spec.cancelEpoch(),spec.businessVersion())&&binding.confirmation().equals(current.get("final_confirmation_id"))&&((Number)current.get("ever_published")).intValue()==0;});authorization.run();budget.check();return new Capture(binding,allowed,budget);
+ }
+ /** Owner-only terminal receipt read. Its reference contains no claim, fence or expiry. */
+ static final class TerminalRead {
+  final Capture capture;final Reference reference;private final Runnable freshOwner;
+  private TerminalRead(Capture capture,Reference reference,Runnable freshOwner){this.capture=capture;this.reference=reference;this.freshOwner=freshOwner;}
+  void requireOwner(){freshOwner.run();}
+ }
+ static TerminalRead terminal(JdbcPublicationService service,Actor actor,String meeting,String publication){return terminal(service,actor,meeting,publication,"SUCCEEDED");}
+ static TerminalRead terminal(JdbcPublicationService service,Actor actor,String meeting,String publication,String expectedState){
+  require(Set.of("SUCCEEDED","FAILED").contains(expectedState),PROVENANCE_UNAVAILABLE);
+  uuid(meeting);uuid(publication);var authority=service.authority;var j=service.j;
+  Runnable owner=()->authority.transactionRunner().required(()->{authority.savedAccess().require(authority.savedAccess().lock(actor,meeting),ama.access.MeetingAccess.Action.EDIT);require(j.queryForObject("SELECT COUNT(*) FROM publication WHERE id=? AND meeting_id=? AND state=?",Long.class,publication,meeting,expectedState)==1,PROVENANCE_UNAVAILABLE);return null;});owner.run();
+  var rows=j.queryForList("SELECT o.id operation_id,e.id execution_id,e.execution_no FROM publication_attempt a JOIN external_execution e ON e.id=a.execution_id AND e.operation_id=a.operation_id JOIN external_operation o ON o.id=e.operation_id JOIN publication_submission s ON s.task_id=e.task_id AND s.publication_id=a.publication_id AND s.confirmation_id=a.confirmation_id JOIN durable_task t ON t.id=s.task_id WHERE a.publication_id=? AND a.outcome=? AND e.state=? AND o.state=? AND t.state=? AND t.subject_kind='MEETING' AND t.subject_id=? LIMIT 2",publication,expectedState.equals("SUCCEEDED")?"SUCCESS":"FAILED",expectedState,expectedState,expectedState,meeting);require(rows.size()==1,PROVENANCE_UNAVAILABLE);var row=rows.getFirst();var token=new Reference((String)row.get("operation_id"),(String)row.get("execution_id"),((Number)row.get("execution_no")).longValue(),new ama.contract.TaskContracts.SubjectRef("MEETING",meeting));var original=captureAuthorized(service,token,owner);require(publication.equals(original.identity().publication()),PROVENANCE_UNAVAILABLE);return new TerminalRead(original,token,owner);
+ }
+
+}

## backend/adapters/src/main/java/ama/transcript/PublicationTaskDomainPolicy.java
--- /dev/null
+++ b/backend/adapters/src/main/java/ama/transcript/PublicationTaskDomainPolicy.java
@@ -0,0 +1,84 @@
+package ama.transcript;
+import ama.access.MeetingAccess;
+import ama.contract.IdentityContracts.*;
+import ama.contract.MinutesContracts.*;
+import ama.contract.TaskContracts.*;
+import ama.contract.ExternalContracts.*;
+import ama.task.*;
+import ama.storage.FileRegistry;
+import javax.sql.DataSource;
+import java.util.*;
+import org.springframework.jdbc.core.JdbcTemplate;
+import static ama.contract.MinutesContracts.*;
+import static ama.contract.MinutesContracts.FailureCode.*;
+
+/** Fixed publication policy, issued by the same source/transaction authority as the minutes graph. */
+final class PublicationTaskDomainPolicy implements PublicationReplacementPolicy {
+ private final JdbcTranscriptAuthority authority;private final JdbcTaskPreparations issuer;private final JdbcTemplate j;private JdbcPublicationService service;private JdbcPublicationLocalRecovery recovery;private JdbcPublicationReplacement replacement;
+ PublicationTaskDomainPolicy(JdbcTranscriptAuthority authority,DataSource source){this.authority=authority;issuer=new JdbcTaskPreparations(source);j=new JdbcTemplate(source);j.setQueryTimeout(2);}
+ synchronized void install(JdbcPublicationService value){if(service!=null)throw new IllegalStateException("Publication bridge already installed");service=Objects.requireNonNull(value);}
+ synchronized void installRecovery(JdbcPublicationLocalRecovery value){if(recovery!=null)throw new IllegalStateException("Local recovery already installed");recovery=Objects.requireNonNull(value);}
+ private static final class LocalAdopted {final JdbcPublicationLocalRecovery.Prepared carrier;PublicationLocalDecision.Eligible localDecision;LocalTerminalContext localContext;LocalAdopted(JdbcPublicationLocalRecovery.Prepared carrier){this.carrier=carrier;}}
+ PreparedDomain adoptLocalRecovery(JdbcPublicationLocalRecovery.Prepared carrier){if(recovery==null)throw new IllegalArgumentException("Local recovery absent");recovery.requireCarrier(carrier);require(!carrier.admitted&&carrier.held==null,PROVENANCE_UNAVAILABLE);return issuer.issue(List.of(carrier.spec),Acquisition.TASK_MUTATION,new LocalAdopted(carrier),true);}
+ public RecoveryBinding requireLocalRecovery(PreparedLocalRecovery value){if(recovery==null)throw new IllegalArgumentException("Local recovery absent");var carrier=recovery.requireCarrier(value);require(!carrier.admitted&&carrier.held!=null,PROVENANCE_UNAVAILABLE);issuer.require(List.of(carrier.spec),Acquisition.TASK_MUTATION,carrier.held);require(issuer.value(carrier.held) instanceof LocalAdopted a&&a.carrier==carrier,PROVENANCE_UNAVAILABLE);carrier.admitted=true;return new RecoveryBinding(carrier.spec,carrier.task,carrier.held);}
+ synchronized void installReplacement(JdbcPublicationReplacement value){if(replacement!=null)throw new IllegalStateException("Replacement issuer already installed");replacement=Objects.requireNonNull(value);}
+ private record ReplacementAdopted(JdbcPublicationReplacement.Prepared carrier){}
+ PreparedDomain adoptReplacement(JdbcPublicationReplacement.Prepared carrier){replacement.requireCarrier(carrier);require(carrier.taskPhase&&!carrier.admitted&&carrier.domain==null,PROVENANCE_UNAVAILABLE);return issuer.issue(carrier.assembly.specs(),Acquisition.TASK_MUTATION,new ReplacementAdopted(carrier),true);}
+ public ReplacementBinding requireReplacement(PreparedPublicationReplacement value){var carrier=replacement.requireCarrier(value);require(carrier.taskPhase&&!carrier.admitted&&carrier.domain!=null,PROVENANCE_UNAVAILABLE);var assembly=carrier.assembly;issuer.require(assembly.specs(),Acquisition.TASK_MUTATION,carrier.domain);require(issuer.value(carrier.domain) instanceof ReplacementAdopted a&&a.carrier()==carrier,PROVENANCE_UNAVAILABLE);carrier.admitted=true;var plan=assembly.plan();return new ReplacementBinding(plan.spec(),assembly.task(),assembly.specs(),assembly.oldTasks(),carrier.domain,assembly.operation(),assembly.previousNo(),assembly.previousLogical(),assembly.execution(),new ExternalRequest("KNOWLEDGE_PUBLISH",plan.businessKey(),plan.logicalHash(),plan.executionHash(),plan.manifest()));}
+ public void lockReplacementRemainder(PreparedDomain domain,ReplacementContext context,PreparedPublicationReplacement value){var carrier=replacement.requireCarrier(value);require(carrier.taskPhase&&carrier.admitted&&carrier.domain==domain,PROVENANCE_UNAVAILABLE);issuer.requirePublicationReplacement(domain,context,carrier);replacement.checkRemainder(carrier);}
+ private JdbcPublicationService installed(){if(service==null)throw new DomainPreparationUnavailableException();return service;}
+ private record Adopted(Object scope,FileRegistry.LockedMeeting gate,MinutesGraphCapture.Graph graph,JdbcPublicationService.Prepared plan){}
+ private static final class Existing {
+  final Actor actor;final Object identities;final Acquisition purpose;final MinutesGraphDiscovery.Discovery discovery;final PublicationGraph.Plan publication;final TranscriptAcquisitionBudget.Ledger budget;boolean effects=true;MinutesGraphCapture.Graph graph;PublicationLocalDecision.Eligible localDecision;PublicationLocalTerminationPolicy.LocalTerminalContext localContext;
+  Existing(Actor actor,Object identities,Acquisition purpose,MinutesGraphDiscovery.Discovery discovery,PublicationGraph.Plan publication,TranscriptAcquisitionBudget.Ledger budget){this.actor=actor;this.identities=identities;this.purpose=purpose;this.discovery=discovery;this.publication=publication;this.budget=budget;}
+ }
+ PreparedDomain adopt(Object scope,FileRegistry.LockedMeeting gate,MinutesGraphCapture.Graph graph,JdbcPublicationService.Prepared plan){require(graph.equals(plan.graph()),PROVENANCE_UNAVAILABLE);authority.requireMinutesInvocation(scope,gate,plan.actor(),graph,JdbcTranscriptAuthority.MinutesWritePurpose.PUBLICATION_SUBMIT,plan);shape(plan.spec());return issuer.issue(List.of(plan.spec()),Acquisition.TASK_MUTATION,new Adopted(scope,gate,graph,plan),true);}
+ public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){var service=installed();if(specs==null||specs.stream().distinct().count()!=1)throw new DomainPreparationUnavailableException();var spec=specs.getFirst();shape(spec);
+  var bindings=j.queryForList("SELECT s.confirmation_id,s.requester_id FROM publication_submission s JOIN durable_task t ON t.id=s.task_id WHERE t.producer_key=? AND t.input_id=s.publication_id AND t.input_fingerprint=s.logical_request_hash AND s.publication_id=?",spec.producerKey(),spec.input().id());require(bindings.size()==1,PROVENANCE_UNAVAILABLE);String confirmation=(String)bindings.getFirst().get("confirmation_id");var actor=new Actor((String)bindings.getFirst().get("requester_id"),Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));var discovery=MinutesGraphDiscovery.discover(authority.dataSource(),spec.subject().id(),"FINAL",confirmation,service.limits,authority.readBudget());var pub=PublicationGraph.discover(authority.dataSource(),spec.subject().id());var access=(ama.meeting.JdbcMeetingAccess)authority.meetingAccess();Optional<?> ids=purpose==Acquisition.EXTERNAL_FACT_OBSERVATION?access.tryLockFactIdentities(actor,discovery.identities(),skip):access.tryLockHistoricalIdentities(actor,discovery.identities(),skip);if(ids.isEmpty())return Optional.empty();var budget=service.limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);JdbcMinutesFactsReader.charge(discovery,budget);var value=new Existing(actor,ids.orElseThrow(),purpose,discovery,pub,budget);if(value.identities instanceof ama.meeting.JdbcMeetingAccess.FactIdentities fact)value.effects=access.factActorActive(fact);return Optional.of(issuer.issue(specs,purpose,value,value.effects));
+ }
+ public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){issuer.require(specs,purpose,held);}
+ public boolean permitsDomainEffects(PreparedDomain held){var value=issuer.value(held);return issuer.permitsEffects(held)&&(!(value instanceof Existing p)||p.effects);}
+ public boolean lockRoot(TaskSpec spec,boolean skip){throw new DomainPreparationUnavailableException();}
+ public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain held){var value=issuer.value(held);if(value instanceof ReplacementAdopted a){var c=replacement.requireCarrier(a.carrier());issuer.require(specs,Acquisition.TASK_MUTATION,held);require(c.taskPhase&&c.admitted&&c.domain==held&&new HashSet<>(specs).equals(new HashSet<>(c.assembly.specs())),PROVENANCE_UNAVAILABLE);return true;}if(value instanceof LocalAdopted local){var c=recovery.requireCarrier(local.carrier);issuer.require(specs,Acquisition.TASK_MUTATION,held);require(c.admitted&&c.held==held&&specs.equals(List.of(c.spec)),PROVENANCE_UNAVAILABLE);return true;}if(value instanceof Adopted a){issuer.require(specs,Acquisition.TASK_MUTATION,held);require(specs.equals(List.of(a.plan().spec())),PROVENANCE_UNAVAILABLE);authority.requireMinutesInvocation(a.scope(),a.gate(),a.plan().actor(),a.graph(),JdbcTranscriptAuthority.MinutesWritePurpose.PUBLICATION_SUBMIT,a.plan());return true;}
+  var p=(Existing)value;issuer.require(specs,p.purpose,held);var spec=specs.getFirst();var access=(ama.meeting.JdbcMeetingAccess)authority.meetingAccess();MeetingAccess.Locked root;
+  if(p.identities instanceof ama.meeting.JdbcMeetingAccess.FactIdentities fact){var locked=access.lockFactRoot(fact,p.discovery.meeting(),skip);if(locked.isEmpty())return false;p.effects=p.effects&&access.factAllowsBusiness(locked.orElseThrow(),spec.cancelEpoch(),spec.businessVersion());if(!p.effects){j.queryForList("SELECT id FROM publication WHERE meeting_id=? ORDER BY id FOR UPDATE",p.discovery.meeting());j.queryForList("SELECT id FROM final_confirmation WHERE meeting_id=? ORDER BY id FOR UPDATE",p.discovery.meeting());PublicationGraph.lock(authority.dataSource(),p.publication,true);PublicationGraph.lock(authority.dataSource(),p.publication,false);return true;}root=access.authorizeFactRoot(locked.orElseThrow(),spec.cancelEpoch(),spec.businessVersion());}
+  else{var locked=access.lockPrepared((MeetingAccess.LockedIdentities)p.identities,spec.subject().id(),skip);if(locked.isEmpty())return false;root=locked.orElseThrow();}
+  access.requirePrepared(root,p.actor,p.discovery.meeting(),p.discovery.identities(),MeetingAccess.Action.EDIT);var gate=authority.fileRegistry().adopt(p.actor,p.discovery.meeting(),root,MeetingAccess.Action.EDIT);var scope=authority.minutesScope(gate,p.actor,p.discovery.meeting());p.graph=MinutesGraphCapture.capturePublication(authority,scope,gate,p.actor,p.discovery,p.budget,()->PublicationGraph.lock(authority.dataSource(),p.publication,true),()->PublicationGraph.lock(authority.dataSource(),p.publication,false));PublicationGraph.reserve(p.publication.rows(),false);return true;
+ }
+ public void validateInput(TaskSpec spec){shape(spec);var rows=j.queryForList("SELECT id FROM publication WHERE id=? AND meeting_id=?",String.class,spec.input().id(),spec.subject().id());require(rows.size()==1,PROVENANCE_UNAVAILABLE);var stored=j.queryForList("SELECT s.task_id FROM publication_submission s JOIN durable_task t ON t.id=s.task_id WHERE t.producer_key=? AND t.input_id=s.publication_id AND t.input_fingerprint=s.logical_request_hash AND s.publication_id=?",String.class,spec.producerKey(),spec.input().id());if(stored.isEmpty())require(j.queryForObject("SELECT COUNT(*) FROM publication_submission WHERE publication_id=?",Long.class,spec.input().id())==0,PROVENANCE_UNAVAILABLE);else require(stored.size()==1,PROVENANCE_UNAVAILABLE);}
+ public void validateOutput(TaskSpec spec,TaskResult result){if(result.outcome()==Outcome.SUCCEEDED){require(new TypedRef("PUBLICATION",spec.input().id()).equals(result.output()),PROVENANCE_UNAVAILABLE);require(j.queryForObject("SELECT COUNT(*) FROM publication p JOIN external_document_mapping m ON m.publication_id=p.id WHERE p.id=? AND p.meeting_id=? AND p.state='SUCCEEDED' AND m.authority_state='ACTIVE'",Long.class,spec.input().id(),spec.subject().id())==1,PROVENANCE_UNAVAILABLE);}else require(result.output()==null,PROVENANCE_UNAVAILABLE);}
+ public void validateExternalRequest(TaskSpec spec,ExternalRequest request){shape(spec);require("KNOWLEDGE_PUBLISH".equals(request.operationKind())&&request.businessKey().startsWith(spec.input().id()+":")&&request.logicalRequestHash().equals(spec.inputFingerprint()),PROVENANCE_UNAVAILABLE);}
+ public ExternalRequest requireRequest(TaskSpec spec,ExternalOperationPort.PreparedRequest value){var request=installed().requireRequest(value);require(request.prepared.spec().equals(spec),PROVENANCE_UNAVAILABLE);return request.external;}
+ private JdbcTaskPreparations.ExternalFacts bound(ExternalOperationPort.ExecutionContext context,JdbcPublicationService.Request request){var facts=issuer.requireExternal(context);require(facts.spec().equals(request.prepared.spec())&&facts.taskId().equals(request.task)&&facts.executionNo()==request.prepared.executionNo()&&facts.requestHash().equals(request.prepared.executionHash())&&facts.requestManifest().equals(request.prepared.manifest()),PROVENANCE_UNAVAILABLE);return facts;}
+ private JdbcTaskPreparations.ExternalFacts bound(ExternalOperationPort.ExecutionContext context,JdbcPublicationService.Observation observation){var facts=issuer.requireExternal(context);var p=observation.identity;require(facts.spec().equals(p.spec())&&facts.taskId().equals(p.task())&&facts.executionNo()==p.executionNo()&&facts.requestHash().equals(p.executionHash())&&facts.requestManifest().equals(p.manifest()),PROVENANCE_UNAVAILABLE);return facts;}
+ private static void sources(JdbcPublicationService.Prepared request,MinutesGraphCapture.Graph graph){require(graph!=null&&request.graph().rows().equals(graph.rows()),PROVENANCE_UNAVAILABLE);for(var entry:request.graph().auxiliary().entrySet())if(!Set.of("publication","durable_task","task_attempt","external_operation","external_execution").contains(entry.getKey())&&!entry.getKey().endsWith("_history_max"))require(entry.getValue().equals(graph.auxiliary().getOrDefault(entry.getKey(),List.of())),PROVENANCE_UNAVAILABLE);}
+ public void writeMarked(PreparedDomain held,ExternalOperationPort.ExecutionContext context,ExternalOperationPort.PreparedRequest value){var request=installed().requireRequest(value);issuer.requireExternal(held,context);var facts=bound(context,request);var p=(Existing)issuer.value(held);sources(request.prepared,p.graph);JdbcPublicationService.current(p.graph,request.prepared.spec().businessVersion(),request.prepared.confirmation());require(p.actor.equals(request.prepared.actor()),PROVENANCE_UNAVAILABLE);
+  require(j.queryForObject("SELECT COUNT(*) FROM publication_submission WHERE task_id=? AND publication_id=? AND confirmation_id=? AND requester_id=? AND logical_request_hash=? AND execution_request_hash=? AND local_state='UNDECIDED'",Long.class,request.task,request.prepared.publication(),request.prepared.confirmation(),p.actor.enterpriseId(),request.prepared.logicalHash(),request.prepared.executionHash())==1,PROVENANCE_UNAVAILABLE);
+  require(j.update("INSERT INTO publication_attempt(id,publication_id,confirmation_id,operation_id,kind,outcome,created_at,execution_id) VALUES (?,?,?,?,'PUBLISH','PENDING',UTC_TIMESTAMP(6),?)",UUID.randomUUID().toString(),request.prepared.publication(),request.prepared.confirmation(),facts.operationId(),facts.executionId())==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE publication SET state='RUNNING',revision=revision+1 WHERE id=? AND state='READY' AND revision<9223372036854775806",request.prepared.publication())==1,PROVENANCE_UNAVAILABLE);
+ }
+ public ExternalObservation verifyRemoteFact(TaskSpec actual,ExternalOperationPort.ExecutionContext context,ExternalOperationPort.PreparedObservation value){var observation=installed().requireObservation(value);var facts=bound(context,observation);require(actual.equals(facts.spec())&&facts.operationId().equals(observation.token.operationId())&&facts.executionId().equals(observation.token.executionId()),PROVENANCE_UNAVAILABLE);return observation.fact;}
+ public TaskResult writeCurrent(PreparedDomain held,ExternalOperationPort.ExecutionContext context,ExternalOperationPort.PreparedObservation value){var o=installed().requireObservation(value);issuer.requireExternal(held,context);bound(context,o);var p=(Existing)issuer.value(held);require(o.request!=null,PROVENANCE_UNAVAILABLE);sources(o.request.prepared,p.graph);var request=o.request.prepared;JdbcPublicationService.current(p.graph,request.spec().businessVersion(),request.confirmation());
+  if(o.fact.outcome()==ExternalOutcome.FAILED_ZERO_EFFECT){
+   require(o.fact.noEffectProof()!=null&&o.fact.noEffectProof().basis()==NoEffectBasis.DEFINITIVE_REJECTION,PROVENANCE_UNAVAILABLE);
+   require(j.update("UPDATE publication_attempt SET outcome='FAILED',finished_at=UTC_TIMESTAMP(6) WHERE publication_id=? AND confirmation_id=? AND operation_id=? AND execution_id=? AND outcome='PENDING'",request.publication(),request.confirmation(),o.token.operationId(),o.token.executionId())==1,PROVENANCE_UNAVAILABLE);
+   require(j.update("UPDATE publication SET state='FAILED',revision=revision+1 WHERE id=? AND confirmation_id=? AND state IN ('RUNNING','RECONCILING') AND revision<9223372036854775806",request.publication(),request.confirmation())==1,PROVENANCE_UNAVAILABLE);
+   require(j.update("UPDATE meeting SET stage='PUBLISH_FAILED',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=? AND final_confirmation_id=? AND ever_published=0",request.spec().subject().id(),request.spec().businessVersion(),request.spec().cancelEpoch(),request.confirmation())==1,PROVENANCE_UNAVAILABLE);return new TaskResult(Outcome.FAILED,null,o.fact.errorCode());
+  }
+  require(o.fact.outcome()==ExternalOutcome.SUCCEEDED,PROVENANCE_UNAVAILABLE);String document=(String)o.receipt.get("documentId"),url="http://127.0.0.1:"+installed().client.port()+"/v1/knowledge/documents/"+document;
+  require(j.update("INSERT INTO external_document_mapping(publication_id,provider_id,external_document_id,document_url,authority_state,verified_at) VALUES (?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",request.publication(),request.provider(),document,url)==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE publication_attempt SET outcome='SUCCESS',finished_at=UTC_TIMESTAMP(6) WHERE publication_id=? AND confirmation_id=? AND operation_id=? AND execution_id=? AND outcome='PENDING'",request.publication(),request.confirmation(),o.token.operationId(),o.token.executionId())==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE publication SET state='SUCCEEDED',revision=revision+1,published_at=UTC_TIMESTAMP(6) WHERE id=? AND confirmation_id=? AND state IN ('RUNNING','RECONCILING') AND revision<9223372036854775806",request.publication(),request.confirmation())==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE meeting SET stage='PUBLISHED',ever_published=1,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=? AND final_confirmation_id=? AND ever_published=0",request.spec().subject().id(),request.spec().businessVersion(),request.spec().cancelEpoch(),request.confirmation())==1,PROVENANCE_UNAVAILABLE);return new TaskResult(Outcome.SUCCEEDED,new TypedRef("PUBLICATION",request.publication()),null);
+ }
+ public void validateStoredResult(PreparedDomain held,ExternalOperationPort.ExecutionContext context,ExternalOperationPort.PreparedObservation value,TaskResult result){var o=installed().requireObservation(value);issuer.requireExternal(held,context);bound(context,o);validateOutput(o.identity.spec(),result);require(o.fact.outcome()==ExternalOutcome.SUCCEEDED?mapping(o):failed(o),PROVENANCE_UNAVAILABLE);}
+ private boolean failed(JdbcPublicationService.Observation o){return o.fact.outcome()==ExternalOutcome.FAILED_ZERO_EFFECT&&j.queryForObject("SELECT COUNT(*) FROM publication_attempt a WHERE a.publication_id=? AND a.confirmation_id=? AND a.operation_id=? AND a.execution_id=? AND a.outcome='FAILED' AND NOT EXISTS (SELECT 1 FROM external_document_mapping m WHERE m.publication_id=a.publication_id)",Long.class,o.identity.publication(),o.identity.confirmation(),o.token.operationId(),o.token.executionId())==1;}
+ private boolean mapping(JdbcPublicationService.Observation o){return j.queryForObject("SELECT COUNT(*) FROM publication_attempt a JOIN external_document_mapping m ON m.publication_id=a.publication_id WHERE a.publication_id=? AND a.confirmation_id=? AND a.operation_id=? AND a.execution_id=? AND a.outcome='SUCCESS' AND m.provider_id=? AND m.external_document_id=?",Long.class,o.identity.publication(),o.identity.confirmation(),o.token.operationId(),o.token.executionId(),o.identity.provider(),o.receipt.get("documentId"))==1;}
+ public boolean verifyReplay(PreparedDomain held,ExternalOperationPort.ExecutionContext context,ExternalOperationPort.PreparedObservation value,StoredResult stored){var o=installed().requireObservation(value);issuer.requireExternal(held,context);bound(context,o);return o.fact.outcome()==ExternalOutcome.SUCCEEDED?stored.state().equals("SUCCEEDED")&&stored.errorCode()==null&&new TypedRef("PUBLICATION",o.identity.publication()).equals(stored.output())&&mapping(o):stored.state().equals("FAILED")&&Objects.equals(stored.errorCode(),o.fact.errorCode())&&stored.output()==null&&failed(o);}
+ private record LocalData(Actor actor,Map<String,Object> root,Map<String,Object> publication,PublicationGraph.Plan graph){}
+ private LocalData localData(Object value){if(value instanceof Existing p){if(!p.effects||p.graph==null)return null;var pubs=p.graph.auxiliary().getOrDefault("publication",List.of());if(pubs.size()!=1)return null;return new LocalData(p.actor,p.graph.discovery().root(),pubs.getFirst(),p.publication);}if(value instanceof LocalAdopted a){var c=recovery.requireCarrier(a.carrier);if(c.plan.publications().size()!=1)return null;return new LocalData(c.actor,c.plan.root(),c.plan.publications().getFirst(),c.plan.graph());}return null;}
+ public boolean validateLocalTerminal(PreparedDomain held,LocalTerminalContext context){var facts=issuer.requireLocalTerminal(held,context,Phase.VALIDATING);var value=issuer.value(held);var p=localData(value);if(p==null)return false;require(value instanceof Existing x?x.localContext==null:((LocalAdopted)value).localContext==null,PROVENANCE_UNAVAILABLE);var root=p.root();if(!"ACTIVE".equals(root.get("lifecycle"))||!"ACTIVE".equals(root.get("governance_state"))||((Number)root.get("ever_published")).intValue()!=0||!facts.spec().input().id().equals(p.publication().get("id"))||!Set.of("READY","RECONCILING","FAILED").contains(p.publication().get("state")))return false;var decision=PublicationLocalDecision.inspect(p.graph(),facts.spec(),facts.taskId());if(decision==null)return false;if(value instanceof Existing e){e.localDecision=decision;e.localContext=context;}else{var a=(LocalAdopted)value;a.localDecision=decision;a.localContext=context;}return true;
+ }
+ public void sealLocalTerminal(PreparedDomain held,LocalTerminalContext context){var facts=issuer.requireLocalTerminal(held,context,Phase.SEALING);var value=issuer.value(held);var p=localData(value);var d=value instanceof Existing e?e.localDecision:((LocalAdopted)value).localDecision;require(p!=null&&d!=null&&(value instanceof Existing e?e.localContext:((LocalAdopted)value).localContext)==context&&d.task().equals(facts.taskId()),PROVENANCE_UNAVAILABLE);require(j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE id=? AND state=? AND attempt=? AND output_kind IS NULL AND output_id IS NULL AND lease_owner IS NULL AND expires_at IS NULL",Long.class,facts.taskId(),facts.resultingState(),facts.attempt())==1,PROVENANCE_UNAVAILABLE);require(j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NULL",Long.class,facts.taskId())==0,PROVENANCE_UNAVAILABLE);
+  String reason="EXPIRED_UNMARKED_ABANDONED".equals(facts.reason())?facts.reason():"ATTEMPT_LIMIT".equals(facts.reason())||facts.attempt()>=facts.spec().maxAttempts()?"PREPARATION_EXHAUSTED":"TERMINAL_UNMARKED_FAILURE";
+  require(j.update("UPDATE publication_submission SET local_state='NEVER_MARKED',local_decided_at=UTC_TIMESTAMP(6),local_reason=?,local_decided_by=?,local_attempt_no=?,local_execution_id=? WHERE task_id=? AND publication_id=? AND submission_no=? AND local_state='UNDECIDED'",reason,p.actor().enterpriseId(),facts.attempt(),d.execution(),facts.taskId(),d.publication(),d.submissionNo())==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE publication SET state='FAILED',revision=revision+1 WHERE id=? AND revision=? AND state IN ('READY','RECONCILING','FAILED') AND revision<9223372036854775806",d.publication(),p.publication().get("revision"))==1,PROVENANCE_UNAVAILABLE);require(j.update("UPDATE meeting SET stage='PUBLISH_FAILED',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=? AND ever_published=0",facts.spec().subject().id(),p.root().get("revision"),p.root().get("cancel_epoch"))==1,PROVENANCE_UNAVAILABLE);
+  new ama.audit.JdbcAuditAdapter(authority.dataSource()).append(new ama.contract.AuditContracts.AuditEvent(p.actor().enterpriseId(),"PUBLICATION_LOCAL_NEVER_MARKED",facts.spec().subject().id(),((Number)p.root().get("revision")).longValue(),"SUCCEEDED",UUID.randomUUID().toString(),java.time.Instant.now()));if(value instanceof Existing e)e.localContext=null;else((LocalAdopted)value).localContext=null;
+ }
+ static void shape(TaskSpec spec){require("KNOWLEDGE_PUBLISH_V1".equals(spec.type())&&"KNOWLEDGE_PUBLISH".equals(spec.pool())&&"MEETING".equals(spec.subject().kind())&&"PUBLICATION".equals(spec.input().kind())&&spec.priority()==0&&spec.maxAttempts()==3,PROVENANCE_UNAVAILABLE);}
+}

## backend/adapters/src/test/java/ama/transcript/Task11KnowledgeCanonicalTest.java
--- /dev/null
+++ b/backend/adapters/src/test/java/ama/transcript/Task11KnowledgeCanonicalTest.java
@@ -0,0 +1,15 @@
+package ama.transcript;
+import com.fasterxml.jackson.databind.ObjectMapper;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import ama.contract.MinutesContracts.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+class Task11KnowledgeCanonicalTest {
+ final ObjectMapper json=new ObjectMapper();
+ @Test void exactRendererJsonBytesSurviveTransportAndFieldOrdering()throws Exception {var outputs=new ArrayList<Object>();try(var in=getClass().getResourceAsStream("/ama/knowledge/canonical-v1.json")){var cases=json.readTree(in);for(var test:cases){var value=json.convertValue(test.get("value"),Object.class);byte[] actual=ControlledKnowledgeClient.canonical(value);assertArrayEquals(test.get("canonical").asText().getBytes(StandardCharsets.UTF_8),actual);outputs.add(Map.of("value",value,"javaBase64",Base64.getEncoder().encodeToString(actual),"javaSha256",ControlledKnowledgeClient.digest(actual)));}}Files.writeString(Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"),"canonical-java-actual.json"),json.writeValueAsString(outputs));}
+ static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
+ static FullRow row(Table table,Object... values){var cells=new ArrayList<Cell>();for(Object v:values)cells.add(v==null?new NullCell():v instanceof Long n?new UIntCell(n):new TextCell((String)v));return new FullRow(table,cells);}
+ @Test void actualDocumentRendererBytesEqualActualClientAndExportForPython()throws Exception {
+  String time="2026-10-25T00:00:00.000000Z",h="a".repeat(64),body="原文😀\n\" /\n\t\b\f\r";
+  var rows=List.of(row(Table.TEMPLATE_REVISION,id(9),id(8),1L,"owner","template","{\"schemaVersion\":\"ama.controlled-minutes-template.v1\",\"themes\":[\"OVERVIEW\",\"DISCUSSION\",\"CONCLUSION\",\"ACTION\",\"RISK\",\"OPEN_QUESTION\",\"DISAGREEMENT\"]}",7L,time),row(Table.MINUTES_REVISION,id(13),id(1),1L,null,"标题😀","HUMAN",body,null,"owner",time),row(Table.FINAL_CONFIRMATION,id(20),id(1),1L,id(21),id(22),id(7),id(13),null,id(11),id(9),id(10),h,h,"owner",time),row(Table.MINUTES_ELEMENT,id(30),id(11),"ACTION",0L,body,"SUPPORTED"),row(Table.ACTION_ITEM,id(30),null,null,null));
+  var facts=new StoredProvenanceFacts(rows,List.of(),List.of(),List.of(),100000L);byte[] archive="{}".getBytes(StandardCharsets.UTF_8);var artifact=new Artifact("render-golden",ControlledKnowledgeClient.digest(archive),ama.contract.ConfigArchiveContracts.ArchiveBytes.capture(archive,65536,65536,()->false));
+  var renderer=new ama.knowledge.ControlledKnowledgeDocumentRenderer();byte[] rendered=renderer.render(new FinalFacts(id(20),facts),new ama.knowledge.ControlledKnowledgeDocumentRenderer.Meeting("会议😀", "category",null,List.of(new ama.knowledge.ControlledKnowledgeDocumentRenderer.Member("INTERNAL","参与者\n😀"))),ama.knowledge.ControlledKnowledgeDocumentRenderer.VERSION,id(9),artifact,8388608,()->false);var value=json.readValue(rendered,Object.class);byte[] client=ControlledKnowledgeClient.canonical(value);assertArrayEquals(rendered,client);assertEquals(ControlledKnowledgeClient.digest(rendered),ControlledKnowledgeClient.digest(client));
+  Files.writeString(Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"),"renderer-client-java-actual.json"),json.writeValueAsString(Map.of("value",value,"rendererBase64",Base64.getEncoder().encodeToString(rendered),"clientBase64",Base64.getEncoder().encodeToString(client),"payloadSha256",ControlledKnowledgeClient.digest(rendered))));
+ }
+}

## backend/adapters/src/test/java/ama/transcript/Task11KnowledgeProofTest.java
--- /dev/null
+++ b/backend/adapters/src/test/java/ama/transcript/Task11KnowledgeProofTest.java
@@ -0,0 +1,10 @@
+package ama.transcript;
+import java.util.*;import java.security.*;import java.security.spec.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
+class Task11KnowledgeProofTest {
+ Map<String,Object> vectors;Map<String,String> identities;PublicKey key;
+ @BeforeEach void publicVectors()throws Exception {try(var in=getClass().getResourceAsStream("/ama/knowledge/permanent-rejection-public-vectors.json")){vectors=ControlledKnowledgeClient.parse(in.readAllBytes(),65536);}identities=(Map<String,String>)vectors.get("identities");key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode((String)vectors.get("publicKeySpki"))));}
+ @Test void permanentHistoricalSignatureRemainsVerifiable(){var proof=ControlledKnowledgeClient.verifyPermanentRejection(key,identities,(String)vectors.get("validPermanent"));assertEquals(1,((Number)proof.get("verifiedAt")).longValue());assertEquals("DEFINITIVE_REJECTION",proof.get("noEffectBasis"));}
+ @Test void forgedSignatureRejected(){String token=(String)vectors.get("validPermanent");int split=token.indexOf('.');char old=token.charAt(split+1);String changed=token.substring(0,split+1)+(old=='A'?'B':'A')+token.substring(split+2);assertThrows(IllegalArgumentException.class,()->ControlledKnowledgeClient.verifyPermanentRejection(key,identities,changed));}
+ @Test void crossStoreRejected(){var other=new HashMap<>(identities);other.put("storeInstanceId","00000000-0000-4000-8000-000000000099");assertThrows(IllegalArgumentException.class,()->ControlledKnowledgeClient.verifyPermanentRejection(key,other,(String)vectors.get("validPermanent")));}
+ @Test void signedInvalidClaimsAndNoncanonicalBytesRejected(){for(String name:List.of("wrongSchema","wrongBasis","extraField","future","noncanonical"))assertThrows(IllegalArgumentException.class,()->ControlledKnowledgeClient.verifyPermanentRejection(key,identities,(String)vectors.get(name)),name);}
+}

## backend/api/src/main/java/ama/api/FinalConfirmationController.java
--- /dev/null
+++ b/backend/api/src/main/java/ama/api/FinalConfirmationController.java
@@ -0,0 +1,34 @@
+package ama.api;
+import ama.contract.IdentityContracts.*;
+import ama.transcript.*;
+import ama.access.MeetingAccess;
+import ama.db.TransactionRunner;
+import com.fasterxml.jackson.core.*;
+import com.fasterxml.jackson.databind.*;
+import jakarta.servlet.http.HttpServletRequest;
+import java.io.*;
+import java.util.*;
+import org.springframework.http.*;
+import org.springframework.web.bind.annotation.*;
+
+@RestController @RequestMapping("/api/v1/meetings/{meetingId}/final-confirmations")
+final class FinalConfirmationController {
+ private final MinutesConfiguration.Runtime runtime;private final JdbcMinutesCommandReceipts receipts;private final TransactionRunner tx;private final MeetingAccess access;
+ private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(1024).maxNumberLength(19).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
+ FinalConfirmationController(MinutesConfiguration.Runtime runtime,JdbcMinutesCommandReceipts receipts,TransactionRunner tx,MeetingAccess access){this.runtime=runtime;this.receipts=receipts;this.tx=tx;this.access=access;}
+ @GetMapping("/readiness") ResponseEntity<byte[]> readiness(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return json(require(request,meetingId).readiness(actor(request),meetingId),200);}
+ @GetMapping("/current") ResponseEntity<byte[]> current(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return json(require(request,meetingId).current(actor(request),meetingId),200);}
+ @GetMapping("/{confirmationId}") ResponseEntity<byte[]> detail(@PathVariable String meetingId,@PathVariable String confirmationId,HttpServletRequest request)throws IOException{return json(require(request,meetingId).detail(actor(request),meetingId,confirmationId),200);}
+ @PostMapping ResponseEntity<byte[]> confirm(@PathVariable String meetingId,HttpServletRequest request)throws IOException{
+  byte[] bytes;try(var in=request.getInputStream()){bytes=in.readNBytes(65537);}if(bytes.length>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");JsonNode node;
+  try{node=JSON.readTree(bytes);}catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw new IllegalArgumentException("Malformed final command");}
+  if(node==null||!node.isObject())throw new IllegalArgumentException("Object required");node.fieldNames().forEachRemaining(name->{if(!Set.of("expectedRevision","expectedRevisionToken","optionalReviewIds").contains(name))throw new IllegalArgumentException("Unknown final field");});long expected=HttpRevisionTokens.expected(node);
+  var selected=node.get("optionalReviewIds");if(selected==null||!selected.isArray()||selected.size()>256)throw new IllegalArgumentException("Bounded optional review array required");var optional=new ArrayList<String>();for(var id:selected){if(!id.isTextual())throw new IllegalArgumentException("Review id required");ama.contract.MinutesContracts.uuid(id.textValue());optional.add(id.textValue());}optional.sort(String::compareTo);if(new HashSet<>(optional).size()!=optional.size())throw new IllegalArgumentException("Duplicate review id");
+  var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One idempotency key required");String key=keys.getFirst();JdbcProcessingIntake.key(key);
+  var replay=receipts.lookup(actor(request),meetingId,"FINAL_CONFIRM",key,expected,optional.toArray(String[]::new));if(replay.isPresent())return json(replay.orElseThrow(),200);
+  var result=require(request,meetingId).confirm(actor(request),meetingId,key,expected,optional,UUID.randomUUID().toString());return json(result,Boolean.TRUE.equals(result.get("replayed"))?200:201);
+ }
+ private JdbcFinalConfirmations require(HttpServletRequest request,String meeting){ama.contract.MinutesContracts.uuid(meeting);if(runtime.finals()!=null)return runtime.finals();tx.required(()->{access.require(access.lock(actor(request),meeting),MeetingAccess.Action.EDIT);return null;});throw new ama.contract.TranscriptContracts.Failure(503,"PUBLICATION_NOT_INSTALLED");}
+ private static Actor actor(HttpServletRequest request){return ((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();}
+ private ResponseEntity<byte[]> json(Object value,int status)throws IOException{return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(value,runtime.responseBytes()));}
+}

## backend/api/src/main/java/ama/api/KnowledgeConfiguration.java
--- /dev/null
+++ b/backend/api/src/main/java/ama/api/KnowledgeConfiguration.java
@@ -0,0 +1,10 @@
+package ama.api;
+import ama.transcript.ControlledKnowledgeClient;
+import org.springframework.context.annotation.*;
+import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
+import org.springframework.core.env.Environment;
+@Configuration(proxyBeanMethods=false)
+class KnowledgeConfiguration {
+ @Bean(destroyMethod="close") @ConditionalOnProperty(name="publication.client-identities")
+ ControlledKnowledgeClient controlledKnowledgeClient(Environment env)throws Exception{return new ControlledKnowledgeClient(java.nio.file.Path.of(env.getRequiredProperty("publication.client-identities")),java.net.URI.create(env.getRequiredProperty("publication.origin")),env.getRequiredProperty("publication.mode"));}
+}

## backend/api/src/main/java/ama/api/MinutesConfiguration.java
--- a/backend/api/src/main/java/ama/api/MinutesConfiguration.java
+++ b/backend/api/src/main/java/ama/api/MinutesConfiguration.java
@@ -1,15 +1,16 @@
 package ama.api;
 import ama.access.MeetingAccess;import ama.config.*;import ama.config.file.*;import ama.contract.ConfigArchiveContracts.*;import ama.db.TransactionRunner;import ama.task.*;import ama.transcript.*;import java.util.*;import java.nio.file.Path;import javax.sql.DataSource;import org.springframework.context.annotation.*;import org.springframework.core.env.Environment;
 @Configuration(proxyBeanMethods=false) class MinutesConfiguration {
  /** Controlled Task10 source is present; formal new effects await the Task11 publication installation. */
- record Runtime(MinutesEditorRuntime editor,JdbcMinutesSavedQueries saved,JdbcMinutesQueries queries,JdbcMinutesInitialSettings initial,JdbcMinutesGenerationCommands generation,long responseBytes){void requireNewInitial(){throw new ama.contract.TranscriptContracts.Failure(503,"TASK11_NOT_INSTALLED");}void requireNewGeneration(){throw new ama.contract.TranscriptContracts.Failure(503,"MINUTES_COMBINED_NOT_INSTALLED");}}
+ record Runtime(MinutesEditorRuntime editor,JdbcMinutesSavedQueries saved,JdbcMinutesQueries queries,JdbcMinutesInitialSettings initial,JdbcMinutesGenerationCommands generation,JdbcFinalConfirmations finals,JdbcPublicationService publications,JdbcPublicationLocalRecovery localPublications,long responseBytes){void requireNewInitial(){throw new ama.contract.TranscriptContracts.Failure(503,"TASK11_NOT_INSTALLED");}void requireNewGeneration(){throw new ama.contract.TranscriptContracts.Failure(503,"MINUTES_COMBINED_NOT_INSTALLED");}}
  @Bean JdbcMinutesCommandReceipts minutesCommandReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){return new JdbcMinutesCommandReceipts(source,tx,access);}
  @Bean JdbcMinutesIntentReceipts minutesIntentReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){return new JdbcMinutesIntentReceipts(source,tx,access);}
  @Bean JdbcMinutesSettingsReceipts minutesSettingsReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){return new JdbcMinutesSettingsReceipts(source,tx,access);}
  @Bean JdbcMinutesGenerationReceipts minutesGenerationReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){return new JdbcMinutesGenerationReceipts(source,tx,access);}
- @Bean Runtime minutesRuntime(ProcessingConfiguration.Runtime processing,TranscriptConfiguration.Runtime transcript,Environment env,JdbcTaskQueue queue,JdbcOutbox outbox,DataSource source){
-  if(!env.getProperty("minutes.enabled",Boolean.class,false))return new Runtime(null,null,null,null,null,33554432);
-  try{if(processing.components()==null||!"CONTROLLED_INSTALLATION".equals(env.getRequiredProperty("minutes.mode")))throw new IllegalArgumentException("Explicit minutes installation required");var authority=processing.components().authority();var bounds=ConfiguredMinutes.acquisition(env);var read=ConfiguredMinutes.archiveBudget(env);String pin=env.getRequiredProperty("minutes.index-sha");Path root=Path.of(env.getRequiredProperty("minutes.archive-root"));java.util.function.Supplier<ConfigArchiveSource.Snapshot> archives=()->new ControlledConfigArchiveSource().open(root,pin,ConfiguredMinutes.indexBudget(env),read,()->false);var prompt=new Ref(ArtifactType.PROMPT_CONFIG,env.getRequiredProperty("minutes.prompt-version"),env.getRequiredProperty("minutes.prompt-sha"));var model=new Ref(ArtifactType.MODEL_CONFIG,env.getRequiredProperty("minutes.model-version"),env.getRequiredProperty("minutes.model-sha"));var prep=new JdbcMinutesGenerationPreparation(authority,archives,pin,prompt,model,bounds);var editor=MinutesEditorRuntime.compose(authority,archives,pin,prompt,model,bounds,transcript.service());return new Runtime(editor,editor.queries(),new JdbcMinutesQueries(authority,archives,pin,prompt,model,bounds),new JdbcMinutesInitialSettings(authority,archives,prompt,model),new JdbcMinutesGenerationCommands(authority,prep,queue,outbox,new ama.audit.JdbcAuditAdapter(source)),bounds.responseBytes());}
-  catch(IllegalArgumentException|IllegalStateException|ama.contract.MinutesContracts.Failure unavailable){return new Runtime(null,null,null,null,null,33554432);}
+ @Bean Runtime minutesRuntime(ProcessingConfiguration.Runtime processing,TranscriptConfiguration.Runtime transcript,Environment env,JdbcTaskQueue queue,JdbcOutbox outbox,DataSource source,org.springframework.beans.factory.ObjectProvider<ControlledKnowledgeClient> knowledge){
+  if(!env.getProperty("minutes.enabled",Boolean.class,false))return new Runtime(null,null,null,null,null,null,null,null,33554432);
+  JdbcPublicationLocalRecovery local=null;
+  try{if(processing.components()==null||!"CONTROLLED_INSTALLATION".equals(env.getRequiredProperty("minutes.mode")))throw new IllegalArgumentException("Explicit minutes installation required");var authority=processing.components().authority();var bounds=ConfiguredMinutes.acquisition(env);local=new JdbcPublicationLocalRecovery(authority,bounds,queue,outbox);var read=ConfiguredMinutes.archiveBudget(env);String pin=env.getRequiredProperty("minutes.index-sha");Path root=Path.of(env.getRequiredProperty("minutes.archive-root"));java.util.function.Supplier<ConfigArchiveSource.Snapshot> archives=()->new ControlledConfigArchiveSource().open(root,pin,ConfiguredMinutes.indexBudget(env),read,()->false);var prompt=new Ref(ArtifactType.PROMPT_CONFIG,env.getRequiredProperty("minutes.prompt-version"),env.getRequiredProperty("minutes.prompt-sha"));var model=new Ref(ArtifactType.MODEL_CONFIG,env.getRequiredProperty("minutes.model-version"),env.getRequiredProperty("minutes.model-sha"));var prep=new JdbcMinutesGenerationPreparation(authority,archives,pin,prompt,model,bounds);var editor=MinutesEditorRuntime.compose(authority,archives,pin,prompt,model,bounds,transcript.service());return new Runtime(editor,editor.queries(),new JdbcMinutesQueries(authority,archives,pin,prompt,model,bounds),new JdbcMinutesInitialSettings(authority,archives,prompt,model),new JdbcMinutesGenerationCommands(authority,prep,queue,outbox,new ama.audit.JdbcAuditAdapter(source)),env.getProperty("publication.enabled",Boolean.class,false)?new JdbcFinalConfirmations(authority,archives,pin,bounds,transcript.service()):null,knowledge.getIfAvailable()==null?null:new JdbcPublicationService(authority,archives,pin,bounds,transcript.service(),queue,outbox,knowledge.getIfAvailable()),local,bounds.responseBytes());}
+  catch(IllegalArgumentException|IllegalStateException|ama.contract.MinutesContracts.Failure unavailable){return new Runtime(null,null,null,null,null,null,null,local,33554432);}
  }
 }

## backend/api/src/main/java/ama/api/PublicationController.java
--- /dev/null
+++ b/backend/api/src/main/java/ama/api/PublicationController.java
@@ -0,0 +1,20 @@
+package ama.api;
+import ama.contract.IdentityContracts.*;import ama.transcript.*;import ama.access.MeetingAccess;import ama.db.TransactionRunner;import jakarta.servlet.http.*;import java.io.*;import java.util.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
+@RestController @RequestMapping("/api/v1/meetings/{meetingId}/publications")
+final class PublicationController {
+ private final MinutesConfiguration.Runtime runtime;private final TransactionRunner tx;private final MeetingAccess access;
+ PublicationController(MinutesConfiguration.Runtime runtime,TransactionRunner tx,MeetingAccess access){this.runtime=runtime;this.tx=tx;this.access=access;}
+ @PostMapping ResponseEntity<byte[]> submit(@PathVariable String meetingId,HttpServletRequest request)throws IOException {
+  byte[] raw;try(var in=request.getInputStream()){raw=in.readNBytes(65537);}if(raw.length>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");var mapper=new com.fasterxml.jackson.databind.ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(1024).maxNumberLength(19).build()).build()).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var body=mapper.readTree(raw);if(body==null||!body.isObject())throw new IllegalArgumentException("Object required");body.fieldNames().forEachRemaining(name->{if(!Set.of("expectedRevision","expectedRevisionToken","confirmationId","providerId","libraryId","folderId").contains(name))throw new IllegalArgumentException("Unknown publication field");});long expected=HttpRevisionTokens.expected(body);var values=new HashMap<String,String>();for(String key:List.of("confirmationId","providerId","libraryId","folderId")){var value=body.get(key);if(value==null||!value.isTextual())throw new IllegalArgumentException("Publication field required");values.put(key,value.textValue());}var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One key required");JdbcProcessingIntake.key(keys.getFirst());
+  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();if(runtime.publications()==null){tx.required(()->{access.require(access.lock(actor,meetingId),MeetingAccess.Action.EDIT);return null;});throw new ama.contract.TranscriptContracts.Failure(503,"PUBLICATION_NOT_INSTALLED");}
+  var result=runtime.publications().submit(actor,meetingId,keys.getFirst(),expected,values.get("confirmationId"),values.get("providerId"),values.get("libraryId"),values.get("folderId"),UUID.randomUUID().toString());return ResponseEntity.status(202).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(result,runtime.responseBytes()));
+ }
+ @PostMapping("/{publicationId}/local-recoveries") ResponseEntity<byte[]> recover(@PathVariable String meetingId,@PathVariable String publicationId,HttpServletRequest request)throws IOException {
+  byte[] raw;try(var in=request.getInputStream()){raw=in.readNBytes(65537);}if(raw.length>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");var mapper=new com.fasterxml.jackson.databind.ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(1024).maxNumberLength(19).build()).build()).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var body=mapper.readTree(raw);if(body==null||!body.isObject())throw new IllegalArgumentException("Object required");body.fieldNames().forEachRemaining(name->{if(!Set.of("expectedRevision","expectedRevisionToken","expectedPublicationRevision","expectedPublicationRevisionToken","taskId").contains(name))throw new IllegalArgumentException("Unknown local recovery field");});long expected=HttpRevisionTokens.expected(body);var publicationRevision=mapper.createObjectNode();if(body.has("expectedPublicationRevision"))publicationRevision.set("expectedRevision",body.get("expectedPublicationRevision"));if(body.has("expectedPublicationRevisionToken"))publicationRevision.set("expectedRevisionToken",body.get("expectedPublicationRevisionToken"));long expectedPublication=HttpRevisionTokens.expected(publicationRevision);if(!body.hasNonNull("taskId")||!body.get("taskId").isTextual())throw new IllegalArgumentException("Actual task required");var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One key required");JdbcProcessingIntake.key(keys.getFirst());var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();if(runtime.localPublications()==null){tx.required(()->{access.require(access.lock(actor,meetingId),MeetingAccess.Action.EDIT);return null;});throw new ama.contract.TranscriptContracts.Failure(503,"PUBLICATION_LOCAL_RECOVERY_NOT_INSTALLED");}var result=runtime.localPublications().recover(actor,meetingId,publicationId,body.get("taskId").textValue(),keys.getFirst(),expected,expectedPublication,UUID.randomUUID().toString());return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(result,runtime.responseBytes()));
+ }
+
+ @PostMapping("/{publicationId}/resubmissions") ResponseEntity<byte[]> resubmit(@PathVariable String meetingId,@PathVariable String publicationId,HttpServletRequest request)throws IOException {
+  byte[] raw;try(var in=request.getInputStream()){raw=in.readNBytes(65537);}if(raw.length>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");var mapper=new com.fasterxml.jackson.databind.ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(1024).maxNumberLength(19).build()).build()).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var body=mapper.readTree(raw);if(body==null||!body.isObject())throw new IllegalArgumentException("Object required");body.fieldNames().forEachRemaining(name->{if(!Set.of("expectedRevision","expectedRevisionToken","expectedPublicationRevision","expectedPublicationRevisionToken","confirmationId","providerId","libraryId","folderId").contains(name))throw new IllegalArgumentException("Unknown replacement field");});long expected=HttpRevisionTokens.expected(body);var publicationRevision=mapper.createObjectNode();if(body.has("expectedPublicationRevision"))publicationRevision.set("expectedRevision",body.get("expectedPublicationRevision"));if(body.has("expectedPublicationRevisionToken"))publicationRevision.set("expectedRevisionToken",body.get("expectedPublicationRevisionToken"));long expectedPublication=HttpRevisionTokens.expected(publicationRevision);var values=new HashMap<String,String>();for(String key:List.of("confirmationId","providerId","libraryId","folderId")){var value=body.get(key);if(value==null||!value.isTextual())throw new IllegalArgumentException("Replacement field required");values.put(key,value.textValue());}var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One key required");JdbcProcessingIntake.key(keys.getFirst());var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();if(runtime.publications()==null){tx.required(()->{access.require(access.lock(actor,meetingId),MeetingAccess.Action.EDIT);return null;});throw new ama.contract.TranscriptContracts.Failure(503,"PUBLICATION_NOT_INSTALLED");}var result=runtime.publications().resubmit(actor,meetingId,publicationId,keys.getFirst(),expected,expectedPublication,values.get("confirmationId"),values.get("providerId"),values.get("libraryId"),values.get("folderId"),UUID.randomUUID().toString());return ResponseEntity.status(202).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(result,runtime.responseBytes()));
+ }
+
+}

## backend/api/src/main/java/ama/api/PublicationErrors.java
--- /dev/null
+++ b/backend/api/src/main/java/ama/api/PublicationErrors.java
@@ -0,0 +1,12 @@
+package ama.api;
+import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;import org.springframework.web.bind.annotation.*;
+/** Publication failures remain on the business route, never a secondary /error dispatch. */
+@RestControllerAdvice(assignableTypes=PublicationController.class)
+@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
+class PublicationErrors {
+ private void write(HttpServletResponse response,int status,String code)throws IOException{BusinessErrors.write(response,status,code,"Publication request could not be completed");response.setHeader("Cache-Control","private, no-store");}
+ @ExceptionHandler(ama.contract.TranscriptContracts.Failure.class) void explicit(ama.contract.TranscriptContracts.Failure failure,HttpServletResponse response)throws IOException{write(response,failure.status(),failure.code());}
+ @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void domain(ama.contract.MinutesContracts.Failure failure,HttpServletResponse response)throws IOException{switch(failure.code()){case INVALID_ARGUMENT,INVALID_SCALAR,INVALID_ID->write(response,400,"INVALID_ARGUMENT");case BUDGET_EXCEEDED->write(response,413,"REQUEST_TOO_LARGE");default->write(response,503,"PUBLICATION_UNAVAILABLE");}}
+ @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse response)throws IOException{write(response,403,"MEETING_FORBIDDEN");}
+ @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse response)throws IOException{write(response,400,"INVALID_ARGUMENT");}
+}

## backend/api/src/test/java/ama/api/Task11PublicationErrorsTest.java
--- /dev/null
+++ b/backend/api/src/test/java/ama/api/Task11PublicationErrorsTest.java
@@ -0,0 +1,11 @@
+package ama.api;
+import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import ama.contract.IdentityContracts.*;import java.util.*;import java.time.Instant;
+class Task11PublicationErrorsTest {
+ @Test void actualControllerUsesSpecificFailureAdviceWithoutErrorDispatch()throws Exception {
+  var local=mock(ama.transcript.JdbcPublicationLocalRecovery.class);var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,local,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationController(runtime,null,null)).setControllerAdvice(new PublicationErrors()).build();var actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));var session=new ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60));
+  for(var fixture:List.of(new Object[]{new ama.contract.TranscriptContracts.Failure(409,"PUBLICATION_LOCAL_RECOVERY_UNAVAILABLE"),409,"PUBLICATION_LOCAL_RECOVERY_UNAVAILABLE"},new Object[]{new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT"),409,"REVISION_CONFLICT"},new Object[]{new ama.contract.MinutesContracts.Failure(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED),413,"REQUEST_TOO_LARGE"})) {
+   doThrow((RuntimeException)fixture[0]).when(local).recover(any(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString());
+   var result=mvc.perform(post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/publications/12345678-1234-4234-8234-123456789abd/local-recoveries").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","local-recovery-map").contentType("application/json").content("{\"taskId\":\"12345678-1234-4234-8234-123456789abe\",\"expectedRevisionToken\":\"13\",\"expectedPublicationRevisionToken\":\"1\"}")).andReturn();assertEquals(fixture[1],result.getResponse().getStatus());assertTrue(result.getResponse().getContentAsString().contains((String)fixture[2]));assertEquals("private, no-store",result.getResponse().getHeader("Cache-Control"));assertNull(result.getResponse().getForwardedUrl());
+  }
+ }
+}

## backend/contracts/src/main/java/ama/contract/MinutesContracts.java
--- a/backend/contracts/src/main/java/ama/contract/MinutesContracts.java
+++ b/backend/contracts/src/main/java/ama/contract/MinutesContracts.java
@@ -323,30 +323,42 @@
     }
     public record GraphSelection(String meetingId,String minutesRevisionId,String elementSetId,String exclusionRevisionId,String appliedInputId) {
         public GraphSelection {uuid(meetingId);if(minutesRevisionId!=null)uuid(minutesRevisionId);if(elementSetId!=null)uuid(elementSetId);if(exclusionRevisionId!=null)uuid(exclusionRevisionId);if(appliedInputId!=null)uuid(appliedInputId);}
     }
     public record Fence(long meetingRevision,long subjectBusinessVersion,long cancelEpoch) {public Fence {uint(meetingRevision);uint(subjectBusinessVersion);uint(cancelEpoch);}}
     public record GenerationFacts(String inputId,StoredProvenanceFacts stored) {public GenerationFacts {uuid(inputId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}}
     /** Actual saved revision facts; does not assert confirmation or write permission. */
     public record ConfirmationDraft(String meetingId,String revisionId,StoredProvenanceFacts stored) {
         public ConfirmationDraft {uuid(meetingId);uuid(revisionId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}
     }
     public record ConfirmationFacts(String confirmationId,StoredProvenanceFacts stored) {public ConfirmationFacts {uuid(confirmationId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}}
     public enum ReviewKind { SOURCE_SUPPORT,ACTION_FIELDS }
     public record ReviewFacts(String meetingId,ReviewKind kind,String elementId,String minutesRevisionId,Anchor anchor,List<String> selectedEvidenceIds,StoredProvenanceFacts stored) {
         public ReviewFacts {uuid(meetingId);require(kind!=null&&(elementId==null)!=(minutesRevisionId==null),FailureCode.INPUT_INVALID);if(elementId!=null)uuid(elementId);if(minutesRevisionId!=null)uuid(minutesRevisionId);require((kind==ReviewKind.SOURCE_SUPPORT)==(anchor!=null),FailureCode.TARGET_INVALID);selectedEvidenceIds=List.copyOf(selectedEvidenceIds);require(stored!=null,FailureCode.INVALID_ARGUMENT);}
     }
+    /** Prospective final identity over actual saved facts; deliberately has no source hash or persisted row. */
+    public record FinalDraft(String confirmationId,String meetingId,long meetingRevision,String infoRevisionId,String rosterRevisionId,
+                             String transcriptConfirmationId,String minutesRevisionId,String materialSnapshotId,String elementSetId,
+                             String templateRevisionId,String exclusionRevisionId,String inputFingerprint,String confirmedBy,String confirmedAt,
+                             List<String> reviewIds,StoredProvenanceFacts stored) {
+        public FinalDraft {
+            for(String id:List.of(confirmationId,meetingId,infoRevisionId,rosterRevisionId,transcriptConfirmationId,minutesRevisionId,elementSetId,templateRevisionId,exclusionRevisionId))uuid(id);
+            if(materialSnapshotId!=null)uuid(materialSnapshotId);positive(meetingRevision);hash(inputFingerprint);
+            require(confirmedBy!=null&&!confirmedBy.isBlank()&&confirmedAt!=null&&stored!=null,FailureCode.INVALID_ARGUMENT);
+            reviewIds=List.copyOf(reviewIds);var unique=new HashSet<String>();for(String id:reviewIds){uuid(id);require(unique.add(id),FailureCode.SOURCE_MEMBERSHIP_MISMATCH);}
+        }
+    }
     public record FinalFacts(String confirmationId,StoredProvenanceFacts stored) {public FinalFacts {uuid(confirmationId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}}
 
     public record GraphBudget(CanonicalBudget canonical,int maxOriginDepth,long maxOriginNodes,long maxAggregateFacts) {
         public GraphBudget {require(canonical!=null&&maxOriginDepth>0&&maxOriginNodes>0&&maxAggregateFacts>0,FailureCode.INVALID_ARGUMENT);}
     }
 
     public record ReviewCoverage(String reviewId,String outcome,boolean wholeTargetSupported) {}
 
     public record ReviewScopeFact(String reviewId,Anchor anchor) {public ReviewScopeFact {uuid(reviewId);}}
 
     public record StoredTask(String taskId,TaskContracts.TaskSpec spec){public StoredTask {uuid(taskId);require(spec!=null,FailureCode.INVALID_ARGUMENT);}}
     /** New input fields only, deliberately excluding its own not-yet-computed fingerprint and TaskSpec. */
     public record GenerationDraft(String inputId,String meetingId,String transcriptConfirmationId,String materialSnapshotId,
       String templateRevisionId,String exclusionRevisionId,String elementSetId,String promptVersion,String modelVersion,Scope scope,String targetElementId) {
       public GenerationDraft {uuid(inputId);uuid(meetingId);uuid(transcriptConfirmationId);if(materialSnapshotId!=null)uuid(materialSnapshotId);uuid(templateRevisionId);uuid(exclusionRevisionId);if(elementSetId!=null)uuid(elementSetId);scalars(promptVersion);scalars(modelVersion);require(scope!=null,FailureCode.INVALID_ARGUMENT);require((scope==Scope.ELEMENT)==(targetElementId!=null),FailureCode.INPUT_INVALID);if(targetElementId!=null)uuid(targetElementId);}

## backend/core/src/main/java/ama/knowledge/ControlledKnowledgeDocumentRenderer.java
--- /dev/null
+++ b/backend/core/src/main/java/ama/knowledge/ControlledKnowledgeDocumentRenderer.java
@@ -0,0 +1,34 @@
+package ama.knowledge;
+import ama.canonical.CanonicalValue;
+import ama.config.CanonicalJson;
+import ama.contract.MinutesContracts.*;
+import java.util.*;
+import java.util.function.BooleanSupplier;
+import static ama.canonical.CanonicalValue.*;
+import static ama.contract.MinutesContracts.*;
+import static ama.contract.MinutesContracts.FailureCode.*;
+
+/** Exact saved content rendering. Provenance authority remains with the registered S/A/F caller. */
+public final class ControlledKnowledgeDocumentRenderer {
+ public static final String VERSION="ama.controlled-minutes-render.v1";
+ public record Member(String memberKind,String displayName){}
+ public record Meeting(String name,String categoryId,String actualDate,List<Member> roster){public Meeting{roster=List.copyOf(roster);}}
+ public byte[] render(FinalFacts selected,Meeting meeting,String rendererVersion,String defaultTemplateRevisionId,Artifact artifact,long limit,BooleanSupplier cancelled){
+  require(VERSION.equals(rendererVersion)&&limit>0&&limit<=8388608,PROFILE_UNAVAILABLE);
+  var facts=selected.stored();var finalRow=one(facts,Table.FINAL_CONFIRMATION,selected.confirmationId());require(defaultTemplateRevisionId.equals(finalRow.string("template_revision_id")),PROFILE_UNAVAILABLE);
+  var body=one(facts,Table.MINUTES_REVISION,finalRow.string("minutes_revision_id"));var template=one(facts,Table.TEMPLATE_REVISION,finalRow.string("template_revision_id"));
+  require(template.string("themes").equals("{\"schemaVersion\":\"ama.controlled-minutes-template.v1\",\"themes\":[\"OVERVIEW\",\"DISCUSSION\",\"CONCLUSION\",\"ACTION\",\"RISK\",\"OPEN_QUESTION\",\"DISAGREEMENT\"]}"),PROFILE_UNAVAILABLE);
+  var roster=new ArrayList<CanonicalValue>();for(var member:meeting.roster()){check(cancelled);roster.add(object(field("memberKind",string(member.memberKind())),field("displayName",string(member.displayName()))));}
+  var sections=new ArrayList<CanonicalValue>();for(Category category:Category.values()){
+   var items=new ArrayList<CanonicalValue>();var elements=facts.rows().stream().filter(r->r.table()==Table.MINUTES_ELEMENT&&finalRow.string("element_set_id").equals(r.string("set_id"))&&category.name().equals(r.string("category"))).sorted(Comparator.comparingLong((FullRow r)->r.number("ordinal")).thenComparing(r->r.string("id"))).toList();
+   for(var element:elements){check(cancelled);CanonicalValue accepted=nil();if(category==Category.ACTION){var actions=facts.rows().stream().filter(r->r.table()==Table.ACTION_ITEM&&element.string("id").equals(r.string("element_id"))).toList();require(actions.size()==1,PROVENANCE_UNAVAILABLE);var a=actions.getFirst();accepted=object(field("ownerEnterpriseId",nullable(a.nullableString("owner_enterprise_id"))),field("ownerDisplay",nullable(a.nullableString("owner_display"))),field("dueOn",nullable(a.nullableString("due_on"))));}
+    items.add(object(field("elementId",string(element.string("id"))),field("ordinal",number(element.number("ordinal"))),field("body",string(element.string("body"))),field("acceptedAction",accepted)));
+   }sections.add(object(field("category",string(category.name())),field("items",new Sequence(items))));
+  }
+  var payload=object(field("schemaVersion",string("ama.controlled-knowledge-document.v1")),field("title",string(body.string("title"))),field("meeting",object(field("name",string(meeting.name())),field("categoryId",string(meeting.categoryId())),field("actualDate",nullable(meeting.actualDate())),field("roster",new Sequence(roster)))),field("minutes",object(field("revisionId",string(body.string("id"))),field("title",string(body.string("title"))),field("body",string(body.string("body"))))),field("sections",new Sequence(sections)),field("provenance",object(field("confirmationId",string(finalRow.string("id"))),field("minutesRevisionId",string(body.string("id"))),field("elementSetId",string(finalRow.string("element_set_id"))),field("transcriptConfirmationId",string(finalRow.string("transcript_confirmation_id"))),field("inputFingerprint",string(finalRow.string("input_fingerprint"))),field("sourceManifestHash",string(finalRow.string("source_manifest_hash"))),field("renderArtifact",object(field("version",string(artifact.version())),field("sha256",string(artifact.sha256())))))));
+  return CanonicalJson.encode(sorted(payload,cancelled),limit,cancelled);
+ }
+ private static CanonicalValue nullable(String value){return value==null?nil():string(value);}
+ private static FullRow one(StoredProvenanceFacts facts,Table table,String id){var rows=facts.rows().stream().filter(r->r.table()==table&&id.equals(r.string("id"))).toList();require(rows.size()==1,PROVENANCE_UNAVAILABLE);return rows.getFirst();}
+ public static CanonicalValue sorted(CanonicalValue value,BooleanSupplier cancelled){check(cancelled);if(value instanceof ObjectValue object){var entries=new ArrayList<Entry>();for(var entry:object.entries())entries.add(field(entry.key(),sorted(entry.value(),cancelled)));entries.sort(Comparator.comparing(Entry::key));return new ObjectValue(entries);}if(value instanceof Sequence list){var values=new ArrayList<CanonicalValue>();for(var item:list.values())values.add(sorted(item,cancelled));return new Sequence(values);}return value;}
+}

## backend/core/src/main/java/ama/minutes/contract/GenerationArchiveRefs.java
--- a/backend/core/src/main/java/ama/minutes/contract/GenerationArchiveRefs.java
+++ b/backend/core/src/main/java/ama/minutes/contract/GenerationArchiveRefs.java
@@ -6,24 +6,29 @@
 import ama.contract.MinutesContracts.*;
 import java.util.*;
 import java.util.function.BooleanSupplier;
 import static ama.contract.MinutesContracts.*;
 import static ama.contract.MinutesContracts.FailureCode.*;
 import static ama.config.WireValues.*;
 /** Exact original root selection for one computation. Roots are data, never provenance authority. */
 public final class GenerationArchiveRefs {
  private final StoredFactsIndex facts;private final ConfigArchiveIndex index;private final ProcessingManifestReader parser;private final ArchiveReadState state;private final Map<Identity,Ref> roots=new HashMap<>();
  public GenerationArchiveRefs(StoredProvenanceFacts facts,ConfigArchiveIndex index,ProcessingManifestReader parser,ArchiveReadBudget read,GraphBudget graph,BooleanSupplier cancelled){this.facts=new StoredFactsIndex(facts,graph,cancelled);this.index=index;this.parser=parser;state=new ArchiveReadState(read,cancelled);}
  public List<Ref> binding(String input){generation(facts.one(Table.GENERATION_INPUT,input,PROVENANCE_UNAVAILABLE));return sorted();}
  public List<Ref> generation(String input){var row=facts.one(Table.GENERATION_INPUT,input,PROVENANCE_UNAVAILABLE);generation(row);set(row.nullableString("element_set_id"),row.string("meeting_id"));return sorted();}
  public List<Ref> draft(GenerationDraft draft){add(find(ArtifactType.PROMPT_CONFIG,draft.promptVersion()));add(find(ArtifactType.MODEL_CONFIG,draft.modelVersion()));confirmation(draft.transcriptConfirmationId());set(draft.elementSetId(),draft.meetingId());return sorted();}
  public List<Ref> review(ReviewFacts review,String profile){add(find(ArtifactType.REVIEW_HASH,profile));String set=review.elementId()==null?null:facts.one(Table.MINUTES_ELEMENT,review.elementId(),PROVENANCE_UNAVAILABLE).string("set_id");var graph=facts.sources.graph(new GraphSelection(review.meetingId(),review.minutesRevisionId(),set,null,null),new HashSet<>(review.selectedEvidenceIds()));walk(graph,review.meetingId());return sorted();}
  public List<Ref> finalSources(String id){var row=facts.one(Table.FINAL_CONFIRMATION,id,PROVENANCE_UNAVAILABLE);var body=facts.one(Table.MINUTES_REVISION,row.string("minutes_revision_id"),PROVENANCE_UNAVAILABLE);String input=body.string("generation_input_id");generation(facts.one(Table.GENERATION_INPUT,input,PROVENANCE_UNAVAILABLE));for(var member:facts.matching(Table.FINAL_RESULT_REVIEW,"confirmation_id",id))add(find(ArtifactType.REVIEW_HASH,facts.one(Table.RESULT_REVIEW,member.string("review_id"),PROVENANCE_UNAVAILABLE).string("hash_profile")));var graph=facts.sources.graph(new GraphSelection(row.string("meeting_id"),body.string("id"),row.string("element_set_id"),row.string("exclusion_revision_id"),input),null);facts.sources.walkInputs(List.of(input),row.string("meeting_id"));walk(graph,row.string("meeting_id"));return sorted();}
+ public List<Ref> finalSources(FinalDraft draft){
+  var body=facts.one(Table.MINUTES_REVISION,draft.minutesRevisionId(),PROVENANCE_UNAVAILABLE);String input=body.string("generation_input_id");generation(facts.one(Table.GENERATION_INPUT,input,PROVENANCE_UNAVAILABLE));
+  for(String id:draft.reviewIds())add(find(ArtifactType.REVIEW_HASH,facts.one(Table.RESULT_REVIEW,id,PROVENANCE_UNAVAILABLE).string("hash_profile")));
+  var graph=facts.sources.graph(new GraphSelection(draft.meetingId(),body.string("id"),draft.elementSetId(),draft.exclusionRevisionId(),input),null);facts.sources.walkInputs(List.of(input),draft.meetingId());walk(graph,draft.meetingId());return sorted();
+ }
  private void set(String set,String meeting){if(set!=null)walk(facts.sources.graph(new GraphSelection(meeting,null,set,null,null),null),meeting);}
  private void walk(SourceGraph graph,String meeting){facts.sources.walk(graph.sourceUsages(),meeting);usages(graph);for(var entry:facts.sources.inputGraphs.entrySet()){generation(facts.one(Table.GENERATION_INPUT,entry.getKey(),PROVENANCE_UNAVAILABLE));usages(entry.getValue());}}
  private void usages(SourceGraph graph){for(var usage:graph.sourceUsages()){check(state.cancelled);if(usage.sourceVersion() instanceof TranscriptVersion t)confirmation(t.confirmationId());}}
  private void generation(FullRow input){add(find(ArtifactType.PROMPT_CONFIG,input.string("prompt_version")));add(find(ArtifactType.MODEL_CONFIG,input.string("model_config_version")));confirmation(input.string("transcript_confirmation_id"));}
  private void confirmation(String id){var confirmation=facts.one(Table.TRANSCRIPT_CONFIRMATION,id,PROVENANCE_UNAVAILABLE);var revision=facts.one(Table.TRANSCRIPT_REVISION,confirmation.string("revision_id"),PROVENANCE_UNAVAILABLE);var processing=facts.one(Table.TRANSCRIPTION_INPUT,revision.string("processing_input_id"),PROVENANCE_UNAVAILABLE);byte[] bytes=processing.string("config_manifest").getBytes(java.nio.charset.StandardCharsets.UTF_8);var manifest=fields(parser.read(state.capture(bytes,true),state),"schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps");var configs=fields(manifest.get("configs"),"processing","parser","model","prompt","profiles");var ref=ref(configs.get("processing"));require(ref.artifactType()==ArtifactType.PROCESSING_CONFIG&&ref.version().equals(processing.string("processing_config_version")),PROFILE_UNAVAILABLE);add(ref);}
  private Ref find(ArtifactType type,String version){var found=index.entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==type&&r.version().equals(version)).toList();require(found.size()==1,PROFILE_UNAVAILABLE);return found.getFirst();}
  private void add(Ref ref){check(state.cancelled);require(index.entries().stream().filter(e->e.ref().equals(ref)).count()==1,PROFILE_UNAVAILABLE);var old=roots.putIfAbsent(ref.identity(),ref);require(old==null||old.equals(ref),PROFILE_UNAVAILABLE);require(roots.size()<=state.budget.maxArtifacts(),BUDGET_EXCEEDED);}
  private List<Ref> sorted(){return roots.values().stream().sorted((a,b)->{int type=a.artifactType().name().compareTo(b.artifactType().name());return type!=0?type:CanonicalValueCodec.compareUtf8(a.version(),b.version(),state.cancelled);}).toList();}
 }

## backend/core/src/main/java/ama/minutes/contract/ResultHashProfiles.java
--- a/backend/core/src/main/java/ama/minutes/contract/ResultHashProfiles.java
+++ b/backend/core/src/main/java/ama/minutes/contract/ResultHashProfiles.java
@@ -94,65 +94,82 @@
  }
 
  static ReviewFacts storedReview(FullRow review,StoredFactsIndex index){
   var kind=ReviewKind.valueOf(review.string("review_kind"));Anchor anchor=null;
   if(kind==ReviewKind.SOURCE_SUPPORT){
    var scopes=new ArrayList<ReviewScopeFact>();for(var fact:index.facts.reviewScopes()){check(index.cancelled);if(fact.reviewId().equals(review.string("id")))scopes.add(fact);}
    require(scopes.size()==1&&scopes.getFirst().anchor()!=null,TARGET_INVALID);anchor=scopes.getFirst().anchor();
    require(OutputValidation.anchorText(anchor).equals(review.nullableString("target_anchor")),TARGET_INVALID);
   }else require(review.nullableString("target_anchor")==null,TARGET_INVALID);
   var ids=new ArrayList<String>();for(var membership:index.matching(Table.RESULT_REVIEW_EVIDENCE,"review_id",review.string("id")))ids.add(membership.string("evidence_id"));
   return new ReviewFacts(review.string("meeting_id"),kind,review.nullableString("element_id"),review.nullableString("minutes_revision_id"),anchor,ids,index.facts);
  }
  public CanonicalValue finalData(FinalFacts facts,GraphBudget budget,BooleanSupplier cancelled){
   return finalData(facts,new StoredFactsIndex(facts.stored(),budget,cancelled));
  }
- private CanonicalValue finalData(FinalFacts facts,StoredFactsIndex index){
-  var budget=index.budget;var cancelled=index.cancelled;var finalRow=index.one(Table.FINAL_CONFIRMATION,facts.confirmationId(),PROVENANCE_UNAVAILABLE);String meeting=finalRow.string("meeting_id");
-  var body=index.one(Table.MINUTES_REVISION,finalRow.string("minutes_revision_id"),PROVENANCE_UNAVAILABLE);index.meeting(body,meeting);
+ private CanonicalValue finalData(FinalFacts facts,StoredFactsIndex index){return finalData(selection(facts,index),index);}
+ public CanonicalValue finalData(FinalDraft draft,GraphBudget budget,BooleanSupplier cancelled){return finalData(draft,new StoredFactsIndex(draft.stored(),budget,cancelled));}
+ private static FinalDraft selection(FinalFacts facts,StoredFactsIndex index){
+  var row=index.one(Table.FINAL_CONFIRMATION,facts.confirmationId(),PROVENANCE_UNAVAILABLE);
+  return new FinalDraft(row.string("id"),row.string("meeting_id"),row.number("meeting_revision"),row.string("info_revision_id"),row.string("roster_revision_id"),row.string("transcript_confirmation_id"),row.string("minutes_revision_id"),row.nullableString("material_snapshot_id"),row.string("element_set_id"),row.string("template_revision_id"),row.string("exclusion_revision_id"),row.string("input_fingerprint"),row.string("confirmed_by"),row.string("confirmed_at"),index.matching(Table.FINAL_RESULT_REVIEW,"confirmation_id",row.string("id")).stream().map(r->r.string("review_id")).toList(),facts.stored());
+ }
+ private static String selected(FinalDraft draft,String field){return switch(field){
+  case "id"->draft.confirmationId();case "meeting_id"->draft.meetingId();case "minutes_revision_id"->draft.minutesRevisionId();case "element_set_id"->draft.elementSetId();case "transcript_confirmation_id"->draft.transcriptConfirmationId();case "material_snapshot_id"->draft.materialSnapshotId();case "template_revision_id"->draft.templateRevisionId();case "exclusion_revision_id"->draft.exclusionRevisionId();case "input_fingerprint"->draft.inputFingerprint();default->throw fail(INVALID_ARGUMENT);
+ };}
+ private static CanonicalValue finalMembership(FinalDraft draft,BooleanSupplier cancelled){
+  var ids=new ArrayList<>(draft.reviewIds());ids.sort((a,b)->CanonicalValueCodec.compareUtf8(a,b,cancelled));var rows=new ArrayList<CanonicalValue>();
+  for(String id:ids){check(cancelled);rows.add(object(field("confirmation_id",string(draft.confirmationId())),field("review_id",string(id))));}return new CanonicalValue.Sequence(rows);
+ }
+ private CanonicalValue finalData(FinalDraft draft,StoredFactsIndex index){
+  var budget=index.budget;var cancelled=index.cancelled;String meeting=draft.meetingId();
+  var body=index.one(Table.MINUTES_REVISION,selected(draft,"minutes_revision_id"),PROVENANCE_UNAVAILABLE);index.meeting(body,meeting);
   String inputId=body.nullableString("generation_input_id");require(inputId!=null,PROFILE_UNAVAILABLE);var input=index.one(Table.GENERATION_INPUT,inputId,PROFILE_UNAVAILABLE);index.meeting(input,meeting);
-  for(var column:List.of("transcript_confirmation_id","material_snapshot_id","template_revision_id","exclusion_revision_id"))require(Objects.equals(input.nullableString(column),finalRow.nullableString(column)),SOURCE_MEMBERSHIP_MISMATCH);
-  require(input.string("input_fingerprint").equals(finalRow.string("input_fingerprint")),FINGERPRINT_MISMATCH);
-  var session=index.sources;var graph=session.graph(new GraphSelection(meeting,body.string("id"),finalRow.string("element_set_id"),finalRow.string("exclusion_revision_id"),inputId),null);session.walk(graph.sourceUsages(),meeting);
+  for(var column:List.of("transcript_confirmation_id","material_snapshot_id","template_revision_id","exclusion_revision_id"))require(Objects.equals(input.nullableString(column),selected(draft,column)),SOURCE_MEMBERSHIP_MISMATCH);
+  require(input.string("input_fingerprint").equals(selected(draft,"input_fingerprint")),FINGERPRINT_MISMATCH);
+  var session=index.sources;var graph=session.graph(new GraphSelection(meeting,body.string("id"),selected(draft,"element_set_id"),selected(draft,"exclusion_revision_id"),inputId),null);session.walk(graph.sourceUsages(),meeting);
   var evidenceIds=new HashSet<String>();for(var usage:graph.sourceUsages())if(usage.usage() instanceof EvidenceUsage e)evidenceIds.add(e.evidenceId());
   var evidenceRows=new ArrayList<FullRow>();for(var row:index.rows(Table.EVIDENCE_LINK))if(evidenceIds.contains(row.string("id")))evidenceRows.add(row);
-  var setRows=RowsetProjection.elements(index,meeting,finalRow.string("element_set_id"));
+  var setRows=RowsetProjection.elements(index,meeting,selected(draft,"element_set_id"));
   var directoryRows=new ArrayList<FullRow>();var elementIds=new HashSet<String>();
   for(var row:setRows){check(cancelled);if(RowsetProjection.DIRECTORY_TABLES.contains(row.table()))directoryRows.add(row);if(row.table()==Table.MINUTES_ELEMENT)elementIds.add(row.string("id"));}
-  var exclusionRows=RowsetProjection.exclusions(index,meeting,finalRow.string("exclusion_revision_id"));
-  var reviewRows=new ArrayList<FullRow>();var reviewEvidence=new ArrayList<FullRow>();var finalReviews=index.matching(Table.FINAL_RESULT_REVIEW,"confirmation_id",finalRow.string("id"));
+  var exclusionRows=RowsetProjection.exclusions(index,meeting,selected(draft,"exclusion_revision_id"));
+  var reviewRows=new ArrayList<FullRow>();var reviewEvidence=new ArrayList<FullRow>();var finalReviews=draft.reviewIds();
   var reviewNumbers=new HashSet<Long>();
   for(var member:finalReviews){
-   check(cancelled);var review=index.one(Table.RESULT_REVIEW,member.string("review_id"),PROVENANCE_UNAVAILABLE);index.meeting(review,meeting);positive(review.number("review_no"));require(reviewNumbers.add(review.number("review_no")),SOURCE_MEMBERSHIP_MISMATCH);
+   check(cancelled);var review=index.one(Table.RESULT_REVIEW,member,PROVENANCE_UNAVAILABLE);index.meeting(review,meeting);positive(review.number("review_no"));require(reviewNumbers.add(review.number("review_no")),SOURCE_MEMBERSHIP_MISMATCH);
    require(review.nullableString("element_id")!=null&&elementIds.contains(review.string("element_id"))||body.string("id").equals(review.nullableString("minutes_revision_id")),SOURCE_MEMBERSHIP_MISMATCH);
    require(review.string("hash_profile").equals(REVIEW_PROFILE),PROFILE_UNAVAILABLE);
    var reviewFacts=storedReview(review,index);var target=targetData(reviewFacts,index);
    String targetHash=new CanonicalValueCodec().sha256(ContractValues.envelope(review.string("hash_profile"),"REVIEW_TARGET",target),budget.canonical(),cancelled);
    String sourcesHash=new CanonicalValueCodec().sha256(ContractValues.envelope(review.string("hash_profile"),"REVIEW_SOURCES",sourcesData(reviewFacts,index)),budget.canonical(),cancelled);
    require(targetHash.equals(review.string("target_hash"))&&sourcesHash.equals(review.string("source_set_hash")),FINGERPRINT_MISMATCH);
    reviewRows.add(review);reviewEvidence.addAll(index.matching(Table.RESULT_REVIEW_EVIDENCE,"review_id",review.string("id")));
   }
   check(cancelled);
-  return object(field("meetingId",string(meeting)),field("confirmationId",string(finalRow.string("id"))),field("minutesRevisionId",string(body.string("id"))),field("elementSetId",string(finalRow.string("element_set_id"))),
-   field("transcriptConfirmationId",string(finalRow.string("transcript_confirmation_id"))),field("materialSnapshotId",ContractValues.nullable(finalRow.nullableString("material_snapshot_id"))),field("templateRevisionId",string(finalRow.string("template_revision_id"))),field("exclusionRevisionId",string(finalRow.string("exclusion_revision_id"))),field("inputFingerprint",string(input.string("input_fingerprint"))),
+  return object(field("meetingId",string(meeting)),field("confirmationId",string(selected(draft,"id"))),field("minutesRevisionId",string(body.string("id"))),field("elementSetId",string(selected(draft,"element_set_id"))),
+   field("transcriptConfirmationId",string(selected(draft,"transcript_confirmation_id"))),field("materialSnapshotId",ContractValues.nullable(selected(draft,"material_snapshot_id"))),field("templateRevisionId",string(selected(draft,"template_revision_id"))),field("exclusionRevisionId",string(selected(draft,"exclusion_revision_id"))),field("inputFingerprint",string(input.string("input_fingerprint"))),
    field("locatorRows",RowValues.rows(graph.locatorRows(),cancelled)),field("sourceUsages",RowValues.usages(graph.sourceUsages(),cancelled)),field("evidenceRows",RowValues.rows(evidenceRows,cancelled)),
    field("directoryRows",RowsetProjection.tableArray(directoryRows,RowsetProjection.DIRECTORY_TABLES,index)),field("exclusionRows",RowsetProjection.tableArray(exclusionRows,EnumSet.of(Table.EXCLUSION_REVISION,Table.SENSITIVE_EXCLUSION),index)),
-   field("reviewRows",RowValues.rows(reviewRows,cancelled)),field("reviewEvidenceRows",RowValues.rows(reviewEvidence,cancelled)),field("finalReviewRows",RowValues.rows(finalReviews,cancelled)));
+   field("reviewRows",RowValues.rows(reviewRows,cancelled)),field("reviewEvidenceRows",RowValues.rows(reviewEvidence,cancelled)),field("finalReviewRows",finalMembership(draft,cancelled)));
  }
  public String finalSources(FinalFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
   var operation=resolver.open(facts.stored(),cancelled);String result=new ResultHashProfiles(operation.resolver()).finalSourcesBound(facts,profile,budget,cancelled);operation.finish();check(cancelled);return result;
  }
- private String finalSourcesBound(FinalFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
-  var index=new StoredFactsIndex(facts.stored(),budget,cancelled);var f=index.one(Table.FINAL_CONFIRMATION,facts.confirmationId(),PROVENANCE_UNAVAILABLE);var body=index.one(Table.MINUTES_REVISION,f.string("minutes_revision_id"),PROVENANCE_UNAVAILABLE);
+ public String finalSources(FinalDraft draft,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
+  var operation=resolver.open(draft.stored(),cancelled);String result=new ResultHashProfiles(operation.resolver()).finalSourcesBound(draft,profile,budget,cancelled);operation.finish();check(cancelled);return result;
+ }
+ private String finalSourcesBound(FinalFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){return finalSourcesBound(selection(facts,new StoredFactsIndex(facts.stored(),budget,cancelled)),profile,budget,cancelled);}
+ private String finalSourcesBound(FinalDraft draft,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
+  var index=new StoredFactsIndex(draft.stored(),budget,cancelled);var body=index.one(Table.MINUTES_REVISION,draft.minutesRevisionId(),PROVENANCE_UNAVAILABLE);
   String inputId=body.nullableString("generation_input_id");require(inputId!=null,PROFILE_UNAVAILABLE);var input=index.one(Table.GENERATION_INPUT,inputId,PROFILE_UNAVAILABLE);
-  var bound=resolver.generation(input,facts.stored(),budget,cancelled);require(bound.equals(index.config(input))&&bound.profiles().definition(ProfileKind.FINAL_SOURCES).equals(profile),PROFILE_UNAVAILABLE);
-  GenerationFingerprint.historicalFence(input,facts.stored(),cancelled);
-  for(var member:index.matching(Table.FINAL_RESULT_REVIEW,"confirmation_id",f.string("id"))){
-   check(cancelled);var review=index.one(Table.RESULT_REVIEW,member.string("review_id"),PROVENANCE_UNAVAILABLE);var reviewFacts=storedReview(review,index);
-   require(review.string("hash_profile").equals(REVIEW_PROFILE),PROFILE_UNAVAILABLE);var reviewProfile=resolver.review(review,facts.stored(),budget,cancelled);
+  var bound=resolver.generation(input,draft.stored(),budget,cancelled);require(bound.equals(index.config(input))&&bound.profiles().definition(ProfileKind.FINAL_SOURCES).equals(profile),PROFILE_UNAVAILABLE);
+  GenerationFingerprint.historicalFence(input,draft.stored(),cancelled);
+  for(var member:draft.reviewIds()){
+   check(cancelled);var review=index.one(Table.RESULT_REVIEW,member,PROVENANCE_UNAVAILABLE);var reviewFacts=storedReview(review,index);
+   require(review.string("hash_profile").equals(REVIEW_PROFILE),PROFILE_UNAVAILABLE);var reviewProfile=resolver.review(review,draft.stored(),budget,cancelled);
    require(targetBound(reviewFacts,reviewProfile,index).equals(review.string("target_hash"))&&sourcesBound(reviewFacts,reviewProfile,index).equals(review.string("source_set_hash")),FINGERPRINT_MISMATCH);
   }
-  var session=index.sources;var graph=session.graph(new GraphSelection(f.string("meeting_id"),body.string("id"),f.string("element_set_id"),f.string("exclusion_revision_id"),inputId),null);
-  session.walkInputs(List.of(inputId),f.string("meeting_id"));OriginChainVerifier.verify(index,session,graph,resolver);
-  return DomainHash.hash(profile,FINAL_PROFILE,"FINAL_SOURCES",finalData(facts,index),TranscriptConfirmationHash.minimum(budget.canonical(),resolver.generationBudget(input,cancelled)),cancelled);
+  var session=index.sources;var graph=session.graph(new GraphSelection(selected(draft,"meeting_id"),body.string("id"),selected(draft,"element_set_id"),selected(draft,"exclusion_revision_id"),inputId),null);
+  session.walkInputs(List.of(inputId),selected(draft,"meeting_id"));OriginChainVerifier.verify(index,session,graph,resolver);
+  return DomainHash.hash(profile,FINAL_PROFILE,"FINAL_SOURCES",finalData(draft,index),TranscriptConfirmationHash.minimum(budget.canonical(),resolver.generationBudget(input,cancelled)),cancelled);
  }
 }

## backend/core/src/main/java/ama/task/ExternalOperationPort.java
--- a/backend/core/src/main/java/ama/task/ExternalOperationPort.java
+++ b/backend/core/src/main/java/ama/task/ExternalOperationPort.java
@@ -28,16 +28,18 @@
     Optional<OperationToken> claimReconciliation(String owner);
     boolean renew(OperationToken token);
     /** Revokes only local lookup minting; already verified receipts retain their fact-recording rules. */
     default void releaseRecordedLookup(OperationToken token) {}
     /**
      * Independent short transaction; rejects ambient transactions before any effects. Domain policy
      * prelocks roots before tasks/operation. The current independent token authorizes business effects;
      * stale-token authoritative contradictions can only be recorded/quarantined, never regain authority.
      * Generation changes restart the whole owned transaction, bounded to four discovery attempts.
      * @throws ObservationRetryRequiredException retain and resubmit the SAME authoritative fact outside
      * a transaction; this is not an acknowledgment or permission to repeat the external request.
      */
     ObservationResult observe(OperationToken token, ExternalObservation observation, Runnable prelockedDomainEffects);
     /** Explicit user retry only: old current execution must be FAILED with complete verified zero-effect proof. */
     String retryAfterZeroEffect(String operationId, long expectedExecutionNo, TaskSpec newTask, ExternalRequest request);
+ default PublicationReplacementPolicy.ReplacementAccepted replaceZeroEffectPublication(PublicationReplacementPolicy.PreparedPublicationReplacement prepared){throw new UnsupportedOperationException("Publication replacement not installed");}
+
 }

## backend/core/src/main/java/ama/task/PublicationLocalRecoveryUnavailable.java
--- /dev/null
+++ b/backend/core/src/main/java/ama/task/PublicationLocalRecoveryUnavailable.java
@@ -0,0 +1,3 @@
+package ama.task;
+/** Expected live-owner/marker/history exclusion; never a no-effect receipt. */
+public final class PublicationLocalRecoveryUnavailable extends IllegalStateException {public PublicationLocalRecoveryUnavailable(String safeReason){super(safeReason);}}

## backend/core/src/main/java/ama/task/PublicationLocalTerminationPolicy.java
--- /dev/null
+++ b/backend/core/src/main/java/ama/task/PublicationLocalTerminationPolicy.java
@@ -0,0 +1,11 @@
+package ama.task;
+/** Fixed publication-only ordinary terminal hook. No caller-selected writer or constructible context. */
+public interface PublicationLocalTerminationPolicy extends RecordedFactRecoveryPolicy {
+ interface LocalTerminalContext {}
+ interface PreparedLocalRecovery {ama.contract.TaskContracts.TaskSpec spec();}
+ record RecoveryBinding(ama.contract.TaskContracts.TaskSpec spec,String taskId,PreparedDomain prepared) {}
+ default RecoveryBinding requireLocalRecovery(PreparedLocalRecovery recovery){throw new IllegalArgumentException("Explicit local recovery not installed");}
+ enum Phase { VALIDATING, SEALING }
+ boolean validateLocalTerminal(PreparedDomain prepared,LocalTerminalContext context);
+ void sealLocalTerminal(PreparedDomain prepared,LocalTerminalContext context);
+}

## backend/core/src/main/java/ama/task/PublicationReplacementPolicy.java
--- /dev/null
+++ b/backend/core/src/main/java/ama/task/PublicationReplacementPolicy.java
@@ -0,0 +1,13 @@
+package ama.task;
+import ama.contract.TaskContracts.TaskSpec;import ama.contract.ExternalContracts.ExternalRequest;import java.util.*;
+/** Fixed registered publication transition; DTOs cannot authenticate a prepared replacement. */
+public interface PublicationReplacementPolicy extends PublicationLocalTerminationPolicy {
+ interface PreparedPublicationReplacement {TaskSpec spec();}
+ interface ReplacementContext {}
+ record ReplacementBinding(TaskSpec next,String taskId,List<TaskSpec> specs,Map<String,TaskSpec> oldTasks,PreparedDomain prepared,String operationId,long expectedExecutionNo,String previousLogicalHash,String executionId,ExternalRequest request){
+  public ReplacementBinding{specs=List.copyOf(specs);oldTasks=Map.copyOf(oldTasks);}
+ }
+ record ReplacementAccepted(String taskId,String operationId,String executionId,Long executionNo){}
+ ReplacementBinding requireReplacement(PreparedPublicationReplacement prepared);
+ void lockReplacementRemainder(PreparedDomain domain,ReplacementContext context,PreparedPublicationReplacement prepared);
+}

## backend/core/src/test/java/ama/minutes/contract/Task11FinalDraftTest.java
--- /dev/null
+++ b/backend/core/src/test/java/ama/minutes/contract/Task11FinalDraftTest.java
@@ -0,0 +1,22 @@
+package ama.minutes.contract;
+import ama.contract.MinutesContracts.*;
+import java.util.*;
+import org.junit.jupiter.api.Test;
+import static ama.minutes.contract.StoredSourceGraphTest.*;
+import static ama.contract.MinutesContracts.FailureCode.*;
+import static org.junit.jupiter.api.Assertions.*;
+
+/** Retains the exact existing final golden and exercises prospective identity without fake rows. */
+class Task11FinalDraftTest extends FinalAndCoverageTest {
+ static FinalDraft draft(StoredProvenanceFacts facts,List<String> reviews){return new FinalDraft(id(20),id(1),1,id(21),id(22),id(7),id(13),null,id(11),id(9),id(10),H,"human",T,reviews,facts);}
+ @Test void draftWithoutFinalRowsEqualsOriginalPersistedGoldenProjection(){
+  var stored=finalFacts();var rows=stored.rows().stream().filter(r->r.table()!=Table.FINAL_CONFIRMATION&&r.table()!=Table.FINAL_RESULT_REVIEW).toList();
+  var actual=new StoredProvenanceFacts(rows,stored.files(),stored.configurations(),stored.evidenceAnchors(),100000L);
+  assertTrue(actual.rows().stream().noneMatch(r->r.table()==Table.FINAL_CONFIRMATION));
+  var profiles=new ResultHashProfiles();assertEquals(profiles.finalData(new FinalFacts(id(20),stored),BUDGET,()->false),profiles.finalData(draft(actual,List.of()),BUDGET,()->false));
+ }
+ @Test void prospectiveMembershipCannotInventOrDuplicateStoredReview(){
+  var actual=finalFacts();fails(SOURCE_MEMBERSHIP_MISMATCH,()->draft(actual,List.of(id(40),id(40))));
+  fails(PROVENANCE_UNAVAILABLE,()->new ResultHashProfiles().finalData(draft(actual,List.of(id(40))),BUDGET,()->false));
+ }
+}

## backend/integration-tests/src/test/java/ama/api/Task11RuntimeProbe.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/api/Task11RuntimeProbe.java
@@ -0,0 +1,2 @@
+package ama.api;
+public final class Task11RuntimeProbe {public static ama.transcript.JdbcPublicationLocalRecovery localPublication(org.springframework.context.ApplicationContext app){return app.getBean(MinutesConfiguration.Runtime.class).localPublications();}public static ama.transcript.JdbcPublicationService publication(org.springframework.context.ApplicationContext app){return app.getBean(MinutesConfiguration.Runtime.class).publications();}}

## backend/integration-tests/src/test/java/ama/publication/Task11BootstrapIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/publication/Task11BootstrapIT.java
@@ -0,0 +1,17 @@
+package ama.publication;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.JdbcTemplate;import static org.junit.jupiter.api.Assertions.*;
+class Task11BootstrapIT {
+ @Test void freshApprovedSchemaNormalSixteenThenZeroPreservesCompleteUtcHistoryAndSubmissionMetadata()throws Exception{
+  var source=Task11Source.open();var j=new JdbcTemplate(source);j.setQueryTimeout(5);assertEquals("ama_t11_9714255c9e2c_behavior",j.queryForObject("SELECT DATABASE()",String.class));
+  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class));
+  var session=j.queryForMap("SELECT @@session.time_zone AS zone,@@character_set_connection AS charset,@@session.lock_wait_timeout AS metadata_wait,@@session.innodb_lock_wait_timeout AS row_wait");assertEquals("+00:00",session.get("zone"));assertEquals("utf8mb4",session.get("charset"));assertEquals(5,((Number)session.get("metadata_wait")).intValue());assertEquals(5,((Number)session.get("row_wait")).intValue());
+  assertEquals(16,new ama.db.SchemaMigrator(source).migrate());var before=history(j);assertEquals(16,before.size());assertEquals("013.4",before.getLast().get("version"));assertEquals("-385117638",before.get(14).get("checksum"));assertEquals(0,new ama.db.SchemaMigrator(source).migrate());var after=history(j);assertEquals(before,after);
+  var columns=j.queryForList("SELECT COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,COLLATION_NAME,EXTRA FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='publication_submission' ORDER BY ORDINAL_POSITION");assertEquals(19,columns.size());assertEquals("task_id",columns.getFirst().get("COLUMN_NAME"));assertEquals("ascii_bin",columns.getFirst().get("COLLATION_NAME"));assertEquals("local_execution_id",columns.getLast().get("COLUMN_NAME"));
+  var indexes=j.queryForList("SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='publication_submission' ORDER BY INDEX_NAME,SEQ_IN_INDEX");assertEquals(7,indexes.stream().map(x->x.get("INDEX_NAME")).distinct().count());
+  var fks=j.queryForList("SELECT k.CONSTRAINT_NAME,k.COLUMN_NAME,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name WHERE k.table_schema=DATABASE() AND k.table_name='publication_submission' ORDER BY k.CONSTRAINT_NAME");assertEquals(6,fks.size());assertEquals(Set.of("durable_task","publication","final_confirmation","enterprise_identity","external_execution"),fks.stream().map(x->x.get("REFERENCED_TABLE_NAME")).collect(java.util.stream.Collectors.toSet()));for(var fk:fks){assertTrue(Set.of("RESTRICT","NO ACTION").contains(fk.get("UPDATE_RULE")));assertTrue(Set.of("RESTRICT","NO ACTION").contains(fk.get("DELETE_RULE")));}
+  var checks=j.queryForList("SELECT c.CONSTRAINT_NAME,c.CHECK_CLAUSE,t.ENFORCED FROM information_schema.check_constraints c JOIN information_schema.table_constraints t ON t.constraint_schema=c.constraint_schema AND t.constraint_name=c.constraint_name WHERE t.table_schema=DATABASE() AND t.table_name='publication_submission' ORDER BY c.CONSTRAINT_NAME");assertEquals(4,checks.size());for(var check:checks)assertEquals("YES",check.get("ENFORCED"));
+  var table=j.queryForMap("SELECT ENGINE,TABLE_COLLATION FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='publication_submission'");assertEquals("InnoDB",table.get("ENGINE"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_submission",Integer.class));
+  var result=new LinkedHashMap<String,Object>();result.put("initialTables",0);result.put("initialMigrations",16);result.put("rerunMigrations",0);result.put("session",session);result.put("before",before);result.put("after",after);result.put("table",table);result.put("columns",columns);result.put("indexes",indexes);result.put("foreignKeys",fks);result.put("checks",checks);Files.writeString(Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"),"full-sixteen-history-metadata.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
+ }
+ static List<Map<String,String>> history(JdbcTemplate j){return j.queryForList("SELECT installed_rank,version,description,type,script,checksum,installed_by,DATE_FORMAT(installed_on,'%Y-%m-%dT%H:%i:%s') AS installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank").stream().map(row->{var out=new LinkedHashMap<String,String>();row.forEach((key,value)->out.put(key,value==null?null:value.toString()));return (Map<String,String>)out;}).toList();}
+}

## backend/integration-tests/src/test/java/ama/publication/Task11Source.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/publication/Task11Source.java
@@ -0,0 +1,5 @@
+package ama.publication;
+import javax.sql.DataSource;import static org.junit.jupiter.api.Assertions.*;
+public final class Task11Source {
+ public static DataSource open(){String url=System.getenv("AMA_TEST_DATABASE_URL");assertEquals("jdbc:mysql://127.0.0.1:46151/ama_t11_9714255c9e2c_behavior",url);return ama.minutes.ControlledMinutesDataSource.create(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11FinalPublicationIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11FinalPublicationIT.java
@@ -0,0 +1,84 @@
+package ama.storage;
+
+import ama.transcript.*;
+import ama.model.*;
+import ama.task.*;
+import ama.contract.MeetingContracts.*;
+import java.util.*;
+import java.nio.file.Files;
+import java.time.Instant;
+import static org.junit.jupiter.api.Assertions.*;
+
+/** Real Task11 fixture: no bootstrap, fake final rows, or inherited old-schema connection. */
+class Task11FinalPublicationIT extends Task10AdoptIT {
+ Task11KnowledgeFixture knowledge;boolean preserveKnowledge;
+ @org.junit.jupiter.api.AfterEach void stopKnowledge()throws Exception{if(knowledge!=null){long unresolved=j.queryForObject("SELECT COUNT(*) FROM external_execution e JOIN durable_task t ON t.id=e.task_id WHERE t.task_type='KNOWLEDGE_PUBLISH_V1' AND e.state IN ('MARKED','UNKNOWN')",Long.class);if(unresolved==0&&!preserveKnowledge)knowledge.close();else Files.writeString(receipt.resolve("knowledge-retained-unresolved.json"),json.writeValueAsString(Map.of("pid",knowledge.pid(),"port",knowledge.port,"store",knowledge.store.toString(),"unresolved",unresolved)));}}
+ @Override javax.sql.DataSource createSource(){return ama.publication.Task11Source.open();}
+ @Override String instruction(){return "controlled:rich-no-sensitive";}
+ @Override void preflightBeforeBusiness()throws Exception {
+  var queue=app.getBean(JdbcTaskQueue.class);
+  new JdbcModelPermitPool(new JdbcExternalOperations(queue),Map.of(ControlledMinutesProtocol.MODEL,new ModelConfiguration(ControlledMinutesProtocol.MODEL,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException("No cutoff proof");}).configure();
+  super.preflightBeforeBusiness();
+ }
+ @Override void beforeWorker(String task) {
+  try {
+  super.beforeWorker(task);
+  Files.writeString(receipt.resolve("task11-generation-pre-post-custody.json"),json.writeValueAsString(Map.ofEntries(
+   Map.entry("task",task),Map.entry("meeting",meeting),Map.entry("requester",owner),
+   Map.entry("providerPort",providerPort),Map.entry("clientPort",relay.port()),Map.entry("store",providerStore.toString()),
+   Map.entry("clientClass",ControlledMinutesClient.class.getName()),Map.entry("transport","ControlledMinutesHttpTransport via Task09ControlledRelay"),
+   Map.entry("authorizationIdentity","task09-owned-provider-test-authorization-value"),Map.entry("mode","CONTROLLED_INSTALLATION"),
+   Map.entry("signingKey","NOT_APPLICABLE_GENERATION_PROTOCOL"),Map.entry("lifecycle","fresh store; same-process provider; close in parent fixture try-with-resources"),
+   Map.entry("observedUtc",Instant.now().toString()),Map.entry("beforeFirstPost",true))));
+  } catch(Exception failure){throw new IllegalStateException(failure);}
+ }
+ long revision(){return j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting);}
+ @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
+  super.afterCandidate(candidate,facts);
+  var authority=ama.api.Task09RuntimeProbe.components(app).authority();
+  var limits=new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256);
+  var runtime=MinutesEditorRuntime.compose(authority,this::snapshot,pin,prompt,model,limits,isolated());
+  var repository=new ama.meeting.JdbcMeetingRepository(source,app.getBean(ama.db.TransactionRunner.class),app.getBean(ama.meeting.JdbcMeetingAccess.class),app.getBean(JdbcSubjectFences.class),app.getBean(JdbcOutbox.class),new ama.audit.JdbcAuditAdapter(source),id->{if(!id.equals("task11-controlled-category"))throw new CategoryUnavailable();},authority);
+  repository.save(actor(),meeting,"final-info",new SaveInfo(revision(),"Task11 actual reviewed meeting","task11-controlled-category",null,null,List.of(),List.of()),UUID.randomUUID().toString());
+  repository.confirm(actor(),meeting,"final-roster",new ConfirmRoster(revision()),UUID.randomUUID().toString());
+  String set=j.queryForObject("SELECT element_set_id FROM meeting WHERE id=?",String.class,meeting);
+  var elements=j.queryForList("SELECT id,category FROM minutes_element WHERE set_id=? AND category IN ('CONCLUSION','ACTION') ORDER BY category,ordinal,id",set);
+  assertEquals(3,elements.size());
+  for(var element:elements){
+   runtime.reviews().review(actor(),meeting,UUID.randomUUID().toString(),revision(),"ELEMENT",(String)element.get("id"),"SOURCE_SUPPORT","WHOLE","SUPPORTED",null,null,UUID.randomUUID().toString());
+   if("ACTION".equals(element.get("category")))runtime.reviews().review(actor(),meeting,UUID.randomUUID().toString(),revision(),"ELEMENT",(String)element.get("id"),"ACTION_FIELDS",null,"CONFIRMED",null,null,UUID.randomUUID().toString());
+  }
+  var readiness=runtime.queries().readiness(actor(),meeting);
+  assertTrue((Boolean)readiness.get("businessReady"));assertEquals(5,((List<?>)readiness.get("requiredReviewIds")).size());
+  Files.writeString(receipt.resolve("task11-final-precondition.json"),json.writeValueAsString(Map.of("meeting",meeting,"revision",revision(),"readiness",readiness,"actualGenerationTask",candidate.get("task_id"))));
+  knowledge=knowledgeFixture();
+  restartForFinal();
+  var response=call("POST","/meetings/"+meeting+"/final-confirmations",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"optionalReviewIds",List.of())),"task11-actual-final");
+  Files.writeString(receipt.resolve("task11-final-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));
+  assertEquals(201,response.statusCode(),response.body());
+  String confirmation=body(response).get("resultId").asText();var frozen=call("GET","/meetings/"+meeting+"/final-confirmations/"+confirmation,"",null);assertEquals(200,frozen.statusCode(),frozen.body());assertTrue(body(frozen).get("provenanceVerified").asBoolean());assertEquals(5,body(frozen).get("reviewIds").size());assertEquals("READY_TO_PUBLISH",j.queryForObject("SELECT stage FROM meeting WHERE id=?",String.class,meeting));
+  Files.writeString(receipt.resolve("task11-final-frozen-readback.json"),frozen.body());
+  var submitted=call("POST","/meetings/"+meeting+"/publications",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"confirmationId",confirmation,"providerId","CONTROLLED_KNOWLEDGE_V1","libraryId","controlled-library","folderId","controlled-root")),"task11-publish");
+  Files.writeString(receipt.resolve("task11-publication-response.json"),json.writeValueAsString(Map.of("status",submitted.statusCode(),"body",submitted.body())));assertEquals(202,submitted.statusCode(),submitted.body());
+  runPublication(json.convertValue(body(submitted),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
+
+
+ }
+ Task11PublicationWaiter.State publicationState(String task,boolean done,boolean failed){var t=j.queryForMap("SELECT state FROM durable_task WHERE id=?",task);var e=j.queryForList("SELECT e.state execution_state,o.state operation_state FROM external_execution e JOIN external_operation o ON o.id=e.operation_id WHERE e.task_id=? ORDER BY e.execution_no DESC LIMIT 1",task);return new Task11PublicationWaiter.State((String)t.get("state"),e.isEmpty()?null:(String)e.getFirst().get("operation_state"),e.isEmpty()?null:(String)e.getFirst().get("execution_state"),done,failed);}
+ void runPublication(Map<String,Object> accepted)throws Exception {
+  String task=(String)accepted.get("taskId");var queue=app.getBean(JdbcTaskQueue.class);var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,Map.of(),(t,m,d)->{throw new IllegalStateException("No publication model reservation");});var service=ama.api.Task11RuntimeProbe.publication(app);var handler=new ama.worker.KnowledgePublicationHandler(service,operations,permits);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var done=new java.util.concurrent.atomic.AtomicBoolean();
+  Files.writeString(receipt.resolve("knowledge-before-first-post.json"),json.writeValueAsString(Map.of("task",task,"publication",accepted.get("publicationId"),"confirmation",accepted.get("confirmationId"),"requester",owner,"port",knowledgeClientPort(),"providerPort",knowledge.port,"store",knowledge.store.toString(),"clientIdentities",knowledge.applicationIdentities.toString(),"trafficBefore",service.traffic())));
+  ama.worker.WorkerLoop.ExternalHandler guarded=execution->{try{handler.handle(execution);}catch(Throwable e){failure.set(e);var safe=new ArrayList<String>();for(Throwable x=e;x!=null;x=x.getCause()){safe.add(x.getClass().getName());if(x instanceof ama.contract.MinutesContracts.Failure f)safe.add(f.code().name());for(var frame:x.getStackTrace())safe.add(frame.toString());}Files.writeString(receipt.resolve("safe-publication-worker-failure.json"),json.writeValueAsString(safe));throw e;}finally{done.set(true);}};
+  var loop=ama.worker.WorkerLoop.external(queue,operations,permits,Map.of("KNOWLEDGE_PUBLISH",1),Map.of("KNOWLEDGE_PUBLISH_V1",guarded),Map.of(),"TASK11_KNOWLEDGE");Task11PublicationWaiter.Decision decision=Task11PublicationWaiter.Decision.WAIT;Task11PublicationWaiter.State before=null,after=null;boolean workerClosed=false;long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
+  try{loop.pollOnce("KNOWLEDGE_PUBLISH");while(decision==Task11PublicationWaiter.Decision.WAIT){before=publicationState(task,done.get(),failure.get()!=null);decision=Task11PublicationWaiter.decide(before,System.nanoTime()>=end);if(decision==Task11PublicationWaiter.Decision.WAIT)Thread.sleep(50);}Files.writeString(receipt.resolve("knowledge-before-worker-close.json"),json.writeValueAsString(Map.of("decision",decision.name(),"state",before,"traffic",service.traffic())));}
+  finally{try{loop.close();workerClosed=true;}finally{try{after=publicationState(task,done.get(),failure.get()!=null);long other=j.queryForObject("SELECT COUNT(*) FROM external_execution e JOIN durable_task t ON t.id=e.task_id WHERE t.task_type='KNOWLEDGE_PUBLISH_V1' AND e.state IN ('MARKED','UNKNOWN')",Long.class);preserveKnowledge=before==null||Task11PublicationWaiter.retainProvider(before,after,workerClosed,other>0);Files.writeString(receipt.resolve("knowledge-after-worker-close.json"),json.writeValueAsString(Map.of("workerClosed",workerClosed,"state",after,"retainProvider",preserveKnowledge,"unresolvedAcrossFixture",other,"traffic",service.traffic())));Files.writeString(receipt.resolve("knowledge-durable-state.json"),json.writeValueAsString(Map.of("task",j.queryForList("SELECT id,state,last_error FROM durable_task WHERE id=?",task),"submission",j.queryForList("SELECT task_id,publication_id,submission_no,confirmation_id,provider_id,local_state FROM publication_submission WHERE task_id=?",task),"executions",j.queryForList("SELECT id,operation_id,execution_no,state FROM external_execution WHERE task_id=?",task))));}catch(Throwable diagnostic){preserveKnowledge=true;throw diagnostic;}}}
+  assertEquals(expectedPublicationDecision(),decision,failure.get()==null?"See captured complete owner states":failure.get().getClass().getName());assertNotNull(after);assertTrue(after.both(expectedPublicationDecision()==Task11PublicationWaiter.Decision.SUCCESS?"SUCCEEDED":expectedPublicationDecision()==Task11PublicationWaiter.Decision.TERMINAL_FAILURE?"FAILED":"UNKNOWN"));
+ }
+ Task11PublicationWaiter.Decision expectedPublicationDecision(){return Task11PublicationWaiter.Decision.SUCCESS;}
+ Task11KnowledgeFixture knowledgeFixture()throws Exception{return new Task11KnowledgeFixture(receipt,owner);}
+ int knowledgeClientPort(){return knowledge.port;}
+ void minutesBounds(Map<String,Object> values){String[] names={"utf8","scalars","utf16","copy-bytes","copy-utf16","response-bytes","revision-depth","revision-nodes","tasks"};long[] bounds={16777216,16777216,16777216,268435456,134217728,33554432,128,256,256};for(int n=0;n<names.length;n++)values.put("minutes.bounds."+names[n],bounds[n]);}
+ void configurePublicationRuntime(Map<String,Object> configured){}
+ void restartForFinal(){if(app!=null)app.close();var configured=new HashMap<>(bounds);minutesBounds(configured);configured.put("minutes.enabled",true);configured.put("transcript.enabled",true);configured.put("publication.enabled",true);configured.put("publication.mode","ISOLATED_LOOPBACK_TEST");configured.put("publication.client-identities",knowledge.applicationIdentities.toString());configured.put("publication.origin","http://127.0.0.1:"+knowledgeClientPort());configured.put("minutes.mode","CONTROLLED_INSTALLATION");configured.put("minutes.archive-root",archive.toString());configured.put("minutes.index-sha",pin);configured.put("minutes.prompt-version",prompt.version());configured.put("minutes.prompt-sha",prompt.sha256());configured.put("minutes.model-version",model.version());configured.put("minutes.model-sha",model.sha256());configurePublicationRuntime(configured);app=new org.springframework.boot.builder.SpringApplicationBuilder(ama.api.ApiApplication.class).properties(configured).initializers(context->{var c=(org.springframework.context.support.GenericApplicationContext)context;c.registerBean("task09Source",javax.sql.DataSource.class,()->source);c.registerBean("task09Backends",ama.storage.FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin="+origin,"--processing.enabled=true","--processing.archive-root="+archive,"--processing.index-sha="+pin,"--processing.config-version="+processing.version(),"--processing.config-sha="+processing.sha256());source=app.getBean(javax.sql.DataSource.class);j=new org.springframework.jdbc.core.JdbcTemplate(source);}
+ @Override void afterExternal(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations){}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11KnowledgeFixture.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11KnowledgeFixture.java
@@ -0,0 +1,25 @@
+package ama.storage;
+import java.nio.file.*;import java.net.*;import java.util.*;import java.time.*;import java.util.concurrent.TimeUnit;import com.fasterxml.jackson.databind.*;
+import static org.junit.jupiter.api.Assertions.*;
+/** Single pinned store/key run; subsequent starts reopen it, never recreate an absent initialized store. */
+final class Task11KnowledgeFixture implements AutoCloseable {
+ final Path providerScript=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"tools/providers/controlled_knowledge.py");
+ final ObjectMapper json=new ObjectMapper();final Path root,config,store,ready,applicationIdentities;final int port;final Process process;ProcessHandle retained;final Path evidence;
+ long pid(){return process==null?retained.pid():process.pid();}
+ Task11KnowledgeFixture(Path evidence,String actor,Path retainedEvidence)throws Exception {this.evidence=evidence;root=Path.of(System.getenv("AMA_TASK11_RUN_ROOT"),"knowledge-fixture-20261025");applicationIdentities=root.resolve("application/publicidentities.json");var record=json.readTree(Files.readAllBytes(retainedEvidence));port=record.get("port").asInt();store=Path.of(record.get("store").asText());assertEquals(root.resolve("runtime/knowledge.sqlite"),store);retained=ProcessHandle.of(record.get("pid").asLong()).orElseThrow();assertTrue(retained.isAlive());String command=retained.info().commandLine().orElseThrow();assertTrue(command.contains(providerScript.toString()));String marker=" --config ";assertTrue(command.contains(marker));config=Path.of(command.substring(command.indexOf(marker)+marker.length()).strip());var cfg=json.readTree(Files.readAllBytes(config));assertEquals(store.toString(),cfg.get("store").asText());assertEquals(port,cfg.get("port").asInt());ready=Path.of(cfg.get("readyFile").asText());var actual=json.readTree(Files.readAllBytes(ready));assertEquals(retained.pid(),actual.get("pid").asLong());assertEquals(retained.pid(),actual.get("pgid").asLong());process=null;Files.writeString(evidence.resolve("knowledge-retained-provider-attached.json"),json.writeValueAsString(Map.of("pid",retained.pid(),"pgid",actual.get("pgid").asLong(),"port",port,"store",store.toString(),"sourceEvidence",retainedEvidence.toString(),"lookupOnly",true)));}
+
+ Task11KnowledgeFixture(Path evidence,String actor)throws Exception {
+  this.evidence=evidence;root=Path.of(System.getenv("AMA_TASK11_RUN_ROOT"),"knowledge-fixture-20261025");applicationIdentities=root.resolve("application/publicidentities.json");var identities=json.readTree(Files.readAllBytes(root.resolve("provider/publicidentities.json")));var ids=new TreeMap<String,Object>();for(String k:List.of("providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId"))ids.put(k,identities.get(k).asText());var paths=identities.get("paths");
+  Path runtime=root.resolve("runtime");if(!Files.exists(runtime))Files.createDirectory(runtime,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));assertFalse(Files.isSymbolicLink(runtime));store=runtime.resolve("knowledge.sqlite");Path endpoint=runtime.resolve("endpoint.json");boolean initialize=!Files.exists(endpoint);
+  if(initialize){assertFalse(Files.exists(store));try(var socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}write(endpoint,json.writeValueAsString(Map.of("port",port,"store",store.toString(),"identities",ids)));}else{var prior=json.readTree(Files.readAllBytes(endpoint));port=prior.get("port").asInt();assertEquals(store.toString(),prior.get("store").asText());assertEquals(ids,json.convertValue(prior.get("identities"),Map.class));if(!Files.isRegularFile(store)){Path ackPath=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),".superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-knowledge-first-initialization-continuation-ack-20261025.json");byte[] ackBytes=Files.readAllBytes(ackPath);String ackHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(ackBytes));assertEquals("bbf2586fa40d0451de0cfeb194625442429dd2c0f3ef14caea23f15cefaaf6b6",ackHash);var ack=json.readTree(ackBytes);assertEquals(port,ack.get("port").asInt());assertEquals(store.toString(),ack.get("store").asText());assertEquals(endpoint.toString(),ack.get("endpoint").asText());assertEquals(ids,json.convertValue(ack.get("identities"),Map.class));assertEquals(ack.get("endpointSha256").asText(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(endpoint))));assertFalse(Files.exists(Path.of(store+"-wal")));assertFalse(Files.exists(Path.of(store+"-shm")));Path consume=runtime.resolve("first-initialization-continuation-consumed.json");assertEquals(consume.toString(),ack.get("requiredConsumePath").asText());write(consume,json.writeValueAsString(Map.of("ackSha256",ackHash,"endpoint",endpoint.toString(),"store",store.toString(),"identities",ids,"port",port)));initialize=true;}}
+  String instance=UUID.randomUUID().toString();ready=runtime.resolve("ready-"+instance+".json");config=runtime.resolve("provider-"+instance+".json");var cfg=new TreeMap<String,Object>();cfg.put("identities",ids);cfg.put("bearerVerifier",paths.get("serviceBearerVerifier").asText());cfg.put("delegationPublic",paths.get("delegationPublicKey").asText());cfg.put("assertionPrivate",paths.get("assertionPrivateKey").asText());cfg.put("store",store.toString());cfg.put("readyFile",ready.toString());cfg.put("port",port);cfg.put("bind","127.0.0.1");cfg.put("mode","ISOLATED_LOOPBACK_TEST");write(config,json.writeValueAsString(cfg));
+  var custody=new TreeMap<String,Object>();custody.put("providerSource",Map.of("path",providerScript.toString(),"bytes",Files.size(providerScript),"sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(providerScript)))));custody.putAll(Map.of("identities",ids,"port",port,"store",store.toString(),"clientIdentities",applicationIdentities.toString(),"config",config.toString(),"initialization",initialize,"maxAddressSpaceBytes",67108864,"cpuAffinityCount",2,"lifecycle","owned setsid process; exact same store/key files on restart; retain process when unresolved execution exists"));Files.writeString(evidence.resolve("knowledge-before-start.json"),json.writeValueAsString(custody));
+  var command=new ArrayList<String>(List.of("setsid","/usr/bin/python3",providerScript.toString(),"--config",config.toString()));if(initialize)command.add("--initialize");process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(evidence.resolve("knowledge-provider.log").toFile()).start();try {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!Files.exists(ready)&&process.isAlive()&&System.nanoTime()<deadline)Thread.sleep(50);assertTrue(process.isAlive(),"Controlled provider startup failed; inspect bounded log");assertTrue(Files.exists(ready));var actual=json.readTree(Files.readAllBytes(ready));assertEquals(port,actual.get("port").asInt());assertEquals(store.toString(),actual.get("store").asText());assertEquals(process.pid(),actual.get("pid").asLong());assertEquals(process.pid(),actual.get("pgid").asLong());
+  Files.writeString(evidence.resolve("knowledge-started.json"),json.writeValueAsString(Map.of("pid",process.pid(),"pgid",actual.get("pgid").asLong(),"port",port,"store",store.toString(),"identities",ids,"startedUtc",Instant.now().toString())));
+  grant(actor);
+  } catch(Throwable failed){if(process.isAlive()){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}Files.writeString(evidence.resolve("knowledge-start-failed.json"),json.writeValueAsString(Map.of("pid",process.pid(),"alive",process.isAlive(),"storeRetained",Files.isRegularFile(store),"beforeAnyBusinessPost",true)));throw failed;}
+ }
+ void grant(String actor)throws Exception {var grant=new ProcessBuilder("/usr/bin/python3",providerScript.toString(),"--config",config.toString(),"--grant-actor",actor,"--grant-read","--grant-publish").redirectErrorStream(true).redirectOutput(evidence.resolve("knowledge-grant.log").toFile()).start();if(!grant.waitFor(10,TimeUnit.SECONDS)){grant.destroyForcibly();grant.waitFor(5,TimeUnit.SECONDS);throw new IllegalStateException("Controlled grant timed out");}assertEquals(0,grant.exitValue(),"Actual controlled actor grant required");}
+ private static void write(Path path,String text)throws Exception {try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")))){channel.write(java.nio.ByteBuffer.wrap(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}}
+ public void close()throws Exception {if(process==null){assertTrue(retained.isAlive());return;}if(process.isAlive()){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}}Files.writeString(evidence.resolve("knowledge-closed.json"),json.writeValueAsString(Map.of("pid",process.pid(),"alive",process.isAlive(),"port",port,"store",store.toString(),"storeRetained",Files.isRegularFile(store),"closedUtc",Instant.now().toString())));}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11LocalMaintenanceIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11LocalMaintenanceIT.java
@@ -0,0 +1,10 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+/** Real existing fixture scan: marked/unknown and already sealed facts cannot be locally rewritten. */
+class Task11LocalMaintenanceIT extends Task11LocalRecoveryResumeIT {
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  var local=ama.api.Task11RuntimeProbe.localPublication(app);assertNotNull(local);var before=inventory();var outcome=local.maintainLocalFailures();assertTrue(outcome.scanned()>0&&outcome.scanned()<=64);assertTrue(outcome.attempted()>0&&outcome.attempted()<=16);assertEquals(0,outcome.recovered());assertEquals(before,inventory());var throttled=local.maintainLocalFailures();assertEquals(0,throttled.scanned());assertEquals(0,throttled.attempted());assertEquals(before,inventory());
+  Files.writeString(receipt.resolve("actual-local-maintenance.json"),json.writeValueAsString(Map.of("first",outcome,"sameCadence",throttled,"before",before,"after",inventory(),"qualification","fresh DB-only scan of existing Task11 fixture; marked remote failure denied local closure, UNKNOWN skipped, sealed excluded; no automatic abandonment/provider traffic; positive crash-repair and >64 fairness matrix pending")));
+ }
+ List<Map<String,Object>> inventory(){return j.queryForList("SELECT t.id,t.state,t.attempt,t.lease_owner,CAST(t.expires_at AS CHAR) expires_at,s.local_state,s.local_reason,CAST(s.local_decided_at AS CHAR) local_decided_at,p.state publication_state,p.revision publication_revision,m.stage,m.revision,m.cancel_epoch,(SELECT COUNT(*) FROM task_attempt a WHERE a.task_id=t.id AND a.ended_at IS NOT NULL) ended_attempts FROM publication_submission s JOIN durable_task t ON t.id=s.task_id JOIN publication p ON p.id=s.publication_id JOIN meeting m ON m.id=p.meeting_id ORDER BY s.task_id");}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoveryIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoveryIT.java
@@ -0,0 +1,13 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import java.time.*;import static org.junit.jupiter.api.Assertions.*;
+/** Actual live lease denial then natural expiry recovery, with provider factory and archive unavailable. */
+class Task11LocalRecoveryIT extends Task11LocalTerminalIT {
+ boolean unavailable;
+ @Override void configurePublicationRuntime(Map<String,Object> configured){if(unavailable){configured.remove("publication.client-identities");configured.remove("publication.origin");configured.remove("publication.mode");configured.put("minutes.archive-root",archive.resolve("absent-local-recovery-source").toString());}}
+ @Override void runPublication(Map<String,Object> accepted)throws Exception {
+  String task=(String)accepted.get("taskId"),publication=(String)accepted.get("publicationId");var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=queue.claim("KNOWLEDGE_PUBLISH","TASK11_EXPLICIT_LOCAL").orElseThrow();assertEquals(task,lease.taskId());long expected=revision();String path="/meetings/"+meeting+"/publications/"+publication+"/local-recoveries",request=json.writeValueAsString(Map.of("taskId",task,"expectedRevisionToken",Long.toString(expected),"expectedPublicationRevisionToken","1"));var before=state(task,publication);var live=call("POST",path,request,"task11-local-live");assertEquals(409,live.statusCode(),live.body());assertEquals(before,state(task,publication));
+  unavailable=true;restartForFinal();assertNull(ama.api.Task11RuntimeProbe.publication(app));assertNotNull(ama.api.Task11RuntimeProbe.localPublication(app));long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25);while(j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE id=? AND expires_at>UTC_TIMESTAMP(6)",Integer.class,task)>0&&System.nanoTime()<deadline)Thread.sleep(50);assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE id=? AND expires_at>UTC_TIMESTAMP(6)",Integer.class,task));
+  var response=call("POST",path,request,"task11-local-expired");Files.writeString(receipt.resolve("local-recovery-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));assertEquals(200,response.statusCode(),response.body());assertEquals("NEVER_MARKED",body(response).get("decision").get("local_state").asText());assertEquals("EXPIRED_UNMARKED_ABANDONED",body(response).get("decision").get("local_reason").asText());assertTrue(body(response).get("decision").get("local_execution_id").isNull());var completed=state(task,publication);var replay=call("POST",path,request,"task11-local-expired");assertEquals(200,replay.statusCode());assertTrue(body(replay).get("replayed").asBoolean());assertEquals(completed,state(task,publication));var other=call("POST",path,request,"task11-local-sealed-new-key");assertEquals(200,other.statusCode(),other.body());assertEquals(completed,state(task,publication));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_attempt WHERE publication_id=?",Integer.class,publication));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NOT NULL",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NULL",Integer.class,task));assertEquals(expected,revision());
+  Files.writeString(receipt.resolve("explicit-local-recovery.json"),json.writeValueAsString(Map.of("accepted",accepted,"liveDenied",live.statusCode(),"before",before,"after",completed,"providerFactoryAbsent",true,"archiveUnavailable",true,"sameKeyReplay",true,"newKeySealedReplay",true,"qualification","real live RUNNING owner denied; natural DB lease expiry; real exact attempt ended once; no fabricated RECONCILING state, remote proof or execution")));
+ }
+}

## backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoveryResumeIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoveryResumeIT.java
@@ -0,0 +1,19 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+/** Continues only the original accepted task from the failed live-lease HTTP window. */
+class Task11LocalRecoveryResumeIT extends Task11PublicationResumeIT {
+ @Override String previousWindow(){return "publication-local-recovery-02";}
+ @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
+ @Override void configurePublicationRuntime(Map<String,Object> configured){configured.remove("publication.client-identities");configured.remove("publication.origin");configured.remove("publication.mode");configured.put("minutes.archive-root",archive.resolve("absent-local-recovery-source").toString());}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  var accepted=json.readTree(json.readTree(Files.readAllBytes(previousPipeline().resolve("task11-publication-response.json"))).get("body").asText());String task=accepted.get("taskId").asText(),publication=accepted.get("publicationId").asText();
+  token=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR FROM enterprise_identity WHERE enterprise_id=?",hash(token),csrf,owner);
+  assertNull(ama.api.Task11RuntimeProbe.publication(app));assertNotNull(ama.api.Task11RuntimeProbe.localPublication(app));
+  long expected=revision();String path="/meetings/"+meeting+"/publications/"+publication+"/local-recoveries",request=json.writeValueAsString(Map.of("taskId",task,"expectedRevisionToken",Long.toString(expected),"expectedPublicationRevisionToken","1"));var before=state(task,publication);assertEquals("RUNNING",before.getFirst().get("state"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE id=? AND expires_at>UTC_TIMESTAMP(6)",Integer.class,task));
+  Files.writeString(receipt.resolve("original-expired-before.json"),json.writeValueAsString(Map.of("accepted",accepted,"before",before,"sourceWindow",previousWindow(),"qualification","original live request failed HTTP mapping; task naturally expired, no new claim or task")));
+  var response=call("POST",path,request,"task11-local-expired");Files.writeString(receipt.resolve("local-recovery-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));assertEquals(200,response.statusCode(),response.body());assertEquals("NEVER_MARKED",body(response).get("decision").get("local_state").asText());assertEquals("EXPIRED_UNMARKED_ABANDONED",body(response).get("decision").get("local_reason").asText());assertTrue(body(response).get("decision").get("local_execution_id").isNull());var completed=state(task,publication);var replay=call("POST",path,request,"task11-local-expired");assertEquals(200,replay.statusCode());assertTrue(body(replay).get("replayed").asBoolean());assertEquals(completed,state(task,publication));var other=call("POST",path,request,"task11-local-sealed-new-key");assertEquals(200,other.statusCode(),other.body());assertEquals(completed,state(task,publication));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_attempt WHERE publication_id=?",Integer.class,publication));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NOT NULL",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NULL",Integer.class,task));assertEquals(expected,revision());
+
+  Files.writeString(receipt.resolve("explicit-local-recovery.json"),json.writeValueAsString(Map.of("accepted",accepted,"before",before,"after",completed,"providerFactoryAbsent",true,"archiveUnavailable",true,"sameKeyReplay",true,"newKeySealedReplay",true,"qualification","original expired RUNNING task; exact real attempt ended once; zero execution and provider POST; no renewed live-lease qualification")));
+ }
+ List<Map<String,Object>> state(String task,String publication){return j.queryForList("SELECT t.id,t.state,t.attempt,t.lease_owner,CAST(t.expires_at AS CHAR) expires_at,t.last_error,s.local_state,s.local_reason,s.local_decided_by,s.local_attempt_no,s.local_execution_id,CAST(s.local_decided_at AS CHAR) local_decided_at,p.state publication_state,p.revision publication_revision,m.revision,m.cancel_epoch,m.stage,m.final_confirmation_id,m.ever_published,(SELECT COUNT(*) FROM task_attempt a WHERE a.task_id=t.id AND a.ended_at IS NOT NULL) ended_attempts FROM durable_task t JOIN publication_submission s ON s.task_id=t.id JOIN publication p ON p.id=s.publication_id JOIN meeting m ON m.id=p.meeting_id WHERE t.id=? AND p.id=?",task,publication);}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoverySecurityIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11LocalRecoverySecurityIT.java
@@ -0,0 +1,14 @@
+package ama.storage;
+import java.nio.file.*;import java.net.*;import java.net.http.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+class Task11LocalRecoverySecurityIT extends Task11LocalRecoveryResumeIT {
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  var accepted=json.readTree(json.readTree(Files.readAllBytes(previousPipeline().resolve("task11-publication-response.json"))).get("body").asText());String task=accepted.get("taskId").asText(),publication=accepted.get("publicationId").asText();var before=state(task,publication);assertEquals("NEVER_MARKED",before.getFirst().get("local_state"));String path="/meetings/"+meeting+"/publications/"+publication+"/local-recoveries",payload=json.writeValueAsString(Map.of("taskId",task,"expectedRevisionToken","13","expectedPublicationRevisionToken","1"));
+  var anonymous=client().send(HttpRequest.newBuilder(URI.create(apiUrl()+path)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(401,anonymous.statusCode());
+  token=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR FROM enterprise_identity WHERE enterprise_id=?",hash(token),csrf,owner);
+  var missingCsrf=client().send(authenticated(HttpRequest.newBuilder(URI.create(apiUrl()+path))).header("Origin",origin).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,missingCsrf.statusCode());
+  var wrongOrigin=client().send(authenticated(HttpRequest.newBuilder(URI.create(apiUrl()+path))).header("Origin","https://not-allowed.invalid").header("X-CSRF-Token",csrf).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,wrongOrigin.statusCode());
+  String ownerToken=token;token=identity("task11-local-outsider-"+UUID.randomUUID());var outsider=call("POST",path,payload,"task11-local-outsider");assertEquals(403,outsider.statusCode(),outsider.body());assertEquals("MEETING_FORBIDDEN",body(outsider).get("error").get("code").asText());token=ownerToken;
+  var ownerAck=call("POST",path,payload,"task11-local-security-owner");assertEquals(200,ownerAck.statusCode(),ownerAck.body());assertEquals(before,state(task,publication));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));
+  Files.writeString(receipt.resolve("actual-local-security.json"),json.writeValueAsString(Map.of("task",task,"publication",publication,"statuses",List.of(anonymous.statusCode(),missingCsrf.statusCode(),wrongOrigin.statusCode(),outsider.statusCode(),ownerAck.statusCode()),"before",before,"after",state(task,publication),"qualification","actual HTTP unauthenticated, missing CSRF, wrong Origin, authenticated nonowner, owner sealed acknowledgement; no business task/claim/provider POST")));
+ }
+}

## backend/integration-tests/src/test/java/ama/storage/Task11LocalTerminalIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11LocalTerminalIT.java
@@ -0,0 +1,14 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import ama.contract.TaskContracts.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
+/** Real no-marker ordinary failure: private context boundaries, whole rollback, then atomic local seal. */
+class Task11LocalTerminalIT extends Task11FinalPublicationIT {
+ @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {Path window=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"artifacts/phase-b/b-remaining/task-11/publication-zero-effect-01");Path retained;try(var paths=Files.list(window)){retained=paths.filter(p->p.getFileName().toString().startsWith("pipeline-")).findFirst().orElseThrow().resolve("knowledge-retained-unresolved.json");}var fixture=new Task11KnowledgeFixture(receipt,owner,retained);fixture.grant(owner);return fixture;}
+ static final class ExpectedRollback extends RuntimeException {}
+ @Override void runPublication(Map<String,Object> accepted)throws Exception {
+  String task=(String)accepted.get("taskId"),publication=(String)accepted.get("publicationId");var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=queue.claim("KNOWLEDGE_PUBLISH","TASK11_LOCAL_TERMINAL").orElseThrow();assertEquals(task,lease.taskId());var service=ama.api.Task11RuntimeProbe.publication(app);var traffic=service.traffic();var authority=ama.api.Task09RuntimeProbe.components(app).authority();assertEquals(3,ama.transcript.Task11LocalContextProbe.verify(authority,lease.spec()));var before=state(task,publication);var failure=new TaskResult(Outcome.FAILED,null,"PREPARATION_UNAVAILABLE");
+  assertThrows(ExpectedRollback.class,()->app.getBean(ama.db.TransactionRunner.class).required(()->{assertTrue(queue.complete(lease,failure));assertEquals("NEVER_MARKED",j.queryForObject("SELECT local_state FROM publication_submission WHERE task_id=?",String.class,task));throw new ExpectedRollback();}));assertEquals(before,state(task,publication));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));
+  assertTrue(queue.complete(lease,failure));assertFalse(queue.complete(lease,failure));var after=state(task,publication);assertEquals("FAILED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertEquals("FAILED",j.queryForObject("SELECT state FROM publication WHERE id=?",String.class,publication));assertEquals("PUBLISH_FAILED",j.queryForObject("SELECT stage FROM meeting WHERE id=?",String.class,meeting));var decision=j.queryForMap("SELECT local_state,local_reason,local_decided_by,local_attempt_no,local_execution_id,CAST(local_decided_at AS CHAR) local_decided_at FROM publication_submission WHERE task_id=?",task);assertEquals("NEVER_MARKED",decision.get("local_state"));assertEquals("TERMINAL_UNMARKED_FAILURE",decision.get("local_reason"));assertEquals(owner,decision.get("local_decided_by"));assertEquals(1,((Number)decision.get("local_attempt_no")).longValue());assertNull(decision.get("local_execution_id"));assertNotNull(decision.get("local_decided_at"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_attempt WHERE publication_id=?",Integer.class,publication));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_document_mapping WHERE publication_id=?",Integer.class,publication));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND outcome='FAILED' AND ended_at IS NOT NULL",Integer.class,task));assertEquals(traffic,service.traffic());
+  Files.writeString(receipt.resolve("actual-local-never-marked.json"),json.writeValueAsString(Map.of("accepted",accepted,"before",before,"after",after,"decision",decision,"traffic",traffic,"privateContextNegativeChecks",3,"rollbackVerified",true,"qualification","genuine queue claim/ordinary complete; no operation/execution/provider publication POST/proof fabricated; whole local decision rollback and subsequent one-use completion")));
+ }
+ List<Map<String,Object>> state(String task,String publication){return j.queryForList("SELECT t.id,t.state,t.attempt,t.lease_owner,CAST(t.expires_at AS CHAR) expires_at,t.last_error,s.local_state,s.local_reason,s.local_decided_by,s.local_attempt_no,s.local_execution_id,CAST(s.local_decided_at AS CHAR) local_decided_at,p.state publication_state,p.revision publication_revision,m.revision,m.cancel_epoch,m.stage,m.final_confirmation_id,m.ever_published,(SELECT COUNT(*) FROM task_attempt a WHERE a.task_id=t.id AND a.ended_at IS NOT NULL) ended_attempts FROM durable_task t JOIN publication_submission s ON s.task_id=t.id JOIN publication p ON p.id=s.publication_id JOIN meeting m ON m.id=p.meeting_id WHERE t.id=? AND p.id=?",task,publication);}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11LostResponseIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11LostResponseIT.java
@@ -0,0 +1,25 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
+/** Real remote commit before response loss, application restart, fair-claim production GET recovery. */
+class Task11LostResponseIT extends Task11FinalPublicationIT {
+ Task11ResponseLossFixture loss;
+ @AfterEach void closeResponseLoss()throws Exception {if(loss!=null)loss.close();}
+ @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {
+  Path previous=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"artifacts/phase-b/b-remaining/task-11/publication-main-03");Path retained;try(var paths=Files.list(previous)){retained=paths.filter(p->p.getFileName().toString().startsWith("pipeline-")).findFirst().orElseThrow().resolve("knowledge-retained-unresolved.json");}
+  var fixture=new Task11KnowledgeFixture(receipt,owner,retained);fixture.grant(owner);loss=new Task11ResponseLossFixture(fixture,receipt);return fixture;
+ }
+ @Override int knowledgeClientPort(){return loss.port;}
+ @Override Task11PublicationWaiter.Decision expectedPublicationDecision(){return Task11PublicationWaiter.Decision.UNRESOLVED;}
+ @Override void runPublication(Map<String,Object> accepted)throws Exception {
+  super.runPublication(accepted);String task=(String)accepted.get("taskId");var remote=loss.read();assertTrue(remote.get("withheld").asBoolean());assertEquals(1,remote.get("postPublication").asInt());assertEquals(0,remote.get("failures").asInt());String execution=j.queryForObject("SELECT id FROM external_execution WHERE task_id=?",String.class,task),operation=j.queryForObject("SELECT operation_id FROM external_execution WHERE id=?",String.class,execution);assertEquals(execution,remote.get("receipt").get("executionId").asText());assertEquals(accepted.get("publicationId"),remote.get("receipt").get("publicationId").asText());assertEquals("UNKNOWN",j.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,execution));
+  Files.writeString(receipt.resolve("response-lost-local-unknown.json"),json.writeValueAsString(Map.of("task",task,"operation",operation,"execution",execution,"providerReceipt",remote,"local",publicationState(task,true,false),"traffic",ama.api.Task11RuntimeProbe.publication(app).traffic(),"qualification","actual provider returned committed SUCCESS before relay withheld response; local observation is UNKNOWN")));
+  restartForFinal();var service=ama.api.Task11RuntimeProbe.publication(app);var trafficBefore=service.traffic();var queue=app.getBean(ama.task.JdbcTaskQueue.class);var operations=new ama.task.JdbcExternalOperations(queue);var permits=new ama.task.JdbcModelPermitPool(operations,Map.of(),(t,m,d)->{throw new IllegalStateException("No publication model reservation");});var handler=new ama.worker.KnowledgeReconciliationHandler(service,operations,permits);var done=new AtomicBoolean();var failure=new AtomicReference<Throwable>();var skipped=new CopyOnWriteArrayList<Map<String,Object>>();
+  var loop=ama.worker.ReconciliationLoop.prepared(operations,permits,1,session->{if(!session.token().operationId().equals(operation)){var token=session.token();skipped.add(Map.of("operation",token.operationId(),"execution",token.executionId(),"owner",token.owner(),"epoch",token.reconcileEpoch(),"expiresAt",token.expiresAt().toString(),"action","genuine session close only; no GET/observe/send"));((ama.worker.ExternalExecutionSession)session).close();return;}try{handler.reconcile(session);}catch(Throwable e){failure.set(e);throw e;}finally{done.set(true);}},"TASK11_LOST_RESPONSE");
+  Task11PublicationWaiter.Decision decision=Task11PublicationWaiter.Decision.WAIT;Task11PublicationWaiter.State before=null,after=null;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
+  try{loop.start();while(decision==Task11PublicationWaiter.Decision.WAIT){before=publicationState(task,done.get(),failure.get()!=null);decision=Task11PublicationWaiter.decide(before,System.nanoTime()>=end);if(decision==Task11PublicationWaiter.Decision.WAIT)Thread.sleep(50);}Files.writeString(receipt.resolve("recovery-before-close.json"),json.writeValueAsString(Map.of("state",before,"decision",decision.name(),"traffic",service.traffic(),"skippedFairClaims",skipped)));}
+  finally{loop.close();after=publicationState(task,done.get(),failure.get()!=null);Files.writeString(receipt.resolve("recovery-after-close.json"),json.writeValueAsString(Map.of("state",after,"traffic",service.traffic(),"skippedFairClaims",skipped)));}
+  if(failure.get()!=null){var frames=new ArrayList<String>();for(Throwable f=failure.get();f!=null;f=f.getCause()){frames.add(f.getClass().getName());if(f instanceof ama.contract.MinutesContracts.Failure v)frames.add(v.code().name());for(var frame:f.getStackTrace())frames.add(frame.toString());}Files.writeString(receipt.resolve("recovery-safe-failure.json"),json.writeValueAsString(frames));}
+  assertEquals(Task11PublicationWaiter.Decision.SUCCESS,decision);assertTrue(after.both("SUCCEEDED"));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));assertEquals(trafficBefore.get("postWriteAttempts"),service.traffic().get("postWriteAttempts"));assertEquals(trafficBefore.get("getWriteAttempts")+1,service.traffic().get("getWriteAttempts"));assertEquals(1,loss.read().get("postPublication").asInt());assertEquals(1,loss.read().get("get").asInt());assertEquals(remote.get("receipt").get("documentId").asText(),j.queryForObject("SELECT external_document_id FROM external_document_mapping WHERE publication_id=?",String.class,accepted.get("publicationId")));loss.settled=true;
+  Files.writeString(receipt.resolve("lost-response-recovered.json"),json.writeValueAsString(Map.of("publication",accepted.get("publicationId"),"task",task,"operation",operation,"execution",execution,"sameExecutionCount",1,"afterApplicationRestart",true,"traffic",service.traffic(),"relay",loss.read(),"skippedFairClaims",skipped)));
+ }
+}

## backend/integration-tests/src/test/java/ama/storage/Task11PublicationOriginalLookupIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11PublicationOriginalLookupIT.java
@@ -0,0 +1,9 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
+class Task11PublicationOriginalLookupIT extends Task11PublicationResumeIT {
+ @Override Task11KnowledgeFixture attachKnowledge()throws Exception {return new Task11KnowledgeFixture(receipt,owner,Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"artifacts/phase-b/b-remaining/task-11/publication-original-main-02/original-publication-resume/knowledge-retained-unresolved.json"));}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  String task="f4d7a0f7-5a78-45c6-918d-9cfa6d7d09dd",operation="4e24a34b-af73-4910-8c34-ccd3f8b086a1",execution="07327d2f-3b2f-4bc1-ba6d-f9f03246d08a";var queue=app.getBean(ama.task.JdbcTaskQueue.class);var operations=new ama.task.JdbcExternalOperations(queue);var claim=operations.claimReconciliation("TASK11_ORIGINAL_LOOKUP").orElseThrow();assertEquals(operation,claim.operationId());assertEquals(execution,claim.executionId());
+  try{var service=ama.api.Task11RuntimeProbe.publication(app);var prepared=service.prepareLookup(operations.recordedLookup(claim),claim);Files.writeString(receipt.resolve("original-before-get.json"),json.writeValueAsString(Map.of("task",task,"operation",operation,"execution",execution,"port",knowledge.port,"store",knowledge.store.toString(),"noPost",true)));var observed=service.lookup(prepared,NEVER);var result=operations.observePrepared(claim,observed);Files.writeString(receipt.resolve("original-get-observed.json"),json.writeValueAsString(Map.of("observationResult",result.name(),"task",j.queryForList("SELECT id,state,last_error FROM durable_task WHERE id=?",task),"operation",j.queryForList("SELECT id,state,last_error FROM external_operation WHERE id=?",operation),"execution",j.queryForList("SELECT id,operation_id,execution_no,state,no_effect_basis,no_effect_evidence FROM external_execution WHERE id=?",execution))));assertEquals("UNKNOWN",j.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,execution));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));}finally{operations.releaseRecordedLookup(claim);}
+ }
+}

## backend/integration-tests/src/test/java/ama/storage/Task11PublicationResumeIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11PublicationResumeIT.java
@@ -0,0 +1,29 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import ama.config.*;import ama.config.file.*;import ama.contract.ConfigArchiveContracts.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
+/** Resume the exact main02 final/meeting. No generation, fixture bootstrap or new meeting. */
+class Task11PublicationResumeIT extends Task11FinalPublicationIT {
+ Process resumedNative;
+ @AfterEach void closeResumedNative()throws Exception {if(resumedNative!=null&&resumedNative.isAlive()){resumedNative.destroy();if(!resumedNative.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)){resumedNative.destroyForcibly();assertTrue(resumedNative.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}}}
+ @Override @BeforeEach void start()throws Exception {
+  source=createSource();j=new org.springframework.jdbc.core.JdbcTemplate(source);receipt=Files.createDirectory(Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW")).resolve("original-publication-resume"));
+  Path previous=previousPipeline();
+  var original=json.readTree(Files.readAllBytes(previous.resolve("resource.json")));var finalFacts=json.readTree(Files.readAllBytes(previous.resolve("task11-final-precondition.json")));var installation=json.readTree(Files.readAllBytes(previous.resolve("controlled-template-archive-installation.json")));
+  meeting=finalFacts.get("meeting").asText();owner=original.get("owner").asText();archive=Path.of(original.get("archive").asText());pin=installation.get("indexSha").asText();
+  var entries=snapshot().index().entries();processing=entries.stream().map(ConfigArchiveIndex.Entry::ref).filter(x->x.artifactType()==ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();prompt=entries.stream().map(ConfigArchiveIndex.Entry::ref).filter(x->x.artifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow();model=entries.stream().map(ConfigArchiveIndex.Entry::ref).filter(x->x.artifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow();
+  Path nativeRoot=Path.of(original.get("nativePath").asText());var properties=new Properties();try(var in=Files.newInputStream(nativeRoot.resolveSibling(nativeRoot.getFileName()+"-managed.properties"))){properties.load(in);}
+  Path nativeConfig=nativeRoot.resolveSibling(nativeRoot.getFileName()+"-managed.properties"),nativeLog=receipt.resolve("original-native-service.log");Files.writeString(receipt.resolve("original-native-before-restart.json"),json.writeValueAsString(Map.of("configuration",nativeConfig.toString(),"root",nativeRoot.toString(),"managedId",original.get("managedId").asText(),"port",original.get("nativePort").asInt(),"heapMiB",64,"processors",2,"sameOriginalRoot",true)));resumedNative=Task09NativeFixture.launch(nativeConfig,nativeLog);long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);while(resumedNative.isAlive()&&System.nanoTime()<end&&!Files.readString(nativeLog).contains("READY "))Thread.sleep(50);assertTrue(resumedNative.isAlive());assertTrue(Files.readString(nativeLog).contains("READY "));
+  String relative=Path.of(properties.getProperty("mountpoint")).relativize(nativeRoot).toString();if(relative.isEmpty())relative=".";
+  var binding=new ManagedBackendId.Local(properties.getProperty("serviceOrigin"),properties.getProperty("volumeId"),relative,properties.getProperty("filesystem"));assertEquals(original.get("managedId").asText(),binding.id());definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(binding.id(),"local",1,"WRITABLE",new LocalFileClient(java.net.URI.create(properties.getProperty("serviceOrigin")),Task09NativeFixture.tls("writer")),binding)),binding.id());
+  bounds=new HashMap<>();String[] names={"artifact-bytes","archive-total-bytes","artifacts","dependencies","graph-depth","json-tokens","json-depth","name-utf16","string-utf16","string-utf8","aggregate-scalars","manifest-bytes","batch-files","fact-rows","fact-edges","canonical-bytes","canonical-depth","canonical-nodes","canonical-string-utf8","canonical-scalars","index-bytes","index-entries","release-bytes","index-generations","total-source-bytes"};long[] values={65536,1048576,64,128,16,200000,32,255,65536,262144,1048576,65536,32,250000,500000,16777216,32,1000000,2097152,250000,4194304,4096,67108864,128,1048576};for(int n=0;n<names.length;n++)bounds.put("processing.bounds."+names[n],values[n]);
+  String[] saved={"utf8","scalars","utf16","copy-bytes","copy-utf16","response-bytes","revision-depth","revision-nodes","tasks"};long[] limits={16777216,16777216,16777216,67108864,67108864,33554432,128,256,64};for(int n=0;n<saved.length;n++)bounds.put("transcript.bounds."+saved[n],limits[n]);for(String k:List.of("correction.mode","correction.installation","candidates.installation","publication.installation"))bounds.put("transcript."+k,"DISABLED_NOT_INSTALLED");bounds.put("transcript.correction.installation-version","1");bounds.put("transcript.transitions.installation-version","1");
+  knowledge=attachKnowledge();restartForFinal();Files.writeString(receipt.resolve("original-context.json"),json.writeValueAsString(Map.of("meeting",meeting,"owner",owner,"archive",archive.toString(),"pin",pin,"previousWindow",previous.toString(),"noNewBusinessSetup",true)));
+ }
+ Path previousPipeline()throws Exception {Path window=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"artifacts/phase-b/b-remaining/task-11",previousWindow());try(var paths=Files.list(window)){return paths.filter(p->p.getFileName().toString().startsWith("pipeline-")).findFirst().orElseThrow();}}
+ String previousWindow(){return "publication-main-02";}
+ Task11KnowledgeFixture attachKnowledge()throws Exception {return new Task11KnowledgeFixture(receipt,owner);}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  String confirmation=j.queryForObject("SELECT final_confirmation_id FROM meeting WHERE id=?",String.class,meeting);var service=ama.api.Task11RuntimeProbe.publication(app);
+  try{var value=service.submit(actor(),meeting,"task11-publish",revision(),confirmation,"CONTROLLED_KNOWLEDGE_V1","controlled-library","controlled-root",UUID.randomUUID().toString());Files.writeString(receipt.resolve("actual-publication-accepted.json"),json.writeValueAsString(value));runPublication(value);}
+  catch(Throwable failed){var safe=new ArrayList<String>();for(Throwable e=failed;e!=null;e=e.getCause()){safe.add(e.getClass().getName());if(e instanceof ama.contract.MinutesContracts.Failure f)safe.add(f.code().name());for(var frame:e.getStackTrace())safe.add(frame.toString());}Files.writeString(receipt.resolve("safe-original-submission-failure.json"),json.writeValueAsString(safe));throw failed;}
+ }
+}

## backend/integration-tests/src/test/java/ama/storage/Task11PublicationWaiter.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11PublicationWaiter.java
@@ -0,0 +1,38 @@
+package ama.storage;
+import java.util.*;
+/** Pure fixture lifecycle decisions. No state here grants a send, retry or no-effect proof. */
+final class Task11PublicationWaiter {
+ enum Decision { WAIT, SUCCESS, TERMINAL_FAILURE, UNRESOLVED, LOCAL_STOP, TIMEOUT }
+ record State(String task,String operation,String execution,boolean handlerDone,boolean handlerFailed) {
+  boolean hasExternal(){return operation!=null||execution!=null;}
+  boolean both(String value){return value.equals(operation)&&value.equals(execution);}
+  boolean unresolved(){return hasExternal()&&!both("SUCCEEDED")&&!both("FAILED");}
+ }
+ static Decision decide(State s,boolean expired){
+  if(s.handlerDone()){
+   if(!s.handlerFailed()&&"SUCCEEDED".equals(s.task())&&s.both("SUCCEEDED"))return Decision.SUCCESS;
+   if(s.unresolved())return Decision.UNRESOLVED;
+   if(Set.of("FAILED","STALE","CANCELLED").contains(s.task())&&s.both("FAILED"))return Decision.TERMINAL_FAILURE;
+   if(s.handlerFailed()&&!s.hasExternal())return Decision.LOCAL_STOP;
+  }
+  return expired?Decision.TIMEOUT:Decision.WAIT;
+ }
+ /** Capture before closing worker, then fresh capture after close; unknown close outcome retains provider. */
+ static boolean retainProvider(State before,State after,boolean workerClosed,boolean anyOtherUnresolved){return !workerClosed||after==null||before.unresolved()||after.unresolved()||anyOtherUnresolved;}
+ static void expect(Decision wanted,State actual,boolean expired){if(decide(actual,expired)!=wanted)throw new AssertionError(wanted+" "+actual);}
+ public static void main(String[] args){int checks=0;
+  for(String task:List.of("READY","RUNNING","RECONCILING"))for(String external:List.of("PREPARED","MARKED","UNKNOWN")){expect(Decision.WAIT,new State(task,external,external,false,false),false);checks++;}
+  expect(Decision.WAIT,new State("RECONCILING","MARKED","MARKED",false,false),false);checks++;
+  expect(Decision.WAIT,new State("SUCCEEDED","SUCCEEDED","SUCCEEDED",false,false),false);checks++;
+  expect(Decision.SUCCESS,new State("SUCCEEDED","SUCCEEDED","SUCCEEDED",true,false),false);checks++;
+  expect(Decision.UNRESOLVED,new State("RECONCILING","UNKNOWN","UNKNOWN",true,false),false);checks++;
+  expect(Decision.UNRESOLVED,new State("RECONCILING","MARKED","MARKED",true,true),false);checks++;
+  expect(Decision.TIMEOUT,new State("RECONCILING","MARKED","MARKED",false,false),true);checks++;
+  expect(Decision.TIMEOUT,new State("SUCCEEDED","UNKNOWN","UNKNOWN",false,false),true);checks++;
+  expect(Decision.LOCAL_STOP,new State("RETRYABLE",null,null,true,true),false);checks++;
+  expect(Decision.TERMINAL_FAILURE,new State("FAILED","FAILED","FAILED",true,false),false);checks++;
+  var marked=new State("RECONCILING","MARKED","MARKED",false,false);var success=new State("SUCCEEDED","SUCCEEDED","SUCCEEDED",true,false);
+  if(!retainProvider(marked,success,true,false)||!retainProvider(success,success,false,false)||!retainProvider(success,null,true,false)||!retainProvider(success,success,true,true)||retainProvider(success,success,true,false))throw new AssertionError("Provider retention policy");checks+=5;
+  System.out.println("{\"checks\":"+checks+",\"passed\":true,\"businessCalls\":0,\"databaseCalls\":0,\"providerStarts\":0}");
+ }
+}

## backend/integration-tests/src/test/java/ama/storage/Task11PublishedReceiptIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11PublishedReceiptIT.java
@@ -0,0 +1,14 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+/** Exact terminal GET qualification; intentionally not lost-response recovery. */
+class Task11PublishedReceiptIT extends Task11PublicationResumeIT {
+ @Override String previousWindow(){return "publication-main-03";}
+ @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  String publication="7255d632-1a01-4e55-9080-7dc173bcb499",execution="3b2920bc-0f3e-42fd-9719-a2ee311bae28";var service=ama.api.Task11RuntimeProbe.publication(app);var before=state(publication);var traffic=service.traffic();
+  var actual=service.readPublishedReceipt(actor(),meeting,publication);assertEquals(execution,actual.get("executionId"));assertEquals("doc-b2b635fc3fcaaf513f82637092e92638",actual.get("documentId"));assertEquals("result-5a7910348168b4e456b36a824644e666",actual.get("resultId"));assertEquals("SUCCESS",actual.get("state"));
+  assertEquals(before,state(publication));var after=service.traffic();assertEquals(traffic.get("postWriteAttempts"),after.get("postWriteAttempts"));assertEquals(traffic.get("getWriteAttempts")+1,after.get("getWriteAttempts"));
+  Files.writeString(receipt.resolve("terminal-get-readback.json"),json.writeValueAsString(Map.of("publication",publication,"execution",execution,"receipt",actual,"before",before,"after",state(publication),"trafficBefore",traffic,"trafficAfter",after,"qualification","owner-authorized terminal GET; no claim/observe/POST; not lost-response recovery")));
+ }
+ List<Map<String,Object>> state(String publication){return j.queryForList("SELECT p.id,p.state publication_state,m.stage,m.revision,m.ever_published,t.state task_state,o.state operation_state,e.id execution_id,e.execution_no,e.state execution_state,a.outcome FROM publication p JOIN meeting m ON m.id=p.meeting_id JOIN publication_submission s ON s.publication_id=p.id JOIN durable_task t ON t.id=s.task_id JOIN external_execution e ON e.task_id=t.id JOIN external_operation o ON o.id=e.operation_id JOIN publication_attempt a ON a.execution_id=e.id WHERE p.id=? ORDER BY e.execution_no",publication);}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11RejectedReceiptIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11RejectedReceiptIT.java
@@ -0,0 +1,11 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+/** Original admitted rejection GET after provider publish revocation; no new publication or send. */
+class Task11RejectedReceiptIT extends Task11PublicationResumeIT {
+ @Override String previousWindow(){return "publication-zero-effect-01";}
+ @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  String publication="368767a5-c477-40fc-8e27-13705667c5a0",execution="0a360254-6710-442e-8c54-a07a913e2362";var service=ama.api.Task11RuntimeProbe.publication(app);var before=state(publication);var traffic=service.traffic();var actual=service.readRejectedReceipt(actor(),meeting,publication);assertEquals(execution,actual.get("executionId"));assertEquals("FAILED",actual.get("state"));assertEquals("DEFINITIVE_REJECTION",actual.get("noEffectBasis"));assertEquals(before,state(publication));assertEquals(traffic.get("postWriteAttempts"),service.traffic().get("postWriteAttempts"));assertEquals(traffic.get("getWriteAttempts")+1,service.traffic().get("getWriteAttempts"));Files.writeString(receipt.resolve("original-rejection-get.json"),json.writeValueAsString(Map.of("publication",publication,"execution",execution,"before",before,"after",state(publication),"actual",actual,"traffic",service.traffic(),"qualification","original signed immutable rejection GET; provider publish grant remains revoked; no claim/observe/new execution/POST")));
+ }
+ List<Map<String,Object>> state(String publication){return j.queryForList("SELECT p.id,p.state publication_state,p.revision publication_revision,m.stage,m.revision,m.ever_published,t.state task_state,o.state operation_state,e.id execution_id,e.execution_no,e.state execution_state,e.no_effect_basis,e.no_effect_evidence,CAST(e.no_effect_verified_at AS CHAR) verified_at,a.outcome FROM publication p JOIN meeting m ON m.id=p.meeting_id JOIN publication_submission s ON s.publication_id=p.id JOIN durable_task t ON t.id=s.task_id JOIN external_execution e ON e.task_id=t.id JOIN external_operation o ON o.id=e.operation_id JOIN publication_attempt a ON a.execution_id=e.id WHERE p.id=? ORDER BY e.execution_no",publication);}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11ReplacementBoundaryIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11ReplacementBoundaryIT.java
@@ -0,0 +1,13 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import java.time.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+/** Accepted next task remains unclaimed/PREPARED while its authority and epoch boundaries are checked. */
+class Task11ReplacementBoundaryIT extends Task11PublicationResumeIT {
+ @Override String previousWindow(){return "publication-zero-effect-01";}
+ @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  String publication="368767a5-c477-40fc-8e27-13705667c5a0",old="302565ef-16e7-49df-b5c2-76caa2a13e33",next="1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27",operation="ccad27fc-5cf1-432b-83f8-a71341efcff5",execution="0a360254-6710-442e-8c54-a07a913e2362";var before=inventory(publication);var op=j.queryForMap("SELECT state,current_execution_no,reconcile_epoch,reconcile_owner,reconcile_expires_at FROM external_operation WHERE id=?",operation);assertEquals("RETRY_QUEUED",op.get("state"));assertEquals(2,((Number)op.get("current_execution_no")).intValue());assertEquals(2,((Number)op.get("reconcile_epoch")).intValue());assertNull(op.get("reconcile_owner"));assertNull(op.get("reconcile_expires_at"));var queue=app.getBean(ama.task.JdbcTaskQueue.class);assertEquals(5,ama.transcript.Task11ReplacementBoundaryProbe.verify(ama.api.Task09RuntimeProbe.components(app).authority(),queue,next,old));
+  var obsolete=new ama.contract.ExternalContracts.OperationToken(operation,execution,1,1,"TASK11_KNOWLEDGE",Instant.parse("2026-10-24T18:34:25.369067Z"),new ama.contract.TaskContracts.SubjectRef("MEETING",meeting));assertFalse(new ama.task.JdbcExternalOperations(queue).renew(obsolete));assertEquals(before,inventory(publication));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=?",Integer.class,next));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_attempt WHERE execution_id='427e9751-2bca-483b-8d91-cb1711ae127c'",Integer.class));assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",0L),ama.api.Task11RuntimeProbe.publication(app).traffic());
+  Files.writeString(receipt.resolve("replacement-boundaries-no-send.json"),json.writeValueAsString(Map.of("nextTask",next,"originalExecution",execution,"operation",op,"before",before,"after",inventory(publication),"privateAuthorityChecks",5,"oldFenceRenewDenied",true,"qualification","historical fence value reconstructed solely for real renew rejection; not a retained genuine initial-send capability; all tasks remain unclaimed/no POST")));
+ }
+ List<Map<String,Object>> inventory(String publication){return j.queryForList("SELECT s.task_id,s.submission_no,s.local_state,t.state task_state,t.attempt,o.id operation_id,o.state operation_state,o.current_execution_no,o.reconcile_epoch,o.reconcile_owner,CAST(o.reconcile_expires_at AS CHAR) expiry,e.id execution_id,e.state execution_state,e.execution_no,CAST(e.marked_at AS CHAR) marked_at,e.task_attempt,p.revision publication_revision FROM publication_submission s JOIN durable_task t ON t.id=s.task_id JOIN publication p ON p.id=s.publication_id LEFT JOIN external_execution e ON e.task_id=t.id LEFT JOIN external_operation o ON o.id=e.operation_id WHERE s.publication_id=? ORDER BY s.submission_no,e.execution_no",publication);}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11ReplacementIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11ReplacementIT.java
@@ -0,0 +1,17 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
+/** Original permanent-rejection fact; acceptance is tested before any next publication POST. */
+class Task11ReplacementIT extends Task11PublicationResumeIT {
+ @Override void configurePublicationRuntime(Map<String,Object> configured){configured.put("logging.level.ama.transcript.JdbcPublicationReplacement","WARN");}
+ @Override String previousWindow(){return "publication-zero-effect-01";}
+ @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  String publication="368767a5-c477-40fc-8e27-13705667c5a0",execution="0a360254-6710-442e-8c54-a07a913e2362";var before=j.queryForMap("SELECT p.state,p.revision,m.revision meeting_revision,m.final_confirmation_id,m.cancel_epoch FROM publication p JOIN meeting m ON m.id=p.meeting_id WHERE p.id=?",publication);assertEquals("FAILED",before.get("state"));assertEquals("DEFINITIVE_REJECTION",j.queryForObject("SELECT no_effect_basis FROM external_execution WHERE id=?",String.class,execution));
+  token=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR FROM enterprise_identity WHERE enterprise_id=?",hash(token),csrf,owner);
+  String path="/meetings/"+meeting+"/publications/"+publication+"/resubmissions";String request=json.writeValueAsString(Map.of("expectedRevisionToken",before.get("meeting_revision").toString(),"expectedPublicationRevisionToken",before.get("revision").toString(),"confirmationId",before.get("final_confirmation_id"),"providerId","CONTROLLED_KNOWLEDGE_V1","libraryId","controlled-library","folderId","controlled-root"));
+  Files.writeString(receipt.resolve("replacement-original-before.json"),json.writeValueAsString(Map.of("publication",publication,"execution",execution,"before",before,"request",request,"nextPublicationPostAuthorizedByTest",false)));
+  knowledge.grant(owner);var original=j.queryForMap("SELECT id,operation_id,execution_no,task_id,state,request_hash,request_manifest,no_effect_basis,no_effect_evidence,CAST(no_effect_verified_at AS CHAR) verified_at,CAST(marked_at AS CHAR) marked_at,task_attempt FROM external_execution WHERE id=?",execution);var oldSubmission=j.queryForMap("SELECT task_id,publication_id,submission_no,confirmation_id,business_key,logical_request_hash,execution_request_hash,request_manifest,local_state FROM publication_submission WHERE task_id=?","302565ef-16e7-49df-b5c2-76caa2a13e33");var traffic=ama.api.Task11RuntimeProbe.publication(app).traffic();
+  var response=call("POST",path,request,"task11-original-rejection-replacement");Files.writeString(receipt.resolve("replacement-response.json"),json.writeValueAsString(Map.of("status",response.statusCode(),"body",response.body())));assertEquals(202,response.statusCode(),response.body());
+  var accepted=body(response);assertEquals(publication,accepted.get("publicationId").asText());assertNotEquals("302565ef-16e7-49df-b5c2-76caa2a13e33",accepted.get("taskId").asText());assertEquals("2",accepted.get("submissionNoToken").asText());String next=accepted.get("taskId").asText();assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,next));var prepared=j.queryForMap("SELECT id,operation_id,execution_no,state,marked_at,task_attempt FROM external_execution WHERE task_id=?",next);assertEquals("PREPARED",prepared.get("state"));assertEquals(original.get("operation_id"),prepared.get("operation_id"));assertEquals(2,((Number)prepared.get("execution_no")).intValue());assertNull(prepared.get("marked_at"));assertNull(prepared.get("task_attempt"));assertEquals(original,j.queryForMap("SELECT id,operation_id,execution_no,task_id,state,request_hash,request_manifest,no_effect_basis,no_effect_evidence,CAST(no_effect_verified_at AS CHAR) verified_at,CAST(marked_at AS CHAR) marked_at,task_attempt FROM external_execution WHERE id=?",execution));assertEquals(oldSubmission,j.queryForMap("SELECT task_id,publication_id,submission_no,confirmation_id,business_key,logical_request_hash,execution_request_hash,request_manifest,local_state FROM publication_submission WHERE task_id=?","302565ef-16e7-49df-b5c2-76caa2a13e33"));var replay=call("POST",path,request,"task11-original-rejection-replacement");assertEquals(202,replay.statusCode(),replay.body());assertEquals(next,body(replay).get("taskId").asText());assertTrue(body(replay).get("replayed").asBoolean());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_attempt WHERE execution_id=?",Integer.class,prepared.get("id")));var afterTraffic=ama.api.Task11RuntimeProbe.publication(app).traffic();assertEquals(traffic.get("postWriteAttempts")+1,afterTraffic.get("postWriteAttempts"));assertEquals(traffic.get("getWriteAttempts")+1,afterTraffic.get("getWriteAttempts"));Files.writeString(receipt.resolve("replacement-accepted-no-send.json"),json.writeValueAsString(Map.of("originalExecution",execution,"accepted",accepted,"prepared",prepared,"traffic",afterTraffic,"oldImmutableExecutionAndSubmissionEqual",true,"qualification","real permanent proof GET plus one destination authorization POST; new task+PREPARED accepted; zero publication POST/marker; independent review gate still holds")));
+ }
+}

## backend/integration-tests/src/test/java/ama/storage/Task11ReplacementRollbackIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11ReplacementRollbackIT.java
@@ -0,0 +1,17 @@
+package ama.storage;
+import java.nio.file.*;import java.sql.*;import java.lang.reflect.*;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.transaction.support.*;import static org.junit.jupiter.api.Assertions.*;
+/** Real fresh-schema TX with test-only fault after actual final outbox sequence UPDATE. */
+class Task11ReplacementRollbackIT extends Task11PublicationResumeIT {
+ boolean armed;final java.util.concurrent.atomic.AtomicBoolean installed=new java.util.concurrent.atomic.AtomicBoolean();boolean rolledBack;
+ static final class ExpectedRollback extends RuntimeException {}
+ @Override String previousWindow(){return "publication-local-terminal-02";}
+ @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
+ static Object invoke(Object actual,Method method,Object[] args)throws Throwable{try{return method.invoke(actual,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
+ @Override javax.sql.DataSource createSource(){var actual=super.createSource();return (javax.sql.DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{javax.sql.DataSource.class},(proxy,method,args)->{Object value=invoke(actual,method,args);if(!(value instanceof Connection connection))return value;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{Object result=invoke(connection,cm,ca);if(cm.getName().equals("rollback"))rolledBack=true;if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{Object executed=invoke(statement,sm,sa);if(armed&&sql.startsWith("UPDATE task_outbox_sequence")&&sm.getName().startsWith("execute")&&installed.compareAndSet(false,true)){assertTrue(TransactionSynchronizationManager.isActualTransactionActive());throw new ExpectedRollback();}return executed;});});});}
+ @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
+  String publication="df3ed515-70f5-483a-8155-933e07266997";var before=inventory(publication);var header=j.queryForMap("SELECT p.revision,m.revision meeting_revision,m.final_confirmation_id,p.library_id,p.folder_id FROM publication p JOIN meeting m ON m.id=p.meeting_id WHERE p.id=?",publication);var service=ama.api.Task11RuntimeProbe.publication(app);var traffic=service.traffic();armed=true;
+  assertThrows(ExpectedRollback.class,()->service.resubmit(actor(),meeting,publication,"task11-real-replacement-rollback",((Number)header.get("meeting_revision")).longValue(),((Number)header.get("revision")).longValue(),(String)header.get("final_confirmation_id"),"CONTROLLED_KNOWLEDGE_V1",(String)header.get("library_id"),(String)header.get("folder_id"),UUID.randomUUID().toString()));armed=false;assertTrue(installed.get());assertTrue(rolledBack);assertEquals(before,inventory(publication));assertEquals(traffic.get("getWriteAttempts"),service.traffic().get("getWriteAttempts"));assertEquals(traffic.get("postWriteAttempts")+1,service.traffic().get("postWriteAttempts"));
+  Files.writeString(receipt.resolve("replacement-actual-rollback.json"),json.writeValueAsString(Map.of("publication",publication,"before",before,"after",inventory(publication),"actualJdbcRollbackObserved",rolledBack,"actualFinalSequenceUpdateObserved",true,"traffic",service.traffic(),"qualification","test-only Connection/PreparedStatement delegates actual JDBC and injects failure after actual final outbox sequence UPDATE; queue/task/submission/publication/idempotency/audit/outbox rolled back; one destination assertion POST, no publication send; original sealed local fact reused")));
+ }
+ Map<String,Object> inventory(String publication){return Map.of("tasks",j.queryForList("SELECT id,state,attempt,producer_key,input_fingerprint FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? ORDER BY id",meeting),"submissions",j.queryForList("SELECT task_id,submission_no,confirmation_id,local_state,local_reason,SHA2(request_manifest,256) manifest_hash FROM publication_submission WHERE publication_id=? ORDER BY submission_no",publication),"publication",j.queryForMap("SELECT p.state,p.revision,m.stage,m.revision meeting_revision,m.cancel_epoch,m.final_confirmation_id FROM publication p JOIN meeting m ON m.id=p.meeting_id WHERE p.id=?",publication),"idempotency",j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=?",Long.class,meeting),"audit",j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Long.class,meeting),"outbox",j.queryForObject("SELECT COUNT(*) FROM task_outbox o JOIN durable_task t ON t.id=o.task_id WHERE t.subject_kind='MEETING' AND t.subject_id=?",Long.class,meeting),"executions",j.queryForObject("SELECT COUNT(*) FROM external_execution e JOIN durable_task t ON t.id=e.task_id WHERE t.subject_kind='MEETING' AND t.subject_id=?",Long.class,meeting));}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11ResponseLossFixture.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11ResponseLossFixture.java
@@ -0,0 +1,16 @@
+package ama.storage;
+import java.nio.file.*;import java.net.*;import java.util.*;import java.util.concurrent.TimeUnit;import com.fasterxml.jackson.databind.*;import static org.junit.jupiter.api.Assertions.*;
+/** Independently owned relay survives the test JVM if its exact fact remains unresolved. */
+final class Task11ResponseLossFixture implements AutoCloseable {
+ final ObjectMapper json=new ObjectMapper();final Process process;final int port;final Path state,config,evidence;boolean settled;
+ Task11ResponseLossFixture(Task11KnowledgeFixture provider,Path evidence)throws Exception {this(provider,evidence,"LOSS",null);}
+ Task11ResponseLossFixture(Task11KnowledgeFixture provider,Path evidence,String mode,String actor)throws Exception {
+  this.evidence=evidence;Path script=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"tools/providers/knowledge_response_loss.py"),runtime=provider.root.resolve("runtime");String id=UUID.randomUUID().toString();state=runtime.resolve("response-loss-state-"+id+".json");config=runtime.resolve("response-loss-config-"+id+".json");
+  try(var socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
+  var configuration=new TreeMap<String,Object>();configuration.putAll(Map.of("port",port,"upstream",provider.port,"state",state.toString(),"mode",mode));if(actor!=null){configuration.put("actor",actor);configuration.put("providerScript",provider.providerScript.toString());configuration.put("providerConfig",provider.config.toString());}Files.createFile(config,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));Files.writeString(config,json.writeValueAsString(configuration));
+  Files.writeString(evidence.resolve("response-loss-before-start.json"),json.writeValueAsString(Map.of("config",config.toString(),"port",port,"providerPort",provider.port,"providerPid",provider.pid(),"store",provider.store.toString(),"clientIdentities",provider.applicationIdentities.toString(),"source",script.toString(),"sourceSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(script))),"lifecycle","64Mi/2CPU owned setsid; retain exact endpoint/config/PGID if unresolved; mode="+mode)));
+  process=new ProcessBuilder("setsid","/usr/bin/python3",script.toString(),"--config",config.toString()).redirectErrorStream(true).redirectOutput(evidence.resolve("response-loss-process.log").toFile()).start();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(process.isAlive()&&!Files.exists(state)&&System.nanoTime()<end)Thread.sleep(50);assertTrue(process.isAlive());assertTrue(Files.exists(state));var ready=read();assertEquals(process.pid(),ready.get("pid").asLong());assertEquals(process.pid(),ready.get("pgid").asLong());Files.writeString(evidence.resolve("response-loss-started.json"),json.writeValueAsString(ready));
+ }
+ JsonNode read()throws Exception{return json.readTree(Files.readAllBytes(state));}
+ public void close()throws Exception {var record=new TreeMap<String,Object>();record.put("pid",process.pid());record.put("pgid",process.pid());record.put("port",port);record.put("config",config.toString());record.put("statePath",state.toString());record.put("actualState",read());record.put("settled",settled);if(settled&&process.isAlive()){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}}record.put("alive",process.isAlive());Files.writeString(evidence.resolve("response-loss-exit-custody.json"),json.writeValueAsString(record));}
+}

## backend/integration-tests/src/test/java/ama/storage/Task11ZeroEffectIT.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/storage/Task11ZeroEffectIT.java
@@ -0,0 +1,14 @@
+package ama.storage;
+import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
+/** Real grant revocation after signed destination authorization, before exact authenticated POST. */
+class Task11ZeroEffectIT extends Task11FinalPublicationIT {
+ Task11ResponseLossFixture revocation;
+ @AfterEach void closeRevocation()throws Exception {if(revocation!=null)revocation.close();}
+ @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {var fixture=new Task11KnowledgeFixture(receipt,owner);revocation=new Task11ResponseLossFixture(fixture,receipt,"REVOKE_BEFORE_PUBLISH",owner);return fixture;}
+ @Override int knowledgeClientPort(){return revocation.port;}
+ @Override Task11PublicationWaiter.Decision expectedPublicationDecision(){return Task11PublicationWaiter.Decision.TERMINAL_FAILURE;}
+ @Override void runPublication(Map<String,Object> accepted)throws Exception {
+  super.runPublication(accepted);String task=(String)accepted.get("taskId"),publication=(String)accepted.get("publicationId");var actual=revocation.read();assertEquals(1,actual.get("postPublication").asInt());assertEquals(0,actual.get("get").asInt());assertEquals(0,actual.get("failures").asInt());assertEquals("FAILED",actual.get("receipt").get("state").asText());assertEquals("DEFINITIVE_REJECTION",actual.get("receipt").get("noEffectBasis").asText());assertTrue(actual.get("receipt").get("permanentRejection").asText().contains("."));var execution=j.queryForMap("SELECT id,operation_id,execution_no,state,no_effect_basis,no_effect_evidence,no_effect_verified_at FROM external_execution WHERE task_id=?",task);assertEquals("FAILED",execution.get("state"));assertEquals("DEFINITIVE_REJECTION",execution.get("no_effect_basis"));assertTrue(((String)execution.get("no_effect_evidence")).startsWith("knowledge:"));assertNotNull(execution.get("no_effect_verified_at"));assertEquals(execution.get("id"),actual.get("receipt").get("executionId").asText());assertEquals("FAILED",j.queryForObject("SELECT state FROM publication WHERE id=?",String.class,publication));assertEquals("PUBLISH_FAILED",j.queryForObject("SELECT stage FROM meeting WHERE id=?",String.class,meeting));assertEquals(0,j.queryForObject("SELECT ever_published FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_document_mapping WHERE publication_id=?",Integer.class,publication));assertEquals("FAILED",j.queryForObject("SELECT outcome FROM publication_attempt WHERE publication_id=?",String.class,publication));revocation.settled=true;
+  execution.put("no_effect_verified_at",execution.get("no_effect_verified_at").toString());Files.writeString(receipt.resolve("actual-permanent-rejection.json"),json.writeValueAsString(Map.of("accepted",accepted,"execution",execution,"provider",actual,"traffic",ama.api.Task11RuntimeProbe.publication(app).traffic(),"qualification","actual authenticated signed durable rejection; no inferred absence/timeout/HTTP error proof")));
+ }
+}

## backend/integration-tests/src/test/java/ama/transcript/Task11LocalContextProbe.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/transcript/Task11LocalContextProbe.java
@@ -0,0 +1,6 @@
+package ama.transcript;
+import ama.task.*;import ama.contract.TaskContracts.TaskSpec;import static org.junit.jupiter.api.Assertions.*;
+/** Negative authority tests using the actual installed issuer/preparation, with no selected writer. */
+public final class Task11LocalContextProbe {
+ public static int verify(JdbcTranscriptAuthority authority,TaskSpec spec){var policy=authority.publicationDomain();var fake=new PublicationLocalTerminationPolicy.LocalTerminalContext(){};var held=authority.transactionRunner().required(()->{var actual=policy.prepare(java.util.List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,false).orElseThrow();assertThrows(IllegalArgumentException.class,()->policy.validateLocalTerminal(actual,fake));assertThrows(IllegalArgumentException.class,()->policy.sealLocalTerminal(actual,fake));return actual;});authority.transactionRunner().required(()->{assertThrows(IllegalArgumentException.class,()->policy.validateLocalTerminal(held,fake));return null;});return 3;}
+}

## backend/integration-tests/src/test/java/ama/transcript/Task11ReplacementBoundaryProbe.java
--- /dev/null
+++ b/backend/integration-tests/src/test/java/ama/transcript/Task11ReplacementBoundaryProbe.java
@@ -0,0 +1,5 @@
+package ama.transcript;
+import ama.task.*;import ama.contract.TaskContracts.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
+public final class Task11ReplacementBoundaryProbe {
+ public static int verify(JdbcTranscriptAuthority authority,JdbcTaskQueue queue,String nextTask,String oldTask){var j=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());TaskSpec next=TranscriptReadGraph.spec(j.queryForMap("SELECT task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,priority,cancel_epoch,max_attempts FROM durable_task WHERE id=?",nextTask));TaskSpec old=TranscriptReadGraph.spec(j.queryForMap("SELECT task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,priority,cancel_epoch,max_attempts FROM durable_task WHERE id=?",oldTask));var fake=new PublicationReplacementPolicy.PreparedPublicationReplacement(){public TaskSpec spec(){return next;}};assertThrows(IllegalArgumentException.class,()->new JdbcExternalOperations(queue).replaceZeroEffectPublication(fake));var policy=authority.publicationDomain();var held=authority.transactionRunner().required(()->{var actual=policy.prepare(List.of(next),TaskDomainPolicy.Acquisition.TASK_MUTATION,false).orElseThrow();assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(List.of(old,next),TaskDomainPolicy.Acquisition.TASK_MUTATION,actual));assertThrows(IllegalArgumentException.class,()->policy.requireReplacement(fake));return actual;});authority.transactionRunner().required(()->{assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(List.of(next),TaskDomainPolicy.Acquisition.TASK_MUTATION,held));var issuer=new JdbcTaskPreparations(authority.dataSource());var own=issuer.issue(List.of(next),TaskDomainPolicy.Acquisition.TASK_MUTATION,new Object(),true);assertThrows(IllegalArgumentException.class,()->issuer.requirePublicationReplacement(own,new PublicationReplacementPolicy.ReplacementContext(){},fake));return null;});return 5;}
+}

## backend/worker/src/main/java/ama/worker/KnowledgePublicationHandler.java
--- /dev/null
+++ b/backend/worker/src/main/java/ama/worker/KnowledgePublicationHandler.java
@@ -0,0 +1,8 @@
+package ama.worker;
+import ama.transcript.*;import ama.task.*;import java.util.Objects;
+/** Only the real Task7 session admits a publication POST after the durable marker. */
+public final class KnowledgePublicationHandler implements WorkerLoop.ExternalHandler {
+ private final JdbcPublicationService service;private final ExternalOperationPort operations;private final ModelPermitPool permits;
+ public KnowledgePublicationHandler(JdbcPublicationService service,ExternalOperationPort operations,ModelPermitPool permits){this.service=Objects.requireNonNull(service);this.operations=Objects.requireNonNull(operations);this.permits=Objects.requireNonNull(permits);}
+ public void handle(ExternalTaskExecution execution)throws Exception{if(!(execution instanceof ExternalExecutionSession session))throw new IllegalArgumentException("Genuine production session required");session.requireBinding(operations,permits,null);var request=service.prepare(session.lease());if(!session.begin(request))return;session.send(()->service.execute(request,session.token(),session.cancellation()));}
+}

## backend/worker/src/main/java/ama/worker/KnowledgeReconciliationHandler.java
--- /dev/null
+++ b/backend/worker/src/main/java/ama/worker/KnowledgeReconciliationHandler.java
@@ -0,0 +1,8 @@
+package ama.worker;
+import ama.transcript.JdbcPublicationService;import ama.task.*;import java.util.Objects;
+/** Genuine committed reconciliation holder; exact original GET only, never send authority. */
+public final class KnowledgeReconciliationHandler implements ReconciliationLoop.PreparedHandler {
+ private final JdbcPublicationService service;private final JdbcExternalOperations operations;private final ModelPermitPool permits;
+ public KnowledgeReconciliationHandler(JdbcPublicationService service,JdbcExternalOperations operations,ModelPermitPool permits){this.service=Objects.requireNonNull(service);this.operations=Objects.requireNonNull(operations);this.permits=Objects.requireNonNull(permits);}
+ public void reconcile(ExternalTaskExecution value)throws Exception {if(!(value instanceof ExternalExecutionSession session))throw new IllegalArgumentException("Genuine reconciliation session required");session.requireReconciliationBinding(operations,permits);var token=session.token();var request=service.prepareLookup(operations.recordedLookup(token),token);var observation=service.lookup(request,session.cancellation());session.observe(observation);}
+}

## backend/worker/src/main/java/ama/worker/ReconciliationLoop.java
--- a/backend/worker/src/main/java/ama/worker/ReconciliationLoop.java
+++ b/backend/worker/src/main/java/ama/worker/ReconciliationLoop.java
@@ -1,71 +1,76 @@
 package ama.worker;
 
 import ama.task.ExternalOperationPort;
 import ama.contract.ExternalContracts.OperationToken;
 import java.util.Objects;
 import java.util.concurrent.*;
 import java.util.concurrent.atomic.AtomicBoolean;
 import static ama.contract.OutboxContracts.protocolCode;
 
 /** Independent bounded reconciliation of known operations. No send/admission authority is minted here. */
 public final class ReconciliationLoop implements WorkerRuntime {
     @FunctionalInterface public interface PreparedHandler { void reconcile(ExternalTaskExecution session) throws Exception; }
     public static ReconciliationLoop prepared(ExternalOperationPort operations,ama.task.ModelPermitPool permits,int capacity,PreparedHandler handler,String owner){
-        return new ReconciliationLoop(operations,capacity,null,Objects.requireNonNull(handler),Objects.requireNonNull(permits),owner);
+        return new ReconciliationLoop(operations,capacity,null,Objects.requireNonNull(handler),Objects.requireNonNull(permits),owner,null);
+    }
+    public static ReconciliationLoop preparedWithPublicationMaintenance(ExternalOperationPort operations,ama.task.ModelPermitPool permits,int capacity,PreparedHandler handler,String owner,ama.transcript.JdbcPublicationLocalRecovery local){
+        return new ReconciliationLoop(operations,capacity,null,Objects.requireNonNull(handler),Objects.requireNonNull(permits),owner,Objects.requireNonNull(local));
     }
     @FunctionalInterface public interface Handler { void reconcile(OperationToken token) throws Exception; }
     private static final System.Logger LOG = System.getLogger(ReconciliationLoop.class.getName());
     private final ExternalOperationPort operations;
+    private final ama.transcript.JdbcPublicationLocalRecovery localPublications;
     private final Handler handler;private final PreparedHandler preparedHandler;private final ama.task.ModelPermitPool permits;
     private final java.util.Set<ExternalExecutionSession> sessions=ConcurrentHashMap.newKeySet();
     private final java.util.Set<ExternalExecutionSession> pending=ConcurrentHashMap.newKeySet();
     private final String owner;
     private final Semaphore slots;
     private final ExecutorService workers;
     private final ScheduledExecutorService scanner;
     private final ExternalOperationHeartbeat heartbeats;
     private final AtomicBoolean started = new AtomicBoolean();
     private final AtomicBoolean closed = new AtomicBoolean();
     private final AtomicBoolean scanning = new AtomicBoolean();
 
     public ReconciliationLoop(ExternalOperationPort operations, int capacity, Handler handler, String owner) {
-        this(operations,capacity,Objects.requireNonNull(handler),null,null,owner);
+        this(operations,capacity,Objects.requireNonNull(handler),null,null,owner,null);
     }
-    private ReconciliationLoop(ExternalOperationPort operations,int capacity,Handler handler,PreparedHandler preparedHandler,ama.task.ModelPermitPool permits,String owner){
+    private ReconciliationLoop(ExternalOperationPort operations,int capacity,Handler handler,PreparedHandler preparedHandler,ama.task.ModelPermitPool permits,String owner,ama.transcript.JdbcPublicationLocalRecovery localPublications){
         if (capacity < 1) throw new IllegalArgumentException("Explicit positive reconciliation capacity required");
-        this.operations = Objects.requireNonNull(operations);
+        this.operations = Objects.requireNonNull(operations);this.localPublications=localPublications;
         this.handler = handler;this.preparedHandler=preparedHandler;this.permits=permits;
         protocolCode(owner);
         this.owner = owner;
         slots = new Semaphore(capacity);
         workers = new ThreadPoolExecutor(capacity, capacity, 0, TimeUnit.SECONDS,
                 new ArrayBlockingQueue<>(capacity), task -> new Thread(task, "ama-reconciliation"));
         scanner = Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "ama-reconciliation-poll"));
         heartbeats = new ExternalOperationHeartbeat(operations, capacity);
     }
 
     @Override public void start() {
         if (closed.get()) throw new IllegalStateException("Reconciler is closed");
         if (started.compareAndSet(false, true)) scanner.scheduleWithFixedDelay(this::poll, 0, 5, TimeUnit.SECONDS);
     }
 
     private void poll() {
         if (closed.get() || !scanning.compareAndSet(false, true)) return;
         try {
             // Maintenance precedes capacity/claim checks, including an entirely saturated worker.
             if(permits!=null)permits.recoverSettlements(8);
+            if(localPublications!=null)try{localPublications.maintainLocalFailures();}catch(RuntimeException unavailable){LOG.log(System.Logger.Level.WARNING,"Publication local maintenance unavailable: "+unavailable.getClass().getSimpleName());}
             for(var session:pending){session.retry();if(!session.pending()&&pending.remove(session)){session.close();sessions.remove(session);slots.release();}}
             while (!closed.get() && slots.tryAcquire()) {
                 boolean submitted = false;
                 try {
                     var claimed = operations.claimReconciliation(owner);
                     if (claimed.isEmpty()) return;
                     workers.execute(() -> reconcile(claimed.orElseThrow()));
                     submitted = true;
                 } finally { if (!submitted) slots.release(); }
             }
         } catch (RuntimeException unavailable) {
             LOG.log(System.Logger.Level.WARNING, "Reconciliation poll unavailable: " + unavailable.getClass().getSimpleName());
         } finally { scanning.set(false); }
     }
 

## backend/worker/src/main/java/ama/worker/TranscriptWorkerMain.java
--- a/backend/worker/src/main/java/ama/worker/TranscriptWorkerMain.java
+++ b/backend/worker/src/main/java/ama/worker/TranscriptWorkerMain.java
@@ -5,25 +5,25 @@
 import java.nio.file.*;
 import java.net.*;
 import java.util.Properties;
 import org.springframework.core.env.*;
 import org.springframework.jdbc.datasource.DriverManagerDataSource;
 /** Explicit independent TXT process; legacy ServiceLoader entry remains unchanged. */
 public final class TranscriptWorkerMain {
  private TranscriptWorkerMain(){}
  public static void main(String[] args)throws Exception{
   if(args.length!=1)throw new IllegalArgumentException("One private worker configuration path required");
   Path path=Path.of(args[0]);if(Files.isSymbolicLink(path)||!Files.isRegularFile(path)||Files.size(path)>65536)throw new IllegalArgumentException("Invalid worker configuration");
   var properties=new Properties();try(var in=Files.newInputStream(path)){properties.load(in);}
   var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("worker",properties));
   javax.sql.DataSource source=env.getProperty("minutes.enabled",Boolean.class,false)?ama.minutes.ControlledMinutesDataSource.create(env.getRequiredProperty("database.url"),env.getRequiredProperty("database.user"),env.getRequiredProperty("database.password")):new DriverManagerDataSource(env.getRequiredProperty("database.url"),env.getRequiredProperty("database.user"),env.getRequiredProperty("database.password"));
   var settings=ConfiguredProcessing.read(env);var definitions=ConfiguredBackends.read(env);
-  var provider=new TranscriptWorkerRuntimeProvider(source,definitions,settings.read(),settings.canonical(),settings.preparation(),env.getRequiredProperty("worker.id"),env.getRequiredProperty("worker.capacity",Integer.class),env.getProperty("minutes.enabled",Boolean.class,false)?ama.transcript.ConfiguredMinutes.read(env,settings.read(),settings.index()):null);
+  var provider=new TranscriptWorkerRuntimeProvider(source,definitions,settings.read(),settings.canonical(),settings.preparation(),env.getRequiredProperty("worker.id"),env.getRequiredProperty("worker.capacity",Integer.class),env.getProperty("minutes.enabled",Boolean.class,false)?ama.transcript.ConfiguredMinutes.read(env,settings.read(),settings.index()):null,env.getProperty("publication.enabled",Boolean.class,false)?ama.transcript.ConfiguredPublication.read(env,settings.read(),settings.index()):null);
   var runtime=(TranscriptWorkerRuntimeProvider.Runtime)provider.create();
   try(runtime){
   var tls=ServiceTls.context(Path.of(env.getRequiredProperty("worker.health.key-store")),env.getRequiredProperty("worker.health.key-store-password").toCharArray(),Path.of(env.getRequiredProperty("worker.health.trust-store")),env.getRequiredProperty("worker.health.trust-store-password").toCharArray());
   try(var health=new WorkerHealthServer(new InetSocketAddress("127.0.0.1",env.getRequiredProperty("worker.health.port",Integer.class)),tls,env.getRequiredProperty("worker.health.peer"),runtime::observation)){
    health.start();System.getLogger(TranscriptWorkerMain.class.getName()).log(System.Logger.Level.INFO,"TXT worker management bound to loopback port "+health.port());WorkerMain.run(runtime);
   }
   }
  }
 }

## backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java
--- a/backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java
+++ b/backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java
@@ -1,64 +1,73 @@
 package ama.worker;
 import ama.config.*;
 import ama.contract.ConfigArchiveContracts.*;
 import ama.contract.MinutesContracts.CanonicalBudget;
 import ama.db.TransactionRunner;
 import ama.meeting.*;
 import ama.storage.*;
 import ama.task.*;
 import ama.transcript.*;
 import javax.sql.DataSource;
 import java.util.*;
 /** Explicit TXT-only provider. Every graph collaborator in this process shares the one assembly. */
 public final class TranscriptWorkerRuntimeProvider implements WorkerRuntimeProvider {
  private final DataSource source;private final FileBackendDefinitions definitions;private final ArchiveReadBudget budget;private final CanonicalBudget canonical;
- private final TranscriptImportRuntime.Settings settings;private final String worker;private final int capacity;private final ConfiguredMinutes minutes;
+ private final TranscriptImportRuntime.Settings settings;private final String worker;private final int capacity;private final ConfiguredMinutes minutes;private final ConfiguredPublication publication;
  public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity){
   this(source,definitions,budget,canonical,settings,worker,capacity,null);
  }
  public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity,ConfiguredMinutes minutes){
-  this.minutes=minutes;
+  this(source,definitions,budget,canonical,settings,worker,capacity,minutes,null);
+ }
+ public TranscriptWorkerRuntimeProvider(DataSource source,FileBackendDefinitions definitions,ArchiveReadBudget budget,CanonicalBudget canonical,TranscriptImportRuntime.Settings settings,String worker,int capacity,ConfiguredMinutes minutes,ConfiguredPublication publication){
+  this.minutes=minutes;this.publication=publication;
   this.source=Objects.requireNonNull(source);this.definitions=Objects.requireNonNull(definitions);this.budget=Objects.requireNonNull(budget);this.canonical=Objects.requireNonNull(canonical);this.settings=Objects.requireNonNull(settings);this.worker=worker;this.capacity=capacity;
   if(capacity<1||capacity>64)throw new IllegalArgumentException("Bounded worker capacity required");
  }
  @Override public WorkerRuntime create(){
-  ama.model.ControlledMinutesClient minutesClient=null;
+  ama.model.ControlledMinutesClient minutesClient=null;ControlledKnowledgeClient knowledgeClient=null;WorkerLoop loop=null,external=null;ReconciliationLoop reconciliation=null;
   try{
    // Validate retained archive index before any poll, independent of the current processing default.
    settings.archives().get();
-   var tx=new TransactionRunner(source,minutes==null?-1:30);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
+   var tx=new TransactionRunner(source,minutes==null&&publication==null?-1:30);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
    var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());catalog.configure();
    var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
    new TranscriptEventReferences(source).validators().forEach((k,v)->{if(validators.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate outbox validator");});
    new MinutesEventReferences(source).validators().forEach((k,v)->{if(validators.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Duplicate outbox validator");});
    var outbox=new JdbcOutbox(source,validators);
    var components=JdbcTranscriptAuthority.create(source,tx,access,new TranscriptFilePurposePolicy(),catalog,Map.of(),budget,canonical);
    var queue=TranscriptImportRuntime.install(source,fences,outbox,components);
-   var loop=WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",capacity),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->TranscriptImportRuntime.prepare(queue,lease,settings,cancel)),worker);
-   ReconciliationLoop reconciliation=null;
-   if(minutes!=null){
-    minutes.archives().get();minutesClient=minutes.client();var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,Map.of(ama.model.ControlledMinutesProtocol.MODEL,minutes.modelConfiguration()),(t,m,d)->{throw new IllegalStateException("No controlled cutoff proof");});
-    var preparation=new MinutesExternalPreparation(components.authority(),minutes.preparation(components.authority()),minutesClient);
-    reconciliation=ReconciliationLoop.prepared(operations,permits,minutes.reconciliationCapacity(),new MinutesReconciliationHandler(preparation,minutesClient,operations,permits),worker);
+   loop=WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",capacity),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->TranscriptImportRuntime.prepare(queue,lease,settings,cancel)),worker);
+   if(minutes!=null||publication!=null){
+    var operations=new JdbcExternalOperations(queue);var permits=new JdbcModelPermitPool(operations,minutes==null?Map.of():Map.of(ama.model.ControlledMinutesProtocol.MODEL,minutes.modelConfiguration()),(t,m,d)->{throw new IllegalStateException("No controlled cutoff proof");});
+    var handlers=new HashMap<String,WorkerLoop.ExternalHandler>();var pools=new HashMap<String,Integer>();var models=new HashMap<String,String>();MinutesReconciliationHandler minutesRecovery=null;KnowledgeReconciliationHandler knowledgeRecovery=null;
+    if(minutes!=null){minutes.archives().get();minutesClient=minutes.client();var preparation=new MinutesExternalPreparation(components.authority(),minutes.preparation(components.authority()),minutesClient);minutesRecovery=new MinutesReconciliationHandler(preparation,minutesClient,operations,permits);if(publication!=null){handlers.put("MINUTES_GENERATE_V1",new MinutesGenerationHandler(preparation,minutesClient,operations,permits));pools.put("MINUTES_GENERATE",capacity);models.put("MINUTES_GENERATE_V1",ama.model.ControlledMinutesProtocol.MODEL);}}
+    if(publication!=null){publication.archives().get();knowledgeClient=publication.client();var service=new JdbcPublicationService(components.authority(),publication.archives(),publication.pin(),publication.bounds(),null,queue,outbox,knowledgeClient);handlers.put("KNOWLEDGE_PUBLISH_V1",new KnowledgePublicationHandler(service,operations,permits));pools.put("KNOWLEDGE_PUBLISH",publication.capacity());knowledgeRecovery=new KnowledgeReconciliationHandler(service,operations,permits);}
+    if(!handlers.isEmpty())external=WorkerLoop.external(queue,operations,permits,pools,handlers,models,worker);
+    final var minutesHandler=minutesRecovery;final var knowledgeHandler=knowledgeRecovery;var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);jdbc.setQueryTimeout(2);
+    ReconciliationLoop.PreparedHandler dispatcher=session->{var actual=(ExternalExecutionSession)session;var token=actual.token();String kind=jdbc.queryForObject("SELECT o.operation_kind FROM external_operation o JOIN external_execution e ON e.operation_id=o.id WHERE o.id=? AND e.id=? AND e.execution_no=?",String.class,token.operationId(),token.executionId(),token.executionNo());if("KNOWLEDGE_PUBLISH".equals(kind)&&knowledgeHandler!=null)knowledgeHandler.reconcile(session);else if("MINUTES_GENERATE".equals(kind)&&minutesHandler!=null)minutesHandler.reconcile(session);else throw new IllegalStateException("No installed recovery handler for recorded operation");};
+    int recoveryCapacity=publication==null?minutes.reconciliationCapacity():publication.reconciliationCapacity();reconciliation=publication==null?ReconciliationLoop.prepared(operations,permits,recoveryCapacity,dispatcher,worker):ReconciliationLoop.preparedWithPublicationMaintenance(operations,permits,recoveryCapacity,dispatcher,worker,new JdbcPublicationLocalRecovery(components.authority(),publication.bounds(),queue,outbox));
    }
-   return new Runtime(loop,reconciliation,minutesClient,definitions);
-  }catch(RuntimeException failure){if(minutesClient!=null)minutesClient.close();try{definitions.close();}catch(Exception ignored){}throw failure;}
+   return new Runtime(loop,external,reconciliation,minutesClient,knowledgeClient,definitions);
+  }catch(RuntimeException|Error failure){for(AutoCloseable resource:Arrays.asList(external,reconciliation,loop)){if(resource!=null)try{resource.close();}catch(Exception|Error close){if(close!=failure)failure.addSuppressed(close);}}if(minutesClient!=null)minutesClient.close();if(knowledgeClient!=null)knowledgeClient.close();try{definitions.close();}catch(Exception ignored){}throw failure;}
  }
  public static final class Runtime implements WorkerRuntime {
-  private final WorkerLoop loop;private final ReconciliationLoop reconciliation;private final ama.model.ControlledMinutesClient client;private final FileBackendDefinitions definitions;private boolean closed;
-  private Runtime(WorkerLoop loop,ReconciliationLoop reconciliation,ama.model.ControlledMinutesClient client,FileBackendDefinitions definitions){this.loop=loop;this.reconciliation=reconciliation;this.client=client;this.definitions=definitions;}
-  public void start(){loop.start();if(reconciliation!=null)reconciliation.start();}
+  private final WorkerLoop loop,external;private final ControlledKnowledgeClient knowledgeClient;private final ReconciliationLoop reconciliation;private final ama.model.ControlledMinutesClient client;private final FileBackendDefinitions definitions;private boolean closed;
+  private Runtime(WorkerLoop loop,WorkerLoop external,ReconciliationLoop reconciliation,ama.model.ControlledMinutesClient client,ControlledKnowledgeClient knowledgeClient,FileBackendDefinitions definitions){this.loop=loop;this.external=external;this.reconciliation=reconciliation;this.client=client;this.knowledgeClient=knowledgeClient;this.definitions=definitions;}
+  public void start(){loop.start();if(external!=null)external.start();if(reconciliation!=null)reconciliation.start();}
   public WorkerLoop.Observation observation(){return loop.observation();}
   public synchronized void close(){
    if(closed)return;closed=true;
    Throwable failure=null;
-   try{loop.close();}catch(RuntimeException|Error error){failure=error;}
+   try{if(external!=null)external.close();}catch(RuntimeException|Error error){failure=error;}
+   try{loop.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
    try{if(reconciliation!=null)reconciliation.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
    try{if(client!=null)client.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
+   try{if(knowledgeClient!=null)knowledgeClient.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
    try{definitions.close();}catch(Exception|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
    if(failure instanceof RuntimeException error)throw error;
    if(failure instanceof Error error)throw error;
    if(failure!=null)throw new IllegalStateException("Worker backend close failed",failure);
   }
  }
 }

## tools/providers/controlled_knowledge.py
--- /dev/null
+++ b/tools/providers/controlled_knowledge.py
@@ -0,0 +1,322 @@
+"""Explicit isolated controlled knowledge provider. Durable SQLite effects; no inferred no-effect."""
+import resource
+import argparse,base64,hashlib,hmac,http.server,json,os,pathlib,re,secrets,socket,sqlite3,threading,time,urllib.parse
+from cryptography.hazmat.primitives import serialization
+from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey,Ed25519PublicKey
+MAX_PAYLOAD=8*1024*1024
+MAX_WIRE=MAX_PAYLOAD+65536
+SCHEMA='ama.controlled-kb-delegation.v1'
+
+def canonical(value):
+ # The pinned Java CanonicalJson profile uses lowercase \u00xx for all controls.
+ raw=json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False)
+ controls={r'\b':r'\u0008',r'\f':r'\u000c',r'\n':r'\u000a',r'\r':r'\u000d',r'\t':r'\u0009'}
+ # Match complete escape tokens, so a literal backslash followed by n remains literal.
+ return re.sub(r'\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4})',lambda match:controls.get(match.group(),match.group()),raw).encode('utf-8')
+def digest(value):return hashlib.sha256(value).hexdigest()
+def b64(value):return base64.urlsafe_b64encode(value).decode().rstrip('=')
+def unb64(value):
+ if not isinstance(value,str) or not re.fullmatch('[A-Za-z0-9_-]+',value):raise Denied(403)
+ raw=base64.urlsafe_b64decode(value+'='*((-len(value))%4))
+ if b64(raw)!=value:raise Denied(403)
+ return raw
+class Denied(Exception):
+ def __init__(self,status=403,code='DESTINATION_DENIED'):self.status=status;self.code=code
+
+def strict(raw,limit):
+ if len(raw)>limit:raise Denied(413,'INVALID_PROVIDER_RECEIPT')
+ def pairs(entries):
+  out={}
+  for key,value in entries:
+   if key in out:raise Denied(400,'INVALID_PROVIDER_RECEIPT')
+   out[key]=value
+  return out
+ try:value=json.loads(raw.decode('utf-8','strict'),object_pairs_hook=pairs,parse_float=lambda _:(_ for _ in ()).throw(Denied(400)),parse_constant=lambda _:(_ for _ in ()).throw(Denied(400)))
+ except (ValueError,UnicodeError):raise Denied(400,'INVALID_PROVIDER_RECEIPT')
+ count=0
+ def visit(v,depth):
+  nonlocal count
+  count+=1
+  if depth>16 or count>200000:raise Denied(413,'INVALID_PROVIDER_RECEIPT')
+  if isinstance(v,str):v.encode('utf-8','strict')
+  elif isinstance(v,dict):
+   for k,x in v.items():visit(k,depth+1);visit(x,depth+1)
+  elif isinstance(v,list):
+   for x in v:visit(x,depth+1)
+  elif v is not None and not isinstance(v,(int,bool)):raise Denied(400)
+ visit(value,0);return value
+
+def exact(value,keys):
+ if not isinstance(value,dict) or set(value)!=set(keys.split()):raise Denied(400,'INVALID_PROVIDER_RECEIPT')
+ return value
+
+def validate_publication_binding(r):
+ exact(r,'protocol providerId publicationId confirmationId businessKey logicalRequestHash executionRequestHash executionId executionNo actorId libraryId folderId destinationAuthorization payloadSha256 payload bindingTransition')
+ if r['protocol']!='ama.controlled-knowledge-publish.v1' or r['businessKey']!=r['publicationId']+':'+r['confirmationId']:raise Denied(400)
+ for key in ('publicationId','confirmationId','executionId'):
+  if not isinstance(r[key],str) or not re.fullmatch('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',r[key]):raise Denied(400)
+ for key in ('logicalRequestHash','executionRequestHash','payloadSha256'):
+  if not isinstance(r[key],str) or not re.fullmatch('[0-9a-f]{64}',r[key]):raise Denied(400)
+ for key in ('actorId','libraryId','folderId'):
+  if not isinstance(r[key],str) or not 1<=len(r[key])<=255 or not r[key].strip():raise Denied(400)
+ if type(r['executionNo'])!=int or not 1<=r['executionNo']<2**63-1:raise Denied(400)
+ value=r['payload'];exact(value,'schemaVersion title meeting minutes provenance sections')
+ if value['schemaVersion']!='ama.controlled-knowledge-document.v1':raise Denied(400)
+ provenance=exact(value['provenance'],'confirmationId elementSetId inputFingerprint minutesRevisionId renderArtifact sourceManifestHash transcriptConfirmationId')
+ if provenance['confirmationId']!=r['confirmationId']:raise Denied(400)
+ exact(provenance['renderArtifact'],'version sha256')
+ payload=canonical(value)
+ if len(payload)>MAX_PAYLOAD or digest(payload)!=r['payloadSha256']:raise Denied(400)
+ logical={k:r[k] for k in ('providerId','publicationId','confirmationId','businessKey','libraryId','folderId','payloadSha256')}
+ logical.update(schema='ama.knowledge-publish.v1',domain='KNOWLEDGE_PUBLISH',**{k:provenance[k] for k in ('sourceManifestHash','inputFingerprint','renderArtifact')})
+ if digest(canonical(logical))!=r['logicalRequestHash']:raise Denied(400)
+ transition=r['bindingTransition'];proofs=[]
+ if transition is not None:
+  exact(transition,'schema expectedGeneration priorProofs newBusinessKey newLogicalRequestHash')
+  if transition['schema']!='ama.controlled-kb-binding-transition.v1' or type(transition['expectedGeneration'])!=int or not 1<=transition['expectedGeneration']<2**63-1 or transition['newBusinessKey']!=r['businessKey'] or transition['newLogicalRequestHash']!=r['logicalRequestHash']:raise Denied(400)
+  proofs=transition['priorProofs']
+  if not isinstance(proofs,list) or not 1<=len(proofs)<=256:raise Denied(400)
+  ids=[]
+  for proof in proofs:
+   exact(proof,'executionId executionRequestHash receiptId proofDigest');ids.append(proof['executionId'])
+   if any(not isinstance(proof[k],str) or not re.fullmatch('[0-9a-f]{64}',proof[k]) for k in ('executionRequestHash','proofDigest')) or not isinstance(proof['receiptId'],str) or not re.fullmatch('receipt-[0-9a-f]{32}',proof['receiptId']):raise Denied(400)
+  if ids!=sorted(set(ids)):raise Denied(400)
+ elif r['executionNo']!=1:raise Denied(400)
+ execution={'schema':'ama.knowledge-publish-execution.v1','logicalRequestHash':r['logicalRequestHash'],'executionNo':r['executionNo'],'priorProofs':proofs}
+ if digest(canonical(execution))!=r['executionRequestHash']:raise Denied(400)
+ return payload
+
+def private(path,limit=8192):
+ path=pathlib.Path(path)
+ if path.is_symlink() or not path.is_file() or path.stat().st_mode&0o777!=0o600 or path.stat().st_size>limit:raise RuntimeError('Private controlled credential required')
+ return path.read_bytes()
+
+class Provider:
+ def __init__(self,config,initialize=False):
+  self.ids=exact(config['identities'],'providerId storeInstanceId clientId delegationKeyId assertionKeyId')
+  self.verifier=private(config['bearerVerifier']).decode().strip()
+  self.app_key=serialization.load_der_public_key(private(config['delegationPublic']))
+  self.key=serialization.load_der_private_key(private(config['assertionPrivate']),password=None)
+  if not isinstance(self.app_key,Ed25519PublicKey) or not isinstance(self.key,Ed25519PrivateKey):raise RuntimeError('Exact Ed25519 required')
+  self.lock=threading.RLock();self.path=pathlib.Path(config['store']).resolve()
+  if self.path.is_symlink() or self.path.parent.stat().st_mode&0o777!=0o700:raise RuntimeError('Owned durable store required')
+  exists=self.path.exists()
+  if initialize==exists:raise RuntimeError('Explicit one-time initialization or existing store required')
+  self.db=sqlite3.connect(self.path,timeout=5,isolation_level=None,check_same_thread=False);self.db.row_factory=sqlite3.Row
+  os.chmod(self.path,0o600);self.db.execute('PRAGMA journal_mode=WAL');self.db.execute('PRAGMA synchronous=FULL');self.db.execute('PRAGMA foreign_keys=ON');self.db.execute('PRAGMA busy_timeout=5000')
+  public=self.key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
+  pins={**self.ids,'delegationPublicSha256':digest(self.app_key.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)),'assertionPublicSha256':digest(public)}
+  if initialize:
+   self.db.executescript("""BEGIN IMMEDIATE;
+CREATE TABLE identity(singleton INTEGER PRIMARY KEY CHECK(singleton=1),pins BLOB NOT NULL);
+CREATE TABLE library(id TEXT PRIMARY KEY,name TEXT NOT NULL,root TEXT NOT NULL);
+CREATE TABLE folder(id TEXT PRIMARY KEY,library TEXT NOT NULL,parent TEXT,name TEXT NOT NULL,is_root INTEGER NOT NULL,FOREIGN KEY(library) REFERENCES library(id));
+CREATE TABLE grant_record(actor TEXT NOT NULL,library TEXT NOT NULL,folder TEXT NOT NULL,can_publish INTEGER NOT NULL,can_read INTEGER NOT NULL,version INTEGER NOT NULL,PRIMARY KEY(actor,library,folder));
+CREATE TABLE publication_slot(publication TEXT PRIMARY KEY,generation INTEGER NOT NULL,key TEXT NOT NULL,logical_hash TEXT NOT NULL,document TEXT UNIQUE);
+CREATE TABLE execution(id TEXT PRIMARY KEY,publication TEXT NOT NULL,key TEXT NOT NULL,number INTEGER NOT NULL,client TEXT NOT NULL,actor TEXT NOT NULL,logical_hash TEXT NOT NULL,request_hash TEXT NOT NULL,payload_hash TEXT NOT NULL,request BLOB NOT NULL,receipt BLOB NOT NULL,UNIQUE(key,number));
+CREATE TABLE document(id TEXT PRIMARY KEY,publication TEXT NOT NULL UNIQUE,client TEXT NOT NULL,actor TEXT NOT NULL,library TEXT NOT NULL,folder TEXT NOT NULL,payload_hash TEXT NOT NULL,payload BLOB NOT NULL,result_id TEXT UNIQUE NOT NULL);
+COMMIT;""")
+   self.db.execute('INSERT INTO identity VALUES(1,?)',(canonical(pins),))
+   self.db.execute('INSERT INTO library VALUES(?,?,?)',('controlled-library','Controlled library','controlled-root'))
+   self.db.execute('INSERT INTO folder VALUES(?,?,NULL,?,1)',('controlled-root','controlled-library','Controlled root'))
+  actual=self.db.execute('SELECT pins FROM identity WHERE singleton=1').fetchone()
+  if not actual or bytes(actual[0])!=canonical(pins):raise RuntimeError('Pinned store/key identity mismatch')
+ def administer(self,actor,library,folder,publish,read):
+  with self.lock:
+   if not self.db.execute('SELECT 1 FROM folder WHERE id=? AND library=?',(folder,library)).fetchone():raise RuntimeError('Actual destination required')
+   self.db.execute('INSERT INTO grant_record VALUES(?,?,?,?,?,1) ON CONFLICT(actor,library,folder) DO UPDATE SET can_publish=excluded.can_publish,can_read=excluded.can_read,version=grant_record.version+1',(actor,library,folder,int(publish),int(read)))
+ def sign(self,value):
+  raw=canonical(value);return b64(raw)+'.'+b64(self.key.sign(raw))
+ def verify(self,token,key,schema):
+  if not isinstance(token,str) or len(token)>4096 or token.count('.')!=1:raise Denied()
+  a,b=token.split('.');raw=unb64(a);signature=unb64(b)
+  if len(signature)!=64:raise Denied()
+  try:key.verify(signature,raw)
+  except Exception:raise Denied()
+  value=strict(raw,3072)
+  if canonical(value)!=raw or value.get('schema')!=schema:raise Denied()
+  now=int(time.time());issued=value.get('issuedAt');expires=value.get('expiresAt')
+  if type(issued)!=int or type(expires)!=int or not issued<=expires<=issued+30 or issued>now+2 or expires<now:raise Denied()
+  return value
+ def authorize(self,headers,method,path,body):
+  bearer=headers.get('authorization','')
+  if not bearer.startswith('Bearer ') or not hmac.compare_digest(digest(bearer[7:].encode()),self.verifier):raise Denied(401)
+  claims=self.verify(headers.get('x-controlled-delegation'),self.app_key,SCHEMA)
+  exact(claims,'schema issuer keyId audience storeInstanceId actorId purpose method resource issuedAt expiresAt nonce requestDigest')
+  purposes={'/v1/knowledge/destination-authorizations':'DESTINATION_AUTHORIZE','/v1/knowledge/publications':'PUBLISH'}
+  purpose=purposes.get(path) if method=='POST' else 'DESTINATION_READ' if path.startswith('/v1/knowledge/libraries') else 'DOCUMENT_READ' if path.startswith('/v1/knowledge/documents/') else 'RECEIPT_LOOKUP'
+  expected={**{'issuer':self.ids['clientId'],'keyId':self.ids['delegationKeyId'],'audience':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'method':method,'resource':path,'purpose':purpose,'requestDigest':digest(body if method=='POST' else canonical({'method':method,'resource':path}))}}
+  if any(claims.get(k)!=v for k,v in expected.items()) or not isinstance(claims['actorId'],str) or not 1<=len(claims['actorId'])<=255 or not re.fullmatch('[A-Za-z0-9_-]{16,64}',claims['nonce']):raise Denied()
+  return claims
+ def grant(self,actor,library,folder,read=False):
+  row=self.db.execute('SELECT g.* FROM grant_record g JOIN folder f ON f.id=g.folder AND f.library=g.library WHERE g.actor=? AND g.library=? AND g.folder=?',(actor,library,folder)).fetchone()
+  if not row or not row['can_read' if read else 'can_publish']:raise Denied()
+  return row
+ def receipt(self,request,state,**extra):return {**{k:request[k] for k in ('providerId','publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256')},'protocol':'ama.controlled-knowledge-receipt.v1','storeInstanceId':self.ids['storeInstanceId'],'clientId':self.ids['clientId'],'state':state,**extra}
+ def serve(self,method,path,headers,raw):
+  call=self.authorize(headers,method,path,raw);actor=call['actorId'];body=strict(raw,MAX_WIRE) if method=='POST' else None
+  if body is not None and body.get('actorId')!=actor:raise Denied()
+  with self.lock:
+   if method=='POST' and path=='/v1/knowledge/destination-authorizations':
+    exact(body,'actorId providerId libraryId folderId context')
+    if body['providerId']!=self.ids['providerId']:raise Denied()
+    grant=self.grant(actor,body['libraryId'],body['folderId']);now=int(time.time())
+    assertion={'schema':'ama.controlled-kb-destination.v1',**self.ids,'actorId':actor,'libraryId':body['libraryId'],'folderId':body['folderId'],'context':body['context'],'authVersion':grant['version'],'issuedAt':now,'expiresAt':now+30}
+    return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'assertion':self.sign(assertion)}
+   if method=='GET' and path.startswith('/v1/knowledge/libraries'):
+    parsed=urllib.parse.urlsplit(path);query=urllib.parse.parse_qs(parsed.query,strict_parsing=True);limit=int(query.get('limit',['20'])[0]);cursor=query.get('cursor',[''])[0]
+    if not 1<=limit<=50 or any(len(v)!=1 for v in query.values()):raise Denied(400)
+    parts=parsed.path.split('/')
+    if parsed.path=='/v1/knowledge/libraries':
+     if set(query)-{'limit','cursor'}:raise Denied(400)
+     rows=self.db.execute('SELECT DISTINCT l.id,l.name,l.root rootFolderId FROM library l JOIN grant_record g ON g.library=l.id WHERE g.actor=? AND g.can_publish=1 AND l.id>? ORDER BY l.id LIMIT ?',(actor,cursor,limit+1)).fetchall()
+    elif len(parts)==6 and parts[5]=='folders':
+     if set(query)-{'limit','cursor','parentFolderId'}:raise Denied(400)
+     parent=query.get('parentFolderId',[None])[0];library=parts[4]
+     rows=self.db.execute('SELECT f.id,f.library libraryId,f.parent parentFolderId,f.is_root isRoot,f.name FROM folder f JOIN grant_record g ON g.library=f.library AND g.folder=f.id WHERE g.actor=? AND g.can_publish=1 AND f.library=? AND (? IS NULL AND f.is_root=1 OR f.parent=?) AND f.id>? ORDER BY f.id LIMIT ?',(actor,library,parent,parent,cursor,limit+1)).fetchall()
+    else:raise Denied(404)
+    more=len(rows)>limit;items=[dict(r) for r in rows[:limit]];return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'items':items,'cursor':items[-1]['id'] if more else None}
+   if method=='POST' and path=='/v1/knowledge/publications':return self.publish(body,actor)
+   if method=='GET' and '/executions/' in path:
+    parts=path.split('/')
+    if len(parts)!=7 or parts[1:4]!=['v1','knowledge','publications'] or parts[5]!='executions':raise Denied(404)
+    row=self.db.execute('SELECT * FROM execution WHERE publication=? AND id=?',(parts[4],parts[6])).fetchone()
+    if not row:return {'protocol':'ama.controlled-knowledge-receipt.v1',**self.ids,'publicationId':parts[4],'executionId':parts[6],'state':'UNKNOWN'}
+    if row['client']!=self.ids['clientId'] or row['actor']!=actor:raise Denied()
+    return strict(row['receipt'],65536)
+   if method=='GET' and path.startswith('/v1/knowledge/publications/by-business-key/'):
+    key=path.split('/')[-1];row=self.db.execute('SELECT * FROM execution WHERE key=? ORDER BY number DESC LIMIT 1',(key,)).fetchone()
+    if not row:return {'protocol':'ama.controlled-knowledge-receipt.v1',**self.ids,'businessKey':key,'state':'UNKNOWN'}
+    if row['client']!=self.ids['clientId'] or row['actor']!=actor:raise Denied()
+    return strict(row['receipt'],65536)
+   if method=='GET' and (path.startswith('/v1/knowledge/results/') or path.startswith('/v1/knowledge/documents/')):
+    document=path.startswith('/v1/knowledge/documents/');id=path.split('/')[-1];row=self.db.execute('SELECT * FROM document WHERE '+('id' if document else 'result_id')+'=?',(id,)).fetchone()
+    if not row:raise Denied(404)
+    if row['client']!=self.ids['clientId']:raise Denied()
+    if document:self.grant(actor,row['library'],row['folder'],True)
+    elif row['actor']!=actor:raise Denied()
+    result={'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'documentId':row['id'],'resultId':row['result_id'],'publicationId':row['publication'],'payloadSha256':row['payload_hash']}
+    if document:result['payload']=strict(row['payload'],MAX_PAYLOAD)
+    return result
+   raise Denied(404)
+ def permanent_receipt(self,receipt):
+  token=receipt.get('permanentRejection','')
+  if not isinstance(token,str) or len(token)>8192 or token.count('.')!=1:raise Denied(409,'PROVIDER_CONFLICT')
+  a,b=token.split('.');raw=unb64(a);signature=unb64(b)
+  try:self.key.public_key().verify(signature,raw)
+  except Exception:raise Denied(409,'PROVIDER_CONFLICT')
+  proof=strict(raw,4096);expected={'schema':'ama.controlled-kb-permanent-rejection.v1',**self.ids,**{k:receipt[k] for k in ('publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256','receiptId','verifiedAt','slotGeneration','noEffectBasis','errorCode')}}
+  if canonical(proof)!=raw or proof!=expected or proof['noEffectBasis']!='DEFINITIVE_REJECTION' or proof['errorCode']!='DESTINATION_DENIED':raise Denied(409,'PROVIDER_CONFLICT')
+  return proof
+ def transition_generation(self,r,slot,predecessors,maximum):
+  # Caller has authenticated the immutable request and destination under BEGIN IMMEDIATE.
+  # This pure validator additionally recomputes the exact new binding; gaps never invent receipts.
+  validate_publication_binding(r)
+  transition=r['bindingTransition']
+  if slot['document'] is not None or transition is None or transition['expectedGeneration']!=slot['generation']:raise Denied(409,'PROVIDER_CONFLICT')
+  if len(predecessors)>256 or len(predecessors)!=len(transition['priorProofs']):raise Denied(409,'PROVIDER_CONFLICT')
+  for previous,expected in zip(predecessors,transition['priorProofs']):
+   receipt=strict(previous['receipt'],65536);self.permanent_receipt(receipt)
+   if receipt.get('state')!='FAILED' or receipt.get('publicationId')!=r['publicationId'] or receipt.get('noEffectBasis')!='DEFINITIVE_REJECTION' or expected!={'executionId':previous['id'],'executionRequestHash':previous['request_hash'],'receiptId':receipt.get('receiptId'),'proofDigest':digest(receipt.get('permanentRejection','').encode())}:raise Denied(409,'PROVIDER_CONFLICT')
+  # Actual accepted rows plus UNIQUE(key,number) forbid number reuse. An authenticated app
+  # may skip only unseen numbers; full real predecessor proof and slot CAS remain mandatory.
+  if type(maximum)!=int or maximum<0 or r['executionNo']<=maximum or slot['generation']>=2**63-2:raise Denied(409,'PROVIDER_CONFLICT')
+  return slot['generation']+1
+ def publish(self,r,actor):
+  payload=validate_publication_binding(r)
+  if r['providerId']!=self.ids['providerId'] or r['actorId']!=actor:raise Denied(400)
+  immutable={k:v for k,v in r.items() if k!='destinationAuthorization'};exact_request=canonical(immutable)
+  self.db.execute('BEGIN IMMEDIATE')
+  try:
+   old=self.db.execute('SELECT * FROM execution WHERE id=?',(r['executionId'],)).fetchone()
+   if old:
+    if old['request']!=exact_request:raise Denied(409,'PROVIDER_CONFLICT')
+    result=strict(old['receipt'],65536);self.db.execute('COMMIT');return result
+   # Immutable replay above precedes destination expiry/revocation; it can never create another effect.
+   assertion=self.verify(r['destinationAuthorization'],self.key.public_key(),'ama.controlled-kb-destination.v1')
+   if any(assertion.get(k)!=v for k,v in self.ids.items()) or any(assertion.get(k)!=r[k] for k in ('actorId','libraryId','folderId')) or assertion.get('context')!=r['publicationId']:raise Denied()
+   slot=self.db.execute('SELECT * FROM publication_slot WHERE publication=?',(r['publicationId'],)).fetchone()
+   if slot:
+    predecessors=self.db.execute('SELECT id,request_hash,receipt FROM execution WHERE publication=? ORDER BY id LIMIT 257',(r['publicationId'],)).fetchall()
+    maximum=self.db.execute('SELECT COALESCE(MAX(number),0) FROM execution WHERE key=?',(r['businessKey'],)).fetchone()[0]
+    generation=self.transition_generation(r,slot,predecessors,maximum)
+    if self.db.execute('UPDATE publication_slot SET generation=?,key=?,logical_hash=? WHERE publication=? AND generation=? AND document IS NULL',(generation,r['businessKey'],r['logicalRequestHash'],r['publicationId'],slot['generation'])).rowcount!=1:raise Denied(409,'PROVIDER_CONFLICT')
+   else:
+    if r['bindingTransition'] is not None or r['executionNo']!=1:raise Denied(409,'PROVIDER_CONFLICT')
+    generation=1;self.db.execute('INSERT INTO publication_slot VALUES(?,1,?,?,NULL)',(r['publicationId'],r['businessKey'],r['logicalRequestHash']))
+   try:
+    grant=self.grant(actor,r['libraryId'],r['folderId'])
+    if grant['version']!=assertion.get('authVersion'):raise Denied()
+   except Denied:
+    # A valid authenticated exact request is durably and permanently rejected before any document write.
+    receipt=self.receipt(r,'FAILED',receiptId='receipt-'+secrets.token_hex(16),verifiedAt=int(time.time()),slotGeneration=generation,noEffectBasis='DEFINITIVE_REJECTION',errorCode='DESTINATION_DENIED')
+    proof={'schema':'ama.controlled-kb-permanent-rejection.v1',**self.ids,**{k:receipt[k] for k in ('publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256','receiptId','verifiedAt','slotGeneration','noEffectBasis','errorCode')}}
+    receipt['permanentRejection']=self.sign(proof)
+    self.db.execute('INSERT INTO execution VALUES(?,?,?,?,?,?,?,?,?,?,?)',(r['executionId'],r['publicationId'],r['businessKey'],r['executionNo'],self.ids['clientId'],actor,r['logicalRequestHash'],r['executionRequestHash'],r['payloadSha256'],exact_request,canonical(receipt)))
+    self.db.execute('COMMIT');return receipt
+   document='doc-'+secrets.token_hex(16);result_id='result-'+secrets.token_hex(16)
+   self.db.execute('INSERT INTO document VALUES(?,?,?,?,?,?,?,?,?)',(document,r['publicationId'],self.ids['clientId'],actor,r['libraryId'],r['folderId'],r['payloadSha256'],payload,result_id))
+   self.db.execute('UPDATE publication_slot SET document=? WHERE publication=? AND document IS NULL',(document,r['publicationId']))
+   receipt=self.receipt(r,'SUCCESS',documentId=document,resultId=result_id,receiptId='receipt-'+secrets.token_hex(16),verifiedAt=int(time.time()),slotGeneration=generation)
+   self.db.execute('INSERT INTO execution VALUES(?,?,?,?,?,?,?,?,?,?,?)',(r['executionId'],r['publicationId'],r['businessKey'],r['executionNo'],self.ids['clientId'],actor,r['logicalRequestHash'],r['executionRequestHash'],r['payloadSha256'],exact_request,canonical(receipt)))
+   self.db.execute('COMMIT');return receipt
+  except BaseException:self.db.execute('ROLLBACK');raise
+
+class Handler(http.server.BaseHTTPRequestHandler):
+ protocol_version='HTTP/1.1';server_version='ControlledKnowledge';sys_version=''
+ def log_message(self,*args):pass
+ def do_POST(self):self.handle_controlled()
+ def do_GET(self):self.handle_controlled()
+ def handle_controlled(self):
+  timer=threading.Timer(15,self.abort);timer.daemon=True;timer.start()
+  try:
+   if len(self.path)>2048 or len(self.headers)>32 or sum(len(k)+len(v)+4 for k,v in self.headers.items())>8192:raise Denied(431)
+   headers={}
+   for k,v in self.headers.items():
+    k=k.lower()
+    if k in headers or any(ord(c)<32 or ord(c)>126 for c in k+v):raise Denied(400)
+    headers[k]=v
+   if headers.get('host')!='127.0.0.1:'+str(self.server.server_port) or any(k in headers for k in ('transfer-encoding','content-encoding','expect')):raise Denied(400)
+   length=headers.get('content-length','0')
+   if not re.fullmatch('0|[1-9][0-9]{0,8}',length) or int(length)>MAX_WIRE or self.command=='GET' and length!='0':raise Denied(413)
+   if self.command=='POST' and headers.get('content-type')!='application/json':raise Denied(415)
+   self.connection.settimeout(5);body=self.rfile.read(int(length))
+   if len(body)!=int(length):raise Denied(400)
+   value=self.server.provider.serve(self.command,self.path,headers,body);raw=canonical(value)
+   if len(raw)>(MAX_WIRE if '/documents/' in self.path else 262144 if '/libraries' in self.path else 65536):raise Denied(413)
+   self.respond(200,raw)
+  except Denied as denied:self.respond(denied.status,canonical({'error':denied.code}))
+  except (BrokenPipeError,ConnectionResetError,TimeoutError,socket.timeout):pass
+  except Exception:self.respond(503,canonical({'error':'PROVIDER_UNAVAILABLE'}))
+  finally:timer.cancel();self.close_connection=True
+ def abort(self):
+  try:self.connection.shutdown(socket.SHUT_RDWR)
+  except OSError:pass
+  self.connection.close()
+ def respond(self,status,body):
+  self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.send_header('Connection','close');self.end_headers();self.wfile.write(body)
+class Server(http.server.ThreadingHTTPServer):
+ daemon_threads=True;request_queue_size=4
+ def __init__(self,bind,provider):super().__init__(bind,Handler);self.provider=provider;self.capacity=threading.BoundedSemaphore(2)
+ def process_request(self,request,address):
+  if not self.capacity.acquire(False):request.close();return
+  super().process_request(request,address)
+ def process_request_thread(self,*args):
+  try:super().process_request_thread(*args)
+  finally:self.capacity.release()
+
+def main():
+ parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);parser.add_argument('--initialize',action='store_true');parser.add_argument('--grant-actor');parser.add_argument('--grant-read',action='store_true');parser.add_argument('--grant-publish',action='store_true');args=parser.parse_args()
+ config=strict(private(args.config),8192)
+ if config['mode']!='ISOLATED_LOOPBACK_TEST' or config['bind']!='127.0.0.1':raise RuntimeError('Explicit isolated loopback test profile required')
+ threading.stack_size(262144)
+ os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2])
+ resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864))
+ provider=Provider(config,args.initialize)
+ if args.grant_actor:
+  provider.administer(args.grant_actor,'controlled-library','controlled-root',args.grant_publish,args.grant_read);provider.db.close();return
+ server=Server(('127.0.0.1',int(config['port'])),provider)
+ ready=pathlib.Path(config['readyFile']);ready.write_bytes(canonical({'port':server.server_port,'identities':provider.ids,'store':str(provider.path),'pid':os.getpid(),'pgid':os.getpgid(0)}));os.chmod(ready,0o600)
+ try:server.serve_forever(poll_interval=.2)
+ finally:server.server_close();provider.db.close()
+if __name__=='__main__':main()

## tools/providers/knowledge_response_loss.py
--- /dev/null
+++ b/tools/providers/knowledge_response_loss.py
@@ -0,0 +1,60 @@
+"""Owned test relay: one durable provider success response is withheld; never generates receipts."""
+import argparse,concurrent.futures,hashlib,json,os,pathlib,resource,socket,threading,time,subprocess
+
+def atomic(path,value):
+ p=pathlib.Path(path);tmp=p.with_suffix('.tmp');data=json.dumps(value,sort_keys=True).encode()
+ fd=os.open(tmp,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
+ with os.fdopen(fd,'wb') as stream:stream.write(data);stream.flush();os.fsync(stream.fileno())
+ os.replace(tmp,p)
+
+def main():
+ parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);args=parser.parse_args();p=pathlib.Path(args.config)
+ if p.is_symlink() or p.stat().st_mode&0o777!=0o600:raise RuntimeError('Private owned config required')
+ cfg=json.loads(p.read_text());threading.stack_size(262144);resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864));cpus=sorted(os.sched_getaffinity(0));os.sched_setaffinity(0,set(cpus[:2]))
+ state={'pid':os.getpid(),'pgid':os.getpgrp(),'port':cfg['port'],'upstream':cfg['upstream'],'postPublication':0,'postAuthorization':0,'get':0,'withheld':False,'failures':0};lock=threading.Lock()
+ def serve(incoming):
+  try:
+   with incoming,socket.create_connection(('127.0.0.1',cfg['upstream']),timeout=5) as target:
+    incoming.settimeout(5);target.settimeout(5);head=bytearray()
+    while not head.endswith(b'\r\n\r\n'):
+     v=incoming.recv(1)
+     if not v or len(head)>=16384:raise RuntimeError('Bounded header')
+     head.extend(v)
+    lines=bytes(head).decode('ascii').split('\r\n');method,path,_=lines[0].split(' ');length=0
+    for line in lines[1:]:
+     if line.lower().startswith('content-length:'):length=int(line.split(':',1)[1])
+    if not 0<=length<=8454144:raise RuntimeError('Bounded body')
+    body=bytearray()
+    while len(body)<length:
+     part=incoming.recv(min(65536,length-len(body)))
+     if not part:raise RuntimeError('Short body')
+     body.extend(part)
+    if cfg.get('mode','LOSS')=='REVOKE_BEFORE_PUBLISH' and method=='POST' and path=='/v1/knowledge/publications':
+     # Operator fixture transition happens before the real POST, never inside application code.
+     result=subprocess.run(['/usr/bin/python3',cfg['providerScript'],'--config',cfg['providerConfig'],'--grant-actor',cfg['actor'],'--grant-read'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=10)
+     if result.returncode!=0:raise RuntimeError('Actual grant revocation failed')
+    forwarded=bytes(head).replace(('Host: 127.0.0.1:'+str(cfg['port'])).encode(),('Host: 127.0.0.1:'+str(cfg['upstream'])).encode());target.sendall(forwarded);target.sendall(body);response=bytearray()
+    while True:
+     part=target.recv(65536)
+     if not part:break
+     response.extend(part)
+     if len(response)>8486912:raise RuntimeError('Bounded response')
+    with lock:
+     if method=='POST' and path=='/v1/knowledge/publications':
+      state['postPublication']+=1
+      if cfg.get('mode','LOSS')=='LOSS' and not state['withheld']:
+       h,raw=bytes(response).split(b'\r\n\r\n',1);receipt=json.loads(raw)
+       if h.split(b' ')[1]!=b'200' or receipt.get('state')!='SUCCESS':raise RuntimeError('Actual success required before loss')
+       state['withheld']=True;state['receipt']=receipt;state['responseSha256']=hashlib.sha256(response).hexdigest();state['withheldUtc']=time.time();atomic(cfg['state'],state);return
+      if cfg.get('mode')=='REVOKE_BEFORE_PUBLISH':
+       h,raw=bytes(response).split(b'\r\n\r\n',1);state['receipt']=json.loads(raw);state['responseSha256']=hashlib.sha256(response).hexdigest()
+     elif method=='POST':state['postAuthorization']+=1
+     elif method=='GET':state['get']+=1
+     atomic(cfg['state'],state)
+    incoming.sendall(response)
+  except Exception:
+   with lock:state['failures']+=1;atomic(cfg['state'],state)
+ listener=socket.socket();listener.bind(('127.0.0.1',cfg['port']));listener.listen(2);atomic(cfg['state'],state)
+ with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
+  while True:incoming,_=listener.accept();executor.submit(serve,incoming)
+if __name__=='__main__':main()

## tools/providers/test_knowledge_binding.py
--- /dev/null
+++ b/tools/providers/test_knowledge_binding.py
@@ -0,0 +1,19 @@
+import copy,hashlib,importlib.util,json,pathlib,unittest
+ROOT=pathlib.Path(__file__).resolve().parents[2]
+spec=importlib.util.spec_from_file_location('kb',ROOT/'tools/providers/controlled_knowledge.py');kb=importlib.util.module_from_spec(spec);spec.loader.exec_module(kb)
+class Binding(unittest.TestCase):
+ def setUp(self):
+  value=json.loads((ROOT/'artifacts/phase-b/b-remaining/task-11/knowledge-canonical-cross-01/renderer-client-java-actual.json').read_text())['value']
+  self.request={'protocol':'ama.controlled-knowledge-publish.v1','providerId':'CONTROLLED_KNOWLEDGE_V1','publicationId':'00000000-0000-4000-8000-000000000030','confirmationId':value['provenance']['confirmationId'],'businessKey':'00000000-0000-4000-8000-000000000030:'+value['provenance']['confirmationId'],'executionId':'00000000-0000-4000-8000-000000000040','executionNo':1,'actorId':'public-test-actor','libraryId':'library','folderId':'folder','destinationAuthorization':'public-unit-placeholder','payloadSha256':kb.digest(kb.canonical(value)),'payload':value,'bindingTransition':None}
+  r=self.request;logical={k:r[k] for k in ('providerId','publicationId','confirmationId','businessKey','libraryId','folderId','payloadSha256')};logical.update(schema='ama.knowledge-publish.v1',domain='KNOWLEDGE_PUBLISH',**{k:value['provenance'][k] for k in ('sourceManifestHash','inputFingerprint','renderArtifact')});r['logicalRequestHash']=kb.digest(kb.canonical(logical));r['executionRequestHash']=kb.digest(kb.canonical({'schema':'ama.knowledge-publish-execution.v1','logicalRequestHash':r['logicalRequestHash'],'executionNo':1,'priorProofs':[]}))
+ def test_original_actual_java_payload(self):self.assertEqual(kb.canonical(self.request['payload']),kb.validate_publication_binding(self.request))
+ def test_tampered_logical(self):self.request['logicalRequestHash']='f'*64;self.denied()
+ def test_tampered_execution(self):self.request['executionRequestHash']='f'*64;self.denied()
+ def test_tampered_payload(self):self.request['payload']['meeting']['name']='changed';self.denied()
+ def test_cross_final(self):self.request['confirmationId']='00000000-0000-4000-8000-000000000099';self.denied()
+ def test_wrong_key(self):self.request['businessKey']='other';self.denied()
+ def test_unjustified_execution_number(self):self.request['executionNo']=2;self.denied()
+ def test_unknown_field(self):self.request['extra']=True;self.denied()
+ def denied(self):
+  with self.assertRaises(kb.Denied):kb.validate_publication_binding(self.request)
+if __name__=='__main__':unittest.main()

## tools/providers/test_knowledge_transition.py
--- /dev/null
+++ b/tools/providers/test_knowledge_transition.py
@@ -0,0 +1,27 @@
+import copy,unittest
+import test_knowledge_binding as binding
+kb=binding.kb
+class Transition(unittest.TestCase):
+ def setUp(self):
+  binding.Binding.setUp(self);self.provider=kb.Provider.__new__(kb.Provider);self.provider.ids={'providerId':'CONTROLLED_KNOWLEDGE_V1','storeInstanceId':'public-test-store','clientId':'public-test-client','delegationKeyId':'public-test-delegation','assertionKeyId':'public-test-assertion'}
+  # Published RFC8032 vector, never a real fixture credential.
+  self.provider.key=kb.Ed25519PrivateKey.from_private_bytes(bytes.fromhex('9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60'))
+  receipt=self.provider.receipt(self.request,'FAILED',receiptId='receipt-'+'1'*32,verifiedAt=1,slotGeneration=1,noEffectBasis='DEFINITIVE_REJECTION',errorCode='DESTINATION_DENIED')
+  claims={'schema':'ama.controlled-kb-permanent-rejection.v1',**self.provider.ids,**{k:receipt[k] for k in ('publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256','receiptId','verifiedAt','slotGeneration','noEffectBasis','errorCode')}};receipt['permanentRejection']=self.provider.sign(claims)
+  self.predecessors=[{'id':self.request['executionId'],'request_hash':self.request['executionRequestHash'],'receipt':kb.canonical(receipt)}];self.slot={'generation':1,'document':None};self.next=copy.deepcopy(self.request);self.next['executionId']='00000000-0000-4000-8000-000000000042';self.next['executionNo']=3
+  self.next['bindingTransition']={'schema':'ama.controlled-kb-binding-transition.v1','expectedGeneration':1,'priorProofs':[{'executionId':self.request['executionId'],'executionRequestHash':self.request['executionRequestHash'],'receiptId':receipt['receiptId'],'proofDigest':kb.digest(receipt['permanentRejection'].encode())}],'newBusinessKey':self.request['businessKey'],'newLogicalRequestHash':self.request['logicalRequestHash']};self.rehash()
+ def rehash(self):self.next['executionRequestHash']=kb.digest(kb.canonical({'schema':'ama.knowledge-publish-execution.v1','logicalRequestHash':self.next['logicalRequestHash'],'executionNo':self.next['executionNo'],'priorProofs':self.next['bindingTransition']['priorProofs']}))
+ def check(self):return self.provider.transition_generation(self.next,self.slot,self.predecessors,1)
+ def test_gap_preserves_only_real_receipts(self):self.assertEqual(2,self.check());self.assertEqual(1,len(self.predecessors));self.assertEqual(1,len(self.next['bindingTransition']['priorProofs']))
+ def test_used_number_cannot_rebind(self):self.next['executionNo']=1;self.rehash();self.reject_transition()
+ def test_stale_generation(self):self.next['bindingTransition']['expectedGeneration']=2;self.reject_transition()
+ def test_document_blocks(self):self.slot['document']='doc-existing';self.reject_transition()
+ def test_missing_predecessor(self):self.next['bindingTransition']['priorProofs']=[];self.rehash();self.reject_transition()
+ def test_invented_unseen_predecessor(self):self.next['bindingTransition']['priorProofs'].append({**self.next['bindingTransition']['priorProofs'][0],'executionId':'00000000-0000-4000-8000-000000000041'});self.rehash();self.reject_transition()
+ def test_wrong_receipt_digest(self):self.next['bindingTransition']['priorProofs'][0]['proofDigest']='f'*64;self.rehash();self.reject_transition()
+ def test_tampered_new_execution_binding(self):self.next['executionRequestHash']='f'*64;self.reject_transition()
+ def test_wrong_new_key(self):self.next['bindingTransition']['newBusinessKey']='wrong';self.reject_transition()
+ def test_permanent_signature_required(self):receipt=kb.strict(self.predecessors[0]['receipt'],65536);receipt['permanentRejection']=receipt['permanentRejection'][:-3]+'abc';self.predecessors[0]['receipt']=kb.canonical(receipt);self.reject_transition()
+ def reject_transition(self):
+  with self.assertRaises(kb.Denied):self.check()
+if __name__=='__main__':unittest.main()

## tools/task11_window.py
--- /dev/null
+++ b/tools/task11_window.py
@@ -0,0 +1,82 @@
+"""Task11 exact-source serial test windows. Only the explicitly ACKed fresh schema; no provisioning."""
+import base64,datetime,hashlib,json,os,pathlib,re,shlex,shutil,stat,subprocess,sys,time,xml.etree.ElementTree as ET
+ROOT=pathlib.Path(__file__).resolve().parents[1]
+W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
+OUT=ROOT/'artifacts/phase-b/b-remaining/task-11'
+ACK='dcab31da53de0a4e065aa05914f5143a5afd68ddbd785a87617455db1aa1574d'
+def digest(b):return hashlib.sha256(b).hexdigest()
+def main():
+ label,group,selector=sys.argv[1:]
+ if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:#[A-Za-z][A-Za-z0-9_]*)?',selector) or not re.fullmatch(r'[a-z0-9-]+',label) or group not in ('it','unit'):raise SystemExit('Invalid bounded window')
+ ackbytes=(W/'task-11-root-fixture-application-ack-20261025.json').read_bytes();assert digest(ackbytes)==ACK
+ ack=json.loads(ackbytes);private=ROOT/ack['privateEnv'];assert stat.S_IMODE(private.stat().st_mode)==0o600 and stat.S_IMODE(private.parent.stat().st_mode)==0o700
+ env=os.environ.copy()
+ env['JAVA_HOME']='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64'
+ assert pathlib.Path(env['JAVA_HOME'],'bin/java').is_file()
+ env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
+ for line in private.read_text().splitlines():
+  if not line.strip() or line.lstrip().startswith('#'):continue
+  match=re.fullmatch(r'(?:export )?([A-Z][A-Z0-9_]*)=(.*)',line);assert match
+  values=shlex.split(match.group(2));assert len(values)==1;env[match.group(1)]=values[0]
+ assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:46151/ama_t11_9714255c9e2c_behavior'
+ target=OUT/label;target.mkdir(exist_ok=False)
+ env.update(JAVA_TOOL_OPTIONS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_CONFIG='-Dtest=Task11NoImplicitUnitSuite -Dsurefire.failIfNoSpecifiedTests=false',AMA_TASK11_EVIDENCE_WINDOW=str(target),AMA_TASK11_REPOSITORY_ROOT=str(ROOT))
+ if group=='unit':env.pop('MAVEN_CONFIG',None)
+ for suffix in ('OWNER','RUN_ROOT','S3_ENV','PKI_ROOT'):env['AMA_TASK09_'+suffix]=env['AMA_TASK11_'+suffix]
+ env['AMA_TASK09_EVIDENCE_WINDOW']=str(target)
+ modules=[ROOT/'backend/integration-tests'] if group=='it' else sorted(p.parent for p in ROOT.glob('backend/*/pom.xml'))
+ matched=[p for m in modules for p in (m/'src/test/java').rglob(selector.split('#',1)[0]+'.java')]
+ assert len(matched)==1,'Selector must identify exactly one discovered class'
+ canonical=re.search(r'package\s+([^;]+);',matched[0].read_text()).group(1).strip()+'.'+matched[0].stem
+ secret_values=[v.encode() for k,v in env.items() if k.startswith('AMA_') and any(x in k for x in ('PASSWORD','SECRET','TOKEN'))]
+ credential_ack=json.loads((W/'task-11-knowledge-credential-root-ack-20261025.json').read_text())
+ for name in credential_ack['privateScanInputs']:
+  value=pathlib.Path(name).read_bytes();encoded=base64.b64encode(value);url=base64.urlsafe_b64encode(value)
+  secret_values.extend([value,value.hex().encode(),encoded,encoded.rstrip(b'='),url,url.rstrip(b'=')])
+  if name.endswith(('.pk8','.spki')):
+   kind=b'PRIVATE KEY' if name.endswith('.pk8') else b'PUBLIC KEY'
+   secret_values.append(b'-----BEGIN '+kind+b'-----\n'+b'\n'.join(encoded[i:i+64] for i in range(0,len(encoded),64))+b'\n-----END '+kind+b'-----')
+
+ report_glob='backend/*/target/'+('failsafe-reports' if group=='it' else 'surefire-reports')+'/TEST-*.xml'
+ previous={str(f):(f.stat().st_mtime_ns,digest(f.read_bytes())) for f in ROOT.glob(report_glob)}
+ (target/'reports-before.json').write_text(json.dumps(previous,indent=2)+'\n')
+ for f in ROOT.glob(report_glob):
+  assert f.resolve().is_relative_to((ROOT/'backend').resolve()) and 'target' in f.parts
+  f.unlink()
+ assert not list(ROOT.glob(report_glob))
+ (target/'reports-cleared.json').write_text(json.dumps({'paths':list(previous),'verifiedAbsent':True,'atNs':time.time_ns()},indent=2)+'\n')
+ candidates=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','--','backend','contracts/openapi','tools/checks/run.py','tools/checks/database_design.py','tools/task11_window.py','tools/providers'],cwd=ROOT,text=True).splitlines()
+ manifest=[];head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
+ changed=set(subprocess.check_output(['git','ls-files','--modified','--others','--exclude-standard','--','backend','contracts/openapi','tools'],cwd=ROOT,text=True).splitlines())
+ for name in sorted(set(candidates)):
+  path=ROOT/name
+  if not path.is_file() or '/target/' in name:continue
+  b=path.read_bytes();manifest.append({'path':name,'bytes':len(b),'sha256':digest(b)})
+  if name in changed:
+   saved=target/'source'/name;saved.parent.mkdir(parents=True,exist_ok=True);saved.write_bytes(b)
+ (target/'source-manifest.json').write_text(json.dumps({'base':head,'files':manifest},indent=2)+'\n')
+ def sample(where):
+  cmd=['docker','exec',ack['cid'],'sh','-c','for f in memory.current memory.peak memory.max memory.events; do echo "$f"; cat "/sys/fs/cgroup/$f"; done']
+  r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(target/('resource-'+where+'.txt')).write_text(r.stdout);assert r.returncode==0
+ sample('before');started=time.time_ns();command=[sys.executable,'tools/checks/run.py',group,selector]
+ (target/'command.json').write_text(json.dumps({'command':command,'canonicalSelector':canonical,'reportsBeforeSha256':digest((target/'reports-before.json').read_bytes()),'reportsClearedSha256':digest((target/'reports-cleared.json').read_bytes()),'cwd':str(ROOT),'startedNs':started,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'heapMiB':512,'processors':2,'schema':ack['schema'],'ackSha256':ACK,'sourceManifestSha256':digest((target/'source-manifest.json').read_bytes())},indent=2)+'\n')
+ with (target/'run.log').open('w') as log:result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
+ finished=time.time_ns();sample('after');counts=[];cases=[];reports=[]
+ log_text=(target/'run.log').read_text(errors='replace')
+ for report in sorted(ROOT.glob(report_glob)):
+  report_stat=report.stat();raw=report.read_bytes();sha=digest(raw);prior=previous.get(str(report));tree=ET.fromstring(raw);suite=tree.get('name','')
+  qualified=started<=report_stat.st_mtime_ns<=finished and prior!=(report_stat.st_mtime_ns,sha) and ('Running '+suite) in log_text and suite==canonical
+  reports.append({'path':str(report.relative_to(ROOT)),'mtimeNs':report_stat.st_mtime_ns,'sha256':sha,'previous':prior,'qualified':qualified,'suite':suite,'canonicalLogAssociation':('Running '+suite) in log_text})
+  if not qualified:continue
+  dest=target/'junit'/report.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
+  counts.append({'path':str(dest.relative_to(target)),'suite':suite,**{k:int(tree.get(k,'0')) for k in ('tests','failures','errors','skipped')}})
+  for c in tree.findall('testcase'):cases.append({'suite':suite,'reportSha256':sha,'reportMtimeNs':report_stat.st_mtime_ns,'startedNs':started,'endedNs':finished,'class':c.get('classname'),'name':c.get('name'),'failure':c.find('failure') is not None,'error':c.find('error') is not None,'skipped':c.find('skipped') is not None})
+ (target/'report-custody.json').write_text(json.dumps({'startedNs':started,'endedNs':finished,'canonicalSelector':canonical,'reports':reports,'logSha256':digest((target/'run.log').read_bytes())},indent=2)+'\n')
+ for f in target.rglob('*'):
+  if f.is_file():assert not any(len(v)>3 and v in f.read_bytes() for v in secret_values),'private value found; do not print'
+ totals={k:sum(c[k] for c in counts) for k in ('tests','failures','errors','skipped')}
+ exitcode=result.returncode or (1 if totals['tests']==0 or any(totals[k] for k in ('failures','errors','skipped')) or any(not r['qualified'] for r in reports) else 0)
+ summary={'exitCode':exitcode,'processExitCode':result.returncode,'startedNs':started,'finishedNs':finished,'suites':counts,'totals':totals,'cases':cases}
+ (target/'result.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2),flush=True)
+ sys.exit(exitcode)
+if __name__=='__main__':main()
