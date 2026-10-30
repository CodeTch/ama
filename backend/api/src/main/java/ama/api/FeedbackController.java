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
    private JsonNode parse(byte[] bytes){try{var b=json.readTree(bytes);if(b==null||!b.isObject()||b.size()!=3||!b.has("meetingId")||!b.has("category")||!b.has("comment"))throw new IllegalArgumentException("Invalid feedback body");return b;}catch(java.io.IOException e){throw new IllegalArgumentException("Invalid feedback JSON");}}
    private static String text(JsonNode b,String n){var v=b.get(n);if(v==null||!v.isTextual())throw new IllegalArgumentException("Text required");return v.textValue();}
    private static String key(HttpServletRequest r){var keys=Collections.list(r.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One idempotency key required");return keys.getFirst();}
    private static Actor actor(HttpServletRequest r){return ((ResolvedSession)r.getAttribute(SessionSecurityFilter.SESSION)).actor();}
}
