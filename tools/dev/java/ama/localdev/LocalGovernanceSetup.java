package ama.localdev;
import ama.canonical.*;
import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.governance.GovernanceCatalog;
import ama.material.MaterialArchives;
import ama.minutes.ControlledMinutesAdmin;
import ama.model.BoundedModelJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;
import static ama.config.WireValues.*;

/** Explicit local installation of the fixed prompt catalog, before API/worker startup. */
public final class LocalGovernanceSetup {
 public static void main(String[] args)throws Exception{
  if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||args.length!=4)throw new IllegalArgumentException("Explicit governed prompt installation required");
  Path root=Path.of(args[0]).toRealPath();String previous=args[1];var source=new ControlledConfigArchiveSource();var old=source.open(root,previous,ControlledMinutesAdmin.INDEX,LocalArchiveBudget.READ,()->false);
  var base=old.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.PROMPT_CONFIG&&r.version().equals(args[2])&&r.sha256().equals(args[3])).findFirst().orElseThrow();
  var raw=old.loadClosure(List.of(base),LocalArchiveBudget.READ,0,()->false).stream().filter(r->r.claimedIdentity().equals(base.identity())).findFirst().orElseThrow();var body=fields(BoundedModelJson.parse(raw.bytes(),65536),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");
  String baseline=text(fields(body.get("definition"),"instructionText").get("instructionText"));var additions=new ArrayList<RawArchive>();var entries=new ArrayList<>(old.index().entries());
  for(String key:GovernanceCatalog.PROMPTS.keySet().stream().sorted().toList()){
   String version=GovernanceCatalog.promptArchiveVersion(base.sha256(),key);var value=object(field("archiveSchema",body.get("archiveSchema")),field("artifactType",body.get("artifactType")),field("version",string(version)),field("definitionSchema",body.get("definitionSchema")),field("definition",object(field("instructionText",string(baseline+"\n"+GovernanceCatalog.PROMPTS.get(key))))),field("dependencies",body.get("dependencies")));
   var archive=new RawArchive(ArtifactType.PROMPT_CONFIG,version,CanonicalJson.encode(value,65536,()->false));additions.add(archive);entries.add(new ConfigArchiveIndex.Entry(MaterialArchives.ref(archive),archive.bytes().length));
  }
  entries.sort(Comparator.comparing((ConfigArchiveIndex.Entry e)->e.ref().artifactType().name()).thenComparing(e->e.ref().version()));var index=new ConfigArchiveIndex(old.index().generation()+1,previous,entries);String expected=HexFormat.of().formatHex(CanonicalValueCodec.digest().digest(CanonicalJson.encode(index.wire(),4194304,()->false)));
  String pin=Files.exists(root.resolve("releases").resolve(expected),LinkOption.NOFOLLOW_LINKS)?expected:new ControlledArchiveReleaseWriter().publish(root,previous,additions,ControlledMinutesAdmin.INDEX,LocalArchiveBudget.READ,()->false);if(!expected.equals(pin))throw new IllegalStateException("Governed prompt index mismatch");source.open(root,pin,ControlledMinutesAdmin.INDEX,LocalArchiveBudget.READ,()->false);
  System.out.println(new ObjectMapper().writeValueAsString(Map.of("minutes.index-sha",pin,"processing.index-sha",pin)));
 }
}
