package ama.api;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.DeleteMeeting;
import ama.meeting.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController final class MeetingDeletionController {
 private final MeetingService meetings;private final JdbcMeetingDeletion deletions;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 MeetingDeletionController(MeetingService meetings,JdbcMeetingDeletion deletions){this.meetings=meetings;this.deletions=deletions;}
 @DeleteMapping("/api/v1/meetings/{id}") ResponseEntity<JdbcMeetingDeletion.Status> delete(@PathVariable String id,HttpServletRequest request)throws IOException{
  noQuery(request);ama.contract.MinutesContracts.uuid(id);byte[] bytes=request.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new IllegalArgumentException("Deletion request too large");JsonNode body;
  try{body=json.readTree(bytes);}catch(JsonProcessingException invalid){throw new IllegalArgumentException("Invalid deletion JSON");}
  if(body==null||!body.isObject()||body.isEmpty())throw new IllegalArgumentException("Deletion revision required");body.fieldNames().forEachRemaining(field->{if(!Set.of("expectedRevisionToken","expectedRevision").contains(field))throw new IllegalArgumentException("Unknown deletion field");});
  var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One original deletion key required");
  meetings.delete(actor(request),id,keys.getFirst(),new DeleteMeeting(HttpRevisionTokens.expected(body)),UUID.randomUUID().toString());
  return ResponseEntity.accepted().header("Cache-Control","private, no-store").body(deletions.forMeeting(actor(request),id));
 }
 @GetMapping("/api/v1/deletions/{id}") ResponseEntity<JdbcMeetingDeletion.Status> status(@PathVariable String id,HttpServletRequest request){noQuery(request);ama.contract.MinutesContracts.uuid(id);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(deletions.status(actor(request),id));}
 private static Actor actor(HttpServletRequest request){if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession s))throw new ama.access.MeetingAccess.AccessDeniedException();return s.actor();}
 private static void noQuery(HttpServletRequest request){if(!request.getParameterMap().isEmpty())throw new IllegalArgumentException("Deletion query unsupported");}
}
