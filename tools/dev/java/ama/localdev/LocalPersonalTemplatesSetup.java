package ama.localdev;

import ama.canonical.CanonicalValueCodec;
import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import ama.material.MaterialArchives;
import ama.minutes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Append a new personal-template release; all prior profiles and execution lookup pins remain valid. */
public final class LocalPersonalTemplatesSetup {
 public static void main(String[] args)throws Exception{
  if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||args.length!=3)throw new IllegalArgumentException("Explicit local personal-template installation required");
  Path root=Path.of(args[0]).toRealPath();String previous=args[1],template=args[2];
  var source=new ControlledConfigArchiveSource();var old=source.open(root,previous,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false);
  var canonical=new CanonicalBudget(16777216,32,1000000,4194304,250000);
  var output=new ValidationBudget(8388608,32,400000,128,4194304,4194304,4194304,16777216,1000,1000,1000,10000,10000,20000);
  var proposed=ControlledMaterialArchives.enable(ControlledPersonalMinutesArchives.enable(ControlledMinutesArchives.build("CONTROLLED_INSTALLATION",template,"ama.local.personal-templates.v1","controlled:rich-no-sensitive",canonical,output)));
  var added=new ArrayList<RawArchive>();var entries=new ArrayList<>(old.index().entries());
  for(var raw:proposed){
   var ref=MaterialArchives.ref(raw);var existing=entries.stream().filter(entry->entry.ref().identity().equals(ref.identity())).toList();
   if(!existing.isEmpty()){if(existing.size()!=1||!existing.getFirst().ref().equals(ref))throw new IllegalStateException("Retained personal-template archive conflict");continue;}
   added.add(raw);entries.add(new ConfigArchiveIndex.Entry(ref,raw.bytes().length));
  }
  entries.sort(Comparator.comparing((ConfigArchiveIndex.Entry entry)->entry.ref().artifactType().name()).thenComparing(entry->entry.ref().version()));
  var index=new ConfigArchiveIndex(old.index().generation()+1,previous,entries);
  String expected=HexFormat.of().formatHex(CanonicalValueCodec.digest().digest(CanonicalJson.encode(index.wire(),4194304,()->false)));
  String pin=Files.exists(root.resolve("releases").resolve(expected),LinkOption.NOFOLLOW_LINKS)?expected:new ControlledArchiveReleaseWriter().publish(root,previous,added,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false);
  if(!expected.equals(pin))throw new IllegalStateException("Personal-template index identity mismatch");
  var snapshot=source.open(root,pin,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false);
  var prompt=MaterialArchives.ref(proposed.stream().filter(raw->raw.claimedArtifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow());
  var model=MaterialArchives.ref(proposed.stream().filter(raw->raw.claimedArtifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow());
  var selection=MinutesCatalogAdmission.capture(snapshot,prompt,model,ControlledMinutesAdmin.READ);
  if(!selection.personalTemplates())throw new IllegalStateException("Personal-template capability unavailable");
  System.out.println(new ObjectMapper().writeValueAsString(Map.of("minutes.index-sha",pin,"processing.index-sha",pin,
   "minutes.prompt-version",prompt.version(),"minutes.prompt-sha",prompt.sha256(),"minutes.model-version",model.version(),"minutes.model-sha",model.sha256())));
 }
}
