package ama.api;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.RecordingContracts;
import ama.db.TransactionRunner;
import ama.identity.SessionPort;
import ama.recording.*;
import ama.realtime.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** One bounded collector connection. Each frame re-resolves identity and the current database gates. */
final class RecordingWebSocketHandler extends AbstractWebSocketHandler implements SubProtocolCapable,AutoCloseable {
 private static final String TOKEN="ama.recording.token",ROUTE="ama.recording.route";
 private static final String PROTOCOL="ama.recording.v1";
 private final SessionPort identities;private final RecordingService recordings;private final RecordingSocketHandler chunks;
 private final JdbcRecordingWriterLeases leases;private final RecordingMutationGuard mutations;private final TransactionRunner tx;private final MeetingAccess access;private final String origin;
 private final Semaphore capacity;private final ConcurrentHashMap<String,Connection> connections=new ConcurrentHashMap<>();
 private final RecordingIngressBudget ingress;
 private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"ama-recording-hello");t.setDaemon(true);return t;});
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(128).maxNumberLength(20).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 private record Bound(String collector,RecordingContracts.Session session){}
 private static final class ExpiredIdentity extends RuntimeException {}
 private static final class Connection {
  final WebSocketSession socket;final String token,id=UUID.randomUUID().toString();final RecordingContracts.Session route;
  final AtomicBoolean processing=new AtomicBoolean(),released=new AtomicBoolean(),detached=new AtomicBoolean();volatile Bound bound;volatile ScheduledFuture<?> hello;
  Connection(WebSocketSession socket,String token,RecordingContracts.Session route){this.socket=socket;this.token=token;this.route=route;}
 }
 RecordingWebSocketHandler(SessionPort identities,RecordingService recordings,JdbcRecordingWriterLeases leases,RecordingMutationGuard mutations,TransactionRunner tx,MeetingAccess access,Environment environment,RecordingIngressBudget ingress){
  this.identities=identities;this.recordings=recordings;chunks=new RecordingSocketHandler(recordings);this.leases=leases;this.mutations=mutations;this.tx=tx;this.access=access;this.ingress=ingress;origin=environment.getProperty("identity.allowed-origin","");int limit=environment.getProperty("recording.max-connections",Integer.class,128);if(limit<1||limit>1000)throw new IllegalArgumentException("Finite recording connection bound required");capacity=new Semaphore(limit);timer.setRemoveOnCancelPolicy(true);
 }
 public List<String> getSubProtocols(){return List.of(PROTOCOL);}
 HandshakeInterceptor handshake(){return new HandshakeInterceptor(){
  public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
   response.getHeaders().set("Cache-Control","no-store");
   try{
    if(!(request instanceof ServletServerHttpRequest servlet)||request.getURI().getRawQuery()!=null)throw new MeetingAccess.AccessDeniedException();
    var origins=request.getHeaders().get("Origin");if(origin.isBlank()||origins==null||origins.size()!=1||!origin.equals(origins.getFirst()))throw new MeetingAccess.AccessDeniedException();
    if(!(servlet.getServletRequest().getAttribute(SessionSecurityFilter.TOKEN) instanceof String token))throw new ExpiredIdentity();
    var identity=identities.resolve(token).orElseThrow(ExpiredIdentity::new);var offer=request.getHeaders().get("Sec-WebSocket-Protocol");
    if(offer==null||offer.size()>4||offer.stream().mapToInt(String::length).sum()>512)throw new MeetingAccess.AccessDeniedException();
    var offered=Arrays.stream(String.join(",",offer).split(",",-1)).map(String::trim).toList();String proof="ama.csrf."+identity.csrfToken();
    if(offered.size()!=2||!offered.contains(PROTOCOL)||offered.stream().noneMatch(value->MessageDigest.isEqual(value.getBytes(StandardCharsets.US_ASCII),proof.getBytes(StandardCharsets.US_ASCII))))throw new MeetingAccess.AccessDeniedException();
    String path=request.getURI().getPath(),prefix="/api/v1/recordings/",suffix="/stream";if(!path.startsWith(prefix)||!path.endsWith(suffix))throw new IllegalArgumentException("Invalid recording route");String id=path.substring(prefix.length(),path.length()-suffix.length());ama.contract.OutboxContracts.uuid(id);
    var route=recordings.session(id);tx.required(()->{var held=access.lock(identity.actor(),route.meetingId());access.require(held,MeetingAccess.Action.EDIT);mutations.require(identity.actor(),route.meetingId(),held);if(recordings.finished(id))throw new RecordingService.Closed();return null;});
    attributes.put(TOKEN,token);attributes.put(ROUTE,route);return true;
   }catch(ExpiredIdentity denied){response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;}
   catch(MeetingAccess.AccessDeniedException denied){response.setStatusCode(HttpStatus.FORBIDDEN);return false;}
   catch(RecordingMutationGuard.Frozen|RecordingService.Closed conflict){response.setStatusCode(HttpStatus.CONFLICT);return false;}
   catch(IllegalArgumentException invalid){response.setStatusCode(HttpStatus.BAD_REQUEST);return false;}
   catch(RuntimeException unavailable){response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);return false;}
  }
  public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception){}
 };}
 @Override public void afterConnectionEstablished(WebSocketSession socket)throws Exception {
  Object token=socket.getAttributes().remove(TOKEN),route=socket.getAttributes().remove(ROUTE);
  if(!(token instanceof String raw)||!(route instanceof RecordingContracts.Session initial)){socket.close(CloseStatus.POLICY_VIOLATION);return;}
  socket.setBinaryMessageSizeLimit(RecordingBinaryCodec.MAX_FRAME_BYTES);socket.setTextMessageSizeLimit(1024);
  if(socket instanceof NativeWebSocketSession nativeSocket){var nativeSession=nativeSocket.getNativeSession(jakarta.websocket.Session.class);if(nativeSession!=null)nativeSession.getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT",5000L);}
  if(!capacity.tryAcquire()){socket.close(new CloseStatus(1013,"RETRY_LATER"));return;}
  var connection=new Connection(socket,raw,initial);if(connections.putIfAbsent(socket.getId(),connection)!=null){capacity.release();socket.close(CloseStatus.POLICY_VIOLATION);return;}
  connection.hello=timer.schedule(()->{if(connection.bound==null)fail(connection,"HELLO_REQUIRED",CloseStatus.POLICY_VIOLATION);},5,TimeUnit.SECONDS);
 }
 private Actor actor(Connection c){return identities.resolve(c.token).orElseThrow(ExpiredIdentity::new).actor();}
 private boolean enter(Connection c){if(c.processing.compareAndSet(false,true))return true;fail(c,"RETRY_LATER",new CloseStatus(1013,"RETRY_LATER"));return false;}
 @Override protected void handleTextMessage(WebSocketSession socket,TextMessage message){
  var c=connections.get(socket.getId());if(c==null)return;if(!enter(c))return;
  try{
   if(c.bound!=null){fail(c,"HELLO_ALREADY_BOUND",CloseStatus.POLICY_VIOLATION);return;}var actor=actor(c);String text=message.getPayload();if(text.length()>1024||text.getBytes(StandardCharsets.UTF_8).length>1024)throw new IllegalArgumentException("HELLO exceeds limit");var hello=json.readTree(text);
   if(hello==null||!hello.isObject()||hello.size()!=3||!hello.path("type").isTextual()||!hello.path("type").asText().equals("HELLO")||!hello.path("collectorId").isTextual()||!hello.path("writerEpoch").isIntegralNumber()||!hello.path("writerEpoch").canConvertToLong())throw new IllegalArgumentException("Invalid HELLO");
   String collector=hello.path("collectorId").asText();long epoch=hello.path("writerEpoch").longValue();ama.contract.OutboxContracts.uuid(collector);
   c.bound=tx.required(()->{var gate=access.lock(actor,c.route.meetingId());access.require(gate,MeetingAccess.Action.EDIT);mutations.require(actor,c.route.meetingId(),gate);leases.bindConnection(c.route.sessionId(),epoch,collector,c.id);var current=recordings.session(c.route.sessionId());if(!current.meetingId().equals(c.route.meetingId())||current.writerEpoch()!=epoch)throw new RecordingService.StaleWriter();return new Bound(collector,current);});
   if(c.hello!=null)c.hello.cancel(false);if(connections.get(socket.getId())!=c||!socket.isOpen()){cleanup(c);return;}
   send(c,Map.of("type","READY","sessionId",c.bound.session().sessionId(),"partId",c.bound.session().partId(),"writerEpoch",c.bound.session().writerEpoch()));
  }catch(JsonProcessingException|IllegalArgumentException invalid){fail(c,"INVALID_HELLO",CloseStatus.BAD_DATA);}
  catch(Exception failure){failed(c,failure);}finally{c.processing.set(false);}
 }
 @Override protected void handleBinaryMessage(WebSocketSession socket,BinaryMessage message){
  var c=connections.get(socket.getId());if(c==null)return;if(!enter(c))return;
  try{
   var bound=c.bound;if(bound==null){fail(c,"HELLO_REQUIRED",CloseStatus.POLICY_VIOLATION);return;}var actor=actor(c);var chunk=RecordingBinaryCodec.decode(message.getPayload());
   if(chunk.writerEpoch()!=bound.session().writerEpoch())throw new RecordingService.StaleWriter();
   var ack=tx.required(()->{var gate=access.lock(actor,bound.session().meetingId());access.require(gate,MeetingAccess.Action.EDIT);mutations.require(actor,bound.session().meetingId(),gate);leases.requireConnection(bound.session().sessionId(),bound.session().writerEpoch(),bound.collector(),c.id);ingress.require(actor.enterpriseId(),bound.session().sessionId(),bound.collector(),message.getPayloadLength());return chunks.accept(bound.session(),chunk);});
   send(c,Map.of("type","ACK","sessionId",bound.session().sessionId(),"partId",chunk.partId(),"writerEpoch",bound.session().writerEpoch(),"highestContiguousSeq",ack.highestContiguousSeq(),"missingSeqs",ack.missingSeqs()));
  }catch(IllegalArgumentException invalid){fail(c,"INVALID_FRAME",CloseStatus.BAD_DATA);}
  catch(Exception failure){failed(c,failure);}finally{c.processing.set(false);}
 }
 private void send(Connection c,Map<String,Object> body)throws IOException {String text=json.writeValueAsString(body);if(text.length()>8192)throw new IOException("Socket response exceeds limit");synchronized(c.socket){if(!c.socket.isOpen())throw new IOException("Socket closed");c.socket.sendMessage(new TextMessage(text));}}
 private void failed(Connection c,Exception failure){
  if(failure instanceof ExpiredIdentity||failure instanceof MeetingAccess.AccessDeniedException)fail(c,"FORBIDDEN",CloseStatus.POLICY_VIOLATION);
  else if(failure instanceof RecordingIngressBudget.RateLimited)fail(c,"RATE_LIMITED",new CloseStatus(1013,"RETRY_LATER"));
  else if(failure instanceof RecordingMutationGuard.Frozen)fail(c,"RECORDING_READ_ONLY",CloseStatus.POLICY_VIOLATION);
  else if(failure instanceof RecordingService.StaleWriter||failure instanceof RecordingService.HashConflict||failure instanceof RecordingService.Closed||failure instanceof RecordingService.Paused)fail(c,"RECORDING_CONFLICT",CloseStatus.POLICY_VIOLATION);
  else {
   Throwable cause=failure;for(int i=0;i<8&&cause.getCause()!=null;i++)cause=cause.getCause();
   var log=org.slf4j.LoggerFactory.getLogger(RecordingWebSocketHandler.class);
   if(cause instanceof java.sql.SQLException sql)log.warn("Recording socket unavailable: type={} sqlState={} vendorCode={}",cause.getClass().getSimpleName(),sql.getSQLState(),sql.getErrorCode());
   else log.warn("Recording socket unavailable: type={}",cause.getClass().getSimpleName());
   fail(c,"RECORDING_UNAVAILABLE",new CloseStatus(1013,"RETRY_LATER"));
  }
 }
 private void fail(Connection c,String code,CloseStatus status){try{send(c,Map.of("type","ERROR","code",code));}catch(IOException|RuntimeException ignored){}try{c.socket.close(status);}catch(IOException|RuntimeException ignored){}finally{cleanup(c);}}
 private void cleanup(Connection c){
  connections.remove(c.socket.getId(),c);if(c.hello!=null)c.hello.cancel(false);if(c.released.compareAndSet(false,true))capacity.release();var bound=c.bound;
  if(bound!=null&&c.detached.compareAndSet(false,true))try{leases.detachConnection(bound.session().sessionId(),bound.session().writerEpoch(),bound.collector(),c.id);}catch(RuntimeException ignored){/* Expiry/new claim still fences the closed connection. */}
 }
 @Override public void afterConnectionClosed(WebSocketSession socket,CloseStatus status){var c=connections.get(socket.getId());if(c!=null)cleanup(c);}
 @Override public void handleTransportError(WebSocketSession socket,Throwable failure){var c=connections.get(socket.getId());if(c!=null)fail(c,"TRANSPORT_CLOSED",new CloseStatus(1013,"RETRY_LATER"));}
 public void close(){for(var c:connections.values())fail(c,"SERVER_STOPPING",CloseStatus.GOING_AWAY);timer.shutdownNow();}
}
