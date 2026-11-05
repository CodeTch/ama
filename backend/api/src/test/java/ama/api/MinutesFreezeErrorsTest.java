package ama.api;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class MinutesFreezeErrorsTest {
 @Test void frozenMinutesMutationIsAConflictWhileUnavailableReadRemainsUnavailable()throws Exception{
  for(String method:List.of("POST","PUT","PATCH","DELETE","GET")){var response=new MockHttpServletResponse();new MinutesErrors().domain(new Failure(FailureCode.CAPABILITY_UNAVAILABLE),new MockHttpServletRequest(method,"/api/v1/meetings/id/minutes/editor-intents"),response);assertEquals(method.equals("GET")?503:409,response.getStatus());assertTrue(response.getContentAsString().contains(method.equals("GET")?"MINUTES_UNAVAILABLE":"MEETING_CONFLICT"));assertFalse(response.getContentAsString().contains("CAPABILITY_UNAVAILABLE"));assertEquals("private, no-store",response.getHeader("Cache-Control"));}
 }
 @Test void provenanceAndCapacityErrorsDoNotBecomeAFalseConflict()throws Exception{
  for(var code:List.of(FailureCode.PROVENANCE_UNAVAILABLE,FailureCode.BUDGET_EXCEEDED)){var response=new MockHttpServletResponse();new MinutesErrors().domain(new Failure(code),new MockHttpServletRequest("POST","/api/v1/meetings/id/minutes/editor-intents"),response);assertEquals(code==FailureCode.BUDGET_EXCEEDED?413:503,response.getStatus());}
 }
}
