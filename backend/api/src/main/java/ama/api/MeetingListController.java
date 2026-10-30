package ama.api;
import ama.contract.IdentityContracts.ResolvedSession;import ama.contract.WorkspaceContracts.MeetingPage;import ama.meeting.JdbcWebMeetingProjectionReader;
import ama.meeting.MeetingListFilter;import java.time.LocalDate;import java.util.Arrays;import java.util.TreeSet;
import jakarta.servlet.http.HttpServletRequest;import java.util.Set;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController class MeetingListController {
 private final JdbcWebMeetingProjectionReader reader;MeetingListController(JdbcWebMeetingProjectionReader reader){this.reader=reader;}
 @GetMapping("/api/v1/meetings") ResponseEntity<MeetingPage> list(HttpServletRequest request){
  request.getParameterMap().forEach((name,values)->{if(!Set.of("scope","limit","cursor","q","stages","categories","from","to").contains(name)||values.length!=1)throw new IllegalArgumentException("Invalid list query");});
  String scope=request.getParameter("scope");if(scope==null)scope="owned";if(!Set.of("owned","joined").contains(scope))throw new IllegalArgumentException("Invalid scope");
  String raw=request.getParameter("limit");int limit=20;if(raw!=null){if(!raw.matches("[1-9][0-9]?"))throw new IllegalArgumentException("Invalid limit");limit=Integer.parseInt(raw);}if(limit>50)throw new IllegalArgumentException("Invalid limit");
  var filter=new MeetingListFilter(request.getParameter("q")==null?"":request.getParameter("q"),values(request.getParameter("stages")),values(request.getParameter("categories")),date(request.getParameter("from")),date(request.getParameter("to")));
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();return ResponseEntity.ok().header("Cache-Control","private, no-store").body(filter.equals(MeetingListFilter.NONE)?reader.list(actor,scope,limit,request.getParameter("cursor")):reader.list(actor,scope,limit,request.getParameter("cursor"),filter));
 }
 private static Set<String> values(String text){if(text==null||text.isEmpty())return Set.of();var parts=text.split(",",-1);if(parts.length>32||Arrays.stream(parts).anyMatch(String::isBlank))throw new IllegalArgumentException("Invalid filter values");var values=new TreeSet<>(Arrays.asList(parts));if(values.size()!=parts.length)throw new IllegalArgumentException("Duplicate filter values");return values;}
 private static LocalDate date(String value){if(value==null||value.isEmpty())return null;try{var result=LocalDate.parse(value);if(!result.toString().equals(value))throw new IllegalArgumentException("Invalid date");return result;}catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Invalid date");}}
}
