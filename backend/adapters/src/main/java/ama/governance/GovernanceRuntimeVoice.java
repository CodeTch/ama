package ama.governance;
import ama.contract.SpeechContracts.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Ephemeral, single-cluster clips. Never compare a whole multi-speaker recording. */
public final class GovernanceRuntimeVoice {
 private static volatile EncryptedEmbeddingStore crypto;
 public static void configure(Path key)throws java.io.IOException{if(Files.isSymbolicLink(key)||!Files.isRegularFile(key)||Files.size(key)!=32)throw new IllegalArgumentException("Controlled voice key required");crypto=new EncryptedEmbeddingStore(Files.readAllBytes(key));}
 public static void candidates(JdbcGovernanceRuntime runtime,String input,WavClip clip,List<SpeakerSpan> spans,byte[] wav,Function<byte[],VoiceEmbedding> infer){
  var store=crypto;if(store==null||runtime.required(input).configuration().voiceThreshold()==null)return;
  for(String speaker:spans.stream().map(SpeakerSpan::speakerId).distinct().sorted().limit(16).toList()){
   var selected=spans.stream().filter(s->s.speakerId().equals(speaker)).sorted(Comparator.comparingLong(SpeakerSpan::startSample)).toList();
   var isolated=selected.stream().filter(s->spans.stream().noneMatch(other->!other.speakerId().equals(speaker)&&other.startSample()<s.endSample()&&other.endSample()>s.startSample())).toList();
   byte[] sample=extract(clip,isolated,wav);if(sample==null)continue;VoiceEmbedding embedding=infer.apply(sample);var candidates=runtime.match(input,embedding,store);
   runtime.saveCandidates(input,clip.fileId(),clip.startSample(),speaker,candidates);
  }
 }
 static byte[] extract(WavClip clip,List<SpeakerSpan> spans,byte[] wav){
  if(wav.length!=44+clip.sampleCount()*2)throw new IllegalArgumentException("Original clip required");var pcm=new java.io.ByteArrayOutputStream();long previous=clip.startSample();
  for(var span:spans){long start=Math.max(previous,span.startSample()),end=Math.min(clip.endSampleExclusive(),span.endSample());if(start>=end)continue;long count=Math.min(end-start,480000-pcm.size()/2);if(count<=0)break;pcm.write(wav,Math.toIntExact(44+(start-clip.startSample())*2),Math.toIntExact(count*2));previous=end;}
  if(pcm.size()<96000)return null;byte[] data=pcm.toByteArray();var out=ByteBuffer.allocate(44+data.length).order(ByteOrder.LITTLE_ENDIAN);out.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(data.length+36).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(data.length).put(data);try{GovernanceAudio.require(out.array());return out.array();}catch(IllegalArgumentException quiet){return null;}
 }
 private GovernanceRuntimeVoice(){}
}
