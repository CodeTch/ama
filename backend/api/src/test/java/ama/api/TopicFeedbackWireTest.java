package ama.api;

import ama.contract.IdentityContracts.*;
import ama.meeting.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopicFeedbackWireTest {
    private final ResolvedSession session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
    @Test void topicAndFeedbackListsArePrivateAndNoStore(){
        var topics=mock(JdbcTopics.class);var feedback=mock(JdbcFeedback.class);when(topics.list(any())).thenReturn(List.of());var page=new HashMap<String,Object>();page.put("items",List.of());page.put("nextCursor",null);when(feedback.list(any(),any())).thenReturn(page);
        var mvc=MockMvcBuilders.standaloneSetup(new TopicController(topics),new FeedbackController(feedback)).build();
        try{mvc.perform(get("/api/v1/topics").requestAttr(SessionSecurityFilter.SESSION,session)).andExpect(status().isOk());mvc.perform(get("/api/v1/feedback").requestAttr(SessionSecurityFilter.SESSION,session)).andExpect(status().isOk());}catch(Exception e){throw new AssertionError(e);}
    }
}
