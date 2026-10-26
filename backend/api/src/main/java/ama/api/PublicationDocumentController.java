package ama.api;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.ResolvedSession;
import ama.db.TransactionRunner;
import ama.transcript.PublicationDestinationPage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;
@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/publications/{publicationId}/document")
final class PublicationDocumentController {
 private final MinutesConfiguration.Runtime runtime;private final TransactionRunner tx;private final MeetingAccess access;private final PublicationReadRequests reads;
 PublicationDocumentController(MinutesConfiguration.Runtime runtime,TransactionRunner tx,MeetingAccess access,PublicationReadRequests reads){this.runtime=runtime;this.tx=tx;this.access=access;this.reads=reads;}
 @GetMapping WebAsyncTask<ResponseEntity<byte[]>> document(@PathVariable String meetingId,@PathVariable String publicationId,HttpServletRequest request){
  PublicationDestinationPage.path(request.getRequestURI());ama.contract.MinutesContracts.uuid(meetingId);ama.contract.MinutesContracts.uuid(publicationId);if(request.getQueryString()!=null)throw new IllegalArgumentException("No official document query parameters");
  var viewer=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();
  if(runtime.publications()==null){tx.required(()->{access.require(access.lock(viewer,meetingId),MeetingAccess.Action.READ);return null;});throw PublicationReadRequests.unavailable();}
  return reads.start(cancellation->{byte[] html=runtime.publications().officialDocument(viewer,meetingId,publicationId,cancellation);if(cancellation.cancelled())throw PublicationReadRequests.unavailable();return ResponseEntity.ok().contentType(new MediaType("text","html",java.nio.charset.StandardCharsets.UTF_8)).header("Cache-Control","private, no-store").header("Content-Security-Policy","default-src 'none'; frame-ancestors 'self'").header("X-Content-Type-Options","nosniff").body(html);});
 }
}
