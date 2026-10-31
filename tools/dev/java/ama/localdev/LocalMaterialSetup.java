package ama.localdev;

import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import ama.material.MaterialArchives;
import ama.minutes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Append a distinct material-enabled release without rewriting any retained archive identity. */
public final class LocalMaterialSetup {
 public static void main(String[] args)throws Exception {
  if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||args.length!=3)throw new IllegalArgumentException("Explicit local material installation required");
  Path root=Path.of(args[0]).toRealPath();String previous=args[1],template=args[2];
  var source=new ControlledConfigArchiveSource();
  var old=source.open(root,previous,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false);
  var canonical=new CanonicalBudget(16777216,32,1000000,4194304,250000);
  var output=new ValidationBudget(8388608,32,400000,128,4194304,4194304,4194304,16777216,1000,1000,1000,10000,10000,20000);
  var proposed=ControlledMaterialArchives.enable(ControlledMinutesArchives.build("CONTROLLED_INSTALLATION",template,"ama.local.materials.v1","controlled:rich-no-sensitive",canonical,output));
  var added=new ArrayList<RawArchive>();var entries=new ArrayList<>(old.index().entries());
  for(var raw:proposed){var ref=MaterialArchives.ref(raw);var existing=entries.stream().filter(e->e.ref().identity().equals(ref.identity())).toList();
   if(!existing.isEmpty()){if(existing.size()!=1||!existing.getFirst().ref().equals(ref))throw new IllegalStateException("Retained material archive conflict");continue;}
   added.add(raw);entries.add(new ConfigArchiveIndex.Entry(ref,raw.bytes().length));
  }
  entries.sort(Comparator.comparing((ConfigArchiveIndex.Entry e)->e.ref().artifactType().name()).thenComparing(e->e.ref().version()));
  var index=new ConfigArchiveIndex(old.index().generation()+1,previous,entries);
  byte[] wire=CanonicalJson.encode(index.wire(),4194304,()->false);
  String expected=HexFormat.of().formatHex(ama.canonical.CanonicalValueCodec.digest().digest(wire));
  String pin=Files.exists(root.resolve("releases").resolve(expected),LinkOption.NOFOLLOW_LINKS)?expected:new ControlledArchiveReleaseWriter().publish(root,previous,added,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false);
  if(!expected.equals(pin))throw new IllegalStateException("Material index identity mismatch");
  var snapshot=source.open(root,pin,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false);
  var prompt=MaterialArchives.ref(proposed.stream().filter(r->r.claimedArtifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow());
  var model=MaterialArchives.ref(proposed.stream().filter(r->r.claimedArtifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow());
  snapshot.loadClosure(List.of(prompt,model),ControlledMinutesAdmin.READ,0,()->false);
  System.out.println(new ObjectMapper().writeValueAsString(Map.of("minutes.index-sha",pin,"processing.index-sha",pin,
   "minutes.prompt-version",prompt.version(),"minutes.prompt-sha",prompt.sha256(),"minutes.model-version",model.version(),"minutes.model-sha",model.sha256())));
 }
}
