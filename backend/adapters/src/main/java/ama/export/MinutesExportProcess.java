package ama.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Bounded local conversion only. Paths and fonts are operator configuration, never request fields. */
public final class MinutesExportProcess {
 private static final Semaphore SLOTS=new Semaphore(2,true);
 private final Path workRoot;private final MinutesExportRenderer.Fonts fonts;private final long timeoutMillis;
 public MinutesExportProcess(Path workRoot,MinutesExportRenderer.Fonts fonts,Duration timeout){
  this.workRoot=Objects.requireNonNull(workRoot).toAbsolutePath().normalize();this.fonts=Objects.requireNonNull(fonts);
  timeoutMillis=Objects.requireNonNull(timeout).toMillis();if(timeoutMillis<1||timeoutMillis>30000)throw new IllegalArgumentException("Export conversion timeout");
  for(Path font:new Path[]{fonts.cjkCollection(),fonts.unicodeTrueType()})if(font!=null&&(!font.isAbsolute()||!Files.isRegularFile(font)))throw new IllegalArgumentException("Explicit installed font required");
 }
 public byte[] render(MinutesExportDocument document,MinutesExportRenderer.Format format,BooleanSupplier cancelled)throws IOException{
  Objects.requireNonNull(document);Objects.requireNonNull(format);Objects.requireNonNull(cancelled);MinutesExportRenderer.check(cancelled);
  if(!SLOTS.tryAcquire())throw new IOException("Export conversion capacity unavailable");
  Path directory=null;Process child=null;
  try{
   Files.createDirectories(workRoot);
   directory=Files.createTempDirectory(workRoot,"export-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
   Path input=directory.resolve("input.bin"),output=directory.resolve("output.bin"),policy=directory.resolve("policy");
   try(var data=new DataOutputStream(Files.newOutputStream(input,StandardOpenOption.CREATE_NEW))){write(data,document.title());write(data,document.body());}
   Files.setPosixFilePermissions(input,PosixFilePermissions.fromString("rw-------"));
   String classpath=classpath();Files.writeString(policy,policy(classpath,input,output,fonts),StandardOpenOption.CREATE_NEW);
   Files.setPosixFilePermissions(policy,PosixFilePermissions.fromString("rw-------"));
   var args=List.of(input.toString(),output.toString(),format.name(),fontArg(fonts.cjkCollection()),fontArg(fonts.unicodeTrueType()));
   var builder=new ProcessBuilder(command(classpath,policy,MinutesExportMain.class.getName(),args)).directory(directory.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
   builder.environment().clear();MinutesExportRenderer.check(cancelled);child=builder.start();
   long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
   while(true){
    MinutesExportRenderer.check(cancelled);long remaining=deadline-System.nanoTime();if(remaining<=0)throw new IOException("Export conversion timed out");
    if(child.waitFor(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining)+1,50),TimeUnit.MILLISECONDS))break;
   }
   MinutesExportRenderer.check(cancelled);
   if(child.exitValue()!=0||!Files.isRegularFile(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("Export conversion failed");
   long size=Files.size(output);if(size<1||size>MinutesExportRenderer.MAX_OUTPUT_BYTES)throw new IOException("Export output budget");
   try(var stream=Files.newInputStream(output)){byte[] result=stream.readNBytes(MinutesExportRenderer.MAX_OUTPUT_BYTES+1);if(result.length!=size)throw new IOException("Export output changed");MinutesExportRenderer.check(cancelled);return result;}
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException("Export interrupted");}
  finally{
   if(child!=null)stop(child);
   try{if(directory!=null){for(String name:List.of("input.bin","output.bin","policy"))Files.deleteIfExists(directory.resolve(name));Files.deleteIfExists(directory);}}
   finally{SLOTS.release();}
  }
 }
 private static void stop(Process child){
  boolean interrupted=Thread.interrupted();
  try{child.descendants().forEach(ProcessHandle::destroyForcibly);if(child.isAlive())child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);}
  catch(InterruptedException failure){interrupted=true;child.destroyForcibly();}
  finally{if(interrupted)Thread.currentThread().interrupt();}
 }
 private static void write(DataOutputStream data,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);data.writeInt(bytes.length);data.write(bytes);}
 private static String fontArg(Path font){return font==null?"":font.toString();}
 static String classpath()throws IOException{
  var locations=new LinkedHashSet<String>();
  for(Class<?> type:List.of(MinutesExportMain.class,org.apache.pdfbox.pdmodel.PDDocument.class,org.apache.fontbox.ttf.TrueTypeCollection.class,org.apache.pdfbox.io.RandomAccessReadBuffer.class,org.apache.commons.logging.LogFactory.class)){
   try{Path location=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();locations.add(location.toString());}
   catch(Exception failure){throw new IOException("Export classpath must contain explicit local classes and jars",failure);}
  }
  return String.join(File.pathSeparator,locations);
 }
 static List<String> command(String classpath,Path policy,String main,List<String> args){
  var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-XX:ActiveProcessorCount=2","-Djava.awt.headless=true","-Djava.security.manager","-Djava.security.policy=="+policy,"-cp",classpath,main));command.addAll(args);return List.copyOf(command);
 }
 static String policy(String classpath,Path input,Path output,MinutesExportRenderer.Fonts fonts){
  var policy=new StringBuilder("grant {\npermission java.util.PropertyPermission \"*\", \"read\";\npermission java.lang.RuntimePermission \"getClassLoader\";\npermission java.lang.RuntimePermission \"accessDeclaredMembers\";\npermission java.lang.RuntimePermission \"exitVM.*\";\npermission java.lang.reflect.ReflectPermission \"suppressAccessChecks\";\n");
  for(String entry:classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))){Path path=Path.of(entry).toAbsolutePath().normalize();permission(policy,path+(Files.isDirectory(path)?File.separator+"-":""),"read");}
  permission(policy,Path.of(System.getProperty("java.home"),"lib")+File.separator+"-","read");
  permission(policy,input.toString(),"read");permission(policy,output.toString(),"write");
  for(Path font:new Path[]{fonts.cjkCollection(),fonts.unicodeTrueType()})if(font!=null)permission(policy,font.toString(),"read");
  return policy.append("};\n").toString();
 }
 private static void permission(StringBuilder policy,String path,String action){policy.append("permission java.io.FilePermission \"").append(path.replace("\\","\\\\").replace("\"","\\\"")).append("\", \"").append(action).append("\";\n");}
}
