package ama.api;
import ama.transcript.JdbcMaterials;
import ama.contract.IdentityContracts.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaterialBatchWireTest {
 final String meeting=UUID.randomUUID().toString();final Actor actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 MockHttpServletRequest request(String body){var r=new MockHttpServletRequest();r.addHeader("Idempotency-Key","original");r.setAttribute(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"csrf",Instant.now().plusSeconds(60)));r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;}
 @Test void exactBoundedBatchHasOneAdmissionAndRejectsIncompleteOrDuplicateSources()throws Exception{
  var service=mock(JdbcMaterials.class);ObjectProvider<JdbcMaterials> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(service);var controller=new MaterialController(provider);
  String item="{\"libraryId\":\"library\",\"documentId\":\"document\",\"version\":\""+"a".repeat(64)+"\"}";
  String valid="{\"expectedRevisionToken\":\"12\",\"items\":["+item+"]}";
  controller.addBatch(meeting,request(valid));verify(service).addBatch(eq(actor),eq(meeting),eq("original"),eq(12L),eq(List.of(new JdbcMaterials.Selection(null,"library","document","a".repeat(64)))),anyString());
  clearInvocations(service);
  for(String bad:List.of(valid.replace("12","-1"),valid.replace("["+item+"]","[]"),valid.replace("["+item+"]","["+item+","+item+"]"),valid.replace("\"version\"","\"unexpected\""),valid.replace("}",",\"ignored\":1}")))assertThrows(Exception.class,()->controller.addBatch(meeting,request(bad)));
  verifyNoInteractions(service);
 }
}
