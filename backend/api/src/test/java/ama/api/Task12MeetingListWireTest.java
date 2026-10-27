package ama.api;
import ama.contract.IdentityContracts.*;import ama.contract.WorkspaceContracts.*;import ama.meeting.*;import java.time.Instant;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class Task12MeetingListWireTest {
 final ResolvedSession session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void strictListQueryAndSafeErrors()throws Exception {
  var reader=mock(JdbcWebMeetingProjectionReader.class);var mvc=MockMvcBuilders.standaloneSetup(new MeetingListController(reader)).setControllerAdvice(new MeetingListErrors()).build();
  for(String query:List.of("limit=0","limit=51","limit=01","limit=1&limit=2","scope=other","scope=owned&scope=joined","actor=other")){
   var response=mvc.perform(get("/api/v1/meetings?"+query).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(400,response.getStatus(),query);assertEquals("private, no-store",response.getHeader("Cache-Control"));
  }verifyNoInteractions(reader);
  when(reader.list(session.actor(),"owned",20,null)).thenThrow(new WorkspaceReadBudget.Unavailable());
  var failure=mvc.perform(get("/api/v1/meetings").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(503,failure.getStatus());assertTrue(failure.getContentAsString().contains("MEETING_UNAVAILABLE"));
 }
 @Test void emptyBoundedScanCanContinueWithoutFabricatingVisibleRows()throws Exception {
  var reader=mock(JdbcWebMeetingProjectionReader.class);when(reader.list(session.actor(),"joined",20,null)).thenReturn(new MeetingPage(List.of(),"opaque",true));var mvc=MockMvcBuilders.standaloneSetup(new MeetingListController(reader)).setControllerAdvice(new MeetingListErrors()).build();
  var response=mvc.perform(get("/api/v1/meetings?scope=joined").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(200,response.getStatus());assertEquals("private, no-store",response.getHeader("Cache-Control"));assertTrue(response.getContentAsString().contains("\"hasMore\":true"));assertTrue(response.getContentAsString().contains("\"items\":[]"));
 }
}
