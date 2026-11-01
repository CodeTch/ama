package ama.api;
import ama.contract.IdentityContracts.*;import ama.contract.MeetingContracts.*;import ama.meeting.*;import java.time.Instant;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class MeetingInfoSuggestionWireTest {
 final String meeting=UUID.randomUUID().toString(),suggestion=UUID.randomUUID().toString();final Actor actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 MockHttpServletRequest request(String body){var r=new MockHttpServletRequest();r.addHeader("Idempotency-Key","original");r.setAttribute(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"csrf",Instant.now().plusSeconds(60)));r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;}
 @Test void exactDecisionRoutesToOriginalVersionAndKeepsBusinessConflict409()throws Exception{
  var repository=mock(JdbcMeetingRepository.class);var runtime=mock(MinutesConfiguration.Runtime.class);var controller=new MeetingInfoSuggestionController(repository,runtime,mock(ama.db.TransactionRunner.class),mock(ama.access.MeetingAccess.class));
  when(repository.decideSuggestion(eq(actor),eq(meeting),eq(suggestion),eq("original"),eq(17L),eq("ACCEPT"),any(),anyString())).thenThrow(new Conflict());
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new MinutesErrors(),new MeetingErrors()).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/meetings/"+meeting+"/info-suggestions/"+suggestion+"/decision").requestAttr(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"csrf",Instant.now().plusSeconds(60))).header("Idempotency-Key","original").contentType("application/json").content("{\"expectedRevisionToken\":\"17\",\"decision\":\"ACCEPT\"}"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
 }
 @Test void rejectsUnknownDuplicateDecisionAndClientSuppliedSuggestionTextBeforeRepository(){
  var repository=mock(JdbcMeetingRepository.class);var controller=new MeetingInfoSuggestionController(repository,mock(MinutesConfiguration.Runtime.class),mock(ama.db.TransactionRunner.class),mock(ama.access.MeetingAccess.class));
  String good="{\"expectedRevisionToken\":\"17\",\"decision\":\"ACCEPT\"}";
  for(String bad:List.of(good+good,good.replace("}",",\"value\":\"forged\"}"),good.replace("}",",\"decision\":\"REJECT\"}"),good.replace("ACCEPT","AUTO"),good.replace("\"17\"","17")))assertThrows(IllegalArgumentException.class,()->controller.decide(meeting,suggestion,request(bad)));
  verifyNoInteractions(repository);
 }
}
