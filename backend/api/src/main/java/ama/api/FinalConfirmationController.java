package ama.api;
import ama.contract.IdentityContracts.*;
import ama.transcript.*;
import ama.access.MeetingAccess;
import ama.db.TransactionRunner;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/meetings/{meetingId}/final-confirmations")
final class FinalConfirmationController {
 private final MinutesConfiguration.Runtime runtime;private final JdbcMinutesCommandReceipts receipts;private final TransactionRunner tx;private final MeetingAccess access;private final JdbcPublicationQueries queries;
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(1024).maxNumberLength(19).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 FinalConfirmationController(MinutesConfiguration.Runtime runtime,JdbcMinutesCommandReceipts receipts,TransactionRunner tx,MeetingAccess access){this(runtime,receipts,tx,access,null);}
 @org.springframework.beans.factory.annotation.Autowired FinalConfirmationController(MinutesConfiguration.Runtime runtime,JdbcMinutesCommandReceipts receipts,TransactionRunner tx,MeetingAccess access,JdbcPublicationQueries queries){this.runtime=runtime;this.receipts=receipts;this.tx=tx;this.access=access;this.queries=queries;}
 @GetMapping ResponseEntity<byte[]> history(@PathVariable String meetingId,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor,HttpServletRequest request)throws IOException{if(queries==null)throw new ama.contract.TranscriptContracts.Failure(503,"PUBLICATION_READ_UNAVAILABLE");return json(queries.finalHistory(actor(request),meetingId,limit,cursor),200);}
 @GetMapping("/readiness") ResponseEntity<byte[]> readiness(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return json(require(request,meetingId).readiness(actor(request),meetingId),200);}
 @GetMapping("/current") ResponseEntity<byte[]> current(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return json(require(request,meetingId).current(actor(request),meetingId),200);}
 @GetMapping("/{confirmationId}") ResponseEntity<byte[]> detail(@PathVariable String meetingId,@PathVariable String confirmationId,HttpServletRequest request)throws IOException{return json(require(request,meetingId).detail(actor(request),meetingId,confirmationId),200);}
 @PostMapping ResponseEntity<byte[]> confirm(@PathVariable String meetingId,HttpServletRequest request)throws IOException{
  byte[] bytes;try(var in=request.getInputStream()){bytes=in.readNBytes(65537);}if(bytes.length>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");JsonNode node;
  try{node=JSON.readTree(bytes);}catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw new IllegalArgumentException("Malformed final command");}
  if(node==null||!node.isObject())throw new IllegalArgumentException("Object required");node.fieldNames().forEachRemaining(name->{if(!Set.of("expectedRevision","expectedRevisionToken","optionalReviewIds").contains(name))throw new IllegalArgumentException("Unknown final field");});long expected=HttpRevisionTokens.expected(node);
  var selected=node.get("optionalReviewIds");if(selected==null||!selected.isArray()||selected.size()>256)throw new IllegalArgumentException("Bounded optional review array required");var optional=new ArrayList<String>();for(var id:selected){if(!id.isTextual())throw new IllegalArgumentException("Review id required");ama.contract.MinutesContracts.uuid(id.textValue());optional.add(id.textValue());}optional.sort(String::compareTo);if(new HashSet<>(optional).size()!=optional.size())throw new IllegalArgumentException("Duplicate review id");
  var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One idempotency key required");String key=keys.getFirst();JdbcProcessingIntake.key(key);
  var replay=receipts.lookup(actor(request),meetingId,"FINAL_CONFIRM",key,expected,optional.toArray(String[]::new));if(replay.isPresent())return json(replay.orElseThrow(),200);
  var result=require(request,meetingId).confirm(actor(request),meetingId,key,expected,optional,UUID.randomUUID().toString());return json(result,Boolean.TRUE.equals(result.get("replayed"))?200:201);
 }
 private JdbcFinalConfirmations require(HttpServletRequest request,String meeting){ama.contract.MinutesContracts.uuid(meeting);if(runtime.finals()!=null)return runtime.finals();tx.required(()->{access.require(access.lock(actor(request),meeting),MeetingAccess.Action.EDIT);return null;});throw new ama.contract.TranscriptContracts.Failure(503,"PUBLICATION_NOT_INSTALLED");}
 private static Actor actor(HttpServletRequest request){return ((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();}
 private ResponseEntity<byte[]> json(Object value,int status)throws IOException{return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(value,runtime.responseBytes()));}
}
