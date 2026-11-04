package ama.api;
import ama.contract.IdentityContracts.*;import ama.meeting.JdbcWorkspaceNavigationReader;import ama.meeting.WorkspaceReadBudget;import ama.access.MeetingAccess;
import java.time.Instant;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class Task12WorkspaceNavigationWireTest {
 static final String M="00000000-0000-4000-8000-000000000001",I="00000000-0000-4000-8000-000000000002";
 final ResolvedSession session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void strictNoQueryWorkspaceUsesGeneratedStringTokenNavigationShape()throws Exception{
  var reader=mock(JdbcWorkspaceNavigationReader.class);var saved=new JdbcWorkspaceNavigationReader.SavedReferences(I,null,null,null,null,null);
  when(reader.read(session.actor(),M)).thenReturn(new JdbcWorkspaceNavigationReader.Navigation(M,"9007199254740993","7","ENTRY","OWNER","CHECK_PRODUCER","RECHECK_REQUIRED",saved));
  var mvc=MockMvcBuilders.standaloneSetup(new WorkspaceNavigationController(reader)).setControllerAdvice(new MeetingListErrors()).build();
  for(String query:List.of("?owner=true","?tasks=all","?cursor=","?limit=20&limit=20")){var r=mvc.perform(get("/api/v1/meetings/"+M+"/workspace"+query).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(400,r.getStatus());assertEquals("private, no-store",r.getHeader("Cache-Control"));}verifyNoInteractions(reader);
  var r=mvc.perform(get("/api/v1/meetings/"+M+"/workspace").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(200,r.getStatus());assertEquals("private, no-store",r.getHeader("Cache-Control"));var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.getContentAsString());assertEquals("9007199254740993",json.get("revisionToken").textValue());assertEquals("OWNER",json.get("relation").textValue());assertEquals("RECHECK_REQUIRED",json.get("contentEligibility").textValue());assertTrue(json.get("saved").get("rosterRevisionId").isNull());assertFalse(json.has("tasks"));assertEquals("UNAVAILABLE",json.get("privateSectionsState").textValue());assertEquals("PROCESSING_RUNTIME_UNAVAILABLE",json.get("privateSectionsReason").textValue());
 }
 @Test void workspaceDenialAndUnavailableNeverExposePrivateCause()throws Exception{
  var reader=mock(JdbcWorkspaceNavigationReader.class);var mvc=MockMvcBuilders.standaloneSetup(new WorkspaceNavigationController(reader)).setControllerAdvice(new MeetingListErrors()).build();
  when(reader.read(session.actor(),M)).thenThrow(new MeetingAccess.AccessDeniedException()).thenThrow(new WorkspaceReadBudget.Unavailable(new IllegalStateException("private-navigation-parent")));
  for(int status:List.of(403,503)){var r=mvc.perform(get("/api/v1/meetings/"+M+"/workspace").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(status,r.getStatus());assertEquals("private, no-store",r.getHeader("Cache-Control"));assertFalse(r.getContentAsString().contains("private-navigation-parent"));}
 }
 @Test void accessRenewalUsesTheRealGateAndOmitsPrivateOverview()throws Exception{
  var reader=mock(JdbcWorkspaceNavigationReader.class);var overview=mock(ama.transcript.JdbcWorkspaceOverview.class);
  var saved=new JdbcWorkspaceNavigationReader.SavedReferences(I,null,null,null,null,null);
  when(reader.read(session.actor(),M)).thenReturn(new JdbcWorkspaceNavigationReader.Navigation(M,"7","1","ENTRY","OWNER","CHECK_PRODUCER","RECHECK_REQUIRED",saved)).thenThrow(new MeetingAccess.AccessDeniedException());
  var mvc=MockMvcBuilders.standaloneSetup(new WorkspaceNavigationController(reader,overview)).setControllerAdvice(new MeetingListErrors()).build();
  var good=mvc.perform(get("/api/v1/meetings/"+M+"/workspace/access").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();
  assertEquals(200,good.getStatus());assertEquals("private, no-store",good.getHeader("Cache-Control"));var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(good.getContentAsString());assertEquals(2,body.size());assertEquals(M,body.get("meetingId").asText());assertTrue(body.get("allowed").asBoolean());
  assertEquals(403,mvc.perform(get("/api/v1/meetings/"+M+"/workspace/access").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());
  assertEquals(400,mvc.perform(get("/api/v1/meetings/"+M+"/workspace/access?owner=true").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());verifyNoInteractions(overview);verify(reader,times(2)).read(session.actor(),M);
 }

}
