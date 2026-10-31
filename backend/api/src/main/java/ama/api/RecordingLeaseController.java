package ama.api;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import ama.recording.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
/** Claims and renewal remain under current HTTP session and owner authorization. */
@RestController final class RecordingLeaseController {
 record Claim(Long expectedEpoch,String collectorId,String requestId){}
 record Renew(Long expectedEpoch,String collectorId){}
 private final RecordingService recordings;private final JdbcRecordingWriterLeases leases;private final TransactionRunner tx;private final MeetingAccess access;
 private final RecordingMutationGuard mutations;
 RecordingLeaseController(RecordingService recordings,JdbcRecordingWriterLeases leases,TransactionRunner tx,MeetingAccess access,RecordingMutationGuard mutations){this.recordings=recordings;this.leases=leases;this.tx=tx;this.access=access;this.mutations=mutations;}
 @PostMapping("/api/v1/recordings/{sessionId}/writer-lease")
 JdbcRecordingWriterLeases.Lease claim(@PathVariable String sessionId,HttpServletRequest request)throws IOException {
  var actor=actor(request);var body=RecordingJson.read(request,Claim.class);epoch(body==null?null:body.expectedEpoch());String meeting=recordings.session(sessionId).meetingId();
  return tx.required(()->{var held=access.lock(actor,meeting);access.require(held,MeetingAccess.Action.EDIT);mutations.require(actor,meeting,held);return leases.acquire(sessionId,body.expectedEpoch(),body.collectorId(),body.requestId());});
 }
 @PostMapping("/api/v1/recordings/{sessionId}/writer-lease/renew")
 JdbcRecordingWriterLeases.Lease renew(@PathVariable String sessionId,HttpServletRequest request)throws IOException {
  var actor=actor(request);var body=RecordingJson.read(request,Renew.class);epoch(body==null?null:body.expectedEpoch());String meeting=recordings.session(sessionId).meetingId();
  return tx.required(()->{var held=access.lock(actor,meeting);access.require(held,MeetingAccess.Action.EDIT);mutations.require(actor,meeting,held);return leases.renew(sessionId,body.expectedEpoch(),body.collectorId());});
 }
 private static void epoch(Long epoch){if(epoch==null||epoch<1)throw new IllegalArgumentException("Expected writer epoch required");}
 private static Actor actor(HttpServletRequest request){if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession session))throw new MeetingAccess.AccessDeniedException();return session.actor();}
}
