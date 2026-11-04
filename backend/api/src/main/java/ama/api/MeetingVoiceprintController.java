package ama.api;
import ama.governance.JdbcMeetingVoiceprintApplications;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController
@ConditionalOnProperty(name="operations.enabled",havingValue="true")
class MeetingVoiceprintController {
 private final JdbcMeetingVoiceprintApplications voices;private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 MeetingVoiceprintController(JdbcMeetingVoiceprintApplications voices){this.voices=voices;}
 @PostMapping("/api/v1/operations/voiceprints/{id}/resampling") ResponseEntity<?> resample(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest request){
  if(bytes.length>16384||!request.getParameterMap().isEmpty())throw new IllegalArgumentException("Bounded resampling request required");try{var b=json.readTree(bytes);if(b==null||!b.isObject()||b.size()!=2||!b.path("reason").isTextual()||!b.has("expectedRevisionToken"))throw new IllegalArgumentException("Revision and reason required");var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One original key required");var actor=((ama.contract.IdentityContracts.ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();return ResponseEntity.ok().header("Cache-Control","private, no-store").body(voices.resample(actor,id,keys.getFirst(),HttpRevisionTokens.expected(b),b.path("reason").textValue()));}catch(java.io.IOException bad){throw new IllegalArgumentException("Invalid resampling JSON");}
 }
 @PostMapping("/api/v1/meetings/{id}/voiceprint-applications") ResponseEntity<?> submit(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest request){
  if(bytes.length>32768)throw new IllegalArgumentException("Bounded application required");try{var b=json.readTree(bytes);var fields=Set.of("sourceRevisionToken","segmentIds","subjectId","subjectName");if(b==null||!b.isObject()||b.size()!=fields.size())throw new IllegalArgumentException("Application fields required");b.fieldNames().forEachRemaining(n->{if(!fields.contains(n))throw new IllegalArgumentException("Unknown application field");});var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One original key required");
   if(!b.path("subjectId").isTextual()||!b.path("subjectName").isTextual()||!b.path("segmentIds").isArray())throw new IllegalArgumentException("Application identity required");
   var actor=((ama.contract.IdentityContracts.ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();return ResponseEntity.ok().header("Cache-Control","private, no-store").body(voices.submit(actor,keys.getFirst(),id,HttpRevisionTokens.expected(json.createObjectNode().set("expectedRevisionToken",b.get("sourceRevisionToken"))),List.of(json.treeToValue(b.get("segmentIds"),String[].class)),b.path("subjectId").textValue(),b.path("subjectName").textValue()));
  }catch(java.io.IOException bad){throw new IllegalArgumentException("Invalid application JSON");}
 }
}
