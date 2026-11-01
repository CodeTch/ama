package ama.minutes;

import ama.canonical.CanonicalValue;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.material.MaterialArchives;
import ama.model.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Pure opt-in transformation of a fresh release. Retained archive identities are never rewritten. */
public final class ControlledPersonalMinutesArchives {
 public static final String RENDERER=ama.knowledge.ControlledKnowledgeDocumentRenderer.PERSONAL_VERSION;
 private ControlledPersonalMinutesArchives(){}
 public static List<RawArchive> enable(List<RawArchive> fresh){
  require(fresh!=null&&fresh.size()==17,PROFILE_UNAVAILABLE);var byType=new EnumMap<ArtifactType,RawArchive>(ArtifactType.class);
  for(var raw:fresh)require(byType.put(raw.claimedArtifactType(),raw)==null,PROFILE_UNAVAILABLE);
  var oldRender=required(byType,ArtifactType.RENDER);var render=body(oldRender);var renderDefinition=fields(render.get("definition"),"contract","rendererVersion","defaultTemplateRevisionId");literal(renderDefinition.get("rendererVersion"),ControlledMinutesArchives.RENDERER);
  var newRender=rewrite(oldRender,replace((CanonicalValue.ObjectValue)render.get("definition"),"rendererVersion",string(RENDERER)),array(render.get("dependencies")));
  var reference=reference(MaterialArchives.ref(newRender));var output=new ArrayList<RawArchive>();
  for(var raw:fresh){
   if(raw.claimedArtifactType()==ArtifactType.RENDER){output.add(newRender);continue;}
   if(raw.claimedArtifactType()!=ArtifactType.PROMPT_CONFIG&&raw.claimedArtifactType()!=ArtifactType.MODEL_CONFIG){output.add(raw);continue;}
   var value=body(raw);var dependencies=new ArrayList<CanonicalValue>();
   for(var entry:array(value.get("dependencies"))){var dependency=fields(entry,"slot","ref");dependencies.add(text(dependency.get("slot")).equals("RENDER")?object(field("slot",string("RENDER")),field("ref",reference)):entry);}
   var definition=(CanonicalValue.ObjectValue)value.get("definition");
   if(raw.claimedArtifactType()==ArtifactType.MODEL_CONFIG){var model=fields(definition,"protocolVersion","modelIdentity","credentialRef","parameters");literal(model.get("protocolVersion"),ControlledMinutesProtocol.VERSION);literal(model.get("modelIdentity"),ControlledMinutesProtocol.MODEL);definition=replace(definition,"protocolVersion",string(ControlledMinutesProtocol.PERSONAL_VERSION));}
   output.add(rewrite(raw,definition,dependencies));
  }
  return List.copyOf(output);
 }
 private static RawArchive required(Map<ArtifactType,RawArchive> values,ArtifactType type){var value=values.get(type);require(value!=null,PROFILE_UNAVAILABLE);return value;}
 private static Map<String,CanonicalValue> body(RawArchive raw){return fields(BoundedModelJson.parse(raw.bytes(),65536),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");}
 private static RawArchive rewrite(RawArchive raw,CanonicalValue definition,List<CanonicalValue> dependencies){var value=body(raw);return new RawArchive(raw.claimedArtifactType(),raw.claimedVersion(),CanonicalJson.encode(object(field("archiveSchema",value.get("archiveSchema")),field("artifactType",value.get("artifactType")),field("version",value.get("version")),field("definitionSchema",value.get("definitionSchema")),field("definition",definition),field("dependencies",new Sequence(dependencies))),65536,()->false));}
 private static CanonicalValue.ObjectValue replace(CanonicalValue.ObjectValue value,String name,CanonicalValue replacement){return new CanonicalValue.ObjectValue(value.entries().stream().map(entry->entry.key().equals(name)?field(name,replacement):entry).toList());}
 private static CanonicalValue reference(Ref ref){return object(field("artifactType",string(ref.artifactType().name())),field("version",string(ref.version())),field("sha256",string(ref.sha256())));}
}
