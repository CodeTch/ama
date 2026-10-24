package ama.config.file;

import ama.config.ConfigArchiveIndex.IndexBudget;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.Failure;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static ama.config.file.ControlledConfigArchiveSourceTest.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class ControlledArchiveFix1Test {
 @TempDir Path root;
 String install(int generation)throws Exception{var fixture=new ControlledConfigArchiveSourceTest();fixture.root=root;return fixture.install(generation);}
 Path object(String index,String name)throws Exception{var fixture=new ControlledConfigArchiveSourceTest();fixture.root=root;return fixture.object(index,name);}
 static IndexBudget releaseBudget(long bytes){return new IndexBudget(4194304,4096,bytes,128);}

 @Test void publicationReservesEntireIndexChainAndReopensAtExactBoundary()throws Exception{
  String old=install(1);var writer=new ControlledArchiveReleaseWriter();String published=null;Failure rejected=null;
  try{published=writer.publish(root,old,List.of(raw("processing2")),releaseBudget(4000),B,()->false);}catch(Failure failure){rejected=failure;}
  if(published!=null){
   String returned=published;assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,returned,releaseBudget(4000),B,()->false)).code());
   fail("publish incorrectly returned a final release that the same 4000-byte budget cannot reopen: "+returned);
  }
  assertNotNull(rejected);assertEquals(BUDGET_EXCEEDED,rejected.code());assertFalse(Files.exists(root.resolve("releases").resolve(text("index2.sha256"))));
  try(var releases=Files.list(root.resolve("releases"))){assertEquals(1,releases.count(),"budget refusal must precede staging");}
  String exact=writer.publish(root,old,List.of(raw("processing2")),releaseBudget(4646),B,()->false);assertEquals(text("index2.sha256"),exact);
  assertEquals(5,new ControlledConfigArchiveSource().open(root,exact,releaseBudget(4646),B,()->false).index().entries().size());
 }
 @Test void startupReleaseCapacityRefusesBeforeAnyObjectAcquisition()throws Exception{
  String index=install(1);FileTime untouched=FileTime.fromMillis(1000);
  for(String name:List.of("codec","confirmation","parser","processing1"))Files.setAttribute(object(index,name),"basic:lastAccessTime",untouched,LinkOption.NOFOLLOW_LINKS);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new ControlledConfigArchiveSource().open(root,index,releaseBudget(2500),B,()->false)).code());
  for(String name:List.of("codec","confirmation","parser","processing1"))assertEquals(untouched,Files.readAttributes(object(index,name),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).lastAccessTime(),"must refuse before reading "+name);
  assertEquals(4,new ControlledConfigArchiveSource().open(root,index,releaseBudget(2641),B,()->false).index().entries().size());
 }
 @Test void publisherImpossibleReleaseBudgetPrecedesCallerPayloadInterpretation()throws Exception{
  var malformed=new ArrayList<>(bundle());var codec=malformed.getFirst();byte[] invalid=codec.bytes().clone();invalid[0]=0;
  malformed.set(0,new RawArchive(codec.claimedArtifactType(),codec.claimedVersion(),invalid));
  // Lengths alone already prove 1884 object bytes exceed 1000; no payload decode/capture is needed.
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new ControlledArchiveReleaseWriter().publish(root,null,malformed,releaseBudget(1000),B,()->false)).code());
  assertFalse(Files.exists(root.resolve("releases")),"admission refusal must precede staging");
 }
 @Test void namedPipePublishingLockFailsUnavailableInBoundedOwnedChild()throws Exception{
  var mkfifo=new ProcessBuilder("mkfifo",root.resolve(".publish.lock").toString()).redirectErrorStream(true).start();
  try{assertTrue(mkfifo.waitFor(3,TimeUnit.SECONDS));assertEquals(0,mkfifo.exitValue());}finally{if(mkfifo.isAlive()){mkfifo.destroyForcibly();assertTrue(mkfifo.waitFor(3,TimeUnit.SECONDS));}}
  Path output=root.resolve("fifo-child.log");String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
  var child=new ProcessBuilder(java,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),ControlledArchiveFifoProbe.class.getName(),root.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
  boolean exited;
  try{exited=child.waitFor(3,TimeUnit.SECONDS);}finally{if(child.isAlive()){child.destroyForcibly();assertTrue(child.waitFor(3,TimeUnit.SECONDS),"owned child must terminate");}}
  String log=Files.readString(output);System.out.println("FIFO child exitedBeforeDeadline="+exited+"; "+log.replace('\n',' '));
  assertTrue(exited,"preexisting FIFO blocked publisher WRITE open: "+log);assertEquals(0,child.exitValue(),log);assertTrue(log.contains("UNAVAILABLE PROFILE_UNAVAILABLE"),log);
  assertFalse(Files.exists(root.resolve("releases")));
 }
}
