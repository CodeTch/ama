package ama.config.json;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09ControlledArchivesTest {
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
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
 @Test void materialReleaseArchivesPassTheStrictReaderWithFiniteParserBindings()throws Exception {
  var raw=ama.minutes.ControlledMaterialArchives.enable(build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION"));
  var state=new ArchiveReadState(B,()->false);
  for(var archive:raw)assertDoesNotThrow(()->new StrictConfigArchiveReader().read(state.capture(archive.bytes(),false),archive.claimedIdentity(),state),archive.claimedIdentity().toString());
  assertEquals(4,raw.stream().filter(r->r.claimedArtifactType()==ArtifactType.PARSER_CONFIG).count());
 }
 @Test void materialParserStillRejectsFieldsOutsideItsExactSchema()throws Exception {
  var raw=ama.material.MaterialArchives.parser("PDF");
  var bytes=new String(raw.bytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\"unitMapping\":\"PAGE\"","\"unitMapping\":\"PAGE\",\"instructionText\":\"unexpected\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var state=new ArchiveReadState(B,()->false);
  assertEquals(ama.contract.MinutesContracts.FailureCode.UNKNOWN_FIELD,assertThrows(ama.contract.MinutesContracts.Failure.class,()->new StrictConfigArchiveReader().read(state.capture(bytes,false),raw.claimedIdentity(),state)).code());
 }
 @Test void catalogAdmissionAcceptsTheExactLegacyOrMaterialDependencyClosure()throws Exception {
  var legacy=build("99999999-9999-4999-8999-999999999999","CONTROLLED_INSTALLATION");
  for(var raw:List.of(legacy,ama.minutes.ControlledMaterialArchives.enable(legacy))){
   var root=java.nio.file.Files.createDirectory(temporary.resolve("release-"+raw.size()));java.nio.file.Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
   var ib=new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128);
   String pin=new ama.config.file.ControlledArchiveReleaseWriter().publish(root,null,raw,ib,B,()->false);
   var snapshot=new ama.config.file.ControlledConfigArchiveSource().open(root,pin,ib,B,()->false);
   var prompt=ama.material.MaterialArchives.ref(raw.stream().filter(r->r.claimedArtifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow());
   var model=ama.material.MaterialArchives.ref(raw.stream().filter(r->r.claimedArtifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow());
   assertEquals(raw.size(),assertDoesNotThrow(()->ama.minutes.MinutesCatalogAdmission.capture(snapshot,prompt,model,B)).archives().size());
  }
 }
 static byte[] bytes(List<RawArchive> raw,ArtifactType kind){return raw.stream().filter(r->r.claimedArtifactType()==kind).findFirst().orElseThrow().bytes();}
}
