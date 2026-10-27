package ama.api;
import ama.contract.IdentityContracts.ResolvedSession;import ama.contract.WorkspaceContracts.MeetingPage;import ama.meeting.JdbcWebMeetingProjectionReader;
import jakarta.servlet.http.HttpServletRequest;import java.util.Set;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController class MeetingListController {
 private final JdbcWebMeetingProjectionReader reader;MeetingListController(JdbcWebMeetingProjectionReader reader){this.reader=reader;}
 @GetMapping("/api/v1/meetings") ResponseEntity<MeetingPage> list(HttpServletRequest request){
  request.getParameterMap().forEach((name,values)->{if(!Set.of("scope","limit","cursor").contains(name)||values.length!=1)throw new IllegalArgumentException("Invalid list query");});
  String scope=request.getParameter("scope");if(scope==null)scope="owned";if(!Set.of("owned","joined").contains(scope))throw new IllegalArgumentException("Invalid scope");
  String raw=request.getParameter("limit");int limit=20;if(raw!=null){if(!raw.matches("[1-9][0-9]?"))throw new IllegalArgumentException("Invalid limit");limit=Integer.parseInt(raw);}if(limit>50)throw new IllegalArgumentException("Invalid limit");
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reader.list(actor,scope,limit,request.getParameter("cursor")));
 }
}
