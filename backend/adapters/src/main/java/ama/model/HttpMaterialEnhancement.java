package ama.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

/** Independently versioned controlled adapter. It never changes the frozen speech profile. */
public final class HttpMaterialEnhancement implements AutoCloseable {
 public static final String PROFILE="ama-material-enhancement.v1";
 public static final int MAX_BYTES=4194304;
 public static final String PROFILE_SHA256=sha((PROFILE+";request=exact-bytes;source=immutable-segments;materials=authorized-pinned-text;result=review-only;maxBytes=4194304").getBytes(java.nio.charset.StandardCharsets.UTF_8));
 private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
 private final java.util.concurrent.ScheduledExecutorService deadline=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"material-enhancement-deadline");t.setDaemon(true);return t;});
 private final URI origin;
 public static final class RejectedInput extends IOException {public RejectedInput(){super("Controlled enhancement rejected this input before producing a result");}}
 private final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 public HttpMaterialEnhancement(URI origin){
  if(!"http://127.0.0.1:8792".equals(origin.toString()))throw new IllegalArgumentException("Controlled enhancement must use the fixed sibling harness origin");this.origin=origin;
 }
 public JsonNode execute(String requestId,byte[] request)throws IOException{
  ama.contract.MinutesContracts.uuid(requestId);request=request.clone();if(request.length>MAX_BYTES)throw new IllegalArgumentException("Enhancement input budget exceeded");
  try{
   var response=client.send(HttpRequest.newBuilder(origin.resolve("/v1/transcript-enhancements/"+requestId)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(request)).build(),HttpResponse.BodyHandlers.ofInputStream());
   try(var body=response.body()){
    var expiry=deadline.schedule(()->{try{body.close();}catch(IOException ignored){}},20,java.util.concurrent.TimeUnit.SECONDS);
    try{
    if(response.statusCode()==422||response.statusCode()==413)throw new RejectedInput();
    if(response.statusCode()!=200)throw new IOException("Controlled enhancement returned HTTP "+response.statusCode());
    byte[] bytes=body.readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new IOException("Enhancement response budget exceeded");
    return verify(requestId,request,bytes);
    }finally{expiry.cancel(false);}
   }
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Enhancement request interrupted",interrupted);}
 }
 public static JsonNode verify(String id,byte[] request,byte[] response){
  if(request.length>MAX_BYTES||response.length>MAX_BYTES)throw new IllegalArgumentException("Enhancement budget exceeded");
  try{
   var source=JSON.readTree(request);var result=JSON.readTree(response);
   exact(result,Set.of("requestId","profile","inputSha256","state","segments","materialRevisionIds"));
   require(id.equals(result.path("requestId").asText())&&PROFILE.equals(result.path("profile").asText())&&PROFILE.equals(source.path("profile").asText())&&sha(request).equals(result.path("inputSha256").asText())&&"SUCCEEDED".equals(result.path("state").asText()));
   JsonNode originals=source.path("segments"),segments=result.path("segments"),materials=source.path("materials"),ids=result.path("materialRevisionIds");
   require(originals.isArray()&&originals.size()>0&&originals.size()<=4096&&segments.isArray()&&segments.size()==originals.size()&&materials.isArray()&&materials.size()<=16&&ids.isArray()&&ids.size()==materials.size());
   for(int n=0;n<ids.size();n++)require(ids.get(n).isTextual()&&ids.get(n).asText().equals(materials.get(n).path("revisionId").asText()));
   var unique=new HashSet<String>();long characters=0;
   for(int n=0;n<segments.size();n++){
    var item=segments.get(n);exact(item,Set.of("id","text"));String segment=item.path("id").asText();ama.contract.MinutesContracts.uuid(segment);
    require(unique.add(segment)&&segment.equals(originals.get(n).path("id").asText())&&item.path("text").isTextual());String text=item.path("text").asText(),before=originals.get(n).path("text").asText();ama.contract.MinutesContracts.scalars(text);
    require(text.length()<=65536&&before.isBlank()==text.isBlank());characters+=text.length();require(characters<=1048576);
   }
   return result.deepCopy();
  }catch(IOException malformed){throw new IllegalArgumentException("Invalid enhancement response",malformed);}
 }
 private static void exact(JsonNode node,Set<String> keys){require(node.isObject());var found=new HashSet<String>();node.fieldNames().forEachRemaining(found::add);require(found.equals(keys));}
 private static void require(boolean condition){if(!condition)throw new IllegalArgumentException("Enhancement result does not bind the original input");}
 public static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 @Override public void close(){client.close();deadline.shutdownNow();}
}
