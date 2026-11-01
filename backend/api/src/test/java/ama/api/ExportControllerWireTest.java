package ama.api;

import ama.contract.IdentityContracts.*;
import ama.export.JdbcMeetingExports;
import ama.task.MinutesExportRuntime;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportControllerWireTest {
 final String meeting=UUID.randomUUID().toString(),revision=UUID.randomUUID().toString();
 final Actor actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 MockHttpServletRequest request(String body){var request=new MockHttpServletRequest();request.addHeader("Idempotency-Key","original");request.setAttribute(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"csrf",Instant.now().plusSeconds(60)));request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return request;}
 @Test void acceptsOnlySavedRevisionAndFormatWithOriginalKey()throws Exception{
  var exports=mock(JdbcMeetingExports.class);var result=new MinutesExportRuntime.Status(UUID.randomUUID().toString(),"READY",revision,"md",null,null);when(exports.accept(actor,meeting,"original",revision,"md")).thenReturn(result);
  var response=new ExportController(exports).create(meeting,request("{\"sourceRevision\":\""+revision+"\",\"format\":\"md\"}"));
  assertEquals(202,response.getStatusCode().value());assertSame(result,response.getBody());assertEquals("private, no-store",response.getHeaders().getFirst("Cache-Control"));verify(exports).accept(actor,meeting,"original",revision,"md");
 }
 @Test void rejectsCallerBodyDuplicateFieldsExtraDocumentsAndAmbiguousHeadersBeforeAdmission(){
  var exports=mock(JdbcMeetingExports.class);var controller=new ExportController(exports);String valid="{\"sourceRevision\":\""+revision+"\",\"format\":\"md\"}";
  for(String bad:List.of(valid.replace("}",",\"body\":\"uncommitted draft\"}"),valid.replace("}",",\"format\":\"pdf\"}"),valid+valid,valid.replace("\"md\"","null"),"[]"))assertThrows(IllegalArgumentException.class,()->controller.create(meeting,request(bad)));
  var duplicate=request(valid);duplicate.addHeader("Idempotency-Key","second");assertThrows(IllegalArgumentException.class,()->controller.create(meeting,duplicate));
  var query=request(valid);query.addParameter("sourceRevision",UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->controller.create(meeting,query));verifyNoInteractions(exports);
 }
 @Test void unavailableAuthorityIsRetryableWithoutExposingContentOrMasqueradingAsRevocation()throws Exception{
  var exports=mock(JdbcMeetingExports.class);String job=UUID.randomUUID().toString();
  when(exports.status(actor,job)).thenThrow(new ama.access.MeetingAccess.AuthorityUnavailableException(),new ama.access.MeetingAccess.AccessDeniedException());
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ExportController(exports)).setControllerAdvice(new AuthorityErrors(),new ExportErrors()).build();
  var session=new ResolvedSession(actor,"csrf",Instant.now().plusSeconds(60));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/exports/"+job).requestAttr(SessionSecurityFilter.SESSION,session))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","private, no-store"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/exports/"+job).requestAttr(SessionSecurityFilter.SESSION,session))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
 }
}
