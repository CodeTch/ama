package ama.model;

import ama.task.PreparationCancellation;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fixed loopback HTTP/1.1 transport. Cancellation and both deadlines close the real socket,
 * including a blocked connect/write/header/body read. No proxy, redirects, chunking or compression. */
public final class ControlledMinutesHttpTransport implements AutoCloseable {
 private final int port,connectMillis,idleMillis;private final long overallMillis;private final String bearer;
 private final Set<Context> active=ConcurrentHashMap.newKeySet();private final Semaphore capacity=new Semaphore(2);
 private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"minutes-transport-deadline");t.setDaemon(true);return t;});private volatile boolean closed;
 public ControlledMinutesHttpTransport(URI origin,String bearer,String mode,Duration connect,Duration overall,Duration idle){
  if(!"CONTROLLED_INSTALLATION".equals(mode)||origin==null||!"http".equals(origin.getScheme())||!"127.0.0.1".equals(origin.getHost())||origin.getPort()<1||origin.getPort()>65535||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!Set.of("","/").contains(origin.getPath())||bearer==null||!bearer.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalArgumentException("Explicit fixed controlled loopback origin required");
  this.port=origin.getPort();this.bearer=bearer;connectMillis=(int)bound(connect,2000);idleMillis=(int)bound(idle,5000);overallMillis=bound(overall,30000);
 }
 private static long bound(Duration duration,long max){if(duration==null||duration.isNegative()||duration.isZero()||duration.toMillis()<1||duration.toMillis()>max)throw new IllegalArgumentException("Controlled transport bounds unavailable");return duration.toMillis();}
 /** Fully bounded inert request bytes, suitable for preparation before marker/admission. */
 public static final class Outbound {
  private final ControlledMinutesHttpTransport issuer;private final byte[] headers,body;
  private Outbound(ControlledMinutesHttpTransport issuer,byte[] headers,byte[] body){this.issuer=issuer;this.headers=headers;this.body=body;}
 }
 public Outbound prepare(String method,String path,Map<String,String> extra,byte[] body)throws IOException {
  if(closed)throw new IOException("Controlled transport closed");if(!Set.of("GET","POST").contains(method)||path==null||path.length()>512||!path.matches("/v1/minutes/[A-Za-z0-9/_-]+")||body==null||body.length>ControlledMinutesProtocol.MAX_REQUEST||method.equals("GET")&&body.length!=0)throw new IllegalArgumentException("Controlled request shape unavailable");
  var headers=new LinkedHashMap<String,String>();headers.put("Host","127.0.0.1:"+port);headers.put("Authorization","Bearer "+bearer);headers.put("Connection","close");headers.put("Content-Length",Integer.toString(body.length));if(method.equals("POST"))headers.put("Content-Type","application/json");
  var names=new HashSet<String>();for(String name:headers.keySet())names.add(name.toLowerCase(Locale.ROOT));
  for(var entry:Objects.requireNonNull(extra).entrySet()){
   String name=entry.getKey(),value=entry.getValue();if(name==null||name.length()>ControlledMinutesProtocol.MAX_HEADER_NAME||!name.matches("[A-Za-z0-9-]+")||value==null||value.isEmpty()||value.length()>ControlledMinutesProtocol.MAX_ENVELOPE_HEADER||value.chars().anyMatch(c->c<32||c>126)||!names.add(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Controlled header unavailable");headers.put(name,value);
  }
  if(headers.size()>ControlledMinutesProtocol.MAX_HEADER_COUNT)throw new IOException("Controlled header count exceeded");
  var wire=new StringBuilder(method+" "+path+" HTTP/1.1\r\n");for(var entry:headers.entrySet()){if(entry.getValue().length()>ControlledMinutesProtocol.MAX_ENVELOPE_HEADER)throw new IOException("Controlled header field exceeded");wire.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");if(wire.length()>ControlledMinutesProtocol.MAX_HEADERS-2)throw new IOException("Controlled aggregate headers exceeded");}wire.append("\r\n");
  return new Outbound(this,wire.toString().getBytes(StandardCharsets.US_ASCII),body.clone());
 }
 public Exchange exchange(String method,String path,Map<String,String> extra,byte[] body,PreparationCancellation cancel,int maximum)throws IOException {return exchange(prepare(method,path,extra,body),cancel,maximum);}
 public Exchange exchange(Outbound request,PreparationCancellation cancel,int maximum)throws IOException {
  if(request==null||request.issuer!=this||cancel==null||maximum<1||maximum>ControlledMinutesStore.MAX_OUTPUT)throw new IllegalArgumentException("Controlled response budget unavailable");
  if(closed||cancel.cancelled()||!capacity.tryAcquire())throw new IOException("Controlled transport unavailable");
  var context=new Context();active.add(context);
  try{
   context.registration(cancel.register(context::close));context.timer(deadlines.schedule(context::close,overallMillis,TimeUnit.MILLISECONDS));
   if(closed||cancel.cancelled()||context.done.get())throw new IOException("Controlled transport cancelled");
   context.socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),port),connectMillis);context.socket.setSoTimeout(idleMillis);
   var output=context.socket.getOutputStream();output.write(request.headers);output.write(request.body);output.flush();
   var input=context.socket.getInputStream();var counter=new Counter();String first=line(input,counter,2048);if(!first.matches("HTTP/1[.]1 [1-5][0-9][0-9] [ -~]*"))throw new IOException("Invalid controlled status line");int status=Integer.parseInt(first.substring(9,12));
   var headers=new LinkedHashMap<String,String>();while(true){String line=line(input,counter,8322);if(line.isEmpty())break;int colon=line.indexOf(':');if(colon<1||colon>128||!line.substring(0,colon).matches("[A-Za-z0-9-]+"))throw new IOException("Invalid controlled response header");String name=line.substring(0,colon).toLowerCase(Locale.ROOT),value=line.substring(colon+1);if(value.startsWith(" "))value=value.substring(1);if(value.length()>8192||headers.size()>=32||headers.putIfAbsent(name,value)!=null)throw new IOException("Invalid controlled response headers");}
   if(status>=300&&status<400||headers.containsKey("content-encoding")||headers.containsKey("transfer-encoding")||!"application/json".equals(headers.get("content-type")))throw new IOException("Unsupported controlled response");
   String size=headers.get("content-length");if(size==null||!size.matches("0|[1-9][0-9]{0,8}"))throw new IOException("Controlled response length required");long length=Long.parseLong(size);if(length>maximum)throw new IOException("Controlled response exceeds admitted budget");
   return new Exchange(context,status,Collections.unmodifiableMap(headers),input,length,cancel);
  }catch(IOException|RuntimeException failure){context.close();throw failure;}
 }
 public final class Exchange implements AutoCloseable {
  private final Context context;private final int status;private final Map<String,String> headers;private final InputStream body;
  private Exchange(Context context,int status,Map<String,String> headers,InputStream input,long length,PreparationCancellation cancel){this.context=context;this.status=status;this.headers=headers;
   body=new InputStream(){long remaining=length;boolean eof;
    @Override public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:Byte.toUnsignedInt(one[0]);}
    @Override public int read(byte[] bytes,int off,int len)throws IOException {
     Objects.checkFromIndexSize(off,len,bytes.length);if(len==0)return 0;if(eof)return -1;
     if(cancel.cancelled()||context.done.get()){context.close();throw new IOException("Controlled exchange cancelled");}
     try{if(remaining==0){if(input.read()!=-1)throw new IOException("Controlled response trailing bytes");eof=true;context.close();return -1;}
      int n=input.read(bytes,off,(int)Math.min((long)len,remaining));if(n<0)throw new EOFException("Incomplete controlled response");remaining-=n;return n;
     }catch(IOException|RuntimeException failure){context.close();throw failure;}
    }
    @Override public void close(){context.close();}
   };
  }
  public int status(){return status;}public Map<String,String> headers(){return headers;}public InputStream body(){return body;}@Override public void close(){context.close();}
 }
 private final class Context {
  final Socket socket=new Socket();final AtomicBoolean done=new AtomicBoolean();AutoCloseable registration;Future<?> timer;
  synchronized void registration(AutoCloseable value){if(done.get())quiet(value);else registration=value;}
  synchronized void timer(Future<?> value){if(done.get())value.cancel(false);else timer=value;}
  void close(){if(!done.compareAndSet(false,true))return;try{socket.close();}catch(IOException ignored){}AutoCloseable registered;Future<?> scheduled;synchronized(this){registered=registration;registration=null;scheduled=timer;timer=null;}if(scheduled!=null)scheduled.cancel(false);quiet(registered);active.remove(this);capacity.release();}
 }
 private static void quiet(AutoCloseable value){if(value!=null)try{value.close();}catch(Exception ignored){}}
 private static final class Counter{int bytes;}
 private static String line(InputStream in,Counter counter,int maximum)throws IOException {
  var buffer=new ByteArrayOutputStream(Math.min(maximum,256));boolean cr=false;while(true){int b=in.read();if(b<0)throw new EOFException("Incomplete controlled response headers");if(++counter.bytes>16384)throw new IOException("Controlled response headers exceeded");if(cr){if(b!=10)throw new IOException("Invalid controlled header framing");return buffer.toString(StandardCharsets.US_ASCII);}if(b==13){cr=true;continue;}if(b<32||b>126||buffer.size()>=maximum)throw new IOException("Invalid controlled header framing");buffer.write(b);}
 }
 public int activeCount(){return active.size();}
 @Override public void close(){if(closed)return;closed=true;for(var context:active)context.close();deadlines.shutdownNow();}
}
