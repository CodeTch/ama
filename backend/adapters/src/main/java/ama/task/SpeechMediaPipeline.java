package ama.task;

import ama.contract.MediaContracts.OrderedSource;
import ama.contract.SpeechContracts.*;
import ama.contract.TaskContracts.TaskLease;
import ama.media.FfmpegNormalizer;
import ama.model.HttpSpeechModelPort;
import ama.transcript.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.transcript.TranscriptDocumentParser.ErrorCode.*;
import ama.transcript.TranscriptDocumentParser.*;
import ama.transcript.MeasuredTranscriptDocumentParser.*;

/** Only ImportPolicy constructs this collaborator; no public model-result writer is installed. */
final class SpeechMediaPipeline implements AutoCloseable {
 private final JdbcTaskQueue q;private final JdbcTranscriptAuthority.Components components;private final TaskLease lease;
 private final String parserVersion;private final boolean recording;private final ControlledSpeechProfile profile;private final BooleanSupplier cancelled;
 private final JdbcSpeechAudio audio;private final ControlledSpeechProofClient proofs;private final JdbcSpeechExecutionObserver observer;
 SpeechMediaPipeline(JdbcTaskQueue q,JdbcTranscriptAuthority.Components components,TaskLease lease,String engine,String parserVersion,BooleanSupplier cancelled){
  TranscriptImportRuntime.requireCombined(q,components.authority());profile=ControlledSpeechProfile.configured(engine);this.parserVersion=parserVersion;recording=ama.transcript.RecordingTranscriptProfile.PARSER.equals(parserVersion);if(!recording&&!ControlledSpeechProfile.PARSER.equals(parserVersion))throw new IllegalArgumentException("Unsupported speech parser");
  this.q=q;this.components=components;this.lease=lease;this.cancelled=cancelled;audio=new JdbcSpeechAudio(q,components,lease,engine,cancelled);proofs=new ControlledSpeechProofClient(profile);
  observer=new JdbcSpeechExecutionObserver(q,components.authority(),lease,ControlledSpeechProfile.key("ama.speech-scope.v1",lease.spec().input().id(),lease.spec().inputFingerprint(),engine),profile,proofs,cancelled);
  try{observer.configure();observer.recover(16);}catch(RuntimeException|Error failure){proofs.close();throw failure;}
 }
 MeasuredResult parse(InputStream source,UUID file,String hash,ParseBudget budget,BooleanSupplier cancelled,String name,long remainingSamples)throws ParseException,IOException {
  if(name==null||name.length()>1048576||budget.maxSourceBytes()<1||budget.maxUtf16CodeUnits()<1||budget.maxSegments()<1)throw new ParseException(INVALID_ARGUMENT);
  String format=name.substring(name.lastIndexOf('.')+1).toUpperCase(Locale.ROOT);if(!MediaTranscriptParser.FORMATS.contains(format)||recording&&!format.equals("WAV"))throw new ParseException(INVALID_ARGUMENT);
  if(Set.of("TXT","DOC","DOCX","PDF","PPTX","SRT","VTT").contains(format)){
   var parser=new MediaTranscriptParser(MediaTranscriptParser.configured(profile.documentSha256()));var value=parser.parseMeasured(source,file,hash,budget,cancelled,name);
   var segments=value.result().segments().stream().map(s->new Segment(stable(file,hash,s.paragraphNo()),file,s.paragraphNo(),s.text(),null,s.startMs(),s.endMs())).toList();
   return new MeasuredResult(new Result(segments,hash,parserVersion),value.usage());
  }
  if(remainingSamples<1||remainingSamples>115200000)throw new ParseException(AUDIO_DURATION_EXCEEDED);
  if(recording){if(!format.equals("WAV"))throw new ParseException(INVALID_ARGUMENT);q.preparation(()->{var valid=q.validatedPreparationLease(lease).orElseThrow(()->new java.util.concurrent.CancellationException("Recording input lease changed"));components.authority().requireImportPreparation(List.of(lease.spec()),valid.domain().prepared());return null;});}
  var counted=new Counted(source,budget.maxSourceBytes(),cancelled);var retained=audio.retained(file.toString(),hash);JdbcSpeechAudio.Source material;
  if(retained.isPresent()){
   verify(counted,hash);material=retained.get();if(material.proof().sampleCount()>remainingSamples)throw new ParseException(AUDIO_DURATION_EXCEEDED);
  }else{
   try(var prepared=new FfmpegNormalizer(id->{if(!id.equals(file.toString()))throw new IOException("Unissued source");return counted;},recording?FfmpegNormalizer.Limits.recording(remainingSamples,Duration.ofMinutes(2)):new FfmpegNormalizer.Limits(Math.min(33554432,budget.maxSourceBytes()),remainingSamples,Duration.ofMinutes(2))).prepare(List.of(new OrderedSource(file.toString(),0)),cancelled)){
    var proof=prepared.sources().getFirst();if(!proof.sourceSha256().equals(hash))throw new ParseException(SOURCE_HASH_MISMATCH);
    var clips=new ArrayList<WavClip>();for(long start=0;start<proof.sampleCount();start+=profile.windowSamples()){
     var clip=new WavClip(file.toString(),start,Math.min(profile.windowSamples(),proof.sampleCount()-start));try(var wav=prepared.openClip(clip.fileId(),clip.startSample(),clip.sampleCount())){audio.retain(proof,clip,wav);}clips.add(clip);
    }
    material=new JdbcSpeechAudio.Source(proof,clips);
   }catch(IllegalArgumentException invalid){throw new ParseException(INVALID_MEDIA);}
  }
  var segments=new ArrayList<Segment>();long units=0,ordinal=1;final var bound=material;
  try(var model=new HttpSpeechModelPort(profile.http(),clip->{if(!bound.clips().contains(clip))throw new IOException("Unissued clip");return audio.open(bound.proof(),clip);},observer)){
   for(var clip:material.clips()){
    check();try(var verified=audio.open(material.proof(),clip)){verified.transferTo(OutputStream.nullOutputStream());}
    var runtime=new ama.governance.JdbcGovernanceRuntime(q.jdbc.getDataSource());
    var store=new JdbcSpeechCheckpoints(q,components.authority(),lease,material.proof(),clip,profile.sha256(),cancelled);var value=new SpeechPipeline(model).transcribe(clip,store,()->runtime.entities(lease.spec().input().id()));
    if(runtime.required(lease.spec().input().id()).configuration().modelVersion().equals(profile.http().embeddingModelVersion())){
     byte[] original;try(var stream=audio.open(material.proof(),clip)){original=stream.readNBytes(Math.toIntExact(44+clip.sampleCount()*2));}
     ama.governance.GovernanceRuntimeVoice.candidates(runtime,lease.spec().input().id(),clip,value.speakers(),original,sample->{check();try(var speakerModel=new HttpSpeechModelPort(profile.http(),ignored->new ByteArrayInputStream(sample),observer)){return speakerModel.embed(new WavClip(clip.fileId(),0,(sample.length-44)/2));}});
    }
    // RAW uses only the actual recognition. Enhanced text, vectors and anonymous
    // model clusters stay in typed checkpoints until a separate explicit adoption.
    if(!value.raw().text().isBlank())units=add(segments,file,hash,ordinal,value.raw().text(),clip.startSample(),clip.endSampleExclusive(),units,budget);
    ordinal++;
   }
  }
  observer.recover(4);
  check();if(segments.isEmpty())throw new ParseException(NO_TEXT);
  return new MeasuredResult(new Result(segments,hash,parserVersion),new Usage(counted.count,units,segments.size(),material.proof().sampleCount()));
 }
 private long add(List<Segment> segments,UUID file,String hash,long ordinal,String text,long start,long end,long units,ParseBudget budget)throws ParseException {
  if(segments.size()>=budget.maxSegments())throw new ParseException(SEGMENTS_EXCEEDED);if(text.length()+1L>budget.maxUtf16CodeUnits()-units)throw new ParseException(UTF16_CODE_UNITS_EXCEEDED);
  segments.add(new Segment(stable(file,hash,ordinal),file,ordinal,text,null,start/16,(end+15)/16));return units+text.length()+1L;
 }
 private UUID stable(UUID file,String sha,long ordinal){byte[] bytes=HexFormat.of().parseHex(ControlledSpeechProfile.key(parserVersion,file.toString(),sha,Long.toString(ordinal)));bytes[6]=(byte)((bytes[6]&15)|0x80);bytes[8]=(byte)((bytes[8]&63)|0x80);var b=ByteBuffer.wrap(bytes);return new UUID(b.getLong(),b.getLong());}
 private void check(){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Speech preparation cancelled");}
 private static void verify(InputStream input,String expected)throws IOException,ParseException {
  java.security.MessageDigest digest;try{digest=java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}byte[] bytes=new byte[8192];for(int n;(n=input.read(bytes))!=-1;)digest.update(bytes,0,n);
  if(!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new ParseException(SOURCE_HASH_MISMATCH);
 }
 private static final class Counted extends FilterInputStream {
  long count;final long maximum;final BooleanSupplier cancelled;
  Counted(InputStream input,long maximum,BooleanSupplier cancelled){super(input);this.maximum=maximum;this.cancelled=cancelled;}
  @Override public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
  @Override public int read(byte[] b,int off,int len)throws IOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Speech source cancelled");int n=in.read(b,off,(int)Math.min(len,maximum-count+1));if(n>maximum-count)throw new IOException("Speech source exceeds bound");if(n>0)count+=n;return n;}
  @Override public void close(){/* SourceReadLifetime and the registry own the original stream. */}
 }
 @Override public void close(){proofs.close();}
}
