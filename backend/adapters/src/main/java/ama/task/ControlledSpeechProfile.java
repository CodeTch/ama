package ama.task;

import ama.model.HttpSpeechModelPort;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

/** Explicit local harness installation. It declares no company capability or quota. */
public final class ControlledSpeechProfile {
 public static final String PARSER="ama.media-documents.v3",SERVICE="ama.harness-speech.v1";
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
  .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(4096).maxNumberLength(19).maxNameLength(64).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 private final String sha,documentSha;private final Path manifest;
 private ControlledSpeechProfile(Path manifest,String sha,String documentSha){this.manifest=manifest;this.sha=sha;this.documentSha=documentSha;}
 public String sha256(){return sha;}public String documentSha256(){return documentSha;}public Path manifest(){return manifest;}
 public int windowSamples(){return 1920000;}public int offlineSlots(){return 2;}public int realtimeSlots(){return 64;}public int totalSlots(){return 66;}public long quotaRevision(){return 1;}
 public URI origin(){return URI.create("http://127.0.0.1:8792");}
 public HttpSpeechModelPort.Config http(){return HttpSpeechModelPort.Config.at(origin(),"inference.dev.zsfund.com",null,"harness-fixture-embedding-v1",256,Duration.ofMillis(3000));}
 public static ControlledSpeechProfile configured(String expected){String value=System.getenv("AMA_LOCAL_SPEECH_RUNTIME");if(value==null)throw unavailable();var profile=fromRuntime(Path.of(value));if(!profile.sha.equals(expected))throw unavailable();return profile;}
 public static ControlledSpeechProfile fromRuntime(Path runtime){
  try{
   if(runtime==null||Files.isSymbolicLink(runtime))throw unavailable();var settings=JSON.readTree(read(runtime,16384));
   fields(settings,Set.of("schema","manifest","manifestSha256"));literal(settings,"schema","ama.controlled-speech-runtime.v1");
   String expected=text(settings,"manifestSha256");hash(expected);Path manifest=Path.of(text(settings,"manifest")).toAbsolutePath().normalize();if(Files.isSymbolicLink(manifest))throw unavailable();byte[] raw=read(manifest,16384);if(!digest(raw).equals(expected))throw unavailable();
   var value=JSON.readTree(raw);fields(value,Set.of("schema","serviceKey","origin","host","embeddingModel","embeddingDimension","windowSamples","timeoutMillis","offlineSlots","realtimeSlots","totalSlots","quotaRevision","ffmpegSha256","documentEngineManifestSha256"));
   literal(value,"schema","ama.harness-speech-engine.v1");literal(value,"serviceKey",SERVICE);literal(value,"origin","http://127.0.0.1:8792");literal(value,"host","inference.dev.zsfund.com");literal(value,"embeddingModel","harness-fixture-embedding-v1");
   number(value,"embeddingDimension",256);number(value,"windowSamples",1920000);number(value,"timeoutMillis",3000);number(value,"offlineSlots",2);number(value,"realtimeSlots",64);number(value,"totalSlots",66);number(value,"quotaRevision",1);
   String ffmpeg=text(value,"ffmpegSha256"),documents=text(value,"documentEngineManifestSha256");hash(ffmpeg);hash(documents);
   if(!Files.isExecutable(Path.of("/usr/bin/ffmpeg"))||!digest(read(Path.of("/usr/bin/ffmpeg"),16*1024*1024)).equals(ffmpeg))throw unavailable();
   return new ControlledSpeechProfile(manifest.toRealPath(),expected,documents);
  }catch(IOException|RuntimeException failure){throw unavailable();}
 }
 static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 static String key(String... fields){try{var out=new ByteArrayOutputStream();var data=new DataOutputStream(out);for(String field:fields){byte[] bytes=Objects.requireNonNull(field).getBytes(java.nio.charset.StandardCharsets.UTF_8);data.writeInt(bytes.length);data.write(bytes);}return digest(out.toByteArray());}catch(IOException impossible){throw new IllegalStateException(impossible);}}
 static void hash(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw unavailable();}
 private static byte[] read(Path path,int maximum)throws IOException{try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] value=input.readNBytes(maximum+1);if(value.length>maximum)throw unavailable();return value;}}
 private static void fields(JsonNode value,Set<String> expected){if(value==null||!value.isObject())throw unavailable();var names=new HashSet<String>();value.fieldNames().forEachRemaining(names::add);if(!names.equals(expected))throw unavailable();}
 private static String text(JsonNode value,String key){var n=value.get(key);if(n==null||!n.isTextual()||n.textValue().isEmpty()||n.textValue().length()>4096)throw unavailable();return n.textValue();}
 private static void literal(JsonNode value,String key,String expected){if(!text(value,key).equals(expected))throw unavailable();}
 private static void number(JsonNode value,String key,long expected){var n=value.get(key);if(n==null||!n.isIntegralNumber()||!n.canConvertToLong()||n.longValue()!=expected)throw unavailable();}
 private static IllegalStateException unavailable(){return new IllegalStateException("Controlled speech installation unavailable");}
}
