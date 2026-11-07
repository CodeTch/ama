package ama.api;

import ama.contract.IdentityContracts.*;
import ama.governance.JdbcGovernanceWorkbench;
import ama.access.MeetingAccess;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GovernanceWorkbenchRoutesTest {
 private final ResolvedSession session=new ResolvedSession(new Actor("reviewer",Status.ACTIVE,Set.of(Role.PLATFORM_ADMIN)),"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void literalPagesDoNotFallThroughToUuidDetails() throws Exception {
  var workbench=mock(JdbcGovernanceWorkbench.class);
  var mvc=MockMvcBuilders.standaloneSetup(new GovernanceWorkbenchController(workbench)).setControllerAdvice(new OperationsErrors()).build();
  for(String module:List.of("voiceprints","datasets","candidates","corpus","evaluations","my-voiceprints")){
   when(workbench.page(session.actor(),module,null,null,"PENDING",20,Map.of())).thenReturn(Map.of("items",List.of(),"total",0,"counts",Map.of("ALL",0)));
   var response=mvc.perform(get("/api/v1/operations/"+module+"/page").param("limit","20").param("state","PENDING").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();
   assertEquals(200,response.getStatus(),module);assertEquals("private, no-store",response.getHeader("Cache-Control"));
   verify(workbench).page(session.actor(),module,null,null,"PENDING",20,Map.of());
  }
  verify(workbench,never()).voiceDetail(any(),any());verify(workbench,never()).datasetDetail(any(),any());verify(workbench,never()).candidate(any(),any());
 }
 @Test void uuidDetailsKeepTheirExistingHandlers() throws Exception {
  var workbench=mock(JdbcGovernanceWorkbench.class);String id="ed6b73c6-feb9-4a5e-8af1-4ea45932f04f";
  var mvc=MockMvcBuilders.standaloneSetup(new GovernanceWorkbenchController(workbench)).build();
  when(workbench.voiceDetail(session.actor(),id)).thenReturn(Map.of("id",id));
  when(workbench.datasetDetail(session.actor(),id)).thenReturn(Map.of("id",id));
  when(workbench.candidate(session.actor(),id)).thenReturn(Map.of("id",id));
  for(String module:List.of("voiceprints","datasets","candidates"))assertEquals(200,mvc.perform(get("/api/v1/operations/"+module+"/"+id).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());
  verify(workbench).voiceDetail(session.actor(),id);verify(workbench).datasetDetail(session.actor(),id);verify(workbench).candidate(session.actor(),id);
  verify(workbench,never()).page(any(),any(),any(),any(),any(),anyInt(),anyMap());
 }
 @Test void pageAuthorizationAndStrictQueriesAreStillEnforced() throws Exception {
  var workbench=mock(JdbcGovernanceWorkbench.class);
  var mvc=MockMvcBuilders.standaloneSetup(new GovernanceWorkbenchController(workbench)).setControllerAdvice(new OperationsErrors()).build();
  when(workbench.page(session.actor(),"voiceprints",null,null,null,20,Map.of())).thenThrow(new MeetingAccess.AccessDeniedException());
  assertEquals(403,mvc.perform(get("/api/v1/operations/voiceprints/page").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());
  assertEquals(400,mvc.perform(get("/api/v1/operations/voiceprints/page").param("actor","other").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());
  verify(workbench,times(1)).page(session.actor(),"voiceprints",null,null,null,20,Map.of());
 }
}
