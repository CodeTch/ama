package ama.api;
import ama.contract.IdentityContracts.*;
import ama.transcript.JdbcPublicationGovernance;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GovernanceControllerWireTest {
 @Test void governanceConflictAndInvalidCallbackStayOnTheirOriginalRoutes()throws Exception{
  var service=mock(JdbcPublicationGovernance.class);
  when(service.accept(any(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString())).thenThrow(new ama.contract.TranscriptContracts.Failure(409,"IDEMPOTENCY_CONFLICT"));
  doThrow(new IllegalArgumentException("Invalid signature")).when(service).callback(anyString());
  var env=new org.springframework.mock.env.MockEnvironment().withProperty("local.server.port","80");
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new GovernanceController(service),new KnowledgeGovernanceEventController(service,env)).setControllerAdvice(new PublicationErrors()).build();
  var session=new ResolvedSession(actor,"csrf",Instant.now().plusSeconds(60));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/meetings/"+meeting+"/publications/"+publication+"/withdrawal").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","original").contentType("application/json").content("{\"expectedRevisionToken\":\"17\",\"reason\":\"changed\"}"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","private, no-store"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/integrations/v1/knowledge/governance-events").contentType("text/plain").content("invalid-signed-event"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("INVALID_ARGUMENT"));
 }
 final String meeting=UUID.randomUUID().toString(),publication=UUID.randomUUID().toString();
 final Actor actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 MockHttpServletRequest request(String body){var r=new MockHttpServletRequest();r.addHeader("Idempotency-Key","original");r.setAttribute(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"csrf",Instant.now().plusSeconds(60)));r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;}
 @Test void withdrawalPersistsOriginalVersionReasonAndReturnsAcceptedMetadata()throws Exception{
  var service=mock(JdbcPublicationGovernance.class);var result=Map.<String,Object>of("governanceJobId",UUID.randomUUID().toString(),"state","PENDING");when(service.accept(actor,meeting,publication,"original",17,"WITHDRAW","撤回原文")).thenReturn(result);
  var response=new GovernanceController(service).withdraw(meeting,publication,request("{\"expectedRevisionToken\":\"17\",\"reason\":\"撤回原文\"}"));
  assertEquals(202,response.getStatusCode().value());assertSame(result,response.getBody());assertEquals("private, no-store",response.getHeaders().getFirst("Cache-Control"));
 }
 @Test void oversizedUnknownDuplicateAndAmbiguousFieldsNeverReachCommandAdmission(){
  var service=mock(JdbcPublicationGovernance.class);var controller=new GovernanceController(service);String valid="{\"expectedRevisionToken\":\"17\",\"reason\":\"原请求\"}";
  for(String bad:List.of(valid+valid,"[]",valid.replace("}",",\"reason\":\"另一原因\"}"),valid.replace("}",",\"documentId\":\"foreign\"}"),valid.replace("\"17\"","17")))assertThrows(IllegalArgumentException.class,()->controller.withdraw(meeting,publication,request(bad)));
  var duplicate=request(valid);duplicate.addHeader("Idempotency-Key","another");assertThrows(IllegalArgumentException.class,()->controller.withdraw(meeting,publication,duplicate));
  assertThrows(ama.contract.TranscriptContracts.Failure.class,()->controller.withdraw(meeting,publication,request("x".repeat(32769))));verifyNoInteractions(service);
 }
}
