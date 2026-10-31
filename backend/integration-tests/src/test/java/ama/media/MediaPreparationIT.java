package ama.media;

import ama.contract.MediaContracts.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual local FFmpeg and private PCM lifetime; synthetic input is not speech-model qualification. */
class MediaPreparationIT {
 @TempDir Path root;
 static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static byte[] wav(int rate,int channels,int samples,int amplitude){int length=samples*channels*2;var b=ByteBuffer.allocate(44+length).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(length+36).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(length);for(int n=0;n<samples*channels;n++)b.putShort((short)amplitude);return b.array();}
 @Test void orderedClipsKeepActualPcmAndStableOriginalSourceMapping()throws Exception {
  var inputs=Map.of("first",wav(48000,2,4800,4000),"second",wav(8000,1,800,8000));var normalizer=new FfmpegNormalizer(id->new ByteArrayInputStream(inputs.get(id)));
  try(var first=normalizer.prepare(List.of(new OrderedSource("first",0),new OrderedSource("second",1)),()->false);var swapped=normalizer.prepare(List.of(new OrderedSource("second",0),new OrderedSource("first",1)),()->false)){
   assertEquals(16000,first.audio().sampleRate());assertEquals(1,first.audio().channels());assertEquals(16,first.audio().bitsPerSample());assertEquals(3200,first.audio().sampleCount());assertEquals(List.of("first","second"),first.audio().sourceMap().stream().map(SourceMap::fileId).toList());assertEquals(1600,first.audio().sourceMap().get(1).normalizedStartSample());
   byte[] original,reordered;try(var in=first.openClip("first",0,1600)){original=in.readAllBytes();}try(var in=swapped.openClip("first",0,1600)){reordered=in.readAllBytes();}assertArrayEquals(original,reordered);assertEquals(3244,original.length);assertEquals(16000,ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).getInt(24));assertEquals(hash(inputs.get("first")),first.sources().getFirst().sourceSha256());assertEquals(hash(Arrays.copyOfRange(original,44,original.length)),first.sources().getFirst().pcmSha256());assertNotEquals(first.fingerprint(),swapped.fingerprint());
   assertThrows(IllegalArgumentException.class,()->first.openClip("unissued",0,1));assertThrows(IllegalArgumentException.class,()->first.openClip("first",1599,2));
  }
 }
 @Test void resourceCloseRevokesOpenStreamsAndReleasesOnlyItsOwnHeavySlot()throws Exception {
  var count=new AtomicInteger();var normalizer=new FfmpegNormalizer(id->{count.incrementAndGet();return new ByteArrayInputStream(wav(16000,1,1600,3000));});var source=List.of(new OrderedSource("source",0));
  try(var first=normalizer.prepare(source,()->false);var second=normalizer.prepare(source,()->false)){
   var in=first.openClip("source",0,1600);assertThrows(IllegalStateException.class,()->normalizer.prepare(source,()->false));assertEquals(2,count.get());first.close();first.close();assertThrows(IOException.class,in::read);assertThrows(IllegalStateException.class,()->first.openClip("source",0,1));
   try(var next=normalizer.prepare(source,()->false)){assertEquals(1600,next.audio().sampleCount());assertThrows(IllegalStateException.class,()->normalizer.prepare(source,()->false));}
  }
 }
 @Test void sourceAndOutputBudgetsCannotReturnAPartialSuccessfulPreparation(){
  byte[] input=wav(16000,1,3200,1000);var source=List.of(new OrderedSource("source",0));
  var smallSource=new FfmpegNormalizer(id->new ByteArrayInputStream(input),new FfmpegNormalizer.Limits(32,1600,Duration.ofSeconds(5)));
  assertThrows(IllegalArgumentException.class,()->smallSource.prepare(source,()->false));
  var smallOutput=new FfmpegNormalizer(id->new ByteArrayInputStream(input),new FfmpegNormalizer.Limits(32768,1600,Duration.ofSeconds(5)));
  assertThrows(IllegalArgumentException.class,()->smallOutput.prepare(source,()->false));
  var expired=new FfmpegNormalizer(id->new ByteArrayInputStream(input),new FfmpegNormalizer.Limits(32768,16000,Duration.ofNanos(1)));
  assertThrows(IllegalArgumentException.class,()->expired.prepare(source,()->false));
 }
 @Test void cancellationAbortsAnAlreadyBlockedOriginalReadAndDoesNotLeakTheHeavySlot()throws Exception {
  var started=new CountDownLatch(1);var closed=new CountDownLatch(1);var cancelled=new AtomicBoolean();var normalizer=new FfmpegNormalizer(id->new InputStream(){public int read()throws IOException{return read(new byte[1],0,1);}public int read(byte[] bytes,int off,int length)throws IOException{started.countDown();try{if(!closed.await(10,TimeUnit.SECONDS))throw new IOException("Test source did not abort");}catch(InterruptedException stopped){Thread.currentThread().interrupt();}throw new IOException("Closed test source");}public void close(){closed.countDown();}});
  try(var executor=Executors.newSingleThreadExecutor()){
   var pending=executor.submit(()->normalizer.prepare(List.of(new OrderedSource("source",0)),cancelled::get));assertTrue(started.await(3,TimeUnit.SECONDS));cancelled.set(true);var failure=assertThrows(ExecutionException.class,()->pending.get(3,TimeUnit.SECONDS));assertInstanceOf(CancellationException.class,failure.getCause());assertEquals(0,closed.getCount());
  }finally{cancelled.set(true);closed.countDown();}
  var valid=new FfmpegNormalizer(id->new ByteArrayInputStream(wav(16000,1,16,1000)));try(var first=valid.prepare(List.of(new OrderedSource("a",0)),()->false);var second=valid.prepare(List.of(new OrderedSource("b",0)),()->false)){assertEquals(16,first.audio().sampleCount());assertEquals(16,second.audio().sampleCount());}
 }
 @Test void externalReferencePlaylistsAreRejectedInsteadOfReadingUnissuedAudio()throws Exception {
  Path outside=root.resolve("unissued.wav");Files.write(outside,wav(16000,1,1600,9000));byte[] playlist=("ffconcat version 1.0\nfile '"+outside.toAbsolutePath()+"'\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);var normalizer=new FfmpegNormalizer(id->new ByteArrayInputStream(playlist));assertThrows(IllegalArgumentException.class,()->normalizer.prepare(List.of(new OrderedSource("playlist",0)),()->false));
 }
 @Test void videoTrackOffsetPreservesTheOriginalPresentationTimeline()throws Exception {
  Path movie=root.resolve("中文 原录音 😀.mkv");
  fixture("-f","lavfi","-i","color=size=16x16:rate=10:duration=1","-itsoffset","0.2","-f","lavfi","-i","sine=frequency=440:sample_rate=48000:duration=0.4","-map","0:v","-map","1:a","-c:v","ffv1","-c:a","pcm_s16le",movie.toString());
  var normalizer=new FfmpegNormalizer(id->Files.newInputStream(movie));
  try(var audio=normalizer.prepare(List.of(new OrderedSource("中文 原录音 😀.mkv",0)),()->false);var clip=audio.openClip("中文 原录音 😀.mkv",0,audio.audio().sampleCount())){
   assertEquals(9600,audio.audio().sampleCount());byte[] bytes=clip.readAllBytes();
   assertTrue(java.util.stream.IntStream.range(44,44+6000).allMatch(i->bytes[i]==0),"leading video presentation time must be retained as silence");
   assertTrue(java.util.stream.IntStream.range(44+7000,bytes.length).anyMatch(i->bytes[i]!=0));
  }
 }
 @Test void damagedMediaAndVideoWithoutAudioNeverProduceAUsableResource()throws Exception {
  Path movie=root.resolve("无音轨.mkv");fixture("-f","lavfi","-i","color=size=16x16:rate=10:duration=0.1","-an","-c:v","ffv1",movie.toString());
  assertThrows(IllegalArgumentException.class,()->new FfmpegNormalizer(id->Files.newInputStream(movie)).prepare(List.of(new OrderedSource("silent",0)),()->false));
  assertThrows(IllegalArgumentException.class,()->new FfmpegNormalizer(id->new ByteArrayInputStream(new byte[]{1,2,3,4})).prepare(List.of(new OrderedSource("broken",0)),()->false));
 }
 @Test void preparationResourceBelongsToTheExistingWorkersSingleCancellationScope()throws Exception {
  var callback=new AtomicReference<Runnable>();var signal=new ama.task.PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable action){assertTrue(callback.compareAndSet(null,action));return ()->assertTrue(callback.compareAndSet(action,null));}};
  var normalizer=new FfmpegNormalizer(id->new ByteArrayInputStream(wav(16000,1,16,3000)));
  try(var scope=new ama.storage.SourceReadLifetime(signal,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));var prepared=normalizer.prepare(List.of(new OrderedSource("source",0)),()->false);var in=prepared.openClip("source",0,16)){
   assertNotNull(callback.get());callback.get().run();assertThrows(IOException.class,in::read);assertThrows(IllegalStateException.class,prepared::audio);
  }
  assertNull(callback.get());
  try(var first=normalizer.prepare(List.of(new OrderedSource("a",0)),()->false);var second=normalizer.prepare(List.of(new OrderedSource("b",0)),()->false)){assertEquals(16,second.audio().sampleCount());}
 }
 private static void fixture(String... arguments)throws Exception {
  var command=new ArrayList<>(List.of("/usr/bin/ffmpeg","-nostdin","-hide_banner","-loglevel","error","-threads","1","-filter_threads","1","-y"));command.addAll(List.of(arguments));
  var builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().put("OPENBLAS_NUM_THREADS","1");builder.environment().put("OMP_NUM_THREADS","1");var process=builder.start();try{assertTrue(process.waitFor(10,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),new String(process.getInputStream().readNBytes(4096)));}finally{process.destroyForcibly();}
 }
}
