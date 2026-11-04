package ama.api;

import ama.contract.IdentityContracts.ResolvedSession;
import ama.transcript.JdbcRecordingProcessing;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@ConditionalOnBean(JdbcRecordingProcessing.class)
@RequestMapping("/api/v1/recordings/{sessionId}/processing")
class RecordingProcessingController {
    private final JdbcRecordingProcessing service;
    RecordingProcessingController(JdbcRecordingProcessing service){this.service=service;}
    @GetMapping ResponseEntity<?> read(@PathVariable String sessionId,HttpServletRequest request){return response(service.read(actor(request),sessionId));}
    @PostMapping ResponseEntity<?> accept(@PathVariable String sessionId,HttpServletRequest request)throws java.io.IOException{
        var body=RecordingJsonObject.read(request,4096);var keys=new HashSet<String>();body.fieldNames().forEachRemaining(keys::add);
        if(!keys.equals(Set.of("expectedRevisionToken","materialSnapshotId"))||!(body.path("materialSnapshotId").isNull()||body.path("materialSnapshotId").isTextual()))throw new IllegalArgumentException("Processing snapshot required");
        var headers=Collections.list(request.getHeaders("Idempotency-Key"));if(headers.size()!=1)throw new IllegalArgumentException("One request key required");
        return response(service.accept(actor(request),sessionId,headers.getFirst(),HttpRevisionTokens.expected(body),body.path("materialSnapshotId").isNull()?null:body.path("materialSnapshotId").asText()));
    }
    @PostMapping("/retry") ResponseEntity<?> retry(@PathVariable String sessionId,HttpServletRequest request)throws java.io.IOException{
        var body=RecordingJsonObject.read(request,256);
        if(body.size()!=1||!body.has("expectedTaskId")||!(body.path("expectedTaskId").isNull()||body.path("expectedTaskId").isTextual()))throw new IllegalArgumentException("Observed task required");
        return response(service.retry(actor(request),sessionId,body.path("expectedTaskId").isNull()?null:body.path("expectedTaskId").asText()));
    }
    private static ama.contract.IdentityContracts.Actor actor(HttpServletRequest request){return ((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();}
    private static ResponseEntity<?> response(Object result){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(result);}
}
