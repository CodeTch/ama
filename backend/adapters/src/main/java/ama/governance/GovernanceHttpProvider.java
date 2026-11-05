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
 private HttpSpeechModelPort speech(byte[] wav,String version){if(!GovernanceCatalog.MODELS.contains(version))throw new IllegalArgumentException("Model version is not installed");GovernanceAudio.require(wav,16000,1920000);return new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(origin,origin.getRawAuthority(),null,version,dimension,Duration.ofSeconds(10)),clip->new ByteArrayInputStream(wav));}
 @Override public VoiceEmbedding embed(byte[] wav,String version){GovernanceAudio.require(wav,48000,800000);try(var port=speech(wav,version)){return port.embed(new WavClip("ephemeral-governance",0,(wav.length-44)/2));}}
 public VoiceEmbedding embedMeeting(byte[] wav){GovernanceAudio.require(wav,48000,800000);try(var port=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(origin,origin.getRawAuthority(),null,model,dimension,Duration.ofSeconds(10)),clip->new ByteArrayInputStream(wav))){return port.embed(new WavClip("meeting-voice-application",0,(wav.length-44)/2));}}
 public JsonNode consent(String subject,String meeting){
  var value=exchange("GET","/v1/feedback/consent?subjectId="+encode(subject)+"&meetingId="+encode(meeting)+"&purpose=SPEAKER_CANDIDATE",null);
  if(!subject.equals(value.path("subjectId").asText())||!meeting.equals(value.path("meetingId").asText())||!"SPEAKER_CANDIDATE".equals(value.path("purpose").asText())||!"ama-feedback-harness.v1".equals(value.path("profile").asText())||!"LOCAL_SIMULATION".equals(value.path("mode").asText()))throw new IllegalStateException("Subject consent unavailable");
  return value;
 }
 public JsonNode applyCorpus(Map<String,Object> body){var value=exchange("POST","/v1/feedback/corpus",body);if(!body.get("id").equals(value.path("id").asText())||((Number)body.get("revision")).longValue()!=value.path("revision").asLong(-1)||!"ama-feedback-harness.v1".equals(value.path("profile").asText())||!"LOCAL_SIMULATION".equals(value.path("mode").asText())||!value.path("version").asText().matches("harness-corpus-[0-9a-f]{20}")||!(Boolean.TRUE.equals(body.get("active"))?"ADOPTED":"REVOKED").equals(value.path("state").asText()))throw new IllegalStateException("Corpus adoption receipt unavailable");return value;}
 @Override public SampleResult evaluate(Sample sample,ModelConfig config){
  GovernanceCatalog.require(config);long start=System.nanoTime();String actual;Map<String,Double> measures;double score;boolean human=false;
  if(Set.of(Kind.MINUTES,Kind.ENTITY,Kind.EVIDENCE).contains(sample.kind())){
   String instruction=switch(sample.kind()){case ENTITY->"从原文抽取实体，仅返回 JSON 字符串数组，每项格式 TYPE:名称，TYPE 为 PERSON、DEPARTMENT、PRODUCT、FINANCIAL_TERM。";case EVIDENCE->"根据输入中的结论与编号原文，返回 JSON 对象，键为结论编号，值为支持该结论的原文编号数组；没有支持证据时返回空数组。";default->GovernanceCatalog.PROMPTS.get(config.promptVersion());};
   var value=exchange("POST","/v1/chat/completions",Map.of("model","deepseek-v4-flash","temperature",0,"messages",List.of(Map.of("role","system","content",instruction),Map.of("role","user","content",sample.input()))));actual=value.path("choices").path(0).path("message").path("content").asText();if(actual.isBlank())throw new IllegalArgumentException("Model response unavailable");
   if(sample.kind()==Kind.ENTITY){measures=QualityMetrics.entities(strings(parse(sample.expected())),strings(parse(actual)));score=measures.get("f1");}
   else if(sample.kind()==Kind.EVIDENCE){var reference=parse(sample.expected());var output=parse(actual);if(!reference.isObject()||reference.isEmpty()||!output.isObject())throw new IllegalArgumentException("Annotated evidence links required");int covered=0,links=0,correct=0;var fields=reference.fieldNames();while(fields.hasNext()){String id=fields.next();var expected=strings(reference.get(id));var predicted=output.has(id)?strings(output.get(id)):Set.<String>of();if(predicted.stream().anyMatch(expected::contains))covered++;correct+=predicted.stream().filter(expected::contains).count();}var outputs=output.elements();while(outputs.hasNext())links+=strings(outputs.next()).size();double coverage=(double)covered/reference.size(),precision=links==0?0:(double)correct/links;measures=Map.of("coverage",coverage,"correctness",precision,"correctLinks",(double)correct,"predictedLinks",(double)links);score=Math.min(coverage,precision);}
   else{score=EvaluationService.textScore(sample.expected(),actual);measures=Map.of("textSimilarity",score);human=true;}
  }else if(sample.kind()==Kind.VOICE_SAME||sample.kind()==Kind.VOICE_DIFFERENT){
   if(config.voiceThreshold()==null)throw new IllegalArgumentException("Candidate threshold required");double similarity=VoiceprintService.similarity(embed(decode(sample.input()),config.modelVersion()),embed(decode(sample.input2()),config.modelVersion()));boolean accepted=similarity>=config.voiceThreshold();actual=Boolean.toString(accepted);score=accepted==Boolean.parseBoolean(sample.expected())?1:0;measures=Map.of("similarity",similarity,"threshold",config.voiceThreshold(),"accepted",accepted?1d:0d);
  }else{
   byte[] wav=decodeEvaluation(sample.input());try(var port=speech(wav,config.modelVersion())){var clip=new WavClip("ephemeral-evaluation",0,(wav.length-44)/2);if(sample.kind()==Kind.ASR){actual=port.recognize(clip).text();measures=QualityMetrics.characters(sample.expected(),actual);score=Math.max(0,1-measures.get("cer"));}else{var turns=port.diarize(clip,port.vad(clip)).stream().map(t->new QualityMetrics.Turn(t.startSample(),t.endSample(),t.speakerId())).toList();actual=serialize(turns);var ref=parse(sample.expected());if(!ref.isArray())throw new IllegalArgumentException("Diarization reference timeline required");var reference=new ArrayList<QualityMetrics.Turn>();for(var t:ref){if(t.size()!=3||!t.path("start").canConvertToLong()||!t.path("end").canConvertToLong()||!t.path("speaker").isTextual())throw new IllegalArgumentException("Exact timeline fields required");reference.add(new QualityMetrics.Turn(t.get("start").longValue(),t.get("end").longValue(),t.get("speaker").textValue()));}measures=QualityMetrics.diarization(reference,turns);score=Math.max(0,1-measures.get("der"));}}
  }
  String actualModel=switch(sample.kind()){case MINUTES,ENTITY,EVIDENCE->"deepseek-v4-flash";case ASR->"asr-nano/provider-version-unspecified";case DIARIZATION->"diarize/provider-version-unspecified";default->config.modelVersion();};
  String actualPrompt=switch(sample.kind()){case MINUTES->config.promptVersion();case ENTITY->"entity-extraction-v1";case EVIDENCE->"evidence-links-v1";default->null;};
  return new SampleResult(sample.id(),true,score,null,actual,measures,(System.nanoTime()-start)/1_000_000,actualModel,actualPrompt,human);
 }
 private JsonNode parse(String value){try{return json.readTree(value);}catch(IOException bad){throw new IllegalArgumentException("Valid annotated JSON required");}}
 private String serialize(Object value){try{return json.writeValueAsString(value);}catch(IOException bad){throw new IllegalArgumentException("Bounded result required");}}
 private static Set<String> strings(JsonNode value){if(value==null||!value.isArray()||value.size()>512)throw new IllegalArgumentException("Bounded string array required");var result=new LinkedHashSet<String>();for(var item:value){if(!item.isTextual()||item.textValue().isBlank()||item.textValue().length()>1024)throw new IllegalArgumentException("Bounded annotation required");result.add(item.textValue());}return result;}
 private static byte[] decodeEvaluation(String value){try{byte[] wav=Base64.getDecoder().decode(value);GovernanceAudio.require(wav,16000,1920000);return wav;}catch(RuntimeException bad){throw new IllegalArgumentException("Valid evaluation PCM sample required");}}
 public static byte[] decode(String value){try{byte[] wav=Base64.getDecoder().decode(value);GovernanceAudio.require(wav);return wav;}catch(RuntimeException bad){throw new IllegalArgumentException("Valid bounded PCM sample required");}}
 @Override public Outcome send(Request request){var body=Map.of("businessKey",request.businessKey(),"sender","noreply@example.invalid","recipients",request.recipients(),"subject",request.subject(),"body",request.body(),"attachments",List.of());return outcome(exchange("POST","/v1/mail/send",body));}
 @Override public Outcome findByBusinessKey(String key){return outcome(exchange("GET","/v1/mail/by-business-key/"+encode(key),null));}
 public String email(String enterpriseId){var person=exchange("GET","/v1/directory/people/"+encode(enterpriseId),null);if(!person.path("active").asBoolean(false)||!person.path("stableId").asText().equals(enterpriseId))throw new IllegalArgumentException("Current active recipient required");String email=person.path("email").asText();if(!email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,128}@[A-Za-z0-9.-]{1,128}"))throw new IllegalArgumentException("Enterprise mailbox unavailable");return email;}
 private Outcome outcome(JsonNode value){String state=value.path("outcome").asText(),id=value.path("messageId").asText();return Set.of("SENT","FAILED").contains(state)&&id.matches("[A-Za-z0-9_.:-]{1,128}")?new Outcome(State.valueOf(state),id):new Outcome(State.UNKNOWN,null);}
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
 private JsonNode exchange(String method,String path,Object body){
  try{var builder=HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(12)).header("Accept","application/json");if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());try(var stream=response.body()){byte[] bytes=stream.readNBytes(131073);if(bytes.length>131072)throw new IOException("Bounded response required");if(response.statusCode()==403&&path.startsWith("/v1/feedback/consent"))throw new IllegalArgumentException("对应人员未提供有效声纹授权，未提交审核。");if(response.statusCode()==404)return json.createObjectNode();if(response.statusCode()!=200)throw new IOException("Provider unavailable");return json.readTree(bytes);}}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Provider unavailable");}catch(IOException e){throw new IllegalStateException("Provider unavailable");}
 }
}
