package ama.api;
import ama.contract.IdentityContracts.*;import ama.contract.WorkspaceContracts.*;import ama.meeting.*;import java.time.Instant;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class Task12CommandDiscoveryWireTest {
 final ResolvedSession session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void lookupIsStrictBodyOnlyAndHasPrivateErrors()throws Exception {
  var reader=mock(JdbcMeetingCommandDiscovery.class);var mvc=MockMvcBuilders.standaloneSetup(new MeetingCommandDiscoveryController(reader)).setControllerAdvice(new MeetingCommandDiscoveryErrors()).build();
  for(String body:List.of("{}","[]","{\"operation\":\"CREATE\",\"resource\":\"meetings\",\"key\":\"a\",\"key\":\"b\"}","{\"operation\":\"CREATE\",\"resource\":\"meetings\",\"key\":1}","{\"operation\":\"CREATE\",\"resource\":\"meetings\",\"key\":\"k\",\"actor\":\"other\"}")){
   var response=mvc.perform(post("/api/v1/meetings/command-discovery").contentType("application/json").content(body).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(400,response.getStatus(),body);assertEquals("private, no-store",response.getHeader("Cache-Control"));
  }verifyNoInteractions(reader);
  String body="{\"operation\":\"CREATE\",\"resource\":\"meetings\",\"key\":\"exact key\"}";
  assertEquals(400,mvc.perform(post("/api/v1/meetings/command-discovery?key=secret").contentType("application/json").content(body).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());verifyNoInteractions(reader);
  when(reader.lookup(session.actor(),"CREATE","meetings","exact key")).thenReturn(new CommandLookup("ABSENT",null,null));var response=mvc.perform(post("/api/v1/meetings/command-discovery").contentType("application/json").content(body).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(200,response.getStatus());assertFalse(response.getContentAsString().contains("exact key"));assertTrue(response.getContentAsString().contains("ABSENT"));
 }
}
