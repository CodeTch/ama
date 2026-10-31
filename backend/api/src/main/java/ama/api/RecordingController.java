package ama.api;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.ResolvedSession;
import ama.contract.RecordingContracts;
import ama.db.TransactionRunner;
import ama.recording.RecordingService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Bounded binary HTTP transport; every operation holds the current meeting authorization gate. */
@RestController
class RecordingController {
    record SessionResponse(String sessionId,String partId,long writerEpoch,String backendId) {}
    record FinishResponse(String sessionId,long finalSequence,String archiveTaskId) {}
    static final class TooLarge extends RuntimeException {}
    private final RecordingService service;
    private final String backendId;
    private final TransactionRunner transactions;
    private final MeetingAccess access;
    private final ama.recording.RecordingArchiveAdmission archives;
    private final ama.recording.JdbcRecordingWriterLeases leases;
    private final ama.recording.RecordingMutationGuard mutations;
    private final RecordingIngressBudget ingress;
    RecordingController(RecordingService service,Environment environment,TransactionRunner transactions,MeetingAccess access,ama.recording.RecordingArchiveAdmission archives,ama.recording.JdbcRecordingWriterLeases leases,ama.recording.RecordingMutationGuard mutations,RecordingIngressBudget ingress) {
        this.service=Objects.requireNonNull(service);this.backendId=environment.getProperty("recording.backend-id","backend-local");
        this.transactions=Objects.requireNonNull(transactions);this.access=Objects.requireNonNull(access);
        this.archives=Objects.requireNonNull(archives);
        this.leases=Objects.requireNonNull(leases);
        this.mutations=Objects.requireNonNull(mutations);
        this.ingress=Objects.requireNonNull(ingress);
    }
    @PostMapping("/api/v1/meetings/{meetingId}/recordings")
    ResponseEntity<SessionResponse> create(@PathVariable String meetingId,HttpServletRequest request) {
        return meeting(request,meetingId,gate -> {
            mutations.require(actor(request),meetingId,gate);
            var s=service.open(meetingId,backendId);
            return ResponseEntity.status(201).body(new SessionResponse(s.sessionId(),s.partId(),s.writerEpoch(),s.backendId()));
        });
    }
    @GetMapping("/api/v1/recordings/{sessionId}")
    SessionResponse current(@PathVariable String sessionId,HttpServletRequest request) {
        return session(request,sessionId,() -> {var s=service.session(sessionId);return new SessionResponse(s.sessionId(),s.partId(),s.writerEpoch(),s.backendId());});
    }
    @GetMapping("/api/v1/recordings/{sessionId}/ack")
    RecordingContracts.Ack ack(@PathVariable String sessionId,@RequestParam String partId,HttpServletRequest request) {
        return session(request,sessionId,() -> service.ack(sessionId,partId));
    }
    @GetMapping("/api/v1/recordings/{sessionId}/manifest")
    RecordingService.Manifest manifest(@PathVariable String sessionId,HttpServletRequest request) { return session(request,sessionId,()->service.manifest(sessionId)); }
    @PostMapping(value="/api/v1/recordings/{sessionId}/chunks",consumes="application/octet-stream")
    RecordingContracts.Ack chunk(@PathVariable String sessionId,@RequestHeader(name="X-Writer-Epoch") long epoch,
            @RequestHeader(name="X-Part-Id") String partId,@RequestHeader(name="X-Seq") long seq,
            @RequestHeader(name="X-Sample-Start") long start,@RequestHeader(name="X-Sample-Count") long count,
            HttpServletRequest request,@RequestHeader(name="X-Sha256",required=false) String sha256,@RequestHeader(name="X-Collector-Id",required=false) String collector) throws IOException {
        actor(request);
        if(request.getContentLengthLong()>RecordingContracts.MAX_PCM_BYTES)throw new TooLarge();
        byte[] pcm=request.getInputStream().readNBytes(RecordingContracts.MAX_PCM_BYTES+1);
        if(pcm.length>RecordingContracts.MAX_PCM_BYTES)throw new TooLarge();
        if(sha256==null)throw new IllegalArgumentException("Recording chunk SHA-256 required");
        var value=new RecordingContracts.Chunk(partId,epoch,seq,start,count,sha256,pcm);
        return writer(request,sessionId,epoch,collector,() -> {ingress.require(actor(request).enterpriseId(),sessionId,collector,pcm.length+ama.realtime.RecordingBinaryCodec.HEADER_BYTES);return service.accept(sessionId,epoch,value);});
    }
    @PostMapping("/api/v1/recordings/{sessionId}/pause")
    ResponseEntity<Void> pause(@PathVariable String sessionId,@RequestHeader(name="X-Expected-Epoch") long epoch,HttpServletRequest request,@RequestHeader(name="X-Collector-Id",required=false) String collector) {
        return writer(request,sessionId,epoch,collector,() -> {service.pause(sessionId,epoch);return ResponseEntity.noContent().build();});
    }
    @PostMapping("/api/v1/recordings/{sessionId}/parts")
    SessionResponse nextPart(@PathVariable String sessionId,@RequestHeader(name="X-Expected-Epoch") long epoch,HttpServletRequest request,@RequestHeader(name="X-Collector-Id",required=false) String collector) {
        return writer(request,sessionId,epoch,collector,() -> {var s=service.nextPart(sessionId,epoch);return new SessionResponse(s.sessionId(),s.partId(),s.writerEpoch(),s.backendId());});
    }
    @PostMapping("/api/v1/recordings/{sessionId}/resume")
    ResponseEntity<Void> resume(@PathVariable String sessionId,@RequestHeader(name="X-Expected-Epoch") long epoch,HttpServletRequest request,@RequestHeader(name="X-Collector-Id",required=false) String collector) {
        return writer(request,sessionId,epoch,collector,() -> {service.resume(sessionId,epoch);return ResponseEntity.noContent().build();});
    }
    @PostMapping("/api/v1/recordings/{sessionId}/finish")
    FinishResponse finishHttp(@PathVariable String sessionId,@RequestHeader(name="X-Expected-Epoch") long epoch,HttpServletRequest request,@RequestHeader(name="X-Collector-Id",required=false) String collector)throws IOException {
        actor(request);return finish(sessionId,epoch,RecordingJson.finals(request),request,collector);
    }
    FinishResponse finish(String sessionId,long epoch,Map<String,Long> finalSequences,HttpServletRequest request,String collector) {
        actor(request);String meeting=service.session(sessionId).meetingId();
        var sealed=meeting(request,meeting,gate -> {if(!service.finished(sessionId)){mutations.require(actor(request),meeting,gate);leases.require(sessionId,epoch,collector);}return service.finish(sessionId,epoch,finalSequences);});
        return new FinishResponse(sealed.sessionId(),sealed.finalSequence(),archives.accept(actor(request),sessionId));
    }
    private <T> T writer(HttpServletRequest request,String id,long epoch,String collector,Supplier<T> work) {
        actor(request);String meeting=service.session(id).meetingId();
        return meeting(request,meeting,gate->{mutations.require(actor(request),meeting,gate);leases.require(id,epoch,collector);return work.get();});
    }
    private <T> T session(HttpServletRequest request,String id,Supplier<T> work) {
        actor(request);String meetingId=service.session(id).meetingId();
        return meeting(request,meetingId,gate->work.get());
    }
    private <T> T meeting(HttpServletRequest request,String id,java.util.function.Function<MeetingAccess.Locked,T> work) {
        var actor=actor(request);
        return transactions.required(() -> {var held=access.lock(actor,id);access.require(held,MeetingAccess.Action.EDIT);return work.apply(held);});
    }
    private static ama.contract.IdentityContracts.Actor actor(HttpServletRequest request) {
        if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession s))throw new MeetingAccess.AccessDeniedException();
        return s.actor();
    }
}
