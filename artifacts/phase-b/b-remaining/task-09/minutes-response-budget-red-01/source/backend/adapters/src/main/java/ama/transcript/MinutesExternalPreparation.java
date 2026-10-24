package ama.transcript;
import ama.contract.IdentityContracts.*;import ama.contract.TaskContracts.*;import ama.contract.ExternalContracts.*;import ama.contract.MinutesContracts.*;
import ama.task.*;import ama.task.ExternalOperationPort.*;import ama.model.*;
import java.util.*;import java.io.*;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Inert original request and verified receipt preparation; no method here authorizes physical send. */
public final class MinutesExternalPreparation {
 private final JdbcTranscriptAuthority authority;private final JdbcMinutesGenerationPreparation preparation;private final ControlledMinutesClient client;
 public MinutesExternalPreparation(JdbcTranscriptAuthority authority,JdbcMinutesGenerationPreparation preparation,ControlledMinutesClient client){this.authority=Objects.requireNonNull(authority);this.preparation=Objects.requireNonNull(preparation);this.client=Objects.requireNonNull(client);authority.minutesDomain().install(this);}
 public static final class Request implements PreparedRequest {
  final MinutesExternalPreparation issuer;final String taskId;final TaskSpec spec;final JdbcMinutesGenerationPreparation.Prepared input;final ExternalRequest external;
  private Request(MinutesExternalPreparation issuer,String taskId,TaskSpec spec,JdbcMinutesGenerationPreparation.Prepared input){this.issuer=issuer;this.taskId=taskId;this.spec=spec;this.input=input;external=new ExternalRequest("MINUTES_GENERATE","minutes:"+spec.input().id(),input.requestHash(),input.requestHash(),input.executionManifest(taskId));}
  public byte[] body(String execution){return input.requestBody(execution);}public String envelope(){return input.requestEnvelope();}
 }
 static final class Observation implements PreparedObservation {
  final MinutesExternalPreparation issuer;final Request request;final OperationToken token;final ControlledMinutesClient.Request wire;final ControlledMinutesClient.Receipt receipt;final ExternalObservation fact;final MinutesCandidateGraphPlan plan;final String localError;
  private Observation(MinutesExternalPreparation issuer,Request request,OperationToken token,ControlledMinutesClient.Request wire,ControlledMinutesClient.Receipt receipt,ExternalObservation fact,MinutesCandidateGraphPlan plan,String localError){this.issuer=issuer;this.request=request;this.token=token;this.wire=wire;this.receipt=receipt;this.fact=fact;this.plan=plan;this.localError=localError;}
 }
 static final class RecordedObservation implements PreparedObservation {
  final MinutesExternalPreparation issuer;final MinutesRecordedLookup request;final ControlledMinutesClient.FactReceipt receipt;final ExternalObservation fact;
  private RecordedObservation(MinutesExternalPreparation issuer,MinutesRecordedLookup request,ControlledMinutesClient.FactReceipt receipt,ExternalObservation fact){this.issuer=issuer;this.request=request;this.receipt=receipt;this.fact=fact;}
 }
 public Optional<MinutesRecordedLookup> prepareDeniedLookup(JdbcExternalOperations.RecordedLookup capability,OperationToken token){return MinutesRecordedLookup.prepare(this,authority,capability,token);}
 RecordedObservation recorded(PreparedObservation value){if(!(value instanceof RecordedObservation o)||o.issuer!=this||o.request.issuer!=this)throw new IllegalArgumentException("Foreign recorded observation");client.require(o.receipt,o.request);return o;}
 public PreparedObservation acceptRecorded(MinutesRecordedLookup request,ControlledMinutesClient.FactReceipt receipt){
  JdbcMinutesFactsReader.independent();if(request.issuer!=this)throw new IllegalArgumentException("Foreign recorded request");client.require(receipt,request);ExternalObservation fact=switch(receipt.state()){
   case "UNKNOWN"->new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"CONTROLLED_UNKNOWN",null);
   case "SUCCEEDED"->new ExternalObservation(ExternalOutcome.SUCCEEDED,receipt.resultId(),null,null,null);
   case "FAILED_ZERO_EFFECT"->new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,receipt.code(),zeroEffectProof(request.taskId,request.requestHash,request.token,receipt.receiptId(),receipt.verifiedAt()));
   default->throw new IllegalArgumentException("Unsupported controlled fact");};return new RecordedObservation(this,request,receipt,fact);
 }
 public void requireClient(ControlledMinutesClient value){if(value!=client)throw new IllegalArgumentException("Foreign controlled client");}
 public Request prepare(TaskLease lease)throws IOException {
  JdbcMinutesFactsReader.independent();var j=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);var owner=j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,lease.spec().subject().id());var actor=new Actor(owner,Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));var input=preparation.prepareStored(actor,lease);var request=new Request(this,lease.taskId(),lease.spec(),input);
  // A UUID-shaped placeholder permits complete framing/header budget admission without network I/O.
  client.prepare(request.body("00000000-0000-4000-8000-000000000000"),request.envelope());return request;
 }
 public Request prepareLookup(OperationToken token){
  JdbcMinutesFactsReader.independent();var j=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);
  var sizes=j.queryForList("SELECT id,task_id,OCTET_LENGTH(request_manifest) manifest_bytes FROM external_execution WHERE id=? AND operation_id=? AND execution_no=?",token.executionId(),token.operationId(),token.executionNo());require(sizes.size()==1&&((Number)sizes.getFirst().get("manifest_bytes")).longValue()<=65536,PROVENANCE_UNAVAILABLE);String taskId=(String)sizes.getFirst().get("task_id");var row=j.queryForMap("SELECT * FROM durable_task WHERE id=?",taskId);var spec=TranscriptReadGraph.spec(row);require(spec.subject().equals(token.subject()),PROVENANCE_UNAVAILABLE);String owner=j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,spec.subject().id());var actor=new Actor(owner,Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));var stored=j.queryForMap("SELECT e.request_hash,e.request_manifest,o.operation_kind,o.business_key,o.request_hash logical_hash FROM external_execution e JOIN external_operation o ON o.id=e.operation_id WHERE e.id=? AND e.operation_id=? AND e.execution_no=? AND e.task_id=? AND OCTET_LENGTH(e.request_manifest) BETWEEN 1 AND 65536",token.executionId(),token.operationId(),token.executionNo(),taskId);var manifest=ama.config.WireValues.fields(BoundedModelJson.parse(((String)stored.get("request_manifest")).getBytes(java.nio.charset.StandardCharsets.UTF_8),65536),"protocolVersion","providerId","modelIdentity","taskId","inputId","inputFingerprint","requesterId","logicalBusinessKey","payloadSha256","archiveIndexSha256","roots","base");var input=preparation.prepareRecordedAtIndex(actor,taskId,spec,ama.config.WireValues.text(manifest.get("archiveIndexSha256")));var request=new Request(this,taskId,spec,input);require(request.external.executionRequestHash().equals(stored.get("request_hash"))&&request.external.logicalRequestHash().equals(stored.get("logical_hash"))&&request.external.requestManifest().equals(stored.get("request_manifest"))&&request.external.operationKind().equals(stored.get("operation_kind"))&&request.external.businessKey().equals(stored.get("business_key")),PROVENANCE_UNAVAILABLE);return request;
 }
 Request request(PreparedRequest value){if(!(value instanceof Request r)||r.issuer!=this)throw new IllegalArgumentException("Foreign minutes request preparation");return r;}
 Observation observation(PreparedObservation value){if(!(value instanceof Observation o)||o.issuer!=this)throw new IllegalArgumentException("Foreign minutes observation preparation");request(o.request);client.require(o.receipt,o.wire);return o;}
 public PreparedObservation accept(Request request,OperationToken token,ControlledMinutesClient.Request wire,ControlledMinutesClient.Receipt receipt,PreparationCancellation cancellation){
  JdbcMinutesFactsReader.independent();request(request);client.require(receipt,wire);require(token.subject().equals(request.spec.subject())&&token.executionNo()==1&&token.executionId().equals(wire.executionId())&&wire.requestHash().equals(request.external.executionRequestHash())&&wire.payloadHash().equals(request.input.payloadHash())&&wire.businessKey().equals(request.external.businessKey()),PROVENANCE_UNAVAILABLE);
  ExternalObservation fact;MinutesCandidateGraphPlan plan=null;String localError=null;
  switch(receipt.state()){
   case "UNKNOWN"->fact=new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"CONTROLLED_UNKNOWN",null);
   case "FAILED_ZERO_EFFECT"->fact=new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,receipt.code(),zeroEffectProof(request,token,receipt));
   case "SUCCEEDED"->{fact=new ExternalObservation(ExternalOutcome.SUCCEEDED,receipt.resultId(),null,null,null);try{var binding=request.input.binding();var output=new ama.minutes.json.StrictMinutesJsonReader().read(new ByteArrayInputStream(receipt.output()),new ValidationContext(binding.fingerprint(),request.spec.inputFingerprint(),Scope.FULL,binding.catalog()),binding,cancellation::cancelled);ControlledMinutesRenderer.rejectExcludedEvidence(binding,output);var graph=request.input.snapshot().graph();plan=MinutesCandidateGraphPlan.prepare(request.spec,request.input.actor().enterpriseId(),(String)graph.discovery().root().get("minutes_revision_id"),output,binding.catalog(),graph.rows(),request.input.budget()).restore(graph,request.taskId);}catch(Failure invalid){if(invalid.code()==CANCELLED||invalid.code()==PROVENANCE_UNAVAILABLE||invalid.code()==PROFILE_UNAVAILABLE)throw invalid;localError="INVALID_MODEL_OUTPUT";}}
   default->throw new IllegalArgumentException("Unsupported controlled fact");
  }
  return new Observation(this,request,token,wire,receipt,fact,plan,localError);
 }
 private NoEffectProof zeroEffectProof(Request request,OperationToken token,ControlledMinutesClient.Receipt receipt){
  return zeroEffectProof(request.taskId,request.external.executionRequestHash(),token,receipt.receiptId(),receipt.verifiedAt());
 }
 private NoEffectProof zeroEffectProof(String taskId,String requestHash,OperationToken token,String receiptId,java.time.Instant instant){
  String evidence="controlled-minutes:"+receiptId;var j=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);
  var rows=j.queryForList("SELECT no_effect_basis,no_effect_evidence,no_effect_verified_at FROM external_execution WHERE id=? AND operation_id=? AND execution_no=? AND task_id=? AND request_hash=?",token.executionId(),token.operationId(),token.executionNo(),taskId,requestHash);require(rows.size()==1,PROVENANCE_UNAVAILABLE);var row=rows.getFirst();java.time.Instant verified=instant;
  if(row.get("no_effect_basis")!=null||row.get("no_effect_evidence")!=null||row.get("no_effect_verified_at")!=null){require("DEFINITIVE_REJECTION".equals(row.get("no_effect_basis"))&&evidence.equals(row.get("no_effect_evidence"))&&row.get("no_effect_verified_at") instanceof java.time.LocalDateTime,PROVENANCE_UNAVAILABLE);verified=((java.time.LocalDateTime)row.get("no_effect_verified_at")).toInstant(java.time.ZoneOffset.UTC);}
  return new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,evidence,verified);
 }
 /** A committed result may be newly visible after a lost acknowledgment; only this exact private plan may be added. */
 static void sameSourcesAfterCommit(Observation observation,MinutesGraphCapture.Graph actual){
  var prepared=observation.request.input.snapshot().graph();if(prepared.rows().equals(actual.rows())){sameSources(prepared,actual);return;}var plan=observation.plan;require(plan!=null&&prepared.discovery().meeting().equals(actual.discovery().meeting()),PROVENANCE_UNAVAILABLE);
  var body=JdbcMinutesFactsReader.one(actual,Table.MINUTES_REVISION,plan.bodyId());var set=JdbcMinutesFactsReader.one(actual,Table.ELEMENT_SET,plan.setId());var combined=new HashSet<FullRow>(prepared.rows());combined.addAll(plan.rows(body.number("revision"),set.number("revision")));require(combined.equals(new HashSet<>(actual.rows())),PROVENANCE_UNAVAILABLE);
  var keys=new HashSet<String>(prepared.auxiliary().keySet());keys.addAll(actual.auxiliary().keySet());
  for(String key:keys){if(Set.of("durable_task","task_attempt","external_operation","external_execution").contains(key)||key.endsWith("_history_max"))continue;var before=prepared.auxiliary().getOrDefault(key,List.of());var after=actual.auxiliary().getOrDefault(key,List.of());if(key.equals("generation_candidate")){var own=after.stream().filter(row->plan.candidateId().equals(row.get("id"))&&observation.request.taskId.equals(row.get("task_id"))).toList();require(own.size()==1&&before.stream().noneMatch(row->plan.candidateId().equals(row.get("id"))),PROVENANCE_UNAVAILABLE);var expected=new ArrayList<Map<String,Object>>(before);expected.add(own.getFirst());require(expected.size()==after.size()&&new HashSet<>(expected).equals(new HashSet<>(after)),PROVENANCE_UNAVAILABLE);}else require(before.equals(after),PROVENANCE_UNAVAILABLE);}
 }
 static void sameSources(MinutesGraphCapture.Graph prepared,MinutesGraphCapture.Graph actual){
  require(prepared.discovery().meeting().equals(actual.discovery().meeting())&&prepared.rows().equals(actual.rows()),PROVENANCE_UNAVAILABLE);
  for(var entry:prepared.auxiliary().entrySet())if(!Set.of("durable_task","task_attempt","external_operation","external_execution").contains(entry.getKey())&&!entry.getKey().endsWith("_history_max"))require(entry.getValue().equals(actual.auxiliary().getOrDefault(entry.getKey(),List.of())),PROVENANCE_UNAVAILABLE);
 }
}
