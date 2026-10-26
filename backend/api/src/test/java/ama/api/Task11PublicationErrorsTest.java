package ama.api;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import ama.contract.IdentityContracts.*;import java.util.*;import java.time.Instant;
class Task11PublicationErrorsTest {
 @Test void actualControllerUsesSpecificFailureAdviceWithoutErrorDispatch()throws Exception {
  var local=mock(ama.transcript.JdbcPublicationLocalRecovery.class);var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,local,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationController(runtime,null,null)).setControllerAdvice(new PublicationErrors()).build();var actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));var session=new ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60));
  for(var fixture:List.of(new Object[]{new ama.contract.TranscriptContracts.Failure(409,"PUBLICATION_LOCAL_RECOVERY_UNAVAILABLE"),409,"PUBLICATION_LOCAL_RECOVERY_UNAVAILABLE"},new Object[]{new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT"),409,"REVISION_CONFLICT"},new Object[]{new ama.contract.MinutesContracts.Failure(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED),413,"REQUEST_TOO_LARGE"})) {
   doThrow((RuntimeException)fixture[0]).when(local).recover(any(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString());
   var result=mvc.perform(post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/publications/12345678-1234-4234-8234-123456789abd/local-recoveries").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","local-recovery-map").contentType("application/json").content("{\"taskId\":\"12345678-1234-4234-8234-123456789abe\",\"expectedRevisionToken\":\"13\",\"expectedPublicationRevisionToken\":\"1\"}")).andReturn();assertEquals(fixture[1],result.getResponse().getStatus());assertTrue(result.getResponse().getContentAsString().contains((String)fixture[2]));assertEquals("private, no-store",result.getResponse().getHeader("Cache-Control"));assertNull(result.getResponse().getForwardedUrl());
  }
 }
 @Test void finalNotReadyRemains409OnTheFinalRoute()throws Exception {
  var finals=mock(ama.transcript.JdbcFinalConfirmations.class);var receipts=mock(ama.transcript.JdbcMinutesCommandReceipts.class);
  when(receipts.lookup(any(),anyString(),anyString(),anyString(),anyLong(),any(String[].class))).thenReturn(Optional.empty());
  when(finals.confirm(any(),anyString(),anyString(),anyLong(),anyList(),anyString())).thenThrow(new ama.contract.TranscriptContracts.Failure(409,"FINAL_NOT_READY"));
  var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,finals,null,null,65536);
  var mvc=MockMvcBuilders.standaloneSetup(new FinalConfirmationController(runtime,receipts,null,null)).setControllerAdvice(new PublicationErrors()).build();
  var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
  var result=assertDoesNotThrow(()->mvc.perform(post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/final-confirmations").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","final-readiness-map").contentType("application/json").content("{\"expectedRevisionToken\":\"13\",\"optionalReviewIds\":[]}")).andReturn(),"Final failure must be handled on the original route");
  assertEquals(409,result.getResponse().getStatus());assertTrue(result.getResponse().getContentAsString().contains("FINAL_NOT_READY"));assertEquals("private, no-store",result.getResponse().getHeader("Cache-Control"));assertNull(result.getResponse().getForwardedUrl());
 }

 @Test void malformedPublicationJsonIs400WithoutErrorDispatch()throws Exception {
  var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,null,65536);
  var mvc=MockMvcBuilders.standaloneSetup(new PublicationController(runtime,null,null)).setControllerAdvice(new PublicationErrors()).build();
  var result=assertDoesNotThrow(()->mvc.perform(post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/publications").contentType("application/json").content("{\"expectedRevisionToken\":\"1\"," )).andReturn());assertEquals(400,result.getResponse().getStatus());assertEquals("private, no-store",result.getResponse().getHeader("Cache-Control"));assertNull(result.getResponse().getForwardedUrl());
 }

 @Test void responseEncodingAndDatabaseFailuresAre503RatherThanInvalidRequests()throws Exception {
  var finals=mock(ama.transcript.JdbcFinalConfirmations.class);var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,finals,null,null,65536);var mvc=MockMvcBuilders.standaloneSetup(new FinalConfirmationController(runtime,null,null,null)).setControllerAdvice(new PublicationErrors()).build();var actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));var session=new ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60));
  when(finals.current(any(),anyString())).thenReturn(Map.of("createdAt",java.time.LocalDateTime.of(2026,10,25,12,0)));
  for(int n=0;n<2;n++){if(n==1)when(finals.current(any(),anyString())).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("read","SELECT invalid",new java.sql.SQLException("unavailable")));var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/final-confirmations/current").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(503,result.getStatus());assertTrue(result.getContentAsString().contains("PUBLICATION_UNAVAILABLE"));assertEquals("private, no-store",result.getHeader("Cache-Control"));}
 }

 @Test void physicalCommitFailureMapsToSafe503OnTheActualLocalRoute()throws Exception {
  var local=mock(ama.transcript.JdbcPublicationLocalRecovery.class);var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,local,65536);
  doThrow(new org.springframework.transaction.TransactionSystemException("private transaction detail",new java.sql.SQLException("private database detail"))).when(local).recover(any(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString());
  var mvc=MockMvcBuilders.standaloneSetup(new PublicationController(runtime,null,null)).setControllerAdvice(new PublicationErrors()).build();var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
  var response=assertDoesNotThrow(()->mvc.perform(post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/publications/12345678-1234-4234-8234-123456789abd/local-recoveries").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","local-commit-error-map").contentType("application/json").content("{\"taskId\":\"12345678-1234-4234-8234-123456789abe\",\"expectedRevisionToken\":\"13\",\"expectedPublicationRevisionToken\":\"6\"}")).andReturn());
  assertEquals(503,response.getResponse().getStatus());assertTrue(response.getResponse().getContentAsString().contains("PUBLICATION_UNAVAILABLE"));assertFalse(response.getResponse().getContentAsString().contains("private"));assertEquals("private, no-store",response.getResponse().getHeader("Cache-Control"));assertNull(response.getResponse().getForwardedUrl());
 }
 @Test void providerIOExceptionMapsToSafe503OnTheActualSubmitRoute()throws Exception {
  var service=mock(ama.transcript.JdbcPublicationService.class);var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);
  when(service.submit(any(),anyString(),anyString(),anyLong(),anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new java.io.IOException("private provider response detail"));
  var mvc=MockMvcBuilders.standaloneSetup(new PublicationController(runtime,null,null)).setControllerAdvice(new PublicationErrors()).build();var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
  var response=assertDoesNotThrow(()->mvc.perform(post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/publications").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","provider-io-error-map").contentType("application/json").content("{\"expectedRevisionToken\":\"13\",\"confirmationId\":\"12345678-1234-4234-8234-123456789abd\",\"providerId\":\"CONTROLLED_KNOWLEDGE_V1\",\"libraryId\":\"library\",\"folderId\":\"folder\"}")).andReturn());
  assertEquals(503,response.getResponse().getStatus());assertTrue(response.getResponse().getContentAsString().contains("PUBLICATION_UNAVAILABLE"));assertFalse(response.getResponse().getContentAsString().contains("private"));assertEquals("private, no-store",response.getResponse().getHeader("Cache-Control"));assertNull(response.getResponse().getForwardedUrl());
 }}
