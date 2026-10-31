package ama.api;
import ama.contract.IdentityContracts.*;
import ama.meeting.JdbcFeedback;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class FeedbackReviewWireTest {
 final String id="59d844ad-ffb3-49db-867d-ec0a387fe7ee",key=UUID.randomUUID().toString();
 final Actor actor=new Actor("review-owner",Status.ACTIVE,Set.of(Role.USER));
 final ResolvedSession session=new ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void exactRevisionAndOriginalKeyReachTheInstalledFeedbackCommands()throws Exception{
  var feedback=mock(JdbcFeedback.class);when(feedback.supplement(any(),any(),any(),anyLong(),any())).thenReturn(Map.of("operation","SUPPLEMENT"));
  var mvc=MockMvcBuilders.standaloneSetup(new FeedbackController(feedback)).build();
  mvc.perform(post("/api/v1/feedback/"+id+"/supplements").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key",key).contentType("application/json").content("{\"expectedRevisionToken\":\"9007199254740993\",\"text\":\"补充说明\"}")).andExpect(status().isOk());
  verify(feedback).supplement(actor,id,key,9007199254740993L,"补充说明");
 }
 @Test void detailAndReviewQueueArePrivateReads()throws Exception{
  var feedback=mock(JdbcFeedback.class);when(feedback.detail(actor,id)).thenReturn(Map.of("history",List.of()));when(feedback.reviewQueue(actor,null)).thenReturn(Map.of("items",List.of()));
  var mvc=MockMvcBuilders.standaloneSetup(new FeedbackController(feedback)).build();
  mvc.perform(get("/api/v1/feedback/"+id).requestAttr(SessionSecurityFilter.SESSION,session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
  mvc.perform(get("/api/v1/feedback/review-queue").requestAttr(SessionSecurityFilter.SESSION,session)).andExpect(status().isOk());verify(feedback).reviewQueue(actor,null);
 }
 @Test void malformedCommandsNeverReachFeedbackMutation()throws Exception{
  var feedback=mock(JdbcFeedback.class);var controller=new FeedbackController(feedback);
  var parse=FeedbackController.class.getDeclaredMethod("parse",byte[].class,Set.class);parse.setAccessible(true);
  for(String body:List.of("{}","{\"expectedRevisionToken\":\"0\",\"text\":\"一\",\"extra\":true}","{\"expectedRevisionToken\":\"0\",\"text\":\"一\",\"text\":\"二\"}","{\"expectedRevisionToken\":\"0\",\"text\":\"一\"}{}","x".repeat(32769))){
   var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->parse.invoke(controller,body.getBytes(java.nio.charset.StandardCharsets.UTF_8),Set.of("expectedRevisionToken","text")));assertInstanceOf(IllegalArgumentException.class,failure.getCause());
  }
  verifyNoInteractions(feedback);
 }
}
