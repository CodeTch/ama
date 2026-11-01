package ama.config.json;
import ama.config.*;import ama.config.file.*;import ama.contract.ConfigArchiveContracts.*;import ama.material.MaterialArchives;import ama.minutes.*;import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
/** Exercises cumulative retained release admission, including both historical model protocols. */
class RegionReleaseRetentionTest {
 @TempDir Path temporary;
 @Test void cumulativeHistoricalAndRegionArchivesFitTheExplicitLocalBudget()throws Exception{
  Path root=Files.createDirectory(temporary.resolve("archive"));Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
  var writer=new ControlledArchiveReleaseWriter();var roots=new ArrayList<List<RawArchive>>();var retained=new HashMap<Identity,RawArchive>();String pin=null;byte[] original=null;
  for(int stage=0;stage<4;stage++){
   var raw=ControlledMinutesArchives.build("CONTROLLED_INSTALLATION","99999999-9999-4999-8999-999999999999","retained.release."+stage,"controlled:rich-no-sensitive",Task09ControlledArchivesTest.C,Task09ControlledArchivesTest.V);
   if(stage>=2)raw=ControlledPersonalMinutesArchives.enable(raw);if(stage==3)raw=ControlledRegionMinutesArchives.enable(raw);raw=ControlledMaterialArchives.enable(raw);roots.add(raw);
   var additions=new ArrayList<RawArchive>();for(var item:raw){var prior=retained.putIfAbsent(item.claimedIdentity(),item);if(prior==null)additions.add(item);else assertArrayEquals(prior.bytes(),item.bytes());}
   pin=assertDoesNotThrow(()->writer.publish(root,previousPin(root),additions,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false));Files.writeString(root.resolve("test-pin"),pin);if(stage==0)original=Files.readAllBytes(root.resolve("releases").resolve(pin).resolve("index.json"));
  }
  var snapshot=new ControlledConfigArchiveSource().open(root,pin,ControlledMinutesAdmin.INDEX,ControlledMinutesAdmin.READ,()->false);
  for(int stage=0;stage<roots.size();stage++){var raw=roots.get(stage);var prompt=MaterialArchives.ref(raw.stream().filter(r->r.claimedArtifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow());var model=MaterialArchives.ref(raw.stream().filter(r->r.claimedArtifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow());assertEquals("ama.controlled-minutes-http.v"+(stage<2?1:stage==2?2:3),MinutesCatalogAdmission.capture(snapshot,prompt,model,ControlledMinutesAdmin.READ).protocolVersion());}
  assertNotNull(original);assertEquals(retained.size(),snapshot.index().entries().size());
 }
 private static String previousPin(Path root)throws Exception{return Files.exists(root.resolve("test-pin"))?Files.readString(root.resolve("test-pin")):null;}
}
