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
  final MinutesExternalPreparation issuer;final TaskLease lease;final JdbcMinutesGenerationPreparation.Prepared input;final ExternalRequest external;
  private Request(MinutesExternalPreparation issuer,TaskLease lease,JdbcMinutesGenerationPreparation.Prepared input){this.issuer=issuer;this.lease=lease;this.input=input;external=new ExternalRequest("MINUTES_GENERATE","minutes:"+lease.spec().input().id(),input.requestHash(),input.requestHash(),input.executionManifest(lease.taskId()));}
  public byte[] body(String execution){return input.requestBody(execution);}public String envelope(){return input.requestEnvelope();}
 }
 static final class Observation implements PreparedObservation {
  final MinutesExternalPreparation issuer;final Request request;final OperationToken token;final ControlledMinutesClient.Request wire;final ControlledMinutesClient.Receipt receipt;final ExternalObservation fact;final MinutesCandidateGraphPlan plan;final String localError;
  private Observation(MinutesExternalPreparation issuer,Request request,OperationToken token,ControlledMinutesClient.Request wire,ControlledMinutesClient.Receipt receipt,ExternalObservation fact,MinutesCandidateGraphPlan plan,String localError){this.issuer=issuer;this.request=request;this.token=token;this.wire=wire;this.receipt=receipt;this.fact=fact;this.plan=plan;this.localError=localError;}
 }
 public void requireClient(ControlledMinutesClient value){if(value!=client)throw new IllegalArgumentException("Foreign controlled client");}
 public Request prepare(TaskLease lease)throws IOException {
  JdbcMinutesFactsReader.independent();var j=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);var owner=j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,lease.spec().subject().id());var actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));var input=preparation.prepareStored(actor,lease);var request=new Request(this,lease,input);
  // A UUID-shaped placeholder permits complete framing/header budget admission without network I/O.
  client.prepare(request.body("00000000-0000-4000-8000-000000000000"),request.envelope());return request;
 }
 Request request(PreparedRequest value){if(!(value instanceof Request r)||r.issuer!=this)throw new IllegalArgumentException("Foreign minutes request preparation");return r;}
 Observation observation(PreparedObservation value){if(!(value instanceof Observation o)||o.issuer!=this)throw new IllegalArgumentException("Foreign minutes observation preparation");request(o.request);client.require(o.receipt,o.wire);return o;}
 public PreparedObservation accept(Request request,OperationToken token,ControlledMinutesClient.Request wire,ControlledMinutesClient.Receipt receipt,PreparationCancellation cancellation){
  JdbcMinutesFactsReader.independent();request(request);client.require(receipt,wire);require(token.subject().equals(request.lease.spec().subject())&&token.executionNo()==1&&token.executionId().equals(wire.executionId())&&wire.requestHash().equals(request.external.executionRequestHash())&&wire.payloadHash().equals(request.input.payloadHash())&&wire.businessKey().equals(request.external.businessKey()),PROVENANCE_UNAVAILABLE);
  ExternalObservation fact;MinutesCandidateGraphPlan plan=null;String localError=null;
  switch(receipt.state()){
   case "UNKNOWN"->fact=new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"CONTROLLED_UNKNOWN",null);
   case "FAILED_ZERO_EFFECT"->fact=new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,receipt.code(),new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"controlled-minutes:"+receipt.receiptId(),receipt.verifiedAt()));
   case "SUCCEEDED"->{fact=new ExternalObservation(ExternalOutcome.SUCCEEDED,receipt.resultId(),null,null,null);try{var binding=request.input.binding();var output=new ama.minutes.json.StrictMinutesJsonReader().read(new ByteArrayInputStream(receipt.output()),new ValidationContext(binding.fingerprint(),request.lease.spec().inputFingerprint(),Scope.FULL,binding.catalog()),binding,cancellation::cancelled);var graph=request.input.snapshot().graph();plan=MinutesCandidateGraphPlan.prepare(request.lease.spec(),request.input.actor().enterpriseId(),(String)graph.discovery().root().get("minutes_revision_id"),output,binding.catalog(),graph.rows(),request.input.budget());}catch(Failure invalid){if(invalid.code()==CANCELLED||invalid.code()==PROVENANCE_UNAVAILABLE||invalid.code()==PROFILE_UNAVAILABLE)throw invalid;localError="INVALID_MODEL_OUTPUT";}}
   default->throw new IllegalArgumentException("Unsupported controlled fact");
  }
  return new Observation(this,request,token,wire,receipt,fact,plan,localError);
 }
 static void sameSources(MinutesGraphCapture.Graph prepared,MinutesGraphCapture.Graph actual){
  require(prepared.discovery().meeting().equals(actual.discovery().meeting())&&prepared.rows().equals(actual.rows()),PROVENANCE_UNAVAILABLE);
  for(var entry:prepared.auxiliary().entrySet())if(!Set.of("durable_task","task_attempt","external_operation","external_execution").contains(entry.getKey())&&!entry.getKey().endsWith("_history_max"))require(entry.getValue().equals(actual.auxiliary().getOrDefault(entry.getKey(),List.of())),PROVENANCE_UNAVAILABLE);
 }
}
