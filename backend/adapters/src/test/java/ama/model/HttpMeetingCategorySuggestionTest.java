package ama.model;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpMeetingCategorySuggestionTest {
 private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
 private byte[] request()throws Exception{
  var categories=List.of(Map.of("id","project","label","项目会议"));
  return JSON.writeValueAsBytes(Map.of("profile","ama-meeting-category.v1","candidateId","10000000-0000-4000-8000-000000000001","inputFingerprint","a".repeat(64),"name","项目推进会","summary","核对项目计划","dictionaryVersion","b".repeat(64),"categories",categories));
 }
 private Map<String,Object> response(byte[] request){return new LinkedHashMap<>(Map.of("profile","ama-meeting-category.v1","inputSha256",HttpMeetingCategorySuggestion.sha(request),"mode","LOCAL_SIMULATION","categoryId","project"));}
 @Test void onlyAnExactInputBoundDictionaryValueCanBecomeASuggestion()throws Exception{
  var request=request();assertEquals("project",HttpMeetingCategorySuggestion.verify(request,JSON.writeValueAsBytes(response(request))));
  var empty=response(request);empty.put("categoryId",null);assertNull(HttpMeetingCategorySuggestion.verify(request,JSON.writeValueAsBytes(empty)));
 }
 @Test void wrongInputUnknownCategoryAndUnversionedResultAreRejected()throws Exception{
  var request=request();for(var change:List.of(Map.of("inputSha256","0".repeat(64)),Map.of("categoryId","invented"),Map.of("profile","other"),Map.of("extra","field"))){var result=response(request);result.putAll(change);assertThrows(IllegalArgumentException.class,()->HttpMeetingCategorySuggestion.verify(request,JSON.writeValueAsBytes(result)));}
 }
}
