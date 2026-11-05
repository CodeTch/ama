package ama.api;

import ama.contract.IdentityContracts.Actor;
import ama.contract.IdentityContracts.ResolvedSession;
import ama.contract.IdentityContracts.Role;
import ama.contract.IdentityContracts.Status;
import ama.meeting.JdbcMeetingAccess;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ImportDraftReadPreparationTest {
 @Test void onlyTheDisplayRenewalHandlerDefersExternalPreparationToItsOwnGate()throws Exception{
  var access=mock(JdbcMeetingAccess.class);
  var preparation=new ExternalReadPreparation(access,mock(javax.sql.DataSource.class),mock(ama.transcript.JdbcMaterialAuthority.class));
  var actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
  var session=new ResolvedSession(actor,"x".repeat(43),Instant.now().plusSeconds(300));
  String meeting="12345678-1234-1234-1234-123456789012";
  var controller=mock(WorkspaceNavigationController.class);
  var request=new MockHttpServletRequest("GET","/api/v1/meetings/"+meeting+"/workspace/access");request.setAttribute(SessionSecurityFilter.SESSION,session);
  var renewal=new HandlerMethod(controller,WorkspaceNavigationController.class.getDeclaredMethod("access",String.class,jakarta.servlet.http.HttpServletRequest.class));
  assertTrue(preparation.preHandle(request,new MockHttpServletResponse(),renewal));verifyNoInteractions(access);
  var workspace=new HandlerMethod(controller,WorkspaceNavigationController.class.getDeclaredMethod("workspace",String.class,jakarta.servlet.http.HttpServletRequest.class));
  assertTrue(preparation.preHandle(request,new MockHttpServletResponse(),workspace));verify(access).prepareExternalRead(actor,meeting);
 }
 @Test void onlyDiscardReachesItsOwnAuthorizationAfterMeetingWithdrawal() throws Exception {
  var access = mock(JdbcMeetingAccess.class);
  var preparation = new ExternalReadPreparation(access, mock(javax.sql.DataSource.class),
      mock(ama.transcript.JdbcMaterialAuthority.class));
  var controller = mock(ImportDraftController.class);
  var actor = new Actor("owner", Status.ACTIVE, Set.of(Role.USER));
  var session = new ResolvedSession(actor, "x".repeat(43), Instant.now().plusSeconds(300));
  var meeting = "12345678-1234-1234-1234-123456789012";
  var discard = new MockHttpServletRequest("POST", "/api/v1/meetings/" + meeting + "/import-discard");
  discard.setAttribute(SessionSecurityFilter.SESSION, session);
  var requestType = jakarta.servlet.http.HttpServletRequest.class;
  var discardHandler = new HandlerMethod(controller,
      ImportDraftController.class.getDeclaredMethod("discard", String.class, byte[].class, requestType));

  assertTrue(preparation.preHandle(discard, new MockHttpServletResponse(), discardHandler));
  verifyNoInteractions(access);

  var read = new MockHttpServletRequest("GET", "/api/v1/meetings/" + meeting + "/import-draft");
  read.setAttribute(SessionSecurityFilter.SESSION, session);
  var readHandler = new HandlerMethod(controller,
      ImportDraftController.class.getDeclaredMethod("read", String.class, requestType));
  assertTrue(preparation.preHandle(read, new MockHttpServletResponse(), readHandler));
  verify(access).prepareExternalRead(actor, meeting);
 }
}
