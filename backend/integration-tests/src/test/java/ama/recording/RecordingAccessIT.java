package ama.recording;

import ama.api.ApiApplication;
import ama.db.MysqlDataSources;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/session security/current MySQL authorization; fixtures only create isolated test identities. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordingAccessIT {
    ConfigurableApplicationContext app;JdbcTemplate jdbc;final ObjectMapper json=new ObjectMapper();
    int priorLoginTimeout;boolean ownsLoginBudget;
    final String origin="https://recording.example.invalid",csrf="C".repeat(43);
    final HttpClient client=HttpClient.newHttpClient();
    @BeforeAll void start() { startWith(); }
    void startWith(String... additional) {
        priorLoginTimeout=java.sql.DriverManager.getLoginTimeout();ownsLoginBudget=true;
        String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");
        if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Fresh owned schema required");
        var source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var args=new ArrayList<>(List.of("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR",
                "--spring.datasource.url="+url,"--spring.datasource.username="+System.getenv("AMA_C_RECORDING_DATABASE_USERNAME"),
                "--spring.datasource.password="+System.getenv("AMA_C_RECORDING_DATABASE_PASSWORD"),"--identity.allowed-origin="+origin,
                "--cas.origin=http://127.0.0.1:8792","--cas.service-url="+origin+"/auth/cas/callback","--cas.logout-service-url="+origin+"/login"));args.addAll(List.of(additional));
        app=new SpringApplicationBuilder(ApiApplication.class,DatabaseProtocolTest.class).run(args.toArray(String[]::new));
    }
    /** This class isolates current ACL behavior; RecordingFileStorageIT qualifies real file persistence. */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    public static class DatabaseProtocolTest {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        RecordingService protocolTestRecordingService(javax.sql.DataSource source){return new RecordingService(new JdbcRecordingPersistence(source));}
    }
    @AfterAll void stop(){try{if(app!=null){app.close();app=null;}}finally{if(ownsLoginBudget){java.sql.DriverManager.setLoginTimeout(priorLoginTimeout);ownsLoginBudget=false;}}}
    String identity(String id) throws Exception {
        jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        jdbc.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,0,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",hash,id,csrf);
        return token;
    }
    static String zeroHash()throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[4]));}
    HttpResponse<String> call(String method,String path,String token,byte[] bytes,Map<String,String> headers) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1"+path))
                .header("Origin",origin).header("X-CSRF-Token",csrf).header("Cookie","__Host-AMA_SESSION="+token);
        headers.forEach(b::header);return client.send(b.method(method,HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode accepted(HttpResponse<String> response,int status) throws Exception {assertEquals(status,response.statusCode(),response.body());return json.readTree(response.body());}
    @Test void foreignIdentityAndDeletedMeetingCannotWriteOrReadRecording() throws Exception {
        String owner="record-owner-"+UUID.randomUUID(),other="record-other-"+UUID.randomUUID();String token=identity(owner),foreign=identity(other);
        var meeting=accepted(call("POST","/meetings",token,"{}".getBytes(StandardCharsets.UTF_8),Map.of("Idempotency-Key",UUID.randomUUID().toString(),"Content-Type","application/json")),201).get("id").asText();
        var session=accepted(call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),201);
        String id=session.get("sessionId").asText(),part=session.get("partId").asText(),base="/recordings/"+id;
        var headers=Map.of("Content-Type","application/octet-stream","X-Writer-Epoch","1","X-Part-Id",part,"X-Seq","1","X-Sample-Start","0","X-Sample-Count","2","X-Sha256",zeroHash());
        accepted(call("POST",base+"/chunks",foreign,new byte[4],headers),403);
        accepted(call("GET",base,foreign,new byte[0],Map.of()),403);
        accepted(call("GET",base+"/manifest",foreign,new byte[0],Map.of()),403);
        accepted(call("POST",base+"/pause",foreign,new byte[0],Map.of("X-Expected-Epoch","1")),403);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,id));
        String collector=UUID.randomUUID().toString();var lease=accepted(call("POST",base+"/writer-lease",token,json.writeValueAsBytes(Map.of("expectedEpoch",1,"collectorId",collector,"requestId",UUID.randomUUID().toString())),Map.of("Content-Type","application/json")),200);assertEquals(2,lease.path("writerEpoch").asLong());
        headers=Map.of("Content-Type","application/octet-stream","X-Writer-Epoch","2","X-Collector-Id",collector,"X-Part-Id",part,"X-Seq","1","X-Sample-Start","0","X-Sample-Count","2","X-Sha256",zeroHash());
        accepted(call("POST",base+"/chunks",token,new byte[4],headers),200);
        var manifest=accepted(call("GET",base+"/manifest",token,new byte[0],Map.of()),200);assertEquals(id,manifest.path("sessionId").asText());assertEquals(part,manifest.path("parts").get(0).path("partId").asText());assertEquals(1,manifest.path("parts").get(0).path("highestReceivedSeq").asLong());assertEquals(2,manifest.path("parts").get(0).path("sampleCount").asLong());
        accepted(call("GET",base+"/ack?partId="+part,token,new byte[0],Map.of()),200);
        jdbc.update("UPDATE meeting SET lifecycle='DELETING' WHERE id=?",meeting);
        accepted(call("GET",base,token,new byte[0],Map.of()),403);
        accepted(call("GET",base+"/manifest",token,new byte[0],Map.of()),403);
        accepted(call("POST",base+"/resume",token,new byte[0],Map.of("X-Expected-Epoch","1")),403);
        accepted(call("POST",base+"/finish",token,("{\""+part+"\":1}").getBytes(StandardCharsets.UTF_8),Map.of("Content-Type","application/json","X-Expected-Epoch","1")),403);
        assertEquals("OPEN",jdbc.queryForObject("SELECT state FROM recording_session WHERE session_id=?",String.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,id));
    }
    @Test void realHttpMutationsRequireCurrentCollectorLease()throws Exception {
        String token=identity("lease-http-"+UUID.randomUUID());String meeting=accepted(call("POST","/meetings",token,"{}".getBytes(StandardCharsets.UTF_8),Map.of("Idempotency-Key",UUID.randomUUID().toString(),"Content-Type","application/json")),201).path("id").asText();
        var s=accepted(call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),201);String id=s.path("sessionId").asText(),part=s.path("partId").asText(),base="/recordings/"+id,collector=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();
        assertEquals(s,accepted(call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),201));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recording_session WHERE meeting_id=?",Integer.class,meeting));
        var unclaimed=Map.of("Content-Type","application/octet-stream","X-Writer-Epoch","1","X-Collector-Id",collector,"X-Part-Id",part,"X-Seq","1","X-Sample-Start","0","X-Sample-Count","2","X-Sha256",zeroHash());
        accepted(call("POST",base+"/chunks",token,new byte[4],unclaimed),409);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,id));
        byte[] claim=json.writeValueAsBytes(Map.of("expectedEpoch",1,"collectorId",collector,"requestId",key));var first=accepted(call("POST",base+"/writer-lease",token,claim,Map.of("Content-Type","application/json")),200);assertEquals(2,first.path("writerEpoch").asLong());assertEquals(first,accepted(call("POST",base+"/writer-lease",token,claim,Map.of("Content-Type","application/json")),200));
        accepted(call("POST",base+"/writer-lease",token,json.writeValueAsBytes(Map.of("expectedEpoch",2,"collectorId",UUID.randomUUID().toString(),"requestId",UUID.randomUUID().toString())),Map.of("Content-Type","application/json")),409);
        var owned=Map.of("Content-Type","application/octet-stream","X-Writer-Epoch","2","X-Collector-Id",collector,"X-Part-Id",part,"X-Seq","1","X-Sample-Start","0","X-Sample-Count","2","X-Sha256",zeroHash());accepted(call("POST",base+"/chunks",token,new byte[4],owned),200);
        accepted(call("POST",base+"/writer-lease/renew",token,json.writeValueAsBytes(Map.of("expectedEpoch",1,"collectorId",collector)),Map.of("Content-Type","application/json")),409);
        accepted(call("POST",base+"/writer-lease/renew",token,json.writeValueAsBytes(Map.of("expectedEpoch",2,"collectorId",collector)),Map.of("Content-Type","application/json")),200);
        accepted(call("POST",base+"/pause",token,new byte[0],Map.of("X-Expected-Epoch","2","X-Collector-Id",UUID.randomUUID().toString())),409);assertEquals("OPEN",jdbc.queryForObject("SELECT state FROM recording_session WHERE session_id=?",String.class,id));
        accepted(call("POST",base+"/pause",token,new byte[0],Map.of("X-Expected-Epoch","2","X-Collector-Id",collector)),204);
        var next=accepted(call("POST",base+"/parts",token,new byte[0],Map.of("X-Expected-Epoch","2","X-Collector-Id",collector)),200);assertEquals(3,next.path("writerEpoch").asLong());
        accepted(call("POST",base+"/writer-lease",token,claim,Map.of("Content-Type","application/json")),409);
        accepted(call("POST",base+"/resume",token,new byte[0],Map.of("X-Expected-Epoch","3")),400);
        jdbc.update("UPDATE meeting SET lifecycle='DELETING' WHERE id=?",meeting);
        accepted(call("POST",base+"/writer-lease/renew",token,json.writeValueAsBytes(Map.of("expectedEpoch",3,"collectorId",collector)),Map.of("Content-Type","application/json")),403);
        accepted(call("GET",base+"/ack?partId="+part,token,new byte[0],Map.of()),403);
    }
    @Test void controlMessageBudgetsAndDuplicateFieldsFailBeforeLeaseOrCaptureWrites()throws Exception {
        String token=identity("lease-budget-"+UUID.randomUUID());String meeting=accepted(call("POST","/meetings",token,"{}".getBytes(StandardCharsets.UTF_8),Map.of("Idempotency-Key",UUID.randomUUID().toString(),"Content-Type","application/json")),201).path("id").asText();
        var s=accepted(call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),201);String id=s.path("sessionId").asText(),base="/recordings/"+id,collector=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();var headers=Map.of("Content-Type","application/json");
        String body=json.writeValueAsString(Map.of("expectedEpoch",1,"collectorId",collector,"requestId",key));accepted(call("POST",base+"/writer-lease",token,(body+" ".repeat(1024)).getBytes(StandardCharsets.UTF_8),headers),413);
        String duplicate="{\"expectedEpoch\":1,\"expectedEpoch\":1,\"collectorId\":\""+collector+"\",\"requestId\":\""+key+"\"}";accepted(call("POST",base+"/writer-lease",token,duplicate.getBytes(StandardCharsets.UTF_8),headers),400);
        accepted(call("POST",base+"/writer-lease",token,json.writeValueAsBytes(Map.of("expectedEpoch",1,"collectorId",collector,"requestId",key,"unknown",true)),headers),400);
        accepted(call("POST",base+"/finish",token,("{"+" ".repeat(1025)+"}").getBytes(StandardCharsets.UTF_8),Map.of("Content-Type","application/json","X-Expected-Epoch","1")),413);
        accepted(call("POST",base+"/chunks",token,new byte[4],Map.of("Content-Type","application/octet-stream","X-Writer-Epoch","not-a-number","X-Part-Id",s.path("partId").asText(),"X-Seq","1","X-Sample-Start","0","X-Sample-Count","2")),400);
        assertNull(jdbc.queryForObject("SELECT writer_collector_id FROM recording_session WHERE session_id=?",String.class,id));assertEquals("OPEN",jdbc.queryForObject("SELECT state FROM recording_session WHERE session_id=?",String.class,id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,id));
    }
    @Test void binaryChunksRequireTheDeclaredShaBeforeAnyManifestWrite()throws Exception {
        String token=identity("lease-sha-"+UUID.randomUUID());String meeting=accepted(call("POST","/meetings",token,"{}".getBytes(StandardCharsets.UTF_8),Map.of("Idempotency-Key",UUID.randomUUID().toString(),"Content-Type","application/json")),201).path("id").asText();
        var s=accepted(call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),201);String id=s.path("sessionId").asText(),base="/recordings/"+id,collector=UUID.randomUUID().toString();
        accepted(call("POST",base+"/writer-lease",token,json.writeValueAsBytes(Map.of("expectedEpoch",1,"collectorId",collector,"requestId",UUID.randomUUID().toString())),Map.of("Content-Type","application/json")),200);
        byte[] pcm={1,2,3,4};var headers=new HashMap<>(Map.of("Content-Type","application/octet-stream","X-Writer-Epoch","2","X-Collector-Id",collector,"X-Part-Id",s.path("partId").asText(),"X-Seq","1","X-Sample-Start","0","X-Sample-Count","2"));
        accepted(call("POST",base+"/chunks",token,pcm,headers),400);headers.put("X-Sha256","0".repeat(64));accepted(call("POST",base+"/chunks",token,pcm,headers),400);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,id));
        headers.put("X-Sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pcm)));assertEquals(1,accepted(call("POST",base+"/chunks",token,pcm,headers),200).path("highestContiguousSeq").asLong());
    }
    @Test void frozenMeetingRejectsCaptureAndLeaseMutationsButKeepsOwnerAckReadable()throws Exception {
        String token=identity("lease-frozen-"+UUID.randomUUID());String meeting=accepted(call("POST","/meetings",token,"{}".getBytes(StandardCharsets.UTF_8),Map.of("Idempotency-Key",UUID.randomUUID().toString(),"Content-Type","application/json")),201).path("id").asText();
        var s=accepted(call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),201);String id=s.path("sessionId").asText(),part=s.path("partId").asText(),base="/recordings/"+id,collector=UUID.randomUUID().toString();
        accepted(call("POST",base+"/writer-lease",token,json.writeValueAsBytes(Map.of("expectedEpoch",1,"collectorId",collector,"requestId",UUID.randomUUID().toString())),Map.of("Content-Type","application/json")),200);
        // Controlled negative latch, never a fabricated successful publication/producer fixture.
        jdbc.update("UPDATE meeting SET ever_published=1,stage='PUBLISHED' WHERE id=?",meeting);
        accepted(call("POST",base+"/chunks",token,new byte[4],Map.of("Content-Type","application/octet-stream","X-Writer-Epoch","2","X-Collector-Id",collector,"X-Part-Id",part,"X-Seq","1","X-Sample-Start","0","X-Sample-Count","2","X-Sha256",zeroHash())),409);
        accepted(call("POST",base+"/writer-lease/renew",token,json.writeValueAsBytes(Map.of("expectedEpoch",2,"collectorId",collector)),Map.of("Content-Type","application/json")),409);
        accepted(call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),409);
        var ack=accepted(call("GET",base+"/ack?partId="+part,token,new byte[0],Map.of()),200);assertEquals(0,ack.path("highestContiguousSeq").asLong());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recording_session WHERE meeting_id=?",Integer.class,meeting));
    }
}
