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
@RequestMapping("/api/v1/meetings/{meetingId}/publication-destinations/libraries")
final class PublicationDiscoveryController {
 private final MinutesConfiguration.Runtime runtime;private final TransactionRunner tx;private final MeetingAccess access;private final PublicationReadRequests reads;
 PublicationDiscoveryController(MinutesConfiguration.Runtime runtime,TransactionRunner tx,MeetingAccess access,PublicationReadRequests reads){this.runtime=runtime;this.tx=tx;this.access=access;this.reads=reads;}
 @GetMapping WebAsyncTask<ResponseEntity<byte[]>> libraries(@PathVariable String meetingId,HttpServletRequest request){return read(meetingId,null,request);}
 @GetMapping("/{libraryId}/folders") WebAsyncTask<ResponseEntity<byte[]>> folders(@PathVariable String meetingId,@PathVariable String libraryId,HttpServletRequest request){return read(meetingId,libraryId,request);}
 private WebAsyncTask<ResponseEntity<byte[]>> read(String meeting,String library,HttpServletRequest request){
  PublicationDestinationPage.path(request.getRequestURI());var parameters=PublicationDestinationPage.parameters(request.getQueryString());PublicationDestinationPage.request(meeting,library,parameters);
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();
  if(runtime.publications()==null){tx.required(()->{access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);return null;});throw PublicationReadRequests.unavailable();}
  return reads.start(cancellation->{var page=runtime.publications().destinations(actor,meeting,library,parameters,cancellation);byte[] body=TranscriptWire.response(page,Math.min(262144,runtime.responseBytes()));if(cancellation.cancelled())throw PublicationReadRequests.unavailable();return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(body);});
 }
}
