package ama.governance;
import ama.contract.GovernanceContracts.*;
import ama.contract.SpeechContracts.*;
import ama.model.HttpSpeechModelPort;
import ama.mail.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
/** Explicit operator origin only. References and quality answers never go to model requests. */
public final class GovernanceHttpProvider implements GovernanceModelPort,MailPort {
 private final URI origin;private final String model;private final int dimension;private final HttpClient http;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 public GovernanceHttpProvider(URI origin,String model,int dimension){
  if(origin==null||!Set.of("http","https").contains(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!Set.of("","/").contains(origin.getPath()))throw new IllegalArgumentException("Operator governance origin required");
  this.origin=origin;this.model=model;this.dimension=dimension;http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,IOException e){}}).build();
 }
 private HttpSpeechModelPort speech(byte[] wav,String version){if(!model.equals(version))throw new IllegalArgumentException("Model version is not installed");GovernanceAudio.require(wav);return new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(origin,origin.getRawAuthority(),null,model,dimension,Duration.ofSeconds(10)),clip->new ByteArrayInputStream(wav));}
 @Override public VoiceEmbedding embed(byte[] wav,String version){try(var port=speech(wav,version)){return port.embed(new WavClip("ephemeral-governance",0,(wav.length-44)/2));}}
 @Override public SampleResult evaluate(Sample sample,ModelConfig config){
  if(!model.equals(config.modelVersion())||!"summary-v1".equals(config.promptVersion()))throw new IllegalArgumentException("Installed model/prompt required");
  String actual;
  if(sample.kind()==Kind.MINUTES){var body=Map.of("model","deepseek-v4-flash","temperature",0,"messages",List.of(Map.of("role","system","content","总结会议内容，保留结论与行动项，直接返回纪要正文。"),Map.of("role","user","content",sample.input())));var value=exchange("POST","/v1/chat/completions",body);actual=value.path("choices").path(0).path("message").path("content").asText();if(actual.isBlank())throw new IllegalArgumentException("Model response unavailable");}
  else if(sample.kind()==Kind.VOICE_SAME||sample.kind()==Kind.VOICE_DIFFERENT){if(config.voiceThreshold()==null)throw new IllegalArgumentException("Candidate threshold required");double score=VoiceprintService.similarity(embed(decode(sample.input()),config.modelVersion()),embed(decode(sample.input2()),config.modelVersion()));return new SampleResult(sample.id(),true,(score>=config.voiceThreshold())==Boolean.parseBoolean(sample.expected())?1:0,null);}
  else{byte[] wav=decode(sample.input());try(var port=speech(wav,config.modelVersion())){var clip=new WavClip("ephemeral-evaluation",0,(wav.length-44)/2);actual=sample.kind()==Kind.ASR?port.recognize(clip).text():Long.toString(port.diarize(clip,port.vad(clip)).stream().map(SpeakerSpan::speakerId).distinct().count());}}
  return new SampleResult(sample.id(),true,EvaluationService.textScore(sample.expected(),actual),null);
 }
 public static byte[] decode(String value){try{byte[] wav=Base64.getDecoder().decode(value);GovernanceAudio.require(wav);return wav;}catch(RuntimeException bad){throw new IllegalArgumentException("Valid bounded PCM sample required");}}
 @Override public Outcome send(Request request){var body=Map.of("businessKey",request.businessKey(),"sender","noreply@example.invalid","recipients",request.recipients(),"subject",request.subject(),"body",request.body(),"attachments",List.of());return outcome(exchange("POST","/v1/mail/send",body));}
 @Override public Outcome findByBusinessKey(String key){return outcome(exchange("GET","/v1/mail/by-business-key/"+encode(key),null));}
 public String email(String enterpriseId){var person=exchange("GET","/v1/directory/people/"+encode(enterpriseId),null);if(!person.path("active").asBoolean(false)||!person.path("stableId").asText().equals(enterpriseId))throw new IllegalArgumentException("Current active recipient required");String email=person.path("email").asText();if(!email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,128}@[A-Za-z0-9.-]{1,128}"))throw new IllegalArgumentException("Enterprise mailbox unavailable");return email;}
 private Outcome outcome(JsonNode value){String state=value.path("outcome").asText(),id=value.path("messageId").asText();return Set.of("SENT","FAILED").contains(state)&&id.matches("[A-Za-z0-9_.:-]{1,128}")?new Outcome(State.valueOf(state),id):new Outcome(State.UNKNOWN,null);}
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
 private JsonNode exchange(String method,String path,Object body){
  try{var builder=HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(12)).header("Accept","application/json");if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());try(var stream=response.body()){byte[] bytes=stream.readNBytes(131073);if(bytes.length>131072)throw new IOException("Bounded response required");if(response.statusCode()==404)return json.createObjectNode();if(response.statusCode()!=200)throw new IOException("Provider unavailable");return json.readTree(bytes);}}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Provider unavailable");}catch(IOException e){throw new IllegalStateException("Provider unavailable");}
 }
}
