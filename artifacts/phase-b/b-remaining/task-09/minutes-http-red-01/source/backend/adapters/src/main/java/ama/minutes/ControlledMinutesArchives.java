package ama.minutes;
import ama.canonical.CanonicalValue;
import ama.config.CanonicalJson;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Pure explicit controlled release construction. Publishing and persisted-template verification
 * belong to the administrative installer; this method has no filesystem or database effects. */
public final class ControlledMinutesArchives {
 private ControlledMinutesArchives(){}
 public static final String RENDERER="ama.controlled-minutes-render.v1";
 private static final Map<ArtifactType,String> CONTRACTS=Map.ofEntries(
  Map.entry(ArtifactType.ANCHOR,"ama.anchor.v1"),Map.entry(ArtifactType.CATALOG,"ama.transcript-catalog.v1"),Map.entry(ArtifactType.CHUNK_PLAN,"ama.no-chunk.v1"),Map.entry(ArtifactType.CONFIRMATION_HASH,"ama.transcript-confirmation.v1"),Map.entry(ArtifactType.EXCLUSION,"ama.exclusion-physical-union.v1"),Map.entry(ArtifactType.FINAL_SOURCES,"ama.final-sources.v2"),Map.entry(ArtifactType.GENERATION_HASH,"ama.generation-input.v2"),Map.entry(ArtifactType.OUTPUT_SCHEMA,"ama.minutes-output.v1"),Map.entry(ArtifactType.REVIEW_HASH,"ama.result-review.v2"),Map.entry(ArtifactType.ROWSET_HASH,"ama.rowset.v1"),Map.entry(ArtifactType.SUPPORT,"ama.creation-support.v1"),Map.entry(ArtifactType.TITLE,"ama.title-suggestion.v1"));
 public static List<RawArchive> build(String mode,String persistedTemplateRevisionId,String release,String prompt,CanonicalBudget canonical,ValidationBudget output){
  require("CONTROLLED_INSTALLATION".equals(mode),CAPABILITY_UNAVAILABLE);uuid(persistedTemplateRevisionId);ama.contract.ConfigArchiveContracts.version(release);ama.contract.ConfigArchiveContracts.version(release+".render");require(prompt!=null&&prompt.length()<=4096&&canonical!=null&&output!=null,INVALID_ARGUMENT);scalars(prompt);
  require(canonical.maxEncodedBytes()<=16777216&&canonical.maxStringBytes()<=4194304&&output.maxWireBytes()<=8388608&&output.maxStringUtf8Bytes()<=4194304&&output.maxStringUtf16()<=4194304,BUDGET_EXCEEDED);
  var raws=new EnumMap<ArtifactType,RawArchive>(ArtifactType.class);var refs=new EnumMap<ArtifactType,Ref>(ArtifactType.class);
  add(raws,refs,ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1","ama.profile-contract.v1",object(field("contract",string("ama.canonical-value.v1"))),Map.of());
  var codec=Map.of("CANONICAL_CODEC",refs.get(ArtifactType.CANONICAL_CODEC));
  for(var entry:CONTRACTS.entrySet())add(raws,refs,entry.getKey(),entry.getValue(),"ama.profile-contract.v1",object(field("contract",string(entry.getValue()))),codec);
  add(raws,refs,ArtifactType.RENDER,release+".render","ama.render-binding.v1",object(field("contract",string("ama.render-binding.v1")),field("rendererVersion",string(RENDERER)),field("defaultTemplateRevisionId",string(persistedTemplateRevisionId))),codec);
  add(raws,refs,ArtifactType.VALIDATION_BUDGET,release+".budget","ama.validation-budget.v1",object(field("canonical",object(field("maxEncodedBytes",number(canonical.maxEncodedBytes())),field("maxDepth",number(canonical.maxDepth())),field("maxNodes",number(canonical.maxNodes())),field("maxStringBytes",number(canonical.maxStringBytes())),field("maxContainerEntries",number(canonical.maxContainerEntries())))),field("output",object(field("maxWireBytes",number(output.maxWireBytes())),field("maxDepth",number(output.maxDepth())),field("maxTokens",number(output.maxTokens())),field("maxNameUtf16",number(output.maxNameUtf16())),field("maxStringUtf16",number(output.maxStringUtf16())),field("maxStringUtf8Bytes",number(output.maxStringUtf8Bytes())),field("maxStringScalars",number(output.maxStringScalars())),field("maxAggregateScalars",number(output.maxAggregateScalars())),field("maxElements",number(output.maxElements())),field("maxDetections",number(output.maxDetections())),field("maxNotes",number(output.maxNotes())),field("maxRefs",number(output.maxRefs())),field("maxEvidence",number(output.maxEvidence())),field("maxMemberships",number(output.maxMemberships()))))),codec);
  var dependencies=new TreeMap<String,Ref>();for(ProfileKind kind:ProfileKind.values()){var ref=refs.get(ArtifactType.valueOf(kind.name()));require(ref!=null,PROFILE_UNAVAILABLE);dependencies.put(kind.name(),ref);}
  add(raws,refs,ArtifactType.PROMPT_CONFIG,release+".prompt","ama.generation-prompt.v1",object(field("instructionText",string(prompt))),dependencies);
  add(raws,refs,ArtifactType.MODEL_CONFIG,release+".model","ama.generation-model.v1",object(field("credentialRef",nil()),field("modelIdentity",string("CONTROLLED_MINUTES_V1")),field("protocolVersion",string("ama.controlled-minutes-http.v1")),field("parameters",object(field("temperatureMicros",number(0)),field("maxOutputTokens",number(4096)),field("seed",number(0))))),dependencies);
  return raws.values().stream().sorted(Comparator.comparing(r->r.claimedArtifactType().name())).toList();
 }
 private static void add(Map<ArtifactType,RawArchive> raw,Map<ArtifactType,Ref> refs,ArtifactType type,String version,String schema,CanonicalValue definition,Map<String,Ref> dependencies){
  var slots=new ArrayList<CanonicalValue>();for(var entry:new TreeMap<>(dependencies).entrySet()){var ref=entry.getValue();slots.add(object(field("slot",string(entry.getKey())),field("ref",object(field("artifactType",string(ref.artifactType().name())),field("version",string(ref.version())),field("sha256",string(ref.sha256()))))));}
  byte[] bytes=CanonicalJson.encode(object(field("archiveSchema",string("ama.config-archive.v1")),field("artifactType",string(type.name())),field("version",string(version)),field("definitionSchema",string(schema)),field("definition",definition),field("dependencies",new Sequence(slots))),65536,()->false);
  var archive=new RawArchive(type,version,bytes);require(raw.put(type,archive)==null,PROFILE_UNAVAILABLE);try{refs.put(type,new Ref(type,version,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
}
