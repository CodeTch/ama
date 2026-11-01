package ama.config.json;
import ama.minutes.*;import ama.contract.ConfigArchiveContracts.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class RegionArchivesTest extends PersonalTemplateArchivesTest {
 @Test void regionReleaseHasDistinctInputOutputAndProtocolWhileRetainingOldReleases()throws Exception{
  var old=ControlledPersonalMinutesArchives.enable(build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION"));var before=old.stream().map(RawArchive::bytes).toList();
  var region=ControlledRegionMinutesArchives.enable(old);var selected=admission(ControlledMaterialArchives.enable(region),"region-material");
  assertEquals("ama.controlled-minutes-http.v3",selected.protocolVersion());assertTrue(selected.personalTemplates());
  assertEquals("ama.generation-input.v3",region.stream().filter(r->r.claimedArtifactType()==ArtifactType.GENERATION_HASH).findFirst().orElseThrow().claimedVersion());
  assertEquals("ama.minutes-output.v2",region.stream().filter(r->r.claimedArtifactType()==ArtifactType.OUTPUT_SCHEMA).findFirst().orElseThrow().claimedVersion());
  for(int i=0;i<old.size();i++)assertArrayEquals(before.get(i),old.get(i).bytes());
 }
 @Test void v4IsAFreshReleaseAndOldRegionArchiveBytesAreNeverRewritten()throws Exception{var personal=ControlledPersonalMinutesArchives.enable(build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION"));var old=ControlledRegionMinutesArchives.enable(personal);var before=old.stream().map(RawArchive::bytes).toList();var safe=ControlledRegionMinutesArchives.enable(personal,true);assertEquals("ama.controlled-minutes-http.v4",admission(ControlledMaterialArchives.enable(safe),"source-exclusions").protocolVersion());for(int i=0;i<old.size();i++)assertArrayEquals(before.get(i),old.get(i).bytes());}
}
