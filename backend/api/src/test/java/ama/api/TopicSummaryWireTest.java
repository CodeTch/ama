package ama.api;
import ama.meeting.JdbcTopics;
import ama.contract.IdentityContracts.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class TopicSummaryWireTest {
 @Test void summaryIsPrivateAndBindsTheActualActorAndTopic()throws Exception{
  String id="a344478d-6b36-4839-8992-0a2bf35b4f88";var actor=new Actor("summary-owner",Status.ACTIVE,Set.of(Role.USER));var topics=mock(JdbcTopics.class);when(topics.summary(actor,id)).thenReturn(Map.of("id",id,"revisionToken","2","meetingCount",3L));var mvc=MockMvcBuilders.standaloneSetup(new TopicController(topics)).build();
  mvc.perform(get("/api/v1/topics/"+id+"/summary").requestAttr(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60)))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.meetingCount").value(3));verify(topics).summary(actor,id);
 }
}
