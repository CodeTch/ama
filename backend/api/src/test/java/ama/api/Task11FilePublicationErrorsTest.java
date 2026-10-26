package ama.api;
import ama.storage.*;
import ama.contract.IdentityContracts.*;
import ama.contract.MinutesContracts.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class Task11FilePublicationErrorsTest {
 @Test void publicationGuardFailuresUseExistingSafeFileErrors()throws Exception{
  var uploads=mock(FileUploads.class);var mvc=MockMvcBuilders.standaloneSetup(new FileController(uploads,mock(FileRegistry.class),new MockEnvironment())).setControllerAdvice(new FileErrors()).build();var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
  for(var code:List.of(FailureCode.CAPABILITY_UNAVAILABLE,FailureCode.PROVENANCE_UNAVAILABLE,FailureCode.BUDGET_EXCEEDED)){
   doThrow(new Failure(code)).when(uploads).begin(any(),anyString(),anyString(),any(),anyString());
   var response=mvc.perform(post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/uploads").requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","test").contentType("application/json").content("{\"purpose\":\"TRANSCRIPT_INPUT\",\"name\":\"source.txt\",\"size\":8}")).andReturn().getResponse();
   assertEquals(code==FailureCode.CAPABILITY_UNAVAILABLE?409:503,response.getStatus());assertTrue(response.getContentAsString().contains(code==FailureCode.CAPABILITY_UNAVAILABLE?"FILE_CONFLICT":"FILE_STORAGE_UNAVAILABLE"));assertEquals("no-store",response.getHeader("Cache-Control"));assertFalse(response.getContentAsString().contains(code.name()));
  }
 }
}