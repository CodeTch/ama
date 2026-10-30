package ama.api;

import ama.contract.IdentityContracts.*;
import ama.meeting.JdbcTopics;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController class TopicController {
    private final JdbcTopics topics; private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    TopicController(JdbcTopics topics){this.topics=topics;}
    @GetMapping("/api/v1/topics") ResponseEntity<?> list(HttpServletRequest r){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(topics.list(actor(r)));}
    @PostMapping("/api/v1/topics") Map<String,Object> create(@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes,Set.of("name","description"));return topics.create(actor(r),key(r),text(b,"name"),text(b,"description"));}
    @PutMapping("/api/v1/topics/{id}") Map<String,Object> update(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes,Set.of("expectedRevisionToken","name","description"));return topics.update(actor(r),id,key(r),HttpRevisionTokens.expected(b),text(b,"name"),text(b,"description"));}
    @DeleteMapping("/api/v1/topics/{id}") Map<String,Object> delete(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes,Set.of("expectedRevisionToken"));return topics.delete(actor(r),id,key(r),HttpRevisionTokens.expected(b));}
    @PostMapping("/api/v1/meetings/{id}/topics") ResponseEntity<Void> assign(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest r){var b=parse(bytes,Set.of("topicId","expectedRevisionToken"));topics.assign(actor(r),id,text(b,"topicId"));return ResponseEntity.noContent().build();}
    @DeleteMapping("/api/v1/meetings/{id}/topics/{topic}") ResponseEntity<Void> unassign(@PathVariable String id,@PathVariable String topic,HttpServletRequest r){topics.unassign(actor(r),id,topic);return ResponseEntity.noContent().build();}
    private JsonNode parse(byte[] bytes,Set<String> fields){try{var b=json.readTree(bytes);if(b==null||!b.isObject()||b.size()!=fields.size())throw new IllegalArgumentException("Invalid topic body");b.fieldNames().forEachRemaining(x->{if(!fields.contains(x))throw new IllegalArgumentException("Unknown topic field");});return b;}catch(java.io.IOException e){throw new IllegalArgumentException("Invalid topic JSON");}}
    private static String text(JsonNode b,String n){var v=b.get(n);if(v==null||v.isNull())return null;if(!v.isTextual())throw new IllegalArgumentException("Text required");return v.textValue();}
    private static String key(HttpServletRequest r){var keys=Collections.list(r.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One idempotency key required");return keys.getFirst();}
    private static Actor actor(HttpServletRequest r){return ((ResolvedSession)r.getAttribute(SessionSecurityFilter.SESSION)).actor();}
}
