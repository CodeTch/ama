package ama.recording;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual embedded HTTP upgrade and binary transport, independently encoded by the client. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordingSocketIT {
 final RecordingAccessIT http=new RecordingAccessIT();final List<WebSocket> sockets=new ArrayList<>();
 @BeforeAll void start(){http.startWith("--recording.max-connections=2","--recording.ingress.burst-bytes=64080","--recording.ingress.bytes-per-second=1");}
 @AfterAll void stop(){sockets.forEach(WebSocket::abort);http.stop();}
 record Capture(String session,String part,String collector,long epoch){}
 Capture capture(String token)throws Exception {
  String meeting=http.accepted(http.call("POST","/meetings",token,"{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),Map.of("Content-Type","application/json","Idempotency-Key",UUID.randomUUID().toString())),201).path("id").asText();
  var session=http.accepted(http.call("POST","/meetings/"+meeting+"/recordings",token,new byte[0],Map.of()),201);var value=new Capture(session.path("sessionId").asText(),session.path("partId").asText(),UUID.randomUUID().toString(),1);return claim(token,value);
 }
 Capture claim(String token,Capture value)throws Exception {
  var lease=http.accepted(http.call("POST","/recordings/"+value.session()+"/writer-lease",token,http.json.writeValueAsBytes(Map.of("expectedEpoch",value.epoch(),"collectorId",value.collector(),"requestId",UUID.randomUUID().toString())),Map.of("Content-Type","application/json")),200);
  return new Capture(value.session(),value.part(),value.collector(),lease.path("writerEpoch").asLong());
 }
 final class Probe implements WebSocket.Listener {
  final BlockingQueue<String> messages=new ArrayBlockingQueue<>(16);final CompletableFuture<Integer> closed=new CompletableFuture<>();final StringBuilder text=new StringBuilder();
  public void onOpen(WebSocket socket){socket.request(1);}
  public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){
   if(text.length()+data.length()>8192){socket.abort();closed.completeExceptionally(new AssertionError("Unbounded server message"));return null;}text.append(data);if(last){if(!messages.offer(text.toString())){socket.abort();closed.completeExceptionally(new AssertionError("Unbounded server queue"));}text.setLength(0);}socket.request(1);return null;
  }
  public CompletionStage<?> onClose(WebSocket socket,int code,String reason){closed.complete(code);return null;}
  public void onError(WebSocket socket,Throwable error){closed.completeExceptionally(error);}
  JsonNode next()throws Exception {String value=messages.poll(5,TimeUnit.SECONDS);assertNotNull(value,"Expected bounded socket response");return http.json.readTree(value);}
 }
 WebSocket connect(String token,Capture value,Probe probe,String origin,boolean csrf)throws Exception {
  var builder=http.client.newWebSocketBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).header("Origin",origin);
  if(token!=null)builder.header("Cookie","__Host-AMA_SESSION="+token);
  if(csrf)builder.subprotocols("ama.recording.v1","ama.csrf."+http.csrf);else builder.subprotocols("ama.recording.v1");
  var socket=builder.buildAsync(URI.create("ws://127.0.0.1:"+http.app.getEnvironment().getProperty("local.server.port")+"/api/v1/recordings/"+value.session()+"/stream"),probe).get(5,TimeUnit.SECONDS);sockets.add(socket);return socket;
 }
 void ready(WebSocket socket,Probe probe,Capture value)throws Exception {
  socket.sendText(http.json.writeValueAsString(Map.of("type","HELLO","collectorId",value.collector(),"writerEpoch",value.epoch())),true).get(5,TimeUnit.SECONDS);var response=probe.next();assertEquals("READY",response.path("type").asText());assertEquals(value.session(),response.path("sessionId").asText());assertEquals(value.epoch(),response.path("writerEpoch").asLong());
 }
 static byte[] frame(Capture value,long seq,byte[] pcm)throws Exception {
  return frame(value,seq,(seq-1)*2,pcm);
 }
 static byte[] frame(Capture value,long seq,long sampleStart,byte[] pcm)throws Exception {
  // Independent client implementation of the documented80-byte network header.
  var out=ByteBuffer.allocate(80+pcm.length).order(ByteOrder.BIG_ENDIAN);var id=UUID.fromString(value.part());out.put(new byte[]{'A','M','A','1'}).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).putLong(value.epoch()).putLong(seq).putLong(sampleStart).putInt(pcm.length/2).put(MessageDigest.getInstance("SHA-256").digest(pcm)).put(pcm);return out.array();
 }
 void handshakeDenied(String token,Capture value,String origin,boolean csrf,int status)throws Exception {
  var error=assertThrows(ExecutionException.class,()->connect(token,value,new Probe(),origin,csrf));var cause=assertInstanceOf(WebSocketHandshakeException.class,error.getCause());assertEquals(status,cause.getResponse().statusCode());
 }
 @Test void reconnectFencesOldConnectionAndRevokedSessionCannotAppendAudio()throws Exception {
  String token=http.identity("ws-owner-"+UUID.randomUUID());var first=capture(token);var oldProbe=new Probe();var old=connect(token,first,oldProbe,http.origin,true);assertEquals("ama.recording.v1",old.getSubprotocol());ready(old,oldProbe,first);
  old.sendBinary(ByteBuffer.wrap(frame(first,1,new byte[]{1,2,3,4})),true).get(5,TimeUnit.SECONDS);var ack=oldProbe.next();assertEquals("ACK",ack.path("type").asText());assertEquals(1,ack.path("highestContiguousSeq").asLong());assertEquals(first.part(),ack.path("partId").asText());
  var second=claim(token,first);var newProbe=new Probe();var next=connect(token,second,newProbe,http.origin,true);ready(next,newProbe,second);
  old.sendBinary(ByteBuffer.wrap(frame(first,2,new byte[]{1,2,3,4})),true).get(5,TimeUnit.SECONDS);assertEquals("ERROR",oldProbe.next().path("type").asText());assertEquals(1008,oldProbe.closed.get(5,TimeUnit.SECONDS));
  next.sendBinary(ByteBuffer.wrap(frame(second,2,new byte[]{4,3,2,1})),true).get(5,TimeUnit.SECONDS);assertEquals(2,newProbe.next().path("highestContiguousSeq").asLong());
  next.sendBinary(ByteBuffer.wrap(frame(second,1,new byte[]{1,2,3,4})),true).get(5,TimeUnit.SECONDS);assertEquals(2,newProbe.next().path("highestContiguousSeq").asLong());assertEquals(2,http.jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,second.session()));
  http.app.getBean(ama.identity.SessionPort.class).revoke(token,"recording-ws-revoke");next.sendBinary(ByteBuffer.wrap(frame(second,3,new byte[]{1,2,3,4})),true).get(5,TimeUnit.SECONDS);assertEquals("ERROR",newProbe.next().path("type").asText());assertEquals(1008,newProbe.closed.get(5,TimeUnit.SECONDS));assertEquals(2,http.jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,second.session()));
 }
 @Test void handshakeAndFrameBoundsRejectForeignUnauthenticatedUnboundAndDamagedWrites()throws Exception {
  String owner=http.identity("ws-owner-"+UUID.randomUUID()),foreign=http.identity("ws-foreign-"+UUID.randomUUID());var capture=capture(owner);
  handshakeDenied(null,capture,http.origin,true,401);handshakeDenied(owner,capture,"https://foreign.example.invalid",true,403);handshakeDenied(owner,capture,http.origin,false,403);handshakeDenied(foreign,capture,http.origin,true,403);
  var unboundProbe=new Probe();var unbound=connect(owner,capture,unboundProbe,http.origin,true);unbound.sendBinary(ByteBuffer.wrap(frame(capture,1,new byte[4])),true).get(5,TimeUnit.SECONDS);assertEquals("ERROR",unboundProbe.next().path("type").asText());assertEquals(1008,unboundProbe.closed.get(5,TimeUnit.SECONDS));
  capture=claim(owner,capture);var largeProbe=new Probe();var large=connect(owner,capture,largeProbe,http.origin,true);ready(large,largeProbe,capture);large.sendBinary(ByteBuffer.wrap(new byte[64081]),true).get(5,TimeUnit.SECONDS);assertEquals(1009,largeProbe.closed.get(5,TimeUnit.SECONDS));
  capture=claim(owner,capture);var badProbe=new Probe();var bad=connect(owner,capture,badProbe,http.origin,true);ready(bad,badProbe,capture);var damaged=frame(capture,1,new byte[4]);damaged[48]^=1;bad.sendBinary(ByteBuffer.wrap(damaged),true).get(5,TimeUnit.SECONDS);assertEquals("ERROR",badProbe.next().path("type").asText());assertEquals(1007,badProbe.closed.get(5,TimeUnit.SECONDS));assertEquals(0,http.jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,capture.session()));
 }
 @Test void connectionBoundAndHelloDeadlineReleaseCapacityWithoutAudioWrites()throws Exception {
  String token=http.identity("ws-capacity-"+UUID.randomUUID());var capture=capture(token);var a=new Probe();connect(token,capture,a,http.origin,true);var b=new Probe();connect(token,capture,b,http.origin,true);var full=new Probe();connect(token,capture,full,http.origin,true);assertEquals(1013,full.closed.get(5,TimeUnit.SECONDS));
  assertEquals("HELLO_REQUIRED",a.next().path("code").asText());assertEquals(1008,a.closed.get(5,TimeUnit.SECONDS));assertEquals("HELLO_REQUIRED",b.next().path("code").asText());assertEquals(1008,b.closed.get(5,TimeUnit.SECONDS));
  var recovered=new Probe();var socket=connect(token,capture,recovered,http.origin,true);ready(socket,recovered,capture);socket.sendBinary(ByteBuffer.wrap(frame(capture,1,new byte[4])),true).get(5,TimeUnit.SECONDS);assertEquals(1,recovered.next().path("highestContiguousSeq").asLong());socket.sendClose(1000,"done").get(5,TimeUnit.SECONDS);recovered.closed.get(5,TimeUnit.SECONDS);
 }
 @Test void currentMeetingFreezeFencesAnAlreadyOpenSocket()throws Exception {
  String token=http.identity("ws-frozen-"+UUID.randomUUID());var capture=capture(token);var probe=new Probe();var socket=connect(token,capture,probe,http.origin,true);ready(socket,probe,capture);
  http.jdbc.update("UPDATE meeting SET ever_published=1,stage='PUBLISHED' WHERE id=(SELECT meeting_id FROM recording_session WHERE session_id=?)",capture.session());socket.sendBinary(ByteBuffer.wrap(frame(capture,1,new byte[4])),true).get(5,TimeUnit.SECONDS);assertEquals("RECORDING_READ_ONLY",probe.next().path("code").asText());assertEquals(1008,probe.closed.get(5,TimeUnit.SECONDS));assertEquals(0,http.jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,capture.session()));
 }
 @Test void httpAndSocketShareTheSameIngressBudgetAndNeverAckRejectedBytes()throws Exception {
  String token=http.identity("ws-rate-"+UUID.randomUUID());var capture=capture(token);byte[] pcm=new byte[64000];var headers=new HashMap<String,String>();headers.put("Content-Type","application/octet-stream");headers.put("X-Writer-Epoch",Long.toString(capture.epoch()));headers.put("X-Collector-Id",capture.collector());headers.put("X-Part-Id",capture.part());headers.put("X-Seq","1");headers.put("X-Sample-Start","0");headers.put("X-Sample-Count","32000");headers.put("X-Sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pcm)));String path="/recordings/"+capture.session()+"/chunks";
  assertEquals(1,http.accepted(http.call("POST",path,token,pcm,headers),200).path("highestContiguousSeq").asLong());headers.put("X-Seq","2");headers.put("X-Sample-Start","32000");var rejected=http.call("POST",path,token,pcm,headers);http.accepted(rejected,429);assertEquals("1",rejected.headers().firstValue("Retry-After").orElseThrow());
  var probe=new Probe();var socket=connect(token,capture,probe,http.origin,true);ready(socket,probe,capture);socket.sendBinary(ByteBuffer.wrap(frame(capture,2,32000,pcm)),true).get(5,TimeUnit.SECONDS);assertEquals("RATE_LIMITED",probe.next().path("code").asText());assertEquals(1013,probe.closed.get(5,TimeUnit.SECONDS));assertEquals(1,http.jdbc.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,capture.session()));
 }
}
