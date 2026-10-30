package ama.minutes;

import ama.canonical.CanonicalValue;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.material.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;
import static ama.config.WireValues.*;

/** Opt-in installation of a fresh minutes release with finite material parser bindings. Keeps old archives immutable. */
public final class ControlledMaterialArchives {
 public static List<RawArchive> enable(List<RawArchive> freshMinutesArchives){var base=new ArrayList<RawArchive>();var parserRefs=new ArrayList<Ref>();for(String format:MaterialProfiles.FORMATS){var parser=MaterialArchives.parser(format);base.add(parser);parserRefs.add(MaterialArchives.ref(parser));}
  var codec=freshMinutesArchives.stream().filter(r->r.claimedArtifactType()==ArtifactType.CANONICAL_CODEC).findFirst().orElseThrow();var dependencies=new TreeMap<String,Ref>();dependencies.put("CANONICAL_CODEC",MaterialArchives.ref(codec));var bindings=new ArrayList<CanonicalValue>();for(int i=0;i<MaterialProfiles.FORMATS.size();i++){var parser=parserRefs.get(i);dependencies.put("PARSER_SHA256_"+parser.sha256(),parser);bindings.add(object(field("format",string(MaterialProfiles.FORMATS.get(i))),field("parser",reference(parser))));}
  var depValues=new ArrayList<CanonicalValue>();for(var d:dependencies.entrySet())depValues.add(object(field("slot",string(d.getKey())),field("ref",reference(d.getValue()))));var catalog=archive(ArtifactType.CATALOG,MaterialProfiles.CATALOG,"ama.material-catalog.v2",object(field("contract",string(MaterialProfiles.CATALOG)),field("bindings",new Sequence(bindings))),depValues);base.add(catalog);var catalogRef=MaterialArchives.ref(catalog);
  for(var raw:freshMinutesArchives){if(raw.claimedArtifactType()==ArtifactType.CATALOG)continue;if(raw.claimedArtifactType()!=ArtifactType.PROMPT_CONFIG&&raw.claimedArtifactType()!=ArtifactType.MODEL_CONFIG){base.add(raw);continue;}var fields=fields(ama.model.BoundedModelJson.parse(raw.bytes(),65536),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");var changed=new ArrayList<CanonicalValue>();for(var value:array(fields.get("dependencies"))){var dependency=fields(value,"slot","ref");changed.add(text(dependency.get("slot")).equals("CATALOG")?object(field("slot",string("CATALOG")),field("ref",reference(catalogRef))):value);}base.add(archive(raw.claimedArtifactType(),raw.claimedVersion(),text(fields.get("definitionSchema")),fields.get("definition"),changed));}
  return List.copyOf(base);
 }
 private static RawArchive archive(ArtifactType type,String version,String schema,CanonicalValue definition,List<CanonicalValue> dependencies){return new RawArchive(type,version,CanonicalJson.encode(object(field("archiveSchema",string("ama.config-archive.v1")),field("artifactType",string(type.name())),field("version",string(version)),field("definitionSchema",string(schema)),field("definition",definition),field("dependencies",new Sequence(dependencies))),65536,()->false));}
 private static CanonicalValue reference(Ref ref){return object(field("artifactType",string(ref.artifactType().name())),field("version",string(ref.version())),field("sha256",string(ref.sha256())));}
 private ControlledMaterialArchives(){}
}
