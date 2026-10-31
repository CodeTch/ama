package ama.model;

import ama.contract.SpeechContracts;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.http.apache5.ProxyConfiguration;

/** Explicit speech endpoints, complete WAV payloads, bounded I/O and source sample mapping. */
public final class HttpSpeechModelPort implements SpeechModelPort,AutoCloseable {
    public enum Stage { VAD,DIARIZATION,ASR,PUNCTUATION,EMBEDDING }
    public record Endpoint(URI uri,String host,String authorization) {
        public Endpoint {
            if(uri==null||!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
                    ||host==null||!host.matches("[A-Za-z0-9.:-]{1,255}")||authorization!=null&&(authorization.isEmpty()||authorization.length()>4096||authorization.chars().anyMatch(c->c<32||c>126)))throw new IllegalArgumentException("Explicit speech endpoint required");
        }
        @Override public String toString(){return "Endpoint[uri="+uri+", host="+host+", authorization=REDACTED]";}
    }
    public record Config(Map<Stage,Endpoint> endpoints,int maxWavBytes,int maxResponseBytes,int concurrency,Duration timeout,String embeddingModelVersion,int embeddingDimension) {
        public Config {
            endpoints=Map.copyOf(endpoints);
            if(!endpoints.keySet().equals(Set.of(Stage.values()))||maxWavBytes<46||maxWavBytes>4*1024*1024||maxResponseBytes<1||maxResponseBytes>1048576||concurrency<1||concurrency>100||timeout==null||timeout.toMillis()<1||timeout.toMillis()>30000||embeddingModelVersion==null||embeddingModelVersion.isBlank()||embeddingDimension<1||embeddingDimension>16384)throw new IllegalArgumentException("Bounded speech configuration required");
        }
        public static Config at(URI origin,String host,String authorization,String model,int dimension,Duration timeout) {
            if(origin==null||!Set.of("","/").contains(origin.getPath()))throw new IllegalArgumentException("Speech origin required");
            var paths=Map.of(Stage.VAD,"/vad",Stage.DIARIZATION,"/diarize",Stage.ASR,"/inference/asr-nano/modelserving/asr",Stage.PUNCTUATION,"/inference/asr-punc/modelserving/punc",Stage.EMBEDDING,"/embed");
            var endpoints=new EnumMap<Stage,Endpoint>(Stage.class);paths.forEach((stage,path)->endpoints.put(stage,new Endpoint(origin.resolve(path),host,authorization)));
            return new Config(endpoints,4*1024*1024,1048576,2,timeout,model,dimension);
        }
    }
    @FunctionalInterface public interface WavSource { InputStream open(SpeechContracts.WavClip clip) throws IOException; }
    public static final class InvalidResponse extends RuntimeException { public InvalidResponse(){super("Invalid speech provider response");} }
    public static final class Unavailable extends RuntimeException { public Unavailable(){super("Speech provider unavailable");} }
    private final Config config;private final WavSource source;private final SdkHttpClient http;private final ObjectMapper json;
    private final Semaphore capacity;private final Set<ExecutableHttpRequest> active=ConcurrentHashMap.newKeySet();private volatile boolean closed;
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"speech-http-deadline");t.setDaemon(true);return t;});
    public HttpSpeechModelPort(Config config,WavSource source) {
        this.config=Objects.requireNonNull(config);this.source=Objects.requireNonNull(source);capacity=new Semaphore(config.concurrency());
        http=Apache5HttpClient.builder().connectionTimeout(config.timeout()).socketTimeout(config.timeout()).connectionAcquisitionTimeout(config.timeout()).maxConnections(config.concurrency())
                .proxyConfiguration(ProxyConfiguration.builder().useSystemPropertyValues(false).useEnvironmentVariableValues(false).build()).build();
        json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(config.maxResponseBytes()).maxNumberLength(20).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @Override public List<SpeechSpan> vad(SpeechContracts.WavClip clip) {
        var response=exchange(Stage.VAD,()->payload(Map.of("audio_b64",Base64.getEncoder().encodeToString(wav(clip)))),"application/json");
        var output=new ArrayList<SpeechSpan>();for(var item:array(response,"segments")) {range(item,2,clip);output.add(new SpeechSpan(sample(clip,item.get(0)),sample(clip,item.get(1))));}return List.copyOf(output);
    }
    @Override public List<SpeechContracts.SpeakerSpan> diarize(SpeechContracts.WavClip clip,List<SpeechSpan> speech) {
        var ranges=new ArrayList<List<Long>>();
        for(var span:List.copyOf(speech)) {
            if(span.startSample()<clip.startSample()||span.endSample()>clip.endSampleExclusive()||(span.startSample()-clip.startSample())%16!=0||(span.endSample()-clip.startSample())%16!=0)throw new IllegalArgumentException("Speech range outside exact clip milliseconds");
            ranges.add(List.of((span.startSample()-clip.startSample())/16,(span.endSample()-clip.startSample())/16));
        }
        var response=exchange(Stage.DIARIZATION,()->payload(Map.of("audio_b64",Base64.getEncoder().encodeToString(wav(clip)),"vad_segments",ranges)),"application/json");
        var output=new ArrayList<SpeechContracts.SpeakerSpan>();for(var item:array(response,"timeline")) {
            range(item,3,clip);var speaker=item.get(2);if(!(speaker.isTextual()&&!speaker.textValue().isBlank()||speaker.isIntegralNumber()&&speaker.canConvertToLong()&&speaker.longValue()>=0))throw new InvalidResponse();
            output.add(new SpeechContracts.SpeakerSpan(sample(clip,item.get(0)),sample(clip,item.get(1)),speaker.asText()));
        }return List.copyOf(output);
    }
    @Override public SpeechContracts.RecognitionResult recognize(SpeechContracts.WavClip clip) {
        var response=exchange(Stage.ASR,()->wav(clip),"application/octet-stream");String text=text(response);
        List<SpeechContracts.Token> tokens=null;
        if(response.hasNonNull("tokens")) {
            tokens=new ArrayList<>();for(var item:array(response,"tokens")) {
                if(!item.isArray()||item.size()!=3||!item.get(0).isTextual()||item.get(0).textValue().isBlank())throw new InvalidResponse();
                rangeValues(item.get(1),item.get(2),clip);
                tokens.add(new SpeechContracts.Token(item.get(0).textValue(),sample(clip,item.get(1)),sample(clip,item.get(2)),null));
            }
        }
        return new SpeechContracts.RecognitionResult(text,tokens);
    }
    @Override public String punctuate(String text) {
        if(text==null||text.isBlank()||text.length()>65536)throw new IllegalArgumentException("Bounded text required");
        return text(exchange(Stage.PUNCTUATION,()->payload(Map.of("text",text)),"application/json"));
    }
    @Override public SpeechContracts.VoiceEmbedding embed(SpeechContracts.WavClip clip) {
        var values=array(exchange(Stage.EMBEDDING,()->wav(clip),"application/octet-stream"),"embedding");
        if(values.size()!=config.embeddingDimension())throw new InvalidResponse();var vector=new ArrayList<Double>();
        for(var v:values){if(!v.isNumber()||!Double.isFinite(v.doubleValue()))throw new InvalidResponse();vector.add(v.doubleValue());}
        return new SpeechContracts.VoiceEmbedding(config.embeddingModelVersion(),config.embeddingDimension(),vector);
    }
    private byte[] wav(SpeechContracts.WavClip clip) {
        Objects.requireNonNull(clip);byte[] bytes;
        try(var in=source.open(clip)){bytes=in.readNBytes(config.maxWavBytes()+1);}catch(IOException failure){throw new Unavailable();}
        if(bytes.length>config.maxWavBytes()||bytes.length<46)throw new IllegalArgumentException("Bounded complete WAV required");
        var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(!ascii(bytes,0,"RIFF")||!ascii(bytes,8,"WAVEfmt ")||!ascii(bytes,36,"data")||b.getInt(4)!=bytes.length-8||b.getInt(16)!=16||b.getShort(20)!=1||b.getShort(22)!=1||b.getInt(24)!=16000||b.getInt(28)!=32000||b.getShort(32)!=2||b.getShort(34)!=16||b.getInt(40)!=bytes.length-44||clip.sampleCount()!=(bytes.length-44)/2L||(bytes.length-44)%2!=0)throw new IllegalArgumentException("16kHz mono PCM16 WAV matching clip required");
        return bytes;
    }
    private static boolean ascii(byte[] bytes,int offset,String text){return new String(bytes,offset,text.length(),StandardCharsets.US_ASCII).equals(text);}
    private byte[] payload(Object value){try{return json.writeValueAsBytes(value);}catch(IOException failure){throw new IllegalArgumentException("Invalid speech request");}}
    private JsonNode exchange(Stage stage,java.util.function.Supplier<byte[]> prepare,String type) {
        if(closed||!capacity.tryAcquire())throw new Unavailable();ExecutableHttpRequest request=null;Future<?> timer=null;var expired=new AtomicBoolean();
        try {
            byte[] body=prepare.get();
            var endpoint=config.endpoints().get(stage);
            var builder=SdkHttpFullRequest.builder().uri(endpoint.uri()).method(SdkHttpMethod.POST).putHeader("Host",endpoint.host()).putHeader("Content-Type",type).putHeader("Accept","application/json").putHeader("Content-Length",Integer.toString(body.length));
            if(endpoint.authorization()!=null)builder.putHeader("Authorization",endpoint.authorization());
            request=http.prepareRequest(HttpExecuteRequest.builder().request(builder.build()).contentStreamProvider(ContentStreamProvider.fromByteArrayUnsafe(body)).build());
            active.add(request);var operation=request;if(closed)throw new Unavailable();
            timer=deadlines.schedule(()->{expired.set(true);operation.abort();},config.timeout().toMillis(),TimeUnit.MILLISECONDS);
            var response=request.call();
            try(var input=response.responseBody().orElseThrow(Unavailable::new)) {
                int status=response.httpResponse().statusCode();if(status==429)throw new CompanySpeechAdapter.RateLimited();if(status!=200)throw new Unavailable();
                var contentType=response.httpResponse().firstMatchingHeader("Content-Type").orElse("");if(!contentType.toLowerCase(Locale.ROOT).matches("application/json(?:;.*)?"))throw new InvalidResponse();
                var length=response.httpResponse().firstMatchingHeader("Content-Length");if(length.isPresent()&&(!length.get().matches("[0-9]{1,9}")||Long.parseLong(length.get())>config.maxResponseBytes()))throw new InvalidResponse();
                byte[] raw=input.readNBytes(config.maxResponseBytes()+1);if(raw.length>config.maxResponseBytes())throw new InvalidResponse();
                var value=json.readTree(raw);if(value==null||!value.isObject())throw new InvalidResponse();return value;
            }
        }catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw new InvalidResponse();}
        catch(IOException failure){if(expired.get())throw new CompanySpeechAdapter.TimedOut();throw new Unavailable();}
        finally{if(timer!=null)timer.cancel(false);if(request!=null){active.remove(request);request.abort();}capacity.release();}
    }
    private static JsonNode array(JsonNode value,String key){var result=value.get(key);if(result==null||!result.isArray())throw new InvalidResponse();return result;}
    private static String text(JsonNode value){var text=value.get("text");if(text==null||!text.isTextual()||text.textValue().isBlank())throw new InvalidResponse();return text.textValue();}
    private static void range(JsonNode value,int size,SpeechContracts.WavClip clip){if(!value.isArray()||value.size()!=size)throw new InvalidResponse();rangeValues(value.get(0),value.get(1),clip);}
    private static void rangeValues(JsonNode start,JsonNode end,SpeechContracts.WavClip clip) {
        if(start==null||end==null||!start.isIntegralNumber()||!end.isIntegralNumber()||!start.canConvertToLong()||!end.canConvertToLong()||start.longValue()<0||end.longValue()<=start.longValue()||end.longValue()>clip.sampleCount()/16)throw new InvalidResponse();
    }
    private static long sample(SpeechContracts.WavClip clip,JsonNode millis){return Math.addExact(clip.startSample(),Math.multiplyExact(millis.longValue(),16));}
    @Override public void close(){closed=true;for(var request:active)request.abort();deadlines.shutdownNow();http.close();}
}
