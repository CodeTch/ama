package ama.config;
import ama.canonical.CanonicalValue;
import ama.contract.MinutesContracts.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import static ama.contract.ConfigArchiveContracts.version;
import java.util.*;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Finite known schemas only. Reader output itself grants no authority. */
public final class ConfigSchema {
 private ConfigSchema(){}
 public static final Map<ArtifactType,String> CONTRACTS=Map.ofEntries(
  Map.entry(ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1"),Map.entry(ArtifactType.CONFIRMATION_HASH,"ama.transcript-confirmation.v1"),
  Map.entry(ArtifactType.GENERATION_HASH,"ama.generation-input.v2"),Map.entry(ArtifactType.ROWSET_HASH,"ama.rowset.v1"),
  Map.entry(ArtifactType.ANCHOR,"ama.anchor.v1"),Map.entry(ArtifactType.REVIEW_HASH,"ama.result-review.v2"),
  Map.entry(ArtifactType.FINAL_SOURCES,"ama.final-sources.v2"),Map.entry(ArtifactType.OUTPUT_SCHEMA,"ama.minutes-output.v1"),
  Map.entry(ArtifactType.CATALOG,"ama.transcript-catalog.v1"),Map.entry(ArtifactType.EXCLUSION,"ama.exclusion-physical-union.v1"),
  Map.entry(ArtifactType.SUPPORT,"ama.creation-support.v1"),Map.entry(ArtifactType.TITLE,"ama.title-suggestion.v1"),Map.entry(ArtifactType.CHUNK_PLAN,"ama.no-chunk.v1"));
 public static Identity archive(CanonicalValue value,ArchiveReadState state){
  var f=fields(value,"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");literal(f.get("archiveSchema"),"ama.config-archive.v1");
  ArtifactType type;try{type=ArtifactType.valueOf(text(f.get("artifactType")));}catch(IllegalArgumentException e){throw fail(PROFILE_UNAVAILABLE);}
  var identity=new Identity(type,text(f.get("version")));String schema=text(f.get("definitionSchema"));var definition=f.get("definition");Set<String> slots;
  if(CONTRACTS.containsKey(type)){
   require(schema.equals("ama.profile-contract.v1")&&identity.version().equals(CONTRACTS.get(type)),PROFILE_UNAVAILABLE);
   literal(fields(definition,"contract").get("contract"),CONTRACTS.get(type));slots=type==ArtifactType.CANONICAL_CODEC?Set.of():Set.of("CANONICAL_CODEC");
  }else switch(type){
   case PARSER_CONFIG->{require(schema.equals("ama.txt-parser-config.v1")&&identity.version().equals("ama.strict-utf8-lines.v1"),PROFILE_UNAVAILABLE);literal(fields(definition,"contract").get("contract"),"ama.strict-utf8-lines.v1");slots=Set.of();}
   case PROCESSING_CONFIG->{
    require(schema.equals("ama.import-txt-config.v1"),PROFILE_UNAVAILABLE);var d=fields(definition,"contract","manifestSchemaVersion","parseBudget","canonicalBudget");literal(d.get("contract"),"ama.import-txt.v1");require(number(d.get("manifestSchemaVersion"))==1,PROFILE_UNAVAILABLE);
    parseBudget(d.get("parseBudget"));canonicalBudget(d.get("canonicalBudget"));slots=Set.of("CANONICAL_CODEC","CONFIRMATION_HASH","PARSER_CONFIG");
   }
   case PROMPT_CONFIG->{require(schema.equals("ama.generation-prompt.v1"),PROFILE_UNAVAILABLE);String instruction=text(fields(definition,"instructionText").get("instructionText"));require(!instruction.isEmpty(),PROFILE_UNAVAILABLE);slots=profileSlots();}
   case MODEL_CONFIG->{
    require(schema.equals("ama.generation-model.v1"),PROFILE_UNAVAILABLE);var d=fields(definition,"protocolVersion","modelIdentity","credentialRef","parameters");
    version(text(d.get("protocolVersion")));version(text(d.get("modelIdentity")));if(!(d.get("credentialRef") instanceof CanonicalValue.Null))version(text(d.get("credentialRef")));
    var parameters=fields(d.get("parameters"),"temperatureMicros","maxOutputTokens","seed");require(number(parameters.get("temperatureMicros"))<=2000000,INVALID_NUMBER);positiveLong(parameters.get("maxOutputTokens"));if(!(parameters.get("seed") instanceof CanonicalValue.Null))number(parameters.get("seed"));slots=profileSlots();
   }
   case VALIDATION_BUDGET->{require(schema.equals("ama.validation-budget.v1"),PROFILE_UNAVAILABLE);var d=fields(definition,"output","canonical");validationBudget(d.get("output"));canonicalBudget(d.get("canonical"));slots=Set.of("CANONICAL_CODEC");}
   case RENDER->{require(schema.equals("ama.render-binding.v1"),PROFILE_UNAVAILABLE);var d=fields(definition,"contract","rendererVersion","defaultTemplateRevisionId");literal(d.get("contract"),"ama.render-binding.v1");version(text(d.get("rendererVersion")));uuid(text(d.get("defaultTemplateRevisionId")));slots=Set.of("CANONICAL_CODEC");}
   default->throw fail(PROFILE_UNAVAILABLE);
  }
  var dependencies=dependencies(value,state);require(dependencies.keySet().equals(slots),PROFILE_UNAVAILABLE);
  for(var e:dependencies.entrySet())require(e.getValue().artifactType().name().equals(e.getKey()),PROFILE_UNAVAILABLE);
  return identity;
 }
 public static Set<String> profileSlots(){var set=new HashSet<String>();for(var kind:ProfileKind.values())set.add(kind.name());return Set.copyOf(set);}
 public static Map<String,Ref> dependencies(CanonicalValue archive,ArchiveReadState state){
  var f=fields(archive,"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");var values=array(f.get("dependencies"));
  var out=new LinkedHashMap<String,Ref>();String previous=null;
  for(var value:values){check(state.cancelled);var d=fields(value,"slot","ref");String slot=text(d.get("slot"));require(slot.matches("[A-Z][A-Z_]*"),PROFILE_UNAVAILABLE);
   require(previous==null||previous.compareTo(slot)<0,PROFILE_UNAVAILABLE);require(out.putIfAbsent(slot,ref(d.get("ref")))==null,PROFILE_UNAVAILABLE);previous=slot;}
  return Map.copyOf(out);
 }
 public static CanonicalBudget canonicalBudget(CanonicalValue value){
  var f=fields(value,"maxEncodedBytes","maxDepth","maxNodes","maxStringBytes","maxContainerEntries");
  return new CanonicalBudget(positiveLong(f.get("maxEncodedBytes")),positiveInt(f.get("maxDepth")),positiveLong(f.get("maxNodes")),positiveLong(f.get("maxStringBytes")),positiveLong(f.get("maxContainerEntries")));
 }
 public static ama.transcript.TranscriptDocumentParser.ParseBudget parseBudget(CanonicalValue value){
  var f=fields(value,"maxSourceBytes","maxUtf16CodeUnits","maxSegments");return new ama.transcript.TranscriptDocumentParser.ParseBudget(positiveLong(f.get("maxSourceBytes")),positiveLong(f.get("maxUtf16CodeUnits")),positiveInt(f.get("maxSegments")));
 }
 public static ValidationBudget validationBudget(CanonicalValue value){
  var f=fields(value,"maxWireBytes","maxDepth","maxTokens","maxNameUtf16","maxStringUtf16","maxStringUtf8Bytes","maxStringScalars","maxAggregateScalars","maxElements","maxDetections","maxNotes","maxRefs","maxEvidence","maxMemberships");
  return new ValidationBudget(positiveLong(f.get("maxWireBytes")),positiveInt(f.get("maxDepth")),positiveLong(f.get("maxTokens")),positiveInt(f.get("maxNameUtf16")),positiveInt(f.get("maxStringUtf16")),positiveLong(f.get("maxStringUtf8Bytes")),positiveLong(f.get("maxStringScalars")),positiveLong(f.get("maxAggregateScalars")),positiveLong(f.get("maxElements")),positiveLong(f.get("maxDetections")),positiveLong(f.get("maxNotes")),positiveLong(f.get("maxRefs")),positiveLong(f.get("maxEvidence")),positiveLong(f.get("maxMemberships")));
 }
 public static void manifest(CanonicalValue value,ArchiveReadState state){
  var f=fields(value,"schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps");require(number(f.get("schemaVersion"))==1,PROFILE_UNAVAILABLE);literal(f.get("profile"),"ama.transcription-input.v1");uuid(text(f.get("meetingId")));uuid(text(f.get("inputId")));
  String kind=text(f.get("kind"));require(Set.of("IMPORT","ASR","ENHANCE").contains(kind),INVALID_SCHEMA);require(kind.equals("IMPORT"),CAPABILITY_UNAVAILABLE);nil(f.get("sourceRevisionId"));
  var material=fields(f.get("materials"),"mode","items");require(text(material.get("mode")).equals("EMPTY"),CAPABILITY_UNAVAILABLE);require(array(material.get("items")).isEmpty(),CAPABILITY_UNAVAILABLE);
  var batch=fields(f.get("batch"),"id","kind","files");uuid(text(batch.get("id")));literal(batch.get("kind"),"TRANSCRIPT");
  var files=array(batch.get("files"));require(!files.isEmpty(),INPUT_INVALID);require(files.size()<=state.budget.maxBatchFiles(),BUDGET_EXCEEDED);var ids=new HashSet<String>();long ordinal=0;
  for(var item:files){check(state.cancelled);var file=fields(item,"ordinal","fileId","sha256","sizeBytes");require(number(file.get("ordinal"))==++ordinal,INPUT_INVALID);String id=uuid(text(file.get("fileId")));require(ids.add(id),INPUT_INVALID);hash(text(file.get("sha256")));number(file.get("sizeBytes"));}
  var configs=fields(f.get("configs"),"processing","parser","model","prompt","profiles");nil(configs.get("model"));nil(configs.get("prompt"));var processing=ref(configs.get("processing"));var parser=ref(configs.get("parser"));require(processing.artifactType()==ArtifactType.PROCESSING_CONFIG&&parser.artifactType()==ArtifactType.PARSER_CONFIG,PROFILE_UNAVAILABLE);
  var profiles=array(configs.get("profiles"));require(profiles.size()==2,PROFILE_UNAVAILABLE);String[] expected={"CANONICAL_CODEC","CONFIRMATION_HASH"};
  for(int i=0;i<2;i++){var p=fields(profiles.get(i),"kind","ref");literal(p.get("kind"),expected[i]);require(ref(p.get("ref")).artifactType().name().equals(expected[i]),PROFILE_UNAVAILABLE);}
  var base=fields(f.get("base"),"meetingRevision","subjectBusinessVersion","cancelEpoch");for(var v:base.values())number(v);
  var steps=array(f.get("steps"));require(steps.size()==1,CAPABILITY_UNAVAILABLE);var step=fields(steps.getFirst(),"ordinal","kind","parentInput","priorOutput","source","config","parser","model","prompt");
  require(number(step.get("ordinal"))==1,INPUT_INVALID);literal(step.get("kind"),"IMPORT_TXT");nil(step.get("parentInput"));nil(step.get("priorOutput"));nil(step.get("model"));nil(step.get("prompt"));
  require(ref(step.get("config")).equals(processing)&&ref(step.get("parser")).equals(parser),PROFILE_UNAVAILABLE);
  var source=fields(step.get("source"),"kind","id");literal(source.get("kind"),"INPUT_BATCH");require(text(source.get("id")).equals(text(batch.get("id"))),INPUT_INVALID);check(state.cancelled);
 }
}
