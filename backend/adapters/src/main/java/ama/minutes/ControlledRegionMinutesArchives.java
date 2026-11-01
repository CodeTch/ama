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

/** Transform a fresh personal-capable release; persisted archive identities remain immutable. */
public final class ControlledRegionMinutesArchives {
 private ControlledRegionMinutesArchives(){}
 public static List<RawArchive> enable(List<RawArchive> fresh){return enable(fresh,false);}
 public static List<RawArchive> enable(List<RawArchive> fresh,boolean sourceExclusions){
  require(fresh!=null&&fresh.size()==17,PROFILE_UNAVAILABLE);var replacements=new EnumMap<ArtifactType,RawArchive>(ArtifactType.class);
  for(var raw:fresh){String version=switch(raw.claimedArtifactType()){case GENERATION_HASH->"ama.generation-input.v3";case OUTPUT_SCHEMA->"ama.minutes-output.v2";default->null;};if(version!=null){var b=body(raw);replacements.put(raw.claimedArtifactType(),rewrite(raw,version,object(field("contract",string(version))),array(b.get("dependencies"))));}}
  require(replacements.size()==2,PROFILE_UNAVAILABLE);var result=new ArrayList<RawArchive>();
  for(var raw:fresh){var replacement=replacements.get(raw.claimedArtifactType());if(replacement!=null){result.add(replacement);continue;}
   if(raw.claimedArtifactType()!=ArtifactType.PROMPT_CONFIG&&raw.claimedArtifactType()!=ArtifactType.MODEL_CONFIG){result.add(raw);continue;}
   var b=body(raw);var dependencies=new ArrayList<CanonicalValue>();for(var entry:array(b.get("dependencies"))){var dependency=fields(entry,"slot","ref");var next=replacements.get(ArtifactType.valueOf(text(dependency.get("slot"))));dependencies.add(next==null?entry:object(field("slot",dependency.get("slot")),field("ref",reference(MaterialArchives.ref(next)))));}
   var definition=(CanonicalValue.ObjectValue)b.get("definition");if(raw.claimedArtifactType()==ArtifactType.MODEL_CONFIG){literal(fields(definition,"protocolVersion","modelIdentity","credentialRef","parameters").get("protocolVersion"),ControlledMinutesProtocol.PERSONAL_VERSION);definition=new CanonicalValue.ObjectValue(definition.entries().stream().map(e->e.key().equals("protocolVersion")?field(e.key(),string(sourceExclusions?ControlledMinutesProtocol.SOURCE_EXCLUSION_VERSION:ControlledMinutesProtocol.REGION_VERSION)):e).toList());}
   result.add(rewrite(raw,raw.claimedVersion(),definition,dependencies));
  }return List.copyOf(result);
 }
 private static Map<String,CanonicalValue> body(RawArchive raw){return fields(BoundedModelJson.parse(raw.bytes(),65536),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");}
 private static RawArchive rewrite(RawArchive raw,String version,CanonicalValue definition,List<CanonicalValue> dependencies){var b=body(raw);return new RawArchive(raw.claimedArtifactType(),version,CanonicalJson.encode(object(field("archiveSchema",b.get("archiveSchema")),field("artifactType",b.get("artifactType")),field("version",string(version)),field("definitionSchema",b.get("definitionSchema")),field("definition",definition),field("dependencies",new Sequence(dependencies))),65536,()->false));}
 private static CanonicalValue reference(Ref ref){return object(field("artifactType",string(ref.artifactType().name())),field("version",string(ref.version())),field("sha256",string(ref.sha256())));}
}
