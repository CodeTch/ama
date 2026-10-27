package ama.api;
import ama.contract.IdentityContracts.ResolvedSession;import ama.transcript.JdbcWorkspaceOverview;import ama.task.JdbcTaskQueue;import ama.meeting.WorkspaceCursor;import ama.meeting.WorkspaceReadBudget;import java.util.*;import ama.meeting.JdbcWorkspaceNavigationReader;import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
/** Complete bounded navigation when genuinely composed; producer content/readiness still use fresh dedicated reads. */
@RestController final class WorkspaceNavigationController {
 private final JdbcWorkspaceNavigationReader reader;private final JdbcWorkspaceOverview overview;
 WorkspaceNavigationController(JdbcWorkspaceNavigationReader reader){this(reader,null);}
 WorkspaceNavigationController(JdbcWorkspaceNavigationReader reader,JdbcWorkspaceOverview overview){this.reader=reader;this.overview=overview;}
 @org.springframework.beans.factory.annotation.Autowired WorkspaceNavigationController(JdbcWorkspaceNavigationReader reader,ProcessingConfiguration.Runtime runtime,JdbcTaskQueue queue,WorkspaceCursor cursors,WorkspaceReadBudget.Limits limits){this(reader,runtime.components()==null?null:new JdbcWorkspaceOverview(runtime.components().authority(),queue,cursors,limits));}
 @GetMapping("/api/v1/meetings/{meetingId}/workspace") ResponseEntity<Object> workspace(@PathVariable String meetingId,HttpServletRequest request){
  ama.contract.OutboxContracts.uuid(meetingId);if(!request.getParameterMap().isEmpty())throw new IllegalArgumentException("Workspace does not accept query parameters");
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();if(overview!=null)return ResponseEntity.ok().header("Cache-Control","private, no-store").body(overview.read(actor,meetingId));
  var value=reader.read(actor,meetingId);var fallback=new LinkedHashMap<String,Object>();fallback.put("meetingId",value.meetingId());fallback.put("revisionToken",value.revisionToken());fallback.put("accessRevisionToken",value.accessRevisionToken());fallback.put("stage",value.stage());fallback.put("relation",value.relation());fallback.put("navigationState",value.navigationState());fallback.put("contentEligibility",value.contentEligibility());fallback.put("saved",value.saved());if(value.relation().equals("OWNER")){fallback.put("privateSectionsState","UNAVAILABLE");fallback.put("privateSectionsReason","PROCESSING_RUNTIME_UNAVAILABLE");}
  return ResponseEntity.ok().header("Cache-Control","private, no-store").body(Collections.unmodifiableMap(fallback));
 }
}
