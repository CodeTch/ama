package ama.api;
import ama.contract.IdentityContracts.ResolvedSession;import ama.meeting.JdbcWorkspaceNavigationReader;import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
/** Saved navigation only. Producer content and private sections require their separate fresh reads. */
@RestController final class WorkspaceNavigationController {
 private final JdbcWorkspaceNavigationReader reader;
 WorkspaceNavigationController(JdbcWorkspaceNavigationReader reader){this.reader=reader;}
 @GetMapping("/api/v1/meetings/{meetingId}/workspace") ResponseEntity<JdbcWorkspaceNavigationReader.Navigation> workspace(@PathVariable String meetingId,HttpServletRequest request){
  ama.contract.OutboxContracts.uuid(meetingId);if(!request.getParameterMap().isEmpty())throw new IllegalArgumentException("Workspace does not accept query parameters");
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reader.read(actor,meetingId));
 }
}
