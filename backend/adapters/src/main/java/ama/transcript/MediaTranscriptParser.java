package ama.transcript;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static ama.transcript.TranscriptDocumentParser.ErrorCode.*;

/** Explicit media profile: verified original bytes enter a confined child; only complete bounded output returns. */
public final class MediaTranscriptParser implements MeasuredTranscriptDocumentParser {
 public static final String LEGACY_PROFILE_VERSION="ama.media-documents.v1";
 public static final String PROFILE_VERSION="ama.media-documents.v2";
 public static final List<String> LEGACY_FORMATS=List.of("TXT","DOC","DOCX","PDF","PPTX","MP3","WAV","M4A","MP4","MOV","MKV","WEBM");
 public static final List<String> FORMATS=List.of("TXT","DOC","DOCX","PDF","PPTX","MP3","WAV","M4A","MP4","MOV","MKV","WEBM","AAC","FLAC","OGG","M4V","AVI","SRT","VTT");
 public static boolean supportsProfile(String version){return PROFILE_VERSION.equals(version)||LEGACY_PROFILE_VERSION.equals(version);}
 public static List<String> formats(String version){if(!supportsProfile(version))throw new IllegalArgumentException("Unsupported media profile");return LEGACY_PROFILE_VERSION.equals(version)?LEGACY_FORMATS:FORMATS;}
 private static final long SOURCE_LIMIT=32L*1024*1024;
 public record Config(Path python,Path runner,Path model,Path manifest,String manifestSha256,long timeoutMillis){
  public Config {if(python==null||runner==null||model==null||manifest==null||manifestSha256==null||!manifestSha256.matches("[0-9a-f]{64}")||timeoutMillis<1||timeoutMillis>3600000)throw new IllegalArgumentException("Explicit bounded media engine required");}
 }
 /** Local immutable installation metadata is never accepted from an HTTP request. Keep the venv path, not its resolved interpreter symlink. */
 public static Config configured(String expectedProof){String path=System.getenv("AMA_LOCAL_ASR_RUNTIME");if(path==null)throw new IllegalStateException("Media engine not installed");var value=fromRuntime(Path.of(path));if(!value.manifestSha256().equals(expectedProof))throw new IllegalStateException("Media engine profile mismatch");return value;}
 public static Config fromRuntime(Path path){
  try{if(path==null||Files.isSymbolicLink(path))throw new IOException();byte[] raw;try(var input=Files.newInputStream(path)){raw=input.readNBytes(16385);}if(raw.length>16384)throw new IOException();var mapper=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var v=mapper.readTree(raw);if(v==null||!fields(v,Set.of("schema","python","runner","model","manifest","manifestSha256","timeoutMillis"))||!v.path("schema").asText().equals("ama.local-asr-runtime.v1"))throw new IOException();for(String field:List.of("python","runner","model","manifest","manifestSha256"))if(!v.path(field).isTextual()||v.path(field).textValue().length()>4096)throw new IOException();var result=new Config(Path.of(v.path("python").textValue()).toAbsolutePath().normalize(),Path.of(v.path("runner").textValue()).toRealPath(),Path.of(v.path("model").textValue()).toRealPath(),Path.of(v.path("manifest").textValue()).toRealPath(),v.path("manifestSha256").textValue(),number(v,"timeoutMillis"));if(!Files.isExecutable(result.python())||!Files.isRegularFile(result.runner())||!Files.isDirectory(result.model())||!manifestProof(result.manifest()).equals(result.manifestSha256()))throw new IOException();return result;}catch(IOException|ParseException|RuntimeException invalid){throw new IllegalStateException("Media engine configuration unavailable");}
 }
 private final Config config;
 private final String profileVersion;
 public MediaTranscriptParser(Config config){this(config,PROFILE_VERSION);}
 public MediaTranscriptParser(Config config,String profileVersion){this.config=Objects.requireNonNull(config);if(!supportsProfile(profileVersion))throw new IllegalArgumentException("Unsupported media profile");this.profileVersion=profileVersion;}
 public Result parse(InputStream source,UUID file,String hash,ParseBudget budget,BooleanSupplier cancelled)throws ParseException{return parseMeasured(source,file,hash,budget,cancelled).result();}
 public MeasuredResult parseMeasured(InputStream source,UUID file,String hash,ParseBudget budget,BooleanSupplier cancelled,String sourceName)throws ParseException {
  if(sourceName==null||sourceName.length()>1048576)throw new ParseException(INVALID_ARGUMENT);int dot=sourceName.lastIndexOf('.');String format=dot<0?"":sourceName.substring(dot+1).toUpperCase(Locale.ROOT);if(!formats(profileVersion).contains(format))throw new ParseException(INVALID_ARGUMENT);
  return parseWithFormat(source,file,hash,budget,cancelled,format);
 }
 public MeasuredResult parseMeasured(InputStream source,UUID file,String hash,ParseBudget budget,BooleanSupplier cancelled)throws ParseException {return parseWithFormat(source,file,hash,budget,cancelled,null);}
 private MeasuredResult parseWithFormat(InputStream source,UUID file,String hash,ParseBudget budget,BooleanSupplier cancelled,String format)throws ParseException {
  if(source==null||file==null||hash==null||!hash.matches("[0-9a-f]{64}")||budget==null||budget.maxSourceBytes()<=0||budget.maxUtf16CodeUnits()<=0||budget.maxSegments()<=0||cancelled==null)throw new ParseException(INVALID_ARGUMENT);
  check(cancelled);Path directory=null;
  try{
   directory=Files.createTempDirectory("ama-media-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));Path input=directory.resolve("source.bin");long size=copy(source,input,Math.min(SOURCE_LIMIT,budget.maxSourceBytes()),hash,cancelled);byte[] prefix;
   try(var in=Files.newInputStream(input)){prefix=in.readNBytes(16);}
   if(format==null?!media(prefix):OfficeTranscriptParser.FORMATS.contains(format)){
    try(var in=Files.newInputStream(input)){var parsed=new OfficeTranscriptParser().parseMeasured(in,file,hash,budget,cancelled);var segments=parsed.result().segments().stream().map(s->new Segment(stable(file,hash,s.paragraphNo()),file,s.paragraphNo(),s.text(),null,null,null)).toList();return new MeasuredResult(new Result(segments,hash,profileVersion),parsed.usage());}
   }
   byte[] output=invoke(input,directory,budget,cancelled,format);check(cancelled);return decode(output,file,hash,size,budget);
  }catch(ParseException e){throw e;}catch(InterruptedIOException e){Thread.currentThread().interrupt();throw new ParseException(CANCELLED);}catch(IOException e){check(cancelled);throw new ParseException(SOURCE_READ_FAILED);}finally{if(directory!=null)cleanup(directory);}
 }
 private byte[] invoke(Path input,Path directory,ParseBudget budget,BooleanSupplier cancelled,String format)throws ParseException {
  Process child=null;var reason=new AtomicReference<ErrorCode>();Thread parent=Thread.currentThread();var scheduler=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"ama-media-cancel");thread.setDaemon(true);return thread;});
  try{
   if(!Files.isExecutable(config.python())||!Files.isRegularFile(config.runner())||!Files.isDirectory(config.model())||Files.isSymbolicLink(config.manifest())||Files.size(config.manifest())>262144||!manifestProof(config.manifest()).equals(config.manifestSha256()))throw new ParseException(ENGINE_UNAVAILABLE);
   var command=new ArrayList<>(List.of(config.python().toString(),config.runner().toString(),"--input",input.toString(),"--model",config.model().toString(),"--manifest",config.manifest().toString(),"--expected-manifest-sha256",config.manifestSha256(),"--max-units",Long.toString(budget.maxUtf16CodeUnits()),"--max-segments",Integer.toString(budget.maxSegments()),"--max-seconds","7200","--max-source-bytes",Long.toString(Math.min(SOURCE_LIMIT,budget.maxSourceBytes()))));
   if(format!=null){command.add("--format");command.add(format);}
   var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);builder.environment().put("HF_HUB_OFFLINE","1");builder.environment().put("HF_HUB_DISABLE_TELEMETRY","1");builder.environment().put("OMP_NUM_THREADS","4");child=builder.start();var process=child;long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.timeoutMillis());
   scheduler.scheduleWithFixedDelay(()->{try{if(parent.isInterrupted()||cancelled.getAsBoolean()||System.nanoTime()-deadline>=0){reason.compareAndSet(null,CANCELLED);stop(process);}}catch(RuntimeException failure){reason.compareAndSet(null,CANCELLED);stop(process);}},0,25,TimeUnit.MILLISECONDS);
   int maximum=(int)Math.min(8L*1024*1024,65536+Math.min(8L*1024*1024,budget.maxUtf16CodeUnits())*8);
   byte[] output=process.getInputStream().readNBytes(maximum+1);if(output.length>maximum)throw new ParseException(UTF16_CODE_UNITS_EXCEEDED);int exit=process.waitFor();if(reason.get()!=null)throw new ParseException(reason.get());check(cancelled);if(exit!=0){if(output.length>512)throw new ParseException(ENGINE_UNAVAILABLE);var failure=new ObjectMapper().readTree(output);if(failure==null||!fields(failure,Set.of("error"))||!failure.path("error").isTextual())throw new ParseException(ENGINE_UNAVAILABLE);}if(reason.get()!=null)throw new ParseException(reason.get());check(cancelled);return output;
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ParseException(CANCELLED);}catch(IOException e){if(reason.get()!=null)throw new ParseException(reason.get());check(cancelled);throw new ParseException(ENGINE_UNAVAILABLE);}finally{scheduler.shutdownNow();if(child!=null)stop(child);}
 }
 private MeasuredResult decode(byte[] raw,UUID file,String sourceHash,long size,ParseBudget budget)throws ParseException {
  try{
   var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength((int)Math.max(256,Math.min(8L*1024*1024,budget.maxUtf16CodeUnits()))).maxNumberLength(19).build()).build();var mapper=new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var value=mapper.readTree(raw);
   if(value!=null&&value.isObject()&&fields(value,Set.of("error"))){String code=value.path("error").asText();throw new ParseException(switch(code){case "SOURCE_BYTES_EXCEEDED"->SOURCE_BYTES_EXCEEDED;case "INVALID_UTF8"->INVALID_UTF8;case "INVALID_TEXT"->INVALID_TEXT;case "SOURCE_READ_FAILED"->SOURCE_READ_FAILED;case "UTF16_CODE_UNITS_EXCEEDED"->UTF16_CODE_UNITS_EXCEEDED;case "SEGMENTS_EXCEEDED"->SEGMENTS_EXCEEDED;case "NO_TEXT"->NO_TEXT;case "AUDIO_REQUIRED"->AUDIO_REQUIRED;case "AUDIO_DURATION_EXCEEDED"->AUDIO_DURATION_EXCEEDED;case "MODEL_INTEGRITY","MODEL_UNAVAILABLE"->ENGINE_UNAVAILABLE;default->INVALID_MEDIA;});}
   if(value==null||!fields(value,Set.of("schema","modelManifestSha256","language","durationMs","decodedUtf16CodeUnits","segments"))||!value.path("schema").asText().equals("ama.local-asr-result.v1")||!value.path("modelManifestSha256").asText().equals(config.manifestSha256())||!value.path("language").isTextual())throw new ParseException(INVALID_MEDIA);
   long duration=number(value,"durationMs"),reported=number(value,"decodedUtf16CodeUnits");if(duration<1||duration>7200000)throw new ParseException(INVALID_MEDIA);if(reported>budget.maxUtf16CodeUnits())throw new ParseException(UTF16_CODE_UNITS_EXCEEDED);var items=value.get("segments");if(items==null||!items.isArray())throw new ParseException(INVALID_MEDIA);if(items.isEmpty())throw new ParseException(NO_TEXT);if(items.size()>budget.maxSegments())throw new ParseException(SEGMENTS_EXCEEDED);
   var segments=new ArrayList<Segment>();long units=0,previous=-1,ordinal=1;
   for(var item:items){if(!fields(item,Set.of("text","startMs","endMs"))||!item.path("text").isTextual())throw new ParseException(INVALID_MEDIA);String text=item.get("text").textValue();long start=number(item,"startMs"),end=number(item,"endMs");if(text.isBlank()||text.indexOf(0)>=0||start<previous||end<=start||end>duration)throw new ParseException(INVALID_MEDIA);for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new ParseException(INVALID_TEXT);}else if(Character.isLowSurrogate(c))throw new ParseException(INVALID_TEXT);}if(text.length()+1L>budget.maxUtf16CodeUnits()-units)throw new ParseException(UTF16_CODE_UNITS_EXCEEDED);units+=text.length()+1L;segments.add(new Segment(stable(file,sourceHash,ordinal),file,ordinal++,text,null,start,end));previous=start;}
   if(reported<units)throw new ParseException(INVALID_MEDIA);return new MeasuredResult(new Result(segments,sourceHash,profileVersion),new Usage(size,reported,segments.size()));
  }catch(ParseException e){throw e;}catch(IOException|RuntimeException e){throw new ParseException(INVALID_MEDIA);}
 }
 private static long number(JsonNode value,String key)throws ParseException{var n=value.get(key);if(n==null||!n.isIntegralNumber()||!n.canConvertToLong()||n.longValue()<0)throw new ParseException(INVALID_MEDIA);return n.longValue();}
 private static boolean fields(JsonNode value,Set<String> keys){if(!value.isObject())return false;var actual=new HashSet<String>();value.fieldNames().forEachRemaining(actual::add);return actual.equals(keys);}
 private static long copy(InputStream source,Path destination,long limit,String expected,BooleanSupplier cancelled)throws IOException,ParseException{var digest=digest();long used=0;byte[] buffer=new byte[8192];try(var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){while(true){check(cancelled);int count=source.read(buffer,0,(int)Math.min(buffer.length,limit-used+1));check(cancelled);if(count<0)break;if(count>limit-used)throw new ParseException(SOURCE_BYTES_EXCEEDED);used+=count;digest.update(buffer,0,count);out.write(buffer,0,count);}}if(!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new ParseException(SOURCE_HASH_MISMATCH);return used;}
 private boolean media(byte[] p){return PROFILE_VERSION.equals(profileVersion)&&(p.length>=4&&p[0]=='f'&&p[1]=='L'&&p[2]=='a'&&p[3]=='C'||p.length>=4&&p[0]=='O'&&p[1]=='g'&&p[2]=='g'&&p[3]=='S'||p.length>=12&&p[0]=='R'&&p[1]=='I'&&p[2]=='F'&&p[3]=='F'&&p[8]=='A'&&p[9]=='V'&&p[10]=='I'&&p[11]==' ')||p.length>=12&&p[0]=='R'&&p[1]=='I'&&p[2]=='F'&&p[3]=='F'&&p[8]=='W'&&p[9]=='A'&&p[10]=='V'&&p[11]=='E'||p.length>=3&&p[0]=='I'&&p[1]=='D'&&p[2]=='3'||p.length>=2&&(p[0]&255)==255&&(p[1]&224)==224||p.length>=8&&p[4]=='f'&&p[5]=='t'&&p[6]=='y'&&p[7]=='p'||p.length>=4&&(p[0]&255)==0x1a&&(p[1]&255)==0x45&&(p[2]&255)==0xdf&&(p[3]&255)==0xa3;}
 private static String manifestProof(Path path)throws IOException,ParseException{try(var in=Files.newInputStream(path)){byte[] bytes=in.readNBytes(262145);if(bytes.length>262144)throw new ParseException(ENGINE_UNAVAILABLE);return hex(bytes);}}
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String hex(byte[] bytes){return HexFormat.of().formatHex(digest().digest(bytes));}
 private UUID stable(UUID file,String hash,long ordinal){var digest=digest();for(String field:List.of(profileVersion,file.toString(),hash,Long.toString(ordinal))){byte[] bytes=field.getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}byte[] bytes=digest.digest();bytes[6]=(byte)((bytes[6]&15)|0x80);bytes[8]=(byte)((bytes[8]&63)|0x80);var b=ByteBuffer.wrap(bytes);return new UUID(b.getLong(),b.getLong());}
 private static void check(BooleanSupplier cancelled)throws ParseException{if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new ParseException(CANCELLED);}
 private static void stop(Process child){if(child.isAlive()){child.descendants().forEach(p->{if(p.isAlive())p.destroyForcibly();});child.destroyForcibly();}}
 private static void cleanup(Path directory){try(var paths=Files.walk(directory)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}catch(IOException ignored){/* Source files remain private; no unrelated paths are followed or removed. */}}
}
