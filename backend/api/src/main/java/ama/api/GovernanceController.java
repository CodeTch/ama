package ama.api;

import ama.contract.IdentityContracts.*;
import ama.transcript.JdbcPublicationGovernance;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/publications/{publicationId}")
@ConditionalOnProperty(name="publication.governance-enabled",havingValue="true")
final class GovernanceController {
 private final JdbcPublicationGovernance service;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(3).maxStringLength(8192).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 GovernanceController(JdbcPublicationGovernance service){this.service=service;}
 @GetMapping("/governance-capabilities") ResponseEntity<Map<String,Object>> capabilities(@PathVariable String meetingId,@PathVariable String publicationId,HttpServletRequest request){query(request);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(service.capabilities(actor(request),meetingId,publicationId));}
 @PostMapping("/withdrawal") ResponseEntity<Map<String,Object>> withdraw(@PathVariable String meetingId,@PathVariable String publicationId,HttpServletRequest request)throws IOException{return accept(meetingId,publicationId,"WITHDRAW",request);}
 @PostMapping("/obsolescence") ResponseEntity<Map<String,Object>> obsolete(@PathVariable String meetingId,@PathVariable String publicationId,HttpServletRequest request)throws IOException{return accept(meetingId,publicationId,"OBSOLETE",request);}
 @GetMapping("/governance/{jobId}") ResponseEntity<Map<String,Object>> status(@PathVariable String meetingId,@PathVariable String publicationId,@PathVariable String jobId,HttpServletRequest request){query(request);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(service.status(actor(request),meetingId,publicationId,jobId));}
 @GetMapping("/governance") ResponseEntity<List<Map<String,Object>>> history(@PathVariable String meetingId,@PathVariable String publicationId,HttpServletRequest request){query(request);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(service.history(actor(request),meetingId,publicationId));}
 private ResponseEntity<Map<String,Object>> accept(String meeting,String publication,String action,HttpServletRequest request)throws IOException{
  query(request);byte[] raw;try(var in=request.getInputStream()){raw=in.readNBytes(32769);}if(raw.length>32768)throw new ama.contract.TranscriptContracts.Failure(413,"GOVERNANCE_REQUEST_TOO_LARGE");
  JsonNode body;try{body=json.readTree(raw);}catch(JsonProcessingException malformed){throw new IllegalArgumentException("Invalid governance JSON");}
  if(body==null||!body.isObject()||body.size()!=2||!body.path("reason").isTextual()||!body.path("expectedRevisionToken").isTextual())throw new IllegalArgumentException("Exact governance command required");
  var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One original governance key required");
  return ResponseEntity.accepted().header("Cache-Control","private, no-store").body(service.accept(actor(request),meeting,publication,keys.getFirst(),HttpRevisionTokens.expected(body),action,body.path("reason").textValue()));
 }
 private static Actor actor(HttpServletRequest request){if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession session))throw new ama.access.MeetingAccess.AccessDeniedException();return session.actor();}
 private static void query(HttpServletRequest request){if(!request.getParameterMap().isEmpty())throw new IllegalArgumentException("Governance query unsupported");}
}
