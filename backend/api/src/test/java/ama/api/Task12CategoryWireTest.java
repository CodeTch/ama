package ama.api;
import ama.contract.IdentityContracts.*;
import ama.contract.WorkspaceContracts.*;
import ama.meeting.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Task12CategoryWireTest {
 final ResolvedSession session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void literalCategoryRouteUsesCatalogAndNeverMeetingIdReader()throws Exception {
  var catalog=mock(JdbcCategoryCatalog.class);var repository=mock(MeetingRepository.class);
  when(catalog.read(session.actor(),20,null)).thenReturn(new CategoryPage(List.of(new CategoryItem("controlled","受控分类")),null,false));
  var mvc=MockMvcBuilders.standaloneSetup(new CategoryController(catalog),new MeetingController(new MeetingService(repository),new RosterService(repository),new MockEnvironment())).setControllerAdvice(new CategoryErrors()).build();
  var response=mvc.perform(get("/api/v1/meetings/categories").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();
  assertEquals(200,response.getStatus());assertEquals("private, no-store",response.getHeader("Cache-Control"));assertTrue(response.getContentAsString().contains("controlled"));verifyNoInteractions(repository);
 }
 @Test void strictQueryAndUnavailableDictionaryHaveClosedNoStoreErrors()throws Exception {
  var catalog=mock(JdbcCategoryCatalog.class);var mvc=MockMvcBuilders.standaloneSetup(new CategoryController(catalog)).setControllerAdvice(new CategoryErrors()).build();
  for(String query:List.of("limit=0","limit=51","limit=01","limit=2147483648","limit=1&limit=2","actor=other")) {
   var response=mvc.perform(get("/api/v1/meetings/categories?"+query).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(400,response.getStatus(),query);assertEquals("private, no-store",response.getHeader("Cache-Control"));
  }
  verifyNoInteractions(catalog);when(catalog.read(session.actor(),20,null)).thenThrow(new ama.contract.MeetingContracts.CategoryUnavailable());
  var response=mvc.perform(get("/api/v1/meetings/categories").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(503,response.getStatus());assertTrue(response.getContentAsString().contains("CATEGORY_DICTIONARY_UNAVAILABLE"));assertEquals("private, no-store",response.getHeader("Cache-Control"));
 }
}
