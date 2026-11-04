package ama.api;
import ama.contract.IdentityContracts.*;
import ama.contract.TranscriptContracts.Failure;
import ama.transcript.JdbcMaterialEnhancements;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/meetings/{meetingId}/transcript/material-enhancements")
class MaterialEnhancementController {
 private final ObjectProvider<JdbcMaterialEnhancements> services;
 MaterialEnhancementController(ObjectProvider<JdbcMaterialEnhancements> services){this.services=services;}
 private JdbcMaterialEnhancements service(){var service=services.getIfAvailable();if(service==null)throw new Failure(503,"MATERIAL_ENHANCEMENT_NOT_INSTALLED");return service;}
 @PostMapping ResponseEntity<?> generate(@PathVariable String meetingId,HttpServletRequest request)throws java.io.IOException{
  var body=body(request);var fields=new HashSet<String>();body.fieldNames().forEachRemaining(fields::add);if(!fields.equals(Set.of("expectedRevisionToken","sourceRevisionId","materialSnapshotId"))||!body.path("sourceRevisionId").isTextual()||!(body.path("materialSnapshotId").isNull()||body.path("materialSnapshotId").isTextual()))throw new IllegalArgumentException("Exact enhancement input required");
  return response(service().generate(actor(request),meetingId,key(request),HttpRevisionTokens.expected(body),body.path("sourceRevisionId").asText(),body.path("materialSnapshotId").isNull()?null:body.path("materialSnapshotId").asText()));
 }
 @GetMapping("/{candidateId}") ResponseEntity<?> read(@PathVariable String meetingId,@PathVariable String candidateId,HttpServletRequest request)throws java.io.IOException{return response(service().read(actor(request),meetingId,candidateId));}
 @PostMapping("/{candidateId}/adoption") ResponseEntity<?> adopt(@PathVariable String meetingId,@PathVariable String candidateId,HttpServletRequest request)throws java.io.IOException{var body=body(request);if(!body.isObject()||!body.isEmpty())throw new IllegalArgumentException("Empty adoption body required");return response(service().adopt(actor(request),meetingId,candidateId,key(request)));}
 private static com.fasterxml.jackson.databind.JsonNode body(HttpServletRequest request)throws java.io.IOException{try(var stream=request.getInputStream()){byte[] bytes=stream.readNBytes(4097);if(bytes.length>4096)throw new IllegalArgumentException("Enhancement envelope limit");var mapper=new com.fasterxml.jackson.databind.ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var node=mapper.readTree(bytes);if(node==null||!node.isObject())throw new IllegalArgumentException("Object required");return node;}}
 private static String key(HttpServletRequest request){var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One key required");ama.contract.MinutesContracts.uuid(keys.getFirst());return keys.getFirst();}
 private static Actor actor(HttpServletRequest request){return ((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();}
 private static ResponseEntity<byte[]> response(Object result)throws java.io.IOException{return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(result,8388608));}
}
