package ama.config.file;

import ama.config.*;
import ama.config.ConfigArchiveIndex.*;
import ama.config.json.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.Failure;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URI;
import java.util.*;
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
}
