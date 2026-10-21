package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.config.ConfigBindings;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.TaskContracts.TaskSpec;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class GenerationFingerprint {
 public static final String PROFILE="ama.generation-input.v2";
 public record Projection(CanonicalValue manifest,String fingerprint,Profiles profiles,ValidationBudget outputBudget,CanonicalBudget canonicalBudget,ConfigBindings.ValidatedOperation operation){
  private Projection complete(ConfigBindings.ValidatedOperation operation){return new Projection(manifest,fingerprint,profiles,outputBudget,canonicalBudget,operation);}
 }
 private final ArchivedProfileResolver resolver;
 public GenerationFingerprint(){this(ArchivedProfileResolver.unavailable());}
 public GenerationFingerprint(ConfigBindings bindings,List<RawArchive> archives,ArchiveReadBudget budget){this(ArchivedProfileResolver.configured(bindings,archives,budget));}
 GenerationFingerprint(ArchivedProfileResolver resolver){require(resolver!=null,INVALID_ARGUMENT);this.resolver=resolver;}
 /** Pure schema projection; compute/verify additionally require the exact raw configuration archive interpreter. */
 public CanonicalValue data(GenerationFacts facts,Fence fence,GraphBudget budget,BooleanSupplier cancelled){
  var index=new StoredFactsIndex(facts.stored(),budget,cancelled);var input=index.one(Table.GENERATION_INPUT,facts.inputId(),PROVENANCE_UNAVAILABLE);
  return data(input,fence,index);
 }
 static CanonicalValue reference(Artifact artifact){return object(field("version",string(artifact.version())),field("sha256",string(artifact.sha256())));}
 static GenerationDraft draft(FullRow input){return new GenerationDraft(input.string("id"),input.string("meeting_id"),input.string("transcript_confirmation_id"),input.nullableString("material_snapshot_id"),input.string("template_revision_id"),input.string("exclusion_revision_id"),input.nullableString("element_set_id"),input.string("prompt_version"),input.string("model_config_version"),Scope.valueOf(input.string("scope")),input.nullableString("target_element_id"));}
 static CanonicalValue data(FullRow input,Fence fence,StoredFactsIndex index){require(input.number("base_revision")==fence.meetingRevision(),INPUT_INVALID);return project(draft(input),fence,index,index.config(input));}
 static CanonicalValue project(GenerationDraft draft,Fence fence,StoredFactsIndex index,GenerationConfiguration config){
  check(index.cancelled);
  require(draft.scope()==Scope.FULL&&draft.targetElementId()==null,CAPABILITY_UNAVAILABLE);
  // Archived material parser-to-locator capability is a separate required D01 contract. Never enumerate existing locator rows as a complete model catalog.
  require(draft.materialSnapshotId()==null,CAPABILITY_UNAVAILABLE);
  var profiles=config.profiles();String meeting=draft.meetingId();
  var session=new StoredSourceGraph.Session(index);
  var confirmation=index.one(Table.TRANSCRIPT_CONFIRMATION,draft.transcriptConfirmationId(),PROVENANCE_UNAVAILABLE);index.meeting(confirmation,meeting);
  var confirmationData=TranscriptConfirmationHash.data(confirmation.string("id"),index);
  var confirmationProfile=profiles.definition(ProfileKind.CONFIRMATION_HASH);DomainHash.profile(confirmationProfile,TranscriptConfirmationHash.PROFILE,index.budget.canonical(),index.cancelled);
  String confirmationHash=new CanonicalValueCodec().sha256(ContractValues.envelope(TranscriptConfirmationHash.PROFILE,"TRANSCRIPT_CONFIRMATION",confirmationData),index.budget.canonical(),index.cancelled);
  require(confirmationHash.equals(confirmation.string("content_hash")),FINGERPRINT_MISMATCH);
  var revision=index.one(Table.TRANSCRIPT_REVISION,confirmation.string("revision_id"),PROVENANCE_UNAVAILABLE);
  var catalogFacts=new ArrayList<CatalogFact>();for(var segment:index.matching(Table.TRANSCRIPT_SEGMENT,"revision_id",revision.string("id"))){check(index.cancelled);catalogFacts.add(session.transcript(confirmation,segment.string("segment_id"),meeting));}
  var catalog=new SourceCatalog().build(new CatalogFacts(catalogFacts,index.budget.canonical().maxContainerEntries()),index.budget.canonical(),index.cancelled);

  var segmentRows=new ArrayList<>(index.matching(Table.TRANSCRIPT_SEGMENT,"revision_id",revision.string("id")));segmentRows.sort(Comparator.comparingLong(r->r.number("ordinal")));
  var segments=new ArrayList<CanonicalValue>();
  for(var segment:segmentRows){
   check(index.cancelled);var marks=new ArrayList<Mark>();
   for(var mark:index.matching(Table.TRANSCRIPT_SEGMENT_MARK,"revision_id",revision.string("id")))if(mark.string("segment_id").equals(segment.string("segment_id")))marks.add(Mark.valueOf(mark.string("mark")));
   marks.sort(Comparator.naturalOrder());var values=new ArrayList<CanonicalValue>();for(var mark:marks){check(index.cancelled);values.add(string(mark.name()));}
   segments.add(TranscriptConfirmationHash.segment(segment,new Sequence(values),false));
  }
  var batchFiles=new ArrayList<>(index.matching(Table.INPUT_BATCH_FILE,"batch_id",revision.string("batch_id")));batchFiles.sort(Comparator.comparingLong(r->r.number("ordinal")));
  var files=new ArrayList<CanonicalValue>();
  for(var file:batchFiles){check(index.cancelled);var content=index.file(file.string("file_id"));files.add(object(field("ordinal",number(file.number("ordinal"))),field("fileId",string(file.string("file_id"))),field("sha256",string(file.string("sha256"))),field("sizeBytes",number(content.sizeBytes()))));}
  var template=index.one(Table.TEMPLATE_REVISION,draft.templateRevisionId(),PROVENANCE_UNAVAILABLE);positive(template.number("revision"));
  String templateHash=RowsetProjection.hash(List.of(template),Set.of(Table.TEMPLATE_REVISION),index);
  var exclusionRows=RowsetProjection.exclusions(index,meeting,draft.exclusionRevisionId());var exclusion=index.one(Table.EXCLUSION_REVISION,draft.exclusionRevisionId(),PROVENANCE_UNAVAILABLE);
  var entries=new ArrayList<CanonicalValue>();var pairs=index.matching(Table.SENSITIVE_EXCLUSION,"revision_id",exclusion.string("id"));
  for(var row:pairs){
   check(index.cancelled);var locator=index.one(Table.SOURCE_LOCATOR,row.string("locator_id"),PROVENANCE_UNAVAILABLE);boolean member=false;for(var source:catalog.sources()){check(index.cancelled);if(source.fact().locator().equals(RowValues.locator(locator)))member=true;}require(member,SOURCE_NOT_IN_INPUT);String reason=row.nullableString("reason");
   entries.add(object(field("locatorId",string(locator.string("id"))),field("locator",ContractValues.locator(RowValues.locator(locator))),field("category",string(row.string("category"))),field("reasonHash",reason==null?nil():string(OutputValidation.textHash(reason,index.cancelled)))));
  }
  CanonicalValue elements=object(field("mode",string("EMPTY")),field("items",list()));String setId=draft.elementSetId();
  if(setId!=null){
   var set=index.one(Table.ELEMENT_SET,setId,PROVENANCE_UNAVAILABLE);index.meeting(set,meeting);
   var graph=session.graph(new GraphSelection(meeting,null,setId,null,null),null);session.walk(graph.sourceUsages(),meeting);
   elements=object(field("mode",string("PINNED")),field("setId",string(setId)),field("revision",number(set.number("revision"))),field("contentHash",string(RowsetProjection.hash(RowsetProjection.elements(index,meeting,setId),RowsetProjection.ELEMENT_TABLES,index))),field("sourceGraph",RowValues.graph(graph,index.cancelled)));
  }
  var sources=new ArrayList<CanonicalValue>();
  for(var source:catalog.sources()){check(index.cancelled);var fact=source.fact();sources.add(object(field("sourceId",string(source.sourceId())),field("locator",ContractValues.locator(fact.locator())),field("role",string(fact.role().name())),field("sourceVersion",ContractValues.sourceVersion(fact.sourceVersion())),field("sourceContentHash",string(fact.sourceContentHash()))));}
  var kinds=new ArrayList<>(profiles.definitions().keySet());kinds.sort(Comparator.comparing(Enum::name));var profileValues=new ArrayList<CanonicalValue>();
  for(var kind:kinds){check(index.cancelled);var artifact=profiles.definition(kind);profileValues.add(object(field("kind",string(kind.name())),field("version",string(artifact.version())),field("sha256",string(artifact.sha256()))));}
  check(index.cancelled);
  return object(field("meetingId",string(meeting)),field("inputId",string(draft.inputId())),
   field("confirmation",object(field("id",string(confirmation.string("id"))),field("revisionId",string(revision.string("id"))),field("hashProfile",string(confirmationProfile.version())),field("contentHash",string(confirmationHash)))),
   field("segments",new Sequence(segments)),field("batch",object(field("id",string(revision.string("batch_id"))),field("files",new Sequence(files)))),
   field("materials",object(field("mode",string("EMPTY")),field("items",list()))),
   field("template",object(field("mode",string(template.string("id").equals(config.defaultTemplateRevisionId())?"DEFAULT":"SELECTED")),field("revisionId",string(template.string("id"))),field("revision",number(template.number("revision"))),field("contentHash",string(templateHash)))),
   field("exclusion",object(field("mode",string(entries.isEmpty()?"EMPTY":"PINNED")),field("revisionId",string(exclusion.string("id"))),field("revision",number(exclusion.number("revision"))),field("contentHash",string(RowsetProjection.hash(exclusionRows,RowsetProjection.EXCLUSION_TABLES,index))),field("entries",new Sequence(entries)))),
   field("elements",elements),field("sources",new Sequence(sources)),
   field("configs",object(field("prompt",reference(config.prompt())),field("model",reference(config.model())),field("profiles",new Sequence(profileValues)))),
   field("scope",object(field("kind",string("FULL")),field("target",nil()))),
   field("base",object(field("meetingRevision",number(fence.meetingRevision())),field("subjectBusinessVersion",number(fence.subjectBusinessVersion())),field("cancelEpoch",number(fence.cancelEpoch())))));
 }
 Projection recomputeStored(GenerationFacts facts,Fence fence,Profiles profiles,GraphBudget budget,BooleanSupplier cancelled){
  var operation=resolver.open(facts.stored(),cancelled);var result=new GenerationFingerprint(operation.resolver()).computeBound(facts,fence,profiles,budget,cancelled);var verified=operation.finish();check(cancelled);return verified==null?result:result.complete(verified);
 }
 private Projection computeBound(GenerationFacts facts,Fence fence,Profiles profiles,GraphBudget budget,BooleanSupplier cancelled){
  var index=new StoredFactsIndex(facts.stored(),budget,cancelled);var input=index.one(Table.GENERATION_INPUT,facts.inputId(),PROVENANCE_UNAVAILABLE);
  var bound=resolver.generation(input,facts.stored(),budget,cancelled);require(bound.equals(index.config(input))&&bound.profiles().equals(profiles),PROFILE_UNAVAILABLE);
  var confirmation=index.one(Table.TRANSCRIPT_CONFIRMATION,input.string("transcript_confirmation_id"),PROVENANCE_UNAVAILABLE);
  String confirmationHash=new TranscriptConfirmationHash(resolver).hash(new ConfirmationFacts(confirmation.string("id"),facts.stored()),profiles.definition(ProfileKind.CONFIRMATION_HASH),budget,cancelled);
  require(confirmationHash.equals(confirmation.string("content_hash")),FINGERPRINT_MISMATCH);
  index.hashBudget=TranscriptConfirmationHash.minimum(budget.canonical(),resolver.generationBudget(input,cancelled));var manifest=data(input,fence,index);String fingerprint=DomainHash.hash(profiles.definition(ProfileKind.GENERATION_HASH),PROFILE,"GENERATION_INPUT",manifest,TranscriptConfirmationHash.minimum(budget.canonical(),resolver.generationBudget(input,cancelled)),cancelled);
  check(cancelled);return new Projection(manifest,fingerprint,bound.profiles(),resolver.outputBudget(bound,cancelled),resolver.generationBudget(input,cancelled),null);
 }
 static void task(FullRow input,TaskSpec task){
  require(task!=null&&task.subject().kind().equals("MEETING")&&task.subject().id().equals(input.string("meeting_id"))&&task.input().kind().equals("GENERATION_INPUT")&&task.input().id().equals(input.string("id")),INPUT_INVALID);
 }
 static Fence historicalFence(FullRow input,StoredProvenanceFacts facts,BooleanSupplier cancelled){
  TaskSpec first=null;
  var tasks=new ArrayList<TaskSpec>(facts.taskSpecs());for(var stored:facts.taskFacts()){check(cancelled);tasks.add(stored.spec());}
  for(var task:tasks){check(cancelled);if(!task.input().kind().equals("GENERATION_INPUT")||!task.input().id().equals(input.string("id")))continue;
   task(input,task);require(task.inputFingerprint().equals(input.string("input_fingerprint")),FINGERPRINT_MISMATCH);
   if(first!=null)require(first.businessVersion()==task.businessVersion()&&first.cancelEpoch()==task.cancelEpoch(),INPUT_INVALID);else first=task;
  }
  require(first!=null,PROFILE_UNAVAILABLE);return new Fence(input.number("base_revision"),first.businessVersion(),first.cancelEpoch());
 }
 public Projection verify(GenerationFacts facts,TaskSpec task,Profiles profiles,GraphBudget budget,BooleanSupplier cancelled){
  var operation=resolver.open(facts.stored(),cancelled);var helper=new GenerationFingerprint(operation.resolver());var result=helper.verifyBound(facts,task,profiles,budget,cancelled);var verified=operation.finish();check(cancelled);return verified==null?result:result.complete(verified);
 }
 private Projection verifyBound(GenerationFacts facts,TaskSpec task,Profiles profiles,GraphBudget budget,BooleanSupplier cancelled){
  var index=new StoredFactsIndex(facts.stored(),budget,cancelled);var input=index.one(Table.GENERATION_INPUT,facts.inputId(),PROVENANCE_UNAVAILABLE);task(input,task);
  var historical=historicalFence(input,facts.stored(),cancelled);require(historical.subjectBusinessVersion()==task.businessVersion()&&historical.cancelEpoch()==task.cancelEpoch(),INPUT_INVALID);
  var projection=recomputeStored(facts,historical,profiles,budget,cancelled);
  require(projection.fingerprint().equals(task.inputFingerprint())&&projection.fingerprint().equals(input.string("input_fingerprint")),FINGERPRINT_MISMATCH);
  if(input.nullableString("element_set_id")!=null){var session=new StoredSourceGraph.Session(index);var graph=session.graph(new GraphSelection(input.string("meeting_id"),null,input.string("element_set_id"),null,null),null);OriginChainVerifier.verify(index,session,graph,resolver);}
  return projection;
 }

 public Projection compute(GenerationDraft draft,StoredProvenanceFacts prerequisites,Fence fence,GraphBudget budget,BooleanSupplier cancelled){
  require(draft!=null&&prerequisites!=null&&fence!=null,INVALID_ARGUMENT);var operation=resolver.open(prerequisites,cancelled);
  var active=operation.resolver();var config=active.generation(draft,prerequisites,cancelled);var index=new StoredFactsIndex(prerequisites,budget,cancelled);
  // A draft needs real prerequisite confirmation/processing facts, never its own row/task/output or a dummy hash.
  var confirmation=index.one(Table.TRANSCRIPT_CONFIRMATION,draft.transcriptConfirmationId(),PROVENANCE_UNAVAILABLE);
  String confirmed=new TranscriptConfirmationHash(active).hash(new ConfirmationFacts(draft.transcriptConfirmationId(),prerequisites),config.profiles().definition(ProfileKind.CONFIRMATION_HASH),budget,cancelled);
  require(confirmed.equals(confirmation.string("content_hash")),FINGERPRINT_MISMATCH);
  index.hashBudget=TranscriptConfirmationHash.minimum(budget.canonical(),active.generationBudget(draft,cancelled));var manifest=project(draft,fence,index,config);
  if(draft.elementSetId()!=null){
   var session=new StoredSourceGraph.Session(index);var graph=session.graph(new GraphSelection(draft.meetingId(),null,draft.elementSetId(),null,null),null);
   OriginChainVerifier.verify(index,session,graph,active);
  }
  String fingerprint=DomainHash.hash(config.profiles().definition(ProfileKind.GENERATION_HASH),PROFILE,"GENERATION_INPUT",manifest,TranscriptConfirmationHash.minimum(budget.canonical(),active.generationBudget(draft,cancelled)),cancelled);
  var outputBudget=active.outputBudget(config,cancelled);var canonicalBudget=active.generationBudget(draft,cancelled);var verified=operation.finish();check(cancelled);return new Projection(manifest,fingerprint,config.profiles(),outputBudget,canonicalBudget,verified);
 }
}
