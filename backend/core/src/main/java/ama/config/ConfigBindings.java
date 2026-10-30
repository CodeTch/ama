package ama.config;
import ama.contract.MinutesContracts.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.TaskContracts.TaskSpec;
import ama.canonical.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Pure verification of caller-supplied raw bytes and detached rows, never storage/index authenticity. */
public final class ConfigBindings {
 private final ConfigArchiveReader archives;private final ProcessingManifestReader manifests;
 public ConfigBindings(ConfigArchiveReader archives,ProcessingManifestReader manifests){require(archives!=null&&manifests!=null,INVALID_ARGUMENT);this.archives=archives;this.manifests=manifests;}
 public static final class ValidatedProcessingBinding {
  private final CanonicalValue.ObjectValue manifest;private final String fingerprint;private final Ref codec,confirmation,processing,parser;
  private final ama.transcript.TranscriptDocumentParser.ParseBudget parseBudget;private final CanonicalBudget canonicalBudget;private final ArchiveBytes rawManifest;private final ValidatedOperation operation;private final String engineManifestSha256;
  private ValidatedProcessingBinding(CanonicalValue.ObjectValue manifest,String fingerprint,Ref codec,Ref confirmation,Ref processing,Ref parser,CanonicalBudget budget,ama.transcript.TranscriptDocumentParser.ParseBudget parseBudget,ArchiveBytes rawManifest,String engineManifestSha256){this(manifest,fingerprint,codec,confirmation,processing,parser,budget,parseBudget,rawManifest,engineManifestSha256,null);}
  private ValidatedProcessingBinding(CanonicalValue.ObjectValue manifest,String fingerprint,Ref codec,Ref confirmation,Ref processing,Ref parser,CanonicalBudget budget,ama.transcript.TranscriptDocumentParser.ParseBudget parseBudget,ArchiveBytes rawManifest,String engineManifestSha256,ValidatedOperation operation){
   this.manifest=manifest;this.fingerprint=fingerprint;this.codec=codec;this.confirmation=confirmation;this.processing=processing;this.parser=parser;this.canonicalBudget=budget;this.parseBudget=parseBudget;this.rawManifest=rawManifest;this.operation=operation;this.engineManifestSha256=engineManifestSha256;
  }
  private ValidatedProcessingBinding complete(ValidatedOperation operation){return new ValidatedProcessingBinding(manifest,fingerprint,codec,confirmation,processing,parser,canonicalBudget,parseBudget,rawManifest,engineManifestSha256,operation);}
  public String fingerprint(){return fingerprint;}public CanonicalValue.ObjectValue manifest(){return manifest;}
  public Ref codec(){return codec;}public Ref confirmation(){return confirmation;}public Ref processing(){return processing;}public Ref parser(){return parser;}
  public ama.transcript.TranscriptDocumentParser.ParseBudget parseBudget(){return parseBudget;}
  public CanonicalBudget canonicalBudget(){return canonicalBudget;}public String engineManifestSha256(){return engineManifestSha256;}
 }
 public static final class ValidatedOperation {
  private final Map<Identity,String> identities;private final List<ArchiveBytes> originals;
  private ValidatedOperation(Map<Identity,String> identities,List<ArchiveBytes> originals){this.identities=Map.copyOf(identities);this.originals=List.copyOf(originals);}
  public Map<Identity,String> verifiedIdentities(){return identities;}
 }
 private record Node(ArchiveBytes bytes,ConfigArchiveReader.ParsedArchive parsed,Map<String,Ref> dependencies){}
 public Context begin(StoredProvenanceFacts facts,List<RawArchive> raw,ArchiveReadBudget budget,BooleanSupplier cancelled){return new Context(facts,raw,new ArchiveReadState(budget,cancelled));}
 /** Provisional operation only. Business hash entrypoints start their own operation and finish exact closure before returning. */
 public final class Context {
  private final StoredProvenanceFacts facts;private final ArchiveReadState state;private final Map<Identity,Node> nodes=new LinkedHashMap<>();
  private final Map<Identity,Ref> roots=new LinkedHashMap<>();private final Map<String,ValidatedProcessingBinding> processingCache=new HashMap<>();
  private boolean finished;
  private Context(StoredProvenanceFacts facts,List<RawArchive> raw,ArchiveReadState state){
   require(facts!=null&&raw!=null,INVALID_ARGUMENT);this.facts=facts;this.state=state;check(state.cancelled);require(raw.size()<=state.budget.maxArtifacts(),BUDGET_EXCEEDED);
   state.rows((long)facts.rows().size()+facts.files().size()+facts.taskSpecs().size()+facts.taskFacts().size());var taskIds=new HashSet<String>();
   for(var task:facts.taskFacts()){check(state.cancelled);require(taskIds.add(task.taskId()),INPUT_INVALID);}
   for(var archive:raw){
    check(state.cancelled);var identity=archive.claimedIdentity();require(!nodes.containsKey(identity),INPUT_INVALID);
    var owned=state.capture(archive.bytes(),false);var parsed=archives.read(owned,identity,state);
    // Revalidate schema/J/raw hash so a parsed DTO or injected parser result cannot attest different bytes.
    require(ConfigSchema.archive(parsed.wire(),state).equals(identity)&&parsed.identity().equals(identity),PROFILE_UNAVAILABLE);
    require(CanonicalJson.matches(parsed.wire(),owned,state.cancelled)&&owned.sha256(state.cancelled).equals(parsed.rawSha256()),PROFILE_UNAVAILABLE);
    var dependencies=ConfigSchema.dependencies(parsed.wire(),state);state.dependencies(dependencies.size());nodes.put(identity,new Node(owned,parsed,dependencies));
   }
  }
  public StoredProvenanceFacts facts(){return facts;}
  private void active(){check(state.cancelled);require(!finished,INVALID_ARGUMENT);}
  private Node node(Identity identity){active();require(nodes.containsKey(identity),PROFILE_UNAVAILABLE);return nodes.get(identity);}
  private Ref reference(Node node){return new Ref(node.parsed.identity().artifactType(),node.parsed.identity().version(),node.parsed.rawSha256());}
  private void root(Ref ref){active();var previous=roots.putIfAbsent(ref.identity(),ref);require(previous==null||previous.equals(ref),PROFILE_UNAVAILABLE);}
  private void refMatches(Ref ref){var node=node(ref.identity());require(reference(node).equals(ref),PROFILE_UNAVAILABLE);}
  private List<FullRow> rows(Table table){active();var result=new ArrayList<FullRow>();for(var row:facts.rows()){check(state.cancelled);if(row.table()==table)result.add(row);}return result;}
  private FullRow one(Table table,String id){
   state.edges(1);var matched=new ArrayList<FullRow>();for(var row:rows(table))if(row.string("id").equals(id))matched.add(row);require(matched.size()==1,PROFILE_UNAVAILABLE);return matched.getFirst();
  }
  private CanonicalValue.ObjectValue manifest(ArchiveBytes owned){
   var value=manifests.read(owned,state);ConfigSchema.manifest(value,state);require(CanonicalJson.matches(value,owned,state.cancelled),INVALID_SCHEMA);return value;
  }
  private ValidatedProcessingBinding processingDraft(byte[] rawManifest){
   active();return processing(state.capture(rawManifest,true),null);
  }
  private ValidatedProcessingBinding processingStored(FullRow input){
   active();require(input.table()==Table.TRANSCRIPTION_INPUT,INPUT_INVALID);String id=input.string("id");
   if(processingCache.containsKey(id))return processingCache.get(id);
   String raw=input.string("config_manifest");require(raw.length()<=state.budget.maxManifestBytes(),BUDGET_EXCEEDED);scalars(raw);
   var binding=processing(state.capture(raw.getBytes(StandardCharsets.UTF_8),true),input);processingCache.put(id,binding);return binding;
  }
  private ValidatedProcessingBinding processing(ArchiveBytes raw,FullRow stored){
   var value=manifest(raw);var f=fields(value,"schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps");
   String meeting=text(f.get("meetingId")),id=text(f.get("inputId"));var batch=fields(f.get("batch"),"id","kind","files");String batchId=text(batch.get("id"));
   var configs=fields(f.get("configs"),"processing","parser","model","prompt","profiles");var processing=ref(configs.get("processing"));var parser=ref(configs.get("parser"));var configNode=node(processing.identity());refMatches(processing);root(processing);
   var profiles=array(configs.get("profiles"));var codec=ref(fields(profiles.get(0),"kind","ref").get("ref"));var confirmation=ref(fields(profiles.get(1),"kind","ref").get("ref"));
   require(configNode.dependencies.equals(Map.of("CANONICAL_CODEC",codec,"CONFIRMATION_HASH",confirmation,"PARSER_CONFIG",parser)),PROFILE_UNAVAILABLE);
   refMatches(codec);refMatches(confirmation);refMatches(parser);require(node(confirmation.identity()).dependencies.equals(Map.of("CANONICAL_CODEC",codec)),PROFILE_UNAVAILABLE);
   var definition=fields(fields(configNode.parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies").get("definition"),"contract","manifestSchemaVersion","parseBudget","canonicalBudget");
   require(text(definition.get("contract")).equals(parser.version().equals("ama.media-documents.v2")?"ama.import-media-documents.v2":parser.version().equals("ama.media-documents.v1")?"ama.import-media-documents.v1":parser.version().equals("ama.office-documents.v1")?"ama.import-documents.v1":"ama.import-txt.v1"),PROFILE_UNAVAILABLE);var canonical=ConfigSchema.canonicalBudget(definition.get("canonicalBudget"));var parse=ConfigSchema.parseBudget(definition.get("parseBudget"));var fileList=array(batch.get("files"));
   var batchRow=one(Table.INPUT_BATCH,batchId);require(batchRow.string("meeting_id").equals(meeting)&&batchRow.string("kind").equals("TRANSCRIPT"),INPUT_INVALID);
   var members=new ArrayList<FullRow>();for(var row:rows(Table.INPUT_BATCH_FILE))if(row.string("batch_id").equals(batchId))members.add(row);members.sort(Comparator.comparingLong(r->r.number("ordinal")));
   require(members.size()==fileList.size(),INPUT_INVALID);
   for(int i=0;i<fileList.size();i++){
    check(state.cancelled);state.edges(2);var file=fields(fileList.get(i),"ordinal","fileId","sha256","sizeBytes");var member=members.get(i);
    require(member.number("ordinal")==number(file.get("ordinal"))&&member.string("file_id").equals(text(file.get("fileId")))&&member.string("sha256").equals(text(file.get("sha256"))),INPUT_INVALID);
    var fileFacts=new ArrayList<ContentFile>();for(var fact:facts.files())if(fact.id().equals(text(file.get("fileId"))))fileFacts.add(fact);
    require(fileFacts.size()==1,INPUT_INVALID);var actual=fileFacts.getFirst();require(meeting.equals(actual.meetingId())&&actual.sha256().equals(text(file.get("sha256")))&&actual.sizeBytes()==number(file.get("sizeBytes")),INPUT_INVALID);
    require(actual.sizeBytes()<=parse.maxSourceBytes(),BUDGET_EXCEEDED);
   }
   String digest=new CanonicalValueCodec().sha256(ama.minutes.contract.ContractValues.envelope("ama.transcription-input.v1","TRANSCRIPTION_INPUT",value),canonical,state.cancelled);
   if(stored!=null){
    require(stored.string("input_fingerprint").equals(digest),FINGERPRINT_MISMATCH);require(one(Table.TRANSCRIPTION_INPUT,id).equals(stored),INPUT_INVALID);
    var base=fields(f.get("base"),"meetingRevision","subjectBusinessVersion","cancelEpoch");
    require(stored.string("id").equals(id)&&stored.string("meeting_id").equals(meeting)&&stored.string("batch_id").equals(batchId)&&stored.string("kind").equals("IMPORT")
     &&stored.nullableString("source_revision_id")==null&&stored.nullableString("material_snapshot_id")==null&&stored.string("material_mode").equals("EMPTY")
     &&stored.string("processing_config_version").equals(processing.version())&&stored.string("parser_version").equals(parser.version())
     &&stored.nullableString("model_config_version")==null&&stored.nullableString("prompt_version")==null&&stored.number("manifest_schema_version")==1
     &&stored.number("base_revision")==number(base.get("meetingRevision"))&&stored.number("cancel_epoch")==number(base.get("cancelEpoch")),INPUT_INVALID);
    var tasks=new ArrayList<TaskSpec>(facts.taskSpecs());for(var task:facts.taskFacts())tasks.add(task.spec());boolean taskFound=false;
    for(var task:tasks){check(state.cancelled);if(!task.input().kind().equals("TRANSCRIPTION_INPUT")||!task.input().id().equals(id))continue;taskFound=true;
     require(task.subject().kind().equals("MEETING")&&task.subject().id().equals(meeting)&&task.businessVersion()==number(base.get("subjectBusinessVersion"))&&task.cancelEpoch()==number(base.get("cancelEpoch"))&&task.inputFingerprint().equals(digest),FINGERPRINT_MISMATCH);}
    require(taskFound,PROFILE_UNAVAILABLE);
    for(var revision:rows(Table.TRANSCRIPT_REVISION))if(revision.string("processing_input_id").equals(id)){
     state.edges(1);require(revision.string("meeting_id").equals(meeting)&&revision.string("batch_id").equals(batchId)&&revision.string("input_fingerprint").equals(digest),FINGERPRINT_MISMATCH);
     String producer=revision.nullableString("producer_task_id");if(producer!=null){
      var actual=new ArrayList<StoredTask>();for(var task:facts.taskFacts())if(task.taskId().equals(producer))actual.add(task);require(actual.size()==1,PROFILE_UNAVAILABLE);
      var spec=actual.getFirst().spec();require(spec.input().kind().equals("TRANSCRIPTION_INPUT")&&spec.input().id().equals(id)&&spec.subject().kind().equals("MEETING")&&spec.subject().id().equals(meeting)&&spec.inputFingerprint().equals(digest)&&spec.businessVersion()==number(base.get("subjectBusinessVersion"))&&spec.cancelEpoch()==number(base.get("cancelEpoch")),FINGERPRINT_MISMATCH);
     }
    }
   }
   String engine=null;if(parser.version().equals("ama.media-documents.v1")||parser.version().equals("ama.media-documents.v2")){var parserWire=fields(node(parser.identity()).parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");var parserDefinition=fields(parserWire.get("definition"),"contract","formats","engines");engine=text(fields(parserDefinition.get("engines"),"asr",parser.version().equals("ama.media-documents.v2")?"decoder":"pdfbox","modelManifestSha256",parser.version().equals("ama.media-documents.v2")?"subtitles":"poi").get("modelManifestSha256"));}active();return new ValidatedProcessingBinding(value,digest,codec,confirmation,processing,parser,canonical,parse,raw,engine);
  }
  public Artifact artifact(Ref ref){
   refMatches(ref);return new Artifact(ref.version(),ref.sha256(),node(ref.identity()).bytes);
  }
  public CanonicalBudget processingBudget(FullRow processing){return processingStored(processing).canonicalBudget();}
  public CanonicalBudget generationBudget(FullRow input){var config=generation(input);var profile=node(new Identity(ArtifactType.VALIDATION_BUDGET,config.profiles().definition(ProfileKind.VALIDATION_BUDGET).version()));return ConfigSchema.canonicalBudget(fields(fields(profile.parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies").get("definition"),"output","canonical").get("canonical"));}
  public CanonicalBudget generationBudget(GenerationDraft draft){var config=generation(draft);var profile=node(new Identity(ArtifactType.VALIDATION_BUDGET,config.profiles().definition(ProfileKind.VALIDATION_BUDGET).version()));return ConfigSchema.canonicalBudget(fields(fields(profile.parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies").get("definition"),"output","canonical").get("canonical"));}
  public ValidationBudget outputBudget(GenerationConfiguration config){var profile=node(new Identity(ArtifactType.VALIDATION_BUDGET,config.profiles().definition(ProfileKind.VALIDATION_BUDGET).version()));return ConfigSchema.validationBudget(fields(fields(profile.parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies").get("definition"),"output","canonical").get("output"));}
  /** Strict parsed original definition; provisional data, never database authority. */
  public CanonicalValue.ObjectValue generationDefinition(ArtifactType kind,String version){
   require(kind==ArtifactType.PROMPT_CONFIG||kind==ArtifactType.MODEL_CONFIG,INVALID_ARGUMENT);
   var value=node(new Identity(kind,version));
   return (CanonicalValue.ObjectValue)fields(value.parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies").get("definition");
  }
  public Artifact confirmation(FullRow processing){var binding=processingStored(processing);return artifact(binding.confirmation());}
  public GenerationConfiguration generation(FullRow input){
   active();require(input.table()==Table.GENERATION_INPUT,INPUT_INVALID);
   return generation(input.string("id"),input.string("meeting_id"),input.string("transcript_confirmation_id"),input.string("prompt_version"),input.string("model_config_version"),input.nullableString("material_snapshot_id"));
  }
  public GenerationConfiguration generation(GenerationDraft draft){return generation(draft.inputId(),draft.meetingId(),draft.transcriptConfirmationId(),draft.promptVersion(),draft.modelVersion(),draft.materialSnapshotId());}
  private GenerationConfiguration generation(String inputId,String meeting,String confirmationId,String promptVersion,String modelVersion,String materialSnapshot){
   var prompt=node(new Identity(ArtifactType.PROMPT_CONFIG,promptVersion));var model=node(new Identity(ArtifactType.MODEL_CONFIG,modelVersion));root(reference(prompt));root(reference(model));
   require(prompt.dependencies.equals(model.dependencies)&&prompt.dependencies.keySet().equals(ConfigSchema.profileSlots()),PROFILE_UNAVAILABLE);var codec=prompt.dependencies.get("CANONICAL_CODEC");
   var definitions=new EnumMap<ProfileKind,Artifact>(ProfileKind.class);
   for(var kind:ProfileKind.values()){
    check(state.cancelled);var ref=prompt.dependencies.get(kind.name());refMatches(ref);var node=node(ref.identity());
    if(kind==ProfileKind.CATALOG&&ref.version().equals(ama.material.MaterialProfiles.CATALOG)){require(codec.equals(node.dependencies.get("CANONICAL_CODEC")),PROFILE_UNAVAILABLE);for(var dependency:node.dependencies.values())refMatches(dependency);}else require(kind==ProfileKind.CANONICAL_CODEC?node.dependencies.isEmpty():node.dependencies.equals(Map.of("CANONICAL_CODEC",codec)),PROFILE_UNAVAILABLE);definitions.put(kind,artifact(ref));
   }
   var confirmation=one(Table.TRANSCRIPT_CONFIRMATION,confirmationId);require(confirmation.string("meeting_id").equals(meeting),INPUT_INVALID);
   var revision=one(Table.TRANSCRIPT_REVISION,confirmation.string("revision_id"));require(revision.string("meeting_id").equals(meeting),INPUT_INVALID);
   var processing=one(Table.TRANSCRIPTION_INPUT,revision.string("processing_input_id"));var older=processingStored(processing);
   require(older.codec().equals(codec)&&older.confirmation().equals(prompt.dependencies.get("CONFIRMATION_HASH")),PROFILE_UNAVAILABLE);
   var render=node(prompt.dependencies.get("RENDER").identity());var definition=fields(fields(render.parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies").get("definition"),"contract","rendererVersion","defaultTemplateRevisionId");
   var selections=ama.material.MaterialBinding.selections(facts,meeting,materialSnapshot,inputId);
   if(materialSnapshot!=null){var catalog=node(prompt.dependencies.get("CATALOG").identity());require(catalog.parsed.identity().version().equals(ama.material.MaterialProfiles.CATALOG),PROFILE_UNAVAILABLE);var bindings=array(fields(fields(catalog.parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies").get("definition"),"contract","bindings").get("bindings"));
    for(var selection:selections){var documents=rows(Table.PARSED_DOCUMENT).stream().filter(d->selection.materialRevisionId().equals(d.string("material_revision_id"))&&selection.parserVersion().equals(d.string("parser_version"))).toList();require(documents.size()==1,SOURCE_CONTEXT_AMBIGUOUS);var profile=ama.material.MaterialBinding.one(facts.rows(),Table.PARSED_DOCUMENT_PROFILE,"document_id",documents.getFirst().string("id"));var expected=new Ref(ArtifactType.PARSER_CONFIG,profile.string("parser_config_version"),profile.string("parser_config_sha256"));refMatches(expected);require(bindings.stream().filter(b->{var binding=fields(b,"format","parser");return profile.string("format").equals(text(binding.get("format")))&&expected.equals(ref(binding.get("parser")));}).count()==1,PROFILE_UNAVAILABLE);}
   }
   return new GenerationConfiguration(inputId,artifact(reference(prompt)),artifact(reference(model)),new Profiles(definitions),text(definition.get("defaultTemplateRevisionId")),selections);
  }
  public Artifact review(String profile){
   var node=node(new Identity(ArtifactType.REVIEW_HASH,profile));root(reference(node));return artifact(reference(node));
  }
  private record Walk(Ref ref,Iterator<Ref> children){}
  public ValidatedOperation finish(){
   active();var done=new HashSet<Identity>();var active=new HashSet<Identity>();var demands=new HashMap<Identity,Ref>();var stack=new ArrayDeque<Walk>();
   for(var root:roots.values()){
    if(done.contains(root.identity()))continue;stack.push(new Walk(root,List.of(root).iterator()));
    while(!stack.isEmpty()){
     check(state.cancelled);var frame=stack.peek();if(!frame.children.hasNext()){stack.pop();if(active.remove(frame.ref.identity()))done.add(frame.ref.identity());continue;}
     var ref=frame.children.next();var previous=demands.putIfAbsent(ref.identity(),ref);require(previous==null||previous.equals(ref),PROFILE_UNAVAILABLE);refMatches(ref);
     require(!active.contains(ref.identity()),INPUT_INVALID);require(stack.size()<=state.budget.maxGraphDepth(),BUDGET_EXCEEDED);if(done.contains(ref.identity()))continue;
     active.add(ref.identity());stack.push(new Walk(ref,node(ref.identity()).dependencies.values().iterator()));
    }
   }
   require(done.equals(nodes.keySet()),PROFILE_UNAVAILABLE);var identities=new LinkedHashMap<Identity,String>();var originals=new ArrayList<ArchiveBytes>();
   for(var e:nodes.entrySet()){check(state.cancelled);identities.put(e.getKey(),e.getValue().parsed.rawSha256());originals.add(e.getValue().bytes);}
   for(var binding:processingCache.values()){check(state.cancelled);originals.add(binding.rawManifest);}
   finished=true;check(state.cancelled);return new ValidatedOperation(identities,originals);
  }
 }
 public ValidatedProcessingBinding computeProcessing(byte[] rawManifest,StoredProvenanceFacts prerequisites,List<RawArchive> raw,ArchiveReadBudget budget,BooleanSupplier cancelled){var context=begin(prerequisites,raw,budget,cancelled);var result=context.processingDraft(rawManifest);var verified=context.finish();check(cancelled);return result.complete(verified);}
 public ValidatedProcessingBinding verifyProcessing(FullRow input,StoredProvenanceFacts facts,List<RawArchive> raw,ArchiveReadBudget budget,BooleanSupplier cancelled){var context=begin(facts,raw,budget,cancelled);var result=context.processingStored(input);var verified=context.finish();check(cancelled);return result.complete(verified);}

 public static final class ValidatedGenerationBinding {
  private final ama.minutes.contract.GenerationFingerprint.Projection projection;
  private ValidatedGenerationBinding(ama.minutes.contract.GenerationFingerprint.Projection projection){require(projection.operation()!=null&&projection.profiles()!=null&&projection.outputBudget()!=null&&projection.canonicalBudget()!=null&&projection.catalog()!=null&&projection.promptDefinition()!=null&&projection.modelDefinition()!=null,PROFILE_UNAVAILABLE);this.projection=projection;}
  public String fingerprint(){return projection.fingerprint();}public CanonicalValue manifest(){return projection.manifest();}
  public Profiles profiles(){return projection.profiles();}public ValidationBudget outputBudget(){return projection.outputBudget();}public CanonicalBudget canonicalBudget(){return projection.canonicalBudget();}
  public Catalog catalog(){return projection.catalog();}public CanonicalValue.ObjectValue promptDefinition(){return projection.promptDefinition();}public CanonicalValue.ObjectValue modelDefinition(){return projection.modelDefinition();}
 }
 public ValidatedGenerationBinding computeGeneration(GenerationDraft draft,StoredProvenanceFacts prerequisites,Fence fence,List<RawArchive> raw,GraphBudget graphBudget,ArchiveReadBudget readBudget,BooleanSupplier cancelled){
  var projection=new ama.minutes.contract.GenerationFingerprint(this,raw,readBudget).compute(draft,prerequisites,fence,graphBudget,cancelled);return new ValidatedGenerationBinding(projection);
 }
 public ValidatedGenerationBinding verifyGeneration(GenerationFacts facts,TaskSpec task,List<RawArchive> raw,GraphBudget graphBudget,ArchiveReadBudget readBudget,BooleanSupplier cancelled){
  var configs=new ArrayList<GenerationConfiguration>();for(var config:facts.stored().configurations()){check(cancelled);if(config.inputId().equals(facts.inputId()))configs.add(config);}require(configs.size()==1,PROFILE_UNAVAILABLE);
  var projection=new ama.minutes.contract.GenerationFingerprint(this,raw,readBudget).verify(facts,task,configs.getFirst().profiles(),graphBudget,cancelled);return new ValidatedGenerationBinding(projection);
 }
}
