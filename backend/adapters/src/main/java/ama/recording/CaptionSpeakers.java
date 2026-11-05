package ama.recording;

import ama.contract.SpeechContracts.RecognitionResult;
import ama.contract.SpeechContracts.SpeakerSpan;
import java.util.*;

/** Display turns in one model window. Character offsets index the original ASR text. */
final class CaptionSpeakers {
 record Turn(int offset,long startMs,String label) {}
 static List<Turn> align(RecognitionResult result,List<SpeakerSpan> spans,long partOffset,long windowStart){
  if(spans.isEmpty()||spans.size()>128||result.text().isBlank())return List.of();
  var ids=new LinkedHashMap<String,String>();
  boolean numbered=spans.stream().allMatch(span->span.speakerId().matches("0|[1-9][0-9]{0,5}"));
  for(var span:spans)ids.computeIfAbsent(span.speakerId(),key->"说话人 "+(numbered?Integer.parseInt(key)+1:ids.size()+1));
  long start=(partOffset+windowStart)/16;
  var mixed=List.of(new Turn(0,start,ids.size()==1?ids.values().iterator().next():"多位说话人"));
  if(result.tokens()==null||result.tokens().isEmpty()||result.tokens().size()>2048)return mixed;
  var turns=new ArrayList<Turn>();int cursor=0;long previous=windowStart;
  for(var token:result.tokens()){
   if(token.startSample()<previous||!result.text().startsWith(token.text(),cursor))return mixed;
   String speaker=null;
   for(var span:spans){
    if(span.startSample()>=token.endSample()||span.endSample()<=token.startSample())continue;
    if(span.startSample()>token.startSample()||span.endSample()<token.endSample()||speaker!=null&&!speaker.equals(span.speakerId()))return mixed;
    speaker=span.speakerId();
   }
   String label=speaker==null?"说话人待识别":ids.get(speaker);
   if(turns.isEmpty()||!turns.getLast().label().equals(label))turns.add(new Turn(cursor,(partOffset+token.startSample())/16,label));
   if(turns.size()>128)return mixed;
   cursor+=token.text().length();previous=token.endSample();
  }
  return cursor==result.text().length()?List.copyOf(turns):mixed;
 }
 private CaptionSpeakers(){}
}
