package ama.transcript;

/** Unicode scalar offsets for the smallest contiguous edit in a verified suggestion. */
public record TranscriptCorrectionRange(int startScalar,int endScalar,String original,String replacement) {
 public static TranscriptCorrectionRange between(String source,String proposed){
  int[] before=source.codePoints().toArray(),after=proposed.codePoints().toArray();int start=0,end=before.length,nextEnd=after.length;
  while(start<end&&start<nextEnd&&before[start]==after[start])start++;
  while(end>start&&nextEnd>start&&before[end-1]==after[nextEnd-1]){end--;nextEnd--;}
  return new TranscriptCorrectionRange(start,end,new String(before,start,end-start),new String(after,start,nextEnd-start));
 }
}
