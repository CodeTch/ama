package ama.task;

import ama.transcript.SpeechPipeline;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;

/** Private bounded value codec; decoding conveys no source or publication authority. */
public final class SpeechCheckpointCodec {
 static final int MAX_BYTES=1048576;
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder()
  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
  .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(65536).maxNumberLength(32).maxNameLength(64).build()).build())
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
 private SpeechCheckpointCodec(){}
 static byte[] encode(SpeechPipeline.Stage stage,SpeechPipeline.Snapshot value){
  if(stage==null||!type(stage).isInstance(value))throw invalid();
  try{var node=JSON.createObjectNode();node.put("schema","ama.speech-checkpoint.v1");node.put("stage",stage.name());node.set("value",JSON.valueToTree(value));byte[] bytes=JSON.writeValueAsBytes(node);if(bytes.length>MAX_BYTES)throw invalid();return bytes;}
  catch(java.io.IOException|RuntimeException failure){throw invalid();}
 }
 public static SpeechPipeline.Snapshot decode(SpeechPipeline.Stage stage,byte[] bytes){
  if(stage==null||bytes==null||bytes.length<1||bytes.length>MAX_BYTES)throw invalid();
  try{var root=JSON.readTree(bytes);var names=new HashSet<String>();if(root!=null)root.fieldNames().forEachRemaining(names::add);
   if(root==null||!root.isObject()||!names.equals(Set.of("schema","stage","value"))||!root.path("schema").asText().equals("ama.speech-checkpoint.v1")||!root.path("stage").asText().equals(stage.name())||!root.path("value").isObject())throw invalid();
   return JSON.treeToValue(root.get("value"),type(stage));
  }catch(java.io.IOException|RuntimeException failure){throw invalid();}
 }
 private static Class<? extends SpeechPipeline.Snapshot> type(SpeechPipeline.Stage stage){return switch(stage){
  case VAD->SpeechPipeline.Vad.class;case DIARIZATION->SpeechPipeline.Diarization.class;case ASR->SpeechPipeline.Recognition.class;
  case ENHANCEMENT->SpeechPipeline.Enhancement.class;case EMBEDDING->SpeechPipeline.Embedding.class;case CORRECTION->SpeechPipeline.Correction.class;
 };}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid speech checkpoint");}
}
