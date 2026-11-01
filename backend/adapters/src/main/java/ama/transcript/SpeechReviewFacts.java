package ama.transcript;

import java.nio.ByteBuffer;
import java.util.*;

/** Detached, bounded S/F comparison data. It carries no authority to write. */
record SpeechReviewFacts(List<Map<String,Object>> candidates,List<Map<String,Object>> audio,List<Stage> stages,boolean modelsCaptured,List<Map<String,Object>> otherPending) {
 SpeechReviewFacts{candidates=List.copyOf(candidates);audio=List.copyOf(audio);stages=List.copyOf(stages);otherPending=List.copyOf(otherPending);}
 SpeechReviewFacts(List<Map<String,Object>> candidates,List<Map<String,Object>> audio,List<Stage> stages,boolean modelsCaptured){this(candidates,audio,stages,modelsCaptured,List.of());}
 record Stage(Map<String,Object> metadata,ByteBuffer payload){
  Stage{metadata=Collections.unmodifiableMap(new LinkedHashMap<>(metadata));payload=payload.asReadOnlyBuffer();}
  @Override public ByteBuffer payload(){return payload.asReadOnlyBuffer();}
  byte[] bytes(){var copy=payload.asReadOnlyBuffer();byte[] result=new byte[copy.remaining()];copy.get(result);return result;}
 }
 static final SpeechReviewFacts EMPTY=new SpeechReviewFacts(List.of(),List.of(),List.of(),false);
}
