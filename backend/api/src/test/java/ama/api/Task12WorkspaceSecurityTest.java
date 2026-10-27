package ama.api;
import ama.identity.SessionPort;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.mock.web.*;import org.springframework.mock.env.MockEnvironment;import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
class Task12WorkspaceSecurityTest {
 @Test void workspaceAuthenticationAndProviderDenialsArePrivateNoStore()throws Exception {
  for(String path:List.of("/api/v1/meetings","/api/v1/meetings/categories","/api/v1/meetings/command-discovery","/api/v1/meetings/00000000-0000-4000-8000-000000000001/tasks","/api/v1/meetings/00000000-0000-4000-8000-000000000001/workspace"))for(boolean unavailable:List.of(false,true)){
   var sessions=mock(SessionPort.class);if(unavailable)when(sessions.resolve(null)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));else when(sessions.resolve(null)).thenReturn(Optional.empty());
   var filter=new SessionSecurityFilter(sessions,new MockEnvironment().withProperty("local.server.port","8080"));var request=new MockHttpServletRequest("GET",path);request.setLocalPort(8080);var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request,response,chain);
   assertEquals(unavailable?503:401,response.getStatus());assertEquals("private, no-store",response.getHeader("Cache-Control"));assertNull(chain.getRequest());assertFalse(response.getContentAsString().contains("private"));
  }
 }
 @Test void commandDiscoveryRequiresOriginAndCsrfBeforeController()throws Exception {
  var actor=new ama.contract.IdentityContracts.Actor("owner",ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  var session=new ama.contract.IdentityContracts.ResolvedSession(actor,"T".repeat(43),java.time.Instant.now().plusSeconds(60));
  for(int variant=0;variant<3;variant++){
   var sessions=mock(SessionPort.class);when(sessions.resolve(null)).thenReturn(Optional.of(session));var filter=new SessionSecurityFilter(sessions,new MockEnvironment().withProperty("local.server.port","8080").withProperty("identity.allowed-origin","https://ama.test"));var request=new MockHttpServletRequest("POST","/api/v1/meetings/command-discovery");request.setLocalPort(8080);if(variant!=0)request.addHeader("Origin","https://ama.test");if(variant!=1)request.addHeader("X-CSRF-Token",session.csrfToken());var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request,response,chain);assertEquals("private, no-store",response.getHeader("Cache-Control"));if(variant<2){assertEquals(403,response.getStatus());assertNull(chain.getRequest());}else assertNotNull(chain.getRequest());
  }
 }
}
