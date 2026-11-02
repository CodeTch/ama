package ama.api;
import ama.contract.IdentityContracts.*;
import ama.governance.*;
import ama.mail.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class OperationsWireTest {
 final Actor actor=new Actor("ops-user",Status.ACTIVE,Set.of(Role.USER));
 final ResolvedSession session=new ResolvedSession(actor,"x".repeat(43),Instant.now().plusSeconds(300));
 @Test void exactLongRevisionAndOriginalMailKeyArePreserved()throws Exception{
  var g=mock(JdbcGovernance.class);var c=mock(JdbcCorpusFeedback.class);var mail=mock(JdbcMailJobs.class);var mvc=MockMvcBuilders.standaloneSetup(new OperationsController(g,c,mail)).setControllerAdvice(new OperationsErrors()).build();
  String m=UUID.randomUUID().toString(),p=UUID.randomUUID().toString();when(mail.enqueue(any(),any(),any(),anyLong(),any(),anyList())).thenReturn(Map.of("state","PENDING"));
  mvc.perform(post("/api/v1/meetings/"+m+"/mail").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","stable-key").contentType("application/json").content("{\"sourceRevisionToken\":\"9007199254740993\",\"publicationId\":\""+p+"\",\"recipientEnterpriseIds\":[\"ops-user\"]}")).andExpect(status().isAccepted());verify(mail).enqueue(actor,"stable-key",m,9007199254740993L,p,List.of("ops-user"));
 }
 @Test void arbitraryEndpointConfigurationAndDuplicateJsonNeverReachGovernance()throws Exception{
  var g=mock(JdbcGovernance.class);var mvc=MockMvcBuilders.standaloneSetup(new OperationsController(g,mock(JdbcCorpusFeedback.class),mock(JdbcMailJobs.class))).setControllerAdvice(new OperationsErrors()).build();
  for(String body:List.of("{\"datasetId\":\"x\",\"configuration\":{\"modelVersion\":\"v1\",\"promptVersion\":\"v1\",\"voiceThreshold\":null,\"url\":\"https://evil.invalid\"}}","{\"datasetId\":\"x\",\"datasetId\":\"y\",\"configuration\":{}}","{\"datasetId\":\"x\",\"configuration\":{}}{}"))mvc.perform(post("/api/v1/operations/evaluations").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","key").contentType("application/json").content(body)).andExpect(status().isBadRequest());
  verifyNoInteractions(g);
 }
 @Test void privateReadsRejectUnrecognizedQueries()throws Exception{
  var g=mock(JdbcGovernance.class);var mvc=MockMvcBuilders.standaloneSetup(new OperationsController(g,mock(JdbcCorpusFeedback.class),mock(JdbcMailJobs.class))).setControllerAdvice(new OperationsErrors()).build();when(g.voices(actor)).thenReturn(List.of());
  mvc.perform(get("/api/v1/operations/voiceprints").requestAttr(SessionSecurityFilter.SESSION,session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
  mvc.perform(get("/api/v1/operations/voiceprints?vector=true").requestAttr(SessionSecurityFilter.SESSION,session)).andExpect(status().isBadRequest());verify(g,times(1)).voices(actor);
 }
 @Test void missingNestedConfigurationFieldsAreRejectedBeforeAnyMutation()throws Exception{
  var g=mock(JdbcGovernance.class);var mvc=MockMvcBuilders.standaloneSetup(new OperationsController(g,mock(JdbcCorpusFeedback.class),mock(JdbcMailJobs.class))).setControllerAdvice(new OperationsErrors()).build();
  mvc.perform(post("/api/v1/operations/evaluations").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","key").contentType("application/json").content("{\"datasetId\":\"12345678-1234-1234-1234-123456789012\",\"configuration\":{\"modelVersion\":\"harness-embed-v1\",\"promptVersion\":\"summary-v1\"}}" )).andExpect(status().isBadRequest());verifyNoInteractions(g);
 }
 @Test void mailRecoveryReachesItsOwnMetadataGateAfterSourceWithdrawal()throws Exception{
  var access=mock(ama.meeting.JdbcMeetingAccess.class);var interceptor=new ExternalReadPreparation(access,mock(javax.sql.DataSource.class),mock(ama.transcript.JdbcMaterialAuthority.class));var controller=new OperationsController(mock(JdbcGovernance.class),mock(JdbcCorpusFeedback.class),mock(JdbcMailJobs.class));
  var request=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/v1/meetings/12345678-1234-1234-1234-123456789012/mail");request.setAttribute(SessionSecurityFilter.SESSION,session);var handler=new org.springframework.web.method.HandlerMethod(controller,OperationsController.class.getDeclaredMethod("enqueue",String.class,byte[].class,jakarta.servlet.http.HttpServletRequest.class));
  org.junit.jupiter.api.Assertions.assertTrue(interceptor.preHandle(request,new org.springframework.mock.web.MockHttpServletResponse(),handler));verifyNoInteractions(access);
 }
}
