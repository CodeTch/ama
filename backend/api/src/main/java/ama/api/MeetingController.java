package ama.api;

import ama.contract.IdentityContracts.Actor;
import ama.contract.IdentityContracts.ResolvedSession;
import ama.contract.MeetingContracts.*;
import ama.meeting.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class MeetingController {
    private final MeetingService meetings;
    private final RosterService rosters;
    private final long requestBudget;
    private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    MeetingController(MeetingService meetings,RosterService rosters,Environment environment){
        this.meetings=meetings;this.rosters=rosters;requestBudget=environment.getProperty("meeting.http.max-request-bytes",Long.class,8L*1024*1024);
        if(requestBudget<=0)throw new IllegalArgumentException("Request budget must be positive");
    }
    @PostMapping("/api/v1/meetings") ResponseEntity<Created> create(@RequestBody byte[] bytes,HttpServletRequest request){
        parse(bytes,Set.of());
        return ResponseEntity.status(201).body(meetings.create(actor(request),key(request),trace()));
    }
    @GetMapping("/api/v1/meetings/{id}") Saved read(@PathVariable String id,HttpServletRequest request){return meetings.read(actor(request),id);}
    @PutMapping("/api/v1/meetings/{id}/info") Mutation save(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest request){
        var body=parse(bytes,Set.of("expectedRevision","expectedRevisionToken","name","category","summary","occurredOn","rosterDraftIds","externalRosterNames"));
        String date=text(body,"occurredOn");LocalDate occurredOn;
        try{occurredOn=date==null?null:LocalDate.parse(date);}catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Invalid date");}
        var command=new SaveInfo(HttpRevisionTokens.expected(body),text(body,"name"),text(body,"category"),text(body,"summary"),occurredOn,strings(body,"rosterDraftIds",false),strings(body,"externalRosterNames",true));
        return meetings.save(actor(request),id,key(request),command,trace());
    }
    @PostMapping("/api/v1/meetings/{id}/roster-confirmations") Mutation confirm(@PathVariable String id,@RequestBody byte[] bytes,HttpServletRequest request){
        var body=parse(bytes,Set.of("expectedRevision","expectedRevisionToken"));
        return rosters.confirm(actor(request),id,key(request),new ConfirmRoster(HttpRevisionTokens.expected(body)),trace());
    }
    private JsonNode parse(byte[] bytes,Set<String> fields){
        if(bytes.length>requestBudget)throw new RequestTooLarge();
        try{
            var body=json.readTree(bytes);if(body==null||!body.isObject())throw new IllegalArgumentException("Object required");
            body.fieldNames().forEachRemaining(name->{if(!fields.contains(name))throw new IllegalArgumentException("Unknown field");});return body;
        }catch(java.io.IOException e){throw new IllegalArgumentException("Invalid JSON");}
    }
    private static Long revision(JsonNode body){var value=body.get("expectedRevision");if(value==null||!value.isIntegralNumber()||!value.canConvertToLong())throw new IllegalArgumentException("Revision required");return value.longValue();}
    private static String text(JsonNode body,String field){var value=body.get(field);if(value==null||value.isNull())return null;if(!value.isTextual())throw new IllegalArgumentException("String required");return value.textValue();}
    private static List<String> strings(JsonNode body,String field,boolean optional){
        var value=body.get(field);if(value==null||value.isNull()){if(optional)return List.of();throw new IllegalArgumentException("List required");}
        if(!value.isArray())throw new IllegalArgumentException("List required");var result=new ArrayList<String>();
        for(var item:value){if(!item.isTextual())throw new IllegalArgumentException("Identity/name must be text");result.add(item.textValue());}return result;
    }
    private static String key(HttpServletRequest request){var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("Exactly one idempotency key required");return keys.getFirst();}
    private static Actor actor(HttpServletRequest request){return ((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();}
    private static String trace(){return UUID.randomUUID().toString();}
    static final class RequestTooLarge extends RuntimeException {}
}
