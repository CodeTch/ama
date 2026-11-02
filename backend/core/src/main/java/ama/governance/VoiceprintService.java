package ama.governance;
import ama.contract.SpeechContracts.VoiceEmbedding;
import java.time.*;
import java.util.*;
public final class VoiceprintService {
 public record Entry(String id,String subjectId,String state,boolean active,Instant expiresAt,VoiceEmbedding embedding){}
 public record Candidate(String voiceprintId,String subjectId,String modelVersion,double score){}
 public List<Candidate> match(VoiceEmbedding input,List<Entry> entries,Double threshold,Instant now){if(threshold==null||!Double.isFinite(threshold)||threshold<=0||threshold>1)throw new IllegalArgumentException("Evaluated threshold required");
  return entries.stream().filter(e->e.state().equals("APPROVED")&&e.active()&&e.expiresAt().isAfter(now)&&e.embedding().modelVersion().equals(input.modelVersion())&&e.embedding().dimension()==input.dimension()).map(e->new Candidate(e.id(),e.subjectId(),input.modelVersion(),similarity(input,e.embedding()))).filter(e->e.score()>=threshold).sorted(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::voiceprintId)).limit(20).toList();}
 public static double similarity(VoiceEmbedding a,VoiceEmbedding b){if(!a.modelVersion().equals(b.modelVersion())||a.dimension()!=b.dimension())throw new IllegalArgumentException("Model version and dimension must match");
  double dot=0,aa=0,bb=0;for(int i=0;i<a.dimension();i++){double x=a.values().get(i),y=b.values().get(i);dot+=x*y;aa+=x*x;bb+=y*y;}if(aa==0||bb==0||!Double.isFinite(aa)||!Double.isFinite(bb))throw new IllegalArgumentException("Nonzero finite vector required");return Math.max(-1,Math.min(1,dot/(Math.sqrt(aa)*Math.sqrt(bb))));}
}
