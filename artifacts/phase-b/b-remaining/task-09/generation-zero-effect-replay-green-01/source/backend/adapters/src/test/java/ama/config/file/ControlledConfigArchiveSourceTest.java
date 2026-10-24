package ama.config.file;

import ama.config.*;
import ama.config.ConfigArchiveIndex.*;
import ama.config.json.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.Failure;
import ama.contract.MinutesContracts.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URI;
import java.util.*;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.FailureCode.*;

class ControlledConfigArchiveSourceTest {
 @TempDir Path root;
 static final IndexBudget I=new IndexBudget(4194304,4096,67108864,128);
 static final ArchiveReadBudget B=new ArchiveReadBudget(65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000);
 static byte[] fixture(String name)throws Exception{try(var in=ControlledConfigArchiveSourceTest.class.getResourceAsStream("/ama/config/file/b06/"+name)){return Objects.requireNonNull(in).readAllBytes();}}
 static String text(String name)throws Exception{return new String(fixture(name),StandardCharsets.UTF_8);}
 static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
 static RawArchive raw(String name)throws Exception{
  return switch(name){
   case "codec"->new RawArchive(ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1",fixture(name+".json"));
   case "parser"->new RawArchive(ArtifactType.PARSER_CONFIG,"ama.strict-utf8-lines.v1",fixture(name+".json"));
   case "confirmation"->new RawArchive(ArtifactType.CONFIRMATION_HASH,"ama.transcript-confirmation.v1",fixture(name+".json"));
   default->new RawArchive(ArtifactType.PROCESSING_CONFIG,"b06.controlled.import-txt."+name.substring(10),fixture(name+".json"));
  };
 }
 static List<RawArchive> bundle()throws Exception{return List.of(raw("codec"),raw("confirmation"),raw("parser"),raw("processing1"));}
 static Ref ref(String name)throws Exception{var r=raw(name);return new Ref(r.claimedArtifactType(),r.claimedVersion(),sha(r.bytes()));}
 Path object(String index,String name)throws Exception{return root.resolve("releases").resolve(index).resolve("objects").resolve(ref(name).sha256()+".json");}
 String install(int generation)throws Exception{
  if(generation==2)install(1);
  String index=text("index"+generation+".sha256");Path release=root.resolve("releases").resolve(index);Files.createDirectories(release.resolve("objects"));
  Files.write(release.resolve("index.json"),fixture("index"+generation+".json"));
  for(String name:generation==1?List.of("codec","confirmation","parser","processing1"):List.of("codec","confirmation","parser","processing1","processing2"))Files.write(object(index,name),raw(name).bytes());
  return index;
 }
 ConfigArchiveSource.Snapshot open(String index){return new ControlledConfigArchiveSource().open(root,index,I,B,()->false);}
 String modifiedIndex(String original,String replacement)throws Exception{
  byte[] bytes=replacement.getBytes(StandardCharsets.UTF_8);String digest=sha(bytes);Path dest=root.resolve("releases").resolve(digest);Files.createDirectories(dest.resolve("objects"));Files.write(dest.resolve("index.json"),bytes);
  try(var files=Files.list(root.resolve("releases").resolve(original).resolve("objects"))){for(var file:files.toList())Files.copy(file,dest.resolve("objects").resolve(file.getFileName()));}return digest;
 }
 static ArchiveReadBudget budget(long total,long tokens,long scalars,int artifacts,int depth){return new ArchiveReadBudget(65536,total,artifacts,32,depth,tokens,32,255,65536,262144,scalars,65536,32,250000,500000);}

 @Test void pinnedHistoryLoadsExactOldClosureWithFreshTransportArrays()throws Exception{
  String index=install(2);var snapshot=assertDoesNotThrow(()->open(index));var first=snapshot.loadClosure(List.of(ref("processing1")),B,fixture("manifest1.json").length,()->false);
  assertEquals(4,first.size());assertEquals(5,snapshot.index().entries().size());assertEquals(2,snapshot.index().generation());
  for(var node:first)assertArrayEquals(raw(node.claimedArtifactType()==ArtifactType.PROCESSING_CONFIG?"processing1":node.claimedArtifactType()==ArtifactType.CANONICAL_CODEC?"codec":node.claimedArtifactType()==ArtifactType.PARSER_CONFIG?"parser":"confirmation").bytes(),node.bytes());
  first.getFirst().bytes()[0]=0;var second=snapshot.loadClosure(List.of(ref("processing1")),B,0,()->false);assertEquals('{',second.getFirst().bytes()[0]);
  assertThrows(UnsupportedOperationException.class,()->snapshot.index().entries().clear());
 }
 @Test void noLatestOrMissingHistoryFallback()throws Exception{
  String index=install(2);assertThrows(Failure.class,()->open("0".repeat(64)));assertThrows(Failure.class,()->open(null));
  Files.delete(root.resolve("releases").resolve(text("index1.sha256")).resolve("index.json"));assertThrows(Failure.class,()->open(index));
 }
 @Test void everyLoadRechecksLengthEofHashAndMissingObjects()throws Exception{
  String index=install(1);var s=open(index);Path file=object(index,"processing1");byte[] original=Files.readAllBytes(file);byte[] changed=original.clone();changed[0]='[';Files.write(file,changed);
  assertThrows(Failure.class,()->s.loadClosure(List.of(ref("processing1")),B,0,()->false));Files.write(file,Arrays.copyOf(original,original.length+1));assertThrows(Failure.class,()->s.loadClosure(List.of(ref("processing1")),B,0,()->false));
  Files.delete(file);assertThrows(Failure.class,()->s.loadClosure(List.of(ref("processing1")),B,0,()->false));
 }
 @Test void startupValidatesAllObjectsIncludingUnselectedHistory()throws Exception{
  String index=install(2);Files.write(object(index,"processing1"),new byte[]{'{','}'});assertThrows(Failure.class,()->open(index));
 }
 @Test void releaseObjectsCannotContainUnindexedFiles()throws Exception{
  String index=install(1);Files.write(object(index,"codec").getParent().resolve("extra.json"),fixture("codec.json"));assertThrows(Failure.class,()->open(index));
 }
 @Test void historyRemapDropAndGenerationGapFailClosed()throws Exception{
  String index=install(2);String wire=text("index2.json");
  String remap=modifiedIndex(index,wire.replace(ref("processing1").sha256(),"0".repeat(64)));assertThrows(Failure.class,()->open(remap));
  String gap=modifiedIndex(index,wire.replace("\"generation\":2","\"generation\":3"));assertThrows(Failure.class,()->open(gap));
  String drop=modifiedIndex(index,wire.replaceFirst("\\{\\\"artifactType\\\":\\\"CANONICAL_CODEC\\\"[^}]*},", ""));assertThrows(Failure.class,()->open(drop));
 }
 @Test void strictIndexRejectsUnknownDuplicateNoncanonicalAndInvalidUtf8()throws Exception{
  var reader=new StrictConfigIndexReader();assertEquals(4,reader.read(fixture("index1.json"),I,()->false).entries().size());
  for(byte[] invalid:List.of((" "+text("index1.json")).getBytes(StandardCharsets.UTF_8),text("index1.json").replace("\"generation\":1","\"generation\":1,\"generation\":1").getBytes(StandardCharsets.UTF_8),text("index1.json").replace("indexSchema","unknown").getBytes(StandardCharsets.UTF_8),text("index1.json").replace("ama.config-index.v1","ama.config-index.v2").getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xc0,(byte)0xaf},("\ufeff"+text("index1.json")).getBytes(StandardCharsets.UTF_8)))assertThrows(Failure.class,()->reader.read(invalid,I,()->false));
 }
 @Test void indexEntryOrderUniquenessAndSizeAreStrict()throws Exception{
  String index=install(1);String wire=text("index1.json");
  assertThrows(Failure.class,()->open(modifiedIndex(index,wire.replace("\"sizeBytes\":"+fixture("codec.json").length,"\"sizeBytes\":0"))));
  assertThrows(Failure.class,()->open(modifiedIndex(index,wire.replace("CANONICAL_CODEC","ZZZ"))));
  String entries=wire.substring(wire.indexOf('[')+1,wire.indexOf(']'));String[] parts=entries.split("(?<=}),(?=\\{)");String reordered=wire.replace(entries,parts[1]+","+parts[0]+","+parts[2]+","+parts[3]);assertThrows(Failure.class,()->open(modifiedIndex(index,reordered)));
 }
 @Test void boundedStartupSharesTokenScalarAndByteBudgets()throws Exception{
  String index=install(1);assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,index,I,budget(1048576,70,1048576,16,8),()->false));
  assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,index,I,budget(1048576,200000,350,16,8),()->false));
  assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,index,I,budget(300,200000,1048576,16,8),()->false));
  assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,index,new IndexBudget(20,4096,67108864,128),B,()->false));
  assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,index,new IndexBudget(4194304,3,67108864,128),B,()->false));
 }
 @Test void closureBudgetsReserveManifestBeforeAnyTransportAllocation()throws Exception{
  var s=open(install(1));assertThrows(Failure.class,()->s.loadClosure(List.of(ref("processing1")),budget(2000,200000,1048576,16,8),1900,()->false));
  assertThrows(Failure.class,()->s.loadClosure(List.of(ref("processing1")),budget(1048576,200000,1048576,3,8),0,()->false));
  assertThrows(Failure.class,()->s.loadClosure(List.of(ref("processing1")),budget(1048576,200000,1048576,16,1),0,()->false));
  assertThrows(Failure.class,()->s.loadClosure(List.of(new Ref(ArtifactType.PROCESSING_CONFIG,ref("processing1").version(),"0".repeat(64))),B,0,()->false));
 }
 @Test void symlinkRootAncestorObjectDirectoryAndReplacementAreRejected()throws Exception{
  String index=install(1);Path link=root.resolveSibling(root.getFileName()+"-link");Files.createSymbolicLink(link,root);
  assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(link,index,I,B,()->false));
  Path file=object(index,"processing1");Path elsewhere=root.resolve("saved");Files.move(file,elsewhere);Files.createSymbolicLink(file,elsewhere);assertThrows(Failure.class,()->open(index));Files.delete(file);Files.move(elsewhere,file);
  var snapshot=open(index);Path objects=file.getParent();Path moved=objects.resolveSibling("saved-objects");Files.move(objects,moved);Files.createSymbolicLink(objects,moved);assertThrows(Failure.class,()->snapshot.loadClosure(List.of(ref("processing1")),B,0,()->false));
 }
 @Test void nonregularFilesAndUnsupportedSecureFilesystemFailUnavailable()throws Exception{
  String index=install(1);Path file=object(index,"codec");Files.delete(file);Files.createDirectory(file);assertThrows(Failure.class,()->open(index));
  try(var zip=FileSystems.newFileSystem(URI.create("jar:"+root.resolve("unsupported.zip").toUri()),Map.of("create","true"))){assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(zip.getPath("/"),index,I,B,()->false));}
 }
 @Test void cancellationAndInterruptFailClosedWithoutPayloadDiagnostics()throws Exception{
  String index=install(1);var s=open(index);Failure f=assertThrows(Failure.class,()->s.loadClosure(List.of(ref("processing1")),B,0,()->true));assertEquals(CANCELLED,f.code());assertNull(f.getCause());assertEquals(0,f.getSuppressed().length);
  Thread.currentThread().interrupt();try{assertEquals(CANCELLED,assertThrows(Failure.class,()->open(index)).code());assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
 @Test void publisherCreatesLiteralReleasesAndPreservesPriorBytes()throws Exception{
  var writer=new ControlledArchiveReleaseWriter();String one=writer.publish(root,null,bundle(),I,B,()->false);assertEquals(text("index1.sha256"),one);
  byte[] old=Files.readAllBytes(root.resolve("releases").resolve(one).resolve("index.json"));String two=writer.publish(root,one,List.of(raw("processing2")),I,B,()->false);assertEquals(text("index2.sha256"),two);
  assertArrayEquals(old,Files.readAllBytes(root.resolve("releases").resolve(one).resolve("index.json")));assertEquals(4,open(two).loadClosure(List.of(ref("processing1")),B,0,()->false).size());
  assertFalse(Files.isSameFile(object(one,"codec"),object(two,"codec")));
 }
 @Test void publisherRejectsRemapDuplicateMissingClosureAndCancellationWithoutChangingOldRelease()throws Exception{
  String one=install(1);var writer=new ControlledArchiveReleaseWriter();
  assertThrows(Failure.class,()->writer.publish(root,one,List.of(raw("processing1")),I,B,()->false));
  assertThrows(Failure.class,()->writer.publish(root,null,List.of(raw("processing2")),I,B,()->false));
  assertThrows(Failure.class,()->writer.publish(root,one,List.of(raw("processing2")),I,B,()->true));
  assertEquals(4,open(one).index().entries().size());try(var files=Files.list(root.resolve("releases"))){assertEquals(1,files.count());}
 }
 @Test void manifestDiscoveryReturnsHistoricalRefsAndNeverRepairsMalformedBytes()throws Exception{
  var discovery=new ProcessingManifestRefs(new StrictProcessingManifestReader());var refs=discovery.roots(fixture("manifest1.json"),B,()->false);assertEquals(Set.of(ref("processing1"),ref("parser"),ref("codec"),ref("confirmation")),Set.copyOf(refs));
  assertThrows(Failure.class,()->discovery.roots("{}".getBytes(StandardCharsets.UTF_8),B,()->false));assertThrows(Failure.class,()->discovery.roots(fixture("manifest1.json"),B,()->true));
  assertThrows(Failure.class,()->discovery.roots(fixture("manifest1.json"),budget(100,200000,1048576,16,8),()->false));
 }
 @Test void initialPublicationCannotResetAnExistingReleaseHistory()throws Exception{
  String old=install(1);var replacement=new ArrayList<>(bundle());replacement.set(3,raw("processing2"));
  assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,null,replacement,I,B,()->false));assertEquals(4,open(old).index().entries().size());
 }
 @Test void heldPublishingLockAndLockSymlinkRejectSecondPublisher()throws Exception{
  Path lockFile=root.resolve(".publish.lock");
  try(var channel=FileChannel.open(lockFile,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);var lock=channel.lock()){
   assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,null,bundle(),I,B,()->false));
  }
  Files.delete(lockFile);Path target=root.resolve("lock-target");Files.write(target,new byte[0]);Files.createSymbolicLink(lockFile,target);
  assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,null,bundle(),I,B,()->false));assertEquals(0,Files.size(target));
 }
 static boolean stageHasObject(Path root){
  Path releases=root.resolve("releases");if(!Files.isDirectory(releases))return false;
  try(var stages=Files.list(releases)){
   for(var stage:stages.filter(p->p.getFileName().toString().startsWith(".staging-")).toList()){
    Path objects=stage.resolve("objects");if(Files.isDirectory(objects))try(var files=Files.list(objects)){if(files.findAny().isPresent())return true;}
   }
   return false;
  }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
 }
 @Test void cancellationAfterPartialStagingLeavesPreviousReleaseIntact()throws Exception{
  String old=install(1);AtomicBoolean partial=new AtomicBoolean();
  assertEquals(CANCELLED,assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,old,List.of(raw("processing2")),I,B,()->{boolean cancel=stageHasObject(root);if(cancel)partial.set(true);return cancel;})).code());
  assertTrue(partial.get());assertEquals(4,open(old).index().entries().size());assertFalse(Files.exists(root.resolve("releases").resolve(text("index2.sha256"))));assertTrue(stageHasObject(root));
 }
 @Test void publisherWritesSameIsolatedBytesEvenWhenCallerMutatesTransportAfterValidation()throws Exception{
  var input=new ArrayList<>(bundle());var mutable=input.getFirst().bytes();byte[] original=mutable.clone();AtomicBoolean changed=new AtomicBoolean();
  String index=new ControlledArchiveReleaseWriter().publish(root,null,input,I,B,()->{if(stageHasObject(root)&&changed.compareAndSet(false,true))Arrays.fill(mutable,(byte)0);return false;});
  assertTrue(changed.get());assertArrayEquals(original,Files.readAllBytes(object(index,"codec")));assertEquals(4,open(index).index().entries().size());
 }
 @Test void versionsUseUnsignedUtf8OrderingRatherThanUtf16Ordering()throws Exception{
  String old=install(1);byte[] base=fixture("processing1.json");String first="\ue000",second="\ud800\udc00";
  var a=new RawArchive(ArtifactType.PROCESSING_CONFIG,first,new String(base,StandardCharsets.UTF_8).replace("b06.controlled.import-txt.1",first).getBytes(StandardCharsets.UTF_8));
  var z=new RawArchive(ArtifactType.PROCESSING_CONFIG,second,new String(base,StandardCharsets.UTF_8).replace("b06.controlled.import-txt.1",second).getBytes(StandardCharsets.UTF_8));
  String index=new ControlledArchiveReleaseWriter().publish(root,old,List.of(z,a),I,B,()->false);
  var versions=open(index).index().entries().stream().filter(e->e.ref().artifactType()==ArtifactType.PROCESSING_CONFIG).map(e->e.ref().version()).toList();assertEquals(List.of("b06.controlled.import-txt.1",first,second),versions);
 }
 @Test void missingDependencyCycleAndWrongHeaderAreNeverPublishable()throws Exception{
  var wrong=new RawArchive(ArtifactType.PARSER_CONFIG,"ama.strict-utf8-lines.v1",fixture("codec.json"));assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,null,List.of(wrong),I,B,()->false));
  var self=new Ref(ArtifactType.PROCESSING_CONFIG,"b06.controlled.import-txt.1","0".repeat(64));String cycle=new String(fixture("processing1.json"),StandardCharsets.UTF_8).replace("\"artifactType\":\"PARSER_CONFIG\"","\"artifactType\":\"PROCESSING_CONFIG\"");
  var bundle=new ArrayList<>(bundle());bundle.set(3,new RawArchive(self.artifactType(),self.version(),cycle.getBytes(StandardCharsets.UTF_8)));assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,null,bundle,I,B,()->false));
  var missing=new ArrayList<>(bundle());missing.set(3,new RawArchive(ArtifactType.PROCESSING_CONFIG,"b06.controlled.import-txt.1",text("processing1.json").replace(ref("parser").sha256(),"0".repeat(64)).getBytes(StandardCharsets.UTF_8)));assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,null,missing,I,B,()->false));
 }
 static long descriptorCount()throws Exception{try(var files=Files.list(Path.of("/proc/self/fd"))){return files.count();}}
 @Test void cancellationAtDirectoryAcquisitionBoundariesClosesEveryHandle()throws Exception{
  String index=install(1);open(index);long baseline=descriptorCount();
  for(int boundary=1;boundary<=24;boundary++){
   final int limit=boundary;var calls=new java.util.concurrent.atomic.AtomicInteger();
   assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,index,I,B,()->calls.incrementAndGet()>=limit));
   assertEquals(baseline,descriptorCount(),"cancel boundary "+boundary);
  }
 }
 @Test void controlledLiteralClosureRemainsAcceptableToUnchangedS1()throws Exception{
  String meeting="11111111-1111-4111-8111-111111111111",batch="33333333-3333-4333-8333-333333333333",file="44444444-4444-4444-8444-444444444444",hash="559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd";
  var batchRow=new FullRow(Table.INPUT_BATCH,List.of(new TextCell(batch),new TextCell(meeting),new TextCell("TRANSCRIPT"),new TextCell("0".repeat(64)),new UIntCell(9),new TextCell("2026-10-23T00:00:00.000000Z")));
  var member=new FullRow(Table.INPUT_BATCH_FILE,List.of(new TextCell(batch),new UIntCell(1),new TextCell(file),new TextCell(hash)));
  var facts=new StoredProvenanceFacts(List.of(batchRow,member),List.of(new ContentFile(file,hash,1,meeting)),List.of(),List.of(),B.maxFactRows());
  byte[] original=fixture("manifest1.json");var roots=new ProcessingManifestRefs(new StrictProcessingManifestReader()).roots(original,B,()->false);
  var archives=open(install(2)).loadClosure(roots,B,original.length,()->false);assertEquals(4,archives.size());
  var binding=new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader()).computeProcessing(original,facts,archives,B,()->false);
  assertEquals(text("manifest1.business.sha256"),binding.fingerprint());assertEquals("b06.controlled.import-txt.1",binding.processing().version());
 }
}
