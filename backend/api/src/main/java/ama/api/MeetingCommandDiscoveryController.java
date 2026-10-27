package ama.api;
import ama.contract.IdentityContracts.ResolvedSession;import ama.contract.WorkspaceContracts.CommandLookup;import ama.meeting.JdbcMeetingCommandDiscovery;
import com.fasterxml.jackson.core.*;import com.fasterxml.jackson.databind.*;import jakarta.servlet.http.HttpServletRequest;
import java.util.*;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController class MeetingCommandDiscoveryController {
 private final JdbcMeetingCommandDiscovery reader;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxStringLength(8192).maxNestingDepth(8).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 MeetingCommandDiscoveryController(JdbcMeetingCommandDiscovery reader){this.reader=reader;}
 @PostMapping("/api/v1/meetings/command-discovery") ResponseEntity<CommandLookup> lookup(@RequestBody byte[] bytes,HttpServletRequest request){
  if(!request.getParameterMap().isEmpty()||bytes.length>16384)throw new IllegalArgumentException("Invalid discovery request");
  try{var body=json.readTree(bytes);if(body==null||!body.isObject()||body.size()!=3)throw new IllegalArgumentException("Invalid discovery body");for(String field:List.of("operation","resource","key"))if(!body.has(field)||!body.get(field).isTextual())throw new IllegalArgumentException("Invalid discovery field");
   var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();return ResponseEntity.ok().header("Cache-Control","private, no-store").body(reader.lookup(actor,body.get("operation").textValue(),body.get("resource").textValue(),body.get("key").textValue()));
  }catch(java.io.IOException error){throw new IllegalArgumentException("Invalid discovery JSON");}
 }
}
