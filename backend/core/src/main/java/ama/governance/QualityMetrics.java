package ama.governance;

import java.util.*;

/** Reproducible metrics. Speaker times use samples, with zero collar and overlap included. */
public final class QualityMetrics {
 private QualityMetrics(){}
 public static Map<String,Double> characters(String reference,String actual){
  int[] a=reference.codePoints().toArray(),b=actual.codePoints().toArray();
  if(a.length==0||a.length>8192||b.length>32768)throw new IllegalArgumentException("Bounded nonempty reference required");
  int[][] prev=new int[b.length+1][4];for(int k=0;k<=b.length;k++){prev[k][0]=k;prev[k][2]=k;}
  for(int i=1;i<=a.length;i++){
   int[][] next=new int[b.length+1][4];next[0]=new int[]{i,0,0,i};
   for(int k=1;k<=b.length;k++){
    int edit=a[i-1]==b[k-1]?0:1;int[] best=prev[k-1].clone();best[0]+=edit;best[1]+=edit;
    if(prev[k][0]+1<best[0]){best=prev[k].clone();best[0]++;best[3]++;}
    if(next[k-1][0]+1<best[0]){best=next[k-1].clone();best[0]++;best[2]++;}next[k]=best;
   }prev=next;
  }
  int[] r=prev[b.length];return Map.of("referenceCharacters",(double)a.length,"substitutions",(double)r[1],"insertions",(double)r[2],"deletions",(double)r[3],"cer",(double)r[0]/a.length);
 }
 public record Turn(long start,long end,String speaker){public Turn{if(start<0||end<=start||speaker==null||speaker.isBlank()||speaker.length()>128)throw new IllegalArgumentException("Annotated speaker timeline required");}}
 public static Map<String,Double> diarization(List<Turn> reference,List<Turn> actual){
  if(reference.isEmpty()||reference.size()>512||actual.size()>512)throw new IllegalArgumentException("Bounded reference timeline required");
  var names=reference.stream().map(Turn::speaker).distinct().sorted().toList();var hypotheses=actual.stream().map(Turn::speaker).distinct().sorted().toList();
  int n=Math.max(names.size(),hypotheses.size());if(n>16)throw new IllegalArgumentException("At most 16 annotated speakers");
  var edges=new TreeSet<Long>();for(var t:reference){edges.add(t.start);edges.add(t.end);}for(var t:actual){edges.add(t.start);edges.add(t.end);}
  var times=new ArrayList<>(edges);double[][] overlaps=new double[n][n];double total=0,miss=0,falseAlarm=0,common=0;
  for(int i=1;i<times.size();i++){
   long start=times.get(i-1),duration=times.get(i)-start;
   var refs=reference.stream().filter(t->t.start<=start&&t.end>start).map(Turn::speaker).collect(java.util.stream.Collectors.toSet());
   var hyps=actual.stream().filter(t->t.start<=start&&t.end>start).map(Turn::speaker).collect(java.util.stream.Collectors.toSet());
   total+=duration*refs.size();miss+=duration*Math.max(0,refs.size()-hyps.size());falseAlarm+=duration*Math.max(0,hyps.size()-refs.size());common+=duration*Math.min(refs.size(),hyps.size());
   for(String ref:refs)for(String hyp:hyps)overlaps[names.indexOf(ref)][hypotheses.indexOf(hyp)]+=duration;
  }
  // Maximum weight one-to-one speaker mapping, independent of arbitrary cluster labels.
  double[] dp=new double[1<<n];Arrays.fill(dp,Double.NEGATIVE_INFINITY);dp[0]=0;
  for(int mask=0;mask<dp.length;mask++){int row=Integer.bitCount(mask);if(row==n)continue;for(int col=0;col<n;col++)if((mask&(1<<col))==0)dp[mask|(1<<col)]=Math.max(dp[mask|(1<<col)],dp[mask]+overlaps[row][col]);}
  double confused=Math.max(0,common-dp[dp.length-1]);return Map.of("referenceSpeakerSamples",total,"missed",miss,"falseAlarm",falseAlarm,"confused",confused,"der",(miss+falseAlarm+confused)/total);
 }
 public static Map<String,Double> entities(Set<String> reference,Set<String> actual){
  if(reference.isEmpty())throw new IllegalArgumentException("Annotated entities required");long tp=reference.stream().filter(actual::contains).count();double p=actual.isEmpty()?0:(double)tp/actual.size(),r=(double)tp/reference.size();
  return Map.of("truePositive",(double)tp,"falsePositive",(double)(actual.size()-tp),"falseNegative",(double)(reference.size()-tp),"precision",p,"recall",r,"f1",p+r==0?0:2*p*r/(p+r));
 }
}
