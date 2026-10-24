package ama.config.json;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09ControlledArchivesTest {
 static final ArchiveReadBudget B=new ArchiveReadBudget(65536,1048576,64,64,32,200000,32,255,65536,262144,1048576,65536,32,250000,500000);
 static final CanonicalBudget C=new CanonicalBudget(16777216,32,1000000,4194304,250000);
 static final ValidationBudget V=new ValidationBudget(8388608,32,400000,128,4194304,4194304,4194304,16777216,1000,1000,1000,10000,10000,20000);
 @SuppressWarnings("unchecked") List<RawArchive> build(String template,String mode)throws Exception {
  Class<?> type=assertDoesNotThrow(()->Class.forName("ama.minutes.ControlledMinutesArchives"));return (List<RawArchive>)type.getMethod("build",String.class,String.class,String.class,String.class,CanonicalBudget.class,ValidationBudget.class).invoke(null,mode,template,"task09.synthetic.1","Controlled synthetic minutes.",C,V);
 }
 @Test void completeFifteenProfilesAndBothRootsUseStrictCanonicalOriginalBytes()throws Exception {
  var raw=build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION");assertEquals(17,raw.size());var state=new ArchiveReadState(B,()->false);var types=new HashSet<ArtifactType>();
  for(var archive:raw){assertTrue(types.add(archive.claimedArtifactType()));var parsed=new StrictConfigArchiveReader().read(state.capture(archive.bytes(),false),archive.claimedIdentity(),state);assertEquals(archive.claimedIdentity(),parsed.identity());var deps=ConfigSchema.dependencies(parsed.wire(),state);if(Set.of(ArtifactType.PROMPT_CONFIG,ArtifactType.MODEL_CONFIG).contains(archive.claimedArtifactType()))assertEquals(15,deps.size());}
  assertEquals(EnumSet.allOf(ProfileKind.class).size()+2,types.size());
 }
 @Test void actualTemplateIdentityChangesBothRootBindingsWithoutChangingSharedCodec()throws Exception {
  var first=build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION");var second=build("99999999-9999-4999-8999-999999999998","CONTROLLED_INSTALLATION");
  for(var kind:List.of(ArtifactType.RENDER,ArtifactType.PROMPT_CONFIG,ArtifactType.MODEL_CONFIG))assertFalse(Arrays.equals(bytes(first,kind),bytes(second,kind)));
  assertArrayEquals(bytes(first,ArtifactType.CANONICAL_CODEC),bytes(second,ArtifactType.CANONICAL_CODEC));assertThrows(Exception.class,()->build("99999999-9999-4999-8999-999999999999","AUTO"));
 }
 static byte[] bytes(List<RawArchive> raw,ArtifactType kind){return raw.stream().filter(r->r.claimedArtifactType()==kind).findFirst().orElseThrow().bytes();}
}
