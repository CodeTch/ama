package ama.api;
import ama.contract.IdentityContracts.*;
import ama.contract.GovernanceContracts.*;
import ama.governance.*;
import ama.mail.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="operations.enabled",havingValue="true")
class OperationsController {
 private final JdbcGovernance operations;private final JdbcCorpusFeedback corpus;private final JdbcMailJobs mail;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxStringLength(1_400_000).maxNestingDepth(12).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 OperationsController(JdbcGovernance operations,JdbcCorpusFeedback corpus,JdbcMailJobs mail){this.operations=operations;this.corpus=corpus;this.mail=mail;}
 @GetMapping("/api/v1/operations/capabilities") ResponseEntity<?> capabilities(HttpServletRequest r){query(r);return ok(operations.capabilities(actor(r)));}
 @GetMapping("/api/v1/meetings/{id}/mail-context") ResponseEntity<?> context(@PathVariable String id,HttpServletRequest r){query(r);return ok(mail.context(actor(r),id));}
 @GetMapping("/api/v1/operations/voiceprints") ResponseEntity<?> voices(HttpServletRequest r){query(r);return ok(operations.voices(actor(r)));}
 @PostMapping("/api/v1/operations/voiceprints") ResponseEntity<?> enroll(@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"consentReference","expiresAt","wavBase64");return ok(operations.enroll(actor(r),key(r),text(b,"consentReference"),text(b,"expiresAt"),GovernanceHttpProvider.decode(text(b,"wavBase64"))));}
 @PostMapping("/api/v1/operations/voiceprints/{id}/review") ResponseEntity<?> review(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"expectedRevisionToken","decision","reason");return ok(operations.reviewVoice(actor(r),id,key(r),revision(b),text(b,"decision"),text(b,"reason")));}
 @PostMapping("/api/v1/operations/voiceprints/{id}/revocation") ResponseEntity<?> revoke(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"expectedRevisionToken","reason");return ok(operations.revoke(actor(r),id,key(r),revision(b),text(b,"reason")));}
 @PostMapping("/api/v1/operations/voiceprints/match") ResponseEntity<?> match(@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"wavBase64");return ok(operations.match(actor(r),GovernanceHttpProvider.decode(text(b,"wavBase64"))));}
 @GetMapping("/api/v1/operations/datasets") ResponseEntity<?> datasets(HttpServletRequest r){query(r);return ok(operations.datasets(actor(r)));}
 @PostMapping("/api/v1/operations/datasets") ResponseEntity<?> dataset(@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"version","samples");try{return ok(operations.dataset(actor(r),key(r),text(b,"version"),List.of(json.treeToValue(b.get("samples"),Sample[].class))));}catch(JsonProcessingException bad){throw new IllegalArgumentException("Invalid samples");}}
 @GetMapping("/api/v1/operations/evaluations") ResponseEntity<?> evaluations(HttpServletRequest r){query(r);return ok(operations.evaluations(actor(r)));}
 @PostMapping("/api/v1/operations/evaluations") ResponseEntity<?> evaluate(@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"datasetId","configuration");return ok(operations.evaluate(actor(r),key(r),text(b,"datasetId"),config(b)));}
 @GetMapping("/api/v1/operations/evaluations/{id}") ResponseEntity<?> evaluation(@PathVariable String id,HttpServletRequest r){query(r);return ok(operations.evaluation(actor(r),id));}
 @PostMapping("/api/v1/operations/evaluations/{id}/review") ResponseEntity<?> evaluationReview(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"reason");return ok(operations.reviewEvaluation(actor(r),id,key(r),text(b,"reason")));}
 @GetMapping("/api/v1/operations/configuration") ResponseEntity<?> configuration(HttpServletRequest r){query(r);return ok(operations.configuration(actor(r)));}
 @PostMapping("/api/v1/operations/configuration-releases") ResponseEntity<?> release(@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"expectedRevisionToken","evaluationId","configuration","reason");return ok(operations.release(actor(r),key(r),revision(b),text(b,"evaluationId"),config(b),text(b,"reason")));}
 @PostMapping("/api/v1/operations/configuration-rollbacks") ResponseEntity<?> rollback(@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"expectedRevisionToken","targetRevisionToken","reason");return ok(operations.rollback(actor(r),key(r),revision(b),revision(b,"targetRevisionToken"),text(b,"reason")));}
 @PostMapping("/api/v1/meetings/{id}/feedback") ResponseEntity<?> corpus(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"sourceRevisionToken","segmentIds","category","comment");try{return ok(corpus.submit(actor(r),key(r),id,revision(b,"sourceRevisionToken"),List.of(json.treeToValue(b.get("segmentIds"),String[].class)),text(b,"category"),text(b,"comment")));}catch(JsonProcessingException bad){throw new IllegalArgumentException("Invalid segments");}}
 @GetMapping("/api/v1/operations/corpus") ResponseEntity<?> corpus(HttpServletRequest r){query(r);return ok(corpus.approved(actor(r)));}
 @PostMapping("/api/v1/operations/corpus/{id}/withdrawal") ResponseEntity<?> withdraw(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){body(bytes);return ok(corpus.withdraw(actor(r),id,key(r)));}
 @PostMapping("/api/v1/meetings/{id}/mail") ResponseEntity<?> enqueue(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=body(bytes,"sourceRevisionToken","publicationId","recipientEnterpriseIds");try{return ResponseEntity.accepted().body(mail.enqueue(actor(r),key(r),id,revision(b,"sourceRevisionToken"),text(b,"publicationId"),List.of(json.treeToValue(b.get("recipientEnterpriseIds"),String[].class))));}catch(JsonProcessingException bad){throw new IllegalArgumentException("Invalid recipients");}}
 @GetMapping("/api/v1/meetings/{id}/mail") ResponseEntity<?> history(@PathVariable String id,HttpServletRequest r){query(r);return ok(mail.history(actor(r),id));}
 @GetMapping("/api/v1/operations/mail/{id}") ResponseEntity<?> job(@PathVariable String id,HttpServletRequest r){query(r);return ok(mail.get(actor(r),id));}
 private ModelConfig config(JsonNode b){var node=b.get("configuration");if(node==null||!node.isObject()||node.size()!=3||!node.has("modelVersion")||!node.has("promptVersion")||!node.has("voiceThreshold"))throw new IllegalArgumentException("Exact configuration fields required");try{return json.treeToValue(b.get("configuration"),ModelConfig.class);}catch(JsonProcessingException bad){throw new IllegalArgumentException("Whitelisted configuration required");}}
 private JsonNode body(byte[] bytes,String... fields){if(bytes.length>8*1024*1024)throw new TooLarge();try{var b=json.readTree(bytes);var allowed=Set.of(fields);if(b==null||!b.isObject()||b.size()!=allowed.size())throw new IllegalArgumentException("Exact fields required");b.fieldNames().forEachRemaining(name->{if(!allowed.contains(name))throw new IllegalArgumentException("Unknown field");});return b;}catch(java.io.IOException bad){throw new IllegalArgumentException("Invalid JSON");}}
 private static String text(JsonNode b,String name){var v=b.get(name);if(v==null||!v.isTextual())throw new IllegalArgumentException("Text field required");return v.textValue();}
 private static long revision(JsonNode b){return revision(b,"expectedRevisionToken");}
 private static long revision(JsonNode b,String name){String v=text(b,name);if(!v.matches("0|[1-9][0-9]{0,18}"))throw new IllegalArgumentException("Exact revision token required");try{long n=Long.parseLong(v);if(n==Long.MAX_VALUE)throw new IllegalArgumentException("Revision exhausted");return n;}catch(NumberFormatException bad){throw new IllegalArgumentException("Exact revision required");}}
 private static void query(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw new IllegalArgumentException("No query parameters accepted");}
 private static String key(HttpServletRequest r){var keys=Collections.list(r.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("Single original key required");return keys.getFirst();}
 private static Actor actor(HttpServletRequest r){return ((ResolvedSession)r.getAttribute(SessionSecurityFilter.SESSION)).actor();}
 private static ResponseEntity<?> ok(Object value){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(value);}
 static class TooLarge extends RuntimeException{}
}
