package ama.api;

import ama.contract.IdentityContracts.*;
import ama.meeting.JdbcFeedback;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController class FeedbackController {
    private final JdbcFeedback feedback; private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    FeedbackController(JdbcFeedback feedback){this.feedback=feedback;}
    @GetMapping("/api/v1/feedback") ResponseEntity<?> list(HttpServletRequest r){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(feedback.list(actor(r),r.getParameter("cursor")));}
    @PostMapping("/api/v1/feedback") ResponseEntity<?> create(@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes);return ResponseEntity.status(201).body(feedback.create(actor(r),key(r),text(b,"meetingId"),text(b,"category"),text(b,"comment")));}
    @GetMapping("/api/v1/feedback/review-queue") ResponseEntity<?> queue(HttpServletRequest r){query(r);return read(feedback.reviewQueue(actor(r),r.getParameter("cursor")));}
    @GetMapping("/api/v1/feedback/summaries") ResponseEntity<?> summaries(HttpServletRequest r){summaryQuery(r);return read(feedback.summaries(actor(r),r.getParameter("cursor"),r.getParameter("state"),"true".equals(r.getParameter("reviewing"))));}
    @GetMapping("/api/v1/feedback/{id}") ResponseEntity<?> detail(@PathVariable String id,HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw new IllegalArgumentException("Unexpected feedback query");return read(feedback.detail(actor(r),id));}
    @PostMapping("/api/v1/feedback/{id}/supplements") ResponseEntity<?> supplement(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes,Set.of("expectedRevisionToken","text"));return read(feedback.supplement(actor(r),id,key(r),HttpRevisionTokens.expected(b),text(b,"text")));}
    @PostMapping("/api/v1/feedback/{id}/review") ResponseEntity<?> review(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes,Set.of("expectedRevisionToken","decision","reason"));return read(feedback.review(actor(r),id,key(r),HttpRevisionTokens.expected(b),text(b,"decision"),text(b,"reason")));}
    @PostMapping("/api/v1/feedback/{id}/review/undo") ResponseEntity<?> undo(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes,Set.of("expectedRevisionToken"));return read(feedback.undoReview(actor(r),id,key(r),HttpRevisionTokens.expected(b)));}
    private static ResponseEntity<?> read(Object value){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(value);}
    private static void query(HttpServletRequest r){r.getParameterMap().forEach((name,values)->{if(values.length!=1||!Set.of("cursor","limit").contains(name)||name.equals("limit")&&!values[0].equals("20"))throw new IllegalArgumentException("Invalid feedback query");});}
    private static void summaryQuery(HttpServletRequest r){r.getParameterMap().forEach((name,values)->{if(values.length!=1||!Set.of("cursor","limit","state","reviewing").contains(name)||name.equals("limit")&&!values[0].equals("20")||name.equals("reviewing")&&!Set.of("true","false").contains(values[0]))throw new IllegalArgumentException("Invalid feedback query");});}
    private JsonNode parse(byte[] bytes){return parse(bytes,Set.of("meetingId","category","comment"));}
    private JsonNode parse(byte[] bytes,Set<String> fields){if(bytes.length>32768)throw new IllegalArgumentException("Bounded feedback body required");try{var b=json.readTree(bytes);if(b==null||!b.isObject()||b.size()!=fields.size())throw new IllegalArgumentException("Invalid feedback body");b.fieldNames().forEachRemaining(name->{if(!fields.contains(name))throw new IllegalArgumentException("Unknown feedback field");});return b;}catch(java.io.IOException e){throw new IllegalArgumentException("Invalid feedback JSON");}}
    private static String text(JsonNode b,String n){var v=b.get(n);if(v==null||!v.isTextual())throw new IllegalArgumentException("Text required");return v.textValue();}
    private static String key(HttpServletRequest r){var keys=Collections.list(r.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One idempotency key required");return keys.getFirst();}
    private static Actor actor(HttpServletRequest r){return ((ResolvedSession)r.getAttribute(SessionSecurityFilter.SESSION)).actor();}
}
