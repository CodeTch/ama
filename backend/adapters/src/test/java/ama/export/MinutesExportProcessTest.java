package ama.export;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MinutesExportProcessTest {
 @TempDir Path directory;
 private MinutesExportProcess process(Duration timeout){return new MinutesExportProcess(directory,new MinutesExportRenderer.Fonts(null,null),timeout);}
 @Test void actualChildReturnsExactMarkdownAndWordWithoutReadingLiteralResources()throws Exception{
  Path secret=directory.resolve("not-an-export-input.txt");Files.writeString(secret,"PRIVATE_SENTINEL");
  String body="## 中文😀\n![private]("+secret.toUri()+")\n<img src=\"http://127.0.0.1:1/private\">";
  var source=new MinutesExportDocument("已保存",body);var process=process(Duration.ofSeconds(10));
  assertEquals("# 已保存\n\n"+body+"\n",new String(process.render(source,MinutesExportRenderer.Format.MD,()->false),StandardCharsets.UTF_8));
  byte[] word=process.render(source,MinutesExportRenderer.Format.DOCX,()->false);
  assertEquals('P',word[0]);assertEquals('K',word[1]);assertEquals(List.of(secret),remaining());
 }
 @Test void timeoutAndCancellationKillOwnedChildAndRemovePartialFiles()throws Exception{
  var before=ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();
  var source=new MinutesExportDocument("title","body".repeat(200000));
  assertThrows(java.io.IOException.class,()->process(Duration.ofMillis(1)).render(source,MinutesExportRenderer.Format.DOCX,()->false));
  var calls=new AtomicInteger();
  assertThrows(CancellationException.class,()->process(Duration.ofSeconds(10)).render(source,MinutesExportRenderer.Format.DOCX,()->calls.incrementAndGet()>3));
  assertTrue(remaining().isEmpty());
  assertTrue(ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).allMatch(child->before.contains(child.pid())));
 }
 @Test void failedConversionCannotReturnPartialPdf()throws Exception{
  assertThrows(java.io.IOException.class,()->process(Duration.ofSeconds(10)).render(new MinutesExportDocument("中文","unsupported fonts"),MinutesExportRenderer.Format.PDF,()->false));
  assertTrue(remaining().isEmpty());
 }
 @Test void exactChildPolicyRejectsFilesSocketsExecAndSecurityManagerRemoval()throws Exception{
  Path secret=directory.resolve("forbidden.txt");Files.writeString(secret,"PRIVATE");
  Path input=directory.resolve("input");Files.writeString(input,"allowed");Path output=directory.resolve("output");
  Path policy=directory.resolve("policy");
  String classpath=MinutesExportProcess.classpath()+java.io.File.pathSeparator+Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  Files.writeString(policy,MinutesExportProcess.policy(classpath,input,output,new MinutesExportRenderer.Fonts(null,null)));
  var command=MinutesExportProcess.command(classpath,policy,Probe.class.getName(),List.of(input.toString(),output.toString(),secret.toString()));
  var builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);builder.environment().clear();
  Process child=builder.start();assertTrue(child.waitFor(10,TimeUnit.SECONDS));assertEquals(0,child.exitValue());assertEquals("DENIED:4",Files.readString(output));
 }
 private List<Path> remaining()throws Exception{try(var files=Files.list(directory)){return files.toList();}}
 @SuppressWarnings("removal") public static class Probe {
  public static void main(String[] args)throws Exception{
   if(System.getSecurityManager()==null)System.exit(20);
   if(!Files.readString(Path.of(args[0])).equals("allowed"))System.exit(21);
   int denied=0;
   try{Files.readString(Path.of(args[2]));}catch(SecurityException expected){denied++;}
   try(var socket=new java.net.Socket("127.0.0.1",1)){}catch(SecurityException expected){denied++;}
   try{new ProcessBuilder("/bin/true").start();}catch(SecurityException expected){denied++;}
   try{System.setSecurityManager(null);}catch(SecurityException expected){denied++;}
   Files.writeString(Path.of(args[1]),"DENIED:"+denied,StandardOpenOption.CREATE_NEW);
  }
 }
}
