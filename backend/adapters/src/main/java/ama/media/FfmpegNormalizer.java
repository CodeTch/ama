package ama.media;

import ama.contract.MediaContracts.*;
import ama.storage.SourceReadLifetime;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Bounded local decoding evidence. A prepared PCM resource conveys no registry or business authority. */
public final class FfmpegNormalizer {
 @FunctionalInterface public interface SourceReader {InputStream open(String fileId)throws IOException;}
 public record Limits(long maxSourceBytes,long maxSamples,Duration timeout){
  public Limits{if(maxSourceBytes<1||maxSourceBytes>32L*1024*1024||maxSamples<1||maxSamples>115200000L||timeout==null||timeout.isZero()||timeout.isNegative()||timeout.compareTo(Duration.ofMinutes(10))>0)throw new IllegalArgumentException("Finite media bounds required");}
 }
 public record SourceProof(String fileId,String sourceSha256,String pcmSha256,long sampleCount){}
 private record Decoded(SourceProof proof,Path pcm){}
 private static final Semaphore HEAVY=new Semaphore(2);
 private static final ScheduledThreadPoolExecutor TIMER=new ScheduledThreadPoolExecutor(2,r->{var t=new Thread(r,"ama-media-deadline");t.setDaemon(true);return t;});
 private static final ExecutorService STDERR=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"ama-media-diagnostic");t.setDaemon(true);return t;});
 static{TIMER.setRemoveOnCancelPolicy(true);}
 private final SourceReader reader;private final Limits limits;
 public FfmpegNormalizer(SourceReader reader){this(reader,32L*1024*1024,Duration.ofSeconds(30));}
 public FfmpegNormalizer(SourceReader reader,long maxInputBytes,Duration timeout){this(reader,new Limits(maxInputBytes,115200000L,timeout));}
 public FfmpegNormalizer(SourceReader reader,Limits limits){this.reader=Objects.requireNonNull(reader);this.limits=Objects.requireNonNull(limits);}
 /** Compatibility metadata view. Call prepare when downstream processing needs the actual audio. */
 public NormalizedAudio normalize(List<OrderedSource> sources){try(var prepared=prepare(sources,()->false)){return prepared.audio();}}
 private static final class Permit implements AutoCloseable{final AtomicBoolean closed=new AtomicBoolean();public void close(){if(closed.compareAndSet(false,true))HEAVY.release();}}
 public PreparedAudio prepare(List<OrderedSource> sources,BooleanSupplier cancelled){
  Objects.requireNonNull(sources);Objects.requireNonNull(cancelled);var ordered=List.copyOf(sources).stream().sorted(Comparator.comparingInt(OrderedSource::order)).toList();if(ordered.isEmpty()||ordered.size()>16)throw new IllegalArgumentException("Invalid media source count");var ids=new HashSet<String>();for(int i=0;i<ordered.size();i++)if(ordered.get(i).order()!=i||!ids.add(ordered.get(i).fileId()))throw new IllegalArgumentException("Invalid media source order");
  if(cancelled.getAsBoolean())throw new CancellationException("Media preparation cancelled");if(!HEAVY.tryAcquire())throw new IllegalStateException("Media preparation capacity unavailable");var permit=new Permit();Path directory=null;PreparedAudio result=null;boolean returned=false;
  try(var control=new Control(cancelled,limits.timeout())){
   control.check();directory=Files.createTempDirectory("ama-prepared-pcm-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));var decoded=new ArrayList<Decoded>();long samples=0;
   for(var source:ordered){control.check();long remaining=limits.maxSamples()-samples;if(remaining<1)throw new IllegalArgumentException("Media duration exceeds batch bound");var value=decode(source.fileId(),directory,decoded.size(),remaining,control);decoded.add(value);samples=Math.addExact(samples,value.proof().sampleCount());}
   control.check();result=new PreparedAudio(directory,decoded,permit,cancelled);result.track();control.check();result.requireOpen();returned=true;return result;
  }catch(IOException failure){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Media preparation cancelled");throw new IllegalArgumentException("Media preparation unavailable",failure);}
  finally{if(!returned){if(result!=null)result.close();else remove(directory);permit.close();}}
 }
 private Decoded decode(String id,Path directory,int ordinal,long maxSamples,Control control)throws IOException{
  Path original=directory.resolve("source-"+ordinal+".bin"),pcm=directory.resolve("pcm-"+ordinal+".s16le");var originalHash=digest();
  try(var source=reader.open(id);var output=Files.newOutputStream(original,StandardOpenOption.CREATE_NEW)){Files.setPosixFilePermissions(original,PosixFilePermissions.fromString("rw-------"));control.source(source);copy(source,output,limits.maxSourceBytes(),originalHash,control,"Media source exceeds byte bound");}finally{control.source(null);}
  control.check();long cpu=Math.max(1,(limits.timeout().toSeconds()+1));
  var command=List.of("/usr/bin/prlimit","--cpu="+cpu,"--as=1073741824","--nofile=64","--","/usr/bin/ffmpeg","-nostdin","-hide_banner","-loglevel","error","-threads","1","-filter_threads","1","-copyts","-start_at_zero","-protocol_whitelist","file,pipe","-format_whitelist","wav,mp3,mov,matroska,flac,ogg,aac,avi","-max_streams","32","-err_detect","explode","-i",original.toString(),"-map","0:a:0","-vn","-af","aresample=16000:async=1:first_pts=0","-ac","1","-ar","16000","-c:a","pcm_s16le","-f","s16le","pipe:1");
  var builder=new ProcessBuilder(command).directory(directory.toFile());builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");builder.environment().put("OPENBLAS_NUM_THREADS","1");builder.environment().put("OMP_NUM_THREADS","1");
  Process process=builder.start();control.process(process);Future<?> diagnostics=null;long bytes;var pcmHash=digest();
  try{
   diagnostics=STDERR.submit(()->{try(var error=process.getErrorStream()){byte[] buffer=new byte[1024];int used=0;for(int n;(n=error.read(buffer))!=-1;){used+=n;if(used>4096){control.fail(new IllegalArgumentException("Media decoder diagnostic bound exceeded"));return;}}}catch(IOException failure){if(process.isAlive())control.fail(new IllegalArgumentException("Media decoder diagnostic unavailable"));}});
   try(var input=process.getInputStream();var output=Files.newOutputStream(pcm,StandardOpenOption.CREATE_NEW)){Files.setPosixFilePermissions(pcm,PosixFilePermissions.fromString("rw-------"));bytes=copy(input,output,Math.multiplyExact(maxSamples,2),pcmHash,control,"Media output exceeds duration bound");}
   control.check();if(!process.waitFor(Math.max(1,control.remainingMillis()),TimeUnit.MILLISECONDS))throw new IllegalArgumentException("Media decoder deadline exceeded");control.check();if(process.exitValue()!=0||bytes<2||bytes%2!=0)throw new IllegalArgumentException("Media has no complete usable audio");diagnostics.get(2,TimeUnit.SECONDS);control.check();
  }catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new CancellationException("Media preparation interrupted");}
  catch(ExecutionException|TimeoutException failed){throw new IllegalArgumentException("Media decoder unavailable");}
  finally{if(process.isAlive())control.stopProcess(process);control.process(null);if(diagnostics!=null&&!diagnostics.isDone())diagnostics.cancel(true);Files.deleteIfExists(original);}
  return new Decoded(new SourceProof(id,hex(originalHash.digest()),hex(pcmHash.digest()),bytes/2),pcm);
 }
 private static long copy(InputStream source,OutputStream target,long maximum,MessageDigest hash,Control control,String error)throws IOException{byte[] buffer=new byte[8192];long used=0;while(true){control.check();int n=source.read(buffer);control.check();if(n<0)return used;if(n>maximum-used)throw new IllegalArgumentException(error);hash.update(buffer,0,n);target.write(buffer,0,n);used+=n;}}
 private static final class Control implements AutoCloseable{
  final BooleanSupplier cancelled;final long deadline;final AtomicReference<InputStream> source=new AtomicReference<>();final AtomicReference<Process> process=new AtomicReference<>();final AtomicReference<RuntimeException> failure=new AtomicReference<>();final AtomicBoolean stopped=new AtomicBoolean();final ScheduledFuture<?> timer;final SourceReadLifetime.Registration scoped;
  Control(BooleanSupplier cancelled,Duration timeout){this.cancelled=cancelled;deadline=System.nanoTime()+timeout.toNanos();scoped=SourceReadLifetime.track(this::abort);timer=TIMER.scheduleWithFixedDelay(()->{try{if(cancelled.getAsBoolean()||System.nanoTime()-deadline>=0)abort();}catch(RuntimeException unavailable){fail(unavailable);}},0,10,TimeUnit.MILLISECONDS);}
  void source(InputStream value){source.set(value);if(value!=null&&stopped.get())close(value);}
  void process(Process value){process.set(value);if(value!=null&&stopped.get())stopProcess(value);}
  void fail(RuntimeException cause){failure.compareAndSet(null,cause);abort();}
  long remainingMillis(){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));}
  void check(){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Media preparation cancelled");if(System.nanoTime()-deadline>=0)throw new IllegalArgumentException("Media preparation deadline exceeded");if(failure.get()!=null)throw failure.get();if(stopped.get())throw new CancellationException("Media source lifetime ended");}
  void stopProcess(Process value){value.descendants().forEach(child->{if(child.isAlive())child.destroyForcibly();});if(value.isAlive())value.destroyForcibly();}
  void abort(){stopped.set(true);var child=process.get();if(child!=null)stopProcess(child);var input=source.getAndSet(null);if(input!=null)close(input);}
  public void close(){timer.cancel(false);scoped.close();abort();}
  private static void close(InputStream in){try{in.close();}catch(IOException ignored){/* Deny-only cleanup; no artifact has been accepted. */}}
 }
 public static final class PreparedAudio implements AutoCloseable{
  private final Path directory;private final List<Decoded> decoded;private final List<SourceProof> sources;private final NormalizedAudio audio;private final String fingerprint;private final Permit permit;private final BooleanSupplier cancelled;private boolean closed;private final Set<Clip> streams=new HashSet<>();private SourceReadLifetime.Registration lifetime;
  private PreparedAudio(Path directory,List<Decoded> decoded,Permit permit,BooleanSupplier cancelled){this.directory=directory;this.decoded=List.copyOf(decoded);this.sources=decoded.stream().map(Decoded::proof).toList();this.permit=permit;this.cancelled=cancelled;audio=NormalizedAudio.fromOrdered(sources.stream().map(s->new SourceAudio(s.fileId(),16000,1,16,s.sampleCount())).toList());fingerprint=FfmpegNormalizer.fingerprint(sources);}
  private void track(){lifetime=SourceReadLifetime.track(this::close);}
  private synchronized void requireOpen(){if(closed)throw new IllegalStateException("Prepared audio closed");if(cancelled.getAsBoolean())throw new CancellationException("Prepared audio cancelled");}
  public NormalizedAudio audio(){requireOpen();return audio;}
  public List<SourceProof> sources(){requireOpen();return sources;}
  public String fingerprint(){requireOpen();return fingerprint;}
  public synchronized InputStream openClip(String fileId,long startSample,long sampleCount)throws IOException{
   requireOpen();var source=decoded.stream().filter(v->v.proof().fileId().equals(fileId)).findFirst().orElseThrow(()->new IllegalArgumentException("Unissued media source"));if(startSample<0||sampleCount<1||sampleCount>2097130||startSample>source.proof().sampleCount()-sampleCount)throw new IllegalArgumentException("Bounded original-source clip required");if(streams.size()>=4)throw new IllegalStateException("Prepared audio read capacity unavailable");
   var input=Files.newInputStream(source.pcm(),LinkOption.NOFOLLOW_LINKS);try{input.skipNBytes(Math.multiplyExact(startSample,2));var clip=new Clip(input,sampleCount);streams.add(clip);return clip;}catch(Exception failure){input.close();throw failure;}
  }
  private final class Clip extends InputStream{
   final InputStream pcm;final byte[] header;int position;long remaining;boolean released;
   Clip(InputStream pcm,long samples){this.pcm=pcm;header=header(samples);remaining=samples*2;}
   @Override public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
   @Override public int read(byte[] bytes,int off,int length)throws IOException{Objects.checkFromIndexSize(off,length,bytes.length);synchronized(PreparedAudio.this){if(closed||released||cancelled.getAsBoolean())throw new IOException("Prepared source closed");}if(length==0)return 0;if(position<header.length){int count=Math.min(length,header.length-position);System.arraycopy(header,position,bytes,off,count);position+=count;return count;}if(remaining==0)return -1;int count=pcm.read(bytes,off,(int)Math.min(length,remaining));if(count<0)throw new EOFException("Prepared PCM truncated");remaining-=count;return count;}
   @Override public void close()throws IOException{synchronized(PreparedAudio.this){if(released)return;released=true;streams.remove(this);}pcm.close();}
  }
  @Override public void close(){List<Clip> active;synchronized(this){if(closed)return;closed=true;active=List.copyOf(streams);}try{for(var stream:active)try{stream.close();}catch(IOException ignored){}if(lifetime!=null)lifetime.close();remove(directory);}finally{permit.close();}}
 }
 private static String fingerprint(List<SourceProof> sources){var digest=digest();try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){value(out,"ama.media-preparation.v2");out.writeInt(sources.size());for(var source:sources){value(out,source.fileId());value(out,source.sourceSha256());value(out,source.pcmSha256());out.writeLong(source.sampleCount());}}catch(IOException impossible){throw new AssertionError(impossible);}return hex(digest.digest());}
 private static void value(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
 private static byte[] header(long samples){int bytes=Math.toIntExact(samples*2);var b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+bytes).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);return b.array();}
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
 private static String hex(byte[] bytes){return HexFormat.of().formatHex(bytes);}
 private static void remove(Path root){if(root==null)return;try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}catch(IOException ignored){/* Private scratch remains inaccessible to product downloads. */}}
}
