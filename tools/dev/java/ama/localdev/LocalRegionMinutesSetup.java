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

/** Append a new region-generation release; all prior profiles and execution lookup pins remain valid. */
public final class LocalRegionMinutesSetup {
 public static void main(String[] args)throws Exception{
  if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||!(args.length==3||args.length==4&&args[3].equals("SENSITIVE_SCENARIO")))throw new IllegalArgumentException("Explicit local region-generation installation required");
  Path root=Path.of(args[0]).toRealPath();var standard=install(root,args[1],args[2],false,false);var sensitive=install(root,standard.get("minutes.index-sha"),args[2],true,false);
  var safeStandard=install(root,sensitive.get("minutes.index-sha"),args[2],false,true);var safeSensitive=install(root,safeStandard.get("minutes.index-sha"),args[2],true,true);
  var selected=new HashMap<String,String>(args.length==4?safeSensitive:safeStandard);selected.put("minutes.index-sha",safeSensitive.get("minutes.index-sha"));selected.put("processing.index-sha",safeSensitive.get("processing.index-sha"));System.out.println(new ObjectMapper().writeValueAsString(selected));
 }
 private static Map<String,String> install(Path root,String previous,String template,boolean sensitive,boolean safe)throws Exception{
  var source=new ControlledConfigArchiveSource();var old=source.open(root,previous,ControlledMinutesAdmin.INDEX,LocalArchiveBudget.READ,()->false);
  var canonical=new CanonicalBudget(16777216,32,1000000,4194304,250000);
  var output=new ValidationBudget(8388608,32,400000,128,4194304,4194304,4194304,16777216,1000,1000,1000,10000,10000,20000);
  String release=safe?(sensitive?"ama.local.source-exclusions-sensitive.v1":"ama.local.source-exclusions.v1"):sensitive?"ama.local.region-generations-sensitive.v1":"ama.local.region-generations.v1",instruction=sensitive?"controlled:rich-output":"controlled:rich-no-sensitive";
  var proposed=ControlledMaterialArchives.enable(ControlledRegionMinutesArchives.enable(ControlledPersonalMinutesArchives.enable(ControlledMinutesArchives.build("CONTROLLED_INSTALLATION",template,release,instruction,canonical,output)),safe));
  var added=new ArrayList<RawArchive>();var entries=new ArrayList<>(old.index().entries());
  for(var raw:proposed){
   var ref=MaterialArchives.ref(raw);var existing=entries.stream().filter(entry->entry.ref().identity().equals(ref.identity())).toList();
   if(!existing.isEmpty()){if(existing.size()!=1||!existing.getFirst().ref().equals(ref))throw new IllegalStateException("Retained region-generation archive conflict");continue;}
   added.add(raw);entries.add(new ConfigArchiveIndex.Entry(ref,raw.bytes().length));
  }
  entries.sort(Comparator.comparing((ConfigArchiveIndex.Entry entry)->entry.ref().artifactType().name()).thenComparing(entry->entry.ref().version()));
  var index=new ConfigArchiveIndex(old.index().generation()+1,previous,entries);
  String expected=HexFormat.of().formatHex(CanonicalValueCodec.digest().digest(CanonicalJson.encode(index.wire(),4194304,()->false)));
  String pin=Files.exists(root.resolve("releases").resolve(expected),LinkOption.NOFOLLOW_LINKS)?expected:new ControlledArchiveReleaseWriter().publish(root,previous,added,ControlledMinutesAdmin.INDEX,LocalArchiveBudget.READ,()->false);
  if(!expected.equals(pin))throw new IllegalStateException("Personal-template index identity mismatch");
  var snapshot=source.open(root,pin,ControlledMinutesAdmin.INDEX,LocalArchiveBudget.READ,()->false);
  var prompt=MaterialArchives.ref(proposed.stream().filter(raw->raw.claimedArtifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow());
  var model=MaterialArchives.ref(proposed.stream().filter(raw->raw.claimedArtifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow());
  var selection=MinutesCatalogAdmission.capture(snapshot,prompt,model,LocalArchiveBudget.READ);
  if(!selection.personalTemplates()||!ama.model.ControlledMinutesProtocol.regionCapable(selection.protocolVersion()))throw new IllegalStateException("Personal-template capability unavailable");
  return Map.of("minutes.index-sha",pin,"processing.index-sha",pin,
   "minutes.prompt-version",prompt.version(),"minutes.prompt-sha",prompt.sha256(),"minutes.model-version",model.version(),"minutes.model-sha",model.sha256());
 }
}
