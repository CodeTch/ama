package ama.api;

import ama.transcript.JdbcProcessingDiscovery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Task12ProcessingQueryTest {
 @Test void outOfIntegerRangePageLimitIsBadRequestBeforeDiscovery() throws Exception {
  var discovery=mock(JdbcProcessingDiscovery.class);
  var session=new ama.contract.IdentityContracts.ResolvedSession(new ama.contract.IdentityContracts.Actor("query-owner",ama.contract.IdentityContracts.Status.ACTIVE,java.util.Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),java.time.Instant.now().plusSeconds(60));
  var mvc=MockMvcBuilders.standaloneSetup(new ProcessingDiscoveryController(discovery)).setControllerAdvice(new ProcessingErrors()).build();
  for(String route:List.of("uploads","uploads/00000000-0000-0000-0000-000000000002/parts","completed-files","processing-inputs","processing-tasks")) {
   for(String limit:List.of("2147483648","9223372036854775807","9223372036854775808")) {
    var response=mvc.perform(get("/api/v1/meetings/00000000-0000-0000-0000-000000000001/"+route).queryParam("limit",limit).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();
    assertEquals(400,response.getStatus(),route+" limit="+limit);
    assertTrue(response.getContentAsString().contains("INVALID_ARGUMENT"));
   }
  }
  verifyNoInteractions(discovery);
 }
}
