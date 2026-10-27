package ama.api;
import ama.access.MeetingAccess;import ama.contract.IdentityContracts.ResolvedSession;import ama.db.TransactionRunner;import ama.meeting.*;import ama.task.JdbcTaskQueue;import ama.transcript.JdbcWorkspaceTasks;import jakarta.servlet.http.HttpServletRequest;import java.util.Set;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController final class WorkspaceTasksController {
 private final JdbcWorkspaceTasks reader;private final TransactionRunner tx;private final MeetingAccess access;
 @org.springframework.beans.factory.annotation.Autowired WorkspaceTasksController(ProcessingConfiguration.Runtime runtime,JdbcTaskQueue queue,WorkspaceCursor cursors,WorkspaceReadBudget.Limits limits,TransactionRunner tx,MeetingAccess access){this(runtime.components()==null?null:new JdbcWorkspaceTasks(runtime.components().authority(),queue,cursors,limits),tx,access);}
 WorkspaceTasksController(JdbcWorkspaceTasks reader,TransactionRunner tx,MeetingAccess access){this.reader=reader;this.tx=tx;this.access=access;}
 @GetMapping("/api/v1/meetings/{meetingId}/tasks") ResponseEntity<JdbcWorkspaceTasks.Page> tasks(@PathVariable String meetingId,HttpServletRequest request){
  ama.contract.OutboxContracts.uuid(meetingId);request.getParameterMap().forEach((name,values)->{if(!Set.of("kind","limit","cursor").contains(name)||values.length!=1)throw new IllegalArgumentException("Invalid task query");});
  String kind=request.getParameter("kind");if(kind==null)kind="ALL";if(!Set.of("ALL","TRANSCRIPT_IMPORT_V1","MINUTES_GENERATE_V1","KNOWLEDGE_PUBLISH_V1").contains(kind))throw new IllegalArgumentException("Invalid task kind");
  String raw=request.getParameter("limit");int limit=20;if(raw!=null){if(!raw.matches("[1-9][0-9]?"))throw new IllegalArgumentException("Invalid limit");limit=Integer.parseInt(raw);}if(limit>50)throw new IllegalArgumentException("Invalid limit");String cursor=request.getParameter("cursor");if(cursor!=null&&(cursor.isEmpty()||cursor.length()>1536))throw new IllegalArgumentException("Invalid cursor");
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();if(reader==null){tx.required(()->{access.require(access.lock(actor,meetingId),MeetingAccess.Action.EDIT);return null;});throw new WorkspaceReadBudget.Unavailable();}
  return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reader.read(actor,meetingId,kind,limit,cursor));
 }
}
