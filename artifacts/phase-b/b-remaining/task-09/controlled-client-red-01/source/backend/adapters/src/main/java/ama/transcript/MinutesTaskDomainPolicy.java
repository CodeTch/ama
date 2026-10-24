package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.storage.FileRegistry;
import ama.task.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Fixed authority-owned generation domain. Detached specs cannot adopt newly created rows. */
final class MinutesTaskDomainPolicy implements TaskDomainPolicy {
 private final JdbcTranscriptAuthority authority;private final JdbcTaskPreparations issuer;private final JdbcTemplate j;
 private record Adopted(Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,TaskSpec spec){}
 private static final class Existing {final Actor actor;final MeetingAccess.LockedIdentities identities;final MinutesGraphDiscovery.Discovery discovery;final TranscriptAcquisitionBudget.Ledger budget;FileRegistry.LockedMeeting gate;MinutesGraphCapture.Graph graph;Existing(Actor actor,MeetingAccess.LockedIdentities identities,MinutesGraphDiscovery.Discovery discovery,TranscriptAcquisitionBudget.Ledger budget){this.actor=actor;this.identities=identities;this.discovery=discovery;this.budget=budget;}}
 MinutesTaskDomainPolicy(JdbcTranscriptAuthority authority,DataSource source){this.authority=authority;issuer=new JdbcTaskPreparations(source);j=new JdbcTemplate(source);j.setQueryTimeout(2);}
 PreparedDomain adopt(Object scope,FileRegistry.LockedMeeting gate,Actor actor,MinutesGraphCapture.Graph graph,TaskSpec spec){authority.requireMinutesWrite(scope,gate,actor,graph);shape(spec);validateInput(spec);return issuer.issue(List.of(spec),Acquisition.TASK_MUTATION,new Adopted(scope,gate,actor,graph,spec),true);}
 public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){
  if(purpose!=Acquisition.TASK_MUTATION||specs==null||specs.size()!=1)throw new DomainPreparationUnavailableException();var spec=specs.getFirst();shape(spec);
  String producer=ama.minutes.MinutesGenerateProducer.compute(spec.subject().id(),spec.input().id(),spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch(),authority.canonicalBudget(),()->false);require(producer.equals(spec.producerKey()),PROVENANCE_UNAVAILABLE);
  var plan=MinutesGraphDiscovery.discover(authority.dataSource(),spec.subject().id(),"STORED_INPUT",spec.input().id(),100000);var actor=new Actor((String)plan.root().get("owner_id"),Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));var ids=authority.meetingAccess().tryLockIdentities(actor,List.of(actor.enterpriseId()),skip);if(ids.isEmpty())return Optional.empty();var limits=new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256);var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);JdbcMinutesFactsReader.charge(plan,budget);return Optional.of(issuer.issue(specs,purpose,new Existing(actor,ids.orElseThrow(),plan,budget),true));
 }
 public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain prepared){issuer.require(specs,purpose,prepared);}
 public boolean permitsDomainEffects(PreparedDomain prepared){return issuer.permitsEffects(prepared);}
 public boolean lockRoot(TaskSpec spec,boolean skip){throw new DomainPreparationUnavailableException();}
 public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain prepared){
  try{return lockAll(specs,skip,prepared);}catch(ama.contract.FileContracts.Unavailable|ama.contract.FileContracts.NotFound unavailable){if(skip)return false;throw unavailable;}catch(Failure raced){if(skip&&Set.of(PROVENANCE_UNAVAILABLE,PROFILE_UNAVAILABLE,CAPABILITY_UNAVAILABLE).contains(raced.code()))return false;throw raced;}
 }
 private boolean lockAll(List<TaskSpec> specs,boolean skip,PreparedDomain prepared){
  issuer.require(specs,Acquisition.TASK_MUTATION,prepared);var value=issuer.value(prepared);if(value instanceof Adopted a){require(specs.equals(List.of(a.spec())),PROVENANCE_UNAVAILABLE);authority.requireMinutesWrite(a.scope(),a.gate(),a.actor(),a.graph());return true;}if(!(value instanceof Existing p))throw new DomainPreparationUnavailableException();
  var root=authority.meetingAccess().lockPrepared(p.identities,specs.getFirst().subject().id(),skip);if(root.isEmpty())return false;authority.meetingAccess().requirePrepared(root.orElseThrow(),p.actor,p.discovery.meeting(),List.of(p.actor.enterpriseId()),MeetingAccess.Action.EDIT);var gate=authority.fileRegistry().adopt(p.actor,p.discovery.meeting(),root.orElseThrow(),MeetingAccess.Action.EDIT);var scope=authority.minutesScope(gate,p.actor,p.discovery.meeting());var spec=specs.getFirst();boolean current=JdbcMinutesFactsReader.num(p.discovery.root(),"revision")==spec.businessVersion()&&JdbcMinutesFactsReader.num(p.discovery.root(),"cancel_epoch")==spec.cancelEpoch();
  p.graph=current?MinutesGraphCapture.captureCurrent(authority,scope,gate,p.actor,p.discovery,p.budget):MinutesGraphCapture.capture(authority,scope,gate,p.actor,p.discovery,p.budget);p.gate=gate;if(current){JdbcMinutesGenerationPreparation.writable(p.discovery.root(),spec.businessVersion());require(p.graph.auxiliary().getOrDefault("publication",List.of()).isEmpty(),CAPABILITY_UNAVAILABLE);var inputs=p.discovery.tables().getOrDefault("generation_input",List.of()).stream().filter(r->spec.input().id().equals(r.get("id"))).toList();require(inputs.size()==1,PROVENANCE_UNAVAILABLE);for(String field:List.of("transcript_confirmation_id","template_revision_id","exclusion_revision_id","element_set_id"))require(Objects.equals(inputs.getFirst().get(field),p.discovery.root().get(field)),PROVENANCE_UNAVAILABLE);}return true;
 }
 public void validateInput(TaskSpec spec){shape(spec);var found=j.queryForList("SELECT g.id FROM generation_input g JOIN transcript_confirmation c ON c.id=g.transcript_confirmation_id JOIN transcript_revision r ON r.id=c.revision_id JOIN template_revision t ON t.id=g.template_revision_id JOIN exclusion_revision e ON e.id=g.exclusion_revision_id WHERE g.id=? AND g.meeting_id=? AND g.base_revision=? AND g.input_fingerprint=? AND g.scope='FULL' AND g.target_element_id IS NULL AND g.material_snapshot_id IS NULL AND c.meeting_id=g.meeting_id AND r.meeting_id=g.meeting_id AND r.kind='WORKING' AND e.meeting_id=g.meeting_id",String.class,spec.input().id(),spec.subject().id(),spec.businessVersion(),spec.inputFingerprint());require(found.size()==1,PROVENANCE_UNAVAILABLE);}
 public void validateOutput(TaskSpec spec,TaskResult result){if(result.output()!=null||result.outcome()==Outcome.SUCCEEDED||result.outcome()==Outcome.RECONCILING)throw new DomainPreparationUnavailableException();}
 public void validateExternalRequest(TaskSpec spec,ama.contract.ExternalContracts.ExternalRequest request){throw new DomainPreparationUnavailableException();}
 static void shape(TaskSpec spec){require("MINUTES_GENERATE_V1".equals(spec.type())&&"MINUTES_GENERATE".equals(spec.pool())&&"MEETING".equals(spec.subject().kind())&&"GENERATION_INPUT".equals(spec.input().kind())&&spec.priority()==0&&spec.maxAttempts()==3,PROVENANCE_UNAVAILABLE);}
}
