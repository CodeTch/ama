package ama.material;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** File parsing has a private JVM, bounded heap/CPU/time and no network, process or ambient-file grants. */
public final class MaterialParseProcess implements MaterialParser {
 private static final Semaphore SLOTS=new Semaphore(2,true);static final int MAX_BYTES=32*1024*1024;
 private final Path root;private final long timeout;
 public MaterialParseProcess(){this(Path.of(System.getProperty("java.io.tmpdir")),Duration.ofSeconds(25));}
 public MaterialParseProcess(Path root,Duration timeout){this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();this.timeout=timeout.toMillis();if(this.timeout<1||this.timeout>30000)throw new IllegalArgumentException("Material parse timeout");}
 public Result parse(InputStream source,String name,String expected,Budget budget,BooleanSupplier cancelled)throws ParseFailure {
  if(source==null||name==null||name.length()>4096||expected==null||!expected.matches("[0-9a-f]{64}")||budget==null||cancelled==null)throw new IllegalArgumentException("Exact bounded parse input required");check(cancelled);
  if(!SLOTS.tryAcquire())throw new ParseFailure("PARSE_BUSY");Path directory=null;Process child=null;
  try{
   Files.createDirectories(root);directory=Files.createTempDirectory(root,"ama-material-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
   Path input=directory.resolve("input.bin"),output=directory.resolve("output.bin"),policy=directory.resolve("policy");
   int bound=(int)Math.min(MAX_BYTES,budget.sourceBytes());var content=new ByteArrayOutputStream();byte[] block=new byte[8192];int count;
   while((count=source.read(block,0,Math.min(block.length,bound+1-content.size())))!=-1){check(cancelled);content.write(block,0,count);if(content.size()>bound)throw new ParseFailure("SOURCE_BYTES_EXCEEDED");}
   try(var data=new DataOutputStream(Files.newOutputStream(input,StandardOpenOption.CREATE_NEW))){text(data,name);text(data,expected);data.writeLong(bound);data.writeLong(Math.min(8L*1024*1024,budget.utf16()));data.writeInt(Math.min(10000,budget.units()));data.writeInt(content.size());content.writeTo(data);}
   Files.setPosixFilePermissions(input,PosixFilePermissions.fromString("rw-------"));String cp=classpath();Files.writeString(policy,policy(cp,input,output),StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(policy,PosixFilePermissions.fromString("rw-------"));
   var builder=new ProcessBuilder(command(cp,policy,MaterialParseMain.class.getName(),List.of(input.toString(),output.toString()))).directory(directory.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);builder.environment().clear();check(cancelled);child=builder.start();long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
   while(true){check(cancelled);long remaining=deadline-System.nanoTime();if(remaining<=0)throw new ParseFailure("PARSE_TIMEOUT");if(child.waitFor(Math.min(50,TimeUnit.NANOSECONDS.toMillis(remaining)+1),TimeUnit.MILLISECONDS))break;}
   check(cancelled);if(child.exitValue()!=0||!Files.isRegularFile(output,LinkOption.NOFOLLOW_LINKS)||Files.size(output)>64L*1024*1024)throw new ParseFailure("PARSE_PROCESS_FAILED");
   try(var data=new DataInputStream(Files.newInputStream(output))){if(data.readBoolean()){String code=text(data,128);if(!code.matches("[A-Z_]{1,64}")||data.read()!=-1)throw new IOException("Invalid parse error");throw new ParseFailure(code);}String format=text(data,16),hash=text(data,64);int size=data.readInt();if(!Set.of("TXT","PDF","WORD","PPT").contains(format)||!expected.equals(hash)||size<1||size>Math.min(10000,budget.units()))throw new IOException("Invalid parse receipt");var units=new ArrayList<Unit>();long used=0;for(int n=0;n<size;n++){long ordinal=data.readLong();String kind=text(data,32);Long page=nullable(data),paragraph=nullable(data);String section=data.readBoolean()?text(data,4096):null;String body=text(data,24*1024*1024);used+=body.length();if(ordinal!=n+1||!Set.of("PAGE","PARAGRAPH").contains(kind)||used>Math.min(budget.utf16(),8L*1024*1024))throw new IOException("Invalid parse coordinates");units.add(new Unit(ordinal,kind,page,paragraph,section,body));}int length=data.readInt();if(length<1||length>MAX_BYTES)throw new IOException("Parse text budget");byte[] bytes=data.readNBytes(length);if(bytes.length!=length||data.read()!=-1||!Arrays.equals(bytes,String.join("\n\n",units.stream().map(Unit::body).toList()).getBytes(StandardCharsets.UTF_8)))throw new IOException("Parse text mismatch");check(cancelled);return new Result(format,hash,units,bytes);}
  }catch(ParseFailure failure){throw failure;}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new ParseFailure("CANCELLED");}catch(IOException|RuntimeException failed){throw new ParseFailure("PARSE_PROCESS_FAILED");}
  finally{if(child!=null)stop(child);try{if(directory!=null){for(String nameToDelete:List.of("input.bin","output.bin","policy"))Files.deleteIfExists(directory.resolve(nameToDelete));Files.deleteIfExists(directory);}}catch(IOException failure){java.util.logging.Logger.getLogger(MaterialParseProcess.class.getName()).warning("Owned material parsing scratch cleanup failed");}finally{SLOTS.release();}}
 }
 static void check(BooleanSupplier stop)throws ParseFailure{if(stop.getAsBoolean()||Thread.currentThread().isInterrupted())throw new ParseFailure("CANCELLED");}
 static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
 static String text(DataInputStream in,int max)throws IOException{int size=in.readInt();if(size<0||size>max)throw new IOException("Bounded parse string required");byte[] bytes=in.readNBytes(size);if(bytes.length!=size)throw new EOFException();return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
 private static Long nullable(DataInputStream data)throws IOException{long n=data.readLong();if(n< -1||n==0)throw new IOException("Invalid source coordinate");return n==-1?null:n;}
 private static void stop(Process child){boolean interrupted=Thread.interrupted();try{child.descendants().forEach(ProcessHandle::destroyForcibly);if(child.isAlive())child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException failure){interrupted=true;child.destroyForcibly();}finally{if(interrupted)Thread.currentThread().interrupt();}}
 static String classpath()throws IOException{var locations=new LinkedHashSet<String>();for(Class<?> type:List.of(MaterialParseMain.class,MaterialParser.class,org.apache.pdfbox.pdmodel.PDDocument.class,org.apache.fontbox.ttf.TrueTypeCollection.class,org.apache.pdfbox.io.RandomAccessReadBuffer.class,org.apache.commons.logging.LogFactory.class)){try{locations.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().toString());}catch(Exception invalid){throw new IOException("Explicit local parser classpath required",invalid);}}return String.join(File.pathSeparator,locations);}
 static List<String> command(String cp,Path policy,String main,List<String> args){var cmd=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-XX:ActiveProcessorCount=2","-Djava.awt.headless=true","-Djava.security.manager","-Djava.security.policy=="+policy,"-cp",cp,main));cmd.addAll(args);return cmd;}
 static String policy(String cp,Path input,Path output){var s=new StringBuilder("grant {\npermission java.util.PropertyPermission \"*\", \"read\";\npermission java.lang.RuntimePermission \"getClassLoader\";\npermission java.lang.RuntimePermission \"accessDeclaredMembers\";\npermission java.lang.RuntimePermission \"exitVM.*\";\npermission java.lang.reflect.ReflectPermission \"suppressAccessChecks\";\n");for(String entry:cp.split(java.util.regex.Pattern.quote(File.pathSeparator))){Path p=Path.of(entry);permission(s,p+(Files.isDirectory(p)?File.separator+"-":""),"read");}permission(s,Path.of(System.getProperty("java.home"),"lib")+File.separator+"-","read");permission(s,input.toString(),"read");permission(s,output.toString(),"write");return s.append("};\n").toString();}
 private static void permission(StringBuilder s,String path,String mode){s.append("permission java.io.FilePermission \"").append(path.replace("\\","\\\\").replace("\"","\\\"")).append("\", \"").append(mode).append("\";\n");}
}
