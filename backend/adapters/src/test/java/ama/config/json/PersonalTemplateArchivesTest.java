package ama.config.json;

import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.material.MaterialArchives;
import ama.minutes.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalTemplateArchivesTest extends Task09ControlledArchivesTest {
 MinutesCatalogAdmission.Selection admission(List<RawArchive> raw,String name)throws Exception{
  var root=Files.createDirectory(temporary.resolve(name));Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
  var ib=new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128);String pin=new ama.config.file.ControlledArchiveReleaseWriter().publish(root,null,raw,ib,B,()->false);
  var snapshot=new ama.config.file.ControlledConfigArchiveSource().open(root,pin,ib,B,()->false);
  var prompt=MaterialArchives.ref(raw.stream().filter(r->r.claimedArtifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow());
  var model=MaterialArchives.ref(raw.stream().filter(r->r.claimedArtifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow());
  return MinutesCatalogAdmission.capture(snapshot,prompt,model,B);
 }
 @Test void personalCapabilityUsesDistinctExplicitRendererAndProtocolWithoutChangingOriginalArchives()throws Exception{
  var old=build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION");var before=old.stream().map(RawArchive::bytes).toList();
  var personal=ControlledPersonalMinutesArchives.enable(old);
  assertEquals("ama.controlled-minutes-http.v1",admission(old,"legacy").protocolVersion());
  assertEquals("ama.controlled-minutes-http.v2",admission(personal,"personal").protocolVersion());
  assertEquals("ama.controlled-minutes-http.v2",admission(ControlledMaterialArchives.enable(personal),"personal-material").protocolVersion());
  for(int n=0;n<old.size();n++)assertArrayEquals(before.get(n),old.get(n).bytes());
  assertArrayEquals(bytes(old,ArtifactType.CANONICAL_CODEC),bytes(personal,ArtifactType.CANONICAL_CODEC));
  assertFalse(Arrays.equals(bytes(old,ArtifactType.RENDER),bytes(personal,ArtifactType.RENDER)));
  assertFalse(Arrays.equals(bytes(old,ArtifactType.PROMPT_CONFIG),bytes(personal,ArtifactType.PROMPT_CONFIG)));
  assertFalse(Arrays.equals(bytes(old,ArtifactType.MODEL_CONFIG),bytes(personal,ArtifactType.MODEL_CONFIG)));
 }
 @Test void aMixedPersonalRendererAndLegacyModelIsNotAnInstalledCapability()throws Exception{
  var old=build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION");var next=ControlledPersonalMinutesArchives.enable(old);
  var mixed=new ArrayList<RawArchive>();for(var raw:next){if(raw.claimedArtifactType()==ArtifactType.MODEL_CONFIG){String text=new String(raw.bytes(),java.nio.charset.StandardCharsets.UTF_8).replace("ama.controlled-minutes-http.v2","ama.controlled-minutes-http.v1");mixed.add(new RawArchive(raw.claimedArtifactType(),raw.claimedVersion(),text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}else mixed.add(raw);}
  assertThrows(RuntimeException.class,()->admission(mixed,"mixed"));
 }
}
