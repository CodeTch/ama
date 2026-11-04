package ama.model;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HttpMaterialEnhancementTest {
 static final String REQUEST="10000000-0000-4000-8000-000000000001",SEGMENT="10000000-0000-4000-8000-000000000002",MATERIAL="10000000-0000-4000-8000-000000000003";
 static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
 byte[] input()throws Exception{return JSON.writeValueAsBytes(Map.of("profile","ama-material-enhancement.v1","segments",List.of(Map.of("id",SEGMENT,"text","招上基金")),"materials",List.of(Map.of("revisionId",MATERIAL,"sha256","a".repeat(64),"units",List.of(Map.of("ordinal",1,"text","术语对照：招上基金=招商基金"))))));}
 Map<String,Object> result(byte[] input){return new LinkedHashMap<>(Map.of("requestId",REQUEST,"profile","ama-material-enhancement.v1","inputSha256",HttpMaterialEnhancement.sha(input),"state","SUCCEEDED","materialRevisionIds",List.of(MATERIAL),"segments",List.of(Map.of("id",SEGMENT,"text","招商基金"))));}
 @Test void candidateBindsExactInputMaterialsAndSegmentIdentityWithoutMutatingOriginal()throws Exception{
  byte[] original=input(),copy=original.clone();var verified=HttpMaterialEnhancement.verify(REQUEST,original,JSON.writeValueAsBytes(result(original)));
  assertEquals("招商基金",verified.path("segments").get(0).path("text").asText());assertArrayEquals(copy,original);
 }
 @Test void foreignScopeWrongMaterialReorderedSegmentsAndErasureAreRejected()throws Exception{
  byte[] original=input();for(var change:List.<Map<String,Object>>of(Map.of("requestId",SEGMENT),Map.of("inputSha256","0".repeat(64)),Map.of("materialRevisionIds",List.of()),Map.of("segments",List.of(Map.of("id",MATERIAL,"text","招商基金"))),Map.of("segments",List.of(Map.of("id",SEGMENT,"text",""))),Map.of("state","RUNNING"))){var candidate=result(original);candidate.putAll(change);assertThrows(IllegalArgumentException.class,()->HttpMaterialEnhancement.verify(REQUEST,original,JSON.writeValueAsBytes(candidate)));}
 }
}
