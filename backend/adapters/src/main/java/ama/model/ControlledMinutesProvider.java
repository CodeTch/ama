package ama.model;

import ama.canonical.CanonicalValue;
import ama.config.CanonicalJson;
import ama.contract.MinutesContracts.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import static ama.canonical.CanonicalValue.*;
import static ama.config.WireValues.*;

/** Explicit local controlled provider with a bounded HTTP/1.1 parser before application code.
 * No framework-normalized headers, implicit body buffering, redirect or resend endpoint. */
public final class ControlledMinutesProvider implements AutoCloseable {
 private final ControlledMinutesProviderStore store;private final ServerSocket listener;private final byte[] authorization;
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"controlled-minutes-http");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final ScheduledExecutorService timers=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"controlled-minutes-deadline");t.setDaemon(true);return t;});
 private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();private volatile boolean closed;private Thread accepting;
 public ControlledMinutesProvider(Path root,InetSocketAddress bind,String bearer,String mode)throws IOException {
  if(!"CONTROLLED_INSTALLATION".equals(mode)||bind==null||bind.isUnresolved()||!bind.getAddress().getHostAddress().equals("127.0.0.1")||bearer==null||!bearer.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalArgumentException("Explicit controlled loopback installation required");
  authorization=("Bearer "+bearer).getBytes(StandardCharsets.US_ASCII);store=new ControlledMinutesProviderStore(root,mode);listener=new ServerSocket();
  try{listener.bind(bind,8);}catch(IOException e){store.close();listener.close();workers.shutdownNow();timers.shutdownNow();throw e;}
 }
 public synchronized void start()throws IOException {
  if(closed||accepting!=null)throw new IllegalStateException("Controlled provider already started or closed");
  // Only already-reserved computation can resume. GET lookup never invokes this path.
  for(String id:store.pending())complete(id);
  accepting=new Thread(this::accept,"controlled-minutes-accept");accepting.setDaemon(false);accepting.start();
 }
 public int port(){return listener.getLocalPort();}
 private void accept(){while(!closed){Socket socket=null;try{socket=listener.accept();socket.setSoTimeout(5000);sockets.add(socket);Socket owned=socket;var timer=timers.schedule(()->closeSocket(owned),30,TimeUnit.SECONDS);
   try{workers.execute(()->{try{serve(owned);}finally{timer.cancel(false);sockets.remove(owned);closeSocket(owned);}});}catch(RejectedExecutionException full){timer.cancel(false);try{response(owned,503,new byte[0]);}catch(IOException ignored){}sockets.remove(owned);closeSocket(owned);}
  }catch(IOException failure){if(socket!=null){sockets.remove(socket);closeSocket(socket);}if(!closed)closeQuietly();}}}
 private record Request(String method,String target,Map<String,String> headers,InputStream input) {}
 private static final class Rejected extends IOException {final int status;Rejected(int status){this.status=status;}}
 private void serve(Socket socket){try{
   Request request=headers(socket.getInputStream());String auth=request.headers().get("authorization");
   if(auth==null||!MessageDigest.isEqual(authorization,auth.getBytes(StandardCharsets.US_ASCII)))throw new Rejected(401);
   String host=request.headers().get("host");if(!("127.0.0.1:"+port()).equals(host))throw new Rejected(400);
   if(request.headers().containsKey("transfer-encoding")||request.headers().containsKey("expect"))throw new Rejected(400);
   if(request.headers().containsKey("content-encoding"))throw new Rejected(415);
   if(request.method().equals("POST")&&request.target().equals("/v1/minutes/executions")){
    if(!"application/json".equals(request.headers().get("content-type")))throw new Rejected(415);
    String envelope=request.headers().get(ControlledMinutesProtocol.HEADER.toLowerCase(Locale.ROOT));
    if(envelope==null||envelope.isEmpty()||!envelope.matches("[A-Za-z0-9_-]+"))throw new Rejected(400);
    int length=length(request.headers(),true);byte[] body=readBody(request.input(),length);
    var receipt=store.reserve(body,envelope);if(receipt.state().equals("UNKNOWN"))receipt=complete(receipt.executionId());response(socket,200,metadata(receipt));
   }else if(request.method().equals("GET")&&request.target().startsWith("/v1/minutes/executions/")){
    if(length(request.headers(),false)!=0)throw new Rejected(400);String id=request.target().substring("/v1/minutes/executions/".length());ama.contract.MinutesContracts.uuid(id);
    String key=required(request,"x-ama-logical-business-key"),lh=required(request,"x-ama-logical-request-hash"),eh=required(request,"x-ama-execution-request-hash"),ph=required(request,"x-ama-payload-sha256");
    if(!"1".equals(required(request,"x-ama-execution-no"))||!key.startsWith("minutes:"))throw new Rejected(400);ama.contract.MinutesContracts.uuid(key.substring(8));ama.contract.MinutesContracts.hash(lh);ama.contract.MinutesContracts.hash(eh);ama.contract.MinutesContracts.hash(ph);if(!lh.equals(eh))throw new Rejected(409);
    var receipt=store.lookup(id);if(receipt==null)receipt=new ControlledMinutesStore.Receipt("UNKNOWN",id,key,lh,eh,ph,0,null,null,0,null,null);
    if(!key.equals(receipt.logicalBusinessKey())||!lh.equals(receipt.logicalRequestHash())||!eh.equals(receipt.executionRequestHash())||!ph.equals(receipt.payloadSha256()))throw new Rejected(409);response(socket,200,metadata(receipt));
   }else if(request.method().equals("GET")&&request.target().startsWith("/v1/minutes/results/")){
    if(length(request.headers(),false)!=0)throw new Rejected(400);response(socket,200,store.result(request.target().substring("/v1/minutes/results/".length())));
   }else throw new Rejected(404);
  }catch(Rejected rejected){sendFailure(socket,rejected.status);}catch(ama.contract.MinutesContracts.Failure invalid){sendFailure(socket,422);}catch(IllegalArgumentException invalid){sendFailure(socket,400);}catch(IllegalStateException conflict){sendFailure(socket,409);}catch(FileNotFoundException absent){sendFailure(socket,404);}catch(IOException integrity){sendFailure(socket,500);}catch(RuntimeException failure){sendFailure(socket,500);}}
 private static String required(Request request,String name)throws Rejected {String value=request.headers().get(name);if(value==null||value.isEmpty())throw new Rejected(400);return value;}
 private static Request headers(InputStream input)throws IOException {
  var counter=new Counter();String first=line(input,counter,2048);String[] parts=first.split(" ",-1);if(parts.length!=3||!parts[2].equals("HTTP/1.1")||!Set.of("GET","POST").contains(parts[0])||!parts[1].matches("/[A-Za-z0-9/_-]+"))throw new Rejected(400);
  var headers=new LinkedHashMap<String,String>();while(true){String line=line(input,counter,ControlledMinutesProtocol.MAX_ENVELOPE_HEADER+ControlledMinutesProtocol.MAX_HEADER_NAME+2);if(line.isEmpty())break;
   if(line.charAt(0)==' '||line.charAt(0)=='\t')throw new Rejected(400);int colon=line.indexOf(':');if(colon<1||colon>ControlledMinutesProtocol.MAX_HEADER_NAME)throw new Rejected(400);
   String name=line.substring(0,colon);if(!name.matches("[A-Za-z0-9-]+"))throw new Rejected(400);name=name.toLowerCase(Locale.ROOT);String value=line.substring(colon+1);if(value.startsWith(" "))value=value.substring(1);
   if(value.length()>ControlledMinutesProtocol.MAX_ENVELOPE_HEADER)throw new Rejected(431);if(headers.size()>=ControlledMinutesProtocol.MAX_HEADER_COUNT)throw new Rejected(431);if(headers.putIfAbsent(name,value)!=null)throw new Rejected(400);
  }return new Request(parts[0],parts[1],Collections.unmodifiableMap(headers),input);
 }
 private static final class Counter{int bytes;}
 private static String line(InputStream input,Counter counter,int maximum)throws IOException {
  var bytes=new ByteArrayOutputStream(Math.min(maximum,256));boolean cr=false;
  while(true){int b=input.read();if(b<0)throw new EOFException("Incomplete controlled HTTP headers");if(++counter.bytes>ControlledMinutesProtocol.MAX_HEADERS)throw new Rejected(431);
   if(cr){if(b!='\n')throw new Rejected(400);return bytes.toString(StandardCharsets.US_ASCII);}if(b=='\r'){cr=true;continue;}if(b<32||b>126)throw new Rejected(400);if(bytes.size()>=maximum)throw new Rejected(431);bytes.write(b);
  }
 }
 private static int length(Map<String,String> headers,boolean mandatory)throws Rejected {
  String text=headers.get("content-length");if(text==null){if(mandatory)throw new Rejected(411);return 0;}if(!text.matches("0|[1-9][0-9]{0,8}"))throw new Rejected(400);long n=Long.parseLong(text);if(n>ControlledMinutesProtocol.MAX_REQUEST)throw new Rejected(413);return (int)n;
 }
 private static byte[] readBody(InputStream in,int length)throws IOException {byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException("Incomplete controlled body");return bytes;}
 private ControlledMinutesStore.Receipt complete(String id)throws IOException {
  var request=store.request(id);var payload=MaterialMinutesWire.payload(request.payloadValue());
  String instruction=text(payload.get("instructionText"));if(instruction.equals("controlled:definitive-rejection"))return store.reject(id,"CONTROLLED_REJECTION");
  if(instruction.equals("controlled:invalid-json"))return store.commitSuccess(id,"{invalid controlled JSON".getBytes(StandardCharsets.UTF_8));
  if(instruction.equals("controlled:rich-output")||instruction.equals("controlled:rich-no-sensitive"))return store.commitSuccess(id,rich(request,payload));
  var categories=new ArrayList<CanonicalValue.Entry>();for(var category:Category.values())categories.add(field(category.name(),list()));
  // Explicit synthetic model: structurally valid unsupported text, with no invented source claims.
  byte[] output=CanonicalJson.encode(object(field("schemaVersion",string("ama.minutes-output.v1")),field("inputFingerprint",string(request.inputFingerprint())),field("title",string("Controlled minutes")),field("body",string("Controlled synthetic minutes.")),field("elements",new CanonicalValue.ObjectValue(categories)),field("sensitive",list()),field("background",list()),field("sourceRefs",list()),field("evidence",list())),ControlledMinutesStore.MAX_OUTPUT,()->false);
  return store.commitSuccess(id,output);
 }
 /** Deterministic explicit controlled scenario; every referenced locator comes from the verified request. */
 private static byte[] rich(ControlledMinutesProtocol.VerifiedRequest request,Map<String,CanonicalValue> payload){
  var manifest=fields(payload.get("inputManifest"),"meetingId","inputId","confirmation","segments","batch","materials","template","exclusion","elements","sources","configs","scope","base");var sources=array(manifest.get("sources"));if(sources.isEmpty())throw new IllegalArgumentException("Controlled rich scenario needs a real source");var source=fields(sources.getFirst(),"sourceId","locator","role","sourceVersion","sourceContentHash");var alias=source.get("sourceId");var aliases=list(alias);var categories=new ArrayList<CanonicalValue.Entry>();var evidence=new ArrayList<CanonicalValue>();
  evidence.add(object(field("target",object(field("kind",string("BODY")),field("start",number(0)),field("end",number(2)))),field("sourceIds",aliases)));
  for(var category:Category.values()){var elements=new ArrayList<CanonicalValue>();int count=category==Category.ACTION?2:1;for(int n=1;n<=count;n++){String key=category.name()+"_"+n;var fields=new ArrayList<CanonicalValue.Entry>(List.of(field("key",string(key)),field("text",string("😀 Controlled "+key))));if(category==Category.ACTION)fields.add(field("action",object(field("ownerMention",n==1?string("Unaccepted owner"):nil()),field("dueOn",n==1?nil():string("2026-11-01")))));else fields.add(field("action",nil()));elements.add(new CanonicalValue.ObjectValue(fields));evidence.add(object(field("target",object(field("kind",string("ELEMENT")),field("elementKey",string(key)),field("start",number(0)),field("end",number(1)))),field("sourceIds",aliases)));}categories.add(field(category.name(),new CanonicalValue.Sequence(elements)));}
  return CanonicalJson.encode(object(field("schemaVersion",string("ama.minutes-output.v1")),field("inputFingerprint",string(request.inputFingerprint())),field("title",string("Controlled rich minutes")),field("body",string("😀 Controlled rich body.")),field("elements",new CanonicalValue.ObjectValue(categories)),field("sensitive",text(payload.get("instructionText")).equals("controlled:rich-no-sensitive")?list():list(object(field("key",string("d1")),field("category",string("INTERNAL_BUSINESS_DATA")),field("sourceIds",aliases)),object(field("key",string("d2")),field("category",string("POLITICAL_SENSITIVE")),field("sourceIds",aliases)))),field("background",list(object(field("key",string("n1")),field("text",string("Controlled original background")),field("sourceIds",aliases)))),field("sourceRefs",list(object(field("sourceId",alias),field("locator",source.get("locator"))))),field("evidence",new CanonicalValue.Sequence(evidence))),ControlledMinutesStore.MAX_OUTPUT,()->false);
 }
 static byte[] metadata(ControlledMinutesStore.Receipt r){
  var entries=new ArrayList<CanonicalValue.Entry>(List.of(field("protocolVersion",string(ControlledMinutesProtocol.VERSION)),field("state",string(r.state())),field("executionId",string(r.executionId())),field("executionNo",number(1)),field("logicalBusinessKey",string(r.logicalBusinessKey())),field("logicalRequestHash",string(r.logicalRequestHash())),field("executionRequestHash",string(r.executionRequestHash())),field("payloadSha256",string(r.payloadSha256()))));
  if(r.state().equals("SUCCEEDED"))entries.addAll(List.of(field("resultId",string(r.resultId())),field("outputSha256",string(r.outputSha256())),field("outputBytes",number(r.outputBytes()))));
  else if(r.state().equals("FAILED_ZERO_EFFECT"))entries.addAll(List.of(field("receiptId",string(r.receiptId())),field("basis",string("DEFINITIVE_REJECTION")),field("code",string(r.code()))));
  return CanonicalJson.encode(new CanonicalValue.ObjectValue(entries),65536,()->false);
 }
 private static void response(Socket socket,int status,byte[] body)throws IOException {
  var out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();socket.shutdownOutput();
 }
 private static void sendFailure(Socket socket,int status){try{response(socket,status,new byte[0]);}catch(IOException ignored){}}
 private static void closeSocket(Socket socket){try{socket.close();}catch(IOException ignored){}}
 private void closeQuietly(){try{close();}catch(IOException ignored){}}
 @Override public synchronized void close()throws IOException {
  if(closed)return;closed=true;listener.close();for(Socket socket:sockets)closeSocket(socket);workers.shutdownNow();timers.shutdownNow();
  try{workers.awaitTermination(5,TimeUnit.SECONDS);if(accepting!=null&&accepting!=Thread.currentThread())accepting.join(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();}store.close();Arrays.fill(authorization,(byte)0);
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("One explicit controlled configuration path required");Path config=Path.of(args[0]).toAbsolutePath().normalize();Path owner=config.getParent();if(Files.isSymbolicLink(config)||Files.size(config)>8192)throw new IllegalArgumentException("Invalid controlled configuration");
  var values=new HashMap<String,String>();for(String line:Files.readAllLines(config,StandardCharsets.UTF_8)){if(line.isEmpty())continue;int eq=line.indexOf('=');if(eq<1||values.putIfAbsent(line.substring(0,eq),line.substring(eq+1))!=null)throw new IllegalArgumentException("Invalid controlled configuration");}
  if(!values.keySet().equals(Set.of("mode","bind","port","store","secretFile","readyFile"))||!"127.0.0.1".equals(values.get("bind"))||!values.get("port").matches("0|[1-9][0-9]{0,4}"))throw new IllegalArgumentException("Invalid controlled configuration");
  Path root=contained(owner,values.get("store")),secret=contained(owner,values.get("secretFile")),ready=contained(owner,values.get("readyFile"));
  if(Files.isSymbolicLink(secret)||Files.size(secret)>128||!Files.getPosixFilePermissions(secret).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")))throw new IllegalArgumentException("Private controlled secret file required");
  String bearer=Files.readString(secret,StandardCharsets.US_ASCII);var server=new ControlledMinutesProvider(root,new InetSocketAddress("127.0.0.1",Integer.parseInt(values.get("port"))),bearer,values.get("mode"));
  Runtime.getRuntime().addShutdownHook(new Thread(server::closeQuietly,"controlled-minutes-shutdown"));server.start();Files.writeString(ready,Integer.toString(server.port()),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);System.out.println("CONTROLLED_MINUTES_READY port="+server.port()+" maxHeap="+Runtime.getRuntime().maxMemory()+" processors="+Runtime.getRuntime().availableProcessors());
 }
 private static Path contained(Path owner,String value){Path p=Path.of(value).toAbsolutePath().normalize();if(p.equals(owner)||!p.startsWith(owner))throw new IllegalArgumentException("Controlled path outside owner");return p;}
}
