package ama.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

/** Bounded, input-bound proposal adapter. A result is never an information write or a grant. */
public final class HttpMeetingCategorySuggestion implements AutoCloseable {
 public static final String PROFILE="ama-meeting-category.v1";
 private static final int REQUEST_LIMIT=1048576,RESPONSE_LIMIT=65536;
 private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 private final URI origin;
 private final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final java.util.concurrent.ScheduledExecutorService deadline=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"category-suggestion-deadline");t.setDaemon(true);return t;});
 public HttpMeetingCategorySuggestion(URI origin){if(!"http://127.0.0.1:8792".equals(origin.toString()))throw new IllegalArgumentException("Category integration must use the fixed sibling harness origin");this.origin=origin;}
 public String execute(byte[] input)throws IOException {
  byte[] request=input.clone();if(request.length>REQUEST_LIMIT)throw new IllegalArgumentException("Category input budget exceeded");
  try {
   var response=client.send(HttpRequest.newBuilder(origin.resolve("/v1/meeting-information/category-suggestions")).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(request)).build(),HttpResponse.BodyHandlers.ofInputStream());
   try(var body=response.body()){
    var expiry=deadline.schedule(()->{try{body.close();}catch(IOException ignored){}},8,java.util.concurrent.TimeUnit.SECONDS);
    try{if(response.statusCode()!=200)throw new IOException("Category suggestion unavailable");var bytes=body.readNBytes(RESPONSE_LIMIT+1);return verify(request,bytes);}finally{expiry.cancel(false);}
   }
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Category suggestion interrupted",interrupted);}
 }
 public static String verify(byte[] request,byte[] response){
  require(request.length<=REQUEST_LIMIT&&response.length<=RESPONSE_LIMIT);
  try {
   var source=JSON.readTree(request);var result=JSON.readTree(response);
   exact(source,Set.of("profile","candidateId","inputFingerprint","name","summary","dictionaryVersion","categories"));
   exact(result,Set.of("profile","mode","inputSha256","categoryId"));
   require(PROFILE.equals(source.path("profile").asText())&&PROFILE.equals(result.path("profile").asText())&&"LOCAL_SIMULATION".equals(result.path("mode").asText())&&sha(request).equals(result.path("inputSha256").asText()));
   var categories=source.path("categories");require(categories.isArray()&&categories.size()<=256);var allowed=new HashSet<String>();
   for(var category:categories){exact(category,Set.of("id","label"));require(category.path("id").isTextual()&&category.path("label").isTextual()&&allowed.add(category.path("id").asText()));}
   if(result.get("categoryId").isNull())return null;
   require(result.get("categoryId").isTextual()&&allowed.contains(result.get("categoryId").textValue()));return result.get("categoryId").textValue();
  }catch(IOException malformed){throw new IllegalArgumentException("Invalid category suggestion response",malformed);}
 }
 private static void exact(JsonNode node,Set<String> keys){require(node!=null&&node.isObject());var found=new HashSet<String>();node.fieldNames().forEachRemaining(found::add);require(found.equals(keys));}
 private static void require(boolean condition){if(!condition)throw new IllegalArgumentException("Category result does not bind the original dictionary and input");}
 public static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 @Override public void close(){client.close();deadline.shutdownNow();}
}
