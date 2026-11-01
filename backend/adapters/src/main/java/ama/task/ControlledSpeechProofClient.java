package ama.task;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.http.apache5.ProxyConfiguration;

/** Local harness evidence protocol only. A 404, timeout or client abort is never settlement. */
final class ControlledSpeechProofClient implements AutoCloseable {
 record Proof(String id,String path,String state,String requestSha,String responseSha,Integer status) {
  boolean terminal(){return Set.of("COMPLETED","FENCED","TERMINATED").contains(state);}
 }
 private final URI origin;private final SdkHttpClient http;
 private final Set<ExecutableHttpRequest> active=ConcurrentHashMap.newKeySet();private volatile boolean closed;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"speech-proof-deadline");t.setDaemon(true);return t;});
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
  .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(4096).maxNumberLength(19).maxNameLength(64).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 ControlledSpeechProofClient(ControlledSpeechProfile profile){origin=profile.origin();http=Apache5HttpClient.builder().maxConnections(2).connectionTimeout(Duration.ofSeconds(1)).connectionAcquisitionTimeout(Duration.ofSeconds(1)).socketTimeout(Duration.ofSeconds(1))
  .proxyConfiguration(ProxyConfiguration.builder().useSystemPropertyValues(false).useEnvironmentVariableValues(false).build()).build();}
 Optional<Proof> lookup(String id){return exchange(id,null,null);}
 Proof fence(String id,String path,String requestSha){return exchange(id,path,requestSha).orElseThrow(ControlledSpeechProofClient::unavailable);}
 private Optional<Proof> exchange(String id,String path,String requestSha){
  if(closed||id==null||!UUID.fromString(id).toString().equals(id))throw unavailable();ExecutableHttpRequest call=null;Future<?> deadline=null;
  try{
   byte[] body=path==null?new byte[0]:JSON.writeValueAsBytes(Map.of("path",path,"requestSha256",requestSha));
   var request=SdkHttpFullRequest.builder().uri(origin.resolve("/v1/harness/speech-executions/"+id+(path==null?"":"/fence")))
    .method(path==null?SdkHttpMethod.GET:SdkHttpMethod.POST).putHeader("Accept","application/json");
   if(path!=null)request.putHeader("Content-Type","application/json").putHeader("Content-Length",Integer.toString(body.length));
   var execution=HttpExecuteRequest.builder().request(request.build());if(path!=null)execution.contentStreamProvider(ContentStreamProvider.fromByteArrayUnsafe(body));
   call=http.prepareRequest(execution.build());active.add(call);if(closed)throw unavailable();var actual=call;deadline=timer.schedule(actual::abort,1000,TimeUnit.MILLISECONDS);
   var response=call.call();try(var input=response.responseBody().orElseThrow(ControlledSpeechProofClient::unavailable)){
    if(response.httpResponse().statusCode()==404&&path==null)return Optional.empty();
    if(response.httpResponse().statusCode()!=200||!response.httpResponse().firstMatchingHeader("Content-Type").orElse("").toLowerCase(Locale.ROOT).matches("application/json(?:;.*)?"))throw unavailable();
    byte[] bytes=input.readNBytes(8193);if(bytes.length>8192)throw unavailable();var root=JSON.readTree(bytes);var keys=new HashSet<String>();if(root!=null)root.fieldNames().forEachRemaining(keys::add);
    if(root==null||!root.isObject()||!keys.equals(Set.of("schema","executionId","path","state","requestSha256","responseSha256","status","startedAt","completedAt"))||!root.path("schema").asText().equals("ama.harness-speech-execution.v1")||!root.path("executionId").asText().equals(id))throw unavailable();
    String recordedPath=string(root,"path"),state=string(root,"state");if(!paths().contains(recordedPath)||!Set.of("STARTED","COMPLETED","FENCED","TERMINATED").contains(state))throw unavailable();
    String source=nullableHash(root,"requestSha256"),result=nullableHash(root,"responseSha256");Integer status=null;var statusNode=root.get("status");if(!statusNode.isNull()){if(!statusNode.isIntegralNumber()||!statusNode.canConvertToInt()||statusNode.intValue()<100||statusNode.intValue()>599)throw unavailable();status=statusNode.intValue();}
    string(root,"startedAt");if(state.equals("STARTED")){if(status!=null||result!=null||!root.get("completedAt").isNull())throw unavailable();}
    else{string(root,"completedAt");if(state.equals("COMPLETED")){if(status==null||source==null||result==null)throw unavailable();}else if(status!=null||result!=null||state.equals("FENCED")&&source==null)throw unavailable();}
    return Optional.of(new Proof(id,recordedPath,state,source,result,status));
   }
  }catch(IOException|RuntimeException failure){throw unavailable();}
  finally{if(deadline!=null)deadline.cancel(false);if(call!=null){active.remove(call);call.abort();}}
 }
 static Set<String> paths(){return Set.of("/vad","/diarize","/embed","/inference/asr-nano/modelserving/asr","/inference/asr-punc/modelserving/punc");}
 private static String string(JsonNode root,String key){var value=root.get(key);if(value==null||!value.isTextual()||value.textValue().isBlank()||value.textValue().length()>256)throw unavailable();return value.textValue();}
 private static String nullableHash(JsonNode root,String key){var value=root.get(key);if(value==null)throw unavailable();if(value.isNull())return null;String hash=string(root,key);if(!hash.matches("[0-9a-f]{64}"))throw unavailable();return hash;}
 private static IllegalStateException unavailable(){return new IllegalStateException("Speech execution evidence unavailable");}
 @Override public void close(){closed=true;for(var request:active)request.abort();timer.shutdownNow();http.close();}
}
